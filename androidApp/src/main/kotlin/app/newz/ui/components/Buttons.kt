package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/** The `Button` variants; Wide is the `wide` flag, which applies to any of them. */
enum class ButtonVariant { Primary, Outline, Text, Disabled }

/**
 * `Button` — every text button: 48 tall, padding 0 20 (0 12 for Text), an optional leading
 * 20 dp [icon] with gap 8, `type-button`, `radius-control` and a 2 dp border. [wide] makes it
 * full width. A Disabled button (or [enabled] false) can't be tapped. [contentColor] overrides
 * the Outline/Text border and text colour (the `sky` outline on a `BoardPanel`); [trailingIcon]
 * follows the label (onboarding's Continue arrow). [compact] draws a smaller button (36 tall,
 * padding 0 14, 16 sp text) centred in the same 48 dp touch target; it ignores [wide].
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    wide: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
    trailingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val c = palette()
    val isEnabled = enabled && variant != ButtonVariant.Disabled
    val fill: Color
    val border: Color
    val content: Color
    when {
        !isEnabled -> {
            fill = c.disabled
            border = c.disabled
            content = c.onDisabled
        }

        variant == ButtonVariant.Primary -> {
            fill = c.button
            border = c.button
            content = c.onButton
        }

        variant == ButtonVariant.Outline -> {
            fill = Color.Transparent
            border = contentColor ?: c.line
            content = contentColor ?: c.ink
        }

        else -> {
            fill = Color.Transparent
            border = Color.Transparent
            content = contentColor ?: c.ink
        }
    }
    if (compact) {
        CompactButton(text, onClick, modifier, isEnabled, fill, border, content)
        return
    }
    val paddingH = if (variant == ButtonVariant.Text) NewzTokens.spaceM else NewzTokens.spaceXl
    Row(
        modifier =
            modifier
                .then(if (wide) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.buttonHeight)
                .clip(ControlShape)
                .background(fill)
                .border(NewzTokens.borderControl, border, ControlShape)
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = paddingH),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) NewzIcon(icon, content, size = NewzTokens.iconSizeInControl)
        SignageText(text = text, style = NewzTokens.typeButton, color = content, textAlign = TextAlign.Center)
        if (trailingIcon != null) NewzIcon(trailingIcon, content, size = NewzTokens.iconSizeInControl)
    }
}

/** The [Button] `compact` size: the 48 dp target is tappable, the drawn button sits centred in it. */
@Composable
private fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    border: Color,
    content: Color,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.touchTarget)
                .clip(ControlShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .heightIn(min = NewzTokens.buttonCompactHeight)
                    .clip(ControlShape)
                    .background(fill)
                    .border(NewzTokens.borderControl, border, ControlShape)
                    .padding(horizontal = NewzTokens.buttonCompactPaddingH),
            contentAlignment = Alignment.Center,
        ) {
            SignageText(
                text = text,
                style = NewzTokens.typeButtonCompact,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * `IconButton` — every icon-only control: a 48×48 target with a 22 dp icon in `icon` (pass
 * `sky` as [tint] on an app bar). [contentDescription] is the spoken label and is required.
 */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = palette().icon,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(NewzTokens.touchTarget)
                .semantics { this.contentDescription = contentDescription }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NewzIcon(icon, tint)
    }
}

/** The `GroupButton` label: "+1 source" or "+{n} sources". */
fun groupButtonLabel(count: Int): String = if (count == 1) "+1 source" else "+$count sources"

/**
 * `GroupButton` — "+n sources" on grouped cards: 48 tall, a trailing 20 dp `icon-arrow-right`
 * with gap 10, `type-button`, `radius-control`; light is a `navy` fill with `sky` text, dark is
 * transparent with a 2 dp `sky` border. [count] is the number of other outlets.
 */
@Composable
fun GroupButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    val fill = if (c.isDark) Color.Transparent else c.navy
    val border = if (c.isDark) c.sky else c.navy
    val paddingH = if (c.isDark) NewzTokens.spaceM else NewzTokens.groupButtonPaddingH
    Row(
        modifier =
            modifier
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.buttonHeight)
                .clip(ControlShape)
                .background(fill)
                .border(NewzTokens.borderControl, border, ControlShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = paddingH),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.groupButtonGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignageText(text = groupButtonLabel(count), style = NewzTokens.typeButton, color = c.sky)
        NewzIcon(NewzIcons.iconArrowRight, c.sky, size = NewzTokens.iconSizeInControl)
    }
}
