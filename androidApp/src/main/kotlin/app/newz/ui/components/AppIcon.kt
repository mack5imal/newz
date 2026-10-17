package app.newz.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.newz.ui.theme.NewzColors
import app.newz.ui.theme.NewzTokens

// The "Flap tile" icon geometry, from design/HANDOFF.md §app-icon (108-unit canvas).
private const val ICON_CANVAS = 108f
private const val TILE_X = 33f
private const val TILE_Y = 29f
private const val TILE_W = 42f
private const val TILE_H = 50f
private const val SPLIT_Y = 53f
private const val SPLIT_H = 2f
private const val LETTER_N = "M42 71V37h7l10 21V37h7v34h-7L49 50v21z"

// The 72-unit visible area a launcher mask shows (inset 18 on each side), rounded at 16/72.
private const val VISIBLE_INSET = 18f
private const val VISIBLE_SIZE = 72f
private const val VISIBLE_CORNER = 16f

/**
 * `AppIcon` — the "Flap tile" launcher icon (a light-blue N on a navy split-flap tile on light
 * blue), drawn from the design's SVG geometry; shown at 56 dp on onboarding. Like a launcher, it
 * shows the canvas's 72-unit visible area scaled to [size], with corners rounded at 16/72 of it.
 * The icon has the same colours in both themes.
 */
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    size: Dp = NewzTokens.appIconSize,
    contentDescription: String? = null,
) {
    val colors = NewzColors.Light
    val letter = remember { PathParser().parsePathString(LETTER_N).toPath() }
    val radius = NewzTokens.radiusAppIcon.value
    val described =
        contentDescription?.let { label -> Modifier.semantics { this.contentDescription = label } } ?: Modifier
    val shape = RoundedCornerShape(size * (VISIBLE_CORNER / VISIBLE_SIZE))
    Canvas(modifier.size(size).clip(shape).then(described)) {
        val unit = this.size.width / VISIBLE_SIZE
        withTransform({
            scale(unit, unit, pivot = Offset.Zero)
            translate(-VISIBLE_INSET, -VISIBLE_INSET)
        }) {
            drawRect(colors.sky, size = Size(ICON_CANVAS, ICON_CANVAS))
            drawRoundRect(
                color = colors.navy,
                topLeft = Offset(TILE_X, TILE_Y),
                size = Size(TILE_W, TILE_H),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawPath(letter, colors.sky)
            drawRect(colors.sky, topLeft = Offset(TILE_X, SPLIT_Y), size = Size(TILE_W, SPLIT_H))
        }
    }
}
