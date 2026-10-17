@file:UseSerializers(EpochMillisSerializer::class)

package app.newz.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Instant

/** How much of a followed topic the feed shows. */
enum class Level { NORMAL, MORE, MUCH_MORE }

/** Article text size. */
enum class TextSize { SMALL, DEFAULT, LARGE, EXTRA_LARGE }

/** A muted keyword. [text] is trimmed; uniqueness is case-insensitive. */
@Serializable
data class MutedKeyword(
    val text: String,
    val mutedAt: Instant,
)

/**
 * Everything the user chose, stored on the device only (and in their own backup file). Change it
 * through the pure functions in PrefsOps.kt.
 *
 * - [followedTopics] keeps the user's order; a topic missing from [topicLevels] is [Level.NORMAL].
 * - [mutedTopics] and [removedOutlets] mark a topic or outlet as off without forgetting its follow
 *   state, so restoring it brings back exactly what was followed before.
 * - [followedOutlets] maps an outlet id to the ids of its enabled section feeds.
 * - [zip] and [localMetroId] drive the Local topic (SPEC S29).
 */
@Serializable
data class UserPrefs(
    val onboarded: Boolean = false,
    val followedTopics: List<String> = emptyList(),
    val topicLevels: Map<String, Level> = emptyMap(),
    val customTopics: List<String> = emptyList(),
    val mutedTopics: Map<String, Instant> = emptyMap(),
    val mutedKeywords: List<MutedKeyword> = emptyList(),
    val followedOutlets: Map<String, Set<String>> = emptyMap(),
    val removedOutlets: Map<String, Instant> = emptyMap(),
    val paywallFlags: Map<String, Instant> = emptyMap(),
    val paywallFilterOn: Boolean = true,
    val readingHistoryOn: Boolean = true,
    val textSize: TextSize = TextSize.DEFAULT,
    val zip: String? = null,
    val localMetroId: String? = null,
)
