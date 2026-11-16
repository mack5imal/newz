package app.newz.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.MainActivity
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.testing.waitForStableFeed
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B48 (review R13): in landscape the app bar's buttons stay clear of a side navigation bar and
 * the camera cutout. With gesture navigation and no cutout the side insets are zero, and this
 * only checks the buttons stay on screen.
 */
@RunWith(AndroidJUnit4::class)
class LandscapeInsetsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = MockFeedServer(feedCount = 4).start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        scenario?.close()
        server.close()
    }

    @Test
    fun appBarButtonsClearTheSideInsets() {
        server.fill(itemsPerFeed = 2)
        TestApp.install(TestCatalog.build(server))
        TestPrefs.onboarded()
        scenario = TestApp.launch()
        compose.waitForStableFeed(minCards = 1)
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForStableFeed(minCards = 1)

        var width = 0
        var left = 0
        var right = 0
        scenario!!.onActivity { activity ->
            val root = activity.window.decorView
            width = root.width
            val insets =
                ViewCompat
                    .getRootWindowInsets(root)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            left = insets?.left ?: 0
            right = insets?.right ?: 0
        }
        assertTrue("landscape", width > 0)
        for (label in listOf("Settings", "Saved")) {
            val bounds =
                compose
                    .onNode(hasText(label) and hasClickAction())
                    .fetchSemanticsNode()
                    .boundsInWindow
            assertTrue(
                "$label ends at ${bounds.right}, inset starts at ${width - right}",
                bounds.right <= width - right,
            )
            assertTrue("$label starts at ${bounds.left}, inset ends at $left", bounds.left >= left)
        }
    }
}
