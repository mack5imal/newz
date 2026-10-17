package app.newz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/** The app-bar surface: `navy` in light, `paper` in dark (the same navy, with a `sky` rule). */
@Composable
private fun AppBarSurface(
    modifier: Modifier,
    paddingStart: Dp,
    paddingEnd: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    val c = palette()
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(if (c.isDark) c.paper else c.navy)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = NewzTokens.appBarHeight)
                        .padding(start = paddingStart, end = paddingEnd),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            if (c.isDark) Box(Modifier.fillMaxWidth().height(NewzTokens.rule).background(c.sky))
        }
        SplitRule()
    }
}

/**
 * `AppBarMain` — the feed's app bar: the [FlapWordmark] "Newz" at the left and the Saved and
 * Settings [NavButton]s at the right, in a 64 dp row below the status bar. While [busy] (the feed
 * is loading or refreshing) the wordmark tiles flip.
 */
@Composable
fun AppBarMain(
    onSaved: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    wordmark: String = "Newz",
    savedLabel: String = "Saved",
    settingsLabel: String = "Settings",
) {
    AppBarSurface(modifier, paddingStart = NewzTokens.spaceL, paddingEnd = NewzTokens.spaceXs) {
        Box(Modifier.weight(1f)) { FlapWordmark(busy = busy, label = wordmark) }
        NavButton(label = savedLabel, icon = NewzIcons.iconBookmark, onClick = onSaved)
        NavButton(label = settingsLabel, icon = NewzIcons.iconSettings, onClick = onSettings)
    }
}

/**
 * `AppBarSub` — the app bar on every other full screen: a back [IconButton] and the
 * `type-screen-title` [title], with an optional [trailing] slot (a [NavButton]). Pass
 * [titleContent] to put a [SearchField] in place of the title.
 */
@Composable
fun AppBarSub(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = palette()
    AppBarSurface(modifier, paddingStart = NewzTokens.spaceXs, paddingEnd = NewzTokens.spaceS) {
        IconButton(icon = NewzIcons.iconBack, contentDescription = backLabel, onClick = onBack, tint = c.sky)
        if (titleContent != null) {
            titleContent()
        } else {
            SignageText(
                text = title,
                style = NewzTokens.typeScreenTitle,
                color = c.sky,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * `SplitRule` — under every app bar in light theme only: a 2 dp `paper` gap, then a 2 dp `navy`
 * line. Dark draws nothing (its app bar carries the `sky` rule).
 */
@Composable
fun SplitRule(modifier: Modifier = Modifier) {
    val c = palette()
    if (c.isDark) return
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(NewzTokens.rule).background(c.paper))
        Box(Modifier.fillMaxWidth().height(NewzTokens.rule).background(c.navy))
    }
}

/** `NavButton` — an app-bar text button: 48 dp tall, padding 0 10, a 20 dp icon, gap 6, `type-nav` in `sky`. */
@Composable
fun NavButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palette()
    Row(
        modifier =
            modifier
                .defaultMinSize(minWidth = NewzTokens.touchTarget, minHeight = NewzTokens.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = NewzTokens.navButtonPaddingH),
        horizontalArrangement = Arrangement.spacedBy(NewzTokens.gapIconLabel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewzIcon(icon, c.sky, size = NewzTokens.iconSizeInControl)
        SignageText(text = label, style = NewzTokens.typeNav, color = c.sky)
    }
}
