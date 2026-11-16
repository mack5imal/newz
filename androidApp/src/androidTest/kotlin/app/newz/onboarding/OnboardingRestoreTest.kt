package app.newz.onboarding

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.AppGraph
import app.newz.store.TextSize
import app.newz.store.UserPrefs
import app.newz.testing.MockFeedServer
import app.newz.testing.TestApp
import app.newz.testing.TestCatalog
import app.newz.testing.TestPrefs
import app.newz.ui.onboarding.OnboardingScreen
import app.newz.ui.onboarding.OnboardingViewModel
import app.newz.ui.theme.NewzTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B50 (review R22): onboarding picks survive process death. The tester rebuilds the screen
 * from its saved state with a brand-new view model, as Android does after killing the app.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingRestoreTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockFeedServer(feedCount = 8).start()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun picksComeBackInANewViewModel() {
        TestApp.install(TestCatalog.build(server))
        TestPrefs.write(UserPrefs())
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            // remember: a restored composition builds a new view model, like a new process.
            val viewModel = remember { OnboardingViewModel(AppGraph.get()) }
            NewzTheme(textSize = TextSize.DEFAULT) { OnboardingScreen(viewModel = viewModel, onFinished = {}) }
        }
        compose.pick("World", "Science", "Tech")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        listOf("World", "Science", "Tech").forEach { compose.chip(it).assertIsOn() }
    }
}
