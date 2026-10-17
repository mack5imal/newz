package app.newz.engine

import app.newz.dedup.StoryGroup
import app.newz.feed.Article
import app.newz.store.Level
import app.newz.store.UserPrefs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FeedRankerTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val topics = listOf("world", "tech", "sports", "science")

    private fun group(
        id: String,
        topic: String,
        outlet: String,
        age: Duration,
    ) = StoryGroup(
        Article(
            id = id,
            feedId = "$outlet-$topic",
            outletId = outlet,
            url = "https://$outlet.example/$id",
            title = "Story $id",
            summary = null,
            imageUrl = null,
            publishedAt = now - age,
            firstSeenAt = now - age,
            topics = listOf(topic),
        ),
        emptyList(),
    )

    /** 100 cards per topic, one every 15 minutes, spread over 12 outlets. */
    private fun equalSupply(): List<StoryGroup> =
        topics.flatMapIndexed { t, topic ->
            (0 until 100).map { i -> group("$topic-$i", topic, "outlet-${(i * 5 + t) % 12}", (i * 15).minutes) }
        }

    /** (count of [boosted]) / (mean count of the other topics) among the first 120 cards. */
    internal fun boostRatio(level: Level): Double {
        val boosted = topics.first()
        val prefs = UserPrefs(followedTopics = topics, topicLevels = mapOf(boosted to level))
        val counts =
            FeedRanker
                .rank(
                    equalSupply(),
                    prefs,
                    now,
                ).take(120)
                .groupingBy { it.lead.primaryTopic }
                .eachCount()
        val normalMean = topics.drop(1).map { counts[it] ?: 0 }.average()
        return (counts[boosted] ?: 0) / normalMean
    }

    @Test
    fun moreTopicGetsAboutTwiceTheCardsOfANormalTopic() {
        val ratio = boostRatio(Level.MORE)
        assertTrue(ratio in 1.7..2.3, "More ratio $ratio")
    }

    @Test
    fun muchMoreTopicGetsAboutThreeTimesTheCardsOfANormalTopic() {
        val ratio = boostRatio(Level.MUCH_MORE)
        assertTrue(ratio in 2.6..3.4, "Much more ratio $ratio")
    }

    @Test
    fun allNormalTopicsShareEvenly() {
        val prefs = UserPrefs(followedTopics = topics)
        val counts =
            FeedRanker
                .rank(
                    equalSupply(),
                    prefs,
                    now,
                ).take(120)
                .groupingBy { it.lead.primaryTopic }
                .eachCount()
        assertEquals<Map<String?, Int>>(topics.associateWith { 30 }, counts)
    }

    /** 200 cards over 10 outlets and 5 topics, random ages within 7 days, in random order. */
    internal fun mixedFixture(): List<StoryGroup> {
        val random = Random(8)
        val mixedTopics = topics + "politics"
        return (0 until 200).map { i ->
            group(
                "m$i",
                mixedTopics[random.nextInt(mixedTopics.size)],
                "outlet-${random.nextInt(10)}",
                random.nextLong(7.days.inWholeMinutes).minutes,
            )
        }
    }

    internal fun worstWindowCount(ranked: List<StoryGroup>): Int =
        ranked
            .windowed(FeedRanker.SPREAD_WINDOW, partialWindows = true)
            .maxOf { window ->
                window
                    .groupingBy { it.lead.outletId }
                    .eachCount()
                    .values
                    .max()
            }

    internal fun medianAgeMinutes(cards: List<StoryGroup>): Long {
        val ages = cards.map { (now - it.lead.date).inWholeMinutes }.sorted()
        return (ages[(ages.size - 1) / 2] + ages[ages.size / 2]) / 2
    }

    @Test
    fun noOutletHoldsMoreThanTwoCardsInAnyTenAndRecencyFalls() {
        val fixture = mixedFixture()
        assertTrue(fixture.map { it.lead.outletId }.distinct().size >= 8)
        val prefs = UserPrefs(followedTopics = topics + "politics", topicLevels = mapOf("tech" to Level.MORE))
        val ranked = FeedRanker.rank(fixture, prefs, now)
        assertEquals(fixture.map { it.lead.id }.toSet(), ranked.map { it.lead.id }.toSet())
        assertEquals(200, ranked.size)
        assertTrue(worstWindowCount(ranked) <= FeedRanker.MAX_PER_WINDOW, "worst ${worstWindowCount(ranked)}")
        val top = medianAgeMinutes(ranked.subList(0, 20))
        val deep = medianAgeMinutes(ranked.subList(80, 100))
        assertTrue(top < deep, "median age 1-20 = $top min, 81-100 = $deep min")
    }

    @Test
    fun spreadRuleRelaxesWhenOnlyOneOutletHasCardsLeft() {
        val fixture =
            (0 until 10).map { group("a$it", "world", "outlet-a", (it * 10).minutes) } +
                (0 until 2).map { group("b$it", "tech", "outlet-b", (it * 10).minutes) }
        val ranked = FeedRanker.rank(fixture, UserPrefs(followedTopics = topics), now)
        assertEquals(fixture.map { it.lead.id }.toSet(), ranked.map { it.lead.id }.toSet())
        assertEquals(12, ranked.size)
    }

    @Test
    fun eachTopicQueueIsNewestFirst() {
        val ranked = FeedRanker.rank(equalSupply().shuffled(Random(3)), UserPrefs(followedTopics = topics), now)
        for (topic in topics) {
            val dates = ranked.filter { it.lead.primaryTopic == topic }.map { it.lead.date }
            assertEquals(dates.sortedDescending(), dates)
        }
    }

    @Test
    fun futureDatesCountAsNow() {
        val future = group("future", "world", "outlet-x", (-2).days)
        val fresh = group("fresh", "world", "outlet-y", 0.minutes)
        val ranked = FeedRanker.rank(listOf(fresh, future), UserPrefs(followedTopics = topics), now)
        assertEquals(listOf("fresh", "future"), ranked.map { it.lead.id })
    }
}
