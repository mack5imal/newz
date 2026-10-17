package app.newz.feed

import app.newz.net.HostGuard

/**
 * Cleans item HTML before display: removes every `<script>`, and every embed-like element
 * whose src/srcset/data/poster points at a host [HostGuard] blocks (YouTube iframes,
 * googleusercontent images, …). Publisher-hosted media is kept.
 */
object HtmlSanitizer {
    private val SCRIPT_BLOCK = Regex("<script\\b[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE)
    private val SCRIPT_TAG = Regex("</?script\\b[^>]*>", RegexOption.IGNORE_CASE)

    // Quote-aware, so a `>` inside a quoted attribute value does not end the tag early. As in
    // the HTML tokenizer, a quote opens a value only right after `=`; any other quote (alt=it's)
    // is plain text. Each alternative starts differently, so matching stays linear.
    private val MEDIA_TAG =
        Regex(
            "<(img|iframe|embed|object|video|audio|source)\\b" +
                "((?:=\\s*\"[^\"]*\"|=\\s*'[^']*'|=(?!\\s*[\"'])|[^=>])*)>",
            RegexOption.IGNORE_CASE,
        )
    private val ATTRIBUTE =
        Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val URL_ATTRIBUTES = setOf("src", "srcset", "data", "poster", "data-src", "data-srcset")
    private val VOID_ELEMENTS = setOf("img", "embed", "source")

    fun sanitize(html: String): String {
        var out = SCRIPT_TAG.replace(SCRIPT_BLOCK.replace(html, ""), "")
        var searchFrom = 0
        while (true) {
            val match = MEDIA_TAG.find(out, searchFrom) ?: break
            if (!hasBlockedUrl(match.groupValues[2])) {
                searchFrom = match.range.last + 1
                continue
            }
            val name = match.groupValues[1].lowercase()
            val selfClosing = match.groupValues[2].trimEnd().endsWith("/")
            var end = match.range.last + 1
            if (name !in VOID_ELEMENTS && !selfClosing) {
                val close = Regex("</$name\\s*>", RegexOption.IGNORE_CASE).find(out, end)
                if (close != null) end = close.range.last + 1
            }
            out = out.removeRange(match.range.first, end)
            searchFrom = match.range.first
        }
        return out
    }

    private fun hasBlockedUrl(attributes: String): Boolean =
        ATTRIBUTE.findAll(attributes).any { attr ->
            val name = attr.groupValues[1].lowercase()
            if (name !in URL_ATTRIBUTES) return@any false
            val value =
                HtmlText.decodeEntities(
                    attr.groupValues
                        .drop(3)
                        .firstOrNull { it.isNotEmpty() }
                        .orEmpty(),
                )
            urlsOf(name, value).any(::isBlockedReference)
        }

    private fun urlsOf(
        name: String,
        value: String,
    ): List<String> =
        if (name.endsWith("srcset")) {
            value.split(',').mapNotNull {
                it
                    .trim()
                    .split(Regex("\\s+"))
                    .firstOrNull()
                    ?.ifEmpty { null }
            }
        } else {
            listOf(value.trim())
        }

    /** Relative references stay on the publisher's host; absolute ones must pass the guard. */
    private fun isBlockedReference(url: String): Boolean {
        val absolute = if (url.startsWith("//")) "https:$url" else url
        val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(absolute)
        return hasScheme && HostGuard.isBlocked(absolute)
    }
}
