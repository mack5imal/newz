package app.newz.ui.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.catalog.Catalog
import app.newz.engine.FeedCard
import app.newz.engine.FeedState
import app.newz.feed.AgeFormatter
import app.newz.feed.Article
import app.newz.filter.customTopicId
import app.newz.filter.topicDisplayName
import app.newz.store.Level
import app.newz.store.UserPrefs
import app.newz.store.flagPaywall
import app.newz.store.labelWithMeaning
import app.newz.store.levelOf
import app.newz.store.lowerLevel
import app.newz.store.muteKeyword
import app.newz.store.muteTopic
import app.newz.store.raiseLevel
import app.newz.store.removeOutlet
import app.newz.store.restoreOutlet
import app.newz.store.restoreTopic
import app.newz.store.setLevel
import app.newz.store.unflagPaywall
import app.newz.store.unmuteKeyword
import app.newz.ui.components.TagKind
import app.newz.ui.components.tagKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/** One chip in the feed's topic row. */
data class TopicChipItem(
    val id: String,
    val label: String,
)

/** The feed's message bar (the Undo `Snackbar`); [key] restarts its timer. */
data class FeedMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
    val key: Any = Any(),
)

/**
 * The feed screen's state holder: the [FeedState] from [app.newz.engine.FeedRepository], the
 * topic chip selection, pull-to-refresh, opening and sharing articles, and the card menu's and
 * keyword sheet's actions. Each action is written to preferences or the article store at once (so
 * it survives refresh and restart) and shows its message with an Undo that reverts exactly what
 * it changed: nothing, when the action found the change already made.
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

    // Card menu (design `card-menu`) and keyword sheet (design `keyword-mute`) actions.

    /** Save for later, or Unsave when [card] is already saved. */
    fun toggleSaved(card: FeedCard) {
        val id = card.group.lead.id
        val store = graph.articleStore
        viewModelScope.launch {
            if (card.saved) {
                // Undo puts back the saved row as it was: its snapshot and its original save time.
                val saved =
                    withContext(Dispatchers.IO) {
                        store.listSaved().find { it.article.id == id }?.also { store.unsave(id) }
                    }
                graph.feedRepository.invalidate()
                showUndo("Removed from Saved.") {
                    if (saved != null) withContext(Dispatchers.IO) { store.save(saved.article, saved.savedAt) }
                }
            } else {
                val stored = withContext(Dispatchers.IO) { store.article(id) } ?: return@launch
                val wasSaved =
                    withContext(Dispatchers.IO) { store.isSaved(id).also { store.save(stored, graph.clock()) } }
                graph.feedRepository.invalidate()
                showUndo("Saved for later. Find it in Saved.") {
                    if (!wasSaved) withContext(Dispatchers.IO) { store.unsave(id) }
                }
            }
        }
    }

    /** Hides every article of [card]'s story group, so the card leaves the feed. */
    fun hide(card: FeedCard) {
        val store = graph.articleStore
        val ids = card.group.members().map { it.id }
        viewModelScope.launch {
            val added =
                withContext(Dispatchers.IO) {
                    val hidden = store.listHidden().mapTo(HashSet()) { it.articleId }
                    val now = graph.clock()
                    ids
                        .filter { it !in hidden }
                        .mapNotNull(store::article)
                        .onEach { store.hide(it, now) }
                        .map { it.id }
                }
            graph.feedRepository.invalidate()
            showUndo("Article hidden. Bring it back in Settings › Hidden articles.") {
                withContext(Dispatchers.IO) { added.forEach(store::unhide) }
            }
        }
    }

    /** Raises [article]'s primary topic one level (Normal, More, Much more). */
    fun showMore(article: Article) = changeLevel(article) { raiseLevel(it) }

    /** Lowers [article]'s primary topic one level (Much more, More, Normal). */
    fun showLess(article: Article) = changeLevel(article) { lowerLevel(it) }

    /**
     * Applies [change] to [article]'s primary topic and names the new level with its meaning, e.g.
     * "Science now at More (about 2× as often). Change it in Settings." (short enough for two lines
     * of the message bar; Settings › Topics & keywords holds the control). Undo puts back the stored level.
     */
    private fun changeLevel(
        article: Article,
        change: UserPrefs.(topicId: String) -> UserPrefs,
    ) {
        val topic = article.primaryTopic ?: return
        var previous: Level? = null
        changePrefs(
            text = { p -> "${topicName(topic)} now at ${p.levelOf(topic).labelWithMeaning}. Change it in Settings." },
            apply = { p ->
                previous = p.topicLevels[topic]
                p.change(topic)
            },
            undo = { p -> previous?.let { p.setLevel(topic, it) } ?: p.copy(topicLevels = p.topicLevels - topic) },
        )
    }

    fun removeOutlet(article: Article) {
        val outlet = article.outletId
        changePrefs(
            "${outletName(article)} won’t appear. Bring it back in Settings › Sources.",
            apply = { it.removeOutlet(outlet, graph.clock()) },
            undo = { it.restoreOutlet(outlet) },
        )
    }

    fun muteTopic(topicId: String) {
        changePrefs(
            "${topicName(topicId)} won’t appear. Bring it back in Settings › Topics & keywords.",
            apply = { it.muteTopic(topicId, graph.clock()) },
            undo = { it.restoreTopic(topicId) },
        )
    }

    fun flagPaywall(article: Article) {
        val outlet = article.outletId
        changePrefs(
            "${outletName(article)} marked as paywalled. Change it in Settings › Sources.",
            apply = { it.flagPaywall(outlet, graph.clock()) },
            undo = { it.unflagPaywall(outlet) },
        )
    }

    /** Mutes [keyword] (already validated by the sheet); muting it again keeps one entry. */
    fun muteKeyword(keyword: String) {
        val text = keyword.trim()
        changePrefs(
            "Muted “$text”. Unmute it in Settings › Topics & keywords.",
            apply = { it.muteKeyword(text, graph.clock()) },
            undo = { it.unmuteKeyword(text) },
        )
    }

    /** [topicId]'s level: the menu offers Show less like this above Normal and no Show more at Much more. */
    fun levelOf(topicId: String): Level = prefs.value?.levelOf(topicId) ?: Level.NORMAL

    /**
     * Applies [apply] to the stored preferences and shows [text] with Undo. Undo applies [undo]
     * only when [apply] changed something.
     */
    private fun changePrefs(
        text: String,
        apply: (UserPrefs) -> UserPrefs,
        undo: (UserPrefs) -> UserPrefs,
    ) = changePrefs({ text }, apply, undo)

    /** As above, with the message worded from the preferences after the change. */
    private fun changePrefs(
        text: (UserPrefs) -> String,
        apply: (UserPrefs) -> UserPrefs,
        undo: (UserPrefs) -> UserPrefs,
    ) {
        viewModelScope.launch {
            var changed = false
            var after: UserPrefs? = null
            graph.preferencesStore.update { p ->
                apply(p).also {
                    after = it
                    changed = it != p
                }
            }
            showUndo(text(checkNotNull(after))) { if (changed) graph.preferencesStore.update(undo) }
        }
    }

    /** Shows [text] with "Undo"; the undo runs, then the feed recomposes from the cache. */
    private fun showUndo(
        text: String,
        undo: suspend () -> Unit,
    ) {
        showMessage(
            FeedMessage(
                text = text,
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        undo()
                        graph.feedRepository.invalidate()
                    }
                },
            ),
        )
    }

    fun showMessage(message: FeedMessage) {
        messageState.value = message
    }

    fun dismissMessage() {
        messageState.value = null
    }

    fun outletName(article: Article): String = graph.catalog.outletDisplayName(article.outletId, article.url)

    fun topicName(id: String?): String = id?.let { graph.catalog.topicDisplayName(it) } ?: ""

    /** The outlet's catalog paywall tag for a story row (SPEC S12), or null. */
    fun paywallTag(article: Article): TagKind? =
        graph.catalog
            .outlet(article.outletId)
            ?.paywall
            .tagKind()

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

/** The name an article's outlet is shown under: the catalog name, else the article URL's host, else the id. */
fun Catalog.outletDisplayName(
    outletId: String,
    url: String,
): String =
    outlet(outletId)?.name
        ?: runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()
        ?: outletId

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
