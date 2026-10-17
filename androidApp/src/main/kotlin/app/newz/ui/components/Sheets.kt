package app.newz.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Dragging the sheet down by more than this share of its height dismisses it. */
private const val SHEET_DISMISS_FRACTION = 0.25f

/**
 * `BottomSheet` — the container for card-menu, keyword-mute and story-group: a `card` surface
 * with `radius-sheet` over `scrim`, with a 40×4 grabber (`line-subtle` in light, `line` in dark)
 * 10 from the top and 4 above the content. It is dismissed by a swipe down, a tap on the scrim,
 * or Back; [label] is its spoken pane title ("Article actions"). Place it over the whole screen.
 */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = palette()
    val dismiss by rememberUpdatedState(onDismiss)
    BackHandler(onBack = { dismiss() })
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val drag = remember { Animatable(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val dragState =
        rememberDraggableState { delta ->
            scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
        }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(NewzTokens.sheet, easing = NewzTokens.sheetEasing)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .pointerInput(Unit) { detectTapGestures { dismiss() } },
            )
        }
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(tween(NewzTokens.sheet, easing = NewzTokens.sheetEasing)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { sheetHeight = it.height }
                        .offset { IntOffset(0, drag.value.roundToInt()) }
                        .clip(RoundedCornerShape(topStart = NewzTokens.radiusSheet, topEnd = NewzTokens.radiusSheet))
                        .background(c.card)
                        .semantics {
                            paneTitle = label
                            dismiss {
                                dismiss()
                                true
                            }
                        }.draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                if (sheetHeight > 0 && drag.value > sheetHeight * SHEET_DISMISS_FRACTION) {
                                    dismiss()
                                } else {
                                    drag.animateTo(0f, tween(NewzTokens.sheet, easing = NewzTokens.sheetEasing))
                                }
                            },
                        ).navigationBarsPadding(),
            ) {
                Spacer(Modifier.height(NewzTokens.sheetGrabberTop))
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(NewzTokens.grabberSize)
                        .clip(RoundedCornerShape(NewzTokens.radiusGrabber))
                        .background(if (c.isDark) c.line else c.lineSubtle),
                )
                Spacer(Modifier.height(NewzTokens.spaceXs))
                content()
            }
        }
    }
}

/**
 * `SheetActionRow` — one action in the card menu: min height 56, padding 0 20, a 22 dp [icon] in
 * `icon`, gap 16, the [label] in `type-row-title` 500. When not [enabled], label and icon turn
 * `ink-muted`, the [explanation] shows in `type-row-sub` 600 `ink`, and the row can't be tapped.
 */
@Composable
fun SheetActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    explanation: String? = null,
) {
    val c = palette()
    val tint = if (enabled) c.icon else c.inkMuted
    val interaction =
        if (enabled) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier.semantics(mergeDescendants = true) { disabled() }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NewzTokens.sheetRowHeight)
                .then(interaction)
                .padding(horizontal = NewzTokens.spaceXl, vertical = NewzTokens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewzIcon(icon, tint)
        Column(Modifier.weight(1f)) {
            PlainText(
                text = label,
                style = NewzTokens.typeRowTitle,
                color = if (enabled) c.ink else c.inkMuted,
                fontWeight = FontWeight.Medium,
            )
            if (!enabled && explanation != null) {
                PlainText(
                    text = explanation,
                    style = NewzTokens.typeRowSub,
                    color = c.ink,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * `BottomActionBar` — the bar pinned to the bottom of onboarding: a `card` surface with a 2 dp
 * `line` top border, padding 12 12 24 16, gap 12; the [status] in `type-row-title` (and an
 * optional `type-row-sub` [subStatus]) at the left and a Primary [Button] at the right, with an
 * optional [buttonTrailingIcon] after its label.
 */
@Composable
fun BottomActionBar(
    status: String,
    buttonLabel: String,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
    subStatus: String? = null,
    buttonEnabled: Boolean = true,
    buttonTrailingIcon: ImageVector? = null,
) {
    val c = palette()
    Column(modifier.fillMaxWidth().background(c.card)) {
        Box(Modifier.fillMaxWidth().height(NewzTokens.borderControl).background(c.line))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = NewzTokens.spaceL,
                        top = NewzTokens.spaceM,
                        end = NewzTokens.spaceM,
                        bottom = NewzTokens.bottomBarPaddingBottom,
                    ),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                PlainText(text = status, style = NewzTokens.typeRowTitle, color = c.ink)
                if (subStatus != null) PlainText(text = subStatus, style = NewzTokens.typeRowSub, color = c.inkMuted)
            }
            Button(
                text = buttonLabel,
                onClick = onButton,
                enabled = buttonEnabled,
                trailingIcon = buttonTrailingIcon,
            )
        }
    }
}

/**
 * `ConfirmSheet` — asks before an action that can't be undone: a [BottomSheet] spoken [title],
 * the [title] in `type-sheet-title`, the [message] in `type-intro` `ink-muted`, then Cancel (Text)
 * and the Primary [confirmLabel] at the right, as on the zip code sheet. [onConfirm] runs the
 * action (the caller closes the sheet); Cancel, a swipe down, a tap outside and Back call [onDismiss].
 */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
) {
    val c = palette()
    BottomSheet(onDismiss = onDismiss, label = title) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = NewzTokens.spaceXl,
                    end = NewzTokens.spaceXl,
                    bottom = NewzTokens.spaceXl + NewzTokens.spaceXs,
                ),
        ) {
            SignageText(text = title, style = NewzTokens.typeSheetTitle, color = c.ink)
            PlainText(
                text = message,
                style = NewzTokens.typeIntro,
                color = c.inkMuted,
                modifier = Modifier.padding(top = NewzTokens.spaceXs),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = NewzTokens.spaceL),
                horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS, Alignment.End),
            ) {
                Button(text = cancelLabel, onClick = onDismiss, variant = ButtonVariant.Text)
                Button(text = confirmLabel, onClick = onConfirm, variant = ButtonVariant.Primary)
            }
        }
    }
}
