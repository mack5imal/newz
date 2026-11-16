package app.newz.feed

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Parses the date formats feeds use: RFC 822/1123 (RSS) and ISO 8601 / W3C-DTF (Atom, RDF dc:date). */
internal object FeedDates {
    private val MONTHS =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /**
     * Zone abbreviations, as minutes east of UTC. Beyond RFC 822's US zones these are the
     * unambiguous ones the catalog's outlets use (B37); an abbreviation not listed here gives no
     * date at all rather than a guess, so the item falls back to when it was first seen. IST
     * (Irish, Israeli or Indian) is left out on purpose.
     */
    private val ZONES =
        mapOf(
            "ut" to 0,
            "utc" to 0,
            "gmt" to 0,
            "z" to 0,
            "est" to -300,
            "edt" to -240,
            "cst" to -360,
            "cdt" to -300,
            "mst" to -420,
            "mdt" to -360,
            "pst" to -480,
            "pdt" to -420,
            "akst" to -540,
            "akdt" to -480,
            "hst" to -600,
            "wet" to 0,
            "west" to 60,
            "bst" to 60,
            "cet" to 60,
            "cest" to 120,
            "eet" to 120,
            "eest" to 180,
            "msk" to 180,
            "hkt" to 480,
            "sgt" to 480,
            "awst" to 480,
            "jst" to 540,
            "kst" to 540,
            "acst" to 570,
            "acdt" to 630,
            "aest" to 600,
            "aedt" to 660,
            "nzst" to 720,
            "nzdt" to 780,
        )

    private val RFC822 =
        Regex(
            "^(?:[A-Za-z]{2,9},?\\s*)?(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{2,4})\\s+" +
                "(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*([+-]\\d{4}|[+-]\\d{2}:\\d{2}|[A-Za-z]{1,5})?\\s*$",
        )

    /** A trailing comment, as in `+0000 (UTC)`, which RFC 822 allows and some feeds add. */
    private val COMMENT = Regex("\\s*\\([^()]*\\)\\s*$")

    /**
     * ISO 8601 date-times in the spellings feeds use (B36): `T` or a space between date and time,
     * seconds and a fraction optional, and an offset of `Z`, `+hh`, `+hhmm` or `+hh:mm`, with or
     * without a space before it. No offset reads as UTC.
     */
    private val ISO =
        Regex(
            "^(\\d{4}-\\d{2}-\\d{2})[Tt ](\\d{2}:\\d{2})(:\\d{2}(?:\\.\\d{1,9})?)?" +
                "\\s?([Zz]|[+-]\\d{2}(?::?\\d{2})?)?$",
        )
    private val ISO_DATE_ONLY = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    fun parse(raw: String?): Instant? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return parseIso(s) ?: parseRfc822(s)
    }

    private fun parseIso(s: String): Instant? {
        ISO.matchEntire(s)?.let { m ->
            val (date, hm, seconds, zone) = m.destructured
            val offset =
                when {
                    zone.isEmpty() || zone.equals("z", ignoreCase = true) -> "Z"
                    zone.length == 3 -> "$zone:00"
                    ':' in zone -> zone
                    else -> zone.substring(0, 3) + ":" + zone.substring(3)
                }
            return runCatching { Instant.parse("${date}T$hm${seconds.ifEmpty { ":00" }}$offset") }.getOrNull()
        }
        ISO_DATE_ONLY.matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching {
                LocalDateTime(y.toInt(), mo.toInt(), d.toInt(), 0, 0).toInstant(UtcOffset.ZERO)
            }.getOrNull()
        }
        return runCatching { Instant.parse(s) }.getOrNull()
    }

    private fun parseRfc822(s: String): Instant? {
        val m = RFC822.matchEntire(COMMENT.replace(s, "")) ?: return null
        val g = m.groupValues
        val month = MONTHS.indexOf(g[2].take(3).lowercase()) + 1
        if (month == 0) return null
        var year = g[3].toInt()
        if (g[3].length == 2) year += if (year < 50) 2000 else 1900
        val offsetSeconds = zoneOffsetSeconds(g[7]) ?: return null
        return runCatching {
            LocalDateTime(year, month, g[1].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
                .toInstant(UtcOffset(seconds = offsetSeconds))
        }.getOrNull()
    }

    private fun zoneOffsetSeconds(zone: String): Int? {
        if (zone.isEmpty()) return 0
        if (zone[0] == '+' || zone[0] == '-') {
            val digits = zone.substring(1).replace(":", "")
            val sign = if (zone[0] == '-') -1 else 1
            val hours = digits.substring(0, 2).toInt()
            val minutes = digits.substring(2, 4).toInt()
            return sign * (hours * 3600 + minutes * 60)
        }
        return ZONES[zone.lowercase()]?.let { it * 60 }
    }
}
