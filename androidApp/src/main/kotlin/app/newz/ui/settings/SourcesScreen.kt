package app.newz.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.newz.catalog.PaywallStatus
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.Checkbox
import app.newz.ui.components.ListRow
import app.newz.ui.components.NavButton
import app.newz.ui.components.NewzIcon
import app.newz.ui.components.Panel
import app.newz.ui.components.PlainText
import app.newz.ui.components.Tag
import app.newz.ui.components.TagKind
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens

/** Following shows this many outlets before "Show all {n}". */
private const val SOURCES_PREVIEW = 4

/** Test tags on the Sources rows. */
object SourcesSemantics {
    /** The row of a followed outlet, `source:<outletId>`. */
    fun row(outletId: String): String = "source:$outletId"

    const val FLAGGED_PANEL = "panel:flagged"
    const val REMOVED_PANEL = "panel:removed"
}

// Screen marker for `settings-sources` sits with the settings family in SettingsScreen.kt.

/**
 * Sources (design `settings-sources`): Following (failing feeds pinned first with Retry and
 * Remove; each outlet's section feeds as checkboxes, the first outlet expanded; Paywall and
 * Metered tags; "Show all {n}" after four), Marked paywalled by you (Unflag) and Removed
 * (Restore, or a hint when empty). Remove shows "{outlet} removed" with Undo.
 */
