package app.newz.dedup

import app.newz.feed.Article
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class StoryGrouperTest {
    private val grouper = StoryGrouper()

    private val headline = "Volcano erupts near Reykjavik airport, flights halted"

    /** Unrelated titles so the IDF is not built from two or three documents. */
    private val background =
        listOf(
            "Central bank holds interest rates steady",
            "Local team wins championship in overtime",
            "New smartphone goes on sale next week",
            "City council approves budget for parks",
            "Scientists find water ice on distant moon",
        ).mapIndexed { i, t -> article("bg$i", "bg-outlet-$i", t, 3.hours) }

    @Test
    fun articlesMoreThan48HoursApartAreNeverGrouped() {
        val cases =
            listOf(
                Triple(47.hours, true, "47 h apart"),
                Triple(48.hours, true, "48 h apart"),
                Triple(49.hours, false, "49 h apart"),
                Triple(72.hours, false, "72 h apart"),
            )
        for ((gap, expected, name) in cases) {
            val a = article("a", "outlet-a", headline, Duration.ZERO)
            val b = article("b", "outlet-b", headline, gap)
            assertEquals(expected, together(grouper.group(listOf(a, b) + background, ::noAffinity), "a", "b"), name)
        }
    }

    @Test
    fun twoArticlesFromOneOutletAreNeverGrouped() {
        val a = article("a", "same-outlet", headline, Duration.ZERO)
        val b = article("b", "same-outlet", headline, 1.hours)
        val c = article("c", "other-outlet", headline, 2.hours)
        val groups = grouper.group(listOf(a, b, c) + background, ::noAffinity)
        assertFalse(together(groups, "a", "b"))
        assertTrue(together(groups, "a", "c") || together(groups, "b", "c"))
    }

    @Test
    fun leadIsTheMemberWhoseOutletHasHighestAffinityTiesToNewest() {
        val a = article("a", "low", headline, Duration.ZERO)
        val b = article("b", "high", headline, 5.hours)
        val c = article("c", "mid", headline, 2.hours)
        val affinity = mapOf("low" to 0.1, "high" to 0.9, "mid" to 0.5)
        val group = groupOf(grouper.group(listOf(a, b, c) + background) { affinity.getValue(it) }, "a")
        assertEquals("b", group.lead.id)
        assertEquals(listOf("a", "c"), group.others.map { it.id })
        assertEquals(2, group.othersCount)

        val tied = groupOf(grouper.group(listOf(b, c) + background) { 0.5 }, "b")
        assertEquals("c", tied.lead.id, "equal affinity: the newest member leads")
    }

    @Test
    fun membersAreNewestFirstAndASingleArticleHasNoOthers() {
        val a = article("a", "outlet-a", headline, 4.hours)
        val b = article("b", "outlet-b", headline, Duration.ZERO)
        val c = article("c", "outlet-c", headline, 9.hours)
        val groups = grouper.group(listOf(a, b, c) + background) { if (it == "outlet-c") 1.0 else 0.0 }
        val group = groupOf(groups, "a")
        assertEquals("c", group.lead.id)
        assertEquals(listOf("b", "a", "c"), group.members().map { it.id })

        val single = groupOf(groups, "bg0")
        assertEquals(listOf("bg0"), single.members().map { it.id })
        assertEquals(0, single.othersCount)
    }

    @Test
    fun similarityDoesNotChainThroughAMiddleArticle() {
        val a = article("a", "outlet-a", "Volcano erupts near Reykjavik airport", Duration.ZERO)
        val b = article("b", "outlet-b", "Reykjavik airport shut as volcano ash cloud drifts over Atlantic", 1.hours)
        val c = article("c", "outlet-c", "Ash cloud over Atlantic delays transatlantic flights", 2.hours)
        val window = listOf(a, b, c) + background
        val v = StoryGrouper.vectors(window)
        assertTrue(StoryGrouper.cosine(v[0], v[1]) >= SAME_STORY_THRESHOLD, "A~B")
        assertTrue(StoryGrouper.cosine(v[1], v[2]) >= SAME_STORY_THRESHOLD, "B~C")
        assertTrue(StoryGrouper.cosine(v[0], v[2]) < SAME_STORY_THRESHOLD, "A not ~C")

        val groups = grouper.group(window, ::noAffinity)
        assertTrue(together(groups, "a", "b"))
        assertFalse(together(groups, "a", "c"), "A and C must not be grouped through B")
    }

    @Test
    fun articleWithoutSummaryIsMatchedOnHeadlineAlone() {
        val a =
            article("a", "outlet-a", headline, Duration.ZERO, summary = "Ash forced the airport to close on Monday.")
        val b = article("b", "outlet-b", headline, 1.hours)
        assertTrue(together(grouper.group(listOf(a, b) + background, ::noAffinity), "a", "b"))
    }

    @Test
    fun stemmerAndTermsFoldInflectionsAndDropStopwords() {
        val cases =
            listOf(
                "caresses" to "caress",
                "ponies" to "poni",
                "agreed" to "agre",
                "hopping" to "hop",
                "relational" to "relat",
                "generalization" to "gener",
                "acquires" to "acquir",
                "acquiring" to "acquir",
            )
        assertEquals(cases.map { it.second }, cases.map { TextFeatures.stem(it.first) })
        val terms = TextFeatures.terms("The Fed raises rates by 0.5%", null)
        assertEquals(setOf("fed", "rais", "rate", "0", "5"), terms.keys)
        assertEquals(TextFeatures.ENTITY_WEIGHT, terms.getValue("fed"))
        assertEquals(1.0, terms.getValue("rais"))
    }

    @Test
    fun decomposedAndComposedAccentsGiveTheSameTerms() {
        val nfd = "Pokémon fans"
        val nfc = "Pokémon fans"
        assertEquals(TextFeatures.terms(nfc, null), TextFeatures.terms(nfd, null))
        assertTrue("pokemon" in TextFeatures.terms(nfd, null).keys)
    }

    private fun noAffinity(outletId: String): Double = 0.0

    private fun together(
        groups: List<StoryGroup>,
        x: String,
        y: String,
    ): Boolean = groups.any { g -> g.members().map { it.id }.containsAll(listOf(x, y)) }

    private fun groupOf(
        groups: List<StoryGroup>,
        id: String,
    ): StoryGroup = groups.single { g -> g.members().any { it.id == id } }

    private fun article(
        id: String,
        outlet: String,
        title: String,
        age: Duration,
        summary: String? = null,
    ): Article {
        val at = NOW - age
        return Article(
            id = id,
            feedId = "$outlet-feed",
            outletId = outlet,
            url = "https://example.test/$id",
            title = title,
            summary = summary,
            imageUrl = null,
            publishedAt = at,
            firstSeenAt = at,
            topics = emptyList(),
        )
    }

    private companion object {
        val NOW = Instant.parse("2026-10-16T12:00:00Z")
    }
}
