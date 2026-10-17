package app.newz.feed

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.openCardMenu
import app.newz.testing.servedCards
import app.newz.testing.tapText
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.keywordSuggestions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** The keyword-mute sheet: suggestions, input rules, and muting across recreation (SPEC S8). */
@RunWith(AndroidJUnit4::class)
class KeywordMuteTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 8).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /** Fills the feeds (19 cards, all on the first page) and puts the keyword in two headlines (one lower-case) and one summary. */
    private fun start() {
        val now = Clock.System.now()
        server.fill(itemsPerFeed = 2, now = now)
        server.feed(0).items = listOf(server.item(0, 50, now - 1.hours, title = HEADLINE)) + server.feed(0).items
        server.feed(2).items =
            listOf(server.item(2, 50, now - 1.hours, title = "Penguins return as antarctica warms")) +
            server.feed(2).items
        val withSummary = server.item(3, 50, now - 1.hours).copy(summary = "Scientists in Antarctica measured the ice.")
        server.feed(3).items = listOf(withSummary) + server.feed(3).items
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 19)
    }

    private fun mentions(card: FeedCardInfo): Boolean {
        val item = server.feeds.flatMap { it.items }.first { it.link == card.url }
        return listOfNotNull(item.title, item.summary).any { KEYWORD_WORD.containsMatchIn(it) }
    }

    private fun openKeywordSheet(title: String) {
        compose.openCardMenu(title)
        compose.tapText("Mute a keyword…")
        compose.waitForText("From this headline")
    }

    private fun field() = compose.onNode(hasSetTextAction())

    private fun muteButton() = compose.onNodeWithText("Mute")

    @Test
    fun muteFromASuggestionRemovesEveryMatchingCardOnceAndSurvivesRecreation() {
        start()
        assertEquals(3, compose.servedCards().count(::mentions))

        openKeywordSheet(HEADLINE)
        compose.onNode(hasText("Antarctica") and hasClickAction() and !hasSetTextAction()).performClick()
        field().assert(hasText("Antarctica"))
        muteButton().performClick()

        compose.waitUntil(TIMEOUT) { compose.servedCards().none(::mentions) }
        compose.onNodeWithText("Muted “Antarctica”").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
        assertEquals(listOf("Antarctica"), currentPrefs().mutedKeywords.map { it.text })

        // The same keyword again, typed in another case and with spaces, keeps one entry.
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty() }
        val other =
            server.feeds
                .flatMap { it.items }
                .first { item -> compose.servedCards().any { it.url == item.link } }
        openKeywordSheet(other.title)
        field().performTextInput(" antarctica ")
        muteButton().performClick()
        compose.waitForText("Muted “antarctica”")
        assertEquals(1, currentPrefs().mutedKeywords.size)

        scenario!!.recreate()
        compose.waitForStableFeed(minCards = 1)
        assertTrue("no card mentions the keyword after recreation", compose.servedCards().none(::mentions))
        assertEquals(1, currentPrefs().mutedKeywords.size)
    }

    @Test
    fun inputRulesBlankTooLongExactlySixtyAndSuggestions() {
        start()
        assertEquals(listOf("Glacier", "Antarctica", "McMurdo Station"), keywordSuggestions(HEADLINE))
        assertEquals(
            listOf("Deep-sea", "Antarctica"),
            keywordSuggestions("Deep-sea survey maps the currents carrying warm water toward Antarctica’s ice shelves"),
        )

        openKeywordSheet(HEADLINE)
        field().assertIsFocused()
        compose.onNodeWithText("Matches the whole word, in any capitalization").assertIsDisplayed()
        compose.onNodeWithText("0/60").assertIsDisplayed()
        muteButton().assertIsNotEnabled()

        field().performTextInput("   ")
        muteButton().assertIsNotEnabled()

        field().performTextClearance()
        val tooLong = "a".repeat(61)
        field().performTextInput(tooLong)
        compose.onNodeWithText("Keep it to 60 characters or fewer").assertIsDisplayed()
        compose.onNodeWithText("61/60").assertIsDisplayed()
        field().assert(hasText(tooLong))
        muteButton().assertIsNotEnabled()

        field().performTextClearance()
        field().performTextInput("b".repeat(60))
        compose.onNodeWithText("60/60").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Keep it to 60 characters or fewer").fetchSemanticsNodes().isEmpty())
        muteButton().assertIsEnabled()

        compose.onNode(hasText("McMurdo Station") and hasClickAction() and !hasSetTextAction()).performClick()
        field().assert(hasText("McMurdo Station"))
        muteButton().assertIsEnabled()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("From this headline").fetchSemanticsNodes().isEmpty() }
        assertTrue(currentPrefs().mutedKeywords.isEmpty())
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val HEADLINE = "Glacier survey finds Antarctica shelf thinning near McMurdo Station"
        val KEYWORD_WORD = Regex("\\bantarctica\\b", RegexOption.IGNORE_CASE)
    }
}
