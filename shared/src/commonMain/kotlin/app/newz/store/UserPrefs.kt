@file:UseSerializers(EpochMillisSerializer::class)

package app.newz.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Instant

/** How much of a topic the feed shows. */
enum class Level { NORMAL, MORE, MUCH_MORE }

/** The level's short name, as the Topics control shows it: "Normal", "More", "Much more". */
val Level.label: String
    get() =
        when (this) {
            Level.NORMAL -> "Normal"
            Level.MORE -> "More"
            Level.MUCH_MORE -> "Much more"
        }

/**
 * What the level does, in the ranker's terms ([app.newz.engine.FeedRanker.weight] 1, 2, 3): a
 * More topic comes up about twice as often as a Normal one, a Much more topic about three times,
 * while it has articles to show. Ranking only orders the feed, so "as often", not "as many".
 */
val Level.meaning: String
    get() =
        when (this) {
            Level.NORMAL -> "the usual share"
            Level.MORE -> "about 2× as often"
            Level.MUCH_MORE -> "about 3× as often"
        }

/** The name with its meaning, wherever a level is named in a sentence: "More (about 2× as often)". */
val Level.labelWithMeaning: String get() = "$label ($meaning)"

/** The caption under a topic's level control, e.g. "Science comes up about 2× as often as a Normal topic." */
fun Level.caption(topic: String): String =
    if (this == Level.NORMAL) {
        "$topic gets the usual share of your feed."
    } else {
        "$topic comes up $meaning as a Normal topic."
    }

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
