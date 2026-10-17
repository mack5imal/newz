package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText
import app.newz.ui.theme.scaled

/** The field box shared by [TextField] and [SearchField]. */
@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    height: Dp,
    borderColor: Color,
    paddingStart: Dp,
    paddingEnd: Dp?,
    description: String?,
    placeholder: String?,
    clearLabel: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = palette()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        textStyle = NewzTokens.typeField.scaled().copy(color = c.ink),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(c.ink),
        decorationBox = { inner ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = height)
                        .background(c.field, ControlShape)
                        .border(NewzTokens.borderControl, borderColor, ControlShape)
                        .padding(start = paddingStart)
                        .then(if (paddingEnd != null) Modifier.padding(end = paddingEnd) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        PlainText(text = placeholder, style = NewzTokens.typeField, color = c.inkMuted)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        icon = NewzIcons.iconClose,
                        contentDescription = clearLabel,
                        onClick = { onValueChange("") },
                        tint = c.icon,
                    )
                }
            }
        },
    )
}

/**
 * `TextField` — the keyword field: a `type-label` [label] with 6 below, a 56 dp field (padding
 * 0 4 0 14, `field` fill, 2 dp `line` border, `radius-control`) with the `type-field` input and a
 * clear [IconButton] ("Clear") once there is text; 6 below, the [helper] at the left and a
 * "{n}/[maxLength]" counter at the right in `type-row-sub`. With an [error], the border, the
 * message (with `icon-warning`) and the counter turn `alert`. [fieldModifier] goes on the editable
 * field itself (for a `FocusRequester`); [counter] replaces the default counter text;
 * [keyboardOptions] picks the keyboard (numeric for the zip code).
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
    error: String? = null,
    maxLength: Int = 60,
    placeholder: String? = null,
    clearLabel: String = "Clear",
    fieldModifier: Modifier = Modifier,
    counter: String = "${value.length}/$maxLength",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = palette()
    val isError = error != null
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            SignageText(text = label, style = NewzTokens.typeLabel, color = c.ink)
            Spacer(Modifier.height(NewzTokens.gapIconLabel))
        }
        FieldBox(
            value = value,
            onValueChange = onValueChange,
            height = NewzTokens.fieldHeight,
            borderColor = if (isError) c.alert else c.line,
            paddingStart = NewzTokens.spaceCardGap,
            paddingEnd = NewzTokens.spaceXs,
            description = label,
            placeholder = placeholder,
            clearLabel = clearLabel,
            modifier = fieldModifier,
            keyboardOptions = keyboardOptions,
        )
        Spacer(Modifier.height(NewzTokens.gapIconLabel))
        Row(horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM), verticalAlignment = Alignment.Top) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(NewzTokens.gapIconLabel),
                verticalAlignment = Alignment.Top,
            ) {
                if (isError) {
                    NewzIcon(NewzIcons.iconWarning, c.alert, size = NewzTokens.iconSizeInControl)
                    PlainText(text = error.orEmpty(), style = NewzTokens.typeRowSub, color = c.alert)
                } else if (helper != null) {
                    PlainText(text = helper, style = NewzTokens.typeRowSub, color = c.inkMuted)
                }
            }
            PlainText(
                text = counter,
                style = NewzTokens.typeRowSub,
                color = if (isError) c.alert else c.inkMuted,
            )
        }
    }
}

/**
 * `SearchField` — the field in the search app bar: a [TextField] with no label, 52 tall,
 * padding 0 0 0 14, a `field` fill and a border in `field` (light) or `line` (dark).
 * [description] is its spoken label (the [placeholder] unless given).
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearLabel: String = "Clear",
    description: String = placeholder,
) {
    val c = palette()
    FieldBox(
        value = value,
        onValueChange = onValueChange,
        height = NewzTokens.fieldHeightSearch,
        borderColor = if (c.isDark) c.line else c.field,
        paddingStart = NewzTokens.spaceCardGap,
        paddingEnd = null,
        description = description,
        placeholder = placeholder,
        clearLabel = clearLabel,
        modifier = modifier,
    )
}
