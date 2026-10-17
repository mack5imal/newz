package app.newz.ui.suggestions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.newz.catalog.PaywallStatus
import app.newz.discover.Ranked
import app.newz.ui.components.NoteRow
import app.newz.ui.components.Panel
import app.newz.ui.components.RankTile
import app.newz.ui.components.StatePanel
import app.newz.ui.components.TagKind
import app.newz.ui.nav.Screen
import app.newz.ui.search.ResultRow
import app.newz.ui.settings.ScreenFrame
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons

/** Test tags on the suggestion rows. */
object SuggestionsSemantics {
    /** A suggested outlet row, `suggest-outlet:<outletId>`. */
    fun outlet(outletId: String): String = "suggest-outlet:$outletId"

    /** A suggested topic row, `suggest-topic:<topicId>`. */
    fun topic(topicId: String): String = "suggest-topic:$topicId"
}

@Suppress("ktlint:standard:max-line-length")
private const val EMPTY_HINT =
    "Suggestions come from the sources and topics you follow. Check back after you’ve followed a few more."

// design: suggestions

/**
 * Suggested for you (design `suggestions`, SPEC S18): the on-device note, then ranked Outlets
 * ("{topic} · {reason}") and Topics (the reason), each with a `RankTile` and Outline "Follow"
 * that turns into a "Following" tag. No reading counts or statistics are shown. With nothing to
 * suggest, a `StatePanel` offers "Find sources" ([onFindSources] opens search).
 */
@Composable
fun SuggestionsScreen(
    viewModel: SuggestionsViewModel,
    onBack: () -> Unit,
    onFindSources: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    ScreenFrame(
        screen = Screen.Suggestions,
        title = "Suggested for you",
        message = null,
        onDismissMessage = {},
        onBack = onBack,
    ) {
        val p = prefs ?: return@ScreenFrame
        val s = suggestions ?: return@ScreenFrame
        NoteRow(
            icon = NewzIcons.iconShield,
            text = "Worked out on this phone from what you follow and read. Nothing is sent anywhere.",
            color = LocalNewzPalette.current.inkMuted,
        )
        if (s.outlets.isEmpty() && s.topics.isEmpty()) {
            StatePanel(
                title = "No suggestions right now",
                body = EMPTY_HINT,
                icon = NewzIcons.iconCompass,
                actionLabel = "Find sources",
                onAction = onFindSources,
            )
            return@ScreenFrame
        }
        if (s.outlets.isNotEmpty()) {
            Panel(title = "Outlets") {
                s.outlets.forEachIndexed { index, ranked ->
                    SuggestionRow(
                        rank = index + 1,
                        ranked = ranked,
                        subtitle = ranked.topicName?.let { "$it · ${ranked.reason}" } ?: ranked.reason,
                        following = viewModel.isFollowingOutlet(p, ranked.id),
                        onFollow = { viewModel.followOutlet(ranked.id) },
                        tag =
                            when (viewModel.outletPaywall(ranked.id)) {
                                PaywallStatus.HARD -> TagKind.Paywall
                                PaywallStatus.METERED -> TagKind.Metered
                                PaywallStatus.NONE, null -> null
                            },
                        modifier = Modifier.testTag(SuggestionsSemantics.outlet(ranked.id)),
                    )
                }
            }
        }
        if (s.topics.isNotEmpty()) {
            Panel(title = "Topics") {
                s.topics.forEachIndexed { index, ranked ->
                    SuggestionRow(
                        rank = index + 1,
                        ranked = ranked,
                        subtitle = ranked.reason,
                        following = viewModel.isFollowingTopic(p, ranked.id),
                        onFollow = { viewModel.followTopic(ranked.id) },
                        modifier = Modifier.testTag(SuggestionsSemantics.topic(ranked.id)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    rank: Int,
    ranked: Ranked,
    subtitle: String,
    following: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
    tag: TagKind? = null,
) {
    ResultRow(
        title = ranked.name,
        subtitle = subtitle,
        showDivider = rank > 1,
        tag = tag,
        following = following,
        onFollow = onFollow,
        modifier = modifier,
        leading = { RankTile(rank) },
    )
}
