package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/**
 * `Panel` — a grouped list on a settings-style screen: a `card` block with 12 side margins and
 * 14 below; a [PanelHeader] (when [title] is set) followed by the [content] rows.
 */
@Composable
fun Panel(
    title: String?,
    modifier: Modifier = Modifier,
    count: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = palette()
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = NewzTokens.spaceM, end = NewzTokens.spaceM, bottom = NewzTokens.spaceCardGap)
            .background(c.card),
    ) {
        if (title != null) PanelHeader(title = title, count = count)
        content()
    }
}

/**
 * `PanelHeader` — the title band of a [Panel]: min height 40, padding 0 14, `strip` background,
 * the [title] in `type-panel-header` at the left and an optional [count] at the right, in `sky`.
 */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    val c = palette()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NewzTokens.panelHeaderHeight)
                .background(c.strip)
                .padding(horizontal = NewzTokens.spaceCardGap),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignageText(
            text = title,
            style = NewzTokens.typePanelHeader,
            color = c.sky,
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).semantics {
                        heading()
                    },
        )
        if (count != null) SignageText(text = count, style = NewzTokens.typePanelHeader, color = c.sky)
    }
}

/**
 * `ListRow` — a row in a [Panel]: min height 64, padding 8 8 8 16, a 1 dp `line-subtle` divider
 * above ([showDivider] false directly under a [PanelHeader]), an optional 22 dp [icon] with gap
 * 14, the `type-row-title` [title] and `type-row-sub` [subtitle], then an optional [trailing]
 * control ([Switch] or [Button]) or, when the row navigates ([onClick]), a trailing chevron.
 * A navigating row is one touch target.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = palette()
    Column(modifier.fillMaxWidth()) {
        if (showDivider) Box(Modifier.fillMaxWidth().height(NewzTokens.divider).background(c.lineSubtle))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.rowHeight)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(
                        start = NewzTokens.spaceL,
                        top = NewzTokens.spaceS,
                        end = NewzTokens.spaceS,
                        bottom = NewzTokens.spaceS,
                    ),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) NewzIcon(icon, c.icon)
            Column(Modifier.weight(1f)) {
                PlainText(text = title, style = NewzTokens.typeRowTitle, color = c.ink)
                if (subtitle != null) PlainText(text = subtitle, style = NewzTokens.typeRowSub, color = c.inkMuted)
            }
            when {
                trailing != null -> trailing()
                onClick != null -> NewzIcon(NewzIcons.iconChevron, c.icon)
            }
        }
    }
}

/**
 * `Switch` — a 56×32 track with a 2 dp `line` border and `radius-switch`, and a 20 dp thumb with
 * radius 2: off is a transparent track with a `line` thumb 4 from the left, on is a `button` track
 * with an `on-button` thumb 4 from the right. [contentDescription] is its spoken label.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.touchTarget)
                .semantics { this.contentDescription = contentDescription }
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        val trackShape = RoundedCornerShape(NewzTokens.radiusSwitch)
        Box(
            modifier =
                Modifier
                    .size(NewzTokens.switchSize)
                    .clip(trackShape)
                    .background(if (checked) c.button else Color.Transparent)
                    .border(NewzTokens.borderControl, if (checked) c.button else c.line, trackShape)
                    .padding(horizontal = NewzTokens.spaceXs),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(NewzTokens.switchThumb)
                    .clip(RoundedCornerShape(NewzTokens.radiusSwitchThumb))
                    .background(if (checked) c.onButton else c.line),
            )
        }
    }
}

/**
 * `SegmentedControl` — Text size and topic levels: 48 tall with a 2 dp `line` border and
 * `radius-control`; equal segments divided by 2 dp `line`, set in `type-segment`; the selected
 * segment is `button`/`on-button`.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = NewzTokens.touchTarget)
                .clip(ControlShape)
                .border(NewzTokens.borderControl, c.line, ControlShape)
                .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) Box(Modifier.width(NewzTokens.borderControl).fillMaxHeight().background(c.line))
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = NewzTokens.touchTarget)
                        .background(if (selected) c.button else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = NewzTokens.spaceXs, vertical = NewzTokens.spaceXs),
                contentAlignment = Alignment.Center,
            ) {
                SignageText(
                    text = option,
                    style = NewzTokens.typeSegment,
                    color = if (selected) c.onButton else c.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * `Checkbox` — a 24 square with a 2 dp `line` border and `radius-control`; checked, it has a
 * `button` fill and a 16 dp `icon-check` in `on-button`. Its 48 dp row, with the [label], is
 * the touch target.
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NewzTokens.touchTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(NewzTokens.checkboxSize)
                    .clip(ControlShape)
                    .background(if (checked) c.button else Color.Transparent)
                    .border(NewzTokens.borderControl, if (checked) c.button else c.line, ControlShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) NewzIcon(NewzIcons.iconCheck, c.onButton, size = NewzTokens.checkboxIcon)
        }
        PlainText(text = label, style = NewzTokens.typeRowTitle, color = c.ink, modifier = Modifier.weight(1f))
    }
}
