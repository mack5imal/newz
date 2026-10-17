package app.newz.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.newz.engine.FeedCard
import app.newz.ui.components.BottomSheet
import app.newz.ui.components.PlainText
import app.newz.ui.components.SheetActionRow
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import app.newz.ui.theme.SignageText

/** What the card menu asks the feed to do; the menu has already closed when each is called. */
data class CardMenuActions(
    val onToggleSaved: () -> Unit,
    val onHide: () -> Unit,
    val onShowMore: () -> Unit,
    val onRemoveOutlet: () -> Unit,
    val onMuteTopic: (topicId: String) -> Unit,
    val onMuteKeyword: () -> Unit,
    val onFlagPaywall: () -> Unit,
)

// design: card-menu

/**
 * Article actions (design `card-menu`): a `BottomSheet` with the "{outlet} · {age}" meta line
 * and the full headline, then the `SheetActionRow`s in the design's order: Save for later (or
 * Unsave with the filled bookmark), Hide this article, Show more like this (disabled with
 * "Already showing much more of {topic}" at Much more, SPEC S10), a divider, Don't show content
 * from {outlet}, one Don't show {topic} per article topic, Mute a keyword…, and Mark {outlet} as
 * paywalled (left out once the outlet is flagged). Choosing a row calls [onDismiss] first.
 */
@Composable
fun CardMenuSheet(
    card: FeedCard,
    viewModel: FeedViewModel,
    paywallFlagged: Boolean,
    actions: CardMenuActions,
    onDismiss: () -> Unit,
) {
    val c = LocalNewzPalette.current
    val article = card.group.lead
    val outlet = viewModel.outletName(article)
    val primary = article.primaryTopic

    fun choose(action: () -> Unit): () -> Unit =
        {
            onDismiss()
            action()
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight - NewzTokens.storySheetTopGap
        BottomSheet(onDismiss = onDismiss, label = "Article actions") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier.padding(
                        start = NewzTokens.spaceXl,
                        top = NewzTokens.spaceS,
                        end = NewzTokens.spaceXl,
                        bottom = NewzTokens.spaceCardGap,
                    ),
                ) {
                    SignageText(
                        text = "$outlet · ${viewModel.shortAge(article)}",
                        style = NewzTokens.typeMeta,
                        color = c.inkMuted,
                    )
                    PlainText(
                        text = article.title,
                        style = NewzTokens.typeRowTitle,
                        color = c.ink,
                        modifier = Modifier.padding(top = NewzTokens.spaceXs),
                    )
                }
                Box(Modifier.fillMaxWidth().height(NewzTokens.divider).background(c.lineSubtle))
                if (card.saved) {
                    SheetActionRow(
                        label = "Unsave",
                        icon = NewzIcons.iconBookmarkFilled,
                        onClick = choose(actions.onToggleSaved),
                    )
                } else {
                    SheetActionRow(
                        label = "Save for later",
                        icon = NewzIcons.iconBookmark,
                        onClick = choose(actions.onToggleSaved),
                    )
                }
                SheetActionRow(
                    label = "Hide this article",
                    icon = NewzIcons.iconEyeOff,
                    onClick = choose(actions.onHide),
                )
                if (primary != null) {
                    val atMax = viewModel.isAtMuchMore(primary)
                    SheetActionRow(
                        label = "Show more like this",
                        icon = NewzIcons.iconArrowUp,
                        onClick = choose(actions.onShowMore),
                        enabled = !atMax,
                        explanation =
                            if (atMax) {
                                "Already showing much more of ${viewModel.topicName(
                                    primary,
                                )}"
                            } else {
                                null
                            },
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = NewzTokens.spaceXl, vertical = NewzTokens.spaceXs)
                        .fillMaxWidth()
                        .height(NewzTokens.divider)
                        .background(c.lineSubtle),
                )
                SheetActionRow(
                    label = "Don't show content from $outlet",
                    icon = NewzIcons.iconBan,
                    onClick = choose(actions.onRemoveOutlet),
                )
                article.topics.distinct().forEach { topic ->
                    SheetActionRow(
                        label = "Don't show ${viewModel.topicName(topic)}",
                        icon = NewzIcons.iconMinusCircle,
                        onClick = choose { actions.onMuteTopic(topic) },
                    )
                }
                SheetActionRow(
                    label = "Mute a keyword…",
                    icon = NewzIcons.iconMute,
                    onClick = choose(actions.onMuteKeyword),
                )
                if (!paywallFlagged) {
                    SheetActionRow(
                        label = "Mark $outlet as paywalled",
                        icon = NewzIcons.iconLock,
                        onClick = choose(actions.onFlagPaywall),
                    )
                }
                Spacer(Modifier.height(NewzTokens.spaceXl))
            }
        }
    }
}
