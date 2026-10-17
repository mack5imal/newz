package app.newz.settings

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.newz.AppGraph
import app.newz.MainActivity
import app.newz.discover.customFeedId
import app.newz.store.CustomFeed
import app.newz.store.Level
import app.newz.store.MutedKeyword
import app.newz.store.TextSize
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.currentPrefs
import app.newz.testing.servedCards
import app.newz.testing.waitForStableFeed
import app.newz.testing.waitForText
import app.newz.ui.nav.Screen
import org.hamcrest.Matchers.not
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private const val TIMEOUT = 15_000L

/**
 * Backup & restore (SPEC S24): export with and without reading history through the
 * create-document picker, a full replace on import after the app data is reset, and a malformed
 * file that changes nothing. The system pickers are stubbed with Espresso-Intents, answering with
 * a file in the app's cache.
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 6).start()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var stubbed = false
    private val cacheDir: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    @After
    fun tearDown() {
        // Release first: ActivityScenario.close() starts a helper activity the stub would catch.
        releaseStubs()
        scenario?.close()
        server.close()
    }

    private fun start() {
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 18)
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

    private fun testFile(name: String): File = File(cacheDir, "backup-test-$name.json").also { it.delete() }

    private fun openBackup() {
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        waitForScreen(Screen.Settings)
        compose.onNode(hasText("Backup & restore") and hasClickAction()).performScrollTo().performClick()
        waitForScreen(Screen.Backup)
    }

    private fun waitForScreen(screen: Screen) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(Screen.tagOf(screen))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    }

    /** Taps "Export to a file" with the picker answering [file]; returns the written JSON. */
    private fun exportTo(file: File): String {
        stubPickers(file)
        tap("Export to a file")
        compose.waitUntil(TIMEOUT) { file.exists() && file.length() > 0 }
        compose.waitForText("Backup saved", TIMEOUT)
        return file.readText()
    }

    private fun epochMillisNow(): Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    /**
     * Gives every kind of choice a value: levels, a keyword topic, a muted topic and keyword, a
     * removed outlet, a paywall flag, settings, a zip; and a hidden, a saved and a history row
     * plus a custom feed in the store.
     */
    private fun seedChoices(): UserPrefs {
        val graph = AppGraph.get()
        val store = graph.articleStore
        val now = epochMillisNow()
        val cards = compose.servedCards()
        val hidden = checkNotNull(store.article(cards[0].id))
        val saved = checkNotNull(store.article(cards[1].id))
        store.hide(hidden, now - 2.hours)
        store.save(saved, now - 1.hours)
        store.recordOpen(cards[2].id, saved.outletId, saved.topics, now - 3.hours)
        store.recordOpen(cards[3].id, saved.outletId, saved.topics, now - 4.hours)
        val url = "http://127.0.0.1:9/custom.xml"
        store.addCustomFeed(CustomFeed(customFeedId(url), url, "Custom Gazette", null, "rss", now))
        val base = currentPrefs()
        return TestPrefs.write(
            base.copy(
                topicLevels = mapOf(TestCatalog.SCIENCE to Level.MORE, TestCatalog.TECH to Level.MUCH_MORE),
                customTopics = listOf("volcano"),
                mutedTopics = mapOf(TestCatalog.HEALTH to now),
                mutedKeywords = listOf(MutedKeyword("crypto", now)),
                removedOutlets = mapOf(TestCatalog.outletId(4) to now),
                paywallFlags = mapOf(TestCatalog.outletId(5) to now),
                paywallFilterOn = false,
                textSize = TextSize.LARGE,
                zip = "98101",
                localMetroId = TestCatalog.SEATTLE,
            ),
        )
    }

    @Test
    fun exportLeavesHistoryOutUnlessTicked() {
        server.fill(itemsPerFeed = 3)
        start()
        seedChoices()
        openBackup()

        val without = JSONObject(exportTo(testFile("plain")))
        assertEquals("newz-backup", without.getString("format"))
        assertFalse("no history without the tick", without.has("history"))
        assertEquals(1, without.getJSONArray("saved").length())

        tap("Include reading history")
        val withHistory = JSONObject(exportTo(testFile("history")))
        assertEquals("newz-backup", withHistory.getString("format"))
        assertEquals(2, withHistory.getJSONArray("history").length())
    }

    @Test
    fun importAfterResetRestoresEveryChoice() {
        server.fill(itemsPerFeed = 3)
        start()
        val seeded = seedChoices()
        openBackup()
        tap("Include reading history")
        val file = testFile("full")
        exportTo(file)

        val store = AppGraph.get().articleStore
        val prefsBefore = currentPrefs()
        assertEquals(seeded, prefsBefore)
        val hiddenBefore = store.listHidden()
        val savedBefore = store.listSaved()
        val customBefore = store.listCustomFeeds()
        val historyBefore = store.listHistory()
        assertTrue(hiddenBefore.isNotEmpty() && savedBefore.isNotEmpty() && customBefore.isNotEmpty())

        // Reset the app data: a fresh database and default onboarded preferences.
        releaseStubs()
        scenario?.close()
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        val fresh = AppGraph.get().articleStore
        assertNotEquals(prefsBefore, currentPrefs())
        assertTrue(fresh.listSaved().isEmpty() && fresh.listHidden().isEmpty())
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 1)
        openBackup()

        stubPickers(file)
        tap("Choose a backup file")
        compose.waitForText("Backup imported", TIMEOUT)
        compose
            .onNodeWithText(
                "Restored 7 sources, 6 topics, 1 muted keywords, 1 hidden and 1 saved articles.",
                substring = true,
            ).assertExists()

        val after = currentPrefs()
        assertEquals(prefsBefore, after)
        assertEquals(prefsBefore.followedTopics, after.followedTopics)
        assertEquals(prefsBefore.topicLevels, after.topicLevels)
        assertEquals(prefsBefore.followedOutlets, after.followedOutlets)
        assertEquals(prefsBefore.removedOutlets, after.removedOutlets)
        assertEquals(prefsBefore.mutedKeywords, after.mutedKeywords)
        assertEquals(prefsBefore.paywallFlags, after.paywallFlags)
        assertEquals(prefsBefore.zip, after.zip)
        assertEquals(TextSize.LARGE, after.textSize)
        assertFalse(after.paywallFilterOn)
        val restored = AppGraph.get().articleStore
        assertEquals(hiddenBefore, restored.listHidden())
        assertEquals(savedBefore, restored.listSaved())
        assertEquals(customBefore, restored.listCustomFeeds())
        assertEquals(historyBefore.toSet(), restored.listHistory().toSet())
    }

    @Test
    fun malformedFileChangesNothing() {
        server.fill(itemsPerFeed = 3)
        start()
        openBackup()
        val before = currentPrefs()
        val file = testFile("broken")
        file.writeText("{\"format\":\"newz-backup\",\"version\":1,\"prefs\":{\"onboarded\":")

        stubPickers(file)
        tap("Choose a backup file")
        compose.waitForText("That file isn’t a valid Newz backup", TIMEOUT)
        compose.onNodeWithText("Nothing was changed.", substring = true).assertExists()
        assertEquals(before, currentPrefs())
        assertTrue(compose.onAllNodes(hasText("Backup imported")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun failedExportSaysNothingWasSaved() {
        server.fill(itemsPerFeed = 3)
        start()
        openBackup()
        val unwritable = File(File(cacheDir, "backup-test-missing-dir"), "backup.json")
        unwritable.parentFile?.deleteRecursively()

        stubPickers(unwritable)
        tap("Export to a file")
        compose.waitForText("Backup not saved", TIMEOUT)
        compose.onNodeWithText("Nothing was written.", substring = true).assertExists()
        assertTrue(compose.onAllNodes(hasText("Backup saved")).fetchSemanticsNodes().isEmpty())
        assertTrue(!unwritable.exists())
    }
}
