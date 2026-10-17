package app.newz.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.newz.store.TextSize
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzColors
import app.newz.ui.theme.NewzTextScale
import app.newz.ui.theme.NewzTheme
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.NewzType
import app.newz.ui.theme.SignageDisplayText
import app.newz.ui.theme.SignageText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The design's `paper` values (design/HANDOFF.md), written out here so the test checks the
    // theme file against the design rather than against itself.
    private val paperLight = 0xFFDCE5F2.toInt()
    private val paperDark = 0xFF1D3A5F.toInt()

    @Composable
    private fun Root(content: @Composable () -> Unit = {}) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalNewzPalette.current.paper)
                .testTag(ROOT),
        ) { content() }
    }

    private fun rootCentrePixel(): Int {
        val pixels = composeRule.onNodeWithTag(ROOT).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2].toArgb()
    }

    @Test
    fun lightThemeBackgroundIsLightPaper() {
        composeRule.setContent { NewzTheme(darkTheme = false, textSize = TextSize.DEFAULT) { Root() } }
        assertEquals(paperLight, rootCentrePixel())
    }

    @Test
    fun darkThemeBackgroundIsDarkPaper() {
        composeRule.setContent { NewzTheme(darkTheme = true, textSize = TextSize.DEFAULT) { Root() } }
        assertEquals(paperDark, rootCentrePixel())
    }

    @Test
    fun followsSystemNightMode() {
        composeRule.setContent {
            val night =
                Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                }
            CompositionLocalProvider(LocalConfiguration provides night) {
                NewzTheme(textSize = TextSize.DEFAULT) { Root() }
            }
        }
        assertEquals(paperDark, rootCentrePixel())
    }

    @Test
    fun paletteTokensMatchDesign() {
        assertEquals(paperLight, NewzColors.Light.paper.toArgb())
        assertEquals(paperDark, NewzColors.Dark.paper.toArgb())
    }

    @Test
    fun signageTextShowsCapitalsAndKeepsCopyCaseSemantics() {
        composeRule.setContent {
            NewzTheme(darkTheme = false, textSize = TextSize.DEFAULT) {
                Root { SignageText("You're all caught up", NewzTokens.typeStateTitle) }
            }
        }
        composeRule
            .onNodeWithText("You're all caught up")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SignageDisplayText, "YOU'RE ALL CAUGHT UP"))
    }

    @Test
    fun textScaleFactorsMatchDesign() {
        val expectedText =
            mapOf(
                TextSize.SMALL to 0.875f,
                TextSize.DEFAULT to 1.0f,
                TextSize.LARGE to 1.15f,
                TextSize.EXTRA_LARGE to 1.33f,
            )
        val expectedSignage =
            mapOf(
                TextSize.SMALL to 0.9f,
                TextSize.DEFAULT to 1.0f,
                TextSize.LARGE to 1.12f,
                TextSize.EXTRA_LARGE to 1.25f,
            )
        assertEquals(expectedText, NewzTokens.textScale)
        assertEquals(expectedSignage, NewzTokens.textScaleSignage)

        val xl = NewzTextScale.of(TextSize.EXTRA_LARGE)
        // Content styles scale; the wordmark and app-bar nav do not.
        assertEquals(18f * 1.33f, sizeAt(NewzTokens.typeHeadline, xl), 0.001f)
        assertEquals(18f * 1.25f, sizeAt(NewzTokens.typeOutlet, xl), 0.001f)
        assertEquals(40f, sizeAt(NewzTokens.typeWordmark, xl), 0.001f)
        assertEquals(18f, sizeAt(NewzTokens.typeNav, xl), 0.001f)
    }

    private fun sizeAt(
        type: NewzType,
        scale: NewzTextScale,
    ): Float = type.resolve(scale).fontSize.value

    private companion object {
        const val ROOT = "root"
    }
}
