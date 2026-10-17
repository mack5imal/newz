package app.newz.feed

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.visibleCards
import app.newz.testing.waitForStableFeed
import app.newz.ui.feed.FeedSemantics
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening and sharing (SPEC S13, S20) with Espresso-Intents: every external intent is stubbed, so
 * no browser or share sheet really opens. Captured intents are logged under [TAG].
 */
@RunWith(AndroidJUnit4::class)
class OpenShareIntentTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 4).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        Intents.init()
        intending(not(isInternal())).respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        // Release first: ActivityScenario.close() starts a helper activity from the test APK, which the stub would catch.
        Intents.release()
        scenario?.close()
        server.close()
    }

    private fun launchFeed(customTabsProvider: () -> String?) {
        server.fill(itemsPerFeed = 2)
        TestApp.install(TestCatalog.build(server), customTabsProvider = customTabsProvider)
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 8)
    }

    private fun tapFirstCard(): String {
        val first = compose.visibleCards().first()
        val title =
            server.feeds
                .flatMap { it.items }
                .first { it.link == first.url }
                .title
        compose.onNode(hasTestTag(FeedSemantics.CARD_TAG) and hasText(title)).performClick()
        return first.url
    }

    @Test
    fun cardTapOpensACustomTabWithoutAPackageWhenTheBrowserProvidesOne() {
        launchFeed { "org.example.browser" }
        val url = tapFirstCard()
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(url)))
        val view = Intents.getIntents().last { it.action == Intent.ACTION_VIEW }
        Log.i(TAG, "custom tab: ${describe(view)}")
        assertNull(view.`package`)
        assertNull(view.component)
        assertEquals(url, view.dataString)
        assertTrue("a Custom Tabs intent", view.hasExtra(CUSTOM_TABS_SESSION))
    }

    @Test
    fun cardTapOpensTheDefaultBrowserWhenNoCustomTabsProvider() {
        launchFeed { null }
        val url = tapFirstCard()
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(url)))
        val view = Intents.getIntents().last { it.action == Intent.ACTION_VIEW }
        Log.i(TAG, "plain view: ${describe(view)}")
        assertNull(view.`package`)
        assertNull(view.component)
        assertEquals(url, view.dataString)
        assertFalse("not a Custom Tabs intent", view.hasExtra(CUSTOM_TABS_SESSION))
        assertTrue(view.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun shareOpensAChooserWrappingPlainTextSendWithHeadlineAndUrl() {
        launchFeed { null }
        val first = compose.visibleCards().first()
        val title =
            server.feeds
                .flatMap { it.items }
                .first { it.link == first.url }
                .title
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Share").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Share")[0].performClick()
        intended(hasAction(Intent.ACTION_CHOOSER))
        val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
        val send = checkNotNull(innerIntent(chooser)) { "chooser wraps an intent" }
        val text = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        Log.i(TAG, "share: chooser=${describe(chooser)} send=${describe(send)}")
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertTrue("headline in $text", text.contains(title))
        assertTrue("url in $text", text.contains(first.url))
        assertEquals("$title ${first.url}", text)
    }

    private fun innerIntent(chooser: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            chooser.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun describe(intent: Intent): String {
        val extras = intent.extras?.let { b -> b.keySet().sorted().joinToString { "$it=${extraValue(b, it)}" } }
        return "action=${intent.action} data=${intent.dataString} type=${intent.type} package=${intent.`package`} " +
            "component=${intent.component} categories=${intent.categories} extras={$extras}"
    }

    @Suppress("DEPRECATION")
    private fun extraValue(
        bundle: android.os.Bundle,
        key: String,
    ): Any? = bundle.get(key)

    private companion object {
        const val TAG = "OpenShareIntent"
        const val CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
    }
}
