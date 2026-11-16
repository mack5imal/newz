package app.newz.store

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.newz.db.NewzDatabase
import app.newz.feed.ParsedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ArticleStoreTest {
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000)
    private val driver: SqlDriver = createSqlDriver()
    private var historyOn = true
    private val store = ArticleStore(driver) { historyOn }

    /** The same database, for writing rows the store itself would refuse to write. */
    private fun db() = NewzDatabase(driver)

    private fun item(
        guid: String,
        publishedAt: Instant? = now,
        title: String = "Title $guid",
    ) = ParsedItem(guid, "https://example.com/$guid", title, "Summary", null, publishedAt)

    private fun insert(
        guid: String,
        publishedAt: Instant?,
        seenAt: Instant = now,
    ): StoredArticle {
        store.upsert("feed-1", "outlet-1", listOf("tech"), listOf(item(guid, publishedAt)), seenAt)
        return store.article(ArticleIds.of("outlet-1", guid, "https://example.com/$guid"))!!
    }

    @Test
    fun upsertingTheSameItemTwiceKeepsOneRow() {
        assertEquals(1, store.upsert("feed-1", "outlet-1", listOf("tech"), listOf(item("a")), now))
        val later = now + 5.minutes
        assertEquals(0, store.upsert("feed-1", "outlet-1", listOf("tech"), listOf(item("a", title = "New")), later))
        val rows = store.articles()
        assertEquals(1, rows.size)
        assertEquals("New", rows.single().title)
        assertEquals(now, rows.single().firstSeenAt)
    }

    @Test
    fun aDateFarInTheFutureIsTreatedAsUndated() {
        // B42: a year typo or a wrong zone must not pin the story to the top.
        assertEquals(now, insert("future", publishedAt = now + 300.days).publishedAt)
        // A few minutes ahead is ordinary clock skew and is kept.
        assertEquals(now + 10.minutes, insert("skew", publishedAt = now + 10.minutes).publishedAt)
    }

    @Test
    fun aFutureDateSeenAgainDoesNotOverwriteTheStoredOne() {
        insert("later", publishedAt = now - 1.days)
        store.upsert("feed-1", "outlet-1", listOf("tech"), listOf(item("later", now + 300.days)), now + 1.minutes)
        assertEquals(
            now - 1.days,
            store.article(ArticleIds.of("outlet-1", "later", "https://example.com/later"))!!.publishedAt,
        )
    }

    @Test
    fun aFutureDatedRowStoredEarlierIsPrunedByWhenItWasFirstSeen() {
        // Rows written before the clamp can still carry a future date.
        val at = now - 8.days
        val id = ArticleIds.of("outlet-1", "old-future", "https://example.com/old-future")
        db().articleQueries.insertIfAbsent(
            id,
            "feed-1",
            "outlet-1",
            "old-future",
            "https://example.com/old-future",
            "T",
            null,
            null,
            (now + 300.days).toEpochMilliseconds(),
            at.toEpochMilliseconds(),
            "[]",
        )
        store.pruneOlderThan(ArticleStore.ARTICLE_RETENTION, now)
        assertNull(store.article(id))
    }

    @Test
    fun missingPublishedAtFallsBackToFirstSeen() {
        val article = insert("undated", publishedAt = null)
        assertEquals(now, article.publishedAt)
        assertEquals(now, article.firstSeenAt)
        assertEquals(listOf("tech"), article.topics)
    }

    @Test
    fun idIsStableHashOfOutletAndGuidElseUrl() {
        assertEquals(ArticleIds.of("o", "g", "https://a"), ArticleIds.of("o", "g", "https://b"))
        assertEquals(ArticleIds.of("o", null, "https://a"), ArticleIds.of("o", " ", "https://a"))
        assertEquals(16, ArticleIds.of("o", "g", "u").length)
        assertFalse(ArticleIds.of("o", "g1", "u") == ArticleIds.of("o", "g2", "u"))
        assertFalse(ArticleIds.of("o1", "g", "u") == ArticleIds.of("o2", "g", "u"))
    }

    @Test
    fun sameGuidFromTwoOutletsKeepsTwoRows() {
        val first = ParsedItem("12345", "https://one.example/a", "One title", "Summary", null, now)
        val second = ParsedItem("12345", "https://two.example/b", "Two title", "Summary", null, now)
        assertEquals(1, store.upsert("feed-1", "outlet-1", listOf("tech"), listOf(first), now))
        assertEquals(1, store.upsert("feed-2", "outlet-2", listOf("world"), listOf(second), now))
        val rows = store.articles().associateBy { it.outletId }
        assertEquals(2, rows.size)
        assertEquals("One title", rows.getValue("outlet-1").title)
        assertEquals("https://one.example/a", rows.getValue("outlet-1").url)
        assertEquals("Two title", rows.getValue("outlet-2").title)
        assertEquals("https://two.example/b", rows.getValue("outlet-2").url)
    }

    @Test
    fun pruneDropsOldArticlesButKeepsSavedAndSevenDayOld() {
        val old = insert("old", now - 7.days - 1.minutes)
        val oldSaved = insert("old-saved", now - 7.days - 1.minutes)
        val sevenDays = insert("seven", now - 7.days)
        store.save(oldSaved, now)

        store.pruneOlderThan(7.days, now)

        val ids = store.articles().map { it.id }.toSet()
        assertFalse(old.id in ids)
        assertTrue(oldSaved.id in ids)
        assertTrue(sevenDays.id in ids)
        assertEquals(listOf(oldSaved), store.listSaved().map { it.article })
    }

    @Test
    fun savingTwiceGivesOneRow() {
        val a = insert("s", now)
        store.save(a, now)
        store.save(a, now + 1.minutes)
        assertEquals(listOf(SavedArticle(now, a)), store.listSaved())
        assertTrue(store.isSaved(a.id))
        store.unsave(a.id)
        assertEquals(emptyList(), store.listSaved())
    }

    @Test
    fun hiddenRowsOlderThanThirtyDaysArePruned() {
        val old = insert("h-old", now)
        val recent = insert("h-new", now)
        store.hide(old, now - 30.days - 1.minutes)
        store.hide(recent, now - 29.days)
        store.hide(recent, now)

        store.pruneHidden(now)

        val hidden = store.listHidden()
        assertEquals(listOf(recent.id), hidden.map { it.articleId })
        assertEquals(now - 29.days, hidden.single().hiddenAt)
        assertEquals(recent.title, hidden.single().title)
        store.unhide(recent.id)
        assertEquals(emptyList(), store.listHidden())
    }

    @Test
    fun recordOpenStoresOutletAndTopicsOnlyWhenHistoryIsOn() {
        assertTrue(store.recordOpen("a1", "outlet-1", listOf("tech", "science"), now))
        assertEquals(listOf(HistoryEntry("a1", "outlet-1", listOf("tech", "science"), now)), store.listHistory())

        historyOn = false
        assertFalse(store.recordOpen("a2", "outlet-2", listOf("world"), now))
        assertEquals(1, store.listHistory().size)

        historyOn = true
        store.recordOpen("a3", "outlet-3", emptyList(), now)
        assertEquals(2, store.listHistory().size)
        store.clearHistory()
        assertEquals(emptyList(), store.listHistory())
    }

    @Test
    fun historyOlderThanTheRetentionIsPruned() {
        // B34: history no longer grows for ever.
        store.recordOpen("old", "outlet-1", emptyList(), now - ArticleStore.HISTORY_RETENTION - 1.minutes)
        store.recordOpen("kept", "outlet-1", emptyList(), now - ArticleStore.HISTORY_RETENTION + 1.minutes)
        store.pruneHistory(now)
        assertEquals(listOf("kept"), store.listHistory().map { it.articleId })
    }

    @Test
    fun historyTableHasOnlyTheFourAllowedColumns() {
        val columns =
            driver
                .executeQuery(null, "PRAGMA table_info(history)", { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(1)!!
                    QueryResult.Value(names)
                }, 0)
                .value
        assertEquals(listOf("articleId", "outletId", "topics", "openedAt"), columns)
    }

    @Test
    fun feedStatusTracksFirstFailureAndClearsOnSuccess() {
        assertNull(store.feedStatus("f"))
        store.markFeedOk("f", now)
        store.markFeedFailed("f", "HTTP 500", now + 1.minutes)
        store.markFeedFailed("f", "timeout", now + 2.minutes)
        assertEquals(FeedStatus("f", now, now + 1.minutes, "timeout"), store.feedStatus("f"))
        store.markFeedOk("f", now + 3.minutes)
        assertEquals(listOf(FeedStatus("f", now + 3.minutes, null, null)), store.feedStatuses())
    }

    @Test
    fun customFeedsAddReplaceAndRemove() {
        val feed = CustomFeed("c1", "https://blog.example/feed", "Blog", null, "RSS", now)
        store.addCustomFeed(feed)
        store.addCustomFeed(feed.copy(title = "Blog 2"))
        assertEquals(listOf(feed.copy(title = "Blog 2")), store.listCustomFeeds())
        store.removeCustomFeed("c1")
        assertEquals(emptyList(), store.listCustomFeeds())
    }
}
