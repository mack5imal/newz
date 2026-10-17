package app.newz.store

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class BackupCodecTest {
    private val t = Instant.fromEpochMilliseconds(1_760_000_123_456)

    /** A device: its own in-memory database and its own preferences file. */
    private class Device {
        val articles = ArticleStore(createSqlDriver())
        val prefs =
            PreferencesStore.create(
                Files.createTempDirectory("newz-prefs").resolve(PreferencesStore.FILE_NAME).toString(),
            )
        val restorer = BackupRestorer(articles, prefs)

        suspend fun state(): BackupContents =
            BackupContents(
                prefs.prefs.first(),
                articles.listHidden(),
                articles.listSaved(),
                articles.listCustomFeeds(),
                articles.listHistory(),
            )

        suspend fun export(includeHistory: Boolean): String =
            BackupCodec.export(
                prefs.prefs.first(),
                articles.listHidden(),
                articles.listSaved(),
                articles.listCustomFeeds(),
                articles.listHistory(),
                includeHistory,
            )
    }

    private val fullPrefs =
        UserPrefs(
            onboarded = true,
            followedTopics = listOf("world", "tech", "local"),
            topicLevels = mapOf("tech" to Level.MUCH_MORE, "world" to Level.MORE),
            customTopics = listOf("rust lang", "mars rover"),
            mutedTopics = mapOf("sports" to t - 2.days),
            mutedKeywords = listOf(MutedKeyword("Kardashian", t - 3.days), MutedKeyword("crypto", t - 1.hours)),
            followedOutlets = mapOf("npr" to setOf("npr-news", "npr-science"), "bbc" to setOf("bbc-world")),
            removedOutlets = mapOf("foxnews" to t - 4.days),
            paywallFlags = mapOf("wsj" to t - 5.days),
            paywallFilterOn = false,
            readingHistoryOn = false,
            textSize = TextSize.EXTRA_LARGE,
            zip = "02134",
            localMetroId = "boston",
            betterPictures = true,
        )

    private fun article(n: Int) =
        StoredArticle(
            id = "id$n",
            feedId = "feed$n",
            outletId = "outlet$n",
            guid = "guid$n",
            url = "https://example.com/$n",
            title = "Title \"$n\" — ünïcode",
            summary = if (n % 2 == 0) null else "Summary $n",
            imageUrl = if (n % 2 == 0) "https://img.example.com/$n.jpg" else null,
            publishedAt = t - n.hours,
            firstSeenAt = t - n.hours + 1.hours,
            topics = listOf("tech", "science").take(n % 3),
        )

    private suspend fun populated(): Device {
        val d = Device()
        d.prefs.replace(fullPrefs)
        d.articles.hide(article(1), t - 1.days)
        d.articles.hide(article(2), t - 2.days)
        d.articles.save(article(3), t - 3.hours)
        d.articles.save(article(4), t - 4.hours)
        val blog = CustomFeed("c1", "https://blog.example/feed", "Blog", "https://blog.example", "ATOM", t)
        d.articles.addCustomFeed(blog)
        d.articles.addCustomFeed(CustomFeed("c2", "https://other.example/rss", "Other", null, "RSS", t - 1.days))
        d.articles.recordOpen("id5", "outlet5", listOf("world"), t - 5.hours)
        d.articles.recordOpen("id6", "outlet6", emptyList(), t - 6.hours)
        return d
    }

    @Test
    fun roundTripWithoutHistoryReproducesEveryField() =
        runTest {
            val source = populated()
            val json = source.export(includeHistory = false)
            assertTrue(json.contains("\"format\": \"newz-backup\""))
            assertTrue(json.contains("\"version\": 1"))
            assertFalse(json.contains("\"history\""))

            val contents = BackupCodec.import(json).getOrThrow()
            assertNull(contents.history)
            val target = Device()
            target.restorer.restore(contents).getOrThrow()

            val expected = source.state().copy(history = emptyList())
            assertEquals(expected, target.state())
            val restoredPrefs = target.prefs.prefs.first()
            assertEquals(fullPrefs, restoredPrefs)
            assertEquals("02134", restoredPrefs.zip)
            assertTrue(restoredPrefs.betterPictures)
        }

    @Test
    fun backupAndPrefsWrittenBeforeBetterPicturesReadItAsOff() =
        runTest {
            val json = populated().export(includeHistory = false)
            assertTrue(json.contains("\"betterPictures\": true"))
            val old = json.replace(Regex(",\\s*\"betterPictures\": true"), "")
            assertFalse(old.contains("betterPictures"))
            val contents = BackupCodec.import(old).getOrThrow()
            assertEquals(fullPrefs.copy(betterPictures = false), contents.prefs)

            val oldPrefs = PreferencesStore.encode(fullPrefs).replace(Regex(",\\s*\"betterPictures\":true"), "")
            assertFalse(oldPrefs.contains("betterPictures"))
            assertEquals(fullPrefs.copy(betterPictures = false), PreferencesStore.decode(oldPrefs))
        }

    @Test
    fun roundTripWithHistoryReproducesEveryField() =
        runTest {
            val source = populated()
            val contents = BackupCodec.import(source.export(includeHistory = true)).getOrThrow()
            assertEquals(2, contents.history?.size)
            val target = Device()
            target.restorer.restore(contents).getOrThrow()
            assertEquals(source.state(), target.state())
        }

    @Test
    fun importReplacesAndIsIdempotent() =
        runTest {
            val json = populated().export(includeHistory = true)
            val target = Device()
            target.prefs.replace(UserPrefs().muteKeyword("extra", t))
            target.articles.hide(article(9), t)
            target.articles.recordOpen("id9", "outlet9", listOf("x"), t)

            target.restorer.restore(BackupCodec.import(json).getOrThrow()).getOrThrow()
            val once = target.state()
            assertFalse(once.prefs.mutedKeywords.any { it.text == "extra" })
            assertFalse(once.hidden.any { it.articleId == "id9" })
            assertFalse(once.history.orEmpty().any { it.articleId == "id9" })

            target.restorer.restore(BackupCodec.import(json).getOrThrow()).getOrThrow()
            assertEquals(once, target.state())
        }

    @Test
    fun badFilesReturnErrorsAndLeaveStateUnchanged() =
        runTest {
            val target = populated()
            val before = target.state()
            val good = target.export(includeHistory = true)
            val cases =
                mapOf(
                    "{not json" to BackupError.Malformed::class,
                    "[1, 2]" to BackupError.Malformed::class,
                    good.replace("\"newz-backup\"", "\"other-app\"") to BackupError.WrongFormat::class,
                    good.replace("\"version\": 1", "\"version\": 99") to BackupError.UnsupportedVersion::class,
                    good.replace("\"zip\": \"02134\"", "\"zip\": [1]") to BackupError.Malformed::class,
                    good.dropLast(20) to BackupError.Malformed::class,
                    good.replace("\"saved\"", "\"unsaved\"") to BackupError.Malformed::class,
                    "" to BackupError.Malformed::class,
                    (" ".repeat(BackupCodec.MAX_BYTES.toInt()) + good) to BackupError.Malformed::class,
                )
            for ((text, error) in cases) {
                val result = BackupCodec.import(text)
                assertTrue(result.isFailure, text.take(40))
                assertEquals(error, result.exceptionOrNull()!!::class)
                result.onSuccess { target.restorer.restore(it) }
                assertEquals(before, target.state())
            }
        }

    @Test
    fun failedRestoreRollsBackTablesAndPrefs() =
        runTest {
            val target = populated()
            val before = target.state()
            val duplicate = SavedArticle(t, article(7))
            val bad = BackupContents(UserPrefs(zip = "10001"), saved = listOf(duplicate, duplicate))
            val result = target.restorer.restore(bad)
            assertTrue(result.isFailure)
            assertEquals(before, target.state())
        }

    @Test
    fun countsSummariseTheBackup() =
        runTest {
            val contents = BackupCodec.import(populated().export(includeHistory = false)).getOrThrow()
            assertEquals(
                BackupCounts(sources = 4, topics = 5, mutedKeywords = 2, hidden = 2, saved = 2, history = 0),
                contents.counts(),
            )
        }
}
