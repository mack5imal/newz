package app.newz.ui.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.catalog.PaywallStatus
import app.newz.engine.FeedState
import app.newz.feed.AgeFormatter
import app.newz.feed.Article
import app.newz.filter.CUSTOM_TOPIC_PREFIX
import app.newz.filter.customTopicId
import app.newz.store.UserPrefs
import app.newz.ui.components.TagKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI

/** One chip in the feed's topic row. */
data class TopicChipItem(
    val id: String,
    val label: String,
)

/** The feed's one-line message bar (the Undo `Snackbar`); [key] restarts its timer. */
data class FeedMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
    val key: Any = Any(),
)

/**
 * The feed screen's state holder: the [FeedState] from [app.newz.engine.FeedRepository], the
 * topic chip selection, pull-to-refresh, and opening and sharing articles.
 *
 * The topic selection lasts while the process lives and resets on restart (SPEC Assumptions):
 * it is held here and seeded from [AppGraph.topicSelection], never written to preferences, and
 * it only hides cards: nothing is muted, hidden or reordered.
 */
class FeedViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val repository = graph.feedRepository

    private val selectionState = MutableStateFlow(graph.topicSelection)
    val selection: StateFlow<Set<String>> = selectionState.asStateFlow()

    val prefs: StateFlow<UserPrefs?> =
        graph.preferencesStore.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The feed as the repository serves it, before the topic selection. Paging keys on this: a
     * page that adds no card in the selected topics still changes it, so the next page is asked for.
     */
    val servedFeed: StateFlow<FeedState> =
        repository.feed.stateIn(viewModelScope, SharingStarted.Eagerly, FeedState.Loading(firstLoad = true))

    /**
     * The selected ids that are still chips (followed or custom topics that are not muted). A
     * selected topic that is later muted or unfollowed stops filtering, so the filter always
     * agrees with the "Showing …" line and Clear filter. Before preferences load, the whole
     * selection applies.
     */
    val activeSelection: StateFlow<Set<String>> =
        combine(selectionState, prefs) { selected, p ->
            if (p == null) selected else selected intersect chips(p).mapTo(HashSet()) { it.id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, graph.topicSelection)

    /**
     * The feed with the topic selection applied to the cards served so far. Filtering here, not
     * in the engine, keeps every card in its place: clearing the selection shows exactly the same
     * feed again (SPEC S14). An empty filtered page keeps loading pages until the end.
     */
    val feed: StateFlow<FeedState> =
        combine(servedFeed, activeSelection) { state, selected -> state.filtered(selected) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, FeedState.Loading(firstLoad = true))

    private val refreshingState = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = refreshingState.asStateFlow()

    private val messageState = MutableStateFlow<FeedMessage?>(null)
    val message: StateFlow<FeedMessage?> = messageState.asStateFlow()

    /** The chips: followed topics that are not muted, in follow order, then custom keyword topics. */
    fun chips(prefs: UserPrefs): List<TopicChipItem> {
        val catalogTopics =
            prefs.followedTopics
                .filter { it !in prefs.mutedTopics }
                .map { TopicChipItem(it, topicName(it)) }
        val custom =
            prefs.customTopics
                .map { customTopicId(it) }
                .filter { it !in prefs.mutedTopics }
                .map { TopicChipItem(it, topicName(it)) }
        return catalogTopics + custom
    }

    fun toggleTopic(id: String) {
        val current = selectionState.value
        setSelection(if (id in current) current - id else current + id)
    }

    fun clearFilter() = setSelection(emptySet())

    private fun setSelection(topics: Set<String>) {
        selectionState.value = topics
        graph.topicSelection = topics
    }

    /** Pull to refresh and Retry: reloads every followed feed, joining a load already running. */
    fun refresh() {
        if (refreshingState.value) return
        refreshingState.value = true
        viewModelScope.launch {
            try {
                repository.refresh().join()
            } finally {
                refreshingState.value = false
            }
        }
    }

    fun loadMore() {
        repository.loadMore()
    }

    /** Card or story-row tap: records the open (when history is on) and opens the browser tab. */
    fun open(
        context: Context,
        article: Article,
    ) {
        repository.recordOpen(article)
        graph.articleOpener.open(context, article.url)
    }

    fun share(
        context: Context,
        article: Article,
    ) {
        graph.shareLauncher.share(context, article.title, article.url)
    }

    fun showMessage(message: FeedMessage) {
        messageState.value = message
    }

    fun dismissMessage() {
        messageState.value = null
    }

    fun outletName(article: Article): String =
        graph.catalog.outlet(article.outletId)?.name
            ?: runCatching { URI(article.url).host?.removePrefix("www.") }.getOrNull()
            ?: article.outletId

    fun topicName(id: String?): String =
        when {
            id == null -> ""
            id.startsWith(CUSTOM_TOPIC_PREFIX) -> id.removePrefix(CUSTOM_TOPIC_PREFIX)
            else -> graph.catalog.topic(id)?.name ?: id
        }

    /** The outlet's catalog paywall tag for a story row (SPEC S12), or null. */
    fun paywallTag(article: Article): TagKind? =
        when (graph.catalog.outlet(article.outletId)?.paywall) {
            PaywallStatus.HARD -> TagKind.Paywall
            PaywallStatus.METERED -> TagKind.Metered
            else -> null
        }

    fun shortAge(article: Article): String = AgeFormatter.shortAge(article.date, graph.clock())

    fun spokenAge(article: Article): String = AgeFormatter.spokenAge(article.date, graph.clock())
}

private fun FeedState.filtered(selected: Set<String>): FeedState =
    if (this is FeedState.Content && selected.isNotEmpty()) {
        copy(
            cards =
                cards.filter { card ->
                    card.group.lead.topics
                        .any { it in selected }
                },
        )
    } else {
        this
    }

/** Joins names as the Copy does: "A", "A and B", "A, B and C" ([conjunction] "or" for the empty title). */
fun joinTopics(
    names: List<String>,
    conjunction: String = "and",
): String =
    when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " $conjunction " + names.last()
    }
