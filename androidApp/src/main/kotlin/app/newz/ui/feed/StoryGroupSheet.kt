package app.newz.ui.feed

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import app.newz.dedup.StoryGroup
import app.newz.feed.Article
import app.newz.ui.components.BottomSheet
import app.newz.ui.components.PlainText
import app.newz.ui.components.StoryRow
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

private var SemanticsPropertyReceiver.storyRowUrl by FeedSemantics.StoryRowUrl

// design: story-group

/**
 * "Same story, other outlets" (design `story-group`): a `BottomSheet` with the meta line
 * "Same story · {n} outlets" (n counts every outlet, the card's own included), the headline,
 * and one `StoryRow` per outlet, newest first. Catalog `hard` outlets get a "Paywall" `Tag` and
 * `metered` ones "Metered" (SPEC S12). The sheet hugs its content; a long list (`--many`) stops
 * just below the app bar and scrolls inside the sheet. Tapping a row calls [onOpen].
 */
@Composable
fun StoryGroupSheet(
    group: StoryGroup,
    viewModel: FeedViewModel,
    onOpen: (Article) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalNewzPalette.current
    val rows = group.members().distinctBy { it.outletId }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxListHeight = maxHeight - NewzTokens.storySheetTopGap
        BottomSheet(onDismiss = onDismiss, label = "Same story · ${rows.size} outlets") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier.padding(
                        start = NewzTokens.spaceXl,
                        top = NewzTokens.spaceS,
                        end = NewzTokens.spaceXl,
                        bottom = NewzTokens.spaceL,
                    ),
                ) {
                    SignageText(
                        text = "Same story · ${rows.size} outlets",
                        style = NewzTokens.typeMeta,
                        color = c.inkMuted,
                    )
                    PlainText(
                        text = group.lead.title,
                        style = NewzTokens.typeHeadline,
                        color = c.ink,
                        modifier = Modifier.padding(top = NewzTokens.gapIconLabel),
                    )
                }
                rows.forEach { article ->
                    StoryRow(
                        outlet = viewModel.outletName(article),
                        age = viewModel.shortAge(article),
                        spokenAge = viewModel.spokenAge(article),
                        headline = article.title,
                        tag = viewModel.paywallTag(article),
                        onClick = { onOpen(article) },
                        modifier =
                            Modifier
                                .testTag(FeedSemantics.STORY_ROW_TAG)
                                .semantics { storyRowUrl = article.url },
                    )
                }
                PlainText(
                    text = "Each article opens in your browser.",
                    style = NewzTokens.typeRowSub,
                    color = c.inkMuted,
                    modifier = Modifier.padding(horizontal = NewzTokens.spaceXl, vertical = NewzTokens.spaceCardGap),
                )
            }
        }
    }
}
