package app.newz.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.catalog.Catalog
import app.newz.discover.AddressInput
import app.newz.discover.CatalogSearch
import app.newz.discover.DiscoveryResult
import app.newz.discover.Input
import app.newz.discover.SearchResults
import app.newz.discover.addCustomTopic
import app.newz.discover.followCatalogTopic
import app.newz.discover.followCustomFeed
import app.newz.discover.followOutlet
import app.newz.discover.normalizeCustomTopic
import app.newz.store.UserPrefs
import app.newz.store.restoreOutlet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the search screen shows below the field for the current (debounced) text. */
sealed interface SearchContent {
    /** The field is empty: the hint panel. */
    data object Hint : SearchContent

    /** A word: catalog results, searched on the phone. */
    data class Results(
        val query: String,
    ) : SearchContent

    /** A web address being checked ([result] null while the check runs). Only [host] is contacted. */
    data class Address(
        val host: String,
        val result: DiscoveryResult?,
    ) : SearchContent

    /** Text with a scheme or a dot that is not a usable address. */
    data object NotAnAddress : SearchContent
}

/**
 * State and actions for search (design `search`, SPEC S17). The typed text lives only in this
 * view model's memory and is never written anywhere: catalog matching runs on the phone, and an
 * address check contacts only that site through the guarded client. Typing is debounced by
 * [DEBOUNCE_MS]. Follow, Add and the custom-topic offer write preferences at once.
 */
class SearchViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    val catalog: Catalog = graph.catalog

    val prefs: StateFlow<UserPrefs?> =
        graph.preferencesStore.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val textState = MutableStateFlow("")

    /** The field's text, exactly as typed. */
    val text: StateFlow<String> = textState.asStateFlow()

    private val contentState = MutableStateFlow<SearchContent>(SearchContent.Hint)
    val content: StateFlow<SearchContent> = contentState.asStateFlow()

    private var pending: Job? = null

    fun onTextChange(value: String) {
        textState.value = value
        pending?.cancel()
        if (value.isBlank()) {
            contentState.value = SearchContent.Hint
            return
        }
        pending =
            viewModelScope.launch {
                delay(DEBOUNCE_MS)
                when (val input = AddressInput.classify(value)) {
                    is Input.Query -> contentState.value = SearchContent.Results(input.text)
                    Input.NotAnAddress -> contentState.value = SearchContent.NotAnAddress
                    is Input.WebAddress -> check(value, input.host)
                }
            }
    }

    private suspend fun check(
        value: String,
        host: String,
    ) {
        contentState.value = SearchContent.Address(host, null)
        val p = prefs.value ?: return
        val result =
            withContext(Dispatchers.IO) {
                graph.feedDiscovery.discover(value, p, graph.articleStore.listCustomFeeds())
            }
        contentState.value = SearchContent.Address(host, result)
    }

    /** Catalog results for [query] against [p]; recomputed as preferences change (Follow → Following). */
    fun results(
        query: String,
        p: UserPrefs,
    ): SearchResults {
        val found = CatalogSearch.search(query, catalog, p)
        val offer =
            found.customTopicOffer
                ?.let(::normalizeCustomTopic)
                ?.takeIf { word -> p.customTopics.none { it.equals(word, ignoreCase = true) } }
        return found.copy(customTopicOffer = offer)
    }

    fun followOutlet(outletId: String) {
        viewModelScope.launch {
            graph.preferencesStore.update { it.followOutlet(outletId, catalog) }
            graph.feedRepository.refresh()
        }
    }

    fun followTopic(topicId: String) {
        viewModelScope.launch {
            graph.preferencesStore.update { it.followCatalogTopic(topicId, catalog) }
            graph.feedRepository.refresh()
        }
    }

    /** Adds [word] as a custom topic; it shows in the feed's topic chip row. */
    fun addCustomTopic(word: String) {
        if (normalizeCustomTopic(word) == null) return
        viewModelScope.launch { graph.preferencesStore.update { it.addCustomTopic(word) } }
    }

    /**
     * Follows the feed a check found: a bundled catalog feed is followed as part of its outlet,
     * any other is added as a custom feed. The panel then shows "Already following".
     */
    fun addFeed(found: DiscoveryResult.Found) {
        val current = contentState.value as? SearchContent.Address ?: return
        viewModelScope.launch {
            val catalogFeed = found.catalogFeedId?.let(catalog::feed)
            val feedId =
                if (catalogFeed != null) {
                    val outletId = catalogFeed.outletId
                    graph.preferencesStore.update { p ->
                        val restored = p.restoreOutlet(outletId)
                        val enabled = restored.followedOutlets[outletId].orEmpty() + catalogFeed.id
                        restored.copy(followedOutlets = restored.followedOutlets + (outletId to enabled))
                    }
                    catalogFeed.id
                } else {
                    val now = graph.clock()
                    val feed = withContext(Dispatchers.IO) { graph.articleStore.followCustomFeed(found, now) }
                    graph.preferencesStore.update { it.followCustomFeed(feed) }
                    feed.id
                }
            // Only if the field still shows this check (the user may have typed on).
            if (contentState.value == current) {
                contentState.value = current.copy(result = DiscoveryResult.AlreadyFollowing(found, feedId))
            }
            graph.feedRepository.refresh()
        }
    }

    companion object {
        const val DEBOUNCE_MS = 300L
    }
}
