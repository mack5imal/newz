package app.newz.engine

import app.newz.store.ArticleIds
import app.newz.store.StoredArticle
import app.newz.store.muteKeyword
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RefreshCoordinatorTest {
    private fun EngineHarness.stored(
        outlet: String,
        item: TestItem,
    ) = StoredArticle(
        id = ArticleIds.of(outlet, item.guid, item.url),
        feedId = "$outlet-world",
        outletId = outlet,
        guid = item.guid,
        url = item.url,
        title = item.title,
        summary = null,
        imageUrl = null,
        publishedAt = now - item.age,
        firstSeenAt = now,
        topics = listOf("world"),
    )

    /** Starts the first load, holds every request at the gate until all [feeds] requests arrived. */
    private suspend fun EngineHarness.startHeldLoad(
        repo: FeedRepository,
        feeds: Int = 3,
    ): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        val arrivedAll = CompletableDeferred<Unit>()
        this.gate = gate
        arrived = feeds to arrivedAll
        assertEquals(FeedState.Loading(firstLoad = true), repo.feed.first())
        arrivedAll.await()
        return gate
    }

    @Test
    fun rapidRefreshesDuringOneLoadJoinIt() =
        runTest {
            val h = EngineHarness()
            listOf("a", "b", "c").forEachIndexed { i, f ->
                h.items[f] =
                    listOf(TestItem("$f-1", EngineHarness.uniqueTitle(i)))
            }
            val repo = h.repository(backgroundScope)
            val gate = h.startHeldLoad(repo)

            val jobs = List(5) { repo.refresh() }
            assertTrue(jobs.all { it === jobs.first() }, "every refresh joins the running load")
            gate.complete(Unit)
            jobs.forEach { it.join() }

            assertEquals(listOf(1, 1, 1), listOf("a", "b", "c").map(h::requestsTo))
            assertEquals(3, assertIs<FeedState.Content>(repo.feed.first()).cards.size)

            val next = repo.refresh()
            assertFalse(next === jobs.first())
            next.join()
            assertEquals(listOf(2, 2, 2), listOf("a", "b", "c").map(h::requestsTo))
        }

    @Test
    fun keywordMutedDuringALoadIsExcludedFromItsContent() =
        runTest {
            val h = EngineHarness()
            h.items["a"] =
                listOf(TestItem("a-1", "Zebra escapes from city zoo"), TestItem("a-2", EngineHarness.uniqueTitle(1)))
            h.items["b"] = listOf(TestItem("b-1", EngineHarness.uniqueTitle(2)))
            h.items["c"] = listOf(TestItem("c-1", EngineHarness.uniqueTitle(3)))
            val repo = h.repository(backgroundScope)
            val gate = h.startHeldLoad(repo)

            h.prefs.value = h.prefs.value.muteKeyword("zebra", h.now)
            gate.complete(Unit)
            repo.refresh().join()

            val content = assertIs<FeedState.Content>(repo.feed.first())
            val titles = content.cards.map { it.group.lead.title }
            assertEquals(3, titles.size)
            assertTrue(titles.none { it.contains("Zebra") }, "$titles")
        }

    @Test
    fun hideImportHistoryToggleAndSaveDuringALoadAreAllReflected() =
        runTest {
            val h = EngineHarness()
            val storyA = TestItem("a-story", "Springfield mayor Quimby wins tight election recount", age = 30.minutes)
            val storyB = TestItem("b-story", "Springfield mayor Quimby wins tight election recount vote", age = 2.hours)
            val hideMe = TestItem("a-hide", EngineHarness.uniqueTitle(1))
            val saveMe = TestItem("b-save", EngineHarness.uniqueTitle(2))
            val cOnly = TestItem("c-1", EngineHarness.uniqueTitle(3))
            h.items["a"] = listOf(storyA, hideMe)
            h.items["b"] = listOf(storyB, saveMe)
            h.items["c"] = listOf(cOnly)
            // History favours outlet b, so while history is on b's version leads the story.
            repeat(3) { h.store.recordOpen("old-$it", "b", listOf("world"), h.now - 1.hours) }
            val repo = h.repository(backgroundScope)
            val gate = h.startHeldLoad(repo)

            h.store.hide(h.stored("a", hideMe), h.now)
            h.store.save(h.stored("b", saveMe), h.now)
            // Import: a restored backup replaces the preferences, dropping outlet c and history.
            h.prefs.value =
                h.prefs.value.copy(
                    followedOutlets = mapOf("a" to setOf("a-world"), "b" to setOf("b-world")),
                    readingHistoryOn = false,
                )
            gate.complete(Unit)
            repo.refresh().join()

            val cards = assertIs<FeedState.Content>(repo.feed.first()).cards
            val leads = cards.map { it.group.lead.url }
            assertEquals(2, cards.size, "$leads")
            assertFalse(hideMe.url in leads, "hidden article excluded")
            assertFalse(cOnly.url in leads, "unfollowed outlet excluded")
            val story = cards.single { it.group.others.isNotEmpty() }
            assertEquals(storyA.url, story.group.lead.url, "history off: newest version leads")
            assertTrue(cards.single { it.group.lead.url == saveMe.url }.saved, "saved flag applied")
            assertFalse(story.saved)
        }

    @Test
    fun historyOnPicksTheMostReadOutletAsLead() =
        runTest {
            val h = EngineHarness()
            h.items["a"] =
                listOf(TestItem("a-story", "Springfield mayor Quimby wins tight election recount", 30.minutes))
            h.items["b"] =
                listOf(TestItem("b-story", "Springfield mayor Quimby wins tight election recount vote", 2.hours))
            repeat(3) { h.store.recordOpen("old-$it", "b", listOf("world"), h.now - 1.hours) }
            val repo = h.repository(backgroundScope)
            repo.feed.first()
            repo.refresh().join()
            val story = assertIs<FeedState.Content>(repo.feed.first()).cards.single()
            assertEquals("b", story.group.lead.outletId)
            assertEquals(1, story.group.othersCount)
        }

    @Test
    fun loadPrunesArticlesOlderThanSevenDays() =
        runTest {
            val h = EngineHarness()
            h.items["a"] = listOf(TestItem("a-old", EngineHarness.uniqueTitle(1), age = 200.hours))
            h.items["b"] = listOf(TestItem("b-new", EngineHarness.uniqueTitle(2)))
            val repo = h.repository(backgroundScope)
            repo.feed.first()
            repo.refresh().join()
            assertEquals(listOf("b"), h.store.articles().map { it.outletId })
        }
}
