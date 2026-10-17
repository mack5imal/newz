package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/**
 * `TopicChip` — topic chips, headline suggestions and muted keywords: 48 tall, padding 0 16, a
 * 2 dp `line` border, `radius-control` and `type-chip`. A [selected] chip gets a `button` fill,
 * `on-button` text and a leading 20 dp `icon-check`. A [muted] keyword chip has a trailing
 * `icon-close`, and tapping it calls [onClick] (unmute). Unless [muted], the chip is a toggle.
 */
@Composable
fun TopicChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    muted: Boolean = false,
) {
    val c = palette()
    val fill: Color
    val border: Color
    val content: Color
    when {
        selected -> {
            fill = c.button
            border = c.button
            content = c.onButton
        }

        c.isDark -> {
            fill = Color.Transparent
            border = c.line
            content = c.sky
        }

        else -> {
            fill = c.field
            border = c.line
            content = c.ink
        }
    }
    val interaction =
        if (muted) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        }
    Row(
        modifier =
            modifier
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.chipHeight)
                .clip(ControlShape)
                .background(fill)
                .border(NewzTokens.borderControl, border, ControlShape)
                .then(interaction)
                .padding(horizontal = NewzTokens.spaceL),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.gapIconLabel, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) NewzIcon(NewzIcons.iconCheck, content, size = NewzTokens.iconSizeInControl)
        SignageText(text = text, style = NewzTokens.typeChip, color = content)
        if (muted) NewzIcon(NewzIcons.iconClose, content, size = NewzTokens.iconSizeInControl)
    }
}

/**
 * `TopicChipRow` — the horizontally scrolling chip row at the top of the feed: padding 10 12 12
 * (12 12 12 in dark), gap 8. [label] is its spoken name ("Topics").
 */
@Composable
fun TopicChipRow(
    modifier: Modifier = Modifier,
    label: String = "Topics",
    content: @Composable RowScope.() -> Unit,
) {
    val c = palette()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
                .horizontalScroll(rememberScrollState())
                .padding(
                    start = NewzTokens.spaceM,
                    end = NewzTokens.spaceM,
                    top = if (c.isDark) NewzTokens.spaceM else NewzTokens.chipRowTopLight,
                    bottom = NewzTokens.spaceM,
                ),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The fixed `Tag` labels. Metered uses the Paywall style (SPEC S12). */
enum class TagKind(
    val label: String,
) {
    Paywall("Paywall"),
    Metered("Metered"),
    Following("Following"),
}

/** `Tag` — a small outline label: 24 tall, padding 0 7, a 1.5 dp `line` border, `radius-control`, `type-tag`. */
@Composable
fun Tag(
    kind: TagKind,
    modifier: Modifier = Modifier,
) {
    Tag(text = kind.label, modifier = modifier)
}

/** `Tag` with free text. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = NewzTokens.tagHeight)
                .border(NewzTokens.borderTag, c.line, ControlShape)
                .padding(horizontal = NewzTokens.tagPaddingH),
        contentAlignment = Alignment.Center,
    ) {
        SignageText(text = text, style = NewzTokens.typeTag, color = c.ink)
    }
}
