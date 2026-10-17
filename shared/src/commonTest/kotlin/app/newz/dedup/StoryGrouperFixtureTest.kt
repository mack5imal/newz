package app.newz.dedup

import app.newz.feed.Article
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Story grouping against labelled headline pairs from the UCI News Aggregator data set
 * (Gasparetti 2017, CC BY 4.0, see resources/dedup/NOTICE.md; built by
 * scripts/build-dedup-fixture.py).
 *
 * The UCI data has titles only, no summaries, so every article here is a headline with an empty
 * summary; real feeds add summary text, which only adds evidence. The same-story pairs are *clear*
 * same-event pairs: same UCI STORY id, different publishers, both titles naming the same event,
 * hand-checked with ambiguous candidates dropped (owner decision, SPEC interview round 9). UCI
 * STORY clusters are coarser than one event, so raw same-STORY pairs would understate recall.
 * Different-story pairs include hard negatives: same category and day, sharing a proper noun.
 *
 * Each pair is grouped alone with the fixture's 30 unrelated distractor titles, so the IDF is
 * computed over a realistic window rather than two documents.
 *
 * Targets (owner decision 2026-10-16, option 1 in phase-7.fix.md): at least 75 % of same-story
 * pairs grouped (amended from 90 %) and at most 5 % of different-story pairs grouped. The fixture
 * has headlines only, and headline-only lexical similarity tops out near 0.77-0.79 recall at 5 %
 * false grouping on it; real feeds add summary text. Measured at name weight 1.0: recall 0.769
 * and false grouping 0 at 0.275; both targets hold from 0.26 to 0.285.
 */
class StoryGrouperFixtureTest {
    internal data class Rates(
        val recall: Double,
        val falseGroupRate: Double,
    )

    private val pairs = UciPairsFixture.pairs
    private val same = pairs.filter { it.label == "same" }
    private val different = pairs.filter { it.label != "same" }

    @Test
    fun fixtureHasEnoughPairsOfEachKind() {
        assertTrue(same.size >= 40, "same-story pairs: ${same.size}")
        assertTrue(different.size >= 40, "different-story pairs: ${different.size}")
        assertTrue(pairs.count { it.label == "hard" } >= 20, "hard negatives")
        assertEquals(30, UciPairsFixture.distractors.size)
        assertTrue(pairs.all { it.publisherA != it.publisherB && it.hoursApart <= 48.0 })
    }

    @Test
    fun groupsAtLeastSeventyFivePercentOfSameStoryPairs() {
        val r = evaluate(SAME_STORY_THRESHOLD)
        assertTrue(r.recall >= MIN_RECALL, "recall ${r.recall} at $SAME_STORY_THRESHOLD")
    }

    @Test
    fun groupsAtMostFivePercentOfDifferentStoryPairs() {
        val r = evaluate(SAME_STORY_THRESHOLD)
        assertTrue(
            r.falseGroupRate <= MAX_FALSE_GROUP_RATE,
            "false-group rate ${r.falseGroupRate} at $SAME_STORY_THRESHOLD",
        )
    }

    internal fun evaluate(threshold: Double): Rates {
        val grouper = StoryGrouper(threshold)

        fun grouped(p: UciPair): Boolean {
            val a = article("a", p.publisherA, p.titleA, T0)
            val b = article("b", p.publisherB, p.titleB, T0 - (p.hoursApart * 3600).roundToLong().seconds)
            val window =
                listOf(a, b) +
                    UciPairsFixture.distractors.mapIndexed { i, title ->
                        article("d$i", "distractor-$i", title, T0 - 2.hours)
                    }
            val groups = grouper.group(window) { 0.0 }
            return groups.any { g -> g.members().any { it.id == "a" } && g.members().any { it.id == "b" } }
        }
        return Rates(
            recall = same.count(::grouped).toDouble() / same.size,
            falseGroupRate = different.count(::grouped).toDouble() / different.size,
        )
    }

    private fun article(
        id: String,
        outlet: String,
        title: String,
        at: Instant,
    ) = Article(
        id = id,
        feedId = "$outlet-feed",
        outletId = outlet,
        url = "https://example.test/$id",
        title = title,
        summary = null,
        imageUrl = null,
        publishedAt = at,
        firstSeenAt = at,
        topics = emptyList(),
    )

    private companion object {
        val T0 = Instant.parse("2026-10-16T12:00:00Z")
        const val MIN_RECALL = 0.75
        const val MAX_FALSE_GROUP_RATE = 0.05
    }
}
