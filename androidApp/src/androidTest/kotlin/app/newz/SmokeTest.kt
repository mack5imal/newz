package app.newz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.waitForText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches on the test graph (mock feeds, in-memory database, test preferences), never the user's data. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val server = MockFeedServer().start()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        server.close()
    }

    @Test
    fun showsNewzText() {
        TestApp.install(TestCatalog.build(server))
        scenario = TestApp.launch()
        composeRule.waitForText("Newz")
        composeRule.onNodeWithText("Newz").assertIsDisplayed()
    }
}
