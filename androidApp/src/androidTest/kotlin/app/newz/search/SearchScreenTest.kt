package app.newz.search

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.catalog.Catalog
import app.newz.catalog.PaywallStatus
import app.newz.discover.customFeedId
import app.newz.net.HostGuard
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import app.newz.ui.search.SearchSemantics
import app.newz.ui.settings.SourcesSemantics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Search (design `search`, SPEC S17): catalog Follow, the custom-topic offer and the web-address
 * check against mock-server site pages, including the Google refusal with zero Google requests.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    /** Feeds 0-5 are catalog feeds; 6 and 7 are only reachable from site pages. */
    private val server = MockFeedServer(feedCount = 8).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** The host of every request the app sent, recorded behind its guarded client. */
    private val hosts = ConcurrentLinkedQueue<String>()

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /** Six catalog outlets (out-1 renamed [GAZETTE], a metered science outlet); following World. */
    private fun start() {
        server.fill(itemsPerFeed = 2)
        server.sitePageWithFeed(SITE_WITH_FEED, index = EXTERNAL_FEED)
        server.page(SITE_WITHOUT_FEED, "<!doctype html><html><head><title>Plain</title></head><body>Hi</body></html>")
        val base = TestCatalog.build(server, feeds = CATALOG_FEEDS)
        val catalog =
            Catalog(
                topics = base.topics,
                outlets =
                    base.outlets.map {
                        if (it.id != GAZETTE_ID) it else it.copy(name = GAZETTE, paywall = PaywallStatus.METERED)
                    },
                feeds = base.feeds,
                metros = base.metros,
            )
        TestApp.install(catalog, httpClient = TestApp.recordingClient(hosts))
        TestPrefs.onboarded(TestCatalog.WORLD)
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 1)
    }

    private fun waitForScreen(screen: Screen) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(Screen.tagOf(screen))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun count(matcher: SemanticsMatcher): Int = compose.onAllNodes(matcher).fetchSemanticsNodes().size

    /** Feed › Settings, then the Settings row [row] opening [screen]. */
    private fun openFromSettings(
        row: String,
        screen: Screen,
    ) {
        if (count(hasTestTag(Screen.tagOf(Screen.Settings))) == 0) {
            compose.onNode(hasText("Settings") and hasClickAction()).performClick()
            waitForScreen(Screen.Settings)
        }
        compose.onNode(hasText(row) and hasClickAction()).performScrollTo().performClick()
        waitForScreen(screen)
    }

    private fun openSearch() = openFromSettings("Find sources", Screen.Search)

    private fun back() {
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
    }

    private fun type(text: String) {
        compose.onNodeWithTag(SearchSemantics.FIELD).performTextReplacement(text)
    }

    /** With its scheme: an address typed without one is checked over https, which the mock server does not speak. */
    private fun address(path: String): String = server.baseUrl + path

    @Test
    fun followingACatalogOutletShowsFollowingAndListsItInSources() {
        start()
        openSearch()
        type("Tidewater")
        val row = hasTestTag(SearchSemantics.outlet(GAZETTE_ID))
        waitFor(row)
        compose.onNode(hasAnyAncestor(row) and hasText("Science · 1 feed")).assertExists()
        compose.onNode(hasAnyAncestor(row) and hasText("Metered")).assertExists()
        compose.onNode(hasAnyAncestor(row) and hasText("Follow") and hasClickAction()).performClick()
        waitFor(hasAnyAncestor(row) and hasText("Following"))
        assertTrue(currentPrefs().followedOutlets[GAZETTE_ID].orEmpty().isNotEmpty())

        back()
        openFromSettings("Sources", Screen.Sources)
        waitFor(hasTestTag(SourcesSemantics.row(GAZETTE_ID)))
        assertEquals(1, count(hasTestTag(SourcesSemantics.row(GAZETTE_ID))))
    }

    @Test
    fun addingACustomTopicPutsItsChipInTheFeed() {
        start()
        openSearch()
        type("solar")
        waitFor(hasTestTag(SearchSemantics.CUSTOM_TOPIC))
        compose.onNode(hasText("Shows articles from your sources that mention it")).assertExists()
        compose.onNode(hasText("Add 'solar' as a custom topic")).performClick()
        compose.waitUntil(TIMEOUT) { "solar" in currentPrefs().customTopics }
        // Once added, the offer is gone.
        compose.waitUntil(TIMEOUT) { count(hasTestTag(SearchSemantics.CUSTOM_TOPIC)) == 0 }

        back()
        waitForScreen(Screen.Settings)
        back()
        compose.waitUntil(TIMEOUT) { count(hasTestTag(FeedSemantics.LIST_TAG)) > 0 }
        waitFor(hasText("solar") and hasClickAction())
    }

    @Test
    fun aSiteWithAnRssLinkIsFoundAddedOnceAndThenAlreadyFollowing() {
        start()
        openSearch()
        type(address(SITE_WITH_FEED))
        compose.waitForText("Feed found", TIMEOUT)
        val feedUrl = server.feedUrl(EXTERNAL_FEED)
        compose.onNode(hasText(feedUrl.removePrefix("http://") + " · RSS")).assertExists()
        compose.onNode(hasText("· RSS", substring = true)).assertExists()
        compose.onNode(hasText("Test feed $EXTERNAL_FEED")).assertExists()
        compose.onNode(hasText("Add") and hasClickAction()).performClick()
        compose.waitForText("Already following", TIMEOUT)

        val id = customFeedId(feedUrl)
        back()
        openFromSettings("Sources", Screen.Sources)
        waitFor(hasTestTag(SourcesSemantics.row(id)))
        assertEquals(1, count(hasTestTag(SourcesSemantics.row(id))))

        back()
        openSearch()
        type(address(SITE_WITH_FEED))
        compose.waitForText("Already following", TIMEOUT)
        compose.onNode(hasText("Add") and hasClickAction()).assertDoesNotExist()

        back()
        openFromSettings("Sources", Screen.Sources)
        waitFor(hasTestTag(SourcesSemantics.row(id)))
        assertEquals(1, count(hasTestTag(SourcesSemantics.row(id))))
        assertEquals(
            1,
            AppGraph
                .get()
                .articleStore
                .listCustomFeeds()
                .size,
        )
    }

    @Test
    fun aSiteWithoutAFeedAndABareSchemeEachShowTheirState() {
        start()
        openSearch()
        type(address(SITE_WITHOUT_FEED))
        compose.waitForText("No feed found on this site", TIMEOUT)
        type("http://")
        compose.waitForText("That doesn't look like a web address", TIMEOUT)
    }

    @Test
    fun aGoogleAddressIsRefusedWithoutAnyRequestToGoogle() {
        start()
        openSearch()
        type("feedburner.com/x")
        compose.waitForText("Newz never contacts Google", TIMEOUT)
        compose.waitForIdle()
        val sent = hosts.toList()
        // The counter works: the feed's own loads went through it.
        assertTrue("no requests recorded at all", sent.contains(MockFeedServer.HOST))
        val googleRequests = sent.count { HostGuard.isBlockedHost(it) || "google" in it || "feedburner" in it }
        assertEquals("requests to a Google host", 0, googleRequests)
        assertTrue(server.requestLog.none { "feedburner" in it || "google" in it })
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val CATALOG_FEEDS = 6
        const val EXTERNAL_FEED = 6
        const val GAZETTE_ID = "out-1"
        const val GAZETTE = "Tidewater Gazette"
        const val SITE_WITH_FEED = "/site/tidepool"
        const val SITE_WITHOUT_FEED = "/site/plain"
    }
}
