package app.newz.dedup

import app.newz.feed.Article
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTimedValue

/**
 * B17: [StoryGrouper] must group exactly as the original quadratic version did, only faster.
 * [ReferenceGrouper] is that original algorithm, copied unchanged; both run on large realistic
 * windows (about 100 outlets, dates over 7 days, stories retold by several outlets, shared names)
 * and on small dense ones built to hit similarity ties, outlet clashes and the 48-hour edge.
 */
class StoryGrouperEquivalenceTest {
    @Test
    fun groupsExactlyAsTheReferenceOnRealisticWindows() {
        for (seed in 1..4) {
            val articles = realisticWindow(seed, size = 3_000)
            val affinity = affinity(seed)
            for (threshold in listOf(SAME_STORY_THRESHOLD, 0.15, 0.5)) {
                assertEquals(
                    ReferenceGrouper(threshold).group(articles, affinity),
                    StoryGrouper(threshold).group(articles, affinity),
                    "seed $seed, threshold $threshold",
                )
            }
        }
    }

    @Test
    fun groupsExactlyAsTheReferenceOnDenseWindowsWithTies() {
        for (seed in 1..60) {
            val articles = denseWindow(seed)
            val affinity = affinity(seed)
            for (threshold in listOf(SAME_STORY_THRESHOLD, 0.0, -1.0, 0.6, 1.0)) {
                assertEquals(
                    ReferenceGrouper(threshold).group(articles, affinity),
                    StoryGrouper(threshold).group(articles, affinity),
                    "seed $seed, threshold $threshold",
                )
            }
        }
    }

    @Test
    fun groupsFiveThousandArticlesWellUnderTwoSeconds() {
        val articles = realisticWindow(seed = 99, size = 5_000)
        val affinity = affinity(99)
        StoryGrouper().group(articles, affinity) // warm up the JIT
        val (fast, newTime) = measureTimedValue { StoryGrouper().group(articles, affinity) }
        val (slow, oldTime) = measureTimedValue { ReferenceGrouper().group(articles, affinity) }
        println("StoryGrouper 5,000 articles: new $newTime, reference $oldTime, ${fast.size} groups")
        assertEquals(slow, fast)
        assertTrue(fast.size < articles.size, "some stories group")
        assertTrue(newTime < 2.seconds, "grouping took $newTime")
    }

    /**
     * [size] articles from 100 outlets over 7 days, as a full cache looks: most are one-off
     * headlines, the rest retell a story another outlet also ran (shared names, numbers and topic
     * words, reworded per outlet). Words are drawn with a skew, so common ones recur everywhere.
     */
    private fun realisticWindow(
        seed: Int,
        size: Int,
    ): List<Article> {
        val random = Random(seed)
        val stories =
            List(size / 3) {
                Story(
                    at = T0 - (random.nextDouble() * 7 * 24).hours,
                    names = List(random.nextInt(1, 4)) { skewed(BIG_NAMES, random) },
                    words = List(random.nextInt(3, 7)) { skewed(BIG_WORDS, random) },
                    number = if (random.nextInt(4) == 0) random.nextInt(2, 500).toString() else null,
                )
            }
        return List(size) { i ->
            val outlet = "outlet-${random.nextInt(100)}"
            val summaryWords = List(random.nextInt(8, 20)) { skewed(BIG_WORDS, random) }
            if (random.nextInt(5) < 3) {
                val words =
                    List(random.nextInt(5, 11)) {
                        if (random.nextInt(4) == 0) skewed(BIG_NAMES, random) else skewed(BIG_WORDS, random)
                    }
                val summary = if (random.nextBoolean()) sentence(summaryWords, random) else null
                article(i, outlet, sentence(words, random), summary, T0 - (random.nextDouble() * 7 * 24).hours, random)
            } else {
                val story = stories.random(random)
                val words =
                    (story.names + story.words.shuffled(random).take(random.nextInt(2, story.words.size + 1)))
                        .plus(listOfNotNull(story.number))
                        .plus(List(random.nextInt(0, 4)) { skewed(BIG_WORDS, random) })
                        .shuffled(random)
                val summary =
                    if (random.nextBoolean()) sentence((story.words + summaryWords).shuffled(random), random) else null
                val at = story.at + (random.nextDouble() * 60 - 6).hours
                article(i, outlet, sentence(words, random), summary, at, random)
            }
        }
    }

    /** A word of [list], early ones far more often (a rough Zipf curve). */
    private fun skewed(
        list: List<String>,
        random: Random,
    ): String {
        val r = random.nextDouble()
        return list[(list.size * r * r * r).toInt()]
    }

