package app.newz.filter

/**
 * Whole-word keyword matching (SPEC S9). Text and keywords are folded the same way: lowercased,
 * Latin-1 / Latin Extended-A accents removed, combining marks dropped and curly apostrophes made
 * straight. Text is split into tokens of letters and digits (an apostrophe after a letter or digit
 * stays in the token, so "trump's" is one token).
 *
 * A keyword matches a run of consecutive tokens equal to its own tokens, where the last token may
 * also carry a possessive or plural ending: `k`, `k's`, `ks`, `kes` or `k'`. So "Trump" matches
 * "Trump's" and "Trumps" but not "trumpet"; "Elon Musk" matches "elon musk's plan" but not "Musk ox".
 */
class KeywordMatcher(
    keywords: List<String>,
) {
    private val compiled: List<Pair<String, List<String>>> =
        keywords.mapNotNull { keyword -> tokens(keyword).takeIf { it.isNotEmpty() }?.let { keyword to it } }

    fun matches(text: String): Boolean = firstMatch(text) != null

    /** The first keyword (as given) that [text] contains, in keyword order; null when none does. */
    fun firstMatch(text: String): String? {
        if (compiled.isEmpty()) return null
        val words = tokens(text)
        return compiled.firstOrNull { (_, keyTokens) -> containsPhrase(words, keyTokens) }?.first
    }

    companion object {
        private val ENDINGS = listOf("", "'s", "s", "es", "'")

        /** Folds [text] for matching: lowercase, accents and combining marks gone, apostrophes straight. */
        fun normalize(text: String): String {
            val lower = text.lowercase()
            val out = StringBuilder(lower.length)
            for (c in lower) {
                when {
                    c in APOSTROPHES -> out.append('\'')
                    c in COMBINING_MARKS -> Unit
                    else -> out.append(FOLDS[c] ?: c)
                }
            }
            return out.toString()
        }

        /** The tokens of [text] after [normalize]. */
        fun tokens(text: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            for (c in normalize(text)) {
                if (c.isLetterOrDigit() || (c == '\'' && current.isNotEmpty())) {
                    current.append(c)
                } else if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) result += current.toString()
            return result
        }

        private fun containsPhrase(
            words: List<String>,
            phrase: List<String>,
        ): Boolean {
            val last = phrase.size - 1
            for (start in 0..words.size - phrase.size) {
                var ok = true
                for (i in 0 until last) {
                    if (words[start + i] != phrase[i]) {
                        ok = false
                        break
                    }
                }
                if (ok && lastWordMatches(words[start + last], phrase[last])) return true
            }
            return false
        }

        private fun lastWordMatches(
            token: String,
            keyword: String,
        ): Boolean =
            token.startsWith(keyword) &&
                ENDINGS.any { ending ->
                    token.length == keyword.length + ending.length &&
                        token.endsWith(ending) &&
                        (ending != "es" || takesEs(keyword))
                }

        /**
         * The word endings English adds "es" after (B43). Elsewhere "es" is part of another word:
         * muting "Tim" must not hide "times", nor "rat" hide "rates".
         */
        private val ES_STEMS = listOf("s", "x", "z", "ch", "sh", "o")

        /**
         * True when "es" may follow [keyword]. A two-letter word ending in s, such as "US", is left
         * out: "uses" is far likelier than a plural of it, while "bus" and "gas" keep theirs.
         */
        private fun takesEs(keyword: String): Boolean =
            ES_STEMS.any { keyword.endsWith(it) } && !(keyword.length < 3 && keyword.endsWith("s"))

        private val APOSTROPHES = setOf('’', '‘', 'ʼ')

        private val COMBINING_MARKS = '̀'..'ͯ'

        /**
         * The common-code stand-in for NFKD + dropping combining marks, for the lowercase letters of
         * Latin-1 Supplement and Latin Extended-A (text is lowercased first).
         */
        private val FOLDS: Map<Char, String> =
            buildMap {
                fun fold(
                    from: String,
                    to: String,
                ) = from.forEach { put(it, to) }
                fold("àáâãäåāăą", "a")
                fold("æ", "ae")
                fold("çćĉċč", "c")
                fold("ďđð", "d")
                fold("èéêëēĕėęě", "e")
                fold("ĝğġģ", "g")
                fold("ĥħ", "h")
                fold("ìíîïĩīĭįı", "i")
                fold("ĳ", "ij")
                fold("ĵ", "j")
                fold("ķĸ", "k")
                fold("ĺļľŀł", "l")
                fold("ñńņňŋ", "n")
                fold("ŉ", "'n")
                fold("òóôõöøōŏő", "o")
                fold("œ", "oe")
                fold("ŕŗř", "r")
                fold("śŝşšſ", "s")
                fold("ß", "ss")
                fold("ţťŧ", "t")
                fold("þ", "th")
                fold("ùúûüũūŭůűų", "u")
                fold("ŵ", "w")
                fold("ýÿŷ", "y")
                fold("źżž", "z")
            }
    }
}
