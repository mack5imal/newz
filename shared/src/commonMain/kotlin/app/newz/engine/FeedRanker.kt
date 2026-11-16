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
                    Queue(band, topic, list.sortedWith(newestFirst), stride(prefs, topic))
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
        // Cards per outlet among [recent], so the spread check is a lookup, not a count (B39).
        val inWindow = HashMap<String, Int>()

        fun blocked(outletId: String) = (inWindow[outletId] ?: 0) >= MAX_PER_WINDOW
        while (true) {
            val live = queues.filter { !it.isEmpty() }.sortedWith(byTurn)
            if (live.isEmpty()) break
            val (current, later) = live.partition { it.band == live.first().band }

            fun headOf(qs: List<Queue>) =
                qs.firstOrNull { !blocked(it.head.lead.outletId) }?.let { it to it.headIndex() }

            fun laterCardOf(qs: List<Queue>) =
                qs.firstNotNullOfOrNull { q -> q.firstUnblocked(::blocked)?.let { q to it } }
            val (queue, index) =
                headOf(current)
                    ?: laterCardOf(current)
                    ?: headOf(later)
                    ?: laterCardOf(later)
                    ?: current.first().let { it to it.headIndex() }
            val picked = queue.remove(index)
            queue.pass += queue.stride
            out += picked
            val outletId = picked.lead.outletId
            recent.addLast(outletId)
            inWindow[outletId] = (inWindow[outletId] ?: 0) + 1
            if (recent.size > SPREAD_WINDOW - 1) {
                val left = recent.removeFirst()
                val n = inWindow.getValue(left) - 1
                if (n == 0) inWindow.remove(left) else inWindow[left] = n
            }
        }
        return out
    }

    private fun stride(
        prefs: UserPrefs,
        topic: String,
    ): Long = STRIDE_BASE / weight(prefs.levelOf(topic))

    /**
     * One topic's cards in one band, newest first, from which cards are removed anywhere. "The
     * first card whose outlet is not blocked" used to scan the whole queue, so ranking went
     * quadratic once one outlet filled the spread window (B39). Instead each outlet keeps the
     * positions of its cards, and a heap holds each outlet's first remaining position. Only a
     * few outlets can be blocked at once, so only those few heap entries are ever skipped.
     */
    private class Queue(
        val band: Int,
        val topic: String,
        private val items: List<StoryGroup>,
        val stride: Long,
    ) {
        var pass = 0L

        private val alive = BooleanArray(items.size) { true }
        private var remaining = items.size
        private var headCursor = 0

        /** Each outlet's card positions, ascending, and the index of its first remaining one. */
        private val positions: Map<String, IntArray> =
            items.indices.groupBy { items[it].lead.outletId }.mapValues { it.value.toIntArray() }
        private val cursor = HashMap<String, Int>().apply { positions.keys.forEach { put(it, 0) } }
        private val heads = IntHeap().apply { positions.values.forEach { push(it[0]) } }

        fun isEmpty(): Boolean = remaining == 0

        fun headIndex(): Int {
            while (!alive[headCursor]) headCursor++
            return headCursor
        }

        val head: StoryGroup get() = items[headIndex()]

        private fun outletOf(i: Int) = items[i].lead.outletId

        /** The outlet's first remaining position, or -1. */
        private fun firstOf(outletId: String): Int {
            val list = positions.getValue(outletId)
            val c = cursor.getValue(outletId)
            return if (c < list.size) list[c] else -1
        }

        /** The first remaining position whose outlet [blocked] lets through, or null. */
        fun firstUnblocked(blocked: (String) -> Boolean): Int? {
            val skipped = ArrayList<Int>()
            var found: Int? = null
            while (!heads.isEmpty()) {
                val i = heads.pop()
                if (firstOf(outletOf(i)) != i) continue // stale: that card is gone
                skipped += i
                if (!blocked(outletOf(i))) {
                    found = i
                    break
                }
            }
            skipped.forEach(heads::push)
            return found
        }

        fun remove(i: Int): StoryGroup {
            alive[i] = false
            remaining--
            val outletId = outletOf(i)
            if (firstOf(outletId) == i) {
                val list = positions.getValue(outletId)
                var c = cursor.getValue(outletId)
                while (c < list.size && !alive[list[c]]) c++
                cursor[outletId] = c
                if (c < list.size) heads.push(list[c])
            }
            return items[i]
        }
    }

    /** A binary min-heap of ints; common code has no PriorityQueue. */
    private class IntHeap {
        private var a = IntArray(16)
        private var size = 0

        fun isEmpty() = size == 0

        fun push(v: Int) {
            if (size == a.size) a = a.copyOf(size * 2)
            var i = size++
            a[i] = v
            while (i > 0) {
                val p = (i - 1) / 2
                if (a[p] <= a[i]) break
                a[p] = a[i].also { a[i] = a[p] }
                i = p
            }
        }

        fun pop(): Int {
            val top = a[0]
            a[0] = a[--size]
            var i = 0
            while (true) {
                val l = 2 * i + 1
                val r = l + 1
                var m = i
                if (l < size && a[l] < a[m]) m = l
                if (r < size && a[r] < a[m]) m = r
                if (m == i) break
                a[m] = a[i].also { a[i] = a[m] }
                i = m
            }
            return top
        }
    }
}
