package app.newz.engine

import app.newz.feed.ParsedItem
import app.newz.net.newzHttpClient
import app.newz.store.CustomFeed
import app.newz.store.UserPrefs
import app.newz.store.muteKeyword
import app.newz.store.muteTopic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class FeedRepositoryTest {
    private fun EngineHarness.cache(
        outlet: String,
        count: Int,
        offset: Int = 0,
    ) {
        val parsed =
            (0 until count).map { i ->
                val n = offset + i
                ParsedItem(
                    "$outlet-$n",
                    "https://$outlet.example/$n",
                    EngineHarness.uniqueTitle(n),
                    null,
                    null,
                    now - (i + 1).hours,
                )
            }
        store.upsert("$outlet-world", outlet, listOf("world"), parsed, now)
    }

    @Test
    fun cachedCardsShowFirstWhenOffline() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cache("a", 3)
            h.cache("b", 2, offset = 10)
            val repo = h.repository(backgroundScope)
            val first = assertIs<FeedState.Content>(repo.feed.first())
            assertTrue(first.offline)
            assertEquals(5, first.cards.size)
            repo.refresh().join()
            assertEquals(0, h.requests.size)
            assertEquals(first, repo.feed.first())
        }

    @Test
    fun noCacheAndOfflineIsEmptyNoCacheOffline() =
        runTest {
            val h = EngineHarness()
            h.online = false
            val repo = h.repository(backgroundScope)
            assertEquals(FeedState.Empty(EmptyReason.NO_CACHE_OFFLINE), repo.feed.first())
            repo.refresh().join()
            assertEquals(FeedState.Empty(EmptyReason.NO_CACHE_OFFLINE), repo.feed.first())
        }

    @Test
    fun everyArticleFilteredOutIsEmptyAllFiltered() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cache("a", 4)
            h.prefs.value = h.prefs.value.muteTopic("world", h.now)
            val repo = h.repository(backgroundScope)
            assertEquals(FeedState.Empty(EmptyReason.ALL_FILTERED), repo.feed.first())
        }

    @Test
    fun cachedCardsShowFirstThenTheRefreshMergesNewOnes() =
        runTest {
            val h = EngineHarness()
            h.cache("a", 2)
            h.items["a"] = listOf(TestItem("fresh", EngineHarness.uniqueTitle(500), age = 0.hours))
            val repo = h.repository(backgroundScope)
            val first = assertIs<FeedState.Content>(repo.feed.first())
            assertFalse(first.offline)
            repo.refresh().join()
            val after = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(3, after.cards.size)
            assertEquals(first.cards, after.cards.take(2))
            assertEquals(1, h.requestsTo("a"))
        }

    @Test
    fun loadMorePagesToTheEnd() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cache("a", 15)
            h.cache("b", 15, offset = 100)
            h.cache("c", 15, offset = 200)
            val repo = h.repository(backgroundScope)
            val first = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(20, first.cards.size)
            assertFalse(first.endReached)
            repo.loadMore().join()
            assertEquals(40, assertIs<FeedState.Content>(repo.feed.first()).cards.size)
            repo.loadMore().join()
            val last = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(45, last.cards.size)
            assertTrue(last.endReached)
            assertEquals(
                45,
                last.cards
                    .map { it.group.lead.id }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun aFeedLandingAfterLoadMoreKeepsTheServedCards() =
        runTest {
            val h = EngineHarness(listOf("a", "b"))
            h.items["a"] = (0 until 12).map { TestItem("a$it", EngineHarness.uniqueTitle(it), age = (it + 1).hours) }
            h.items["b"] = (0 until 6).map { TestItem("b$it", EngineHarness.uniqueTitle(100 + it), age = 30.minutes) }
            val slowB = CompletableDeferred<Unit>()
            h.feedGates["b"] = slowB
            val repo = h.repository(backgroundScope, pageSize = 5)
            val firstPage = assertIs<FeedState.Content>(repo.feed.first { it is FeedState.Content })
            assertEquals(5, firstPage.cards.size)
            repo.loadMore().join()
            val paged = assertIs<FeedState.Content>(repo.feed.first()).cards.map { it.group.lead.id }
            assertEquals(10, paged.size)
            slowB.complete(Unit)
            repo.refresh().join()
            val after = assertIs<FeedState.Content>(repo.feed.first()).cards.map { it.group.lead.id }
            assertTrue(after.size >= paged.size, "served cards shrank from ${paged.size} to ${after.size}")
            assertEquals(paged, after.take(paged.size))
            assertEquals(1, h.requestsTo("b"))
        }

    @Test
    fun recordOpenIsGatedByReadingHistory() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cache("a", 1)
            val repo = h.repository(backgroundScope)
            val article =
                assertIs<FeedState.Content>(repo.feed.first())
                    .cards
                    .single()
                    .group.lead
            repo.recordOpen(article).join()
            assertEquals(listOf("a"), h.store.listHistory().map { it.outletId })
            h.prefs.value = h.prefs.value.copy(readingHistoryOn = false)
            repo.recordOpen(article).join()
            assertEquals(1, h.store.listHistory().size)
        }

    @Test
    fun everyFeedFailingWithCacheIsContentOffline() =
        runTest {
            val h = EngineHarness()
            h.failing += listOf("a", "b", "c")
            h.cache("a", 2)
            val repo = h.repository(backgroundScope)
            repo.feed.first()
            repo.refresh().join()
            val content = assertIs<FeedState.Content>(repo.feed.first())
            assertTrue(content.offline)
            assertEquals(2, content.cards.size)
            assertEquals(setOf("a-world", "b-world", "c-world"), content.failingFeeds)
            assertEquals(listOf(1, 1, 1), listOf("a", "b", "c").map(h::requestsTo))
        }

    @Test
    fun everyFeedFailingWithNoCacheIsEmptyNoCacheOffline() =
        runTest {
            val h = EngineHarness()
            h.failing += listOf("a", "b", "c")
            val repo = h.repository(backgroundScope)
            repo.feed.first()
            repo.refresh().join()
            assertEquals(FeedState.Empty(EmptyReason.NO_CACHE_OFFLINE), repo.feed.first())
        }

    private fun EngineHarness.cacheStale(count: Int) {
        val parsed =
            (0 until count).map { i ->
                ParsedItem(
                    "old-$i",
                    "https://a.example/old-$i",
                    EngineHarness.uniqueTitle(900 + i),
                    null,
                    null,
                    now - 8.days,
                )
            }
        store.upsert("a-world", "a", listOf("world"), parsed, now)
    }

    @Test
    fun staleOnlyCacheWhileOnlineLoadRunsIsLoading() =
        runTest {
            val h = EngineHarness()
            h.cacheStale(3)
            val gate = CompletableDeferred<Unit>()
            h.gate = gate
            val repo = h.repository(backgroundScope)
            assertEquals(FeedState.Loading(firstLoad = true), repo.feed.first())
            gate.complete(Unit)
            repo.refresh().join()
        }

    @Test
    fun staleOnlyCacheOfflineIsEmptyNoCacheOffline() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cacheStale(3)
            val repo = h.repository(backgroundScope)
            assertEquals(FeedState.Empty(EmptyReason.NO_CACHE_OFFLINE), repo.feed.first())
        }

    @Test
    fun aCancelledFirstCollectorDoesNotAbortTheStart() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cache("a", 2)
            val reached = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()
            val heldPrefs =
                flow {
                    reached.complete(Unit)
                    hold.await()
                    emitAll(h.prefs)
                }
            val repo =
                FeedRepository(
                    scope = backgroundScope,
                    store = h.store,
                    prefs = heldPrefs,
                    catalog = h.catalog,
                    client = newzHttpClient(h.engine),
                    isOnline = { h.online },
                    clock = { h.now },
                )
            val firstCollector = launch { repo.feed.collect { } }
            reached.await()
            firstCollector.cancelAndJoin()
            hold.complete(Unit)
            val state = repo.feed.first { it !is FeedState.Loading }
            assertEquals(2, assertIs<FeedState.Content>(state).cards.size)
        }

    @Test
    fun prefsAndStoreChangesRecomposeWithoutFetching() =
        runTest {
            val h = EngineHarness()
            h.items["a"] =
                listOf(TestItem("a-1", "Zebra escapes from city zoo"), TestItem("a-2", EngineHarness.uniqueTitle(1)))
            h.items["b"] =
                listOf(TestItem("b-1", EngineHarness.uniqueTitle(2)), TestItem("b-2", EngineHarness.uniqueTitle(4)))
            h.items["c"] = listOf(TestItem("c-1", EngineHarness.uniqueTitle(3)))
            val repo = h.repository(backgroundScope)
            repo.feed.first()
            repo.refresh().join()
            val before = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(5, before.cards.size)
            val requests = h.requests.values.sumOf { it.get() }

            h.prefs.value = h.prefs.value.muteKeyword("zebra", h.now)
            val muted =
                repo.feed.first { s ->
                    s is FeedState.Content &&
                        s.cards.none {
                            it.group.lead.title
                                .contains("Zebra")
                        }
                } as FeedState.Content
            val mutedIds = muted.cards.map { it.group.lead.id }
            val expected =
                before.cards
                    .map { it.group.lead }
                    .filterNot { it.title.contains("Zebra") }
                    .map { it.id }
            assertEquals(4, expected.size)
            assertEquals(expected, mutedIds)
            assertEquals(requests, h.requests.values.sumOf { it.get() })

            val target = mutedIds[1]
            h.store.hide(requireNotNull(h.store.article(target)), h.now)
            repo.invalidate().join()
            val after = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(mutedIds - target, after.cards.map { it.group.lead.id })
            assertEquals(requests, h.requests.values.sumOf { it.get() })
        }

    /**
     * B17: a first load of 100 feeds (50 articles each, landing over a few seconds) recomposes at
     * most once per [FeedRepository.GROWING_PUBLISH_INTERVAL] plus a trailing one, not once per
     * feed (which was 102 composes of the whole cache). Cards still show while feeds land, and the
     * finished load publishes every feed. Real time: the coalescing waits on the clock.
     */
    @Test
    fun aGrowingFirstLoadOfManyFeedsRecomposesAFewTimesNotOncePerFeed() =
        runBlocking {
            val ids = List(100) { "f$it" }
            val h = EngineHarness(ids)
            val random = Random(17)
            val words = WORDS.split(' ')
            ids.forEachIndexed { i, id ->
                h.items[id] =
                    (0 until 50).map { j ->
                        val title = List(random.nextInt(5, 10)) { words.random(random) }.joinToString(" ")
                        TestItem("$id-$j", title, age = ((i + j * 3) % 160 + 1).hours)
                    }
                h.feedDelays[id] = 100L + (i * 37) % 300
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val started = TimeSource.Monotonic.markNow()
                val repo = h.repository(scope)
                assertIs<FeedState.Content>(repo.feed.first { it is FeedState.Content })
                assertTrue(repo.isLoading.value, "cards show while feeds are still landing")
                repo.refresh().join()
                val elapsed = started.elapsedNow()
                val done = assertIs<FeedState.Content>(repo.feed.first())
                assertEquals(FeedPager.DEFAULT_PAGE_SIZE, done.cards.size)
                assertEquals(100 * 50, h.store.articles().size)
                assertEquals(100, ids.sumOf(h::requestsTo))
                val composes = repo.composes
                // The start, one per interval while growing, a trailing one, and the finished load.
                val bound = 3 + (elapsed / FeedRepository.GROWING_PUBLISH_INTERVAL).toInt() + 1
                println("growing first load: $composes composes in $elapsed (bound $bound)")
                assertTrue(composes <= bound, "$composes composes in $elapsed, bound $bound")
            } finally {
                scope.cancel()
            }
        }

    private companion object {
        const val WORDS =
            "election vote court ruling judge strike union wage inflation rates market stocks bonds oil " +
                "gas storm hurricane flood fire quake missile drone attack ceasefire talks summit deal " +
                "tariff trade chip launch rocket satellite vaccine virus outbreak hospital school police " +
                "arrest trial verdict appeal senate bill budget shutdown merger layoffs earnings profit " +
                "record crash flight airport rail bridge outage heat drought prices housing rent bank " +
                "Biden Trump Harris Putin Zelensky Macron Modi Musk Nvidia Apple Tesla Boeing Kyiv Gaza " +
                "Taiwan Texas Florida Paris Berlin Tokyo Beijing Delhi Lagos Cairo Seoul Mexico Canada"
    }
}
