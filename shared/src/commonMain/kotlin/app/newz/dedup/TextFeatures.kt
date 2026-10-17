package app.newz.dedup

import app.newz.filter.KeywordMatcher

/**
 * Text features for story grouping (SPEC S15): headline and summary are split into words the way
 * [KeywordMatcher] does (same accent folding and apostrophe handling), English stopwords are
 * dropped, the rest are Porter-stemmed, and each term gets a weight. Names (a capitalised word
 * that does not start the headline or summary) and numbers weigh [ENTITY_WEIGHT]; other words 1.
 * The spec started names at x1.5; on the headline-only UCI fixture a flat x1.0 grouped more
 * same-story pairs at no extra false grouping (owner decision, phase 7 option 1), so it is 1.0.
 */
object TextFeatures {
    const val ENTITY_WEIGHT = 1.0

    /** Term frequencies of [headline] plus [summary] (when present), weighted as described above. */
    fun terms(
        headline: String,
        summary: String?,
    ): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        addTerms(headline, out)
        if (!summary.isNullOrBlank()) addTerms(summary, out)
        return out
    }

    private fun addTerms(
        text: String,
        out: MutableMap<String, Double>,
    ) {
        rawWords(text).forEachIndexed { index, raw ->
            val entity = raw.any { it.isDigit() } || (index > 0 && raw.first().isUpperCase())
            for (token in KeywordMatcher.tokens(raw)) {
                val word = stripApostrophes(token)
                if (word.isEmpty() || word in STOPWORDS || (word.length == 1 && !word[0].isDigit())) continue
                val term = stem(word)
                out[term] = (out[term] ?: 0.0) + if (entity) ENTITY_WEIGHT else 1.0
            }
        }
    }

    /** Words of [text] with their original case: runs of letters and digits, apostrophes inside. */
    internal fun rawWords(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text) {
            if (c.isLetterOrDigit() || (c in APOSTROPHES && current.isNotEmpty())) {
                current.append(c)
            } else if (current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    /** "trump's" -> "trump", "jesus'" -> "jesus", "don't" -> "dont". */
    private fun stripApostrophes(token: String): String {
        val base =
            when {
                token.endsWith("'s") -> token.dropLast(2)
                else -> token
            }
        return base.replace("'", "")
    }

    private val APOSTROPHES = setOf('\'', '’', '‘', 'ʼ')

    /** Porter (1980) stemmer, steps 1a to 5b. Words of two letters or fewer are returned as is. */
    fun stem(word: String): String {
        if (word.length <= 2 || word.any { it !in 'a'..'z' }) return word
        var w = step1a(word)
        w = step1b(w)
        w = step1c(w)
        w = replaceSuffix(w, STEP2, 0)
        w = replaceSuffix(w, STEP3, 0)
        w = step4(w)
        w = step5(w)
        return w
    }

    private fun isConsonant(
        w: CharSequence,
        i: Int,
    ): Boolean =
        when (w[i]) {
            'a', 'e', 'i', 'o', 'u' -> false
            'y' -> i == 0 || !isConsonant(w, i - 1)
            else -> true
        }

    /** Porter's m: the number of vowel-consonant sequences in [stem]. */
    private fun measure(stem: CharSequence): Int {
        var m = 0
        var i = 0
        val n = stem.length
        while (i < n && isConsonant(stem, i)) i++
        while (i < n) {
            while (i < n && !isConsonant(stem, i)) i++
            if (i >= n) break
            while (i < n && isConsonant(stem, i)) i++
            m++
        }
        return m
    }

    private fun hasVowel(stem: CharSequence): Boolean = stem.indices.any { !isConsonant(stem, it) }

    private fun endsDoubleConsonant(w: CharSequence): Boolean =
        w.length >= 2 && w[w.length - 1] == w[w.length - 2] && isConsonant(w, w.length - 1)

    /** Consonant-vowel-consonant ending where the last consonant is not w, x or y. */
    private fun endsCvc(w: CharSequence): Boolean {
        val n = w.length
        if (n < 3) return false
        return isConsonant(w, n - 3) && !isConsonant(w, n - 2) && isConsonant(w, n - 1) && w[n - 1] !in "wxy"
    }

    private fun step1a(w: String): String =
        when {
            w.endsWith("sses") -> w.dropLast(2)
            w.endsWith("ies") -> w.dropLast(2)
            w.endsWith("ss") -> w
            w.endsWith("s") -> w.dropLast(1)
            else -> w
        }

    private fun step1b(w: String): String {
        if (w.endsWith("eed")) return if (measure(w.dropLast(3)) > 0) w.dropLast(1) else w
        val stem =
            when {
                w.endsWith("ed") && hasVowel(w.dropLast(2)) -> w.dropLast(2)
                w.endsWith("ing") && hasVowel(w.dropLast(3)) -> w.dropLast(3)
                else -> return w
            }
        return when {
            stem.endsWith("at") || stem.endsWith("bl") || stem.endsWith("iz") -> stem + "e"
            endsDoubleConsonant(stem) && stem.last() !in "lsz" -> stem.dropLast(1)
            measure(stem) == 1 && endsCvc(stem) -> stem + "e"
            else -> stem
        }
    }

    private fun step1c(w: String): String = if (w.endsWith("y") && hasVowel(w.dropLast(1))) w.dropLast(1) + "i" else w

    private fun replaceSuffix(
        w: String,
        rules: List<Pair<String, String>>,
        minMeasure: Int,
    ): String {
        for ((suffix, replacement) in rules) {
            if (w.endsWith(suffix)) {
                val stem = w.dropLast(suffix.length)
                return if (measure(stem) > minMeasure) stem + replacement else w
            }
        }
        return w
    }

    private fun step4(w: String): String {
        for (suffix in STEP4) {
            if (w.endsWith(suffix)) {
                val stem = w.dropLast(suffix.length)
                if (measure(stem) <= 1) return w
                if (suffix == "ion" && stem.last() != 's' && stem.last() != 't') return w
                return stem
            }
        }
        return w
    }

    private fun step5(w: String): String {
        var out = w
        if (out.endsWith("e")) {
            val stem = out.dropLast(1)
            val m = measure(stem)
            if (m > 1 || (m == 1 && !endsCvc(stem))) out = stem
        }
        if (out.endsWith("ll") && measure(out) > 1) out = out.dropLast(1)
        return out
    }

    private val STEP2 =
        listOf(
            "ational" to "ate",
            "tional" to "tion",
            "enci" to "ence",
            "anci" to "ance",
            "izer" to "ize",
            "abli" to "able",
            "alli" to "al",
            "entli" to "ent",
            "eli" to "e",
            "ousli" to "ous",
            "ization" to "ize",
            "ation" to "ate",
            "ator" to "ate",
            "alism" to "al",
            "iveness" to "ive",
            "fulness" to "ful",
            "ousness" to "ous",
            "aliti" to "al",
            "iviti" to "ive",
            "biliti" to "ble",
        ).sortedByDescending { it.first.length }

    private val STEP3 =
        listOf(
            "icate" to "ic",
            "ative" to "",
            "alize" to "al",
            "iciti" to "ic",
            "ical" to "ic",
            "ful" to "",
            "ness" to "",
        ).sortedByDescending { it.first.length }

    private val STEP4 =
        listOf(
            "al",
            "ance",
            "ence",
            "er",
            "ic",
            "able",
            "ible",
            "ant",
            "ement",
            "ment",
            "ent",
            "ion",
            "ou",
            "ism",
            "ate",
            "iti",
            "ous",
            "ive",
            "ize",
        ).sortedByDescending { it.length }

    /** English stopwords (a short news-oriented list), compared after normalisation. */
    val STOPWORDS: Set<String> =
        """
        a about above after again against all also am an and any are as at be because been before
        being below between both but by can could did do does doing down during each few for from
        further had has have having he her here hers herself him himself his how i if in into is it
        its itself just me more most my myself no nor not now of off on once only or other our ours
        ourselves out over own same she should so some such than that the their theirs them
        themselves then there these they this those through to too under until up very was we were
        what when where which while who whom why will with would you your yours yourself yourselves
        says said say via new update updated report reports video watch live photos amp
        """.trimIndent()
            .split(Regex("\\s+"))
            .filter {
                it.isNotEmpty()
            }.toSet()
}
