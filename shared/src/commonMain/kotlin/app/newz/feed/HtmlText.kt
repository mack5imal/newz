package app.newz.feed

/** HTML entity decoding and tag stripping for feed titles and summaries. */
internal object HtmlText {
    private const val LATIN1_NAMES =
        "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr " +
            "deg plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest " +
            "Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml " +
            "ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig " +
            "agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml " +
            "eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml"

    private val NAMED: Map<String, Int> =
        buildMap {
            LATIN1_NAMES.split(' ').forEachIndexed { i, name -> put(name, 160 + i) }
            putAll(
                mapOf(
                    "amp" to 38,
                    "lt" to 60,
                    "gt" to 62,
                    "quot" to 34,
                    "apos" to 39,
                    "OElig" to 338,
                    "oelig" to 339,
                    "Scaron" to 352,
                    "scaron" to 353,
                    "Yuml" to 376,
                    "fnof" to 402,
                    "circ" to 710,
                    "tilde" to 732,
                    "ensp" to 8194,
                    "emsp" to 8195,
                    "thinsp" to 8201,
                    "zwnj" to 8204,
                    "zwj" to 8205,
                    "lrm" to 8206,
                    "rlm" to 8207,
                    "ndash" to 8211,
                    "mdash" to 8212,
                    "lsquo" to 8216,
                    "rsquo" to 8217,
                    "sbquo" to 8218,
                    "ldquo" to 8220,
                    "rdquo" to 8221,
                    "bdquo" to 8222,
                    "dagger" to 8224,
                    "Dagger" to 8225,
                    "bull" to 8226,
                    "hellip" to 8230,
                    "permil" to 8240,
                    "prime" to 8242,
                    "Prime" to 8243,
                    "lsaquo" to 8249,
                    "rsaquo" to 8250,
                    "euro" to 8364,
                    "trade" to 8482,
                    "larr" to 8592,
                    "rarr" to 8594,
                    "minus" to 8722,
                ),
            )
        }

    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,31});")
    private val SCRIPT_OR_STYLE = Regex("<(script|style)\\b[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE)
    private val COMMENT = Regex("<!--[\\s\\S]*?-->")
    private val TAG = Regex("</?([A-Za-z][A-Za-z0-9]*)\\b[^>]*>")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val INLINE_TAGS =
        setOf(
            "a",
            "abbr",
            "b",
            "bdi",
            "bdo",
            "cite",
            "code",
            "em",
            "font",
            "i",
            "kbd",
            "mark",
            "q",
            "s",
            "small",
            "span",
            "strong",
            "sub",
            "sup",
            "time",
            "u",
            "var",
            "wbr",
        )

    /** Decodes named and numeric HTML entities in one pass (so `&amp;#8217;` becomes `&#8217;`). */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match ->
            val body = match.groupValues[1]
            val codePoint =
                when {
                    body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)
                    body.startsWith("#") -> body.substring(1).toIntOrNull()
                    else -> NAMED[body] ?: return@replace match.value
                }
            codePointToString(numericFixup(codePoint))
        }
    }

    /** Removes tags, scripts, styles and comments, then decodes entities and collapses whitespace. */
    fun toPlainText(html: String): String {
        val withoutBlocks = COMMENT.replace(SCRIPT_OR_STYLE.replace(html, " "), " ")
        val withoutTags =
            TAG.replace(withoutBlocks) { match ->
                if (match.groupValues[1].lowercase() in INLINE_TAGS) "" else " "
            }
        return WHITESPACE.replace(decodeEntities(withoutTags), " ").trim()
    }

    /** HTML5 maps numeric references in 0x80–0x9F to windows-1252 and invalid ones to U+FFFD. */
    private fun numericFixup(codePoint: Int?): Int =
        when {
            codePoint == null || codePoint == 0 || codePoint > 0x10FFFF -> 0xFFFD
            codePoint in 0xD800..0xDFFF -> 0xFFFD
            codePoint in 0x80..0x9F -> Charsets.windows1252HighChar(codePoint)
            else -> codePoint
        }

    private fun codePointToString(codePoint: Int): String =
        if (codePoint < 0x10000) {
            codePoint.toChar().toString()
        } else {
            val v = codePoint - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
}
