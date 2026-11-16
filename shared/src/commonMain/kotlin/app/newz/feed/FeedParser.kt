package app.newz.feed

import app.newz.net.HostGuard
import app.newz.net.WebUrl
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
    /** A feed with thousands of items is cut to its newest [MAX_ITEMS] (the feed only shows 7 days). */
    const val MAX_ITEMS = 300

    /**
     * The longest summary kept (B35), cut on a word boundary. Full-text feeds put whole article
     * bodies in `<description>`; a card shows a few lines, and grouping reads only the opening.
     */
    const val MAX_SUMMARY_CHARS = 400

    /** [text] cut to at most [MAX_SUMMARY_CHARS] at the last space, with an ellipsis when cut. */
    internal fun capSummary(text: String): String {
        if (text.length <= MAX_SUMMARY_CHARS) return text
        val cut = text.lastIndexOf(' ', MAX_SUMMARY_CHARS).takeIf { it > 0 } ?: MAX_SUMMARY_CHARS
        return text.substring(0, cut).trimEnd() + "…"
    }

    private val PROLOG = Regex("^\\s*<\\?xml[^>]*\\?>")
    private val PROLOG_ENCODING = Regex("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']")

    // DOCTYPEs are removed so the platform XML parser never resolves an external entity (no XXE).
    private val DOCTYPE = Regex("<!DOCTYPE[^\\[>]*(\\[[\\s\\S]*?])?\\s*>", RegexOption.IGNORE_CASE)
    private val DECLARATION = Regex("<!(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
    private val ROOT = Regex("<([A-Za-z_][\\w.-]*:)?(rss|feed|RDF)\\b")
    private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val IMG_SRC_ATTR = imgAttribute("src")
    private val IMG_WIDTH_ATTR = imgAttribute("width")
    private val IMG_HEIGHT_ATTR = imgAttribute("height")

    /** Addresses that read like a tracking pixel: a pixel/beacon/spacer file, or a beacon path. */
    private val TRACKING_SRC =
        Regex(
            "(pixel|beacon|spacer|blank|1x1)[^/?#]*\\.(gif|png)([?#]|$)|/(pixel|beacon|track|tracking)(/|\\?|$)",
            RegexOption.IGNORE_CASE,
        )

    private fun imgAttribute(name: String) =
        Regex("\\s$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE)

    /**
     * Parses [bytes]. The charset comes from the XML prolog, else [contentTypeCharset] (the HTTP
     * header's), else UTF-8.
     */
    suspend fun parse(
        bytes: ByteArray,
        contentTypeCharset: String?,
    ): ParsedFeed {
        val xml = prepareXml(decode(bytes, contentTypeCharset))
        // A DOCTYPE the pattern could not remove whole leaves a declaration behind: refuse the feed
        // rather than let any parser see an entity definition (no XXE, no entity expansion).
        if (declaresEntities(xml)) throw FeedParseException("Feed declares a DTD or entities")
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
            items = newest(distinctGuids(channel.items.mapNotNull(::toItem))),
        )
    }

    /**
     * Items whose guid another item with a different link shares get their link as guid (B41).
     * The article id is a hash of outlet and guid, so a feed that stamps every item with, say,
     * the site's address would otherwise show as one card that changes on every refresh.
     */
    internal fun distinctGuids(items: List<ParsedItem>): List<ParsedItem> {
        val urlsByGuid = items.groupBy({ it.guid }) { it.url }.mapValues { (_, urls) -> urls.toSet() }
        return items.map { if ((urlsByGuid[it.guid]?.size ?: 0) > 1) it.copy(guid = it.url) else it }
    }

    /** At most [MAX_ITEMS] items per feed: the newest ones (undated count as oldest), in feed order. */
    internal fun newest(items: List<ParsedItem>): List<ParsedItem> {
        if (items.size <= MAX_ITEMS) return items
        val keep =
            items.indices
                .sortedWith(
                    compareByDescending<Int> { items[it].publishedAt != null }
                        .thenByDescending { items[it].publishedAt }
                        .thenBy { it },
                ).take(MAX_ITEMS)
                .toSet()
        return items.filterIndexed { i, _ -> i in keep }
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

    /**
     * True when `<!DOCTYPE` or `<!ENTITY` appears as markup, that is outside comments and CDATA
     * sections (B44). Quoted inside CDATA it is only text, as in a post about XXE, and refusing
     * it would reject the whole feed. An unterminated comment or CDATA section is not trusted to
     * hide anything: the rest of the document is checked as it is.
     */
    internal fun declaresEntities(xml: String): Boolean {
        var i = 0
        while (true) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) return false
            val skipTo =
                when {
                    xml.startsWith("<!--", lt) -> xml.indexOf("-->", lt + 4).let { if (it < 0) -1 else it + 3 }
                    xml.startsWith("<![CDATA[", lt) -> xml.indexOf("]]>", lt + 9).let { if (it < 0) -1 else it + 3 }
                    else -> null
                }
            when {
                skipTo == -1 -> return DECLARATION.containsMatchIn(xml.substring(lt + 1))

                skipTo != null -> i = skipTo

                // Plain comparisons, not a regex at each '<': on Android a regex match from an
                // offset can cost the length of the document, which made a 5,000-item feed crawl.
                xml.regionMatches(lt, "<!DOCTYPE", 0, 9, ignoreCase = true) ||
                    xml.regionMatches(lt, "<!ENTITY", 0, 8, ignoreCase = true) -> return true

                else -> i = lt + 1
            }
        }
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
        // Only http(s) links are kept: a `javascript:`, `intent:` or `file:` link is never stored.
        // A link to a Google host is dropped too (B25): opening it would tell Google, through
        // FeedBurner's redirect or YouTube, which article the user read.
        val url = item.link?.trim()?.takeIf { WebUrl.isWebUrl(it) && !HostGuard.isBlocked(it) } ?: return null
        val title = HtmlText.toPlainText(item.title.orEmpty())
        if (title.isEmpty()) return null
        val body = item.description ?: item.content
        return ParsedItem(
            guid = item.guid?.trim()?.ifEmpty { null } ?: url,
            url = url,
            title = title,
            summary = body?.let(HtmlText::toPlainText)?.ifEmpty { null }?.let(::capSummary),
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
        val tags = sequenceOf(item.content, item.description).filterNotNull().flatMap { IMG_TAG.findAll(it) }
        val pixels = tags.mapNotNull { tag -> attribute(tag.value, IMG_SRC_ATTR)?.takeIf { isPixel(tag.value, it) } }
        // RSS-Parser fills item.image from the description's first <img> itself, so the same
        // pixel test applies to it.
        val parsedImage =
            item.image?.takeUnless { image ->
                TRACKING_SRC.containsMatchIn(image) ||
                    pixels.any { it.trim() == image.trim() }
            }
        val firstPicture =
            tags
                .mapNotNull { tag ->
                    attribute(tag.value, IMG_SRC_ATTR)?.takeUnless { isPixel(tag.value, it) }
                }.firstOrNull()
        val candidates = sequenceOf(media?.url, parsedImage, enclosure?.url, firstPicture)
        return candidates
            .mapNotNull { normalizeImageUrl(it) }
            .firstOrNull()
    }

    /**
     * True when an `<img>` is a tracking pixel (B40): declared at 2 px or less, or with an
     * address that reads like a beacon. Loading one would tell its host which article the user
     * scrolled past, which is exactly what Newz promises not to allow.
     */
    private fun isPixel(
        tag: String,
        src: String,
    ): Boolean {
        val tiny =
            listOf(IMG_WIDTH_ATTR, IMG_HEIGHT_ATTR).any { attr ->
                attribute(tag, attr)
                    ?.trim()
                    ?.removeSuffix("px")
                    ?.toIntOrNull()
                    ?.let { it <= 2 } == true
            }
        return tiny || TRACKING_SRC.containsMatchIn(src)
    }

    private fun attribute(
        tag: String,
        pattern: Regex,
    ): String? =
        pattern
            .find(tag)
            ?.groupValues
            ?.drop(2)
            ?.firstOrNull { it.isNotEmpty() }

    private fun normalizeImageUrl(raw: String?): String? {
        val decoded = raw?.let(HtmlText::decodeEntities)?.trim().orEmpty()
        if (decoded.isEmpty()) return null
        val absolute = if (decoded.startsWith("//")) "https:$decoded" else decoded
        // A Google-hosted image is treated as no image; so is anything not absolute http(s).
        return absolute.takeUnless { HostGuard.isBlocked(it) }
    }
}
