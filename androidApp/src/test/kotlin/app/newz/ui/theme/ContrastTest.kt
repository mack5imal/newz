package app.newz.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.x contrast of the text tokens against the surfaces they sit on, computed from the theme's
 * own colour values (SPEC S23): body text needs at least 4.5:1 in light and dark.
 */
class ContrastTest {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Text colour over background colour, named for the failure message. */
    private fun pairs(p: NewzPalette): List<Triple<String, Color, Color>> =
        listOf(
            Triple("ink on paper", p.ink, p.paper),
            Triple("ink on card", p.ink, p.card),
            Triple("ink on field", p.ink, p.field),
            Triple("ink-muted on paper", p.inkMuted, p.paper),
            Triple("ink-muted on card", p.inkMuted, p.card),
            Triple("on-button on button", p.onButton, p.button),
            Triple("sky on navy", p.sky, p.navy),
            Triple("sky on strip", p.sky, p.strip),
            Triple("sky on tile", p.sky, p.tile),
            Triple("on-snackbar on snackbar", p.onSnackbar, p.snackbar),
            Triple("alert on card", p.alert, p.card),
            // on-disabled on disabled is left out: WCAG 1.4.3 exempts inactive controls.
        )

    @Test
    fun bodyTextOnPaperAndCardIsAtLeastFourAndAHalfToOne() {
        for ((name, palette) in listOf("light" to NewzColors.Light, "dark" to NewzColors.Dark)) {
            for (background in listOf(palette.paper, palette.card)) {
                val ratio = contrast(palette.ink, background)
                assertTrue("$name body text: %.2f:1".format(ratio), ratio >= 4.5)
            }
        }
    }

    @Test
    fun everyTextTokenPairMeetsAA() {
        val failures =
            listOf("light" to NewzColors.Light, "dark" to NewzColors.Dark).flatMap { (name, palette) ->
                pairs(palette).mapNotNull { (what, text, background) ->
                    val ratio = contrast(text, background)
                    if (ratio >= 4.5) null else "$name $what: %.2f:1".format(ratio)
                }
            }
        assertTrue("below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun formulaMatchesKnownValues() {
        val ratio = contrast(Color.Black, Color.White)
        assertTrue("black on white is 21:1, got $ratio", ratio in 20.99..21.01)
    }
}
