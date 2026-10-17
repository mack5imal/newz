package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.engine.FeedFetcher
import app.newz.feed.FeedKind
import app.newz.feed.FeedParseException
import app.newz.feed.FeedParser
import app.newz.feed.HtmlText
import app.newz.feed.ParsedFeed
import app.newz.net.BlockedHostException
import app.newz.net.FetchResult
import app.newz.net.HostGuard
import app.newz.net.fetchCapped
import app.newz.store.CustomFeed
import app.newz.store.UserPrefs
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The single result of checking a web address for a feed (design `search`, "Web address"). */
sealed interface DiscoveryResult {
    /**
     * A feed was found. [kind] is RSS or ATOM (an RDF / RSS 1.0 feed reports RSS). [catalogFeedId]
     * is set when the feed is a bundled catalog feed the user does not follow yet, so "Add"
     * follows that feed instead of adding a custom one.
     */
    data class Found(
        val feedUrl: String,
        val kind: FeedKind,
        val siteName: String,
        val siteUrl: String?,
        val latestTitle: String?,
        val latestAge: Duration?,
        val catalogFeedId: String? = null,
    ) : DiscoveryResult

    /** The site answered but neither is a feed nor declares one. */
    data object NoFeed : DiscoveryResult

    /** The text is not a usable web address. */
    data object NotAnAddress : DiscoveryResult

    /** The address, its declared feed or a redirect is Google-owned: refused, nothing sent there. */
    data object Google : DiscoveryResult

    /** The feed is already followed ([feedId] is its catalog or custom feed id). */
    data class AlreadyFollowing(
        val feed: Found,
        val feedId: String,
    ) : DiscoveryResult

    /** A response was over the 5 MB cap. */
    data object TooLarge : DiscoveryResult

    /** The site could not be reached, answered an HTTP error or redirected too often. */
    data object Failed : DiscoveryResult
}

/**
 * Finds the feed behind a typed web address (SPEC S17). Only the typed site and the feed URL it
 * declares are requested, through the guarded client, so every hop is checked by [HostGuard].
 * The order: classify the input; refuse Google before any request; fetch (5 MB cap); a body that
 * parses as a feed is the feed; otherwise an HTML page's `<link rel="alternate">` RSS/Atom URL
 * is resolved, guard-checked, fetched and parsed. Nothing is recorded anywhere.
 */
