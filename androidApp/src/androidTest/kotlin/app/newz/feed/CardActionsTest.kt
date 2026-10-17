package app.newz.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.store.Level
import app.newz.store.levelOf
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.feedOf
import app.newz.testing.openCardMenu
import app.newz.testing.servedCards
import app.newz.testing.tapText
import app.newz.testing.visibleCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** The card menu's actions on the feed, each with Undo and kept across refresh and recreation (SPEC S7, S8, S10, S12, S21). */
@RunWith(AndroidJUnit4::class)
class CardActionsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 8).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    private fun start(
        catalog: Catalog = TestCatalog.build(server),
        minCards: Int = 16,
    ) {
        TestApp.install(catalog)
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = minCards)
    }

    private fun titleOf(card: FeedCardInfo): String =
        server.feeds
            .flatMap { it.items }
            .first { it.link == card.url }
            .title

    private fun waitUntilServed(
        message: String,
        condition: (List<FeedCardInfo>) -> Boolean,
    ) {
        compose.waitUntil(TIMEOUT) { condition(compose.servedCards()) }
        assertTrue(message, condition(compose.servedCards()))
    }

    /** Recreates the activity (ViewModel and graph kept, as after a configuration change) and waits for the feed. */
    private fun recreate() {
        scenario!!.recreate()
        compose.waitForStableFeed(minCards = 1)
    }

    private fun cardGone(title: String) =
        compose
            .onAllNodes(hasTestTag(FeedSemantics.CARD_TAG) and hasText(title))
            .fetchSemanticsNodes()
            .isEmpty()

    @Test
    fun hideRemovesTheCardUndoRestoresItAndAHideSurvivesRefreshAndRecreation() {
        server.fill(itemsPerFeed = 3)
        start()
        val served = compose.servedCards()
        val target = compose.visibleCards().first()
        val title = titleOf(target)
        val position = served.indexOfFirst { it.id == target.id }

        compose.openCardMenu(title)
        compose.tapText("Hide this article")
        waitUntilServed("hidden card leaves the feed") { cards -> cards.none { it.id == target.id } }
        compose.waitUntil(TIMEOUT) { cardGone(title) }
        compose.onNodeWithText("Article hidden. Bring it back in Settings › Hidden articles.").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()

        compose.onNodeWithText("Undo").performClick()
        waitUntilServed("Undo brings the card back") { cards -> cards.any { it.id == target.id } }
        assertEquals("restored in its place", position, compose.servedCards().indexOfFirst { it.id == target.id })
        assertTrue(
            AppGraph
                .get()
                .articleStore
                .listHidden()
                .none { it.articleId == target.id },
        )

        compose.openCardMenu(title)
        compose.tapText("Hide this article")
        waitUntilServed("hidden again") { cards -> cards.none { it.id == target.id } }
        runBlocking {
            AppGraph
                .get()
                .feedRepository
                .refresh()
                .join()
        }
        compose.waitForStableFeed(minCards = 1)
        assertTrue("absent after refresh", compose.servedCards().none { it.id == target.id })
        recreate()
        assertTrue("absent after recreation", compose.servedCards().none { it.id == target.id })
        assertTrue(cardGone(title))
        assertTrue(
            AppGraph
                .get()
                .articleStore
                .listHidden()
                .any { it.articleId == target.id },
        )
    }

    @Test
    fun hidingTheLastVisibleCardShowsTheNextCardThenTheEndState() {
        val now = Clock.System.now()
        server.feed(0).items = listOf(server.item(0, 0, now - 1.hours), server.item(0, 1, now - 2.hours))
        start(minCards = 2)
        val (first, second) = compose.servedCards()

        compose.openCardMenu(titleOf(first))
        compose.tapText("Hide this article")
        waitUntilServed("only the next card is left") { cards -> cards.map { it.id } == listOf(second.id) }
        compose.waitUntil(TIMEOUT) { cardGone(titleOf(first)) }
        compose.onNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(titleOf(second))).assertIsDisplayed()
        compose.waitForText(CAUGHT_UP)

        compose.openCardMenu(titleOf(second))
        compose.tapText("Hide this article")
        compose.waitForText(ALL_FILTERED)
        assertTrue(compose.servedCards().isEmpty())
        compose.onNodeWithText(ALL_FILTERED).assertIsDisplayed()
    }

    @Test
    fun dontShowOutletRemovesEverySectionFeedOfThatOutletAndSurvivesRecreation() {
        server.fill(itemsPerFeed = 2)
        // Outlet 0 has two section feeds (server feeds 0 and 1); the rest have one each.
        val catalog =
            Catalog(
                topics = TestCatalog.topics,
                outlets =
                    (0 until server.feedCount).filter { it != 1 }.map {
                        Outlet(
                            TestCatalog.outletId(it),
                            TestCatalog.outletName(it),
                            "outlet$it.test",
                            PaywallStatus.NONE,
                            0.5,
                        )
                    },
                feeds =
                    (0 until server.feedCount).map {
                        val outlet = TestCatalog.outletId(if (it == 1) 0 else it)
                        CatalogFeed(
                            TestCatalog.feedId(it),
                            outlet,
                            "Section $it",
                            server.feedUrl(it),
                            listOf(TestCatalog.topicOf(it)),
                        )
                    },
            )
        start(catalog, minCards = 16)
        val outletFeeds = setOf(0, 1)
        assertTrue(compose.servedCards().count { feedOf(it.url) in outletFeeds } >= 4)
        val target = compose.servedCards().first { feedOf(it.url) == 0 }

        compose.openCardMenu(titleOf(target))
        compose.tapText("Don't show content from Outlet 0")
        waitUntilServed("no card from either section") { cards -> cards.none { feedOf(it.url) in outletFeeds } }
        compose.onNodeWithText("Outlet 0 won’t appear. Bring it back in Settings › Sources.").assertIsDisplayed()
        assertTrue(TestCatalog.outletId(0) in currentPrefs().removedOutlets)

        compose.onNodeWithText("Undo").performClick()
        waitUntilServed("Undo brings both sections back") { cards ->
            cards.any { feedOf(it.url) == 0 } && cards.any { feedOf(it.url) == 1 }
        }
        assertFalse(TestCatalog.outletId(0) in currentPrefs().removedOutlets)

        compose.openCardMenu(titleOf(target))
        compose.tapText("Don't show content from Outlet 0")
        waitUntilServed("removed again") { cards -> cards.none { feedOf(it.url) in outletFeeds } }
        recreate()
        assertTrue("absent after recreation", compose.servedCards().none { feedOf(it.url) in outletFeeds })
        assertTrue(compose.visibleCards().none { feedOf(it.url) in outletFeeds })
    }

    @Test
    fun dontShowTopicRemovesEveryCardInThatTopicAndSurvivesRecreation() {
        server.fill(itemsPerFeed = 3)
        start()
        val target = compose.servedCards().first { TestCatalog.SCIENCE in it.topics }

        compose.openCardMenu(titleOf(target))
        compose.tapText("Don't show Science")
        waitUntilServed("no Science card") { cards -> cards.none { TestCatalog.SCIENCE in it.topics } }
        compose
            .onNodeWithText(
                "Science won’t appear. Bring it back in Settings › Topics & keywords.",
            ).assertIsDisplayed()
        assertTrue(TestCatalog.SCIENCE in currentPrefs().mutedTopics)

        recreate()
        assertTrue("absent after recreation", compose.servedCards().none { TestCatalog.SCIENCE in it.topics })
        assertTrue(compose.servedCards().isNotEmpty())
    }

    @Test
    fun showMoreAndShowLessStepTheTopicWithItsLevelNamedAndUndo() {
        server.fill(itemsPerFeed = 3)
        start()
        val target = compose.servedCards().first { it.topics.firstOrNull() == TestCatalog.SCIENCE }
        assertEquals(Level.NORMAL, currentPrefs().levelOf(TestCatalog.SCIENCE))

        // At Normal: Show more only, no level note.
        compose.openCardMenu(titleOf(target))
        assertTrue(compose.onAllNodesWithText("Show less like this").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("You’re seeing more", substring = true).fetchSemanticsNodes().isEmpty())
        compose.tapText("Show more like this")
        compose.waitUntil(TIMEOUT) { currentPrefs().levelOf(TestCatalog.SCIENCE) == Level.MORE }
        compose.onNodeWithText(MESSAGE_MORE).assertIsDisplayed()
        compose.tapText("Undo")
        compose.waitUntil(TIMEOUT) { TestCatalog.SCIENCE !in currentPrefs().topicLevels }

        compose.openCardMenu(titleOf(target))
        compose.tapText("Show more like this")
        compose.waitUntil(TIMEOUT) { currentPrefs().levelOf(TestCatalog.SCIENCE) == Level.MORE }
        waitUntilNoUndo()

        // Above Normal: the note names the level and what it means, and Show less steps down.
        compose.openCardMenu(titleOf(target))
        compose.onNodeWithText("You’re seeing more Science — More (about 2× as often)").assertIsDisplayed()
        compose.tapText("Show less like this")
        compose.waitUntil(TIMEOUT) { TestCatalog.SCIENCE !in currentPrefs().topicLevels }
        compose.onNodeWithText(MESSAGE_NORMAL).assertIsDisplayed()
        compose.tapText("Undo")
        compose.waitUntil(TIMEOUT) { currentPrefs().levelOf(TestCatalog.SCIENCE) == Level.MORE }

        val prefs = currentPrefs()
        TestPrefs.write(prefs.copy(topicLevels = prefs.topicLevels + (TestCatalog.SCIENCE to Level.MUCH_MORE)))
        waitUntilNoUndo()
        compose.openCardMenu(titleOf(target))
        compose.onNodeWithText("You’re seeing more Science — Much more (about 3× as often)").assertIsDisplayed()
        compose.onNodeWithText("Show more like this").assertIsNotEnabled()
        compose.onNodeWithText("Already showing much more of Science").assertIsDisplayed()
        compose.onNodeWithText("Show more like this").performClick()
        assertEquals(Level.MUCH_MORE, currentPrefs().levelOf(TestCatalog.SCIENCE))
        compose.tapText("Show less like this")
        compose.waitUntil(TIMEOUT) { currentPrefs().levelOf(TestCatalog.SCIENCE) == Level.MORE }
        compose.onNodeWithText(MESSAGE_MORE).assertIsDisplayed()
    }

    /** The Undo snackbar sits over the bottom of the list; let it time out before opening a menu. */
    private fun waitUntilNoUndo() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun markPaywalledRemovesTheOutletWithTheFilterOnAndTheRowHidesOnceFlagged() {
        server.fill(itemsPerFeed = 3)
        start()
        assertTrue(currentPrefs().paywallFilterOn)
        val target = compose.servedCards().first { feedOf(it.url) == 2 }

        compose.openCardMenu(titleOf(target))
        compose.tapText("Mark Outlet 2 as paywalled")
        waitUntilServed("no Outlet 2 card") { cards -> cards.none { feedOf(it.url) == 2 } }
        compose.onNodeWithText("Outlet 2 marked as paywalled. Change it in Settings › Sources.").assertIsDisplayed()
        assertTrue(TestCatalog.outletId(2) in currentPrefs().paywallFlags)
        recreate()
        assertTrue("absent after recreation", compose.servedCards().none { feedOf(it.url) == 2 })

        // With the filter off the outlet's cards show again, and its menu no longer offers the row.
        TestPrefs.write(currentPrefs().copy(paywallFilterOn = false))
        waitUntilServed("Outlet 2 back with the filter off") { cards -> cards.any { feedOf(it.url) == 2 } }
        // The confirmation snackbar sits over the bottom of the list; let it time out first.
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty() }
        compose.openCardMenu(titleOf(target))
        compose.onNodeWithText("Don't show content from Outlet 2").assertExists()
        assertTrue(compose.onAllNodesWithText("Mark Outlet 2 as paywalled").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun saveAddsOneSavedRowAndTheMenuThenOffersUnsave() {
        server.fill(itemsPerFeed = 3)
        start()
        val target = compose.visibleCards().first()
        val title = titleOf(target)
        val store = AppGraph.get().articleStore

        compose.openCardMenu(title)
        compose.tapText("Save for later")
        compose.waitUntil(TIMEOUT) { store.listSaved().isNotEmpty() }
        compose.onNodeWithText("Saved for later. Find it in Saved.").assertIsDisplayed()
        assertEquals(listOf(target.id), store.listSaved().map { it.article.id })
        // Saving the same article again keeps one row.
        store.save(store.article(target.id)!!)
        assertEquals(1, store.listSaved().size)

        val savedAt = store.listSaved().single().savedAt

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Saved for later. Find it in Saved.").fetchSemanticsNodes().isEmpty()
        }
        compose.openCardMenu(title)
        compose.onNodeWithText("Unsave").assertIsDisplayed()
        compose.tapText("Unsave")
        compose.waitUntil(TIMEOUT) { store.listSaved().isEmpty() }
        compose.onNodeWithText("Removed from Saved.").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(TIMEOUT) { store.listSaved().size == 1 }
        // Undo restores the saved row exactly, keeping its original save time.
        assertEquals(savedAt, store.listSaved().single().savedAt)
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val CAUGHT_UP = "You're all caught up"
        const val ALL_FILTERED = "Nothing to show — your filters hide everything"
        const val MESSAGE_MORE = "Science now at More (about 2× as often). Change it in Settings."
        const val MESSAGE_NORMAL = "Science now at Normal (the usual share). Change it in Settings."
    }
}
