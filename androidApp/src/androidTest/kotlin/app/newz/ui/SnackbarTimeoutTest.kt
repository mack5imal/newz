package app.newz.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.store.TextSize
import app.newz.ui.components.Snackbar
import app.newz.ui.theme.NewzTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** B53 (review R31): the Undo bar waits as long as the user's "Time to take action" asks. */
@RunWith(AndroidJUnit4::class)
class SnackbarTimeoutTest {
    @get:Rule
    val compose = createComposeRule()

    private class LongerTimeouts(
        private val millis: Long,
    ) : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = maxOf(originalTimeoutMillis, millis)
    }

    @Test
    fun theRecommendedTimeoutIsUsedInsteadOfSixSeconds() {
        var timedOut = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides LongerTimeouts(20_000)) {
                NewzTheme(textSize = TextSize.DEFAULT) {
                    Snackbar(message = "Article hidden", actionLabel = "Undo", onTimeout = { timedOut++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(7_000)
        assertEquals("still showing after 7 s", 0, timedOut)
        compose.mainClock.advanceTimeBy(14_000)
        assertEquals(1, timedOut)
    }
}
