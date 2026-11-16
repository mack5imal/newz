package app.newz.store

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.newz.db.NewzDatabase
import app.newz.feed.ParsedItem
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * An install made before Better pictures (schema version 1, kept as databases/1.db) opens on the
 * current schema with every row it had, and gains the page_image table (B5).
 */
class MigrationTest {
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000)

    private fun SqlDriver.userVersion(): Long =
        executeQuery(
            null,
            "PRAGMA user_version",
            { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) },
            0,
        ).value ?: 0

    @Test
    fun versionOneDatabaseUpgradesAndKeepsItsRows() {
        val file = Files.createTempFile("newz-v1", ".db").toFile()
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        val url = "jdbc:sqlite:${file.path}"

        // As Android left it: user_version 1, with an article, a hide and a save in it.
        JdbcSqliteDriver(url, Properties()).use { v1 ->
            v1.execute(null, "PRAGMA user_version = 1", 0)
            seedArticleHiddenAndSaved(v1)
        }

        JdbcSqliteDriver(url, Properties(), NewzDatabase.Schema).use { driver ->
            assertEquals(NewzDatabase.Schema.version, driver.userVersion())
            val store = ArticleStore(driver)
            assertEquals("A", store.articles().single().title)
            assertEquals(1, store.listHidden().size)
            assertEquals(1, store.listSaved().size)
            assertNull(store.pageImage("https://e.example/a"))
            store.recordPageImage("https://e.example/a", "out-1", "https://e.example/pic.jpg", now)
            assertNotNull(store.pageImage("https://e.example/a"))
        }
        file.delete()
    }

    /**
     * B31: an install from before article_feed keeps its rows, and each cached article starts
     * with the one feed its row remembers, so the feed filter sees no change.
     */
    @Test
    fun versionTwoDatabaseUpgradesAndBackfillsSightings() {
        val file = Files.createTempFile("newz-v2", ".db").toFile()
        File("src/commonMain/sqldelight/databases/2.db").copyTo(file, overwrite = true)
        val url = "jdbc:sqlite:${file.path}"
        JdbcSqliteDriver(url, Properties()).use { v2 ->
            v2.execute(null, "PRAGMA user_version = 2", 0)
            seedArticleHiddenAndSaved(v2)
        }

        JdbcSqliteDriver(url, Properties(), NewzDatabase.Schema).use { driver ->
            assertEquals(NewzDatabase.Schema.version, driver.userVersion())
            val store = ArticleStore(driver)
            val article = store.articles().single()
            assertEquals(1, store.listHidden().size)
            assertEquals(1, store.listSaved().size)
            assertEquals(listOf(FeedSighting("feed-1", listOf("tech"))), store.articleSightings()[article.id])
            // A later sighting in another section adds to it.
            val again = ParsedItem("g", "https://e.example/a", "A", null, null, now)
            store.upsert("feed-2", "out-1", listOf("science"), listOf(again), now)
            assertEquals(2, store.articleSightings().getValue(article.id).size)
        }
        file.delete()
    }

    /**
     * An article, a hide and a save written with the queries an older install had. [ArticleStore]
     * itself is not used: its upsert also writes tables the old schema lacks.
     */
    private fun seedArticleHiddenAndSaved(driver: SqlDriver) {
        val db = NewzDatabase(driver)
        val at = now.toEpochMilliseconds()
        val url = "https://e.example/a"
        val id = ArticleIds.of("out-1", "g", url)
        db.articleQueries.insertIfAbsent(id, "feed-1", "out-1", "g", url, "A", null, null, at, at, "[\"tech\"]")
        db.hiddenQueries.insert(id, at, "A", "out-1", url)
        db.savedQueries.insert(id, at, "feed-1", "out-1", "g", url, "A", null, null, at, at, "[\"tech\"]")
    }
}
