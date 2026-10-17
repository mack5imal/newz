package app.newz.store

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.coroutines.cancellation.CancellationException

/** What a backup file holds. [history] is null when the backup was made without it. */
@Serializable
data class BackupContents(
    val prefs: UserPrefs,
    val hidden: List<HiddenArticle> = emptyList(),
    val saved: List<SavedArticle> = emptyList(),
    val customFeeds: List<CustomFeed> = emptyList(),
    val history: List<HistoryEntry>? = null,
) {
    /** Counts for the "Restored {n} sources, …" message. */
    fun counts(): BackupCounts =
        BackupCounts(
            sources = prefs.followedOutlets.size + customFeeds.size,
            topics = prefs.followedTopics.size + prefs.customTopics.size,
            mutedKeywords = prefs.mutedKeywords.size,
            hidden = hidden.size,
            saved = saved.size,
            history = history?.size ?: 0,
        )
}

/** [sources] counts followed outlets plus custom feeds; [topics] followed plus keyword topics. */
data class BackupCounts(
    val sources: Int,
    val topics: Int,
    val mutedKeywords: Int,
    val hidden: Int,
    val saved: Int,
    val history: Int,
)

/** Why a backup file could not be read. */
sealed class BackupError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Malformed(
        cause: Throwable? = null,
    ) : BackupError("This file is not a readable Newz backup", cause)

    class WrongFormat(
        val found: String?,
    ) : BackupError("This file is not a Newz backup (format: $found)")

    class UnsupportedVersion(
        val found: Int?,
    ) : BackupError("This backup's version ($found) is not supported")
}

/**
 * The backup file: `{"format":"newz-backup","version":1,"prefs":{…},"hidden":[…],"saved":[…],
 * "customFeeds":[…],"history":[…]}`, timestamps as epoch milliseconds. [UserPrefs] is written
 * whole, so every preference field (zip included) is always in the backup.
 */
object BackupCodec {
    const val FORMAT = "newz-backup"
    const val VERSION = 1

    @Serializable
    private data class BackupFile(
        val format: String,
        val version: Int,
        val prefs: UserPrefs,
        val hidden: List<HiddenArticle>,
        val saved: List<SavedArticle>,
        val customFeeds: List<CustomFeed>,
        val history: List<HistoryEntry>? = null,
    )

    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    /** History goes in only when [includeHistory] is true (and [history] is given). */
    fun export(
        prefs: UserPrefs,
        hidden: List<HiddenArticle>,
        saved: List<SavedArticle>,
        customFeeds: List<CustomFeed>,
        history: List<HistoryEntry>?,
        includeHistory: Boolean,
    ): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                format = FORMAT,
                version = VERSION,
                prefs = prefs,
                hidden = hidden,
                saved = saved,
                customFeeds = customFeeds,
                history = if (includeHistory) history.orEmpty() else null,
            ),
        )

    /** Reads a backup. Fails with a [BackupError]; nothing is changed by reading. */
    fun import(text: String): Result<BackupContents> {
        val root =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (e: IllegalArgumentException) {
                return Result.failure(BackupError.Malformed(e))
            } ?: return Result.failure(BackupError.Malformed())
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != FORMAT) return Result.failure(BackupError.WrongFormat(format))
        val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (version != VERSION) return Result.failure(BackupError.UnsupportedVersion(version))
        val file =
            try {
                json.decodeFromJsonElement(BackupFile.serializer(), root)
            } catch (e: IllegalArgumentException) {
                return Result.failure(BackupError.Malformed(e))
            }
        return Result.success(BackupContents(file.prefs, file.hidden, file.saved, file.customFeeds, file.history))
    }
}

/**
 * Applies a backup by replacing, not merging: preferences and the hidden, saved, custom-feed and
 * history tables become exactly the backup's (a backup without history clears it). The tables are
 * replaced in one SQL transaction; if that fails the previous preferences are written back, so on
 * any error nothing changes.
 */
class BackupRestorer(
    private val articles: ArticleStore,
    private val preferences: PreferencesStore,
) {
    suspend fun restore(contents: BackupContents): Result<BackupCounts> {
        val previous = preferences.prefs.first()
        preferences.replace(contents.prefs)
        return try {
            articles.replaceUserData(
                contents.hidden,
                contents.saved,
                contents.customFeeds,
                contents.history.orEmpty(),
            )
            Result.success(contents.counts())
        } catch (e: CancellationException) {
            withContext(NonCancellable) { preferences.replace(previous) }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) { preferences.replace(previous) }
            Result.failure(e)
        }
    }
}
