package app.newz.feed

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.servedCards
import app.newz.testing.waitForStableFeed
import app.newz.ui.components.ARTICLE_IMAGE_TAG
import app.newz.ui.components.NO_IMAGE_TAG
import app.newz.ui.feed.FeedSemantics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Feed pictures (B5): a picture under 300 px wide shows the flap-tile panel, as no picture does;
 * with Settings › Better pictures on, such cards read the start of their article's page for one.
 */
@RunWith(AndroidJUnit4::class)
class FeedPicturesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 1).start()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val now = Clock.System.now()

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    private fun item(
        index: Int,
        image: String? = null,
    ): MockFeedServer.FeedItem =
        server.item(feed = 0, index = index, published = now - (index + 1).hours).copy(imageUrl = image)

    /** Serves [items] as feed 0, launches onboarded with Better pictures [on] or off, waits for the feed. */
    private fun launch(
        items: List<MockFeedServer.FeedItem>,
        betterPictures: Boolean,
    ) {
        server.feed(0).items = items
        TestApp.install(TestCatalog.build(server))
        TestPrefs.write(TestPrefs.onboarded().copy(betterPictures = betterPictures))
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = items.size)
    }

    /** Scrolls the card headed [title] into view and waits until it holds a node tagged [tag]. */
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

    /** Brings every served card on screen in turn, so each is composed at least once. */
    private fun scrollThroughFeed() {
        repeat(compose.servedCards().size + 1) {
            compose.onNodeWithTag(FeedSemantics.LIST_TAG).performScrollToIndex(it)
            compose.waitForIdle()
        }
        compose.onNodeWithTag(FeedSemantics.LIST_TAG).performScrollToIndex(0)
    }

    private fun awaitPaths(vararg paths: String) =
        compose.waitUntil(TIMEOUT) { server.requestPaths.containsAll(paths.toList()) }

    @Test
    fun tinyFeedPictureShowsTheTilePanel() {
        val tiny = item(0, server.tinyImageUrl("a"))
        val full = item(1, server.imageUrl("b"))
        launch(listOf(tiny, full), betterPictures = false)

        awaitPicture(tiny.title, NO_IMAGE_TAG)
        // By now the 320 px picture has loaded too, and stays a picture.
        awaitPicture(full.title, ARTICLE_IMAGE_TAG)
    }

    @Test
    fun switchOffReadsNoArticlePage() {
        val items = listOf(item(0), item(1, server.tinyImageUrl("b")), item(2, server.imageUrl("c")), item(3))
        items.indices.forEach { server.articlePage(0, it, "/img/page-$it.png") }
        launch(items, betterPictures = false)

        awaitPicture(items[1].title, NO_IMAGE_TAG)
        scrollThroughFeed()
        awaitPaths("/img/tiny-b.png", "/img/c.png")
        Thread.sleep(QUIET_MS)
        assertEquals(emptyMap<String, Int>(), server.articlePageReads())
    }

    @Test
    fun switchOnReadsPagesOnlyForCardsWithoutAUsablePictureOnceEach() {
        val none = item(0)
        val tiny = item(1, server.tinyImageUrl("b"))
        val full = item(2, server.imageUrl("c"))
        val pageWithoutPicture = item(3)
        val pageWithTinyPicture = item(4)
        val items = listOf(none, tiny, full, pageWithoutPicture, pageWithTinyPicture)
        server.articlePage(0, 0, "/img/page-0.png")
        server.articlePage(0, 1, "${server.baseUrl}/img/page-1.png")
        server.articlePage(0, 2, "/img/page-2.png")
        server.articlePage(0, 3, null)
        server.articlePage(0, 4, "/img/tiny-page-4.png")
        launch(items, betterPictures = true)

        // A card with no picture, or a tiny one, gets its page's picture once it arrives.
        awaitPicture(none.title, ARTICLE_IMAGE_TAG)
        awaitPaths("/article/0/0", "/img/page-0.png")
        awaitPicture(tiny.title, ARTICLE_IMAGE_TAG)
        awaitPaths("/article/0/1", "/img/page-1.png")
        awaitPicture(full.title, ARTICLE_IMAGE_TAG)
        // A page with no picture, or only a tiny one, leaves the tiles.
        awaitPicture(pageWithoutPicture.title, NO_IMAGE_TAG)
        awaitPaths("/article/0/3")
        awaitPicture(pageWithTinyPicture.title, NO_IMAGE_TAG)
        awaitPaths("/article/0/4", "/img/tiny-page-4.png")
        awaitPicture(pageWithTinyPicture.title, NO_IMAGE_TAG)

        // Scrolling back and forth, and a relaunch on the same database, read no page again.
        scrollThroughFeed()
        scrollThroughFeed()
        scenario?.close()
        TestApp.install(TestCatalog.build(server), keepDatabase = true)
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = items.size)
        scrollThroughFeed()
        awaitPicture(none.title, ARTICLE_IMAGE_TAG)
        Thread.sleep(QUIET_MS)
        assertEquals(
            mapOf("/article/0/0" to 1, "/article/0/1" to 1, "/article/0/3" to 1, "/article/0/4" to 1),
            server.articlePageReads(),
        )
    }

    @Test
    fun anOutletsRepeatedPageImageIsTreatedAsItsLogo() {
        val items = List(4) { item(it) }
        items.indices.forEach { server.articlePage(0, it, "/img/logo.png") }
        launch(items, betterPictures = true)

        items.forEachIndexed { i, article ->
            awaitPicture(article.title, NO_IMAGE_TAG)
            awaitPaths("/article/0/$i")
        }
        val pages = items.map { it.link }
        val pictures = AppGraph.get().pageImages.images
        compose.waitUntil(TIMEOUT) { pictures.value.keys.containsAll(pages) }
        assertTrue("logo kept for ${pictures.value}", pages.all { pictures.value[it] == null })
        items.forEach { awaitPicture(it.title, NO_IMAGE_TAG) }
        assertEquals(items.indices.associate { "/article/0/$it" to 1 }, server.articlePageReads())
    }

    private companion object {
        const val TIMEOUT = 10_000L

        /** How long to wait for requests that must not come. */
        const val QUIET_MS = 1_500L
    }
}
