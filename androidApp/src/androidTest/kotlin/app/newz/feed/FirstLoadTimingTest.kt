package app.newz.feed

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.waitForStableFeed
import app.newz.ui.feed.FeedSemantics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-load time (SPEC S5): 30 feeds, each answering after 1000 ms. Cold (empty cache) the
 * first card must show within 5000 ms of launching the activity; with a cache, within 1500 ms.
 * Each case runs [RUNS] times and every run must pass; timings are logged under [TAG].
 */
@RunWith(AndroidJUnit4::class)
class FirstLoadTimingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = FEEDS).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    @Test
    fun coldStartShowsTheFirstCardWithinFiveSeconds() {
        val timings =
            List(RUNS) {
                scenario?.close()
                server.fill(itemsPerFeed = 3)
                server.feeds.forEach { it.delayMs = FEED_DELAY_MS }
                TestApp.install(TestCatalog.build(server))
                TestPrefs.onboarded()
                timeToFirstCard()
            }
        Log.i(TAG, "cold runs (ms): $timings")
        timings.forEachIndexed { run, ms -> assertTrue("cold run ${run + 1}: $ms ms", ms <= COLD_LIMIT_MS) }
    }

    @Test
    fun cachedStartShowsTheFirstCachedCardWithinFifteenHundredMillis() {
        val timings =
            List(RUNS) {
                scenario?.close()
                server.fill(itemsPerFeed = 3)
                server.feeds.forEach { it.delayMs = 0 }
                TestApp.install(TestCatalog.build(server))
                TestPrefs.onboarded()
                scenario = TestApp.launch()
                compose.waitForStableFeed(minCards = 20)
                scenario?.close()

                // Same database (the cache), a new feed engine, and a slow network again.
                server.feeds.forEach { it.delayMs = FEED_DELAY_MS }
                TestApp.install(TestCatalog.build(server), keepDatabase = true)
                timeToFirstCard()
            }
        Log.i(TAG, "cached runs (ms): $timings")
        timings.forEachIndexed { run, ms -> assertTrue("cached run ${run + 1}: $ms ms", ms <= CACHED_LIMIT_MS) }
    }

    private fun timeToFirstCard(): Long {
        val start = SystemClock.elapsedRealtime()
        scenario = TestApp.launch()
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithTag(FeedSemantics.CARD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        return SystemClock.elapsedRealtime() - start
    }

    private companion object {
        const val TAG = "FirstLoadTiming"
        const val FEEDS = 30
        const val RUNS = 3
        const val FEED_DELAY_MS = 1_000L
        const val COLD_LIMIT_MS = 5_000L
        const val CACHED_LIMIT_MS = 1_500L
        const val TIMEOUT_MS = 15_000L
    }
}
