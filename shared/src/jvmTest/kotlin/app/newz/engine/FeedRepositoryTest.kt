package app.newz.engine

import app.newz.feed.ParsedItem
import app.newz.store.muteTopic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

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
    fun topicFilterNarrowsTheCards() =
        runTest {
            val h = EngineHarness()
            h.online = false
            h.cache("a", 3)
            val tech =
                ParsedItem("t1", "https://b.example/t1", EngineHarness.uniqueTitle(77), null, null, h.now - 1.hours)
            h.store.upsert("b-world", "b", listOf("world", "tech"), listOf(tech), h.now)
            val repo = h.repository(backgroundScope)
            assertEquals(4, assertIs<FeedState.Content>(repo.feed.first()).cards.size)
            repo.setTopicFilter(setOf("tech")).join()
            assertEquals(
                listOf(tech.url),
                assertIs<FeedState.Content>(repo.feed.first()).cards.map { it.group.lead.url },
            )
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
}
