package app.newz.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.catalog.PaywallStatus
import app.newz.discover.SuggestionRanker
import app.newz.discover.Suggestions
import app.newz.discover.followCatalogTopic
import app.newz.discover.followOutlet
import app.newz.engine.ReadingAffinity
import app.newz.store.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State and actions for Suggested for you (design `suggestions`, SPEC S18). The ranking is worked
 * out on the phone from what the user follows and (only while reading history is on) what they
 * open; nothing is sent anywhere and no counts are shown. The list is ranked once per visit
 * ([load]) so a followed row stays in place and turns into a "Following" tag.
 */
class SuggestionsViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val catalog = graph.catalog

    val prefs: StateFlow<UserPrefs?> =
        graph.preferencesStore.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val suggestionsState = MutableStateFlow<Suggestions?>(null)

    /** The ranked outlets and topics; null until [load] has worked them out. */
    val suggestions: StateFlow<Suggestions?> = suggestionsState.asStateFlow()

    /** Ranks suggestions from the current preferences and reading history. */
    fun load() {
        viewModelScope.launch {
            val p = graph.preferencesStore.prefs.first()
            suggestionsState.value =
                withContext(Dispatchers.IO) {
                    val affinity =
                        if (p.readingHistoryOn) {
                            ReadingAffinity(
                                graph.articleStore.listHistory(),
                            )
                        } else {
                            ReadingAffinity.EMPTY
                        }
                    SuggestionRanker.suggest(catalog, p, affinity)
                }
        }
    }

    fun isFollowingOutlet(
        p: UserPrefs,
        outletId: String,
    ): Boolean = !p.followedOutlets[outletId].isNullOrEmpty() && outletId !in p.removedOutlets

    /** The outlet's paywall status from the catalog, or null for an unknown outlet. */
    fun outletPaywall(outletId: String): PaywallStatus? = catalog.outlet(outletId)?.paywall

    fun isFollowingTopic(
        p: UserPrefs,
        topicId: String,
    ): Boolean = topicId in p.followedTopics && topicId !in p.mutedTopics

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
}
