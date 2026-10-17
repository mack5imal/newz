package app.newz.search

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.store.UserPrefs
import app.newz.store.followTopic
import app.newz.store.muteTopic
import app.newz.store.removeOutlet
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestCatalog.BUSINESS
import app.newz.testing.TestCatalog.HEALTH
import app.newz.testing.TestCatalog.SCIENCE
import app.newz.testing.TestCatalog.TECH
import app.newz.testing.TestCatalog.WORLD
import app.newz.testing.TestPrefs
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.nav.Screen
import app.newz.ui.settings.SourcesSemantics
import app.newz.ui.suggestions.SuggestionsSemantics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/**
 * Suggested for you (design `suggestions`, SPEC S18): ranked outlets and topics after onboarding,
 * Follow lands in Sources, and the empty state leads to search.
 */
@RunWith(AndroidJUnit4::class)
class SuggestionsScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = FEED_TOPICS.size).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /**
     * Outlet n has one feed carrying [FEED_TOPICS] n. Onboarding with World, Science and Tech
     * follows out-0..2; out-3..7 cover Business or Health, which out-0 and out-1 also cover, so
     * they share stories with the user's sources, and Business and Health ride along with
     * followed topics.
     */
    private val catalog: Catalog by lazy {
        Catalog(
            topics = TestCatalog.topics,
            outlets =
                FEED_TOPICS.indices.map {
                    Outlet(
                        "out-$it",
                        "Outlet $it",
                        "outlet$it.test",
                        PaywallStatus.NONE,
                        0.5,
                    )
                },
            feeds =
                FEED_TOPICS.mapIndexed {
                    i,
                    topics,
                    ->
                    CatalogFeed("feed-$i", "out-$i", "Main", server.feedUrl(i), topics)
                },
        )
    }

    private fun start(adjust: (UserPrefs) -> UserPrefs = { it }) {
        server.fill(itemsPerFeed = 2)
        TestApp.install(catalog)
        TestPrefs.write(adjust(TestPrefs.onboarded(WORLD, SCIENCE, TECH)))
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 1)
    }

    private fun waitForScreen(screen: Screen) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(Screen.tagOf(screen))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun count(matcher: SemanticsMatcher): Int = compose.onAllNodes(matcher).fetchSemanticsNodes().size

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

    @Test
    fun suggestionsAreRankedAndFollowingOneListsItInSources() {
        start()
        openFromSettings("Suggestions", Screen.Suggestions)
        compose.waitUntil(TIMEOUT) { count(hasContentDescription("Rank ", substring = true)) >= MIN_ITEMS }
        assertTrue(count(hasContentDescription("Rank 1")) >= 1)
        compose
            .onNode(hasText("Worked out on this phone from what you follow and read. Nothing is sent anywhere."))
            .assertExists()

        val row = hasTestTag(SuggestionsSemantics.outlet(SUGGESTED))
        compose.onNode(row).performScrollTo()
        compose.onNode(hasAnyAncestor(row) and hasText("Follow") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { count(hasAnyAncestor(row) and hasText("Following")) > 0 }

        compose.onNodeWithContentDescription("Back").performClick()
        openFromSettings("Sources", Screen.Sources)
        compose.waitUntil(TIMEOUT) { count(hasTestTag(SourcesSemantics.row(SUGGESTED))) == 1 }
    }

    @Test
    fun withEveryCandidateRemovedMutedOrFollowedFindSourcesOpensSearch() {
        start { onboarded ->
            val now = Clock.System.now()
            val followed = listOf("out-3", "out-4").associateWith { setOf(it.replace("out", "feed")) }
            listOf("out-5", "out-6", "out-7")
                .fold(onboarded.copy(followedOutlets = onboarded.followedOutlets + followed)) { p, id ->
                    p.removeOutlet(id, now)
                }.followTopic(BUSINESS)
                .muteTopic(HEALTH, now)
        }
        openFromSettings("Suggestions", Screen.Suggestions)
        compose.waitForText("No suggestions right now", TIMEOUT)
        assertTrue(count(hasContentDescription("Rank ", substring = true)) == 0)
        compose.onNode(hasText("Find sources") and hasClickAction()).performClick()
        waitForScreen(Screen.Search)
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val MIN_ITEMS = 5
        const val SUGGESTED = "out-3"
        val FEED_TOPICS =
            listOf(
                listOf(WORLD, BUSINESS),
                listOf(SCIENCE, HEALTH),
                listOf(TECH),
                listOf(BUSINESS),
                listOf(HEALTH),
                listOf(BUSINESS),
                listOf(HEALTH),
                listOf(BUSINESS),
            )
    }
}
