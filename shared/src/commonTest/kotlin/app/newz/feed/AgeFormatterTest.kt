package app.newz.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AgeFormatterTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")

    @Test
    fun shortAges() {
        assertEquals("now", AgeFormatter.shortAge(now - 30.seconds, now))
        assertEquals("5m", AgeFormatter.shortAge(now - 5.minutes, now))
        assertEquals("3h", AgeFormatter.shortAge(now - 3.hours, now))
        assertEquals("2d", AgeFormatter.shortAge(now - 2.days, now))
        assertEquals("59m", AgeFormatter.shortAge(now - 59.minutes - 59.seconds, now))
    }

    @Test
    fun futureDateIsNow() {
        assertEquals("now", AgeFormatter.shortAge(now + 2.hours, now))
        assertEquals("just now", AgeFormatter.spokenAge(now + 2.hours, now))
    }

    @Test
    fun spokenAges() {
        assertEquals("just now", AgeFormatter.spokenAge(now - 10.seconds, now))
        assertEquals("1 minute ago", AgeFormatter.spokenAge(now - 1.minutes, now))
        assertEquals("12 minutes ago", AgeFormatter.spokenAge(now - 12.minutes, now))
        assertEquals("1 hour ago", AgeFormatter.spokenAge(now - 1.hours, now))
        assertEquals("4 hours ago", AgeFormatter.spokenAge(now - 4.hours, now))
        assertEquals("1 day ago", AgeFormatter.spokenAge(now - 1.days, now))
        assertEquals("6 days ago", AgeFormatter.spokenAge(now - 6.days, now))
    }
}
