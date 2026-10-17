package app.newz.settings

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.catalog.Catalog
import app.newz.catalog.PaywallStatus
import app.newz.store.MutedKeyword
import app.newz.store.TextSize
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.feedOf
import app.newz.testing.servedCards
import app.newz.testing.tapText
import app.newz.testing.visibleCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedCardInfo
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import app.newz.ui.settings.SourcesSemantics
import app.newz.ui.settings.TopicsSemantics
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Settings controls: the paywall filter, the Sources rows' paywall tags, reading history, text
 * size, a failing feed, the Local zip, and touch targets (SPEC S12, S19, S23, S25, S29). External
 * intents (opening an article) are stubbed.
 */
@RunWith(AndroidJUnit4::class)
class SettingsControlsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 6).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        Intents.init()
        intending(not(isInternal())).respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        // Release first: ActivityScenario.close() starts a helper activity the stub would catch.
        Intents.release()
        scenario?.close()
        server.close()
    }

    private fun start(
        catalog: Catalog = TestCatalog.build(server),
        minCards: Int = 18,
        prefs: () -> Unit = { TestPrefs.onboarded() },
    ) {
        TestApp.install(catalog)
        prefs()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = minCards)
    }

    private fun titleOf(card: FeedCardInfo): String =
        server.feeds
            .flatMap { it.items }
            .first { it.link == card.url }
            .title

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

    private fun backToFeed() {
        while (compose.onAllNodes(hasTestTag(FeedSemantics.LIST_TAG)).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
        }
        compose.waitForStableFeed(minCards = 1)
    }

    private fun waitUntilServed(
        message: String,
        condition: (List<FeedCardInfo>) -> Boolean,
    ) {
        compose.waitUntil(TIMEOUT) { condition(compose.servedCards()) }
        assertTrue(message, condition(compose.servedCards()))
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun paywallFilterHidesAndShowsAHardOutlet() {
        server.fill(itemsPerFeed = 3)
        val catalog = TestCatalog.build(server) { if (it == 2) PaywallStatus.HARD else PaywallStatus.NONE }
        start(catalog, minCards = 15)
        assertTrue(compose.servedCards().none { feedOf(it.url) == 2 })

        openSettings()
        compose.onNodeWithContentDescription("Paywall filter").performClick()
        compose.waitUntil(TIMEOUT) { !currentPrefs().paywallFilterOn }
        backToFeed()
        waitUntilServed("filter off: the hard outlet shows") { cards -> cards.count { feedOf(it.url) == 2 } == 3 }

        openSettings()
        compose.onNodeWithContentDescription("Paywall filter").performClick()
        compose.waitUntil(TIMEOUT) { currentPrefs().paywallFilterOn }
        backToFeed()
        waitUntilServed("filter on: the hard outlet is hidden") { cards -> cards.none { feedOf(it.url) == 2 } }
    }

    @Test
    fun sourcesRowsShowPaywallAndMeteredTags() {
        server.fill(itemsPerFeed = 2)
        val built =
            TestCatalog.build(server) {
                when (it) {
                    0 -> PaywallStatus.HARD
                    1 -> PaywallStatus.METERED
                    else -> PaywallStatus.NONE
                }
            }
        val catalog =
            Catalog(
                topics = built.topics,
                outlets = built.outlets.map { if (it.id == TestCatalog.outletId(0)) it.copy(name = HARBOR) else it },
                feeds = built.feeds,
                metros = built.metros,
            )
        start(catalog, minCards = 6) {
            val p = TestPrefs.onboarded()
            TestPrefs.write(p.copy(paywallFilterOn = false))
        }
        openSettings("Sources")

        fun inRow(
            feed: Int,
            matcher: SemanticsMatcher,
        ) = exists(matcher and hasAnyAncestor(hasTestTag(SourcesSemantics.row(TestCatalog.outletId(feed)))))
        val hiddenNote = hasText("hidden while the paywall filter is on", substring = true)

        assertTrue("hard: Paywall tag", inRow(0, hasText("Paywall")))
        assertTrue("hard: hidden note", inRow(0, hiddenNote))
        assertTrue("metered: Metered tag", inRow(1, hasText("Metered")))
        assertTrue("metered: no hidden note", !inRow(1, hiddenNote))
        assertTrue("none: no Paywall tag", !inRow(2, hasText("Paywall")))
        assertTrue("none: no Metered tag", !inRow(2, hasText("Metered")))
        assertTrue("none: no hidden note", !inRow(2, hiddenNote))
        // The first outlet is expanded to its section feeds.
        assertTrue(exists(hasContentDescription("The Harbor Ledger section feeds")))
    }

    @Test
    fun betterPicturesIsOffByDefaultAndToggles() {
        server.fill(itemsPerFeed = 3)
        start()
        assertTrue(!currentPrefs().betterPictures)

        openSettings()
        val switch = hasContentDescription("Better pictures from article pages") and hasClickAction()
        compose.onNode(switch).performScrollTo().assertIsOff()
        compose.onNode(switch).performClick()
        compose.waitUntil(TIMEOUT) { currentPrefs().betterPictures }
        compose.onNode(switch).assertIsOn()
        compose.onNode(switch).performClick()
        compose.waitUntil(TIMEOUT) { !currentPrefs().betterPictures }

        // About says what the switch reads.
        compose.onNode(hasText("About") and hasClickAction()).performScrollTo().performClick()
        waitForScreen(Screen.About)
        compose
            .onNode(hasText("it also reads the start of an article’s page to find its picture", substring = true))
            .assertExists()
    }

    @Test
    fun readingHistoryOffRecordsNothingAndClearEmptiesIt() {
        server.fill(itemsPerFeed = 3)
        start()
        val store = AppGraph.get().articleStore

        openSettings()
        compose.onNodeWithContentDescription("Reading history").performClick()
        compose.waitUntil(TIMEOUT) { !currentPrefs().readingHistoryOn }
        backToFeed()
        openFirstCard()
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        assertEquals("history off: nothing recorded", 0, store.listHistory().size)

        openSettings()
        compose.onNodeWithContentDescription("Reading history").performClick()
        compose.waitUntil(TIMEOUT) { currentPrefs().readingHistoryOn }
        backToFeed()
        openFirstCard()
        compose.waitUntil(TIMEOUT) { store.listHistory().size == 1 }

        openSettings()
        // Clearing asks first: Cancel keeps the history, Clear history clears it.
        val clear = hasText("Clear reading history") and hasClickAction()
        // The compact button sits centred in its row and keeps a 48 dp touch target (B13).
        val button = compose.onNode(clear).performScrollTo().fetchSemanticsNode()
        val screenWidth =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .size.width
        val centre = button.positionInRoot.x + button.size.width / 2f
        assertTrue("centred: $centre of $screenWidth", abs(centre - screenWidth / 2f) <= 2f)
        val minPx =
            48f *
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext.resources.displayMetrics.density - 0.5f
        assertTrue("48 dp tall: ${button.size.height}", button.size.height >= minPx)
        compose.onNode(clear).performClick()
        compose.waitForText("Clear reading history?")
        compose
            .onNodeWithText(
                "This removes what you’ve opened from this phone. It can’t be undone.",
            ).assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { !exists(hasText("Clear reading history?")) }
        assertEquals("cancel keeps history", 1, store.listHistory().size)

        compose.onNode(clear).performScrollTo().performClick()
        compose.tapText("Clear history")
        compose.waitForText("Reading history cleared")
        compose.waitUntil(TIMEOUT) { store.listHistory().isEmpty() }
        assertTrue("no Undo", !exists(hasText("Undo")))
    }

    private fun openFirstCard() {
        val first = compose.visibleCards().first()
        compose.onNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(titleOf(first))).performClick()
    }

    @Test
    fun extraLargeTextMakesFeedHeadlinesLarger() {
        server.fill(itemsPerFeed = 3)
        start()
        val title = titleOf(compose.visibleCards().first())
        val before = headlineFontSize(title)

        openSettings()
        compose.onNode(hasText("Extra large") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { currentPrefs().textSize == TextSize.EXTRA_LARGE }
        backToFeed()
        val after = headlineFontSize(title)
        assertTrue("Extra large headline ($after sp) is larger than Default ($before sp)", after > before)
    }

    private fun headlineFontSize(title: String): Float {
        compose.waitUntil(TIMEOUT) { exists(hasText(title)) }
        val node = compose.onNode(hasText(title), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts
            .single()
            .layoutInput.style.fontSize.value
    }

    @Test
    fun aFailingFeedIsPinnedInSourcesWithRetryAndRemove() {
        server.fill(itemsPerFeed = 3)
        server.feed(4).status = 500
        start(minCards = 15)

        openSettings()
        compose.waitForText("6 followed · 1 not loading")
        compose.onNode(hasText("Sources") and hasClickAction()).performClick()
        waitForScreen(Screen.Sources)
        val row = hasAnyAncestor(hasTestTag(SourcesSemantics.row(TestCatalog.outletId(4))))
        compose.waitUntil(TIMEOUT) { exists(hasText("Not loading since", substring = true) and row) }
        assertTrue(exists(hasText("Retry") and hasClickAction() and row))
        assertTrue(exists(hasText("Remove") and hasClickAction() and row))

        // Retry fetches that one feed only.
        server.feed(4).status = 200
        val feedRequests = { server.requestLog.count { "/feed/" in it } }
        val before = feedRequests()
        compose.onNode(hasText("Retry") and hasClickAction() and row).performClick()
        compose.waitUntil(TIMEOUT) { !exists(hasText("Not loading since", substring = true)) }
        assertEquals("Retry fetched one feed", before + 1, feedRequests())
        backToFeed()
        waitUntilServed("the feed's cards arrive") { cards -> cards.count { feedOf(it.url) == 4 } == 3 }
    }

    @Test
    fun changingTheZipSwitchesLocalCardsToTheNewMetro() {
        server.fill(itemsPerFeed = 2)
        val catalog =
            TestCatalog.build(server, localMetro = { i ->
                when (i) {
                    4 -> TestCatalog.SEATTLE
                    5 -> TestCatalog.CHICAGO
                    else -> null
                }
            })
        start(catalog, minCards = 10) {
            val p = TestPrefs.onboarded(*(TestCatalog.FEED_TOPICS + TestCatalog.LOCAL).toTypedArray())
            val local = setOf(TestCatalog.outletId(4), TestCatalog.outletId(5))
            TestPrefs.write(
                p.copy(
                    followedOutlets = p.followedOutlets - local,
                    zip = "98101",
                    localMetroId = TestCatalog.SEATTLE,
                ),
            )
        }
        assertTrue(compose.servedCards().any { feedOf(it.url) == 4 })
        assertTrue(compose.servedCards().none { feedOf(it.url) == 5 })

        openSettings("Topics & keywords")
        // Local is the fifth followed topic: past the first three.
        compose.onNode(hasText("Show all 5") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("98101 · Seattle") and hasClickAction()).performScrollTo().performClick()
        compose.waitForText("Your zip code")
        compose.onNode(hasSetTextAction()).performTextReplacement("60601")
        compose.onNode(hasText("Save") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { !exists(hasText("Your zip code")) }
        compose.waitForText("60601 · Chicago")
        assertEquals("60601", currentPrefs().zip)
        assertEquals(TestCatalog.CHICAGO, currentPrefs().localMetroId)

        backToFeed()
        waitUntilServed("Chicago local cards replace Seattle's") { cards ->
            cards.count { feedOf(it.url) == 5 } == 2 && cards.none { feedOf(it.url) == 4 }
        }
    }

    @Test
    fun everyClickableIconIsLabelledAndAtLeast48dp() {
        server.fill(itemsPerFeed = 2)
        start(minCards = 6) {
            val p = TestPrefs.onboarded()
            val now = Clock.System.now()
            TestPrefs.write(
                p.copy(
                    customTopics = listOf("harbour"),
                    mutedTopics = mapOf(TestCatalog.HEALTH to now),
                    mutedKeywords = listOf(MutedKeyword("Spoilers", now)),
                    removedOutlets = mapOf(TestCatalog.outletId(5) to now - 2.days),
                    paywallFlags = mapOf(TestCatalog.outletId(3) to now),
                ),
            )
        }
        openSettings()
        assertTouchTargets("settings")
        compose.onNode(hasText("Sources") and hasClickAction()).performClick()
        waitForScreen(Screen.Sources)
        assertTouchTargets("settings-sources")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Topics & keywords") and hasClickAction()).performClick()
        waitForScreen(Screen.Topics)
        assertTrue(exists(hasTestTag(TopicsSemantics.row("custom:harbour"))))
        assertTouchTargets("settings-topics")
    }

    /** Every clickable node with no text needs a spoken label; every clickable node is at least 48 dp. */
    private fun assertTouchTargets(screen: String) {
        val density =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.displayMetrics.density
        val min = 48 * density - 0.5f
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("$screen has clickable nodes", nodes.size > 3)
        var icons = 0
        nodes.forEach { node ->
            val label = node.label()
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            if (texts.isEmpty()) {
                icons++
                assertTrue("$screen: a clickable icon has no content description", label.isNotBlank())
                assertTrue("$screen: '$label' is ${node.size.width}px wide", node.size.width >= min)
            }
            assertTrue("$screen: '$label' is ${node.size.height}px tall", node.size.height >= min)
        }
        assertTrue("$screen has a clickable icon", icons > 0)
    }

    private fun SemanticsNode.label(): String =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ") +
            config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")

    private companion object {
        const val TIMEOUT = 10_000L
        const val SETTLE_MS = 1_000L
        const val HARBOR = "The Harbor Ledger"
    }
}
