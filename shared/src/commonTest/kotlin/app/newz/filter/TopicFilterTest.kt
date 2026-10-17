package app.newz.filter

import app.newz.feed.Article
import app.newz.store.UserPrefs
import app.newz.store.followTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TopicFilterTest {
    private val t0 = Instant.fromEpochMilliseconds(1_760_000_000_000)

    private fun article(
        id: String,
        vararg topics: String,
    ) = Article(
        id = id,
        feedId = "f",
        outletId = "o",
        url = "https://example.com/$id",
        title = "Title $id",
        summary = null,
        imageUrl = null,
        publishedAt = t0,
        firstSeenAt = t0,
        topics = topics.toList(),
    )

    private val feed =
        listOf(
            article("1", "world"),
            article("2", "tech"),
            article("3", "world", "politics"),
            article("4", "sports"),
            article("5", "tech", "custom:rust"),
        )

    @Test
    fun selectingATopicKeepsOnlyArticlesCarryingIt() {
        assertEquals(listOf("1", "3"), applyTopicFilter(feed, setOf("world")).map { it.id })
        assertEquals(listOf("5"), applyTopicFilter(feed, setOf("custom:rust")).map { it.id })
        assertEquals(listOf("2", "4", "5"), applyTopicFilter(feed, setOf("tech", "sports")).map { it.id })
        assertEquals(emptyList(), applyTopicFilter(feed, setOf("science")))
    }

    @Test
    fun clearingReturnsTheOriginalIdsInOrder() {
        val filtered = applyTopicFilter(feed, setOf("tech"))
        val cleared = applyTopicFilter(feed, emptySet())
        assertEquals(listOf("2", "5"), filtered.map { it.id })
        assertEquals(feed.map { it.id }, cleared.map { it.id })
        assertEquals(feed, cleared)
    }

    @Test
    fun filteringNeverChangesPrefs() {
        val prefs = UserPrefs().followTopic("world").followTopic("tech")
        val before = prefs.copy()
        applyTopicFilter(feed, setOf("world"))
        applyTopicFilter(feed, emptySet())
        assertEquals(before, prefs)
    }
}
