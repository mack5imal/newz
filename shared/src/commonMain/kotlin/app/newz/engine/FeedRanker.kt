package app.newz.engine

import app.newz.dedup.StoryGroup
import app.newz.store.Level
import app.newz.store.UserPrefs
import app.newz.store.levelOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Orders eligible story groups into the feed (SPEC S10, S16; B22). Only ever pass groups that
 * [app.newz.filter.FeedFilter] let through: ranking orders, it never re-admits.
 *
 * - Recency bands: a group's effective age is its lead's age divided by the weight of its topic
 *   (the lead's `primaryTopic`): Normal 1, More 2, Much more 3, so a level stretches the bands.
 *   [BAND_EDGES] split effective ages into bands; every card of a band comes before the next band.
 * - Inside a band each topic is a queue, newest first. Queues are interleaved by stride
 *   scheduling with the same weights, so over equal supply a More topic gets about twice and a
 *   Much more topic about three times the cards of a Normal one (a boosted topic also has about
 *   weight times the supply in each band, which the stride spreads evenly through it).
 * - Source spread: a card whose outlet already has [MAX_PER_WINDOW] cards among the last
 *   [SPREAD_WINDOW] - 1 emitted (across bands) is skipped for another queue's head in its band,
 *   then for a later card in the band, then for the next bands' heads and then their later cards
 *   (the cases where newest-first or band order bends). Only when no other outlet has cards left
 *   is the rule relaxed.
 */
object FeedRanker {
    const val SPREAD_WINDOW = 10
    const val MAX_PER_WINDOW = 2

    /** Lower edges of the recency bands after the first, by effective age: < 2 h, 2-6 h, 6-24 h, 1-3 days, 3 days +. */
    val BAND_EDGES: List<Duration> = listOf(2.hours, 6.hours, 24.hours, 3.days)

    /** Stride per weight: 6 / weight, so Normal 6, More 3, Much more 2 (integer, no drift). */
    private const val STRIDE_BASE = 6L

    fun weight(level: Level): Int =
        when (level) {
            Level.NORMAL -> 1
            Level.MORE -> 2
            Level.MUCH_MORE -> 3
        }

    /** The band (0 newest) of a card [age] old whose topic is at [level]. */
    fun band(
        age: Duration,
        level: Level,
    ): Int {
        val effective = age / weight(level)
        return BAND_EDGES.count { effective >= it }
    }

    fun rank(
        groups: List<StoryGroup>,
        prefs: UserPrefs,
        now: Instant,
    ): List<StoryGroup> {
        val newestFirst =
            compareByDescending<StoryGroup> { minOf(it.lead.date, now) }.thenBy { it.lead.id }
        val queues =
            groups
                .groupBy { g ->
                    val topic = g.lead.primaryTopic.orEmpty()
                    band(now - minOf(g.lead.date, now), prefs.levelOf(topic)) to topic
                }.map { (key, list) ->
                    val (band, topic) = key
                    Queue(band, topic, list.sortedWith(newestFirst).toMutableList(), stride(prefs, topic))
                }
        val order = prefs.followedTopics.withIndex().associate { (i, t) -> t to i }
        val byTurn =
            compareBy<Queue> { it.band }
                .thenBy { it.pass }
                .thenByDescending { minOf(it.head.lead.date, now) }
                .thenBy { order[it.topic] ?: Int.MAX_VALUE }
                .thenBy { it.topic }

        val out = ArrayList<StoryGroup>(groups.size)
        val recent = ArrayDeque<String>()
        while (true) {
            val live = queues.filter { it.items.isNotEmpty() }.sortedWith(byTurn)
            if (live.isEmpty()) break
            val (current, later) = live.partition { it.band == live.first().band }

            fun fits(g: StoryGroup) = recent.count { it == g.lead.outletId } < MAX_PER_WINDOW

            fun headOf(qs: List<Queue>) = qs.firstOrNull { fits(it.head) }?.let { it to 0 }

            fun laterCardOf(qs: List<Queue>) =
                qs.firstNotNullOfOrNull { q ->
                    q.items
                        .indexOfFirst(::fits)
                        .takeIf { it >= 0 }
                        ?.let { q to it }
                }
            val (queue, index) =
                headOf(current)
                    ?: laterCardOf(current)
                    ?: headOf(later)
                    ?: laterCardOf(later)
                    ?: (current.first() to 0)
            val picked = queue.items.removeAt(index)
            queue.pass += queue.stride
            out += picked
            recent.addLast(picked.lead.outletId)
            if (recent.size > SPREAD_WINDOW - 1) recent.removeFirst()
        }
        return out
    }

    private fun stride(
        prefs: UserPrefs,
        topic: String,
    ): Long = STRIDE_BASE / weight(prefs.levelOf(topic))

    private class Queue(
        val band: Int,
        val topic: String,
        val items: MutableList<StoryGroup>,
        val stride: Long,
    ) {
        var pass = 0L

        val head: StoryGroup get() = items.first()
    }
}
