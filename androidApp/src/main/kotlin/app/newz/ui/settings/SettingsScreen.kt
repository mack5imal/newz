package app.newz.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.newz.store.TextSize
import app.newz.ui.components.AppBarSub
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.ConfirmSheet
import app.newz.ui.components.ListRow
import app.newz.ui.components.Panel
import app.newz.ui.components.PlainText
import app.newz.ui.components.SegmentedControl
import app.newz.ui.components.Snackbar
import app.newz.ui.components.Switch
import app.newz.ui.feed.FeedMessage
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens

/** Where the Settings home rows lead. */
class SettingsDestinations(
    val onBack: () -> Unit,
    val open: (Screen) -> Unit,
)

private val TEXT_SIZES = listOf(TextSize.SMALL, TextSize.DEFAULT, TextSize.LARGE, TextSize.EXTRA_LARGE)
private val TEXT_SIZE_LABELS = listOf("Small", "Default", "Large", "Extra large")

/** Text start inside a `ListRow` with an icon: 16 padding + 22 icon + 14 gap. */
private val ROW_TEXT_INSET = NewzTokens.spaceL + NewzTokens.iconSize + NewzTokens.spaceCardGap

// design: settings
// design: settings-sources
// design: settings-topics
// design: settings-hidden
// The settings family shares SettingsFrame (below), so its four screen markers sit together here:
// the screen check matches `settings` as a whole word, which a marker for a sub-screen id in its
// own file would also satisfy. Sources, Topics and Hidden are built in SourcesScreen.kt,
// TopicsScreen.kt and HiddenScreen.kt.

/**
 * Settings home (design `settings`): Your feed, Reading and Your data panels. The Your feed rows
 * carry live counts; the paywall filter and reading history switches and the text size control
 * (applied at once through the theme's text scale) act immediately. Clear reading history asks
 * first in a [ConfirmSheet], since it can't be undone.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    destinations: SettingsDestinations,
) {
    val prefs by viewModel.prefs.collectAsState()
    val counts by viewModel.counts.collectAsState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.reload() }
    SettingsFrame(Screen.Settings, "Settings", viewModel, destinations.onBack) {
        val p = prefs ?: return@SettingsFrame
        val sources = viewModel.sources(p, counts)
        val failing = sources.count { it.failingSince != null }
        Panel(title = "Your feed") {
            ListRow(
                title = "Sources",
                subtitle =
                    if (failing >
                        0
                    ) {
                        "${sources.size} followed · $failing not loading"
                    } else {
                        "${sources.size} followed"
                    },
                icon = NewzIcons.iconRss,
                showDivider = false,
                onClick = { destinations.open(Screen.Sources) },
            )
            val topics = p.followedTopics.size + p.customTopics.size
            ListRow(
                title = "Topics & keywords",
                subtitle = "$topics topics · ${p.mutedKeywords.size} muted keywords",
                icon = NewzIcons.iconTag,
                onClick = { destinations.open(Screen.Topics) },
            )
            ListRow(
                title = "Hidden articles",
                subtitle = "${counts.hidden} hidden",
                icon = NewzIcons.iconEyeOff,
                onClick = { destinations.open(Screen.Hidden) },
            )
            ListRow(
                title = "Saved",
                subtitle = "${counts.saved} saved for later",
                icon = NewzIcons.iconBookmark,
                onClick = { destinations.open(Screen.Saved) },
            )
            ListRow(
                title = "Find sources",
                subtitle = "Search outlets and topics, or add any site",
                icon = NewzIcons.iconSearch,
                onClick = { destinations.open(Screen.Search) },
            )
            ListRow(
                title = "Suggestions",
                subtitle = "Worked out on this phone from what you follow",
                icon = NewzIcons.iconCompass,
                onClick = { destinations.open(Screen.Suggestions) },
            )
        }
        Panel(title = "Reading") {
            ListRow(
                title = "Paywall filter",
                subtitle = "Hide outlets that need a subscription",
                icon = NewzIcons.iconLock,
                showDivider = false,
                trailing = {
                    Switch(
                        checked = p.paywallFilterOn,
                        onCheckedChange = viewModel::setPaywallFilter,
                        contentDescription = "Paywall filter",
                    )
                },
            )
            ListRow(
                title = "Reading history",
                subtitle = "Kept only on this phone, to tune your feed and suggestions",
                icon = NewzIcons.iconClock,
                trailing = {
                    Switch(
                        checked = p.readingHistoryOn,
                        onCheckedChange = viewModel::setReadingHistory,
                        contentDescription = "Reading history",
                    )
                },
            )
            Divider()
            Button(
                text = "Clear reading history",
                onClick = { confirmClear = true },
                variant = ButtonVariant.Outline,
                modifier =
                    Modifier.padding(
                        start = ROW_TEXT_INSET,
                        top = NewzTokens.spaceS,
                        bottom = NewzTokens.spaceS,
                    ),
            )
            ListRow(title = "Text size", icon = NewzIcons.iconTextSize)
            SegmentedControl(
                options = TEXT_SIZE_LABELS,
                selectedIndex = TEXT_SIZES.indexOf(p.textSize),
                onSelect = { viewModel.setTextSize(TEXT_SIZES[it]) },
                modifier =
                    Modifier.padding(
                        start = NewzTokens.spaceL,
                        end = NewzTokens.spaceL,
                        bottom = NewzTokens.spaceL,
                    ),
            )
        }
        Panel(title = "Your data") {
            ListRow(
                title = "Backup & restore",
                subtitle = "Export or import your choices as a file",
                icon = NewzIcons.iconDownload,
                showDivider = false,
                onClick = { destinations.open(Screen.Backup) },
            )
            ListRow(
                title = "About",
                subtitle = "Privacy statement and credits",
                icon = NewzIcons.iconShield,
                onClick = { destinations.open(Screen.About) },
            )
        }
    }
    if (confirmClear) {
        ConfirmSheet(
            title = "Clear reading history?",
            message = "This removes what you’ve opened from this phone. It can’t be undone.",
            confirmLabel = "Clear history",
            onConfirm = {
                confirmClear = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** The 1 dp `line-subtle` rule between rows that are not `ListRow`s. */
