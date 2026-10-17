package app.newz.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.newz.catalog.Catalog
import app.newz.store.Level
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.IconButton
import app.newz.ui.components.ListRow
import app.newz.ui.components.Panel
import app.newz.ui.components.PlainText
import app.newz.ui.components.SegmentedControl
import app.newz.ui.components.TopicChip
import app.newz.ui.local.ZipCodeSheet
import app.newz.ui.nav.Screen
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens

/** Followed topics shows this many rows before "Show all {n}". */
private const val TOPICS_PREVIEW = 3

private val LEVELS = listOf(Level.NORMAL, Level.MORE, Level.MUCH_MORE)
private val LEVEL_LABELS = listOf("Normal", "More", "Much more")

/** Test tags on the Topics rows. */
object TopicsSemantics {
    /** The row of a followed or keyword topic, `topic:<id>`. */
    fun row(topicId: String): String = "topic:$topicId"
}

// Screen marker for `settings-topics` sits with the settings family in SettingsScreen.kt.

/**
 * Topics & keywords (design `settings-topics`): Followed topics (× Unfollow with Undo, the level
 * control, the Much-more note, "Show all {n}" after three; Local shows its zip and metro and opens
 * the zip sheet), Your keyword topics, Muted topics (Restore) and Muted keywords (chips that
 * unmute on tap, or a hint when there are none).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var editZip by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.reload() }
    SettingsFrame(Screen.Topics, "Topics & keywords", viewModel, onBack) {
        val p = prefs ?: return@SettingsFrame
        val followed = viewModel.followedTopics(p)
        Panel(title = "Followed topics", count = "${followed.size}") {
            val shown = if (showAll) followed else followed.take(TOPICS_PREVIEW)
            shown.forEachIndexed { index, topic ->
                val local = topic.id == Catalog.LOCAL_TOPIC
                TopicRow(
                    topic = topic,
                    removeLabel = "Unfollow ${topic.name}",
                    showDivider = index > 0,
                    viewModel = viewModel,
                    onClick = if (local) ({ editZip = true }) else null,
                    extra =
                        if (local && p.zip != null) {
                            { Button(text = "Clear", onClick = viewModel::clearLocal, variant = ButtonVariant.Text) }
                        } else {
                            null
                        },
                )
            }
            if (!showAll && followed.size > TOPICS_PREVIEW) {
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(
                        text = "Show all ${followed.size}",
                        onClick = { showAll = true },
                        variant = ButtonVariant.Text,
                    )
                }
            }
        }
        val keywordTopics = viewModel.keywordTopics(p)
        if (keywordTopics.isNotEmpty()) {
            Panel(title = "Your keyword topics", count = "${keywordTopics.size}") {
                keywordTopics.forEachIndexed { index, topic ->
                    TopicRow(
                        topic = topic,
                        removeLabel = "Remove ${topic.name}",
                        showDivider = index > 0,
                        viewModel = viewModel,
                    )
                }
            }
        }
        val muted = viewModel.mutedTopics(p)
        Panel(title = "Muted topics", count = "${muted.size}") {
            muted.forEachIndexed { index, topic ->
                ListRow(
                    title = topic.name,
                    showDivider = index > 0,
                    modifier = Modifier.testTag(TopicsSemantics.row(topic.id)),
                    trailing = {
                        Button(
                            text = "Restore",
                            onClick = { viewModel.restoreTopic(topic.id) },
                            variant = ButtonVariant.Text,
                            icon = NewzIcons.iconRestore,
                        )
                    },
                )
            }
        }
        Panel(title = "Muted keywords", count = "${p.mutedKeywords.size}") {
            if (p.mutedKeywords.isEmpty()) {
                Hint("No muted keywords. Mute a word or name from any article’s ⋮ menu.")
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(NewzTokens.spaceL),
                    horizontalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                    verticalArrangement = Arrangement.spacedBy(NewzTokens.spaceS),
                ) {
                    p.mutedKeywords.forEach { keyword ->
                        TopicChip(
                            text = keyword.text,
                            onClick = { viewModel.unmuteKeyword(keyword.text) },
                            muted = true,
                            modifier = Modifier.semantics { contentDescription = "Unmute ${keyword.text}" },
                        )
                    }
                }
            }
        }
    }
    if (editZip) {
        ZipCodeSheet(
            resolver = viewModel.zipResolver,
            onSave = { area ->
                viewModel.saveZip(area)
                editZip = false
            },
            onDismiss = { editZip = false },
            initialZip = prefs?.zip,
        )
    }
}

/**
 * A followed or keyword topic: its name (and [TopicItem.subtitle]), an × [IconButton] spoken
 * [removeLabel], and the Normal / More / Much more control, with the note at Much more.
 */
@Composable
private fun TopicRow(
    topic: TopicItem,
    removeLabel: String,
    showDivider: Boolean,
    viewModel: SettingsViewModel,
    onClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val c = LocalNewzPalette.current
    Column(Modifier.fillMaxWidth().testTag(TopicsSemantics.row(topic.id))) {
        if (showDivider) Divider()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewzTokens.touchTarget)
                    .padding(start = NewzTokens.spaceL, top = NewzTokens.spaceS, end = NewzTokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = NewzTokens.touchTarget)
                    .then(onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier),
                verticalArrangement = Arrangement.Center,
            ) {
                PlainText(text = topic.name, style = NewzTokens.typeRowTitle, color = c.ink)
                topic.subtitle?.let { PlainText(text = it, style = NewzTokens.typeRowSub, color = c.inkMuted) }
            }
            extra?.invoke()
            IconButton(
                icon = NewzIcons.iconClose,
                contentDescription = removeLabel,
                onClick = { viewModel.unfollow(topic.id) },
            )
        }
        SegmentedControl(
            options = LEVEL_LABELS,
            selectedIndex = LEVELS.indexOf(topic.level),
            onSelect = { viewModel.setLevel(topic.id, LEVELS[it]) },
            modifier = Modifier.padding(start = NewzTokens.spaceL, end = NewzTokens.spaceL, top = NewzTokens.spaceS),
        )
        if (topic.level == Level.MUCH_MORE) {
            PlainText(
                text = "${topic.name} is at the top level, so Show more like this is turned off for it.",
                style = NewzTokens.typeRowSub,
                color = c.inkMuted,
                modifier =
                    Modifier.padding(
                        start = NewzTokens.spaceL,
                        end = NewzTokens.spaceL,
                        top = NewzTokens.spaceS,
                    ),
            )
        }
        Spacer(Modifier.height(NewzTokens.spaceL))
    }
}
