package app.newz.settings

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.openCardMenu
import app.newz.testing.servedCards
import app.newz.testing.tapText
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import app.newz.ui.saved.SavedSemantics
import app.newz.ui.settings.HiddenSemantics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 15_000L
private const val SAVED_OFFLINE = "You’re offline. Your list is here; articles open once you’re back online."

/**
 * Hidden articles (SPEC S7) and Saved (SPEC S21): a card hidden from the feed is listed under
 * Settings › Hidden articles and Restore brings it back; a saved card is listed in Saved (once,
 * however often it is saved), Unsave empties the list, and the list stays readable offline.
 */
@RunWith(AndroidJUnit4::class)
class HiddenSavedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 6).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /** Six feeds of three items: 18 cards on one page. */
    private fun start() {
        server.fill(itemsPerFeed = 3)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 18)
    }

    private fun titleOf(card: FeedCardInfo): String =
        server.feeds
            .flatMap { it.items }
            .first { it.link == card.url }
            .title

    /** A served card that is one article (no story group), so hiding it hides just that one. */
    private fun singleCard(): FeedCardInfo = compose.servedCards().first { it.outlets == 1 }

    private fun waitForScreen(screen: Screen) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(Screen.tagOf(screen))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilNoUndo() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isEmpty() }
    }

    private fun served(id: String): Boolean = compose.servedCards().any { it.id == id }

    private fun openSaved() {
        compose.onNode(hasText("Saved") and hasClickAction()).performClick()
        waitForScreen(Screen.Saved)
    }

    @Test
    fun hiddenArticleIsRestoredToTheFeed() {
        start()
        val target = singleCard()
        val title = titleOf(target)
        compose.openCardMenu(title)
        compose.tapText("Hide this article")
        compose.waitUntil(TIMEOUT) { !served(target.id) }
        waitUntilNoUndo()

        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        waitForScreen(Screen.Settings)
        compose.waitForText("1 hidden")
        compose.onNode(hasText("Hidden articles") and hasClickAction()).performScrollTo().performClick()
        waitForScreen(Screen.Hidden)
        compose.waitForText(title)
        val row = hasTestTag(HiddenSemantics.row(target.id))
        compose.onNode(row).assertExists()
        compose
            .onNode(
                hasText("Outlet", substring = true) and hasText("· Hidden today", substring = true),
            ).assertExists()

        compose.onNode(hasText("Restore") and hasClickAction() and hasAnyAncestor(row)).performClick()
        compose.waitForText("No hidden articles")
        compose.onNodeWithText("No hidden articles").assertExists()
        assertTrue(compose.onAllNodes(row).fetchSemanticsNodes().isEmpty())

        while (compose.onAllNodes(hasTestTag(FeedSemantics.LIST_TAG)).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
        }
        compose.waitUntil(TIMEOUT) { served(target.id) }
        assertTrue("restored article is back in the feed", served(target.id))
    }

    @Test
    fun savedArticleIsListedOnceAndUnsaved() {
        start()
        val target = singleCard()
        val title = titleOf(target)
        compose.openCardMenu(title)
        compose.tapText("Save for later")
        waitUntilNoUndo()

        // Saving the same article again keeps one entry.
        val store = AppGraph.get().articleStore
        store.save(checkNotNull(store.article(target.id)))
        assertEquals(1, store.listSaved().count { it.article.id == target.id })

        openSaved()
        compose.waitForText(title)
        val card = hasTestTag(SavedSemantics.card(target.id))
        assertEquals(1, compose.onAllNodes(card).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasText(title)).fetchSemanticsNodes().size)

        compose.onNode(hasContentDescription("Unsave") and hasAnyAncestor(card)).performClick()
        compose.waitForText("Removed from saved")
        compose.waitForText("Nothing saved yet")
        assertTrue(compose.onAllNodes(card).fetchSemanticsNodes().isEmpty())
        assertTrue(store.listSaved().isEmpty())
    }

    @Test
    fun savedListStaysReadableOffline() {
        start()
        val target = singleCard()
        val title = titleOf(target)
        compose.openCardMenu(title)
        compose.tapText("Save for later")
        waitUntilNoUndo()
        scenario?.close()

        server.close()
        TestApp.install(TestCatalog.build(server), keepDatabase = true)
        scenario = TestApp.launch()
        compose.waitForText("Offline — showing articles loaded earlier", TIMEOUT)
        openSaved()
        compose.waitForText(SAVED_OFFLINE, TIMEOUT)
        compose.onNodeWithText(SAVED_OFFLINE).assertExists()
        compose.onNode(hasText("Retry") and hasClickAction()).assertExists()
        compose.waitForText(title)
        compose.onNode(hasTestTag(SavedSemantics.card(target.id))).assertExists()
        assertFalse(compose.onAllNodes(hasText("Nothing saved yet")).fetchSemanticsNodes().isNotEmpty())
        // The banner sits above the list.
        val banner =
            compose
                .onNodeWithText(SAVED_OFFLINE)
                .fetchSemanticsNode()
                .positionInRoot.y
        val card =
            compose
                .onNode(hasTestTag(SavedSemantics.card(target.id)))
                .fetchSemanticsNode()
                .positionInRoot.y
        assertTrue(banner < card)
    }
}
