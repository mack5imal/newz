package app.newz.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.feedOf
import app.newz.testing.servedCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Local zip sheet in onboarding: gating, validation, on-device resolution and privacy (SPEC S29). */
@RunWith(AndroidJUnit4::class)
class ZipCodeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 10).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Feeds 0-5 are topic feeds; 6-7 are Seattle's local feeds and 8-9 Chicago's. */
    private val seattleFeeds = setOf(6, 7)
    private val chicagoFeeds = setOf(8, 9)

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    private fun firstLaunch() {
        server.fill(itemsPerFeed = 4)
        val catalog =
            TestCatalog.build(
                server,
                localMetro = {
                    when (it) {
                        in seattleFeeds -> TestCatalog.SEATTLE
                        in chicagoFeeds -> TestCatalog.CHICAGO
                        else -> null
                    }
                },
            )
        TestApp.install(catalog)
        TestPrefs.write(UserPrefs())
        scenario = TestApp.launch()
        compose.waitForText("Pick at least 3 topics")
    }

    private fun openZipSheet() {
        compose.pick("Local")
        compose.waitForText("Your zip code")
    }

    private fun typeZip(zip: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(zip)
    }

    private fun tapSave() {
        compose.onNode(hasText("Save") and hasClickAction()).performClick()
    }

    private fun assertNothingSaved() {
        val prefs = currentPrefs()
        assertNull(prefs.zip)
        assertNull(prefs.localMetroId)
        assertFalse(prefs.onboarded)
    }

    @Test
    fun localNeedsASavedZip() {
        firstLaunch()
        compose.pick("World", "Science", "Tech")
        compose.continueButton().assertIsEnabled()

        openZipSheet()
        compose.chip("Local").assertIsOn()
        compose.continueButton().assertIsNotEnabled()

        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your zip code").fetchSemanticsNodes().isEmpty() }
        compose.chip("Local").assertIsOff()
        compose.continueButton().assertIsEnabled()
        assertNothingSaved()
    }

    @Test
    fun zipIsValidatedOnTheDevice() {
        firstLaunch()
        compose.pick("World", "Science", "Tech")
        openZipSheet()

        typeZip("9810")
        tapSave()
        compose.waitForText("Enter a 5-digit zip code")
        assertNothingSaved()

        typeZip("abcde")
        compose.onAllNodesWithText("Enter a 5-digit zip code").assertCountEquals(0)
        tapSave()
        compose.waitForText("Enter a 5-digit zip code")
        assertNothingSaved()

        typeZip("00000")
        tapSave()
        compose.waitForText("We couldn’t find that zip code")
        compose.onAllNodesWithText("Enter a 5-digit zip code").assertCountEquals(0)
        compose.continueButton().assertIsNotEnabled()
        assertNothingSaved()

        typeZip("98101")
        compose.waitForText("Showing local news from Seattle")
        compose.onAllNodesWithText("We couldn’t find that zip code").assertCountEquals(0)
        tapSave()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your zip code").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Showing local news from Seattle").assertIsDisplayed()
        compose.chip("Local").assertIsOn()
        compose.continueButton().assertIsEnabled()

        compose.continueButton().performClick()
        compose.waitUntil(5_000) { currentPrefs().onboarded }
        val prefs = currentPrefs()
        assertEquals("98101", prefs.zip)
        assertEquals(TestCatalog.SEATTLE, prefs.localMetroId)
        assertEquals(
            listOf(TestCatalog.WORLD, TestCatalog.SCIENCE, TestCatalog.TECH, TestCatalog.LOCAL),
            prefs.followedTopics,
        )
    }

    @Test
    fun localCardsComeFromTheZipsMetroAndTheZipStaysOnThePhone() {
        firstLaunch()
        compose.pick("World", "Science", "Tech")
        openZipSheet()
        typeZip("98101")
        compose.waitForText("Showing local news from Seattle")
        tapSave()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your zip code").fetchSemanticsNodes().isEmpty() }
        compose.continueButton().performClick()

        compose.waitForStableFeed(minCards = 12)
        val local = compose.servedCards().filter { TestCatalog.LOCAL in it.topics }
        assertTrue("the feed has Local cards", local.isNotEmpty())
        local.forEach { assertTrue("${it.url} is a Seattle feed's", feedOf(it.url) in seattleFeeds) }
        assertEquals(seattleFeeds, local.map { feedOf(it.url) }.toSet())

        val requests = server.requestLog
        assertTrue("the app fetched feeds", requests.isNotEmpty())
        requests.forEach { assertFalse("the zip left the phone: $it", "98101" in it) }
        chicagoFeeds.forEach { feed ->
            assertFalse("Chicago feed $feed was fetched", requests.any { "/feed/$feed.xml" in it })
        }
    }
}
