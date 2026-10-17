package app.newz.feed

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.waitForStableFeed
import app.newz.ui.components.ARTICLE_IMAGE_TAG
import app.newz.ui.components.NO_IMAGE_TAG
import app.newz.ui.feed.FeedSemantics
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Feed pictures (B5): a picture under 300 px wide shows the flap-tile panel, as no picture does. */
@RunWith(AndroidJUnit4::class)
class FeedPicturesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 1).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /** Waits until the card headed [title] (scrolled into view) holds a node tagged [tag]. */
    private fun awaitPicture(
        title: String,
        tag: String,
    ) {
        val card = hasTestTag(FeedSemantics.CARD_TAG) and hasAnyDescendant(hasText(title))
        compose
            .onNodeWithTag(FeedSemantics.LIST_TAG)
            .performScrollToNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(title))
        compose.waitUntil(TIMEOUT) {
            compose
                .onAllNodes(card and hasAnyDescendant(hasTestTag(tag)), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun tinyFeedPictureShowsTheTilePanel() {
        val now = Clock.System.now()
        val tiny = server.item(feed = 0, index = 0, published = now - 1.hours).copy(imageUrl = server.tinyImageUrl("a"))
        val full = server.item(feed = 0, index = 1, published = now - 2.hours, withImage = true)
        server.feed(0).items = listOf(tiny, full)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 2)

        awaitPicture(tiny.title, NO_IMAGE_TAG)
        // By now the 320 px picture has loaded too, and stays a picture.
        awaitPicture(full.title, ARTICLE_IMAGE_TAG)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
