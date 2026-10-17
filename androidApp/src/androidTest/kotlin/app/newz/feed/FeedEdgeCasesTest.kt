package app.newz.feed

import android.os.SystemClock
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.a11y.EdgeHeadlines
import app.newz.feed.FeedParser.MAX_ITEMS
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.feedOf
import app.newz.testing.servedCards
import app.newz.testing.waitForCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedSemantics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Feed edges and drawn states the feature tests do not reach (phase 19): the first-load board,
 * a 10 s feed that must not hold the others back, an empty feed, a 5,000-item feed, a broken
 * image link, and very long, emoji and right-to-left headlines.
 */
@RunWith(AndroidJUnit4::class)
class FeedEdgeCasesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 6).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    private fun launch() {
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        scenario = TestApp.launch()
    }

    @Test
    fun firstLoadShowsTheLoadingBoardThenCards() {
        server.fill(itemsPerFeed = 2)
        server.feeds.forEach { it.delayMs = 2_000 }
        launch()
        compose.waitForText("Loading today’s news…", TIMEOUT)
        compose.waitForCards(TIMEOUT)
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Loading today’s news…")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aTenSecondFeedDoesNotHoldBackTheOthers() {
        server.fill(itemsPerFeed = 2)
        server.feed(0).delayMs = 10_000
        val start = SystemClock.elapsedRealtime()
        launch()
        compose.waitForCards(TIMEOUT)
        val firstCardMs = SystemClock.elapsedRealtime() - start
        assertTrue("first card after $firstCardMs ms with one 10 s feed", firstCardMs < 5_000)
        compose.waitUntil(TIMEOUT) { compose.servedCards().size >= 10 }
        assertTrue(
            "only the slow feed is missing",
            compose.servedCards().none { feedOf(it.url) == 0 },
        )
        // The slow feed is still fetched and stored once it answers (it shows on the next refresh).
        compose.waitUntil(SLOW_TIMEOUT) {
            AppGraph
                .get()
                .articleStore
                .articles()
                .any { feedOf(it.url) == 0 }
        }
    }

    @Test
    fun emptyHugeAndBrokenImageFeedsLoadAlongsideOddHeadlines() {
        server.fill(itemsPerFeed = 2)
        val now = Clock.System.now()
        server.feed(1).items = emptyList()
        // 5,000 items a minute apart: only the newest 300 are kept.
        server.feed(2).items = List(5_000) { i -> server.item(2, i, now - 2.minutes - i.minutes) }
        server.feed(3).items =
            listOf(
                server
                    .item(3, 0, now - 1.minutes, title = "A card whose image link is broken")
                    .copy(imageUrl = "${server.baseUrl}/missing/image.png"),
            )
        EdgeHeadlines.ALL.forEachIndexed { i, title ->
            server.feed(4).items = server.feed(4).items + server.item(4, 10 + i, now - (3 + i).minutes, title = title)
        }
        launch()
        compose.waitForStableFeed(minCards = 20, timeoutMs = SLOW_TIMEOUT)

        val hugeFeed = compose.servedCards().count { feedOf(it.url) == 2 }
        assertTrue("the 5,000-item feed shows cards", hugeFeed > 0)
        val stored =
            AppGraph
                .get()
                .articleStore
                .articles()
                .count { feedOf(it.url) == 2 }
        assertEquals("the newest $MAX_ITEMS of the 5,000 items are stored", MAX_ITEMS, stored)
        assertTrue("nothing from the empty feed", compose.servedCards().none { feedOf(it.url) == 1 })

        for (title in EdgeHeadlines.ALL + "A card whose image link is broken") {
            compose
                .onNodeWithTag(FeedSemantics.LIST_TAG)
                .performScrollToNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(title))
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val SLOW_TIMEOUT = 30_000L
    }
}
