package app.newz.filter

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.PaywallStatus
import app.newz.feed.Article
import app.newz.store.UserPrefs
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How long an article stays in the feed (SPEC S6). */
val FEED_WINDOW = 7.days

/** The prefix of a custom keyword topic's id: the topic "rust" has the id `custom:rust`. */
const val CUSTOM_TOPIC_PREFIX = "custom:"

fun customTopicId(word: String): String = CUSTOM_TOPIC_PREFIX + word

/** Why [FeedFilter] keeps an article out of the feed, in the order the rules are checked. */
enum class ExclusionReason {
    TOO_OLD,
    HIDDEN,
    OUTLET_REMOVED,
    FEED_NOT_FOLLOWED,
    TOPIC_NOT_FOLLOWED,
    TOPIC_MUTED,
    KEYWORD_MUTED,
    PAYWALLED,
}

/**
 * Everything the filter reads. [hiddenIds] are the ids of articles the user hid; [customFeedIds]
 * are the feeds the user added by URL, which are followed by being added and carry no catalog
 * topics. Build one per feed load and reuse it: the keyword matchers are compiled once.
 */
data class FilterContext(
    val prefs: UserPrefs,
    val catalog: Catalog,
    val hiddenIds: Set<String>,
    val now: Instant,
    val customFeedIds: Set<String> = emptySet(),
) {
    internal val mutedKeywords: KeywordMatcher by lazy { KeywordMatcher(prefs.mutedKeywords.map { it.text }) }

    internal val customTopicMatchers: List<Pair<String, KeywordMatcher>> by lazy {
        prefs.customTopics.map { customTopicId(it) to KeywordMatcher(listOf(it)) }
    }
}

/**
 * The rules that decide whether an article may ever reach a card (SPEC S6, S8, S9, S12). Pure:
 * no IO. Mutes always win: no follow, level or boost re-admits an article excluded here, and
 * ranking (phase 8) only orders what [eligible] lets through.
 */
object FeedFilter {
    fun eligible(
        a: Article,
        ctx: FilterContext,
    ): Boolean = reason(a, ctx) == null

    /** The first rule that excludes [a], or null when it is eligible. */
    fun reason(
        a: Article,
        ctx: FilterContext,
    ): ExclusionReason? {
        val prefs = ctx.prefs
        val effectiveDate = minOf(a.date, ctx.now)
        if (effectiveDate < ctx.now - FEED_WINDOW) return ExclusionReason.TOO_OLD
        if (a.id in ctx.hiddenIds) return ExclusionReason.HIDDEN
        if (a.outletId in prefs.removedOutlets) return ExclusionReason.OUTLET_REMOVED

        val feed = ctx.catalog.feed(a.feedId)
        if (feed != null && isLocal(feed) && !localFollowed(feed, prefs)) return ExclusionReason.FEED_NOT_FOLLOWED

        val customTopics = matchedCustomTopics(a, ctx)
        val isCustomFeed = a.feedId in ctx.customFeedIds
        if (!isCustomFeed && !feedFollowed(feed, prefs) &&
            customTopics.isEmpty()
        ) {
            return ExclusionReason.FEED_NOT_FOLLOWED
        }
        if (!isCustomFeed && customTopics.isEmpty() && a.topics.none { it in prefs.followedTopics }) {
            return ExclusionReason.TOPIC_NOT_FOLLOWED
        }
        if ((a.topics + customTopics).any { it in prefs.mutedTopics }) return ExclusionReason.TOPIC_MUTED
        if (matchesAny(ctx.mutedKeywords, a)) return ExclusionReason.KEYWORD_MUTED
        if (prefs.paywallFilterOn && paywalled(a.outletId, ctx)) return ExclusionReason.PAYWALLED
        return null
    }

    /** [a] with the ids of the followed custom keyword topics it matches added to its topics. */
    fun withCustomTopics(
        a: Article,
        ctx: FilterContext,
    ): Article {
        val extra = matchedCustomTopics(a, ctx).filterNot { it in a.topics }
        return if (extra.isEmpty()) a else a.copy(topics = a.topics + extra)
    }

    /** The custom topic ids [a] carries or whose word its headline or summary contains. */
    private fun matchedCustomTopics(
        a: Article,
        ctx: FilterContext,
    ): List<String> =
        ctx.customTopicMatchers
            .filter { (id, matcher) ->
                id in a.topics || matchesAny(matcher, a)
            }.map { it.first }

    private fun isLocal(feed: CatalogFeed): Boolean = feed.metroId != null && Catalog.LOCAL_TOPIC in feed.topics

    /** A local feed passes only when `local` is followed and the feed's metro is the user's (S29). */
    private fun localFollowed(
        feed: CatalogFeed,
        prefs: UserPrefs,
    ): Boolean = Catalog.LOCAL_TOPIC in prefs.followedTopics && feed.metroId == prefs.localMetroId

    /**
     * A catalog feed is followed when its section is ticked under its outlet; a local feed is
     * followed through [localFollowed] instead. A feed missing from the catalog is not followed.
     */
    private fun feedFollowed(
        feed: CatalogFeed?,
        prefs: UserPrefs,
    ): Boolean =
        when {
            feed == null -> false
            isLocal(feed) -> localFollowed(feed, prefs)
            else -> prefs.followedOutlets[feed.outletId]?.contains(feed.id) == true
        }

    private fun paywalled(
        outletId: String,
        ctx: FilterContext,
    ): Boolean = outletId in ctx.prefs.paywallFlags || ctx.catalog.outlet(outletId)?.paywall == PaywallStatus.HARD

    /** Headline and summary are matched separately, so a phrase never spans the two. */
    private fun matchesAny(
        matcher: KeywordMatcher,
        a: Article,
    ): Boolean = matcher.matches(a.title) || a.summary?.let(matcher::matches) == true
}
