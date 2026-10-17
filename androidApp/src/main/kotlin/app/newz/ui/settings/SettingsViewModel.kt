package app.newz.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.newz.AppGraph
import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.PaywallStatus
import app.newz.filter.CUSTOM_TOPIC_PREFIX
import app.newz.filter.customTopicId
import app.newz.filter.topicDisplayName
import app.newz.local.ZipResolver
import app.newz.store.CustomFeed
import app.newz.store.HiddenArticle
import app.newz.store.Level
import app.newz.store.TextSize
import app.newz.store.UserPrefs
import app.newz.store.clearZip
import app.newz.store.labelWithMeaning
import app.newz.store.removeOutlet
import app.newz.store.restoreOutlet
import app.newz.store.restoreTopic
import app.newz.store.setLevel
import app.newz.store.setZip
import app.newz.store.unflagPaywall
import app.newz.store.unfollowTopic
import app.newz.store.unmuteKeyword
import app.newz.ui.feed.FeedMessage
import app.newz.ui.local.LocalArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** One section feed of a followed outlet, as a `Checkbox` row. */
data class SectionItem(
    val feedId: String,
    val name: String,
    val checked: Boolean,
)

/**
 * A followed outlet in Settings › Sources. [sections] are its catalog section feeds (empty for a
 * feed added by URL); [failingSince] is set when one of its followed feeds is not loading.
 */
data class SourceItem(
    val outletId: String,
    val name: String,
    val sections: List<SectionItem>,
    val paywall: PaywallStatus,
    val failingSince: Instant?,
    val failingFeedIds: List<String>,
    val address: String? = null,
) {
    val followedCount: Int get() = sections.count { it.checked }
}

/** An outlet listed with a date: marked paywalled by the user, or removed. */
data class DatedOutlet(
    val outletId: String,
    val name: String,
    val at: Instant,
)

/** A followed or keyword topic row in Settings › Topics & keywords. */
data class TopicItem(
    val id: String,
    val name: String,
    val level: Level,
    val subtitle: String? = null,
)

/** The counts on the Settings home rows that come from the article store. */
data class StoreCounts(
    val hidden: Int = 0,
    val saved: Int = 0,
    val failingFeeds: Map<String, Instant> = emptyMap(),
    val customFeeds: List<CustomFeed> = emptyList(),
)

/**
 * State and actions for Settings (design `settings`), Sources (`settings-sources`) and Topics &
 * keywords (`settings-topics`): every feed choice is listed here and can be reversed (SPEC S11).
 * Preference changes are written at once; the feed recomposes from them. Remove and Unfollow
 * show a message with Undo; Restore, Unflag, Unmute and level changes act immediately.
 */
class SettingsViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val catalog: Catalog = graph.catalog
    val zipResolver: ZipResolver = ZipResolver(catalog)

    val prefs: StateFlow<UserPrefs?> =
        graph.preferencesStore.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val countsState = MutableStateFlow(StoreCounts())
    val counts: StateFlow<StoreCounts> = countsState.asStateFlow()

    private val hiddenState = MutableStateFlow<List<HiddenArticle>?>(null)

    /** Hidden articles, newest hidden first; null until [loadHidden] has read them. */
    val hidden: StateFlow<List<HiddenArticle>?> = hiddenState.asStateFlow()

    private val messageState = MutableStateFlow<FeedMessage?>(null)
    val message: StateFlow<FeedMessage?> = messageState.asStateFlow()

    private val sectionsByOutlet: Map<String, List<CatalogFeed>> by lazy {
        catalog.feeds.filter { it.metroId == null }.groupBy { it.outletId }
    }

    init {
        reload()
    }

    /** Re-reads the hidden, saved, custom-feed and feed-health tables (each screen calls it on entry). */
    fun reload() {
        viewModelScope.launch {
            val store = graph.articleStore
            countsState.value =
                withContext(Dispatchers.IO) {
                    StoreCounts(
                        hidden = store.listHidden().size,
                        saved = store.listSaved().size,
                        failingFeeds =
                            store
                                .feedStatuses()
                                .mapNotNull { s -> s.failingSince?.let { s.feedId to it } }
                                .toMap(),
                        customFeeds = store.listCustomFeeds(),
                    )
                }
        }
    }

    // Settings home

    fun setPaywallFilter(on: Boolean) = update { it.copy(paywallFilterOn = on) }

    fun setReadingHistory(on: Boolean) = update { it.copy(readingHistoryOn = on) }

    fun setTextSize(size: TextSize) = update { it.copy(textSize = size) }

    /** Clears every reading-history row at once; there is no Undo. */
    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.articleStore.clearHistory() }
            graph.feedRepository.invalidate()
            showMessage(FeedMessage("Reading history cleared"))
        }
    }

    // Hidden articles

    fun now(): Instant = graph.clock()

    /** Forgets hides older than 30 days, then reads the rest, newest first. */
    fun loadHidden() {
        viewModelScope.launch {
            val store = graph.articleStore
            hiddenState.value =
                withContext(Dispatchers.IO) {
                    store.pruneHidden(graph.clock())
                    store.listHidden().sortedByDescending { it.hiddenAt }
                }
        }
    }

    /**
     * Restore: the row leaves the list and the article is back in the feed, when it is still
     * inside the 7-day window (older articles are no longer cached, so they stay out).
     */
    fun restoreHidden(articleId: String) {
        hiddenState.value = hiddenState.value?.filterNot { it.articleId == articleId }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.articleStore.unhide(articleId) }
            graph.feedRepository.invalidate()
            reload()
        }
    }

    // Sources

    /**
     * The followed outlets: failing ones pinned first, then in follow order. Local feeds follow
     * through the zip (Topics), so an outlet with only local feeds is listed here only while one
     * of its feeds is not loading (SPEC S25).
     */
    fun sources(
        p: UserPrefs,
        counts: StoreCounts,
    ): List<SourceItem> {
        val fromCatalog =
            p.followedOutlets
                .filterKeys { it !in p.removedOutlets && it in sectionsByOutlet }
                .map { (outletId, enabled) ->
                    val sections =
                        sectionsByOutlet[outletId].orEmpty().map { SectionItem(it.id, it.section, it.id in enabled) }
                    val failing = enabled.filter { it in counts.failingFeeds }
                    SourceItem(
                        outletId = outletId,
                        name = outletName(outletId),
                        sections = sections,
                        paywall = catalog.outlet(outletId)?.paywall ?: PaywallStatus.NONE,
                        failingSince = failing.mapNotNull { counts.failingFeeds[it] }.minOrNull(),
                        failingFeedIds = failing,
                    )
                }
        val custom =
            counts.customFeeds
                .filter { it.id !in p.removedOutlets }
                .map { feed ->
                    SourceItem(
                        outletId = feed.id,
                        name = feed.title,
                        sections = emptyList(),
                        paywall = PaywallStatus.NONE,
                        failingSince = counts.failingFeeds[feed.id],
                        failingFeedIds = listOfNotNull(feed.id.takeIf { it in counts.failingFeeds }),
                        address = feed.siteUrl ?: feed.url,
                    )
                }
        val localFailing =
            p.localMetroId
                ?.takeIf { Catalog.LOCAL_TOPIC in p.followedTopics }
                ?.let(catalog::localFeeds)
                .orEmpty()
                .filter { it.outletId !in p.removedOutlets && it.id in counts.failingFeeds }
                .groupBy({ it.outletId }, { it.id })
        val listed =
            fromCatalog.map { item ->
                val extra = localFailing[item.outletId] ?: return@map item
                val failing = item.failingFeedIds + extra
                item.copy(
                    failingSince = failing.mapNotNull { counts.failingFeeds[it] }.minOrNull(),
                    failingFeedIds = failing,
                )
            }
        val listedIds = fromCatalog.map { it.outletId }.toSet()
        val local =
            localFailing
                .filterKeys { it !in listedIds }
                .map { (outletId, failing) ->
                    SourceItem(
                        outletId = outletId,
                        name = outletName(outletId),
                        sections = emptyList(),
                        paywall = catalog.outlet(outletId)?.paywall ?: PaywallStatus.NONE,
                        failingSince = failing.mapNotNull { counts.failingFeeds[it] }.minOrNull(),
                        failingFeedIds = failing,
                    )
                }
        return (listed + local + custom).sortedBy { it.failingSince == null }
    }

    fun paywallFlagged(p: UserPrefs): List<DatedOutlet> =
        p.paywallFlags.map { (id, at) -> DatedOutlet(id, outletName(id), at) }.sortedByDescending { it.at }

    fun removed(p: UserPrefs): List<DatedOutlet> =
        p.removedOutlets.map { (id, at) -> DatedOutlet(id, outletName(id), at) }.sortedByDescending { it.at }

    /** Remove: the outlet moves to Removed, with Undo. */
    fun removeOutlet(outletId: String) {
        changePrefs(
            "${outletName(outletId)} removed",
            apply = { it.removeOutlet(outletId, graph.clock()) },
            undo = { it.restoreOutlet(outletId) },
        )
    }

    /** Restore: the outlet's feeds are fetched again, since a removed outlet's cache may be pruned. */
    fun restoreOutlet(outletId: String) {
        viewModelScope.launch {
            graph.preferencesStore.update { it.restoreOutlet(outletId) }
            graph.feedRepository.refresh()
        }
    }

    fun unflagPaywall(outletId: String) = update { it.unflagPaywall(outletId) }

    /**
     * Turns one section feed of [outletId] on or off. The last checked section stays on: an
     * outlet is unfollowed with Remove. A section turned on is fetched by a refresh.
     */
    fun setSection(
        outletId: String,
        feedId: String,
        on: Boolean,
    ) {
        viewModelScope.launch {
            var added = false
            graph.preferencesStore.update { p ->
                val current = p.followedOutlets[outletId].orEmpty()
                val next = if (on) current + feedId else current - feedId
                if (next.isEmpty() || next == current) {
                    p
                } else {
                    added = on
                    p.copy(followedOutlets = p.followedOutlets + (outletId to next))
                }
            }
            if (added) graph.feedRepository.refresh()
        }
    }

    /** Retry on a failing source: re-fetches only its failing feeds, one at a time, never a full load. */
    fun retry(source: SourceItem) {
        viewModelScope.launch {
            source.failingFeedIds.forEach { graph.feedRepository.retryFeed(it).join() }
            reload()
        }
    }

    // Topics & keywords

    /** Followed catalog topics that are not muted, in follow order; Local carries its zip and metro. */
    fun followedTopics(p: UserPrefs): List<TopicItem> =
        p.followedTopics
            .filter { it !in p.mutedTopics }
            .map { id ->
                val subtitle =
                    if (id == Catalog.LOCAL_TOPIC) {
                        p.zip?.let { zip ->
                            val metro = p.localMetroId?.let(catalog::metro)?.name
                            if (metro != null) "$zip · $metro" else zip
                        }
                    } else {
                        null
                    }
                TopicItem(id, topicName(id), p.topicLevels[id] ?: Level.NORMAL, subtitle)
            }

    fun keywordTopics(p: UserPrefs): List<TopicItem> =
        p.customTopics
            .filter { customTopicId(it) !in p.mutedTopics }
            .map { word -> TopicItem(customTopicId(word), word, p.topicLevels[customTopicId(word)] ?: Level.NORMAL) }

    fun mutedTopics(p: UserPrefs): List<TopicItem> =
        p.mutedTopics.entries
            .sortedByDescending { it.value }
            .map { (id, _) -> TopicItem(id, topicName(id), p.topicLevels[id] ?: Level.NORMAL) }

    /**
     * Topics raised above Normal (from a card's Show more like this) that are neither followed,
     * keyword topics nor muted, so their level can still be seen and lowered (SPEC S10).
     */
    fun boostedTopics(p: UserPrefs): List<TopicItem> {
        val listed = p.followedTopics.toSet() + p.customTopics.map(::customTopicId) + p.mutedTopics.keys
        return p.topicLevels
            .filter { (id, level) -> level != Level.NORMAL && id !in listed }
            .map { (id, level) -> TopicItem(id, topicName(id), level) }
            .sortedBy { it.name.lowercase() }
    }

    fun setLevel(
        topicId: String,
        level: Level,
    ) = update { it.setLevel(topicId, level) }

    /**
     * The level control of a boosted topic that is not followed: Normal forgets the level, so the
     * row leaves the list, and says so with Undo.
     */
    fun setBoostedLevel(
        topicId: String,
        level: Level,
    ) {
        if (level != Level.NORMAL) return setLevel(topicId, level)
        var previous: Level? = null
        changePrefs(
            "${topicName(topicId)} is back to ${Level.NORMAL.labelWithMeaning}.",
            apply = { p ->
                previous = p.topicLevels[topicId]
                p.copy(topicLevels = p.topicLevels - topicId)
            },
            undo = { p -> previous?.let { p.setLevel(topicId, it) } ?: p },
        )
    }

    /**
     * Unfollows a catalog topic or removes a keyword topic, with Undo, which puts it back in its
     * place with its level.
     */
    fun unfollow(topicId: String) {
        var before: UserPrefs? = null
        changePrefs(
            "${topicName(topicId)} unfollowed",
            apply = { p ->
                before = p
                if (topicId.startsWith(CUSTOM_TOPIC_PREFIX)) {
                    val word = topicId.removePrefix(CUSTOM_TOPIC_PREFIX)
                    p.copy(customTopics = p.customTopics - word, topicLevels = p.topicLevels - topicId)
                } else {
                    p.unfollowTopic(topicId)
                }
            },
            undo = { p ->
                val old = before ?: return@changePrefs p
                p.copy(
                    followedTopics = restoreOrder(old.followedTopics, p.followedTopics, topicId),
                    customTopics =
                        restoreOrder(
                            old.customTopics,
                            p.customTopics,
                            topicId.removePrefix(CUSTOM_TOPIC_PREFIX),
                        ),
                    topicLevels =
                        old.topicLevels[topicId]?.let {
                            p.topicLevels + (topicId to it)
                        } ?: p.topicLevels,
                )
            },
        )
    }

    fun restoreTopic(topicId: String) = update { it.restoreTopic(topicId) }

    fun unmuteKeyword(text: String) = update { it.unmuteKeyword(text) }

    /** Saves a new Local zip and fetches the feeds of the metro it resolved to. */
    fun saveZip(area: LocalArea) {
        viewModelScope.launch {
            graph.preferencesStore.update { it.setZip(area.zip, area.metroId) }
            graph.feedRepository.refresh()
        }
    }

    /** Clear on the Local row: forgets the zip and unfollows Local. */
    fun clearLocal() = update { it.clearZip().unfollowTopic(Catalog.LOCAL_TOPIC) }

    // Shared

    fun outletName(outletId: String): String =
        catalog.outlet(outletId)?.name
            ?: countsState.value.customFeeds
                .find { it.id == outletId }
                ?.title
            ?: outletId

    fun topicName(id: String): String = catalog.topicDisplayName(id)

    fun dismissMessage() {
        messageState.value = null
    }

    private fun showMessage(message: FeedMessage) {
        messageState.value = message
    }

    private fun update(transform: (UserPrefs) -> UserPrefs) {
        viewModelScope.launch { graph.preferencesStore.update(transform) }
    }

    /** Applies [apply] and shows [text] with Undo, which applies [undo] only when [apply] changed something. */
    private fun changePrefs(
        text: String,
        apply: (UserPrefs) -> UserPrefs,
        undo: (UserPrefs) -> UserPrefs,
    ) {
        viewModelScope.launch {
            var changed = false
            graph.preferencesStore.update { p -> apply(p).also { changed = it != p } }
            showMessage(
                FeedMessage(
                    text = text,
                    actionLabel = "Undo",
                    onAction = { if (changed) viewModelScope.launch { graph.preferencesStore.update(undo) } },
                ),
            )
        }
    }

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        /** "Oct 12": the short date on "Not loading since" and "Removed" lines, in the phone's time zone. */
        fun shortDate(at: Instant): String = DATE.format(at.toJavaInstant().atZone(ZoneId.systemDefault()))

        private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)

        /**
         * `{when}` on a hidden row: "today", "yesterday", the weekday ("Tue") within the last
         * week, else the date ("Oct 10"), in the phone's time zone.
         */
        fun hiddenWhen(
            at: Instant,
            now: Instant,
        ): String {
            val zone = ZoneId.systemDefault()
            val day = at.toJavaInstant().atZone(zone).toLocalDate()
            val today = now.toJavaInstant().atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(day, today)
            return when {
                days <= 0L -> "today"
                days == 1L -> "yesterday"
                days < 7L -> WEEKDAY.format(day)
                else -> DATE.format(day)
            }
        }

        /** [old] order with [item] put back where it was, keeping anything added since. */
        internal fun restoreOrder(
            old: List<String>,
            now: List<String>,
            item: String,
        ): List<String> {
            if (item !in old || item in now) return now
            val index = old.indexOf(item).coerceAtMost(now.size)
            return now.toMutableList().apply { add(index, item) }
        }
    }
}
