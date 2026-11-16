package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.dedup.StoryGroup
import app.newz.dedup.StoryGrouper
import app.newz.feed.Article
import app.newz.filter.FeedFilter
import app.newz.filter.FilterContext
import app.newz.store.ArticleStore
import app.newz.store.FeedSighting
import app.newz.store.StoredArticle
import app.newz.store.UserPrefs
import kotlin.time.Instant

/**
 * Everything one composition reads, taken together at one moment: the cache plus the user's
 * current preferences, hidden, saved, custom-feed and (when history is on) history rows.
 */
class FeedSnapshot(
    val prefs: UserPrefs,
    val articles: List<StoredArticle>,
    val hiddenIds: Set<String>,
    val savedIds: Set<String>,
    val customFeedIds: Set<String>,
    val affinity: ReadingAffinity,
    /** Every feed each article was seen in (B31), by article id; missing ids have only their own. */
    val sightings: Map<String, List<FeedSighting>> = emptyMap(),
) {
    companion object {
        /** Reads [store] now. Call it on the store's dispatcher. */
        fun read(
            store: ArticleStore,
            prefs: UserPrefs,
        ): FeedSnapshot =
            FeedSnapshot(
                prefs = prefs,
                articles = store.articles(),
                hiddenIds = store.listHidden().mapTo(HashSet()) { it.articleId },
                savedIds = store.listSaved().mapTo(HashSet()) { it.article.id },
                customFeedIds = store.listCustomFeeds().mapTo(HashSet()) { it.id },
                affinity = if (prefs.readingHistoryOn) ReadingAffinity(store.listHistory()) else ReadingAffinity.EMPTY,
                sightings = store.articleSightings(),
            )
    }
}

/** One card: a story group, and whether its lead article is saved. */
data class FeedCard(
    val group: StoryGroup,
    val saved: Boolean,
)

/**
 * The pure pipeline from a [FeedSnapshot] to ranked groups: cache rows to [Article]s,
 * [FeedFilter] (mutes always win), one article per URL, custom topic tags, [StoryGrouper]
 * (lead = followed outlet plus reading affinity) and [FeedRanker]. The topic chip filter is not
 * applied here: the feed's view model narrows the served cards (SPEC S14).
 */
object FeedComposer {
    fun compose(
        snapshot: FeedSnapshot,
        catalog: Catalog,
        now: Instant,
        grouper: StoryGrouper = StoryGrouper(),
    ): List<StoryGroup> {
        val prefs = snapshot.prefs
        val ctx = FilterContext(prefs, catalog, snapshot.hiddenIds, now, snapshot.customFeedIds)
        val eligible =
            snapshot.articles
                .mapNotNull { eligibleSighting(it, snapshot.sightings[it.id], ctx) }
                .sortedWith(compareBy<Article> { it.firstSeenAt }.thenBy { it.id })
                .distinctBy { it.url }
                .map { FeedFilter.withCustomTopics(it, ctx) }
        val followed = followedOutletIds(prefs, catalog, snapshot.customFeedIds)
        val groups =
            grouper.group(eligible) { outletId ->
                (if (outletId in followed) 1.0 else 0.0) + snapshot.affinity.outletScore(outletId)
            }
        return FeedRanker.rank(groups, prefs, now)
    }

    /**
     * [s] as an [Article] through the first of its feeds the filter lets through, its own feed
     * first, or null when none does (B31). The same story in two of an outlet's sections stays
     * in the feed while either section, and its topics, is still followed and unmuted.
     */
    private fun eligibleSighting(
        s: StoredArticle,
        sightings: List<FeedSighting>?,
        ctx: FilterContext,
    ): Article? {
        val own = toArticle(s)
        if (FeedFilter.eligible(own, ctx)) return own
        for (sighting in sightings.orEmpty()) {
            if (sighting.feedId == s.feedId) continue
            val other = own.copy(feedId = sighting.feedId, topics = sighting.topics)
            if (FeedFilter.eligible(other, ctx)) return other
        }
        return null
    }

    fun toArticle(s: StoredArticle): Article =
        Article(
            id = s.id,
            feedId = s.feedId,
            outletId = s.outletId,
            url = s.url,
            title = s.title,
            summary = s.summary,
            imageUrl = s.imageUrl,
            publishedAt = s.publishedAt,
            firstSeenAt = s.firstSeenAt,
            topics = s.topics,
        )

    private fun followedOutletIds(
        prefs: UserPrefs,
        catalog: Catalog,
        customFeedIds: Set<String>,
    ): Set<String> {
        val outlets = prefs.followedOutlets.filterValues { it.isNotEmpty() }.keys
        val local =
            prefs.localMetroId
                ?.takeIf { Catalog.LOCAL_TOPIC in prefs.followedTopics }
                ?.let(catalog::localFeeds)
                .orEmpty()
                .map { it.outletId }
        return outlets + local + customFeedIds
    }
}
