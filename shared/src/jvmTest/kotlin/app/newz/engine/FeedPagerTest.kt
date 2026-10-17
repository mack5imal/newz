package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.dedup.StoryGroup
import app.newz.feed.ParsedItem
import app.newz.store.ArticleStore
import app.newz.store.UserPrefs
import app.newz.store.createSqlDriver
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FeedPagerTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val topics = listOf("world", "tech", "sports", "science")
    private val outlets = (0 until 24).map { "o$it" }
    private val catalog =
        Catalog(
            topics = emptyList(),
            outlets = outlets.map { Outlet(it, it, "$it.example", PaywallStatus.NONE, 1.0) },
            feeds =
                outlets.flatMap { o ->
                    topics.map { t -> CatalogFeed("$o-$t", o, t, "https://$o.example/$t.xml", listOf(t)) }
                },
        )
    private val prefs =
        UserPrefs(
            followedTopics = topics,
            followedOutlets = outlets.associateWith { o -> topics.map { "$o-$it" }.toSet() },
        )
    private val store = ArticleStore(createSqlDriver())

    private data class Fixture(
        val feedId: String,
        val outletId: String,
        val topic: String,
        val item: ParsedItem,
    )

    /** 250 articles from the last 6 days plus 20 from 8-10 days ago, in random order. */
    private fun fixture(): List<Fixture> {
        val random = Random(42)
        return (0 until 270)
            .map { i ->
                val outlet = outlets[random.nextInt(outlets.size)]
                val topic = topics[random.nextInt(topics.size)]
                val age =
                    if (i < 250) {
                        random.nextLong(6.days.inWholeMinutes).minutes
                    } else {
                        (8.days.inWholeMinutes + random.nextLong(2.days.inWholeMinutes)).minutes
                    }
                val item =
                    ParsedItem(
                        "g$i",
                        "https://$outlet.example/a/$i",
                        EngineHarness.uniqueTitle(i),
                        null,
                        null,
                        now - age,
                    )
                Fixture("$outlet-$topic", outlet, topic, item)
            }.shuffled(random)
    }

    private fun insert(list: List<Fixture>) {
        for (f in list) store.upsert(f.feedId, f.outletId, listOf(f.topic), listOf(f.item), now)
    }

    private fun ranked(): List<StoryGroup> =
        FeedComposer.compose(FeedSnapshot.read(store, prefs), catalog, emptySet(), now)

    private fun FeedPager.drain(): List<Page> {
        val pages = mutableListOf<Page>()
        while (!endReached) pages += next()
        return pages
    }

    @Test
    fun servesExactlyTheEligibleArticlesOnceThenEnds() {
        val fixture = fixture()
        insert(fixture)
        val eligibleUrls = fixture.filter { it.item.publishedAt!! >= now - 7.days }.map { it.item.url }.toSet()
        assertEquals(250, eligibleUrls.size)

        val pager = FeedPager()
        pager.reset(ranked())
        assertFalse(pager.endReached)
        val pages = pager.drain()
        val served = pages.flatMap { it.groups }.flatMap { it.members() }.map { it.url }
        assertEquals(250, served.size)
        assertEquals(eligibleUrls, served.toSet())
        assertTrue(pager.endReached)
        assertTrue(pages.last().endReached)
        assertEquals(13, pages.size)
        assertTrue(pages.dropLast(1).all { it.groups.size == 20 && !it.endReached })
        assertEquals(emptyList(), pager.next().groups)
    }

    @Test
    fun outOfOrderDatesComeOutNewestFirstWithinEachTopicQueue() {
        insert(fixture())
        val ranked = ranked()
        for (topic in topics) {
            val dates = ranked.filter { it.lead.primaryTopic == topic }.map { it.lead.date }
            assertEquals(dates.sortedDescending(), dates, "topic $topic")
        }
    }

    @Test
    fun articleReSeenOnRefreshAppearsOnce() {
        val fixture = fixture()
        insert(fixture)
        val pager = FeedPager()
        pager.reset(ranked())
        val first = pager.next().groups
        // Refresh: every item is seen again (same guid), and one served article comes back under
        // a new guid but the same url.
        insert(fixture)
        val servedLead = first.first().lead
        val sameUrl = fixture.first { it.item.url == servedLead.url }
        store.upsert(
            sameUrl.feedId,
            sameUrl.outletId,
            listOf(sameUrl.topic),
            listOf(sameUrl.item.copy(guid = "new-guid")),
            now + 1.minutes,
        )
        assertEquals(271, store.articles().size)

        pager.submit(ranked())
        assertEquals(first, pager.servedGroups)
        pager.drain()
        val urls = pager.servedGroups.flatMap { it.members() }.map { it.url }
        val ids = pager.servedGroups.flatMap { it.members() }.map { it.id }
        assertEquals(250, urls.size)
        assertEquals(urls.size, urls.toSet().size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun submitKeepsServedGroupsDropsExcludedOnesAndQueuesNewOnes() {
        val fixture = fixture().take(60)
        insert(fixture)
        val pager = FeedPager(pageSize = 10)
        pager.reset(ranked())
        val served = pager.next().groups
        val hidden = served[3].lead
        store.hide(store.article(hidden.id)!!, now)
        val fresh = ParsedItem("fresh", "https://o1.example/fresh", EngineHarness.uniqueTitle(999), null, null, now)
        store.upsert("o1-world", "o1", listOf("world"), listOf(fresh), now)

        pager.submit(ranked())
        assertEquals(served - served[3], pager.servedGroups)
        val rest = pager.drain().flatMap { it.groups }
        assertTrue(rest.any { it.lead.url == fresh.url })
        assertTrue(rest.none { it.lead.id == hidden.id })
    }
}
