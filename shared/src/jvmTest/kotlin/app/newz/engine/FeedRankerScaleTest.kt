package app.newz.engine

import app.newz.dedup.StoryGroup
import app.newz.feed.Article
import app.newz.store.Level
import app.newz.store.UserPrefs
import app.newz.store.levelOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * B39 (review R11): ranking stays fast when one outlet dominates, and its output is exactly
 * what the original quadratic algorithm, kept below as [reference], produced.
 */
class FeedRankerScaleTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val topics = listOf("world", "tech", "sports", "science", "health")

    private fun group(
        i: Int,
        topic: String,
        outlet: String,
        ageMinutes: Long,
    ) = StoryGroup(
        Article(
            id = "g$i",
            feedId = "$outlet-$topic",
            outletId = outlet,
            url = "https://$outlet.example/$i",
            title = "Story $i",
            summary = null,
            imageUrl = null,
            publishedAt = now - ageMinutes.minutes,
            firstSeenAt = now - ageMinutes.minutes,
            topics = listOf(topic),
        ),
        emptyList(),
    )

    /** [n] groups over a week; [dominant] of them from one outlet, the rest from [outlets] others. */
    private fun feed(
        n: Int,
        dominant: Double,
        outlets: Int,
        seed: Int,
    ): List<StoryGroup> {
        val random = Random(seed)
        return (0 until n).map { i ->
            val outlet = if (random.nextDouble() < dominant) "big" else "o${random.nextInt(outlets)}"
            group(i, topics[random.nextInt(topics.size)], outlet, random.nextLong(7 * 24 * 60))
        }
    }

    private val prefs =
        UserPrefs(followedTopics = topics, topicLevels = mapOf("tech" to Level.MORE, "health" to Level.MUCH_MORE))

    @Test
    fun outputMatchesTheOriginalAlgorithm() {
        for (seed in 1..40) {
            val n = 50 + seed * 13
            val dominant = (seed % 5) * 0.2
            val groups = feed(n, dominant, outlets = 1 + seed % 9, seed = seed)
            assertEquals(
                reference(groups, prefs, now).map {
                    it.lead.id
                },
                FeedRanker.rank(groups, prefs, now).map { it.lead.id },
                "seed $seed",
            )
        }
    }

    @Test
    fun twelveThousandGroupsFromOneDominantOutletRankQuickly() {
        val groups = feed(12_000, dominant = 0.8, outlets = 30, seed = 7)
        FeedRanker.rank(groups, prefs, now) // warm up the JIT
        val mark = TimeSource.Monotonic.markNow()
        val ranked = FeedRanker.rank(groups, prefs, now)
        val took = mark.elapsedNow()
        println("FeedRanker: 12,000 groups, 80% from one outlet, in $took")
        assertEquals(12_000, ranked.size)
        // The quadratic version took about a second on a desktop JVM.
        assertTrue(took.inWholeMilliseconds < 250, "ranking took $took")
    }

    /** The ranking algorithm as it was before B39, verbatim apart from names. */
    private fun reference(
        groups: List<StoryGroup>,
        prefs: UserPrefs,
        now: Instant,
    ): List<StoryGroup> {
        class Queue(
            val band: Int,
            val topic: String,
            val items: MutableList<StoryGroup>,
            val stride: Long,
        ) {
            var pass = 0L
            val head: StoryGroup get() = items.first()
        }
        val newestFirst = compareByDescending<StoryGroup> { minOf(it.lead.date, now) }.thenBy { it.lead.id }
        val queues =
            groups
                .groupBy { g ->
                    val topic = g.lead.primaryTopic.orEmpty()
                    FeedRanker.band(now - minOf(g.lead.date, now), prefs.levelOf(topic)) to topic
                }.map { (key, list) ->
                    val (band, topic) = key
                    Queue(
                        band,
                        topic,
                        list.sortedWith(newestFirst).toMutableList(),
                        6L / FeedRanker.weight(prefs.levelOf(topic)),
                    )
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

            fun fits(g: StoryGroup) = recent.count { it == g.lead.outletId } < FeedRanker.MAX_PER_WINDOW

            fun headOf(qs: List<Queue>) = qs.firstOrNull { fits(it.head) }?.let { it to 0 }

            fun laterCardOf(qs: List<Queue>) =
                qs.firstNotNullOfOrNull { q ->
                    q.items
                        .indexOfFirst(::fits)
                        .takeIf { it >= 0 }
                        ?.let { q to it }
                }
            val (queue, index) =
                headOf(current) ?: laterCardOf(current) ?: headOf(later) ?: laterCardOf(later) ?: (current.first() to 0)
            val picked = queue.items.removeAt(index)
            queue.pass += queue.stride
            out += picked
            recent.addLast(picked.lead.outletId)
            if (recent.size > FeedRanker.SPREAD_WINDOW - 1) recent.removeFirst()
        }
        return out
    }
}
