package app.newz.engine

import app.newz.dedup.StoryGroup
import app.newz.store.Level
import app.newz.store.UserPrefs
import app.newz.store.levelOf
import kotlin.time.Instant

/**
 * Orders eligible story groups into the feed (SPEC S10, S16). Only ever pass groups that
 * [app.newz.filter.FeedFilter] let through: ranking orders, it never re-admits.
 *
 * - Each topic (the lead's `primaryTopic`) is a queue, newest first. Queues are interleaved by
 *   stride scheduling with weight Normal 1, More 2, Much more 3, so over equal supply a More topic
 *   gets about twice and a Much more topic about three times the cards of a Normal one.
 * - Source spread: a card whose outlet already has [MAX_PER_WINDOW] cards among the last
 *   [SPREAD_WINDOW] - 1 emitted is skipped for another queue's head, then for a later card in any
 *   queue (the one case where a queue's newest-first order bends). Only when no other outlet has
 *   cards left is the rule relaxed.
 */
object FeedRanker {
    const val SPREAD_WINDOW = 10
    const val MAX_PER_WINDOW = 2

    /** Stride per weight: 6 / weight, so Normal 6, More 3, Much more 2 (integer, no drift). */
    private const val STRIDE_BASE = 6L

    fun weight(level: Level): Int =
        when (level) {
            Level.NORMAL -> 1
            Level.MORE -> 2
            Level.MUCH_MORE -> 3
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
                .groupBy { it.lead.primaryTopic.orEmpty() }
                .map { (topic, list) ->
                    Queue(topic, list.sortedWith(newestFirst).toMutableList(), stride(prefs, topic))
                }
        val order = prefs.followedTopics.withIndex().associate { (i, t) -> t to i }
        val byTurn =
            compareBy<Queue> { it.pass }
                .thenByDescending { minOf(it.head.lead.date, now) }
                .thenBy { order[it.topic] ?: Int.MAX_VALUE }
                .thenBy { it.topic }

        val out = ArrayList<StoryGroup>(groups.size)
        val recent = ArrayDeque<String>()
        while (true) {
            val live = queues.filter { it.items.isNotEmpty() }.sortedWith(byTurn)
            if (live.isEmpty()) break

            fun fits(g: StoryGroup) = recent.count { it == g.lead.outletId } < MAX_PER_WINDOW
            val (queue, index) =
                live.firstOrNull { fits(it.head) }?.let { it to 0 }
                    ?: live.firstNotNullOfOrNull { q ->
                        q.items
                            .indexOfFirst(::fits)
                            .takeIf { it >= 0 }
                            ?.let { q to it }
                    }
                    ?: (live.first() to 0)
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
        val topic: String,
        val items: MutableList<StoryGroup>,
        val stride: Long,
    ) {
        var pass = 0L

        val head: StoryGroup get() = items.first()
    }
}
