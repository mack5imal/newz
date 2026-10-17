package app.newz.feed

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.MainActivity
import app.newz.catalog.PaywallStatus
import app.newz.filter.customTopicId
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.servedCards
import app.newz.testing.visibleCards
import app.newz.testing.waitForCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.components.ARTICLE_IMAGE_TAG
import app.newz.ui.components.NO_IMAGE_TAG
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import app.newz.ui.theme.NewzColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The feed screen against the on-device [MockFeedServer] (SPEC S5, S6, S12, S14, S15, S22, S25). */
@RunWith(AndroidJUnit4::class)
class FeedScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 8).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
        setNightMode(UiModeManager.MODE_NIGHT_AUTO)
    }

    private fun launch() {
        scenario = TestApp.launch()
    }

    @Test
    fun cardShowsHeadlineOutletAgeAndImageOrPlaceholder() {
        server.fill(itemsPerFeed = 2, imageEvery = 2)
        val byUrl = server.feeds.flatMap { it.items }.associateBy { it.link }
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 16)

        val first = compose.visibleCards().first()
        val item = byUrl.getValue(first.url)
        val feed = server.feeds.indexOfFirst { item in it.items }
        val card = hasTestTag(FeedSemantics.CARD_TAG) and hasText(item.title)
        compose.onNode(card).assertIsDisplayed()
        compose.onNode(card and hasText(TestCatalog.outletName(feed))).assertExists()
        val spoken =
            compose
                .onNode(card)
                .fetchSemanticsNode()
                .config
                .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue("age text on card: $spoken", spoken.any { AGE.matches(it) })
        // The unmerged tree keeps the card's image (or placeholder) as its own descendant node.
        val unmerged = hasTestTag(FeedSemantics.CARD_TAG) and hasAnyDescendant(hasText(item.title))
        val visual = hasTestTag(ARTICLE_IMAGE_TAG) or hasTestTag(NO_IMAGE_TAG)
        compose.onNode(unmerged and hasAnyDescendant(visual), useUnmergedTree = true).assertExists()
        val expectedTag = if (item.imageUrl != null) ARTICLE_IMAGE_TAG else NO_IMAGE_TAG
        compose.onNode(unmerged and hasAnyDescendant(hasTestTag(expectedTag)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun scrollingToTheEndShowsAllCaughtUpAndNothingOlderThanSevenDays() {
        val now = Clock.System.now()
        server.fill(itemsPerFeed = 5, now = now, spacing = 5.hours)
        val stale = mutableSetOf<String>()
        server.feeds.forEachIndexed { f, config ->
            val old = server.item(f, 100, now - 7.days - 30.minutes)
            val older = server.item(f, 101, now - 9.days)
            stale += old.link
            stale += older.link
            config.items = config.items + old + older
        }
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 20)

        // Scroll one list item at a time so every card is composed (and checked) on the way down.
        val seen = linkedSetOf<String>()
        var index = 0
        while (index < MAX_STEPS && compose.onAllNodesWithText(CAUGHT_UP).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag(FeedSemantics.LIST_TAG).performScrollToIndex(index)
            compose.waitForIdle()
            compose.visibleCards().forEach { seen += it.url }
            assertTrue("no article older than 7 days: ${seen.intersect(stale)}", seen.none { it in stale })
            index++
        }
        compose.visibleCards().forEach { seen += it.url }
        compose.onNodeWithText(CAUGHT_UP).assertIsDisplayed()
        assertEquals("every fresh article shown once", 40, seen.size)
        assertTrue("no article older than 7 days: ${seen.intersect(stale)}", seen.none { it in stale })
        assertTrue(compose.servedCards().all { now - it.date <= 7.days })
    }

    @Test
    fun everySourceRemovedShowsNothingToShowAndOpensSettings() {
        server.fill(itemsPerFeed = 2)
        val catalog = TestCatalog.build(server)
        TestApp.install(catalog)
        val prefs = TestPrefs.onboarded()
        TestPrefs.write(prefs.copy(removedOutlets = catalog.outlets.associate { it.id to Clock.System.now() }))
        launch()
        compose.waitForText(ALL_FILTERED)
        compose.onNodeWithText(ALL_FILTERED).assertIsDisplayed()
        compose.onNodeWithText("Review in Settings").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(Screen.tagOf(Screen.Settings)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(Screen.tagOf(Screen.Settings)).assertIsDisplayed()
    }

    @Test
    fun topicFilterHidesAndClearingRestoresTheSameCards() {
        server.fill(itemsPerFeed = 5)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 20)
        val firstTen = compose.servedCards().take(10).map { it.id }
        assertEquals(10, firstTen.size)
        val prefsBefore = currentPrefs()

        compose.onNode(hasText("Science") and hasClickAction()).performClick()
        compose.waitUntil(5_000) {
            val cards = compose.servedCards()
            cards.isNotEmpty() && cards.all { TestCatalog.SCIENCE in it.topics }
        }
        compose.onNodeWithText("Showing Science").assertIsDisplayed()
        val visible = compose.visibleCards()
        assertTrue(visible.isNotEmpty())
        assertTrue("visible cards all Science: $visible", visible.all { TestCatalog.SCIENCE in it.topics })

        compose.onNodeWithText("Clear filter").performClick()
        compose.waitUntil(5_000) { compose.servedCards().take(10).map { it.id } == firstTen }
        assertEquals(firstTen, compose.servedCards().take(10).map { it.id })
        assertEquals("filtering left the preferences unchanged", prefsBefore, currentPrefs())

        compose.onNode(hasText("Health") and hasClickAction()).performClick()
        compose.waitForText("No Health articles right now")
        compose.onNodeWithText("No Health articles right now").assertIsDisplayed()
        compose.onNode(hasText("Clear filter") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun aTopicFirstMatchedBeyondThePageStillPagesToTheEnd() {
        // 96 cards, five pages. Only the oldest item of feeds 0 and 1 matches the custom topic, so
        // each ranks last in its topic queue: the first four pages hold no matching card.
        server.fill(itemsPerFeed = 12, imageEvery = 0)
        val matchingFeeds = server.feeds.take(2)
        matchingFeeds.forEach { config ->
            val last = config.items.last()
            config.items = config.items.dropLast(1) + last.copy(summary = "A $CUSTOM_WORD report.")
        }
        val matching = matchingFeeds.map { it.items.last().link }.toSet()
        val catalog = TestCatalog.build(server)
        TestApp.install(catalog)
        val prefs = TestPrefs.onboarded().copy(customTopics = listOf(CUSTOM_WORD))
        TestPrefs.write(prefs)
        launch()
        compose.waitForStableFeed(minCards = 20)
        // Relaunch on the filled cache: the first page is then the ranked order, not whichever
        // feed landed first.
        scenario?.close()
        TestApp.install(catalog, keepDatabase = true)
        TestPrefs.write(prefs)
        launch()
        compose.waitForStableFeed(minCards = 20)
        val customId = customTopicId(CUSTOM_WORD)
        val firstPage = compose.servedCards().take(20)
        assertEquals(20, firstPage.size)
        assertTrue("first 20 cards hold no $CUSTOM_WORD article", firstPage.none { customId in it.topics })

        // The custom chip comes after the five followed topics: bring it into the chip row's view.
        compose.onNode(hasText(CUSTOM_WORD) and hasClickAction()).performScrollTo().performClick()
        // No scrolling: the filtered pages alone must keep asking for more until the end.
        compose.waitUntil(10_000) { compose.servedCards().map { it.url }.toSet() == matching }
        // The end item ("end" key) exists only once the last page is served; it may sit below the
        // screen, so find it by key first and only then scroll to it to see it.
        val list = compose.onNodeWithTag(FeedSemantics.LIST_TAG)
        compose.waitUntil(10_000) { list.fetchSemanticsNode().config[SemanticsProperties.IndexForKey]("end") >= 0 }
        list.performScrollToIndex(list.fetchSemanticsNode().config[SemanticsProperties.IndexForKey]("end"))
        compose.onNodeWithText(CAUGHT_UP).assertIsDisplayed()
        val served = compose.servedCards()
        assertEquals(matching, served.map { it.url }.toSet())
        assertTrue(served.all { customId in it.topics })
    }

    @Test
    fun mutingTheSelectedTopicStopsItFiltering() {
        server.fill(itemsPerFeed = 3)
        TestApp.install(TestCatalog.build(server))
        val prefs = TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 20)

        compose.onNode(hasText("Science") and hasClickAction()).performClick()
        compose.waitForText("Showing Science")
        TestPrefs.write(prefs.copy(mutedTopics = mapOf(TestCatalog.SCIENCE to Clock.System.now())))

        compose.waitUntil(5_000) {
            val cards = compose.servedCards()
            cards.isNotEmpty() && cards.none { TestCatalog.SCIENCE in it.topics }
        }
        assertTrue(compose.visibleCards().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Showing Science").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Clear filter").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun groupedCardsShowSourceCountsAndOpenTheStorySheet() {
        val now = Clock.System.now()
        server.fill(itemsPerFeed = 2, now = now, newestAge = 6.hours)
        val storyA = "Harbor tunnel reopens after a month of repairs to flooded tracks"
        val storyB = "City council approves the new riverside library budget"
        val aFeeds = listOf(0 to 2.hours, 1 to 1.hours, 2 to 3.hours, 3 to 30.minutes)
        val aLinks = mutableMapOf<String, Instant>()
        aFeeds.forEach { (f, age) ->
            val item = server.item(f, 50, now - age, title = storyA)
            aLinks[item.link] = now - age
            server.feed(f).items = listOf(item) + server.feed(f).items
        }
        listOf(4 to 1.hours, 5 to 2.hours).forEach { (f, age) ->
            server.feed(f).items = listOf(server.item(f, 60, now - age, title = storyB)) + server.feed(f).items
        }
        TestApp.install(TestCatalog.build(server) { if (it == 1) PaywallStatus.METERED else PaywallStatus.NONE })
        TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 10)

        scrollToCard { it.outlets == 2 }
        compose.waitForText("+1 source")
        scrollToCard { it.outlets == 4 }
        compose.waitForText("+3 sources")
        compose.onNodeWithText("+3 sources").performClick()
        compose.waitForText("Same story · 4 outlets")
        compose.onNodeWithText("Same story · 4 outlets").assertIsDisplayed()
        // positionInRoot is unclipped: rows scrolled below the sheet's edge still sort by layout order.
        val rows =
            compose
                .onAllNodesWithTag(FeedSemantics.STORY_ROW_TAG)
                .fetchSemanticsNodes()
                .sortedBy { it.positionInRoot.y }
                .map { it.config[FeedSemantics.StoryRowUrl] }
        assertEquals(4, rows.size)
        assertEquals(aLinks.entries.sortedByDescending { it.value }.map { it.key }, rows)
        compose.onNodeWithText("Metered").assertExists()
        compose.onNodeWithText("Each article opens in your browser.").assertExists()
    }

    @Test
    fun offlineWithCacheShowsBannerAndRetry() {
        server.fill(itemsPerFeed = 2)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 8)
        scenario?.close()

        server.close()
        TestApp.install(TestCatalog.build(server), keepDatabase = true)
        launch()
        compose.waitForText(OFFLINE_BANNER)
        compose.onNodeWithText(OFFLINE_BANNER).assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        assertTrue(compose.visibleCards().isNotEmpty())
    }

    @Test
    fun offlineWithoutCacheShowsYoureOffline() {
        server.fill(itemsPerFeed = 2)
        server.close()
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        launch()
        compose.waitForText("You’re offline")
        compose.onNodeWithText("You’re offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun aFailingSourceIsSkippedWhileOthersShow() {
        server.fill(itemsPerFeed = 2)
        server.feed(0).status = 500
        val failing =
            server
                .feed(0)
                .items
                .map { it.link }
                .toSet()
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        launch()
        compose.waitForStableFeed(minCards = 14)
        val served = compose.servedCards()
        assertEquals(14, served.size)
        assertTrue(served.none { it.url in failing })
        assertTrue(compose.onAllNodesWithText(OFFLINE_BANNER).fetchSemanticsNodes().isEmpty())
    }

    @SdkSuppress(minSdkVersion = 31)
    @Test
    fun feedBackgroundIsPaperInLightAndDark() {
        assertEquals(NewzColors.Light.paper, gapPixel(night = false))
        assertEquals(NewzColors.Dark.paper, gapPixel(night = true))
        assertEquals(Color(0xFFDCE5F2), NewzColors.Light.paper)
        assertEquals(Color(0xFF1D3A5F), NewzColors.Dark.paper)
    }

    /** Scrolls the feed to the first served card matching [predicate] (item 0 is the chip row). */
    private fun scrollToCard(predicate: (FeedCardInfo) -> Boolean) {
        val index = compose.servedCards().indexOfFirst(predicate)
        assertTrue("card in feed: ${compose.servedCards()}", index >= 0)
        compose.onNodeWithTag(FeedSemantics.LIST_TAG).performScrollToIndex(index + 1)
        compose.waitForIdle()
    }

    /** The pixel in the 14 dp gap under the first card, with night mode [night]. */
    private fun gapPixel(night: Boolean): Color {
        scenario?.close()
        setNightMode(if (night) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        server.fill(itemsPerFeed = 1)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.write(UserPrefs())
        TestPrefs.onboarded()
        launch()
        compose.waitUntil(5_000) {
            var matches = false
            scenario?.onActivity {
                val mode = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                matches = mode == if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            matches
        }
        compose.waitForStableFeed(minCards = 2)
        val card =
            compose
                .onAllNodesWithTag(FeedSemantics.CARD_TAG)
                .fetchSemanticsNodes()
                .minBy { it.positionInRoot.y }
        val density =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.displayMetrics.density
        val x = (card.positionInRoot.x + card.size.width / 2).toInt()
        val y = (card.positionInRoot.y + card.size.height + GAP_PROBE.value * density).toInt()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertFalse("probe inside the screen", y >= pixels.height)
        return pixels[x, y]
    }

    private fun setNightMode(mode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSystemService(Context.UI_MODE_SERVICE).let { (it as UiModeManager).setApplicationNightMode(mode) }
    }

    private companion object {
        const val CAUGHT_UP = "You're all caught up"
        const val ALL_FILTERED = "Nothing to show — your filters hide everything"
        const val OFFLINE_BANNER = "Offline — showing articles loaded earlier"
        const val MAX_STEPS = 120
        const val CUSTOM_WORD = "zeppelin"
        val GAP_PROBE = 7.dp
        val AGE = Regex("just now|\\d+ (minute|hour|day)s? ago")
    }
}
