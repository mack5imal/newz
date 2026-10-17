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
 *
 * Near-linear (B17): each group keeps its centroid's length up to date instead of recomputing it
 * per comparison; groups whose newest member is more than [WINDOW] newer than the current article
 * are retired (later articles are older still, so they never join them); and an index from term
 * to groups scores only groups sharing at least one term with the article, summing just the shared
 * terms. A group sharing none has cosine 0, below any positive [threshold], so skipping it changes
 * nothing; the skipped terms add exactly 0.0, so the cosines are bit for bit the same. Ties still
 * go to the oldest group, as when every group was scanned in creation order.
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
        val byTerm = HashMap<String, MutableList<Building>>()
        // Groups are created newest first, so the retired ones are always a prefix of [groups].
        var live = 0
        ordered.forEachIndexed { i, article ->
            val v = vectors[i]
            while (live < groups.size && groups[live].expiredFor(article)) live++
            val best =
                when {
                    v.isEmpty() -> null

                    threshold > 0.0 -> bestSharing(article, v, live, byTerm, stamp = i)

                    // A group sharing no term (cosine 0) can still pass: score every live group.
                    else -> bestOf(article, groups.subList(live, groups.size).map { it to it.similarity(v) })
                }
            val target = best ?: Building(groups.size, article).also { groups += it }
            for (term in target.add(article, v)) byTerm.getOrPut(term) { mutableListOf() } += target
        }
        return groups.map { it.toGroup(affinity) }
    }

    /**
     * The best live group ([live] onwards) for [article] among those sharing a term with [v]. Each
     * one's dot product sums only the shared terms, in [v]'s order, as [Building.similarity] would.
     */
    private fun bestSharing(
        article: Article,
        v: Map<String, Double>,
        live: Int,
        byTerm: HashMap<String, MutableList<Building>>,
        stamp: Int,
    ): Building? {
        val found = mutableListOf<Building>()
        for ((term, w) in v) {
            val list = byTerm[term] ?: continue
            var kept = 0
            for (group in list) {
                if (group.index < live) continue
                list[kept++] = group
                if (group.seenAt != stamp) {
                    group.seenAt = stamp
                    group.dot = 0.0
                    found += group
                }
                group.dot += w * group.centroid.getValue(term)
            }
            if (kept < list.size) list.subList(kept, list.size).clear()
        }
        return bestOf(article, found.map { it to if (it.norm == 0.0) 0.0 else it.dot / it.norm })
    }

    /** The accepting group with the highest cosine of at least [threshold]; ties to the oldest group. */
    private fun bestOf(
        article: Article,
        scored: List<Pair<Building, Double>>,
    ): Building? {
        var best: Building? = null
        var bestScore = 0.0
        for ((group, score) in scored) {
            if (score < threshold || !group.accepts(article)) continue
            if (best == null || score > bestScore || (score == bestScore && group.index < best.index)) {
                best = group
                bestScore = score
            }
        }
        return best
    }

    private class Building(
        val index: Int,
        first: Article,
    ) {
        val newest = first
        val members = mutableListOf<Article>()
        val outlets = mutableSetOf<String>()
        val centroid = mutableMapOf<String, Double>()

        /** The centroid's length, recomputed by [add] (once per member, not once per comparison). */
        var norm = 0.0

        /** The article index that last listed this group as a candidate, and its dot product then. */
        var seenAt = -1
        var dot = 0.0

        /**
         * Articles arrive newest first, so [newest] is the newest member and every member lies
         * between [article] and it: "within [WINDOW] of all members" is "within [WINDOW] of [newest]".
         */
        fun accepts(article: Article): Boolean = article.outletId !in outlets && !expiredFor(article)

        /** True once [article] (and so every later, older one) is beyond [WINDOW] of [newest]. */
        fun expiredFor(article: Article): Boolean = (newest.date - article.date).absoluteValue > WINDOW

        fun similarity(v: Map<String, Double>): Double {
            if (norm == 0.0) return 0.0
            return v.entries.sumOf { (term, w) -> w * (centroid[term] ?: 0.0) } / norm
        }

        /** Adds [article] with vector [v]; returns the terms new to the centroid. */
        fun add(
            article: Article,
            v: Map<String, Double>,
        ): List<String> {
            members += article
            outlets += article.outletId
            val added = mutableListOf<String>()
            for ((term, w) in v) {
                val old = centroid[term]
                if (old == null) added += term
                centroid[term] = (old ?: 0.0) + w
            }
            norm = sqrt(centroid.values.sumOf { it * it })
            return added
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