class FeedDiscovery(
    private val client: HttpClient,
    private val catalog: Catalog,
    private val clock: Clock = Clock.System,
) {
    suspend fun discover(
        input: String,
        prefs: UserPrefs,
        customFeeds: List<CustomFeed>,
    ): DiscoveryResult {
        val address = AddressInput.classify(input) as? Input.WebAddress ?: return DiscoveryResult.NotAnAddress
        if (HostGuard.isBlocked(address.url)) return DiscoveryResult.Google
        val page =
            when (val fetched = fetch(address.url)) {
                is Fetched.Stop -> return fetched.result
                is Fetched.Ok -> fetched.success
            }
        parseOrNull(page)?.let { return found(address.url, page.finalUrl, it, prefs, customFeeds) }
        val html = FeedParser.decode(page.bytes, page.charset)
        if (!HTML_MARKER.containsMatchIn(html)) return DiscoveryResult.NoFeed
        val feedUrl = feedLink(html, page.finalUrl) ?: return DiscoveryResult.NoFeed
        if (HostGuard.isBlocked(feedUrl)) return DiscoveryResult.Google
        val feedPage =
            when (val fetched = fetch(feedUrl)) {
                is Fetched.Stop -> return fetched.result
                is Fetched.Ok -> fetched.success
            }
        val parsed = parseOrNull(feedPage) ?: return DiscoveryResult.NoFeed
        return found(feedUrl, feedPage.finalUrl, parsed, prefs, customFeeds)
    }

    private sealed interface Fetched {
        class Ok(
            val success: FetchResult.Success,
        ) : Fetched

        class Stop(
            val result: DiscoveryResult,
        ) : Fetched
    }

    private suspend fun fetch(url: String): Fetched =
        try {
            when (val result = client.fetchCapped(url)) {
                is FetchResult.Success -> Fetched.Ok(result)
                FetchResult.TooLarge -> Fetched.Stop(DiscoveryResult.TooLarge)
                is FetchResult.HttpError -> Fetched.Stop(DiscoveryResult.Failed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: BlockedHostException) {
            Fetched.Stop(DiscoveryResult.Google)
        } catch (_: Exception) {
            Fetched.Stop(DiscoveryResult.Failed)
        }

    private suspend fun parseOrNull(page: FetchResult.Success): ParsedFeed? =
        try {
            FeedParser.parse(page.bytes, page.charset)
        } catch (_: FeedParseException) {
            null
        }

    private fun found(
        requestedUrl: String,
        finalUrl: String,
        feed: ParsedFeed,
        prefs: UserPrefs,
        customFeeds: List<CustomFeed>,
    ): DiscoveryResult {
        val latest = feed.items.maxByOrNull { it.publishedAt ?: Instant.DISTANT_PAST } ?: feed.items.firstOrNull()
        val now = clock.now()
        val keys = setOf(normalizeFeedUrl(requestedUrl), normalizeFeedUrl(finalUrl))
        val catalogId = catalogFeedUrls().entries.firstOrNull { it.key in keys }?.value
        val result =
            DiscoveryResult.Found(
                feedUrl = requestedUrl,
                kind = if (feed.kind == FeedKind.ATOM) FeedKind.ATOM else FeedKind.RSS,
                siteName = feed.title.ifBlank { Url(requestedUrl).host },
                siteUrl = feed.siteUrl,
                latestTitle = latest?.title?.ifBlank { null },
                latestAge = latest?.publishedAt?.let { (now - it).coerceAtLeast(Duration.ZERO) },
                catalogFeedId = catalogId,
            )
        val followed = followedFeedUrls(prefs, customFeeds).entries.firstOrNull { it.key in keys }
        return if (followed != null) {
            DiscoveryResult.AlreadyFollowing(result.copy(catalogFeedId = null), followed.value)
        } else {
            result
        }
    }

    /**
     * Normalised URL -> feed id for every feed [prefs] follow, by the same rule the feed engine
     * fetches with ([FeedFetcher.followedSources]): removed outlets and removed custom feeds are
     * not followed. A catalog feed is keyed by both its `url` and its `checkedUrl`.
     */
    private fun followedFeedUrls(
        prefs: UserPrefs,
        customFeeds: List<CustomFeed>,
    ): Map<String, String> =
        buildMap {
            for (source in FeedFetcher.followedSources(prefs, catalog, customFeeds)) {
                put(normalizeFeedUrl(source.url), source.feedId)
                catalog.feed(source.feedId)?.let { put(normalizeFeedUrl(it.url), it.id) }
            }
        }

    private fun catalogFeedUrls(): Map<String, String> =
        buildMap {
            for (feed in catalog.feeds) {
                put(normalizeFeedUrl(feed.url), feed.id)
                feed.checkedUrl?.let { put(normalizeFeedUrl(it), feed.id) }
            }
        }

    internal companion object {
        private val HTML_MARKER = Regex("<(html|head|link)\\b", RegexOption.IGNORE_CASE)
        private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
        private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml")
        private val HTTP_SCHEMES = setOf("http", "https")

        /**
         * The first `<link rel="alternate" type="application/rss+xml|atom+xml" href>` in [html]
         * whose href resolves against [baseUrl] to an http(s) URL; null when there is none. A
         * candidate that fails to resolve or has another scheme (e.g. `feed://`) is skipped.
         */
        fun feedLink(
            html: String,
            baseUrl: String,
        ): String? {
            for (tag in LINK_TAG.findAll(html)) {
                val attrs =
                    ATTRIBUTE.findAll(tag.value).associate { m ->
                        val value = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty()
                        m.groupValues[1].lowercase() to HtmlText.decodeEntities(value).trim()
                    }
                val rel = attrs["rel"].orEmpty().lowercase().split(' ', '\t', '\n')
                val type =
                    attrs["type"]
                        .orEmpty()
                        .lowercase()
                        .substringBefore(';')
                        .trim()
                val href = attrs["href"].orEmpty()
                if ("alternate" !in rel || type !in FEED_TYPES || href.isEmpty()) continue
                val resolved =
                    try {
                        URLBuilder(baseUrl).takeFrom(href)
                    } catch (_: Exception) {
                        continue
                    }
                if (resolved.protocol.name.lowercase() !in HTTP_SCHEMES) continue
                return try {
                    resolved.buildString()
                } catch (_: Exception) {
                    continue
                }
            }
            return null
        }
    }
}

/**
 * The form two feed URLs are compared in: lowercase scheme and host, no fragment, no trailing
 * slash. The host is punycode-encoded so an IDN and its `xn--` form compare equal.
 */
fun normalizeFeedUrl(url: String): String {
    val trimmed = url.trim().substringBefore('#')
    val parsed =
        try {
            Url(trimmed)
        } catch (_: Exception) {
            return trimmed.lowercase().trimEnd('/')
        }
    val host = HostGuard.normalizeHost(parsed.host) ?: parsed.host.lowercase()
    val port = if (parsed.port == parsed.protocol.defaultPort) "" else ":${parsed.port}"
    val path = parsed.encodedPath.trimEnd('/')
    val query =
        parsed.encodedQuery
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" }
            .orEmpty()
    return "${parsed.protocol.name.lowercase()}://$host$port$path$query"
}
