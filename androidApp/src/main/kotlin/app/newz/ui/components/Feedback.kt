package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText
import kotlinx.coroutines.delay

/**
 * `Snackbar` — the Undo bar: it floats 12 from the sides and 24 above the navigation bar (place
 * it at the bottom of the screen; the app draws edge to edge), min height 60, padding 6 6 6 16, gap 12, `snackbar` background,
 * `type-message` in `on-snackbar`, `radius-control`, `shadow-snackbar`, and a [SnackbarAction]
 * only when [actionLabel] is non-null (a plain confirmation such as "Reading history cleared" has none).
 * When [onTimeout] is set it is called after `snackbar-timeout`. The timer restarts whenever
 * [timeoutKey] changes; it defaults to [message], so callers that may show the same message twice
 * in a row (e.g. "Article hidden") pass a per-event id to give each one a full window.
 */
@Composable
fun Snackbar(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
    onTimeout: (() -> Unit)? = null,
    timeoutKey: Any? = message,
) {
    val c = palette()
    if (onTimeout != null) {
        val timeout = rememberUpdatedState(onTimeout)
        LaunchedEffect(timeoutKey) {
            delay(NewzTokens.snackbarTimeout)
            timeout.value()
        }
    }
    val shadow = NewzTokens.shadowSnackbar
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = NewzTokens.spaceM, end = NewzTokens.spaceM, bottom = NewzTokens.snackbarBottom)
                .dropShadow(
                    shape = ControlShape,
                    shadow =
                        Shadow(
                            radius = shadow.blur,
                            color = shadow.color,
                            offset = DpOffset(shadow.offsetX, shadow.offsetY),
                        ),
                ).clip(ControlShape)
                .background(c.snackbar)
                .heightIn(min = NewzTokens.snackbarMinHeight)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(
                    start = NewzTokens.spaceL,
                    top = NewzTokens.snackbarPadding,
                    end = NewzTokens.snackbarPadding,
                    bottom = NewzTokens.snackbarPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainText(text = message, style = NewzTokens.typeMessage, color = c.onSnackbar, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            SnackbarAction(label = actionLabel, onClick = onAction, color = c.onSnackbar)
        }
    }
}

/** `SnackbarAction` — "Undo" or "Retry": 48 tall, padding 0 14, a 2 dp border in the text [color], `type-button` at 18. */
@Composable
fun SnackbarAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = palette().onSnackbar,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.buttonHeight)
                .clip(ControlShape)
                .border(NewzTokens.borderControl, color, ControlShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = NewzTokens.snackbarActionPaddingH),
        contentAlignment = Alignment.Center,
    ) {
        SignageText(text = label, style = NewzTokens.typeSnackbarAction, color = color)
    }
}

/**
 * `OfflineBanner` — the offline notice: a `strip` band with 12 side margins, margin-bottom 14,
 * padding 6 6 6 14, gap 12, a 22 dp `icon-wifi-off`, the [message] in 600 15/20 and a
 * [SnackbarAction] "Retry", all in `sky`.
 */
@Composable
fun OfflineBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Retry",
) {
    val c = palette()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = NewzTokens.spaceM, end = NewzTokens.spaceM, bottom = NewzTokens.spaceCardGap)
                .background(c.strip)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(
                    start = NewzTokens.spaceCardGap,
                    top = NewzTokens.snackbarPadding,
                    end = NewzTokens.snackbarPadding,
                    bottom = NewzTokens.snackbarPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewzIcon(NewzIcons.iconWifiOff, c.sky)
        PlainText(text = message, style = NewzTokens.typeBanner, color = c.sky, modifier = Modifier.weight(1f))
        SnackbarAction(label = retryLabel, onClick = onRetry, color = c.sky)
    }
}

/** Colours for the shared body of [StatePanel] and [BoardPanel]. */
private data class PanelColors(
    val background: Color,
    val title: Color,
    val body: Color,
    val icon: Color,
    val outline: Color?,
)

