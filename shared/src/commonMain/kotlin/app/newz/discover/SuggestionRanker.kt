package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.catalog.Outlet
import app.newz.engine.ReadingAffinity
import app.newz.store.UserPrefs

/**
 * One suggestion. [id] is an outlet or topic id. [topicName] is the outlet's best-matching topic
 * (shown as "{topic} · {reason}"; null for topic rows). [reason] is a line from the design's Copy.
 */
data class Ranked(
    val id: String,
    val name: String,
    val topicName: String?,
    val reason: String,
    val score: Double,
)

data class Suggestions(
    val outlets: List<Ranked>,
    val topics: List<Ranked>,
)

/**
 * Ranks outlets and topics to suggest (SPEC S18), worked out on the phone only.
 *
 * Outlets the user does not follow score `0.4 · popularity + 0.6 · topicAffinity`, plus a small
 * co-coverage bonus for sharing topics with outlets the user follows. `topicAffinity` is 1 when
 * the outlet covers a followed topic, averaged (while reading history is on and has entries)
 * with the outlet's best topic share of the user's opens, so with no history the order is
 * popularity within followed topics. Topics not followed score by how often catalog feeds carry
 * them together with followed topics, and by reading history. Removed outlets, muted topics and
 * Local are never suggested; nothing that scores zero is suggested.
 */
object SuggestionRanker {
    const val POPULARITY_WEIGHT = 0.4
    const val AFFINITY_WEIGHT = 0.6
    const val CO_COVERAGE_WEIGHT = 0.1
    const val SIMILAR_OUTLET_JACCARD = 0.5
    const val DEFAULT_OUTLET_LIMIT = 10
    const val DEFAULT_TOPIC_LIMIT = 6

    fun suggest(
        catalog: Catalog,
        prefs: UserPrefs,
        affinity: ReadingAffinity,
        outletLimit: Int = DEFAULT_OUTLET_LIMIT,
        topicLimit: Int = DEFAULT_TOPIC_LIMIT,
    ): Suggestions {
        val ctx = Context(catalog, prefs, affinity)
        return Suggestions(
            outlets = rankOutlets(ctx).take(outletLimit),
            topics = rankTopics(ctx).take(topicLimit),
        )
    }

    private class Context(
        val catalog: Catalog,
        val prefs: UserPrefs,
        affinity: ReadingAffinity,
    ) {
        val muted = prefs.mutedTopics.keys + Catalog.LOCAL_TOPIC

        /** Followed topics in the user's order, without muted ones. */
        val followedTopics = prefs.followedTopics.filter { it !in muted }

        /** Topic opens from history, only while history is on. */
        val topicOpens: Map<String, Int> = if (prefs.readingHistoryOn) affinity.topicCounts else emptyMap()
        val maxTopicOpens = topicOpens.filterKeys { it !in muted }.values.maxOrNull() ?: 0
        val historyOn = maxTopicOpens > 0

        val outletTopics: Map<String, Set<String>> =
            catalog.outlets.associate { outlet ->
                outlet.id to
                    catalog.feeds
                        .filter { it.outletId == outlet.id && it.metroId == null }
                        .flatMapTo(LinkedHashSet()) { it.topics }
                        .apply { removeAll(muted) }
            }

        val followedOutlets: List<Outlet> =
            prefs.followedOutlets
                .filter { (id, feeds) -> feeds.isNotEmpty() && id !in prefs.removedOutlets }
                .keys
                .mapNotNull(catalog::outlet)

        val followedOutletTopics: Set<String> = followedOutlets.flatMapTo(HashSet()) { outletTopics[it.id].orEmpty() }

        fun historyShare(topicId: String): Double =
            if (!historyOn) {
                0.0
            } else {
                (topicOpens[topicId] ?: 0).toDouble() /
                    maxTopicOpens
            }

        fun topicName(id: String): String = catalog.topic(id)?.name ?: id
    }

