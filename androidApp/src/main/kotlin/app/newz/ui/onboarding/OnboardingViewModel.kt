package app.newz.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Topic
import app.newz.local.ZipResolver
import app.newz.store.UserPrefs
import app.newz.store.followTopic
import app.newz.store.setZip
import app.newz.ui.local.LocalArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Onboarding needs at least this many topics before Continue. */
const val MIN_TOPICS = 3

/**
 * The onboarding screen's state: the picked topics and, when Local is picked, the zip code the
 * user saved in the zip sheet. Nothing is written to preferences until [finish], so leaving the
 * app before Continue saves nothing (SPEC S4).
 */
class OnboardingViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    val topics: List<Topic> = graph.catalog.topics
    val zipResolver: ZipResolver = ZipResolver(graph.catalog)

    private val pickedState = MutableStateFlow<Set<String>>(emptySet())
    val picked: StateFlow<Set<String>> = pickedState.asStateFlow()

    private val localState = MutableStateFlow<LocalArea?>(null)

    /** The zip the user saved for Local, held until Continue. */
    val local: StateFlow<LocalArea?> = localState.asStateFlow()

    private var finishing = false

    /** Toggles [topicId]. Unpicking Local forgets its zip. */
    fun toggle(topicId: String) {
        val now = pickedState.value
        if (topicId in now) {
            pickedState.value = now - topicId
            if (topicId == Catalog.LOCAL_TOPIC) localState.value = null
        } else {
            pickedState.value = now + topicId
        }
    }

    fun saveZip(area: LocalArea) {
        localState.value = area
    }

    /** Continue is allowed with [MIN_TOPICS] picked, and a saved zip when Local is one of them. */
    fun canContinue(
        picked: Set<String>,
        local: LocalArea?,
    ): Boolean = picked.size >= MIN_TOPICS && (Catalog.LOCAL_TOPIC !in picked || local != null)

    /**
     * Saves the picked topics, follows every catalog section feed in them, saves the Local zip
     * when Local is picked, marks onboarding done, then calls [onDone].
     */
    fun finish(onDone: () -> Unit) {
        val picked = pickedState.value
        val local = localState.value
        if (finishing || !canContinue(picked, local)) return
        finishing = true
        viewModelScope.launch {
            graph.preferencesStore.update { it.onboard(graph.catalog, picked, local) }
            onDone()
        }
    }
}

/**
 * [this] after onboarding: [picked] followed in catalog order, every catalog outlet with a section
 * feed in those topics followed with all such sections (local feeds are followed through the zip's
 * metro instead), the Local zip when Local is picked, and [UserPrefs.onboarded] set.
 */
internal fun UserPrefs.onboard(
    catalog: Catalog,
    picked: Set<String>,
    local: LocalArea?,
): UserPrefs {
    var prefs = this
    for (topic in catalog.topics) {
        if (topic.id in picked) prefs = prefs.followTopic(topic.id)
    }
    val sections =
        catalog.feeds
            .filter { feed -> !feed.isLocal() && feed.topics.any { it in picked } }
            .groupBy({ it.outletId }, { it.id })
    val outlets =
        prefs.followedOutlets.toMutableMap().apply {
            for ((outlet, ids) in sections) this[outlet] = getOrElse(outlet) { emptySet() } + ids
        }
    prefs = prefs.copy(followedOutlets = outlets, onboarded = true)
    if (local != null && Catalog.LOCAL_TOPIC in picked) prefs = prefs.setZip(local.zip, local.metroId)
    return prefs
}

private fun CatalogFeed.isLocal(): Boolean = metroId != null && Catalog.LOCAL_TOPIC in topics
