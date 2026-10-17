package app.newz.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.AppGraph
import app.newz.NewzApp
import app.newz.R
import app.newz.catalog.Catalog
import app.newz.onboarding.pick
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.openCardMenu
import app.newz.testing.tapText
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Captures each of the 14 approved design screens in its default state (SPEC S34): deterministic
 * fixtures, light theme, 412 dp wide, saved as `design-check/<id>.png` under the app's external
 * files directory. `scripts/capture-design-screens.sh` runs this class and pulls the PNGs; the
 * side-by-side with `design/screens/<id>.png` is the owner's.
 */
@RunWith(AndroidJUnit4::class)
class DesignCaptureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val server = MockFeedServer(feedCount = OUTLETS.size).start()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        server.close()
    }

    @Test fun feed() = capture("feed") {}

    @Test
    fun cardMenu() =
        capture("card-menu") {
            compose.openCardMenu(STORY_A)
        }

    @Test
    fun keywordMute() =
        capture("keyword-mute") {
            compose.openCardMenu(STORY_A)
            compose.tapText("Mute a keyword…")
            compose.waitForText("From this headline")
            // Pick the first suggestion from the headline, as drawn.
            compose
                .onAllNodes(hasText("Antarctica", substring = true) and hasClickAction() and !hasSetTextAction())
                .onFirst()
                .performClick()
        }

    @Test
    fun storyGroup() =
        capture("story-group") {
            compose
                .onNodeWithTag(FeedSemantics.LIST_TAG)
                .performScrollToNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(STORY_B))
            compose.onNode(hasText("+1 source") and hasClickAction()).performClick()
            compose.waitForText("Same story · 2 outlets")
        }

    @Test
    fun onboarding() =
        capture("onboarding", firstLaunch = true) {
            compose.pick("World", "Science")
        }

    @Test fun settings() = capture("settings") { openSettings() }

    @Test fun settingsSources() = capture("settings-sources") { openSettings("Sources", Screen.Sources) }

    @Test fun settingsTopics() = capture("settings-topics") { openSettings("Topics & keywords", Screen.Topics) }

    @Test fun settingsHidden() = capture("settings-hidden") { openSettings("Hidden articles", Screen.Hidden) }

    @Test fun saved() = capture("saved") { openSettings("Saved", Screen.Saved) }

    @Test fun search() = capture("search") { openSettings("Find sources", Screen.Search) }

    @Test fun suggestions() = capture("suggestions") { openSettings("Suggestions", Screen.Suggestions) }

    @Test fun backup() = capture("backup") { openSettings("Backup & restore", Screen.Backup) }

    @Test
    fun appIcon() {
        val drawable = checkNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PX, ICON_PX)
        drawable.draw(Canvas(bitmap))
        save("app-icon", bitmap)
    }

    /** Launches the app on the fixtures (light, 412 dp wide), runs [navigate], and saves the screen as [id]. */
    private fun capture(
        id: String,
        firstLaunch: Boolean = false,
        navigate: () -> Unit,
    ) {
        fillFixtures()
        TestApp.install(catalog(), clock = { NOW })
        if (firstLaunch) TestPrefs.write(UserPrefs()) else TestPrefs.onboarded()
        compose.setContent {
            val widthPx = context.resources.displayMetrics.widthPixels
            val configuration =
                Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                    fontScale = 1f
                    screenWidthDp = WIDTH_DP
                }
            CompositionLocalProvider(
                LocalDensity provides Density(widthPx / WIDTH_DP.toFloat(), 1f),
                LocalConfiguration provides configuration,
            ) {
                NewzApp()
            }
        }
        if (firstLaunch) {
            compose.waitForText("Pick at least 3 topics")
        } else {
            compose.waitForStableFeed(minCards = 10)
            seedHiddenAndSaved()
        }
        navigate()
        settle()
        save(id, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun openSettings(
        row: String? = null,
        screen: Screen? = null,
    ) {
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        waitFor(hasTestTag(Screen.tagOf(Screen.Settings)))
        if (row != null && screen != null) {
            compose.onNode(hasText(row) and hasClickAction()).performScrollTo().performClick()
            waitFor(hasTestTag(Screen.tagOf(screen)))
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Lets images load and sheet animations finish before the capture. */
    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun save(
        id: String,
        bitmap: Bitmap,
    ) {
        val dir = File(context.getExternalFilesDir(null), "design-check").apply { mkdirs() }
        val file = File(dir, "$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$id.png written", file.length() > 0)
    }

    /** Catalog outlets get the fixture names instead of "Outlet n". */
    private fun catalog(): Catalog {
        val base = TestCatalog.build(server)
        return Catalog(
            topics = base.topics,
            outlets = base.outlets.mapIndexed { i, outlet -> outlet.copy(name = OUTLETS[i]) },
            feeds = base.feeds,
            metros = base.metros,
        )
    }

    /** One hidden and one saved article, for the Hidden and Saved screens. */
    private fun seedHiddenAndSaved() {
        val store = AppGraph.get().articleStore
        val byTitle = store.articles().associateBy { it.title }
        store.hide(checkNotNull(byTitle[HIDDEN]), NOW - 3.hours)
        store.save(checkNotNull(byTitle[SAVED]), NOW - 2.hours)
    }

    /**
     * Story A from four outlets ("+3 sources"), story B from two ("+1 source"), then each outlet's
     * own headlines; every other item has an image, so some cards show the no-image panel.
     */
    private fun fillFixtures() {
        OUTLETS.indices.forEach { f ->
            val own =
                OWN_HEADLINES[f].mapIndexed { i, title ->
                    server.item(f, i, NOW - ages(f, i), withImage = (f + i) % 2 == 0, title = title)
                }
            val shared =
                when (f) {
                    in 0..3 -> {
                        listOf(
                            server.item(f, 50, NOW - (20 + f * 10).minutes, withImage = f == 0, title = STORY_A),
                        )
                    }

                    else -> {
                        listOf(server.item(f, 60, NOW - (2 * f).minutes, withImage = true, title = STORY_B))
                    }
                }
            server.feed(f).items = shared + own
        }
    }

    private fun ages(
        feed: Int,
        item: Int,
    ): Duration = 1.hours * (item + 1) + (feed * 7).minutes

    private companion object {
        const val WIDTH_DP = 412
        const val ICON_PX = 432
        const val TIMEOUT = 15_000L
        const val SETTLE_MS = 1_500L

        /** One instant for every fixture date and the app's clock, so ages read the same each run. */
        val NOW: Instant = Clock.System.now()

        val OUTLETS =
            listOf(
                "The Harbor Ledger",
                "Northern Science Review",
                "Circuit Daily",
                "Market Street Journal",
                "The Evening Courier",
                "Field & Lab",
            )

        const val STORY_A = "Deep-sea survey maps the currents carrying warm water toward Antarctica’s ice shelves"
        const val STORY_B = "City council approves the new riverside library budget"
        const val HIDDEN = "Ferry timetable changes for the winter season"
        const val SAVED = "How a small lab is rethinking battery recycling"

        val OWN_HEADLINES =
            listOf(
                listOf("Port expansion vote delayed after record turnout at hearing", HIDDEN),
                listOf(
                    "Telescope array spots a comet heading for a close pass next spring",
                    "Bird migration starts weeks earlier than it did a decade ago",
                ),
                listOf(SAVED, "Open-source maps project reaches ten million edits"),
                listOf(
                    "Shipping costs ease as new routes open through the canal",
                    "Local bakery chain expands to three new neighborhoods",
                ),
                listOf(
                    "Night markets return to the waterfront this weekend",
                    "Rail line upgrade promises quieter trains by summer",
                ),
                listOf(
                    "Coral nursery reports its best growing season yet",
                    "Students build a weather station on the school roof",
                ),
            )
    }
}
