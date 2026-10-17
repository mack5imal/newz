package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.net.newzHttpClient
import app.newz.store.CustomFeed
import app.newz.store.UserPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class FeedFetcherTest {
    private fun EngineHarness.serveTwoEach() {
        items["a"] = listOf(TestItem("a1", EngineHarness.uniqueTitle(1)), TestItem("a2", EngineHarness.uniqueTitle(2)))
        items["b"] = listOf(TestItem("b1", EngineHarness.uniqueTitle(3)), TestItem("b2", EngineHarness.uniqueTitle(4)))
        items["c"] = listOf(TestItem("c1", EngineHarness.uniqueTitle(5)), TestItem("c2", EngineHarness.uniqueTitle(6)))
    }

    @Test
    fun oneFailingFeedIsSkippedAndMarkedThenClearedOnSuccess() =
        runTest {
            val h = EngineHarness()
            h.serveTwoEach()
            h.failing += "b"
            val repo = h.repository(backgroundScope)
            assertEquals(FeedState.Loading(firstLoad = true), repo.feed.first())
            repo.refresh().join()

            val content = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(setOf("a", "c"), content.cards.map { it.group.lead.outletId }.toSet())
            assertEquals(4, content.cards.size)
            assertEquals(setOf("b-world"), content.failingFeeds)
            val failed = assertNotNull(h.store.feedStatus("b-world"))
            assertNotNull(failed.failingSince)
            assertEquals("HTTP 500", failed.lastError)
            assertNull(h.store.feedStatus("a-world")?.failingSince)
            assertEquals(listOf(1, 1, 1), listOf("a", "b", "c").map(h::requestsTo))

            h.failing.clear()
            repo.refresh().join()
            assertNull(h.store.feedStatus("b-world")?.failingSince)
            val after = assertIs<FeedState.Content>(repo.feed.first())
            assertEquals(emptySet(), after.failingFeeds)
            assertEquals(6, after.cards.size)
        }

    @Test
    fun failingSinceKeepsTheFirstFailure() =
        runTest {
            val h = EngineHarness()
            h.serveTwoEach()
            h.failing += "b"
            val fetcher = FeedFetcher(newzHttpClient(h.engine), h.store, { true }, Dispatchers.IO, { h.now })
            val sources = FeedFetcher.followedSources(h.prefs.value, h.catalog, emptyList())
            fetcher.fetch(sources).toList()
            val first = h.store.feedStatus("b-world")?.failingSince
            assertNotNull(first)
            val later = FeedFetcher(newzHttpClient(h.engine), h.store, { true }, Dispatchers.IO, { h.now + 1.hours })
            later.fetch(sources).toList()
            assertEquals(first, h.store.feedStatus("b-world")?.failingSince)
        }

    @Test
    fun progressIsEmittedPerFeedAsEachCompletes() =
        runTest {
            val h = EngineHarness()
            h.serveTwoEach()
            h.failing += "c"
            val fetcher = FeedFetcher(newzHttpClient(h.engine), h.store, { true }, Dispatchers.IO, { h.now })
            val progress = fetcher.fetch(FeedFetcher.followedSources(h.prefs.value, h.catalog, emptyList())).toList()
            assertEquals(listOf(1, 2, 3), progress.map { it.completed })
            assertTrue(progress.all { it.total == 3 })
            val outcomes =
                progress.filterIsInstance<FetchProgress.FeedDone>().associate {
                    it.source.feedId to
                        it.outcome
                }
            assertEquals(FeedOutcome.Ok(2), outcomes["a-world"])
            assertIs<FeedOutcome.Failed>(outcomes["c-world"])
        }

    @Test
    fun offlineFailsFastWithoutRequests() =
        runTest {
            val h = EngineHarness()
            h.serveTwoEach()
            val fetcher = FeedFetcher(newzHttpClient(h.engine), h.store, { false }, Dispatchers.IO, { h.now })
            val progress = fetcher.fetch(FeedFetcher.followedSources(h.prefs.value, h.catalog, emptyList())).toList()
            assertEquals(listOf<FetchProgress>(FetchProgress.Offline(3)), progress)
            assertEquals(0, h.requests.size)
            assertNull(h.store.feedStatus("a-world"))
        }

    @Test
    fun atMostEightFeedsAreFetchedAtOnce() =
        runTest {
            val ids = (1..20).map { "f$it" }
            val h = EngineHarness(ids)
            ids.forEachIndexed { i, id -> h.items[id] = listOf(TestItem("$id-1", EngineHarness.uniqueTitle(i))) }
            val gate = CompletableDeferred<Unit>()
            val eight = CompletableDeferred<Unit>()
            h.gate = gate
            h.arrived = 8 to eight
            val fetcher = FeedFetcher(newzHttpClient(h.engine), h.store, { true }, Dispatchers.IO, { h.now })
            val sources = FeedFetcher.followedSources(h.prefs.value, h.catalog, emptyList())
            assertEquals(20, sources.size)
            val job = launch { fetcher.fetch(sources).toList() }
            eight.await()
            assertEquals(8, h.requests.values.sumOf { it.get() })
            gate.complete(Unit)
            job.join()
            assertTrue(h.maxInFlight.get() <= 8, "max in flight ${h.maxInFlight.get()}")
            assertEquals(20, h.store.articles().size)
            assertTrue(ids.all { h.requestsTo(it) == 1 })
        }

    @Test
    fun followedSourcesCoverCatalogLocalAndCustomFeedsButNotRemovedOutlets() {
        val h = EngineHarness()
        val prefs = h.prefs.value.copy(removedOutlets = mapOf("c" to h.now))
        val custom = CustomFeed("custom-1", "https://blog.example/rss", "Blog", null, "RSS", h.now)
        val sources = FeedFetcher.followedSources(prefs, h.catalog, listOf(custom))
        assertEquals(listOf("a-world", "b-world", "custom-1"), sources.map { it.feedId })
        assertEquals(FeedSource("custom-1", "custom-1", "https://blog.example/rss", emptyList()), sources.last())
    }

    @Test
    fun localFeedsOfTheUsersMetroAreFetchedOnlyWhenLocalIsFollowed() {
        val catalog =
            Catalog(
                topics = emptyList(),
                outlets = emptyList(),
                feeds =
                    listOf(
                        CatalogFeed(
                            "kiro",
                            "kiro",
                            "Local",
                            "https://kiro.example/rss",
                            listOf("local"),
                            metroId = "sea",
                        ),
                        CatalogFeed("wgn", "wgn", "Local", "https://wgn.example/rss", listOf("local"), metroId = "chi"),
                    ),
            )
        val prefs = UserPrefs(followedTopics = listOf("local"), localMetroId = "sea")
        assertEquals(listOf("kiro"), FeedFetcher.followedSources(prefs, catalog, emptyList()).map { it.feedId })
        val notFollowed = prefs.copy(followedTopics = emptyList())
        assertEquals(emptyList(), FeedFetcher.followedSources(notFollowed, catalog, emptyList()))
    }
}
