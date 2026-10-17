package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.CatalogLoader
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.catalog.Topic
import app.newz.store.UserPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CatalogSearchTest {
    private val catalog = CatalogLoader.load()
    private val t0 = Instant.fromEpochMilliseconds(1_760_000_000_000)

    @Test
    fun outletIsFoundByNameAndByDomain() {
        val byName = CatalogSearch.search("new york times", catalog, UserPrefs())
        assertEquals(
            "nyt",
            byName.outlets
                .first()
                .outlet.id,
        )
        val byDomain = CatalogSearch.search("nytimes.com", catalog, UserPrefs())
        assertEquals(
            "nyt",
            byDomain.outlets
                .first()
                .outlet.id,
        )
        val partialDomain = CatalogSearch.search("NYTIMES", catalog, UserPrefs())
        assertTrue(partialDomain.outlets.any { it.outlet.id == "nyt" })
    }

    @Test
    fun solarFindsClimateThroughItsDescription() {
        val results = CatalogSearch.search("solar", catalog, UserPrefs())
        assertEquals(
            "climate",
            results.topics
                .first()
                .topic.id,
        )
        assertEquals("solar", results.customTopicOffer)
    }

    @Test
    fun unknownWordOnlyOffersACustomTopic() {
        val results = CatalogSearch.search("  axolotl  ", catalog, UserPrefs())
        assertEquals(emptyList(), results.outlets)
        assertEquals(emptyList(), results.topics)
        assertEquals("axolotl", results.customTopicOffer)
    }

    @Test
    fun existingTopicNameIsNotOfferedAndOverlongWordsAreNot() {
        assertNull(CatalogSearch.search("Climate", catalog, UserPrefs()).customTopicOffer)
        assertNull(CatalogSearch.search("x".repeat(61), catalog, UserPrefs()).customTopicOffer)
        assertEquals("x".repeat(60), CatalogSearch.search("x".repeat(60), catalog, UserPrefs()).customTopicOffer)
        assertEquals(SearchResults.EMPTY, CatalogSearch.search("   ", catalog, UserPrefs()))
    }

    @Test
    fun matchingIgnoresCaseAndAccents() {
        val results = CatalogSearch.search("CLÍMATE", catalog, UserPrefs())
        assertEquals(
            "climate",
            results.topics
                .first()
                .topic.id,
        )
        assertNull(results.customTopicOffer)
        val small = smallCatalog()
        assertEquals(
            "cafe",
            CatalogSearch
                .search("cafe", small, UserPrefs())
                .outlets
                .single()
                .outlet.id,
        )
    }

    @Test
    fun outletsRankByMatchQualityThenPopularity() {
        val ids = CatalogSearch.search("sun", smallCatalog(), UserPrefs()).outlets.map { it.outlet.id }
        // Name prefixes (by popularity), then a word prefix, then a domain-only substring.
        assertEquals(listOf("sun-big", "sun-small", "daily-sun", "grid"), ids)
    }

    @Test
    fun topicsCarryFollowingAndMutedFlagsAndRemovedOutletsAreStillFound() {
        val prefs =
            UserPrefs(
                followedTopics = listOf("climate"),
                mutedTopics = mapOf("space" to t0),
                followedOutlets = mapOf("sun-big" to setOf("sun-big-energy")),
                removedOutlets = mapOf("sun-small" to t0),
            )
        val small = smallCatalog()
        val climate = CatalogSearch.search("climate", small, prefs).topics.single()
        assertTrue(climate.following)
        val space = CatalogSearch.search("space", small, prefs).topics.single()
        assertTrue(space.muted)
        val suns = CatalogSearch.search("sun", small, prefs).outlets.associateBy { it.outlet.id }
        assertTrue(suns.getValue("sun-big").following)
        assertTrue(suns.getValue("sun-small").removed)
        assertEquals(2, suns.getValue("sun-big").feedCount)
        assertEquals("climate", suns.getValue("sun-big").primaryTopic?.id)
    }

    private fun smallCatalog(): Catalog {
        fun outlet(
            id: String,
            name: String,
            domain: String,
            popularity: Double,
        ) = Outlet(id, name, domain, PaywallStatus.NONE, popularity)
        return Catalog(
            topics =
                listOf(
                    Topic("climate", "Climate", "Solar, wind, batteries and the grid"),
                    Topic("space", "Space", "Rockets and planets"),
                ),
            outlets =
                listOf(
                    outlet("grid", "Grid Watch", "gridsunrise.example", 0.9),
                    outlet("sun-small", "Sun Weekly", "sunweekly.example", 0.2),
                    outlet("daily-sun", "The Daily Sun", "dailysun.example", 0.95),
                    outlet("sun-big", "Sun & Grid", "sungrid.example", 0.8),
                    outlet("cafe", "Café Times", "cafetimes.example", 0.1),
                ),
            feeds =
                listOf(
                    CatalogFeed(
                        "sun-big-energy",
                        "sun-big",
                        "Energy",
                        "https://sungrid.example/energy",
                        listOf("climate"),
                    ),
                    CatalogFeed(
                        "sun-big-space",
                        "sun-big",
                        "Space",
                        "https://sungrid.example/space",
                        listOf("space", "climate"),
                    ),
                ),
        )
    }
}