@Composable
internal fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(NewzTokens.divider)
            .background(LocalNewzPalette.current.lineSubtle),
    )
}

/**
 * The frame the Settings screens share: `AppBarSub` [title] with Back (and [trailing]), a
 * scrolling column of panels on `paper`, and the view model's message `Snackbar` (with Undo when
 * the action has one).
 */
@Composable
internal fun SettingsFrame(
    screen: Screen,
    title: String,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val message by viewModel.message.collectAsState()
    ScreenFrame(screen, title, message, viewModel::dismissMessage, onBack, trailing, content = content)
}

/** [SettingsFrame] for any screen with its own [message] (a `Snackbar`, cleared by [onDismissMessage]). */
@Composable
internal fun ScreenFrame(
    screen: Screen,
    title: String,
    message: FeedMessage?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalNewzPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .testTag(Screen.tagOf(screen)),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppBarSub(title = title, onBack = onBack, titleContent = titleContent, trailing = trailing)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = NewzTokens.spaceCardGap),
            ) {
                content()
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        message?.let { m ->
            Snackbar(
                message = m.text,
                actionLabel = m.actionLabel,
                onAction = {
                    m.onAction()
                    onDismissMessage()
                },
                onTimeout = onDismissMessage,
                timeoutKey = m.key,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** An intro line under the app bar (`type-intro` in `ink-muted`, padding 0 16 12). */
@Composable
internal fun Intro(text: String) {
    PlainText(
        text = text,
        style = NewzTokens.typeIntro,
        color = LocalNewzPalette.current.inkMuted,
        modifier = Modifier.padding(start = NewzTokens.spaceL, end = NewzTokens.spaceL, bottom = NewzTokens.spaceM),
    )
}
