package app.newz.a11y

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.NewzApp
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.servedCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Edge headlines: 300 characters, emoji, and right-to-left fragments (phase 19 edges pass). */
internal object EdgeHeadlines {
    val LONG: String =
        (
            "Coastal towns weigh a decade-long plan to rebuild sea walls, relocate the harbor road, " +
                "restore the salt marsh and pay for it with a regional bond that voters will see on the " +
                "ballot next spring, while fishermen, shop owners and the ferry service ask how the work "
        ).padEnd(300, 'x')
            .take(300)
    const val EMOJI = "Storm season 🌀🌊 brings record surf 🏄‍♀️ and a busy week for lifeguards 🛟"
    const val RTL = "Talks resume in القاهرة as מערכת delegates arrive for the second round"

    val ALL = listOf(LONG, EMOJI, RTL)

    /** Puts the three edge headlines at the top of feeds 0, 1 and 2 of [server]. */
    fun install(server: MockFeedServer) {
        val now = Clock.System.now()
        ALL.forEachIndexed { feed, title ->
            server.feed(feed).items =
                listOf(server.item(feed, 90, now - 5.minutes, title = title)) + server.feed(feed).items
        }
    }
}

/**
 * Accessibility (SPEC S23): feed headlines at system font scale 2.0 are never clipped or
 * ellipsised, and on the nine main screens every clickable node has a spoken label and a
 * touch target of at least 48 × 48 dp.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val server = MockFeedServer(feedCount = 6).start()

    @After
    fun tearDown() {
        server.close()
    }

    /** Six feeds of two items plus the three edge headlines (15 cards, one page); the app at [fontScale]. */
    private fun start(fontScale: Float) {
        server.fill(itemsPerFeed = 2, imageEvery = 2)
        EdgeHeadlines.install(server)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        compose.setContent {
            val density = LocalDensity.current
            val configuration =
                Configuration(LocalConfiguration.current).apply { this.fontScale = fontScale }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalConfiguration provides configuration,
            ) {
                NewzApp()
            }
        }
        compose.waitForStableFeed(minCards = CARDS)
    }

    private fun titleOf(url: String): String =
        server.feeds
            .flatMap { it.items }
            .first { it.link == url }
            .title

    /** The drawn text layout whose text is exactly [title] (the headline as the app stored it). */
    private fun layoutOf(title: String): TextLayoutResult {
        val layouts =
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                    val out = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
                    out.firstOrNull()
                }
        return layouts.firstOrNull { it.layoutInput.text.text == title }
            ?: throw AssertionError(
                "no drawn text equals '${title.take(60)}'; drawn: ${layouts.map {
                    it.layoutInput.text.text
                        .take(40)
                }}",
            )
    }

    @Test
    fun feedHeadlinesAtFontScaleTwoHaveNoOverflowOrEllipsis() {
        start(fontScale = 2f)
        assertTrue(
            "the edge headlines are served",
            compose.servedCards().map { titleOf(it.url) }.containsAll(EdgeHeadlines.ALL),
        )
        val store = AppGraph.get().articleStore
        val titles = compose.servedCards().map { checkNotNull(store.article(it.id)).title }
        var checked = 0
        for (title in titles) {
            compose
                .onNodeWithTag(FeedSemantics.LIST_TAG)
                .performScrollToNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(title))
            compose.waitForIdle()
            val layout = layoutOf(title)
            val where = "'${title.take(40)}…' (${layout.lineCount} lines)"
            assertEquals("$where: drawn text is the whole headline", title, layout.layoutInput.text.text)
            assertFalse("$where has visual overflow", layout.hasVisualOverflow)
            for (line in 0 until layout.lineCount) {
                assertFalse("$where: line $line is ellipsised", layout.isLineEllipsized(line))
            }
            checked++
        }
        assertTrue("checked $checked headlines", checked >= CARDS)
        // At 2.0 the 300-character headline wraps to many lines.
        compose
            .onNodeWithTag(FeedSemantics.LIST_TAG)
            .performScrollToNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(EdgeHeadlines.LONG))
        assertTrue(layoutOf(EdgeHeadlines.LONG).lineCount >= 8)
    }

    @Test
    fun everyClickableOnTheNineScreensIsLabelledAndAtLeast48dp() {
        start(fontScale = 1f)
        seedHiddenAndSaved()
        val checked = mutableMapOf<String, Int>()

        checked["feed"] = assertTouchTargets("feed")

        open(hasText("Settings") and hasClickAction(), Screen.Settings)
        checked["settings"] = assertTouchTargets("settings")
        for ((row, screen) in listOf(
            "Sources" to Screen.Sources,
            "Topics & keywords" to Screen.Topics,
            "Hidden articles" to Screen.Hidden,
            "Saved" to Screen.Saved,
            "Suggestions" to Screen.Suggestions,
            "Backup & restore" to Screen.Backup,
        )) {
            open(hasText(row) and hasClickAction(), screen, scroll = true)
            checked[screen.route] = assertTouchTargets(screen.route)
            back(Screen.Settings)
        }

        open(hasText("Find sources") and hasClickAction(), Screen.Search, scroll = true)
        checked["search"] = assertTouchTargets("search")
        compose.onNode(hasSetTextAction()).performTextInput("Outlet")
        compose.waitForText("Outlet 1")
        checked["search results"] = assertTouchTargets("search results")

        assertEquals(10, checked.size)
        assertTrue("checked: $checked", checked.values.all { it > 0 })
    }

    private fun seedHiddenAndSaved() {
        val store = AppGraph.get().articleStore
        val cards = compose.servedCards()
        val now = Clock.System.now()
        store.hide(checkNotNull(store.article(cards.last().id)), now - 1.hours)
        store.save(checkNotNull(store.article(cards[1].id)), now - 2.hours)
    }

    private fun open(
        target: SemanticsMatcher,
        screen: Screen,
        scroll: Boolean = false,
    ) {
        val node = compose.onNode(target)
        if (scroll) node.performScrollTo()
        node.performClick()
        waitForScreen(screen)
    }

    private fun back(to: Screen) {
        compose.onNodeWithContentDescription("Back").performClick()
        waitForScreen(to)
    }

    private fun waitForScreen(screen: Screen) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(Screen.tagOf(screen))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    /** Asserts every clickable node shown has a spoken label and is at least 48 × 48 dp; returns the count. */
    private fun assertTouchTargets(screen: String): Int = compose.assertTouchTargets(screen)

    private companion object {
        const val TIMEOUT = 10_000L
        const val CARDS = 15
    }
}

/** Every clickable node on screen: a non-empty text or content description, and ≥ 48 dp each way. */
internal fun ComposeTestRule.assertTouchTargets(screen: String): Int {
    val min = with(density) { 48f * this.density } - 0.5f
    val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    assertTrue("$screen has clickable nodes", nodes.isNotEmpty())
    for (node in nodes) {
        val label = node.spokenLabel()
        assertTrue("$screen: clickable node ${node.id} has no label", label.isNotBlank())
        assertTrue("$screen: '$label' is ${node.size.width}px wide (< 48 dp)", node.size.width >= min)
        assertTrue("$screen: '$label' is ${node.size.height}px tall (< 48 dp)", node.size.height >= min)
    }
    return nodes.size
}

private fun SemanticsNode.spokenLabel(): String {
    val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    return "$texts $descriptions $editable".trim()
}
