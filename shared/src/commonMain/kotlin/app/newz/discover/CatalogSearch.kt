package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.catalog.Outlet
import app.newz.catalog.Topic
import app.newz.filter.KeywordMatcher
import app.newz.store.UserPrefs

/** The longest custom topic, in characters after trimming. */
const val MAX_CUSTOM_TOPIC_LENGTH = 60

/**
 * One outlet in search results. [feedCount] counts its section feeds (not local ones) and
 * [primaryTopic] is the topic most of them carry. [removed] outlets are still found so they can
 * be restored.
 */
data class OutletResult(
    val outlet: Outlet,
    val feedCount: Int,
    val primaryTopic: Topic?,
    val following: Boolean,
    val removed: Boolean,
)

/** One topic in search results. [muted] topics are still found so they can be restored. */
data class TopicResult(
    val topic: Topic,
    val following: Boolean,
    val muted: Boolean,
)

/** [customTopicOffer] is the word to offer as "Add '{word}' as a custom topic", if any. */
data class SearchResults(
    val outlets: List<OutletResult>,
    val topics: List<TopicResult>,
    val customTopicOffer: String?,
) {
    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), null)
    }
}

/**
 * Searches the bundled catalog on the phone (SPEC S17). Matching ignores case and accents: a
 * prefix (of the whole field or of a word in it) beats a substring. Outlets match on name and
 * domain and are ranked by match quality, then popularity; topics match on name and description.
 * Nothing is recorded: search terms never leave this function.
 */
object CatalogSearch {
    fun search(
        query: String,
        catalog: Catalog,
        prefs: UserPrefs,
    ): SearchResults {
        val text = query.trim()
        val needle = fold(text)
        if (needle.isEmpty()) return SearchResults.EMPTY
        val outlets =
            catalog.outlets
                .mapNotNull { outlet ->
                    val quality =
                        listOfNotNull(
                            matchQuality(outlet.name, needle),
                            matchQuality(outlet.domain, needle)?.plus(1),
                        ).minOrNull() ?: return@mapNotNull null
                    quality to outlet
                }.sortedWith(compareBy<Pair<Int, Outlet>> { it.first }.thenByDescending { it.second.popularity })
                .map { (_, outlet) -> outletResult(outlet, catalog, prefs) }
        val topics =
            catalog.topics
                .mapIndexedNotNull { index, topic ->
                    val quality =
                        matchQuality(topic.name, needle)
                            ?: matchQuality(topic.description, needle)?.plus(DESCRIPTION_PENALTY)
                            ?: return@mapIndexedNotNull null
                    Triple(quality, index, topic)
                }.sortedWith(compareBy({ it.first }, { it.second }))
                .map { (_, _, topic) ->
                    TopicResult(
                        topic,
                        following = topic.id in prefs.followedTopics,
                        muted =
                            topic.id in prefs.mutedTopics,
                    )
                }
        val isTopicName = catalog.topics.any { fold(it.name) == needle }
        val offer = text.takeIf { it.length <= MAX_CUSTOM_TOPIC_LENGTH && !isTopicName }
        return SearchResults(outlets, topics, offer)
    }

    internal fun outletResult(
        outlet: Outlet,
        catalog: Catalog,
        prefs: UserPrefs,
    ): OutletResult {
        val feeds = sectionFeeds(catalog, outlet.id)
        val primary =
            feeds
                .flatMap { it.topics }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?.let(catalog::topic)
        return OutletResult(
            outlet = outlet,
            feedCount = feeds.size,
            primaryTopic = primary,
            following = !prefs.followedOutlets[outlet.id].isNullOrEmpty(),
            removed = outlet.id in prefs.removedOutlets,
        )
    }

    /**
     * 0 = the field starts with [needle], 1 = a word in it does, 2 = it contains [needle];
     * null when it does not match.
     */
    private fun matchQuality(
        field: String,
        needle: String,
    ): Int? {
        val folded = fold(field)
        return when {
            folded.startsWith(needle) -> 0
            WORD_SPLIT.split(folded).any { it.startsWith(needle) } -> 1
            folded.contains(needle) -> 2
            else -> null
        }
    }

    private fun fold(text: String): String = KeywordMatcher.normalize(text.trim())

    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /** A description match always ranks below any name match. */
    private const val DESCRIPTION_PENALTY = 3
}

/** An outlet's own section feeds: its local (metro) feeds are left out unless it has nothing else. */
internal fun sectionFeeds(
    catalog: Catalog,
    outletId: String,
) = catalog.feeds.filter { it.outletId == outletId }.let { all -> all.filter { it.metroId == null }.ifEmpty { all } }
