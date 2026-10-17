package app.newz.store

import kotlin.time.Instant

/** The longest muted keyword, in characters after trimming. */
const val MAX_KEYWORD_LENGTH = 60

private val ZIP = Regex("\\d{5}")

/** Returns [raw] trimmed when it is a valid muted keyword (1..60 chars, not blank), else null. */
fun normalizeKeyword(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_KEYWORD_LENGTH }

/**
 * Mutes [raw]. Throws [IllegalArgumentException] when [normalizeKeyword] rejects it; check with
 * [normalizeKeyword] first. Muting a keyword already muted (ignoring case) changes nothing.
 */
fun UserPrefs.muteKeyword(
    raw: String,
    now: Instant,
): UserPrefs {
    val text = requireNotNull(normalizeKeyword(raw)) { "A muted keyword must be 1-$MAX_KEYWORD_LENGTH characters" }
    if (mutedKeywords.any { it.text.equals(text, ignoreCase = true) }) return this
    return copy(mutedKeywords = mutedKeywords + MutedKeyword(text, now))
}

/** Unmutes [text], ignoring case and surrounding whitespace. */
fun UserPrefs.unmuteKeyword(text: String): UserPrefs {
    val key = text.trim()
    return copy(mutedKeywords = mutedKeywords.filterNot { it.text.equals(key, ignoreCase = true) })
}

/** Removes [outletId] from the feed. Its followed sections are kept for [restoreOutlet]. */
fun UserPrefs.removeOutlet(
    outletId: String,
    now: Instant,
): UserPrefs = if (outletId in removedOutlets) this else copy(removedOutlets = removedOutlets + (outletId to now))

/** Undoes [removeOutlet]: the outlet's earlier follow state applies again. */
fun UserPrefs.restoreOutlet(outletId: String): UserPrefs = copy(removedOutlets = removedOutlets - outletId)

/** Marks [outletId] as paywalled (a user correction, SPEC S12). */
fun UserPrefs.flagPaywall(
    outletId: String,
    now: Instant,
): UserPrefs = if (outletId in paywallFlags) this else copy(paywallFlags = paywallFlags + (outletId to now))

fun UserPrefs.unflagPaywall(outletId: String): UserPrefs = copy(paywallFlags = paywallFlags - outletId)

/** The level of [topicId]; [Level.NORMAL] when none was set. */
fun UserPrefs.levelOf(topicId: String): Level = topicLevels[topicId] ?: Level.NORMAL

/** NORMAL -> MORE -> MUCH_MORE; MUCH_MORE stays. */
fun UserPrefs.raiseLevel(topicId: String): UserPrefs {
    val next =
        when (levelOf(topicId)) {
            Level.NORMAL -> Level.MORE
            Level.MORE, Level.MUCH_MORE -> Level.MUCH_MORE
        }
    return setLevel(topicId, next)
}

fun UserPrefs.setLevel(
    topicId: String,
    level: Level,
): UserPrefs = copy(topicLevels = topicLevels + (topicId to level))

/** Appends [topicId] to the followed topics; following it again keeps its place. */
fun UserPrefs.followTopic(topicId: String): UserPrefs =
    if (topicId in followedTopics) this else copy(followedTopics = followedTopics + topicId)

/** Stops following [topicId] and forgets its level. */
fun UserPrefs.unfollowTopic(topicId: String): UserPrefs =
    copy(followedTopics = followedTopics - topicId, topicLevels = topicLevels - topicId)

/** Mutes [topicId] without unfollowing it, so [restoreTopic] brings back the same follow state. */
fun UserPrefs.muteTopic(
    topicId: String,
    now: Instant,
): UserPrefs = if (topicId in mutedTopics) this else copy(mutedTopics = mutedTopics + (topicId to now))

fun UserPrefs.restoreTopic(topicId: String): UserPrefs = copy(mutedTopics = mutedTopics - topicId)

/** Sets the Local zip (five digits) and the metro it resolved to, if any. */
fun UserPrefs.setZip(
    zip: String,
    metroId: String?,
): UserPrefs {
    require(ZIP.matches(zip)) { "A zip code is five digits" }
    return copy(zip = zip, localMetroId = metroId)
}

fun UserPrefs.clearZip(): UserPrefs = copy(zip = null, localMetroId = null)
