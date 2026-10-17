package app.newz.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.newz.store.TextSize
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.LocalNewzTextScale
import app.newz.ui.theme.NewzPalette
import app.newz.ui.theme.NewzTextScale
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.NewzType
import app.newz.ui.theme.scaled

/** `radius-control`, the corner of every control, chip, tile and field. */
internal val ControlShape = RoundedCornerShape(NewzTokens.radiusControl)

/** The palette in effect. */
@Composable
@ReadOnlyComposable
internal fun palette(): NewzPalette = LocalNewzPalette.current

/** True at the Extra large text size, where some components change layout. */
@Composable
@ReadOnlyComposable
internal fun isExtraLargeText(): Boolean = LocalNewzTextScale.current == NewzTextScale.of(TextSize.EXTRA_LARGE)

/**
 * Content text in a `font-text` style. It wraps and is never ellipsised (no maxLines), and keeps
 * Text's own semantics (including the text-layout action tests use).
 */
@Composable
internal fun PlainText(
    text: String,
    style: NewzType,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val resolved = style.scaled().let { if (fontWeight != null) it.copy(fontWeight = fontWeight) else it }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = resolved,
        textAlign = textAlign,
        onTextLayout = onTextLayout,
    )
}

/** A decorative line icon (the label is carried by the control around it). */
@Composable
internal fun NewzIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = NewzTokens.iconSize,
) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** The image placeholder: a [fill] with diagonal [line]s every [spacing], rising left to right. */
internal fun Modifier.hatch(
    fill: Color,
    line: Color,
    spacing: Dp,
): Modifier =
    drawBehind {
        drawRect(fill)
        val step = spacing.toPx()
        val stroke = NewzTokens.divider.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(line, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
            x += step
        }
    }
