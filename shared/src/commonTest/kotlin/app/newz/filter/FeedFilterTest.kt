package app.newz.filter

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.catalog.Topic
import app.newz.feed.Article
import app.newz.store.Level
import app.newz.store.UserPrefs
import app.newz.store.flagPaywall
import app.newz.store.muteKeyword
import app.newz.store.muteTopic
import app.newz.store.removeOutlet
import app.newz.store.setLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FeedFilterTest {
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000)

    private val catalog =
        Catalog(
            topics = listOf("world", "politics", "tech", "local").map { Topic(it, it, "") },
            outlets =
                listOf(
                    Outlet("npr", "NPR", "npr.org", PaywallStatus.NONE, 0.9),
                    Outlet("nyt", "NYT", "nytimes.com", PaywallStatus.HARD, 0.9),
                    Outlet("wapo", "WaPo", "washingtonpost.com", PaywallStatus.METERED, 0.8),
                    Outlet("bbc", "BBC", "bbc.co.uk", PaywallStatus.NONE, 0.9),
                    Outlet("kiro", "KIRO", "kiro7.com", PaywallStatus.NONE, 0.3),
                    Outlet("wgn", "WGN", "wgntv.com", PaywallStatus.NONE, 0.3),
                ),
            feeds =
                listOf(
                    CatalogFeed("npr-world", "npr", "World", "https://npr.org/world.xml", listOf("world")),
                    CatalogFeed(
                        "npr-politics",
                        "npr",
                        "Politics",
                        "https://npr.org/politics.xml",
                        listOf("politics", "world"),
                    ),
                    CatalogFeed("npr-tech", "npr", "Tech", "https://npr.org/tech.xml", listOf("tech")),
                    CatalogFeed("nyt-world", "nyt", "World", "https://nyt.com/world.xml", listOf("world")),
                    CatalogFeed("wapo-world", "wapo", "World", "https://wapo.com/world.xml", listOf("world")),
                    CatalogFeed("bbc-world", "bbc", "World", "https://bbc.co.uk/world.xml", listOf("world")),
                    CatalogFeed(
                        "kiro-local",
                        "kiro",
                        "Local",
                        "https://kiro7.com/rss",
                        listOf("local"),
                        metroId = "seattle",
                    ),
                    CatalogFeed(
                        "wgn-local",
                        "wgn",
                        "Local",
                        "https://wgntv.com/rss",
                        listOf("local"),
                        metroId = "chicago",
                    ),
                ),
        )

    private val prefs =
        UserPrefs(
            followedTopics = listOf("world", "politics"),
            followedOutlets =
                mapOf(
                    "npr" to setOf("npr-world", "npr-politics"),
                    "nyt" to setOf("nyt-world"),
                    "wapo" to setOf("wapo-world"),
                    "bbc" to setOf("bbc-world"),
                ),
        )

    private fun article(
        id: String,
        feedId: String,
        title: String = "Headline $id",
        summary: String? = null,
        publishedAt: Instant? = now - 1.hours,
    ): Article {
        val feed = catalog.feed(feedId)
        return Article(
            id = id,
            feedId = feedId,
            outletId = feed?.outletId ?: "custom",
            url = "https://example.com/$id",
            title = title,
            summary = summary,
            imageUrl = null,
            publishedAt = publishedAt,
            firstSeenAt = now - 2.hours,
            topics = feed?.topics.orEmpty(),
        )
    }

    private fun ctx(
        p: UserPrefs = prefs,
        hidden: Set<String> = emptySet(),
        customFeeds: Set<String> = emptySet(),
    ) = FilterContext(p, catalog, hidden, now, customFeeds)

    @Test
    fun sevenDayWindowBoundaries() {
        val c = ctx()
        assertTrue(FeedFilter.eligible(article("a", "npr-world", publishedAt = now - 7.days), c))
        val stale = article("b", "npr-world", publishedAt = now - 7.days - 1.minutes)
        assertEquals(ExclusionReason.TOO_OLD, FeedFilter.reason(stale, c))
        assertTrue(FeedFilter.eligible(article("c", "npr-world", publishedAt = now + 3.days), c))
        assertTrue(FeedFilter.eligible(article("d", "npr-world", publishedAt = null), c))
    }

    @Test
    fun hiddenArticleIsExcluded() {
        val a = article("a", "npr-world")
        assertEquals(ExclusionReason.HIDDEN, FeedFilter.reason(a, ctx(hidden = setOf("a"))))
        assertTrue(FeedFilter.eligible(a, ctx(hidden = setOf("other"))))
    }

    @Test
    fun removedOutletExcludesEverySectionFeed() {
        val c = ctx(prefs.removeOutlet("npr", now))
        val all = listOf("npr-world", "npr-politics", "npr-tech").mapIndexed { i, f -> article("n$i", f) }
        assertEquals(0, all.count { FeedFilter.eligible(it, c) })
        assertTrue(all.all { FeedFilter.reason(it, c) == ExclusionReason.OUTLET_REMOVED })
    }

    @Test
    fun unfollowedSectionAndUnknownFeedAreExcluded() {
        val c = ctx()
        assertEquals(ExclusionReason.FEED_NOT_FOLLOWED, FeedFilter.reason(article("a", "npr-tech"), c))
        assertEquals(ExclusionReason.FEED_NOT_FOLLOWED, FeedFilter.reason(article("b", "nowhere"), c))
        val p = prefs.copy(followedOutlets = prefs.followedOutlets + ("npr" to setOf("npr-world", "npr-tech")))
        assertEquals(ExclusionReason.TOPIC_NOT_FOLLOWED, FeedFilter.reason(article("c", "npr-tech"), ctx(p)))
    }

    @Test
    fun customTopicMatchesAcrossUnfollowedFeedsAndIsTagged() {
        val c = ctx(prefs.copy(customTopics = listOf("Rust")))
        val a = article("a", "npr-tech", title = "Why Rust's borrow checker wins")
        assertTrue(FeedFilter.eligible(a, c))
        assertEquals(listOf("tech", "custom:Rust"), FeedFilter.withCustomTopics(a, c).topics)
        assertFalse(FeedFilter.eligible(article("b", "npr-tech", title = "Rusty pipes"), c))
    }

    @Test
    fun customFeedArticlesAreFollowedButStillMuted() {
        val p = prefs.muteKeyword("crypto", now)
        val c = ctx(p, customFeeds = setOf("my-blog"))
        assertTrue(FeedFilter.eligible(article("a", "my-blog"), c))
        assertEquals(
            ExclusionReason.KEYWORD_MUTED,
            FeedFilter.reason(article("b", "my-blog", title = "Crypto news"), c),
        )
    }

    @Test
    fun mutedTopicGivesZeroEligible() {
        val c = ctx(prefs.muteTopic("politics", now))
        val politics = listOf(article("a", "npr-politics"), article("b", "npr-politics", title = "Other"))
        assertEquals(0, politics.count { FeedFilter.eligible(it, c) })
        assertEquals(ExclusionReason.TOPIC_MUTED, FeedFilter.reason(politics[0], c))
        assertTrue(FeedFilter.eligible(article("c", "npr-world"), c))
    }

    @Test
    fun mutedCustomTopicIsExcluded() {
        val c = ctx(prefs.copy(customTopics = listOf("rust")).muteTopic("custom:rust", now))
        assertEquals(ExclusionReason.TOPIC_MUTED, FeedFilter.reason(article("a", "npr-world", title = "Rust 2.0"), c))
    }

    @Test
    fun mutedKeywordGivesZeroEligibleInHeadlineOrSummary() {
        val c = ctx(prefs.muteKeyword("Trump", now))
        val hits =
            listOf(
                article("a", "npr-world", title = "Trump’s plan"),
                article("b", "npr-world", summary = "Donald Trump said"),
                article("c", "npr-politics", title = "Trumps rally"),
            )
        assertEquals(0, hits.count { FeedFilter.eligible(it, c) })
        assertTrue(FeedFilter.eligible(article("d", "npr-world", title = "Trumpet recital"), c))
    }

    @Test
    fun paywallFilterOnExcludesHardAndFlagged() {
        val p = prefs.flagPaywall("bbc", now)
        val byOutlet =
            mapOf(
                PaywallStatus.HARD to article("h", "nyt-world"),
                PaywallStatus.METERED to article("m", "wapo-world"),
                PaywallStatus.NONE to article("n", "npr-world"),
            )
        val flagged = article("f", "bbc-world")
        val on = ctx(p)
        assertEquals(ExclusionReason.PAYWALLED, FeedFilter.reason(byOutlet.getValue(PaywallStatus.HARD), on))
        assertEquals(ExclusionReason.PAYWALLED, FeedFilter.reason(flagged, on))
        assertTrue(FeedFilter.eligible(byOutlet.getValue(PaywallStatus.METERED), on))
        assertTrue(FeedFilter.eligible(byOutlet.getValue(PaywallStatus.NONE), on))

        val off = ctx(p.copy(paywallFilterOn = false))
        assertTrue((byOutlet.values + flagged).all { FeedFilter.eligible(it, off) })
    }

    @Test
    fun mutesWinOverMuchMore() {
        val boosted = prefs.setLevel("world", Level.MUCH_MORE)
        val keyword = ctx(boosted.muteKeyword("election", now))
        assertEquals(
            ExclusionReason.KEYWORD_MUTED,
            FeedFilter.reason(article("a", "npr-world", title = "Election night"), keyword),
        )
        val removed = ctx(boosted.removeOutlet("npr", now))
        assertEquals(ExclusionReason.OUTLET_REMOVED, FeedFilter.reason(article("b", "npr-world"), removed))
        assertTrue(FeedFilter.eligible(article("c", "npr-world"), ctx(boosted)))
    }

    @Test
    fun localArticlesNeedLocalFollowedAndTheUsersMetro() {
        val seattle = article("s", "kiro-local")
        val chicago = article("c", "wgn-local")
        val local = prefs.copy(followedTopics = prefs.followedTopics + "local", localMetroId = "seattle")
        assertNull(FeedFilter.reason(seattle, ctx(local)))
        assertEquals(ExclusionReason.FEED_NOT_FOLLOWED, FeedFilter.reason(chicago, ctx(local)))
        assertFalse(FeedFilter.eligible(seattle, ctx(local.copy(followedTopics = listOf("world")))))
        assertFalse(FeedFilter.eligible(seattle, ctx(local.copy(localMetroId = "chicago"))))
        assertFalse(FeedFilter.eligible(seattle, ctx(prefs.copy(localMetroId = "seattle"))))
        val custom = ctx(local.copy(customTopics = listOf("Headline")))
        assertFalse(FeedFilter.eligible(chicago, custom))
    }
}
