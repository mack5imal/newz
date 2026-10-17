package app.newz.dedup

import app.newz.feed.Article
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours

/**
 * Cosine similarity (TF-IDF over [TextFeatures]) an article must reach against a group's centroid
 * to join it. Tuned on the UCI fixture in `StoryGrouperFixtureTest` (recall 0.769, false grouping 0 at
 * [TextFeatures.ENTITY_WEIGHT] 1.0).
 */
const val SAME_STORY_THRESHOLD = 0.275

/**
 * One story: [lead] is the card shown, [others] the other outlets' versions (newest first).
 * [othersCount] is the number of other outlets, shown as "+1 source" / "+{n} sources".
 */
data class StoryGroup(
    val lead: Article,
    val others: List<Article>,
) {
    val othersCount: Int get() = others.map { it.outletId }.distinct().count { it != lead.outletId }

    /** Every member, newest first. */
    fun members(): List<Article> = (others + lead).sortedWith(NEWEST_FIRST)
}

/** Newest first; equal dates by id so the order is stable. */
internal val NEWEST_FIRST: Comparator<Article> =
    compareByDescending<Article> { it.date }.thenBy { it.id }

/**
 * Groups articles about the same story (SPEC S15), on device.
 *
 * Each article becomes a TF-IDF vector of its headline plus summary (headline alone when it has no
 * summary). IDF comes from the given window: `ln((N + 1) / (df + 1)) + 1`. Articles are taken newest
 * first; each joins the existing group whose centroid it is most similar to, if that cosine is at
 * least [threshold], else it starts a group. A group only takes an article within [WINDOW] of all
 * its members and never two articles from one outlet. Comparing against the centroid rather than
 * any single member keeps A~B, B~C from chaining A and C together when they are unrelated.
 */
class StoryGrouper(
    private val threshold: Double = SAME_STORY_THRESHOLD,
) {
    fun group(
        articles: List<Article>,
        affinity: (outletId: String) -> Double,
    ): List<StoryGroup> {
        val ordered = articles.sortedWith(NEWEST_FIRST)
        val vectors = vectors(ordered)
        val groups = mutableListOf<Building>()
        ordered.forEachIndexed { i, article ->
            val v = vectors[i]
            val best =
                if (v.isEmpty()) {
                    null
                } else {
                    groups
                        .filter { it.accepts(article) }
                        .map { it to it.similarity(v) }
                        .filter { it.second >= threshold }
                        .maxByOrNull { it.second }
                        ?.first
                }
            if (best != null) best.add(article, v) else groups += Building(article, v)
        }
        return groups.map { it.toGroup(affinity) }
    }

    private class Building(
        first: Article,
        vector: Map<String, Double>,
    ) {
        val members = mutableListOf(first)
        val outlets = mutableSetOf(first.outletId)
        val centroid = vector.toMutableMap()

        fun accepts(article: Article): Boolean =
            article.outletId !in outlets && members.all { (it.date - article.date).absoluteValue <= WINDOW }

        fun similarity(v: Map<String, Double>): Double {
            val norm = sqrt(centroid.values.sumOf { it * it })
            if (norm == 0.0) return 0.0
            return v.entries.sumOf { (term, w) -> w * (centroid[term] ?: 0.0) } / norm
        }

        fun add(
            article: Article,
            v: Map<String, Double>,
        ) {
            members += article
            outlets += article.outletId
            for ((term, w) in v) centroid[term] = (centroid[term] ?: 0.0) + w
        }

        fun toGroup(affinity: (String) -> Double): StoryGroup {
            val lead = members.sortedWith(NEWEST_FIRST).maxBy { affinity(it.outletId) }
            return StoryGroup(lead, members.filter { it !== lead }.sortedWith(NEWEST_FIRST))
        }
    }

    companion object {
        /** Articles further apart than this are never compared. */
        val WINDOW = 48.hours

        /** Unit-length TF-IDF vectors for [articles], IDF taken from [articles] themselves. */
        internal fun vectors(articles: List<Article>): List<Map<String, Double>> {
            val tfs = articles.map { TextFeatures.terms(it.title, it.summary) }
            val df = mutableMapOf<String, Int>()
            for (tf in tfs) for (term in tf.keys) df[term] = (df[term] ?: 0) + 1
            val n = articles.size
            return tfs.map { tf ->
                val weighted = tf.mapValues { (term, f) -> f * (ln((n + 1.0) / (df.getValue(term) + 1.0)) + 1.0) }
                val norm = sqrt(weighted.values.sumOf { it * it })
                if (norm == 0.0) emptyMap() else weighted.mapValues { it.value / norm }
            }
        }

        /** Cosine of two unit vectors from [vectors]. */
        internal fun cosine(
            a: Map<String, Double>,
            b: Map<String, Double>,
        ): Double = a.entries.sumOf { (term, w) -> w * (b[term] ?: 0.0) }
    }
}
