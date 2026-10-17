package app.newz.settings

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
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
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import app.newz.ui.settings.SourcesSemantics
import app.newz.ui.settings.TopicsSemantics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Every S11 list but hidden articles (phase 16): each choice is made from the feed, found in
 * Settings, reversed there, and the feed shows the reversal (SPEC S11, S12).
 */
@RunWith(AndroidJUnit4::class)
class SettingsReversalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 6).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /** Six feeds of three items (18 cards, one page): topics world, science, tech, business, world, science. */
    private fun start(fill: () -> Unit = { server.fill(itemsPerFeed = 3) }) {
        fill()
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

    private fun waitUntilServed(
        message: String,
        condition: (List<FeedCardInfo>) -> Boolean,
    ) {
        compose.waitUntil(TIMEOUT) { condition(compose.servedCards()) }
        assertTrue(message, condition(compose.servedCards()))
    }

    private fun waitForScreen(screen: Screen) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(Screen.tagOf(screen))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openSettings(row: String? = null) {
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        waitForScreen(Screen.Settings)
        if (row != null) {
            compose.onNode(hasText(row) and hasClickAction()).performScrollTo().performClick()
            waitForScreen(if (row == "Sources") Screen.Sources else Screen.Topics)
        }
    }

    /** Back from a Settings sub-screen to the feed. */
    private fun backToFeed() {
        while (compose.onAllNodes(hasTestTag(FeedSemantics.LIST_TAG)).fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isNotEmpty()
        ) {
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
        }
        compose.waitForStableFeed(minCards = 1)
    }

    private fun clickIn(
        label: String,
        container: SemanticsMatcher,
    ) {
        compose
            .onNode(hasText(label) and hasClickAction() and hasAnyAncestor(container))
            .performScrollTo()
            .performClick()
    }

    private fun waitUntilNoUndo() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun removedSourceIsRestoredFromSources() {
        start()
        val target = compose.servedCards().first { feedOf(it.url) == 2 }
        compose.openCardMenu(titleOf(target))
        compose.tapText("Don't show content from Outlet 2")
        waitUntilServed("Outlet 2 removed") { cards -> cards.none { feedOf(it.url) == 2 } }

        openSettings("Sources")
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Restore")).fetchSemanticsNodes().isNotEmpty() }
        clickIn("Restore", hasTestTag(SourcesSemantics.REMOVED_PANEL))
        compose.waitUntil(TIMEOUT) { TestCatalog.outletId(2) !in currentPrefs().removedOutlets }
        backToFeed()
        waitUntilServed("Outlet 2's cards return") { cards -> cards.count { feedOf(it.url) == 2 } == 3 }
    }

    @Test
    fun followedSourceIsRemovedFromSourcesWithUndo() {
        start()
        assertTrue(compose.servedCards().any { feedOf(it.url) == 0 })

        openSettings("Sources")
        clickIn("Remove", hasTestTag(SourcesSemantics.row(TestCatalog.outletId(0))))
        compose.waitForText("Outlet 0 removed")
        assertTrue(TestCatalog.outletId(0) in currentPrefs().removedOutlets)
        compose.onNode(hasText("Undo") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { TestCatalog.outletId(0) !in currentPrefs().removedOutlets }
        waitUntilNoUndo()
        clickIn("Remove", hasTestTag(SourcesSemantics.row(TestCatalog.outletId(0))))
        compose.waitUntil(TIMEOUT) { TestCatalog.outletId(0) in currentPrefs().removedOutlets }

        backToFeed()
        waitUntilServed("Outlet 0's cards leave") { cards -> cards.none { feedOf(it.url) == 0 } }
    }

    @Test
    fun followedTopicIsUnfollowedFromTopics() {
        start()
        assertTrue(compose.servedCards().any { TestCatalog.SCIENCE in it.topics })

        openSettings("Topics & keywords")
        compose.onNodeWithContentDescription("Unfollow Science").performScrollTo().performClick()
        compose.waitForText("Science unfollowed")
        assertFalse(TestCatalog.SCIENCE in currentPrefs().followedTopics)

        backToFeed()
        waitUntilServed("Science cards leave") { cards -> cards.none { TestCatalog.SCIENCE in it.topics } }
    }

    @Test
    fun mutedTopicIsRestoredFromTopics() {
        start()
        val target = compose.servedCards().first { TestCatalog.SCIENCE in it.topics }
        compose.openCardMenu(titleOf(target))
        compose.tapText("Don't show Science")
        waitUntilServed("Science muted") { cards -> cards.none { TestCatalog.SCIENCE in it.topics } }

        openSettings("Topics & keywords")
        clickIn("Restore", hasTestTag(TopicsSemantics.row(TestCatalog.SCIENCE)))
        compose.waitUntil(TIMEOUT) { TestCatalog.SCIENCE !in currentPrefs().mutedTopics }

        backToFeed()
        waitUntilServed("Science cards return") { cards -> cards.count { TestCatalog.SCIENCE in it.topics } == 6 }
    }

    @Test
    fun mutedKeywordIsUnmutedFromTopics() {
        start {
            val now = Clock.System.now()
            server.fill(itemsPerFeed = 3, now = now)
            server.feed(0).items =
                listOf(server.item(0, 50, now - 30.minutes, title = HEADLINE)) + server.feed(0).items.dropLast(1)
        }
        val keywordCard = { cards: List<FeedCardInfo> -> cards.any { titleOf(it) == HEADLINE } }
        assertTrue(keywordCard(compose.servedCards()))
        compose.openCardMenu(HEADLINE)
        compose.tapText("Mute a keyword…")
        compose.waitForText("From this headline")
        compose.onNode(hasSetTextAction()).performTextInput(KEYWORD)
        compose.onNode(hasText("Mute") and hasClickAction()).performClick()
        waitUntilServed("the keyword's card leaves") { !keywordCard(it) }

        openSettings("Topics & keywords")
        compose.onNodeWithContentDescription("Unmute $KEYWORD").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { currentPrefs().mutedKeywords.isEmpty() }
        compose.waitForText("No muted keywords. Mute a word or name from any article’s ⋮ menu.")

        backToFeed()
        waitUntilServed("the keyword's card returns", keywordCard)
    }

    @Test
    fun showMoreLevelIsLoweredFromTopics() {
        start()
        val target = compose.servedCards().first { it.topics.firstOrNull() == TestCatalog.SCIENCE }
        compose.openCardMenu(titleOf(target))
        compose.tapText("Show more like this")
        compose.waitUntil(TIMEOUT) { currentPrefs().levelOf(TestCatalog.SCIENCE) == Level.MORE }
        val atMore = relaunchAndCountScienceInTop()

        openSettings("Topics & keywords")
        clickIn("Normal", hasTestTag(TopicsSemantics.row(TestCatalog.SCIENCE)))
        compose.waitUntil(TIMEOUT) { currentPrefs().levelOf(TestCatalog.SCIENCE) == Level.NORMAL }
        backToFeed()
        val atNormal = relaunchAndCountScienceInTop()

        assertTrue("More gives Science more of the top cards ($atMore) than Normal ($atNormal)", atNormal < atMore)
    }

    /** Relaunches on the same cache and preferences, so the feed is ranked afresh; Science cards in the top 10. */
    private fun relaunchAndCountScienceInTop(): Int {
        scenario?.close()
        TestApp.install(TestCatalog.build(server), keepDatabase = true)
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 18)
        return compose.servedCards().take(TOP).count { TestCatalog.SCIENCE in it.topics }
    }

    @Test
    fun paywallFlagFromACardIsListedAndUnflagBringsTheOutletBack() {
        start()
        val target = compose.servedCards().first { feedOf(it.url) == 3 }
        compose.openCardMenu(titleOf(target))
        compose.tapText("Mark Outlet 3 as paywalled")
        waitUntilServed("Outlet 3 hidden") { cards -> cards.none { feedOf(it.url) == 3 } }

        openSettings("Sources")
        compose.waitForText("Hidden while the paywall filter is on")
        clickIn("Unflag", hasTestTag(SourcesSemantics.FLAGGED_PANEL))
        compose.waitUntil(TIMEOUT) { TestCatalog.outletId(3) !in currentPrefs().paywallFlags }

        backToFeed()
        waitUntilServed("Outlet 3's cards return") { cards -> cards.count { feedOf(it.url) == 3 } == 3 }
        assertEquals(18, compose.servedCards().size)
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val TOP = 10
        const val KEYWORD = "Zebrafin"
        const val HEADLINE = "Zebrafin parade closes the harbour road"
    }
}
