package app.newz.feed

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Parses the date formats feeds use: RFC 822/1123 (RSS) and ISO 8601 / W3C-DTF (Atom, RDF dc:date). */
internal object FeedDates {
    private val MONTHS =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val ZONES =
        mapOf(
            "ut" to 0,
            "utc" to 0,
            "gmt" to 0,
            "z" to 0,
            "est" to -5,
            "edt" to -4,
            "cst" to -6,
            "cdt" to -5,
            "mst" to -7,
            "mdt" to -6,
            "pst" to -8,
            "pdt" to -7,
        )

    private val RFC822 =
        Regex(
            "^(?:[A-Za-z]{2,9},?\\s*)?(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{2,4})\\s+" +
                "(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*([+-]\\d{4}|[+-]\\d{2}:\\d{2}|[A-Za-z]{1,5})?\\s*$",
        )

    private val ISO_DATE_ONLY = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val ISO_NO_ZONE = Regex("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$")

    fun parse(raw: String?): Instant? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return parseIso(s) ?: parseRfc822(s)
    }

    private fun parseIso(s: String): Instant? {
        runCatching { return Instant.parse(s.replace(' ', 'T')) }
        if (ISO_NO_ZONE.matches(s)) {
            runCatching { return LocalDateTime.parse(s.replace(' ', 'T')).toInstant(UtcOffset.ZERO) }
        }
        ISO_DATE_ONLY.matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching {
                LocalDateTime(y.toInt(), mo.toInt(), d.toInt(), 0, 0).toInstant(UtcOffset.ZERO)
            }.getOrNull()
        }
        return null
    }

    private fun parseRfc822(s: String): Instant? {
        val m = RFC822.matchEntire(s) ?: return null
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
        return ZONES[zone.lowercase()]?.let { it * 3600 } ?: 0
    }
}