@Composable
fun SourcesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    val counts by viewModel.counts.collectAsState()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { viewModel.reload() }
    SettingsFrame(
        Screen.Sources,
        "Sources",
        viewModel,
        onBack,
        trailing = { NavButton(label = "Add", icon = NewzIcons.iconPlus, onClick = onAdd) },
    ) {
        val p = prefs ?: return@SettingsFrame
        val sources = viewModel.sources(p, counts)
        val open =
            expanded
                ?: listOfNotNull(sources.firstOrNull { it.failingSince == null && it.sections.isNotEmpty() }?.outletId)
        Panel(title = "Following", count = "${sources.size}") {
            val shown = if (showAll) sources else sources.take(SOURCES_PREVIEW)
            shown.forEachIndexed { index, source ->
                SourceRow(
                    source = source,
                    showDivider = index > 0,
                    expanded = source.outletId in open,
                    onToggleExpanded = {
                        expanded = if (source.outletId in open) open - source.outletId else open + source.outletId
                    },
                    viewModel = viewModel,
                )
            }
            if (!showAll && sources.size > SOURCES_PREVIEW) {
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(
                        text = "Show all ${sources.size}",
                        onClick = { showAll = true },
                        variant = ButtonVariant.Text,
                    )
                }
            }
        }
        val flagged = viewModel.paywallFlagged(p)
        Panel(
            title = "Marked paywalled by you",
            count = "${flagged.size}",
            modifier = Modifier.testTag(SourcesSemantics.FLAGGED_PANEL),
        ) {
            flagged.forEachIndexed { index, outlet ->
                ListRow(
                    title = outlet.name,
                    subtitle = "Hidden while the paywall filter is on",
                    showDivider = index > 0,
                    trailing = {
                        Button(
                            text = "Unflag",
                            onClick = { viewModel.unflagPaywall(outlet.outletId) },
                            variant = ButtonVariant.Text,
                        )
                    },
                )
            }
        }
        val removed = viewModel.removed(p)
        Panel(
            title = "Removed",
            count = "${removed.size}",
            modifier = Modifier.testTag(SourcesSemantics.REMOVED_PANEL),
        ) {
            if (removed.isEmpty()) {
                Hint("Outlets you remove are listed here, so you can bring them back any time.")
            }
            removed.forEachIndexed { index, outlet ->
                ListRow(
                    title = outlet.name,
                    subtitle = "Removed ${SettingsViewModel.shortDate(outlet.at)}",
                    showDivider = index > 0,
                    trailing = {
                        Button(
                            text = "Restore",
                            onClick = { viewModel.restoreOutlet(outlet.outletId) },
                            variant = ButtonVariant.Text,
                            icon = NewzIcons.iconRestore,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceItem,
    showDivider: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val c = LocalNewzPalette.current
    val remove = { viewModel.removeOutlet(source.outletId) }
    Column(Modifier.fillMaxWidth().testTag(SourcesSemantics.row(source.outletId))) {
        if (showDivider) Divider()
        val since = source.failingSince
        if (since != null) {
            Column(
                Modifier.padding(
                    start = NewzTokens.spaceL,
                    top = NewzTokens.spaceM,
                    end = NewzTokens.spaceS,
                    bottom = NewzTokens.spaceM,
                ),
                verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceXs),
            ) {
                PlainText(text = source.name, style = NewzTokens.typeRowTitle, color = c.ink)
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NewzIcon(NewzIcons.iconWarning, c.alert, size = NewzTokens.iconSizeInControl)
                    PlainText(
                        text = "Not loading since ${SettingsViewModel.shortDate(since)}",
                        style = NewzTokens.typeRowSub,
                        color = c.alert,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = NewzTokens.spaceS),
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceM),
                ) {
                    Button(
                        text = "Retry",
                        onClick = { viewModel.retry(source) },
                        variant = ButtonVariant.Outline,
                        icon = NewzIcons.iconRetry,
                    )
                    Button(text = "Remove", onClick = remove, variant = ButtonVariant.Text)
                }
            }
            return@Column
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.rowHeight)
                    .then(
                        if (source.sections.isNotEmpty()) Modifier.clickable(onClick = onToggleExpanded) else Modifier,
                    ).padding(
                        start = NewzTokens.spaceL,
                        top = NewzTokens.spaceS,
                        end = NewzTokens.spaceS,
                        bottom = NewzTokens.spaceS,
                    ),
            horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceCardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainText(
                        text = source.name,
                        style = NewzTokens.typeRowTitle,
                        color = c.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when (source.paywall) {
                        PaywallStatus.HARD -> Tag(TagKind.Paywall)
                        PaywallStatus.METERED -> Tag(TagKind.Metered)
                        PaywallStatus.NONE -> Unit
                    }
                }
                sourceSubtitle(source)?.let {
                    PlainText(text = it, style = NewzTokens.typeRowSub, color = c.inkMuted)
                }
            }
            Button(text = "Remove", onClick = remove, variant = ButtonVariant.Text)
        }
        if (expanded && source.sections.isNotEmpty()) {
            Column(
                Modifier
                    .padding(start = NewzTokens.spaceL, end = NewzTokens.spaceL, bottom = NewzTokens.spaceS)
                    .semantics { contentDescription = "${source.name} section feeds" },
            ) {
                source.sections.forEach { section ->
                    Checkbox(
                        checked = section.checked,
                        onCheckedChange = { viewModel.setSection(source.outletId, section.feedId, it) },
                        label = section.name,
                    )
                }
            }
        }
    }
}

/**
 * "{n} of {m} section feeds" or "All {m} section feeds"; a `hard` outlet adds "· hidden while the
 * paywall filter is on" (a `metered` outlet never does, SPEC S12).
 */
private fun sourceSubtitle(source: SourceItem): String? {
    if (source.sections.isEmpty()) return source.address
    val n = source.followedCount
    val m = source.sections.size
    val hidden = source.paywall == PaywallStatus.HARD
    return when {
        n == m && hidden -> "All $m section feeds · hidden while the paywall filter is on"
        n == m -> "All $m section feeds"
        hidden -> "$n of $m section feeds · hidden while the paywall filter is on"
        else -> "$n of $m section feeds"
    }
}

/** A hint line inside an empty panel. */
@Composable
internal fun Hint(text: String) {
    val c = LocalNewzPalette.current
    PlainText(
        text = text,
        style = NewzTokens.typeRowSub,
        color = c.inkMuted,
        modifier = Modifier.padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceCardGap),
    )
}
