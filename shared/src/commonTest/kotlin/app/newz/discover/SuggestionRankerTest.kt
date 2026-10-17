package app.newz.discover

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.CatalogLoader
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.catalog.Topic
import app.newz.engine.ReadingAffinity
import app.newz.store.HistoryEntry
import app.newz.store.UserPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SuggestionRankerTest {
    private val t0 = Instant.fromEpochMilliseconds(1_760_000_000_000)

    private val catalog =
        Catalog(
            topics =
                listOf(
                    Topic("tech", "Tech", ""),
                    Topic("sports", "Sports", ""),
                    Topic("science", "Science", ""),
                    Topic("climate", "Climate", ""),
                    Topic("food", "Food", ""),
                    Topic("space", "Space", ""),
                    Topic("local", "Local", ""),
                ),
            outlets =
                listOf(
                    outlet("mine", "My Tech Daily", 0.5),
                    outlet("tech-a", "Tech A", 0.5),
                    outlet("sports-a", "Sports A", 0.5),
                    outlet("tech-hi", "Tech Hi", 0.9),
                    outlet("tech-lo", "Tech Lo", 0.3),
                    outlet("both", "Both", 0.4),
                    outlet("food-only", "Food Only", 0.99),
                    outlet("gone", "Gone", 0.95),
                    outlet("sci-clim", "Sci Clim", 0.2),
                    outlet("space-only", "Space Only", 0.8),
                    outlet("metro", "Metro TV", 0.9),
                ),
            feeds =
                listOf(
                    feed("mine", "tech"),
                    feed("tech-a", "tech"),
                    feed("sports-a", "sports"),
                    feed("tech-hi", "tech"),
                    feed("tech-lo", "tech"),
                    feed("both", "tech", "science"),
                    feed("food-only", "food"),
                    feed("gone", "tech"),
                    feed("sci-clim", "science", "climate"),
                    feed("space-only", "space"),
                    CatalogFeed(
                        "metro-local",
                        "metro",
                        "News",
                        "https://metro.example/rss",
                        listOf("local", "tech"),
                        metroId = "m1",
                    ),
                ),
        )

    private fun outlet(
        id: String,
        name: String,
        popularity: Double,
    ) = Outlet(id, name, "$id.example", PaywallStatus.NONE, popularity)

    private fun feed(
        outletId: String,
        vararg topics: String,
    ) = CatalogFeed("$outletId-main", outletId, "Main", "https://$outletId.example/rss", topics.toList())

    private fun opens(
        topic: String,
        count: Int,
        outlet: String = "mine",
    ) = List(count) { HistoryEntry("$topic-$outlet-$it", outlet, listOf(topic), t0) }

    private val basePrefs =
        UserPrefs(
            followedTopics = listOf("tech", "sports"),
            removedOutlets = mapOf("gone" to t0),
            mutedTopics = mapOf("space" to t0),
        )

    @Test
    fun sourceInMostReadTopicOutranksEquallyPopularSourceInUnreadTopic() {
        val affinity = ReadingAffinity(opens("tech", 9) + opens("sports", 1, "sports-a"))
        val ids = SuggestionRanker.suggest(catalog, basePrefs, affinity).outlets.map { it.id }
        assertTrue(ids.indexOf("tech-a") < ids.indexOf("sports-a"), "$ids")
        val unread = ReadingAffinity(opens("tech", 9))
        val ids2 = SuggestionRanker.suggest(catalog, basePrefs, unread).outlets.map { it.id }
        assertTrue(ids2.indexOf("tech-a") < ids2.indexOf("sports-a"), "$ids2")
    }

    @Test
    fun atEqualAffinityTheMorePopularSourceRanksHigher() {
        val ids = SuggestionRanker.suggest(catalog, basePrefs, ReadingAffinity(opens("tech", 3))).outlets.map { it.id }
        assertTrue(ids.indexOf("tech-hi") < ids.indexOf("tech-a"), "$ids")
        assertTrue(ids.indexOf("tech-a") < ids.indexOf("tech-lo"), "$ids")
    }

    @Test
    fun removedOutletsMutedTopicsLocalAndFollowedOutletsAreNeverSuggested() {
        val prefs = basePrefs.copy(followedOutlets = mapOf("mine" to setOf("mine-main")))
        val affinity = ReadingAffinity(opens("space", 20) + opens("tech", 2))
        val suggestions = SuggestionRanker.suggest(catalog, prefs, affinity, outletLimit = 100, topicLimit = 100)
        val outletIds = suggestions.outlets.map { it.id }
        assertFalse("gone" in outletIds)
        assertFalse("space-only" in outletIds)
        assertFalse("mine" in outletIds)
        assertFalse("metro" in outletIds)
        val topicIds = suggestions.topics.map { it.id }
        assertFalse("space" in topicIds)
        assertFalse("local" in topicIds)
        assertFalse("tech" in topicIds)
    }

    @Test
    fun withHistoryOffRankingIsPopularityWithinFollowedTopics() {
        val prefs = basePrefs.copy(readingHistoryOn = false)
        val affinity = ReadingAffinity(opens("sports", 50, "sports-a") + opens("food", 50, "food-only"))
        val outlets = SuggestionRanker.suggest(catalog, prefs, affinity, outletLimit = 100).outlets
        // Ties (0.5) are broken by name.
        assertEquals(listOf("tech-hi", "mine", "sports-a", "tech-a", "both", "tech-lo"), outlets.map { it.id })
        assertTrue(outlets.none { it.reason.startsWith("Often in") })
        assertEquals("Because you follow Tech", outlets.first().reason)
        assertEquals(
            outlets.map {
                it.id
            },
            SuggestionRanker.suggest(catalog, prefs, ReadingAffinity.EMPTY, outletLimit = 100).outlets.map { it.id },
        )
    }

    @Test
    fun reasonsComeFromTheDesignCopy() {
        val prefs = UserPrefs(followedTopics = listOf("tech", "science"))
        val withHistory = ReadingAffinity(opens("tech", 4) + opens("science", 1))
        val byId =
            SuggestionRanker
                .suggest(
                    catalog,
                    prefs,
                    withHistory,
                    outletLimit = 100,
                ).outlets
                .associateBy { it.id }
        assertEquals("Often in the Tech articles you open", byId.getValue("tech-hi").reason)
        assertEquals("Tech", byId.getValue("tech-hi").topicName)
        val similarPrefs = prefs.copy(followedOutlets = mapOf("tech-a" to setOf("tech-a-main")))
        val noHistory =
            SuggestionRanker
                .suggest(
                    catalog,
                    similarPrefs,
                    ReadingAffinity.EMPTY,
                    outletLimit = 100,
                ).outlets
                .associateBy {
                    it.id
                }
        assertEquals("Similar to Tech A, which you follow", noHistory.getValue("tech-hi").reason)
        assertEquals("Because you follow Science", noHistory.getValue("sci-clim").reason)
        val twoTopics =
            SuggestionRanker.suggest(
                catalog,
                UserPrefs(followedTopics = listOf("tech", "science")),
                ReadingAffinity.EMPTY,
                outletLimit = 100,
            )
        assertEquals("Because you follow Tech and Science", twoTopics.outlets.single { it.id == "both" }.reason)
        val coverage =
            SuggestionRanker.suggest(
                catalog,
                UserPrefs(followedOutlets = mapOf("both" to setOf("both-main"))),
                ReadingAffinity.EMPTY,
                outletLimit = 100,
            )
        assertEquals(
            "Covers many of the same stories as your sources",
            coverage.outlets.single { it.id == "sci-clim" }.reason,
        )
    }

    @Test
    fun topicsAreScoredByCoOccurrenceWithFollowedTopics() {
        val prefs = UserPrefs(followedTopics = listOf("tech"))
        val topics = SuggestionRanker.suggest(catalog, prefs, ReadingAffinity.EMPTY).topics
        assertEquals(listOf("science"), topics.map { it.id })
        assertEquals("Because you follow Tech", topics.single().reason)
        val withHistory =
            SuggestionRanker
                .suggest(
                    catalog,
                    prefs,
                    ReadingAffinity(opens("food", 3, "food-only")),
                ).topics
        assertEquals("Often in the Food articles you open", withHistory.single { it.id == "food" }.reason)
        val historyOff =
            SuggestionRanker
                .suggest(
                    catalog,
                    prefs.copy(readingHistoryOn = false),
                    ReadingAffinity(opens("food", 3)),
                ).topics
        assertFalse(historyOff.any { it.id == "food" })
    }

    @Test
    fun afterOnboardingWithThreeTopicsThereAreAtLeastFiveOutletSuggestions() {
        val real = CatalogLoader.load()
        val prefs = UserPrefs(onboarded = true, followedTopics = listOf("world", "tech", "science"))
        val suggestions = SuggestionRanker.suggest(real, prefs, ReadingAffinity.EMPTY)
        assertTrue(suggestions.outlets.size >= 5, "${suggestions.outlets.size}")
        assertTrue(suggestions.topics.isNotEmpty())
        val scores = suggestions.outlets.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }
}
