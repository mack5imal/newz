package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.store.ArticleIds
import app.newz.store.ArticleStore
import app.newz.store.CustomFeed
import app.newz.store.UserPrefs
import app.newz.store.characterCount
import app.newz.store.followTopic
import app.newz.store.restoreOutlet
import app.newz.store.restoreTopic
import kotlin.time.Instant

/**
 * Follows [outletId] with all its section feeds (search and suggestions "Follow"). A removed
 * outlet is restored. An id the catalog does not know changes nothing.
 */
fun UserPrefs.followOutlet(
    outletId: String,
    catalog: Catalog,
): UserPrefs {
    if (catalog.outlet(outletId) == null) return this
    val feeds = sectionFeeds(catalog, outletId).mapTo(LinkedHashSet()) { it.id }
    return restoreOutlet(outletId).copy(followedOutlets = followedOutlets + (outletId to feeds))
}

/** Follows a catalog topic found in search or suggestions; a muted topic is unmuted. */
fun UserPrefs.followCatalogTopic(
    topicId: String,
    catalog: Catalog,
): UserPrefs = if (catalog.topic(topicId) == null) this else followTopic(topicId).restoreTopic(topicId)

/** Returns [raw] trimmed when it can be a custom topic (1..60 chars, not blank), else null. */
fun normalizeCustomTopic(raw: String): String? =
    raw.trim().takeIf {
        it.isNotEmpty() &&
            it.characterCount() <= MAX_CUSTOM_TOPIC_LENGTH
    }

/**
 * Adds [raw] as a custom topic (shows articles from the user's sources that mention it). Throws
 * [IllegalArgumentException] when [normalizeCustomTopic] rejects it; adding a word already
 * there (ignoring case) changes nothing.
 */
fun UserPrefs.addCustomTopic(raw: String): UserPrefs {
    val word =
        requireNotNull(normalizeCustomTopic(raw)) { "A custom topic must be 1-$MAX_CUSTOM_TOPIC_LENGTH characters" }
    if (customTopics.any { it.equals(word, ignoreCase = true) }) return this
    return copy(customTopics = customTopics + word)
}

/** The stable custom-feed id for [feedUrl]: the same normalised URL always gives the same id. */
fun customFeedId(feedUrl: String): String = "url-" + ArticleIds.of(CUSTOM_FEED_SCOPE, null, normalizeFeedUrl(feedUrl))

/**
 * The prefs half of [ArticleStore.followCustomFeed]: following a custom feed the user removed
 * earlier brings it back by taking its id out of [UserPrefs.removedOutlets].
 */
fun UserPrefs.followCustomFeed(feed: CustomFeed): UserPrefs = restoreOutlet(feed.id)

/**
 * Adds the feed [found] by discovery as a custom feed. Adding a feed whose normalised URL is
 * already a custom feed keeps the existing row and returns it, so there is never a second row.
 */
fun ArticleStore.followCustomFeed(
    found: DiscoveryResult.Found,
    now: Instant,
): CustomFeed {
    val key = normalizeFeedUrl(found.feedUrl)
    listCustomFeeds().firstOrNull { normalizeFeedUrl(it.url) == key }?.let { return it }
    val feed =
        CustomFeed(
            id = customFeedId(found.feedUrl),
            url = found.feedUrl,
            title = found.siteName,
            siteUrl = found.siteUrl,
            kind = found.kind.name,
            addedAt = now,
        )
    addCustomFeed(feed)
    return feed
}

private const val CUSTOM_FEED_SCOPE = "custom-feed"
