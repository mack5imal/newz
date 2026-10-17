package app.newz.ui.feed

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import app.newz.dedup.StoryGroup
import app.newz.engine.EmptyReason
import app.newz.engine.FeedCard
import app.newz.engine.FeedState
import app.newz.feed.Article
import app.newz.ui.components.AppBarMain
import app.newz.ui.components.ArticleCard
import app.newz.ui.components.BoardPanel
import app.newz.ui.components.Button
import app.newz.ui.components.ButtonVariant
import app.newz.ui.components.LoadingBoard
import app.newz.ui.components.OfflineBanner
import app.newz.ui.components.PlainText
import app.newz.ui.components.SkeletonCard
import app.newz.ui.components.Snackbar
import app.newz.ui.components.StatePanel
import app.newz.ui.components.TopicChip
import app.newz.ui.components.TopicChipRow
import app.newz.ui.theme.LocalNewzPalette
import app.newz.ui.theme.NewzIcons
import app.newz.ui.theme.NewzTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt
import kotlin.time.Instant

/** The all-filtered body and the offline-empty body (SPEC S25 wording), verbatim from the Copy. */
@Suppress("ktlint:standard:max-line-length")
private const val ALL_FILTERED_BODY =
    "The topics, sources and keywords you’ve turned off cover every article from the past 7 days. You can review and undo any of them."

@Suppress("ktlint:standard:max-line-length")
private const val OFFLINE_EMPTY_BODY =
    "New articles load as soon as you’re connected again. Articles you saved for later are still in Saved."

/** The pull-to-refresh panel: the `LoadingBoard` tiles with 12 above and below. */
private val REFRESH_PANEL_HEIGHT =
    NewzTokens.tileSize.height + NewzTokens.spaceXs + NewzTokens.space2xs + NewzTokens.spaceM * 2

/** `loadMore()` fires when the last visible card is within this many items of the end. */
private const val LOAD_MORE_THRESHOLD = 5

/** What tests read off a card (and, as a list, off the feed). */
data class FeedCardInfo(
    val id: String,
    val url: String,
    val topics: List<String>,
    val date: Instant,
    val outlets: Int,
)

/** Semantics keys and test tags the feed exposes to UI tests. */
object FeedSemantics {
    const val LIST_TAG = "feedList"
    const val CARD_TAG = "articleCard"
    const val STORY_ROW_TAG = "storyRow"
    const val REFRESH_PANEL_TAG = "refreshPanel"

    val Card = SemanticsPropertyKey<FeedCardInfo>("FeedCard")
    val Cards = SemanticsPropertyKey<List<FeedCardInfo>>("FeedCards")
    val StoryRowUrl = SemanticsPropertyKey<String>("StoryRowUrl")
}

private var SemanticsPropertyReceiver.feedCard by FeedSemantics.Card
private var SemanticsPropertyReceiver.feedCards by FeedSemantics.Cards

private fun FeedCard.info(): FeedCardInfo {
    val lead = group.lead
    return FeedCardInfo(lead.id, lead.url, lead.topics, lead.date, group.othersCount + 1)
}

// design: feed