@Composable
private fun PanelBlock(
    title: String,
    body: String,
    colors: PanelColors,
    modifier: Modifier,
    icon: ImageVector?,
    actionLabel: String?,
    onAction: () -> Unit,
    actionVariant: ButtonVariant,
    top: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = NewzTokens.spaceM,
                    end = NewzTokens.spaceM,
                    top = NewzTokens.spaceXs,
                    bottom = NewzTokens.spaceCardGap,
                ).background(colors.background)
                .padding(
                    start = NewzTokens.spaceXl,
                    end = NewzTokens.spaceXl,
                    top = NewzTokens.space2xl,
                    bottom =
                        NewzTokens.spaceXl + NewzTokens.spaceXs,
                ),
    ) {
        if (top != null) {
            top()
            Spacer(Modifier.height(NewzTokens.boardTilesGap))
        }
        if (icon != null) {
            NewzIcon(icon, colors.icon, size = NewzTokens.iconSizeState)
            Spacer(Modifier.height(NewzTokens.spaceL))
        }
        SignageText(
            text = title,
            style = NewzTokens.typeStateTitle,
            color = colors.title,
            modifier =
                Modifier
                    .semantics {
                        heading()
                    },
        )
        Spacer(Modifier.height(NewzTokens.statePanelBodyGap))
        PlainText(text = body, style = NewzTokens.typeBody, color = colors.body)
        if (actionLabel != null) {
            Spacer(Modifier.height(NewzTokens.spaceXl))
            Button(text = actionLabel, onClick = onAction, variant = actionVariant, contentColor = colors.outline)
        }
    }
}

/**
 * `StatePanel` — every empty, error and info panel: a `card` block, margin 4 12 14, padding
 * 28 20 24; an optional 40 dp [icon] in `ink` (`alert` when [isError]) with 16 below, the
 * `type-state-title` [title], the `type-body` [body] in `ink-muted` 10 below, and an optional
 * [Button] 20 below.
 */
@Composable
fun StatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    actionVariant: ButtonVariant = ButtonVariant.Outline,
) {
    val c = palette()
    PanelBlock(
        title = title,
        body = body,
        colors = PanelColors(c.card, c.ink, c.inkMuted, if (isError) c.alert else c.ink, null),
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction,
        actionVariant = actionVariant,
    )
}

/**
 * `BoardPanel` — a [StatePanel] on a `strip` background with title, body, icon and outline
 * button in `sky`. [tiles] ("END") draws one [FlapTile] per letter, 18 above the title.
 */
@Composable
fun BoardPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tiles: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val c = palette()
    PanelBlock(
        title = title,
        body = body,
        colors = PanelColors(c.strip, c.sky, c.sky, c.sky, c.sky),
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction,
        actionVariant = ButtonVariant.Outline,
        top =
            tiles?.let { word ->
                {
                    Row(
                        modifier = Modifier.clearAndSetSemantics { contentDescription = word },
                        horizontalArrangement = Arrangement.spacedBy(NewzTokens.tileGap),
                    ) {
                        word.forEach { FlapTile(text = it.toString()) }
                    }
                }
            },
    )
}

/**
 * `NoteRow` — hints and notes: padding 14 16, a 22 dp [icon] in the text [color], gap 12,
 * top-aligned. With only [text] it is set at 15/22; with a [subtitle], [text] is a
 * `type-row-title` line over a `type-row-sub` line.
 */
@Composable
fun NoteRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    color: Color = palette().ink,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceCardGap),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
        verticalAlignment = Alignment.Top,
    ) {
        NewzIcon(icon, color)
        Column(Modifier.weight(1f)) {
            if (subtitle == null) {
                PlainText(text = text, style = NewzTokens.typeIntro, color = color)
            } else {
                PlainText(text = text, style = NewzTokens.typeRowTitle, color = color)
                PlainText(text = subtitle, style = NewzTokens.typeRowSub, color = color)
            }
        }
    }
}