    /**
     * A few dozen articles from 4 outlets over 3 days, all on 3 stories with identical or nearly
     * identical headlines: outlets clash, groups tie on similarity and some pairs sit at 48 hours.
     */
    private fun denseWindow(seed: Int): List<Article> {
        val random = Random(seed)
        val headlines = List(3) { List(4) { WORDS.random(random) } + NAMES.random(random) }
        return List(random.nextInt(10, 60)) { i ->
            val words = headlines.random(random).let { if (random.nextInt(3) == 0) it.drop(1) else it }
            val at =
                when (random.nextInt(3)) {
                    0 -> T0 - (random.nextInt(4) * 24).hours
                    1 -> T0 - 48.hours * random.nextInt(2)
                    else -> T0 - (random.nextInt(72 * 60)).minutes
                }
            article(i, "outlet-${random.nextInt(4)}", sentence(words, random), null, at, random)
        } + article(999, "outlet-9", "", null, T0, random)
    }

    private fun sentence(
        words: List<String>,
        random: Random,
    ): String = words.joinToString(" ").replaceFirstChar { if (random.nextBoolean()) it.uppercaseChar() else it }

    private fun article(
        n: Int,
        outlet: String,
        title: String,
        summary: String?,
        at: Instant,
        random: Random,
    ) = Article(
        id = "a$n",
        feedId = "$outlet-feed",
        outletId = outlet,
        url = "https://example.test/$n",
        title = title,
        summary = summary,
        imageUrl = null,
        publishedAt = if (random.nextInt(10) == 0) null else at,
        firstSeenAt = at,
        topics = emptyList(),
    )

    private fun affinity(seed: Int): (String) -> Double {
        val random = Random(seed * 31)
        val scores = List(100) { if (random.nextBoolean()) 0.0 else random.nextInt(3) * 0.5 }
        return { outlet -> scores[outlet.substringAfter('-').toInt() % 100] }
    }

    private class Story(
        val at: Instant,
        val names: List<String>,
        val words: List<String>,
        val number: String?,
    )

    /** The original grouper (before B17), copied unchanged as the reference for exact results. */
    private class ReferenceGrouper(
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
                article.outletId !in outlets &&
                    members.all { (it.date - article.date).absoluteValue <= StoryGrouper.WINDOW }

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

        private fun vectors(articles: List<Article>): List<Map<String, Double>> {
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
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-16T12:00:00Z")

        val NAMES =
            """
            Biden Trump Harris Putin Zelensky Macron Starmer Modi Netanyahu Xi Musk Bezos Swift Ohtani
            Nvidia Apple Tesla Boeing Pfizer Fed NATO Kyiv Gaza Taiwan Texas Florida California Ohio
            Paris Berlin Tokyo Beijing Delhi Lagos Cairo Seoul Manila Mexico Canada Brazil Chile Peru
            """.trim().split(Regex("\\s+"))

        val WORDS =
            """
            election vote court ruling judge strike union wage inflation rates market stocks bonds oil
            gas storm hurricane flood fire wildfire quake earthquake missile drone attack ceasefire talks
            summit deal tariff trade chip chips launch rocket satellite vaccine virus outbreak hospital
            school teachers police shooting arrest trial verdict appeal senate house bill budget shutdown
            merger layoffs earnings profit loss record crash flight airport rail bridge power outage
            heat drought crop prices housing rent mortgage bank crypto bitcoin league final title coach
            injury transfer season playoff museum film award festival album tour concert study climate
            emissions ocean species ice space moon mars telescope robot software hack breach privacy
            """.trim().split(Regex("\\s+"))

        /** [WORDS] then made-up lowercase words, 3,000 in all. */
        val BIG_WORDS = WORDS + List(3_000 - WORDS.size) { made(it, upper = false) }

        /** [NAMES] then made-up names, 600 in all. */
        val BIG_NAMES = NAMES + List(600 - NAMES.size) { made(it + 50_000, upper = true) }

        private fun made(
            n: Int,
            upper: Boolean,
        ): String {
            val syllables = listOf("ka", "lo", "mi", "ren", "tu", "sha", "vo", "dra", "pel", "nix", "or", "ba")
            var x = n
            val sb = StringBuilder()
            do {
                sb.append(syllables[x % syllables.size])
                x /= syllables.size
            } while (x > 0)
            sb.append("z")
            return if (upper) sb.toString().replaceFirstChar { it.uppercaseChar() } else sb.toString()
        }
    }
}
