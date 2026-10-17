package app.newz.ui.saved

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.engine.EmptyReason
import app.newz.engine.FeedState
import app.newz.feed.AgeFormatter
import app.newz.filter.CUSTOM_TOPIC_PREFIX
import app.newz.store.SavedArticle
import app.newz.store.StoredArticle
import app.newz.ui.feed.FeedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * The Saved list (design `saved`, SPEC S21): every saved article from the store, newest saved
 * first. Saved rows are full copies, so the list works offline and outlives the 7-day feed window.
 * Unsave shows "Removed from saved" with Undo, which puts the row back with its save time.
 */
class SavedViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val savedState = MutableStateFlow<List<SavedArticle>?>(null)

    /** Null until [load] has read the store. */
    val saved: StateFlow<List<SavedArticle>?> = savedState.asStateFlow()

    /** True while the phone is offline or the last feed load reached no source. */
    val offline: StateFlow<Boolean> =
        graph.feedRepository.feed
            .map { state ->
                !graph.config.isOnline() ||
                    (state is FeedState.Content && state.offline) ||
                    (state is FeedState.Empty && state.reason == EmptyReason.NO_CACHE_OFFLINE)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, !graph.config.isOnline())

    private val messageState = MutableStateFlow<FeedMessage?>(null)
    val message: StateFlow<FeedMessage?> = messageState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            savedState.value = withContext(Dispatchers.IO) { readSaved() }
        }
    }

    /** Retry on the offline banner: reloads the feed, which updates [offline]. */
    fun retry() {
        graph.feedRepository.refresh()
    }

    /** Opens the article in the browser and records the open while reading history is on. */
    fun open(
        context: Context,
        article: StoredArticle,
    ) {
        viewModelScope.launch {
            if (graph.preferencesStore.prefs
                    .first()
                    .readingHistoryOn
            ) {
                withContext(Dispatchers.IO) {
                    graph.articleStore.recordOpen(article.id, article.outletId, article.topics, graph.clock())
                }
            }
        }
        graph.articleOpener.open(context, article.url)
    }

    fun share(
        context: Context,
        article: StoredArticle,
    ) {
        graph.shareLauncher.share(context, article.title, article.url)
    }

    fun unsave(saved: SavedArticle) {
        val store = graph.articleStore
        val id = saved.article.id
        savedState.value = savedState.value?.filterNot { it.article.id == id }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.unsave(id) }
            graph.feedRepository.invalidate()
            messageState.value =
                FeedMessage(
                    text = "Removed from saved",
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch {
                            savedState.value =
                                withContext(Dispatchers.IO) {
                                    store.save(saved.article, saved.savedAt)
                                    readSaved()
                                }
                            graph.feedRepository.invalidate()
                        }
                    },
                )
        }
    }

    fun dismissMessage() {
        messageState.value = null
    }

    fun outletName(article: StoredArticle): String =
        graph.catalog.outlet(article.outletId)?.name
            ?: runCatching { URI(article.url).host?.removePrefix("www.") }.getOrNull()
            ?: article.outletId

    /** The topic shown on a card without an image: the article's first topic. */
    fun topicName(article: StoredArticle): String {
        val id = article.topics.firstOrNull() ?: return ""
        return if (id.startsWith(CUSTOM_TOPIC_PREFIX)) {
            id.removePrefix(CUSTOM_TOPIC_PREFIX)
        } else {
            graph.catalog.topic(id)?.name ?: id
        }
    }

    fun shortAge(article: StoredArticle): String = AgeFormatter.shortAge(article.publishedAt, graph.clock())

    fun spokenAge(article: StoredArticle): String = AgeFormatter.spokenAge(article.publishedAt, graph.clock())

    private fun readSaved(): List<SavedArticle> = graph.articleStore.listSaved().sortedByDescending { it.savedAt }
}
