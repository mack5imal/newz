package app.newz.onboarding

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.servedCards
import app.newz.testing.visibleCards
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

/** The topic chip labelled [name]. */
fun ComposeTestRule.chip(name: String): SemanticsNodeInteraction = onNode(hasText(name) and isToggleable())

/** The bottom bar's Continue button. */
fun ComposeTestRule.continueButton(): SemanticsNodeInteraction = onNode(hasText("Continue") and hasClickAction())

/** Taps the topic chips named [names], scrolling each into view first. */
fun ComposeTestRule.pick(vararg names: String) {
    names.forEach { chip(it).performScrollTo().performClick() }
}

private val ONBOARDING_TAG = Screen.tagOf(Screen.Onboarding)

/** First launch: pick at least 3 topics, Continue gating, and nothing saved before Continue (SPEC S4). */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 8).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    /** A first launch: fresh preferences, the picker showing. */
    private fun firstLaunch() {
        server.fill(itemsPerFeed = 4)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.write(UserPrefs())
        scenario = TestApp.launch()
        compose.waitForText("Pick at least 3 topics")
    }

    private fun relaunch() {
        scenario?.close()
        scenario = TestApp.launch()
    }

    @Test
    fun continueNeedsThreeTopics() {
        firstLaunch()
        compose.onNodeWithTag(ONBOARDING_TAG).assertIsDisplayed()
        compose.onNodeWithText("0 picked — pick at least 3").assertIsDisplayed()

        compose.pick("World", "Science")
        compose.onNodeWithText("2 picked — pick at least 3").assertIsDisplayed()
        compose.continueButton().assertIsNotEnabled()

        compose.pick("Tech")
        compose.onNodeWithText("3 picked").assertIsDisplayed()
        compose.onNodeWithText("Change them any time in Settings").assertIsDisplayed()
        compose.continueButton().assertIsEnabled()

        compose.pick("Tech")
        compose.onNodeWithText("2 picked — pick at least 3").assertIsDisplayed()
        compose.continueButton().assertIsNotEnabled()
    }

    @Test
    fun continueSeedsTheFeedWithThePickedTopics() {
        firstLaunch()
        compose.pick("World", "Science", "Tech")
        compose.continueButton().performClick()

        compose.waitForStableFeed(minCards = 12)
        val picked = setOf(TestCatalog.WORLD, TestCatalog.SCIENCE, TestCatalog.TECH)
        val served = compose.servedCards()
        val outside = served.filterNot { card -> card.topics.all { it in picked } }
        assertTrue("served cards outside the picked topics: $outside", outside.isEmpty())
        val visible = compose.visibleCards()
        assertTrue("cards are visible", visible.isNotEmpty())
        assertTrue("every visible card is in a picked topic", visible.all { card -> card.topics.all { it in picked } })
        assertEquals(picked, served.flatMap { it.topics }.toSet())

        val prefs = currentPrefs()
        assertTrue(prefs.onboarded)
        assertEquals(listOf(TestCatalog.WORLD, TestCatalog.SCIENCE, TestCatalog.TECH), prefs.followedTopics)
        val business = (0 until server.feedCount).filter { TestCatalog.topicOf(it) == TestCatalog.BUSINESS }
        business.forEach { assertFalse(TestCatalog.outletId(it) in prefs.followedOutlets) }
        (0 until server.feedCount).filterNot { it in business }.forEach {
            assertEquals(setOf(TestCatalog.feedId(it)), prefs.followedOutlets[TestCatalog.outletId(it)])
        }

        relaunch()
        compose.waitForStableFeed(minCards = 12)
        compose.onNodeWithTag(FeedSemantics.LIST_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(ONBOARDING_TAG).assertCountEquals(0)
    }

    @Test
    fun leavingBeforeContinueSavesNothing() {
        firstLaunch()
        compose.pick("World", "Science", "Tech")
        compose.continueButton().assertIsEnabled()

        relaunch()
        compose.waitForText("Pick at least 3 topics")
        compose.onNodeWithTag(ONBOARDING_TAG).assertIsDisplayed()
        compose.onNodeWithText("0 picked — pick at least 3").assertIsDisplayed()
        val prefs = currentPrefs()
        assertFalse(prefs.onboarded)
        assertTrue(prefs.followedTopics.isEmpty())
        assertTrue(prefs.followedOutlets.isEmpty())
    }
}