/**
 * The feed (design `feed`): `AppBarMain` with Saved and Settings, then a list whose first item
 * is the `TopicChipRow` (it scrolls away), then `ArticleCard`s, with every drawn state: first
 * load, filter active / empty, end of feed, all filtered, offline with cache and offline empty.
 * Pull to refresh slides the `LoadingBoard` tiles down on a `paper` panel and moves the list down
 * below it. ⋮ and a long-press open [CardMenuSheet],
 * whose Mute a keyword… opens [KeywordMuteSheet]. A card removed by an action collapses out
 * (`card-collapse`). The Undo `Snackbar` host lives here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = LocalNewzPalette.current
    val context = LocalContext.current
    val state by viewModel.feed.collectAsState()
    val served by viewModel.servedFeed.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val message by viewModel.message.collectAsState()
    var openGroup by remember { mutableStateOf<StoryGroup?>(null) }
    var menuCardId by remember { mutableStateOf<String?>(null) }
    var muteHeadline by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val chips = prefs?.let(viewModel::chips).orEmpty()

    LoadMoreEffect(listState, served, viewModel::loadMore)

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize()) {
            AppBarMain(onSaved = onOpenSaved, onSettings = onOpenSettings)
            val pullState = rememberPullToRefreshState()
            // The pull reveals a `paper` panel holding the tiles from under the app bar and pushes
            // the list (chips first) down by as much, so the tiles never sit on the chips.
            val pulled = { pullState.distanceFraction.coerceIn(0f, 1f) }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                state = pullState,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                indicator = {
                    if (pullState.distanceFraction > 0f) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(REFRESH_PANEL_HEIGHT)
                                .offset {
                                    IntOffset(0, -((1f - pulled()) * REFRESH_PANEL_HEIGHT.toPx()).roundToInt())
                                }.background(c.paper)
                                .testTag(FeedSemantics.REFRESH_PANEL_TAG),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingBoard(label = null)
                        }
                    }
                },
            ) {
                val cards = (state as? FeedState.Content)?.cards.orEmpty()
                LazyColumn(
                    state = listState,
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, (pulled() * REFRESH_PANEL_HEIGHT.toPx()).roundToInt()) }
                            .testTag(FeedSemantics.LIST_TAG)
                            .semantics { feedCards = cards.map { it.info() } },
                ) {
                    item(key = "chips") {
                        TopicChipRow {
                            chips.forEach { chip ->
                                TopicChip(
                                    text = chip.label,
                                    selected = chip.id in selection,
                                    onClick = { viewModel.toggleTopic(chip.id) },
                                )
                            }
                        }
                    }
                    val selectedNames = chips.filter { it.id in selection }.map { it.label }
                    feedBody(
                        state = state,
                        selectedNames = selectedNames,
                        viewModel = viewModel,
                        onOpenSettings = onOpenSettings,
                        onCard = { viewModel.open(context, it) },
                        onShare = { viewModel.share(context, it) },
                        onMore = { menuCardId = it.group.lead.id },
                        onGroup = { openGroup = it },
                    )
                }
            }
        }
        message?.let { m ->
            Snackbar(
                message = m.text,
                actionLabel = m.actionLabel,
                onAction = {
                    m.onAction()
                    viewModel.dismissMessage()
                },
                onTimeout = viewModel::dismissMessage,
                timeoutKey = m.key,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        openGroup?.let { group ->
            StoryGroupSheet(
                group = group,
                viewModel = viewModel,
                onOpen = { viewModel.open(context, it) },
                onDismiss = { openGroup = null },
            )
        }
        val menuCard = menuCardId?.let { id -> (state as? FeedState.Content)?.cards?.find { it.group.lead.id == id } }
        // The card left the feed (a refresh dropped it): close the menu rather than reopen it later.
        LaunchedEffect(menuCardId, menuCard == null) { if (menuCard == null) menuCardId = null }
        menuCard?.let { card ->
            val article = card.group.lead
            CardMenuSheet(
                card = card,
                viewModel = viewModel,
                paywallFlagged = prefs?.paywallFlags?.containsKey(article.outletId) == true,
                actions =
                    CardMenuActions(
                        onToggleSaved = { viewModel.toggleSaved(card) },
                        onHide = { viewModel.hide(card) },
                        onShowMore = { viewModel.showMore(article) },
                        onRemoveOutlet = { viewModel.removeOutlet(article) },
                        onMuteTopic = viewModel::muteTopic,
                        onMuteKeyword = { muteHeadline = article.title },
                        onFlagPaywall = { viewModel.flagPaywall(article) },
                    ),
                onDismiss = { menuCardId = null },
            )
        }
        muteHeadline?.let { headline ->
            KeywordMuteSheet(
                headline = headline,
                onMute = viewModel::muteKeyword,
                onDismiss = { muteHeadline = null },
            )
        }
    }
}

/**
 * Calls [loadMore] whenever the last visible item comes within [LOAD_MORE_THRESHOLD] of the end.
 * [served] is the unfiltered feed, so the trigger re-arms on every served page.
 */
@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    served: FeedState,
    loadMore: () -> Unit,
) {
    val content = served as? FeedState.Content
    if (content == null || content.endReached) return
    // Keyed on the unfiltered state: a page with no card in the selected topics leaves the
    // filtered feed unchanged, but still changes this, so the next page is asked for.
    LaunchedEffect(listState, content) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }
}

