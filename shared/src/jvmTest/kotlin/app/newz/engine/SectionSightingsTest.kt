package app.newz.engine

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogFeed
import app.newz.catalog.Outlet
import app.newz.catalog.PaywallStatus
import app.newz.feed.ParsedItem
import app.newz.store.ArticleStore
import app.newz.store.FeedSighting
import app.newz.store.UserPrefs
import app.newz.store.createSqlDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * B31 (review R6): one outlet's story in two section feeds is one article row. It must stay in
 * the feed while either section, and that section's topic, is followed and not muted.
 */
class SectionSightingsTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val catalog =
        Catalog(
            topics = emptyList(),
            outlets = listOf(Outlet("nyt", "NYT", "nyt.example", PaywallStatus.NONE, 1.0)),
            feeds =
                listOf(
                    CatalogFeed("nyt-world", "nyt", "World", "https://nyt.example/world.xml", listOf("world")),
                    CatalogFeed("nyt-us", "nyt", "U.S.", "https://nyt.example/us.xml", listOf("us")),
                ),
        )
    private val both =
        UserPrefs(
            followedTopics = listOf("world", "us"),
            followedOutlets = mapOf("nyt" to setOf("nyt-world", "nyt-us")),
        )
    private val store = ArticleStore(createSqlDriver())
    private val story = ParsedItem("g1", "https://nyt.example/story", "Harbor talks resume", null, null, now - 1.hours)

    private fun seenInBothSections() {
        store.upsert("nyt-world", "nyt", listOf("world"), listOf(story), now - 1.hours)
        store.upsert("nyt-us", "nyt", listOf("us"), listOf(story), now)
    }

    private fun titles(prefs: UserPrefs): List<String> =
        FeedComposer.compose(FeedSnapshot.read(store, prefs), catalog, now).map { it.lead.title }

    @Test
    fun everySightingIsRecorded() {
        seenInBothSections()
        assertEquals(1, store.articles().size)
        val id = store.articles().single().id
        assertEquals(
            listOf(FeedSighting("nyt-us", listOf("us")), FeedSighting("nyt-world", listOf("world"))),
            store.articleSightings().getValue(id),
        )
    }

    @Test
    fun unfollowingTheFirstSectionKeepsTheStory() {
        seenInBothSections()
        assertEquals(listOf("Harbor talks resume"), titles(both))
        val usOnly = both.copy(followedOutlets = mapOf("nyt" to setOf("nyt-us")))
        assertEquals(listOf("Harbor talks resume"), titles(usOnly))
    }

    @Test
    fun unfollowingOrMutingTheFirstSectionsTopicKeepsTheStory() {
        seenInBothSections()
        assertEquals(listOf("Harbor talks resume"), titles(both.copy(followedTopics = listOf("us"))))
        assertEquals(listOf("Harbor talks resume"), titles(both.copy(mutedTopics = mapOf("world" to now))))
    }

    @Test
    fun unfollowingBothSectionsHidesTheStory() {
        seenInBothSections()
        assertEquals(emptyList(), titles(both.copy(followedOutlets = mapOf("nyt" to emptySet()))))
    }

    @Test
    fun pruningAnArticleDropsItsSightings() {
        store.upsert("nyt-world", "nyt", listOf("world"), listOf(story.copy(publishedAt = now - 9.days)), now - 9.days)
        store.pruneOlderThan(ArticleStore.ARTICLE_RETENTION, now)
        assertTrue(store.articles().isEmpty())
        assertTrue(store.articleSightings().isEmpty())
    }
}
