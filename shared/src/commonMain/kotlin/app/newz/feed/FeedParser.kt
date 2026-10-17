package app.newz.feed

import app.newz.net.HostGuard
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import kotlin.time.Instant

enum class FeedKind { RSS, ATOM, RDF }

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val kind: FeedKind,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val guid: String,
    val url: String,
    val title: String,
    val summary: String?,
    val imageUrl: String?,
    val publishedAt: Instant?,
)

/** The bytes were not a feed RSS-Parser could read. */
class FeedParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Decodes and parses RSS 2.0, Atom and RDF (RSS 1.0) feeds into [ParsedFeed]. */
object FeedParser {
    private val PROLOG = Regex("^\\s*<\\?xml[^>]*\\?>")
    private val PROLOG_ENCODING = Regex("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']")

    // DOCTYPEs are removed so the platform XML parser never resolves an external entity (no XXE).
    private val DOCTYPE = Regex("<!DOCTYPE[^\\[>]*(\\[[\\s\\S]*?])?\\s*>", RegexOption.IGNORE_CASE)
    private val ROOT = Regex("<([A-Za-z_][\\w.-]*:)?(rss|feed|RDF)\\b")
    private val IMG_SRC =
        Regex("<img\\b[^>]*?\\bsrc\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE)

    /**
     * Parses [bytes]. The charset comes from the XML prolog, else [contentTypeCharset] (the HTTP
     * header's), else UTF-8.
     */
    suspend fun parse(
        bytes: ByteArray,
        contentTypeCharset: String?,
    ): ParsedFeed {
        val xml = prepareXml(decode(bytes, contentTypeCharset))
        val kind = detectKind(xml) ?: throw FeedParseException("Not an RSS, Atom or RDF document")
        val channel =
            try {
                RssParser().parse(xml)
            } catch (e: Exception) {
                throw FeedParseException("Feed could not be parsed", e)
            }
        return ParsedFeed(
            title = HtmlText.toPlainText(channel.title.orEmpty()),
            siteUrl = channel.link?.trim()?.ifEmpty { null },
            kind = kind,
            items = channel.items.mapNotNull(::toItem),
        )
    }

    internal fun decode(
        bytes: ByteArray,
        contentTypeCharset: String?,
    ): String {
        val head = bytes.copyOf(minOf(bytes.size, 256)).decodeToString()
        val declared = PROLOG_ENCODING.find(head.removePrefix("\uFEFF"))?.groupValues?.get(1)
        val label =
            when {
                declared != null && Charsets.isSupported(declared) -> declared
                contentTypeCharset != null && Charsets.isSupported(contentTypeCharset) -> contentTypeCharset
                else -> "utf-8"
            }
        return Charsets.decode(bytes, label)
    }

    /** The text is already decoded, so the prolog's encoding must not be applied a second time. */
    private fun prepareXml(text: String): String = DOCTYPE.replace(PROLOG.replace(text.removePrefix("\uFEFF"), ""), "")

    private fun detectKind(xml: String): FeedKind? =
        when (ROOT.find(xml)?.groupValues?.get(2)) {
            "rss" -> FeedKind.RSS
            "feed" -> FeedKind.ATOM
            "RDF" -> FeedKind.RDF
            else -> null
        }

    private fun toItem(item: RssItem): ParsedItem? {
        val url = item.link?.trim()?.ifEmpty { null } ?: return null
        val title = HtmlText.toPlainText(item.title.orEmpty())
        if (title.isEmpty()) return null
        val body = item.description ?: item.content
        return ParsedItem(
            guid = item.guid?.trim()?.ifEmpty { null } ?: url,
            url = url,
            title = title,
            summary = body?.let(HtmlText::toPlainText)?.ifEmpty { null },
            imageUrl = imageOf(item),
            publishedAt = FeedDates.parse(item.pubDate),
        )
    }

    /** media:content, then media:thumbnail (RSS-Parser's image), then an image enclosure, then the first `<img>`. */
    private fun imageOf(item: RssItem): String? {
        val media =
            item.rawMediaContent?.takeIf {
                val type = it.type
                val medium = it.medium
                (type == null && medium == null) || type?.startsWith("image/") == true || medium == "image"
            }
        val enclosure = item.rawEnclosure?.takeIf { it.type?.startsWith("image/") == true }
        val candidates =
            sequenceOf(media?.url, item.image, enclosure?.url) +
                sequenceOf(item.content, item.description).map { html ->
                    html
                        ?.let { IMG_SRC.find(it) }
                        ?.groupValues
                        ?.drop(2)
                        ?.firstOrNull { v -> v.isNotEmpty() }
                }
        return candidates
            .mapNotNull { normalizeImageUrl(it) }
            .firstOrNull()
    }

    private fun normalizeImageUrl(raw: String?): String? {
        val decoded = raw?.let(HtmlText::decodeEntities)?.trim().orEmpty()
        if (decoded.isEmpty()) return null
        val absolute = if (decoded.startsWith("//")) "https:$decoded" else decoded
        // A Google-hosted image is treated as no image; so is anything not absolute http(s).
        return absolute.takeUnless { HostGuard.isBlocked(it) }
    }
}
