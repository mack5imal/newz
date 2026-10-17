package app.newz.journey

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.MainActivity
import app.newz.onboarding.continueButton
import app.newz.onboarding.pick
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.feedOf
import app.newz.testing.openCardMenu
import app.newz.testing.servedCards
import app.newz.testing.tapText
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.feed.FeedSemantics
import app.newz.ui.nav.Screen
import app.newz.ui.search.SearchSemantics
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The regression journey (phase 19): onboarding → feed → mute a keyword → Settings → restore
 * it → search → follow an outlet → back up → reset → import, checking the feed and the stored
 * choices at each step.
 */
@RunWith(AndroidJUnit4::class)
class JourneyTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 8).start()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var stubbed = false

    @After
    fun tearDown() {
        releaseStubs()
        scenario?.close()
        server.close()
    }

    @Test
    fun onboardingFeedMuteRestoreSearchFollowBackupImport() {
        // Onboarding: a first launch, three topics.
        server.fill(itemsPerFeed = 2)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.write(UserPrefs())
        scenario = TestApp.launch()
        compose.waitForText("Pick at least 3 topics")
        compose.pick("World", "Science", "Tech")
        compose.continueButton().performClick()

        // Feed: only the picked topics' outlets (business feeds 3 and 7 are not followed).
        compose.waitForStableFeed(minCards = 12)
        assertTrue(compose.servedCards().none { feedOf(it.url) in BUSINESS_FEEDS })

        // Mute a keyword from a card's menu: its card leaves the feed.
        val card = compose.servedCards().first()
        val title = titleOf(card.url)
        val keyword = title.substringBefore(' ')
        compose.openCardMenu(title)
        compose.tapText("Mute a keyword…")
        compose.waitForText("From this headline")
        compose.onNode(hasSetTextAction()).performTextInput(keyword)
        compose.onNode(hasText("Mute") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { compose.servedCards().none { it.url == card.url } }
        assertEquals(listOf(keyword), currentPrefs().mutedKeywords.map { it.text })

        // Settings › Topics & keywords: restore (unmute) it; the card comes back.
        compose.waitUntil(TIMEOUT) { !exists(hasText("Undo")) }
        openFromFeed("Settings", Screen.Settings)
        open("Topics & keywords", Screen.Topics)
        compose.onNodeWithContentDescription("Unmute $keyword").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { currentPrefs().mutedKeywords.isEmpty() }
        back(Screen.Settings)

        // Search: follow a business outlet.
        open("Find sources", Screen.Search)
        compose.onNodeWithTag(SearchSemantics.FIELD).performTextReplacement(TestCatalog.outletName(3))
        val row = hasTestTag(SearchSemantics.outlet(TestCatalog.outletId(3)))
        waitFor(row)
        compose.onNode(hasAnyAncestor(row) and hasText("Follow") and hasClickAction()).performClick()
        waitFor(hasAnyAncestor(row) and hasText("Following"))
        assertTrue(currentPrefs().followedOutlets[TestCatalog.outletId(3)].orEmpty().isNotEmpty())
        back(Screen.Settings)
        back(null)
        compose.waitUntil(TIMEOUT) { compose.servedCards().any { it.url == card.url } }

        // Backup: export to a file.
        val chosen = currentPrefs()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "journey-backup.json")
        file.delete()
        openFromFeed("Settings", Screen.Settings)
        open("Backup & restore", Screen.Backup)
        stubPickers(file)
        tap("Export to a file")
        compose.waitForText("Backup saved", TIMEOUT)
        assertTrue(file.length() > 0)

        // Reset: a fresh database and a first-launch state, then import from Backup.
        releaseStubs()
        scenario?.close()
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded(TestCatalog.WORLD)
        assertFalse(currentPrefs().followedOutlets.containsKey(TestCatalog.outletId(3)))
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 1)
        openFromFeed("Settings", Screen.Settings)
        open("Backup & restore", Screen.Backup)
        stubPickers(file)
        tap("Choose a backup file")
        compose.waitForText("Backup imported", TIMEOUT)
        assertEquals(chosen, currentPrefs())

        // Back on the feed with the restored choices.
        releaseStubs()
        back(Screen.Settings)
        back(null)
        assertEquals(chosen.followedTopics, currentPrefs().followedTopics)
    }

    private fun titleOf(url: String): String =
        server.feeds
            .flatMap { it.items }
            .first { it.link == url }
            .title

    private fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT) { exists(matcher) }
    }

    private fun waitForScreen(screen: Screen) = waitFor(hasTestTag(Screen.tagOf(screen)))

    private fun openFromFeed(
        label: String,
        screen: Screen,
    ) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        waitForScreen(screen)
    }

    private fun open(
        row: String,
        screen: Screen,
    ) {
        tap(row)
        waitForScreen(screen)
    }

    private fun tap(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    }

    /** Back once; [to] null means back on the feed. */
    private fun back(to: Screen?) {
        compose.onNodeWithContentDescription("Back").performClick()
        if (to != null) waitForScreen(to) else compose.waitForStableFeed(minCards = 1)
        compose.waitForIdle()
        if (to == null) compose.onNodeWithTag(FeedSemantics.LIST_TAG).assertExists()
    }

    /** Both document pickers answer with [file]; any other outside intent is swallowed. */
    private fun stubPickers(file: File) {
        releaseStubs()
        Intents.init()
        stubbed = true
        val result = ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        intending(not(isInternal())).respondWith(ActivityResult(Activity.RESULT_OK, null))
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)
    }

    private fun releaseStubs() {
        if (stubbed) Intents.release()
        stubbed = false
    }

    private companion object {
        const val TIMEOUT = 15_000L
        val BUSINESS_FEEDS = setOf(3, 7)
    }
}
