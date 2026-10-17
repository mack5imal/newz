@file:UseSerializers(EpochMillisSerializer::class)

package app.newz.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/** Writes an [Instant] as epoch milliseconds, the timestamp form of preferences and backups. */
object EpochMillisSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.newz.store.EpochMillis", PrimitiveKind.LONG)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) = encoder.encodeLong(value.toEpochMilliseconds())

    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
}

/**
 * One cached article. [id] is [ArticleIds.of] its outlet and guid (or url). [publishedAt] is the feed's date,
 * or [firstSeenAt] when the feed gave none. Timestamps are kept to the millisecond.
 */
@Serializable
data class StoredArticle(
    val id: String,
    val feedId: String,
    val outletId: String,
    val guid: String,
    val url: String,
    val title: String,
    val summary: String?,
    val imageUrl: String?,
    val publishedAt: Instant,
    val firstSeenAt: Instant,
    val topics: List<String>,
)

/** An article the user hid, with just enough to list it under "Hidden" and to undo it. */
@Serializable
data class HiddenArticle(
    val articleId: String,
    val hiddenAt: Instant,
    val title: String,
    val outletId: String,
    val url: String,
)

/** A saved article: a full copy, never pruned by the 7-day cache window. */
@Serializable
data class SavedArticle(
    val savedAt: Instant,
    val article: StoredArticle,
)

/** One reading-history row: this article (outlet + topics) was opened. Nothing else is kept. */
@Serializable
data class HistoryEntry(
    val articleId: String,
    val outletId: String,
    val topics: List<String>,
    val openedAt: Instant,
)

/** A feed the user added by URL. [kind] is a [app.newz.feed.FeedKind] name. */
@Serializable
data class CustomFeed(
    val id: String,
    val url: String,
    val title: String,
    val siteUrl: String?,
    val kind: String,
    val addedAt: Instant,
)

/** A feed's refresh health. [failingSince] is set from the first failure after a success. */
data class FeedStatus(
    val feedId: String,
    val lastOkAt: Instant?,
    val failingSince: Instant?,
    val lastError: String?,
)

/**
 * Stable article ids: a 64-bit FNV-1a hash of the outlet id plus the guid (or the url when there is
 * no guid). Scoping by outlet keeps two outlets that reuse the same non-global guid apart.
 */
object ArticleIds {
    private const val OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME = 0x100000001b3L
    private const val SEPARATOR = '\u0000'

    fun of(
        outletId: String,
        guid: String?,
        url: String,
    ): String {
        val key = outletId + SEPARATOR + (guid?.takeIf { it.isNotBlank() } ?: url)
        var hash = OFFSET_BASIS
        for (byte in key.encodeToByteArray()) {
            hash = (hash xor (byte.toLong() and 0xff)) * PRIME
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }
}