    private fun rankOutlets(ctx: Context): List<Ranked> {
        val prefs = ctx.prefs
        return ctx.catalog.outlets
            .asSequence()
            .filter { it.id !in prefs.removedOutlets && prefs.followedOutlets[it.id].isNullOrEmpty() }
            .mapNotNull { outlet -> scoreOutlet(ctx, outlet) }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.name })
            .toList()
    }

    private fun scoreOutlet(
        ctx: Context,
        outlet: Outlet,
    ): Ranked? {
        val topics = ctx.outletTopics[outlet.id].orEmpty()
        if (topics.isEmpty()) return null
        val followedOverlap = ctx.followedTopics.filter { it in topics }
        val follow = if (followedOverlap.isEmpty()) 0.0 else 1.0
        val bestHistoryTopic = topics.maxByOrNull { ctx.historyShare(it) }?.takeIf { ctx.historyShare(it) > 0.0 }
        val history = bestHistoryTopic?.let(ctx::historyShare) ?: 0.0
        val topicAffinity = if (ctx.historyOn) (follow + history) / 2 else follow
        val coCoverage = topics.count { it in ctx.followedOutletTopics }.toDouble() / topics.size
        if (topicAffinity == 0.0 && coCoverage == 0.0) return null
        val score =
            POPULARITY_WEIGHT * outlet.popularity + AFFINITY_WEIGHT * topicAffinity + CO_COVERAGE_WEIGHT * coCoverage
        val similar =
            ctx.followedOutlets
                .map { it to jaccard(topics, ctx.outletTopics[it.id].orEmpty()) }
                .filter { it.second >= SIMILAR_OUTLET_JACCARD }
                .maxByOrNull { it.second }
                ?.first
        val reason =
            when {
                bestHistoryTopic != null && history >= follow / 2 -> often(ctx.topicName(bestHistoryTopic))
                similar != null -> "Similar to ${similar.name}, which you follow"
                followedOverlap.isNotEmpty() -> because(followedOverlap.take(2).map(ctx::topicName))
                else -> COVERS_SAME_STORIES
            }
        val displayTopic = bestHistoryTopic ?: followedOverlap.firstOrNull() ?: topics.first()
        return Ranked(outlet.id, outlet.name, ctx.topicName(displayTopic), reason, score)
    }

    private fun rankTopics(ctx: Context): List<Ranked> {
        val followed = ctx.followedTopics.toSet()
        val coOccurrence = HashMap<String, MutableMap<String, Int>>()
        for (feed in ctx.catalog.feeds) {
            val withFollowed = feed.topics.filter { it in followed }
            if (withFollowed.isEmpty()) continue
            for (topic in feed.topics) {
                if (topic in followed) continue
                val counts = coOccurrence.getOrPut(topic) { HashMap() }
                for (f in withFollowed) counts[f] = (counts[f] ?: 0) + 1
            }
        }
        val maxCo = coOccurrence.values.maxOfOrNull { it.values.sum() } ?: 0
        return ctx.catalog.topics
            .asSequence()
            .filter { it.id !in followed && it.id !in ctx.muted && it.id !in ctx.prefs.followedTopics }
            .mapNotNull { topic ->
                val counts = coOccurrence[topic.id].orEmpty()
                val co = if (maxCo == 0) 0.0 else counts.values.sum().toDouble() / maxCo
                val history = ctx.historyShare(topic.id)
                if (co == 0.0 && history == 0.0) return@mapNotNull null
                val score = if (ctx.historyOn) (co + history) / 2 else co
                val reason =
                    if (history > 0.0 && history >= co) {
                        often(topic.name)
                    } else {
                        because(
                            counts.entries
                                .sortedWith(
                                    compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy {
                                        ctx.followedTopics
                                            .indexOf(
                                                it.key,
                                            )
                                    },
                                ).take(2)
                                .map { ctx.topicName(it.key) },
                        )
                    }
                Ranked(topic.id, topic.name, null, reason, score)
            }.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.name })
            .toList()
    }

    private fun jaccard(
        a: Set<String>,
        b: Set<String>,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.count { it in b }.toDouble() / (a + b).size
    }

    private fun because(topics: List<String>): String =
        when (topics.size) {
            0 -> COVERS_SAME_STORIES
            1 -> "Because you follow ${topics[0]}"
            else -> "Because you follow ${topics[0]} and ${topics[1]}"
        }

    private fun often(topic: String): String = "Often in the $topic articles you open"

    const val COVERS_SAME_STORIES = "Covers many of the same stories as your sources"
}