private fun LazyListScope.feedBody(
    state: FeedState,
    selectedNames: List<String>,
    viewModel: FeedViewModel,
    onOpenSettings: () -> Unit,
    onCard: (Article) -> Unit,
    onShare: (Article) -> Unit,
    onMore: (FeedCard) -> Unit,
    onGroup: (StoryGroup) -> Unit,
) {
    when (state) {
        is FeedState.Loading -> {
            item(key = "loading") {
                LoadingBoard(
                    label = "Loading today’s news…",
                    modifier = Modifier.padding(horizontal = NewzTokens.spaceL, vertical = NewzTokens.spaceM),
                )
            }
            items(2, key = { "skeleton-$it" }) {
                SkeletonCard(
                    Modifier.padding(
                        start = NewzTokens.spaceM,
                        end = NewzTokens.spaceM,
                        bottom = NewzTokens.spaceCardGap,
                    ),
                )
            }
        }

        is FeedState.Content -> {
            if (selectedNames.isNotEmpty() && state.cards.isNotEmpty()) {
                item(key = "filter") { FilterLine(selectedNames, viewModel::clearFilter) }
            }
            if (state.offline) {
                item(key = "offline") {
                    OfflineBanner(message = "Offline — showing articles loaded earlier", onRetry = viewModel::refresh)
                }
            }
            if (state.cards.isEmpty() && selectedNames.isNotEmpty()) {
                item(key = "filter-empty") { FilterEmptyPanel(selectedNames, viewModel::clearFilter) }
            }
            items(state.cards, key = { it.group.lead.id }) { card ->
                FeedArticleCard(
                    card = card,
                    viewModel = viewModel,
                    onCard = onCard,
                    onShare = onShare,
                    onMore = onMore,
                    onGroup = onGroup,
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = tween(NewzTokens.cardCollapse),
                            fadeOutSpec = tween(NewzTokens.cardCollapse),
                        ),
                )
            }
            if (state.endReached) {
                item(key = "end") {
                    BoardPanel(
                        tiles = "END",
                        title = "You're all caught up",
                        body = "That’s everything from the past 7 days. Pull down any time to check for something new.",
                    )
                }
            }
        }

        is FeedState.Empty -> {
            item(key = "empty") { EmptyPanel(state.reason, selectedNames, viewModel, onOpenSettings) }
        }
    }
}

@Composable
private fun FeedArticleCard(
    card: FeedCard,
    viewModel: FeedViewModel,
    onCard: (Article) -> Unit,
    onShare: (Article) -> Unit,
    onMore: (FeedCard) -> Unit,
    onGroup: (StoryGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lead = card.group.lead
    val info = card.info()
    ArticleCard(
        outlet = viewModel.outletName(lead),
        age = viewModel.shortAge(lead),
        spokenAge = viewModel.spokenAge(lead),
        headline = lead.title,
        imageUrl = lead.imageUrl,
        topic = viewModel.topicName(lead.primaryTopic),
        onOpen = { onCard(lead) },
        onShare = { onShare(lead) },
        onMore = { onMore(card) },
        onLongPress = { onMore(card) },
        groupCount = card.group.othersCount,
        onGroup = { onGroup(card.group) },
        modifier =
            modifier
                .padding(start = NewzTokens.spaceM, end = NewzTokens.spaceM, bottom = NewzTokens.spaceCardGap)
                .testTag(FeedSemantics.CARD_TAG)
                .semantics { feedCard = info },
    )
}

/** `--filter-active`: "Showing {topics}" in `type-row-sub` 600 with a Text "Clear filter" at the right. */
@Composable
private fun FilterLine(
    names: List<String>,
    onClear: () -> Unit,
) {
    val c = LocalNewzPalette.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = NewzTokens.spaceL, end = NewzTokens.spaceXs, bottom = NewzTokens.gapIconLabel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainText(
            text = "Showing ${joinTopics(names)}",
            style = NewzTokens.typeRowSub,
            color = c.ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Button(text = "Clear filter", onClick = onClear, variant = ButtonVariant.Text)
    }
}

@Composable
private fun EmptyPanel(
    reason: EmptyReason,
    selectedNames: List<String>,
    viewModel: FeedViewModel,
    onOpenSettings: () -> Unit,
) {
    when {
        reason == EmptyReason.NO_CACHE_OFFLINE -> {
            StatePanel(
                icon = NewzIcons.iconWifiOff,
                title = "You’re offline",
                body = OFFLINE_EMPTY_BODY,
                actionLabel = "Retry",
                onAction = viewModel::refresh,
                actionVariant = ButtonVariant.Primary,
            )
        }

        selectedNames.isNotEmpty() -> {
            FilterEmptyPanel(selectedNames, viewModel::clearFilter)
        }

        else -> {
            StatePanel(
                title = "Nothing to show — your filters hide everything",
                body = ALL_FILTERED_BODY,
                actionLabel = "Review in Settings",
                onAction = onOpenSettings,
                actionVariant = ButtonVariant.Primary,
            )
        }
    }
}

/** `--filter-empty`: no served card is in the selected topics. */
@Composable
private fun FilterEmptyPanel(
    selectedNames: List<String>,
    onClear: () -> Unit,
) {
    val topics = joinTopics(selectedNames, conjunction = "or")
    StatePanel(
        title = "No $topics articles right now",
        body = "New $topics stories from your sources will show up here as soon as they’re published.",
        actionLabel = "Clear filter",
        onAction = onClear,
        actionVariant = ButtonVariant.Primary,
    )
}
