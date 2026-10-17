package app.newz.store

import app.cash.sqldelight.db.SqlDriver
import app.newz.db.NewzDatabase
import app.newz.feed.ParsedItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import app.newz.db.Article as ArticleRow
import app.newz.db.Feed_status as FeedStatusRow
import app.newz.db.Page_image as PageImageRow
import app.newz.db.Saved as SavedRow

/** Opens this platform's database: a file on Android and iOS, in memory on the JVM (tests). */
expect fun createSqlDriver(): SqlDriver

/**
 * The on-device article cache plus the user's hidden, saved, history and custom-feed tables, and
 * the pictures found on article pages.
 * [historyEnabled] is read on every [recordOpen]; when it returns false nothing is recorded.
 */
class ArticleStore(
    private val db: NewzDatabase,
    private val historyEnabled: () -> Boolean = { true },
) {
    constructor(driver: SqlDriver, historyEnabled: () -> Boolean = { true }) :
        this(NewzDatabase(driver), historyEnabled)

    /**
     * Inserts [items] from one feed, or refreshes the text of those already cached (same id), so
     * an item seen again is never duplicated and keeps its first-seen time. Returns how many were new.
     */
    fun upsert(
        feedId: String,
        outletId: String,
        topics: List<String>,
        items: List<ParsedItem>,
        now: Instant = Clock.System.now(),
    ): Int {
        val seenAt = now.toEpochMilliseconds()
        val topicsText = encodeTopics(topics)
        var added = 0
        db.transaction {
            for (item in items) {
                val id = ArticleIds.of(outletId, item.guid, item.url)
                val publishedAt = item.publishedAt?.toEpochMilliseconds()
                val existing = db.articleQueries.selectById(id).executeAsOneOrNull()
                if (existing == null) {
                    db.articleQueries.insertIfAbsent(
                        id = id,
                        feedId = feedId,
                        outletId = outletId,
                        guid = item.guid,
                        url = item.url,
                        title = item.title,
                        summary = item.summary,
                        imageUrl = item.imageUrl,
                        publishedAt = publishedAt ?: seenAt,
                        firstSeenAt = seenAt,
                        topics = topicsText,
                    )
                    added++
                } else if (publishedAt != null) {
                    db.articleQueries.refresh(item.url, item.title, item.summary, item.imageUrl, publishedAt, id)
                } else {
                    db.articleQueries.refreshKeepingDate(item.url, item.title, item.summary, item.imageUrl, id)
                }
            }
        }
        return added
    }

    fun article(id: String): StoredArticle? =
        db.articleQueries
            .selectById(id)
            .executeAsOneOrNull()
            ?.toModel()

    fun articles(): List<StoredArticle> =
        db.articleQueries
            .selectAll()
            .executeAsList()
            .map { it.toModel() }

    /** Drops cached articles published before `now - age`. Saved articles are never dropped. */
    fun pruneOlderThan(
        age: Duration,
        now: Instant = Clock.System.now(),
    ) {
        db.articleQueries.pruneBefore((now - age).toEpochMilliseconds())
    }

    // Hidden

    /** Hides [article]. Hiding it again keeps the first hide time. */
    fun hide(
        article: StoredArticle,
        hiddenAt: Instant = Clock.System.now(),
    ) {
        db.hiddenQueries.insertIfAbsent(
            article.id,
            hiddenAt.toEpochMilliseconds(),
            article.title,
            article.outletId,
            article.url,
        )
    }

    fun unhide(articleId: String) {
        db.hiddenQueries.delete(articleId)
    }

    fun listHidden(): List<HiddenArticle> =
        db.hiddenQueries.selectAll().executeAsList().map {
            HiddenArticle(it.articleId, Instant.fromEpochMilliseconds(it.hiddenAt), it.title, it.outletId, it.url)
        }

    /** Forgets hides older than [HIDDEN_RETENTION]. */
    fun pruneHidden(now: Instant = Clock.System.now()) {
        db.hiddenQueries.pruneBefore((now - HIDDEN_RETENTION).toEpochMilliseconds())
    }

    // Saved

    /** Saves a full copy of [article]. Saving it again keeps one row and the first save time. */
    fun save(
        article: StoredArticle,
        savedAt: Instant = Clock.System.now(),
    ) {
        with(article) {
            db.savedQueries.insertIfAbsent(
                id,
                savedAt.toEpochMilliseconds(),
                feedId,
                outletId,
                guid,
                url,
                title,
                summary,
                imageUrl,
                publishedAt.toEpochMilliseconds(),
                firstSeenAt.toEpochMilliseconds(),
                encodeTopics(topics),
            )
        }
    }

    fun unsave(articleId: String) {
        db.savedQueries.delete(articleId)
    }

    fun isSaved(articleId: String): Boolean = db.savedQueries.isSaved(articleId).executeAsOne() > 0

    fun listSaved(): List<SavedArticle> =
        db.savedQueries
            .selectAll()
            .executeAsList()
            .map { it.toModel() }

    // History

    /**
     * Records that an article (its outlet and topics) was opened, only while [historyEnabled].
     * Returns whether a row was written.
     */
    fun recordOpen(
        articleId: String,
        outletId: String,
        topics: List<String>,
        openedAt: Instant = Clock.System.now(),
    ): Boolean {
        if (!historyEnabled()) return false
        db.historyQueries.insert(articleId, outletId, encodeTopics(topics), openedAt.toEpochMilliseconds())
        return true
    }

    fun listHistory(): List<HistoryEntry> =
        db.historyQueries.selectAll().executeAsList().map {
            HistoryEntry(it.articleId, it.outletId, decodeTopics(it.topics), Instant.fromEpochMilliseconds(it.openedAt))
        }

    fun clearHistory() {
        db.historyQueries.deleteAll()
    }

    // Feed status

    fun markFeedOk(
        feedId: String,
        at: Instant = Clock.System.now(),
    ) {
        db.feedStatusQueries.upsert(feedId, at.toEpochMilliseconds(), null, null)
    }

    /** Records a failure; [FeedStatus.failingSince] keeps the time of the first failure in a row. */
    fun markFeedFailed(
        feedId: String,
        error: String,
        at: Instant = Clock.System.now(),
    ) {
        db.transaction {
            val current = db.feedStatusQueries.selectById(feedId).executeAsOneOrNull()
            db.feedStatusQueries.upsert(
                feedId,
                current?.lastOkAt,
                current?.failingSince ?: at.toEpochMilliseconds(),
                error,
            )
        }
    }

    fun feedStatus(feedId: String): FeedStatus? =
        db.feedStatusQueries
            .selectById(feedId)
            .executeAsOneOrNull()
            ?.toModel()

    fun feedStatuses(): List<FeedStatus> =
        db.feedStatusQueries
            .selectAll()
            .executeAsList()
            .map { it.toModel() }

    // Custom feeds

    /** Adds [feed], or replaces the one with the same id. */
    fun addCustomFeed(feed: CustomFeed) {
        with(feed) { db.customFeedQueries.upsert(id, url, title, siteUrl, kind, addedAt.toEpochMilliseconds()) }
    }

    fun removeCustomFeed(id: String) {
        db.customFeedQueries.delete(id)
    }

    fun listCustomFeeds(): List<CustomFeed> =
        db.customFeedQueries.selectAll().executeAsList().map {
            CustomFeed(it.id, it.url, it.title, it.siteUrl, it.kind, Instant.fromEpochMilliseconds(it.addedAt))
        }

    // Page images

    /** What reading [articleUrl]'s page found, or null when it was never read. */
    fun pageImage(articleUrl: String): PageImage? =
        db.pageImageQueries
            .selectByUrl(articleUrl)
            .executeAsOneOrNull()
            ?.toModel()

    /** Every page read for an article of [outletId]. */
    fun pageImagesOf(outletId: String): List<PageImage> =
        db.pageImageQueries
            .selectByOutlet(outletId)
            .executeAsList()
            .map { it.toModel() }

    /** Records what reading [articleUrl]'s page found; [imageUrl] null records that it found nothing. */
    fun recordPageImage(
        articleUrl: String,
        outletId: String,
        imageUrl: String?,
        fetchedAt: Instant = Clock.System.now(),
    ) {
        db.pageImageQueries.insert(articleUrl, outletId, imageUrl, fetchedAt.toEpochMilliseconds())
    }

    /** Forgets page reads older than [PAGE_IMAGE_RETENTION]. */
    fun prunePageImages(now: Instant = Clock.System.now()) {
        db.pageImageQueries.pruneBefore((now - PAGE_IMAGE_RETENTION).toEpochMilliseconds())
    }

    /**
     * Replaces the hidden, saved, custom-feed and history tables with the given rows in one
     * transaction (backup restore). Any failure, such as a duplicate id, rolls all of it back.
     */
    fun replaceUserData(
        hidden: List<HiddenArticle>,
        saved: List<SavedArticle>,
        customFeeds: List<CustomFeed>,
        history: List<HistoryEntry>,
    ) {
        db.transaction {
            db.hiddenQueries.deleteAll()
            db.savedQueries.deleteAll()
            db.customFeedQueries.deleteAll()
            db.historyQueries.deleteAll()
            for (h in hidden) {
                db.hiddenQueries.insert(h.articleId, h.hiddenAt.toEpochMilliseconds(), h.title, h.outletId, h.url)
            }
            for (s in saved) {
                with(s.article) {
                    db.savedQueries.insert(
                        id,
                        s.savedAt.toEpochMilliseconds(),
                        feedId,
                        outletId,
                        guid,
                        url,
                        title,
                        summary,
                        imageUrl,
                        publishedAt.toEpochMilliseconds(),
                        firstSeenAt.toEpochMilliseconds(),
                        encodeTopics(topics),
                    )
                }
            }
            for (f in customFeeds) {
                db.customFeedQueries.insert(f.id, f.url, f.title, f.siteUrl, f.kind, f.addedAt.toEpochMilliseconds())
            }
            for (e in history) {
                val openedAt = e.openedAt.toEpochMilliseconds()
                db.historyQueries.insert(e.articleId, e.outletId, encodeTopics(e.topics), openedAt)
            }
        }
    }

    companion object {
        /** How long the feed cache keeps an unsaved article. */
        val ARTICLE_RETENTION: Duration = 7.days

        /** How long a hide is remembered. */
        val HIDDEN_RETENTION: Duration = 30.days

        /**
         * How long a page read is remembered: longer than an article is cached, so a page is never
         * read twice, and long enough to spot an outlet's logo standing in for its pictures.
         */
        val PAGE_IMAGE_RETENTION: Duration = 30.days

        private val topicsSerializer = ListSerializer(String.serializer())

        private fun encodeTopics(topics: List<String>): String = Json.encodeToString(topicsSerializer, topics)

        private fun decodeTopics(text: String): List<String> = Json.decodeFromString(topicsSerializer, text)

        private fun ArticleRow.toModel() =
            StoredArticle(
                id,
                feedId,
                outletId,
                guid,
                url,
                title,
                summary,
                imageUrl,
                Instant.fromEpochMilliseconds(publishedAt),
                Instant.fromEpochMilliseconds(firstSeenAt),
                decodeTopics(topics),
            )

        private fun PageImageRow.toModel() =
            PageImage(articleUrl, outletId, imageUrl, Instant.fromEpochMilliseconds(fetchedAt))

        private fun FeedStatusRow.toModel() =
            FeedStatus(
                feedId,
                lastOkAt?.let(Instant::fromEpochMilliseconds),
                failingSince?.let(Instant::fromEpochMilliseconds),
                lastError,
            )

        private fun SavedRow.toModel() =
            SavedArticle(
                Instant.fromEpochMilliseconds(savedAt),
                StoredArticle(
                    articleId,
                    feedId,
                    outletId,
                    guid,
                    url,
                    title,
                    summary,
                    imageUrl,
                    Instant.fromEpochMilliseconds(publishedAt),
                    Instant.fromEpochMilliseconds(firstSeenAt),
                    decodeTopics(topics),
                ),
            )
    }
}
