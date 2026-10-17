package app.newz.feed

import kotlin.time.Instant

/**
 * One article as the feed sees it. [publishedAt] is the feed's date, or null when it gave none
 * (then [firstSeenAt] stands in, SPEC S5). [topics] are the feed's topics plus any custom keyword
 * topic ids (`custom:<word>`) the article matched; [primaryTopic] is the feed's first topic.
 */
data class Article(
    val id: String,
    val feedId: String,
    val outletId: String,
    val url: String,
    val title: String,
    val summary: String?,
    val imageUrl: String?,
    val publishedAt: Instant?,
    val firstSeenAt: Instant,
    val topics: List<String>,
    val primaryTopic: String? = topics.firstOrNull(),
) {
    /** The date the article is aged and ordered by: [publishedAt], else [firstSeenAt]. */
    val date: Instant get() = publishedAt ?: firstSeenAt
}
