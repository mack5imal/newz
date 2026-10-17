package app.newz.feed

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Article ages for cards: `now`/`5m`/`3h`/`2d`, and spoken forms for accessibility (design Copy §feed). */
object AgeFormatter {
    fun shortAge(
        published: Instant,
        now: Instant,
    ): String {
        val age = ageOf(published, now)
        return when {
            age < 1.minutes -> "now"
            age < 1.hours -> "${age.inWholeMinutes}m"
            age < 1.days -> "${age.inWholeHours}h"
            else -> "${age.inWholeDays}d"
        }
    }

    fun spokenAge(
        published: Instant,
        now: Instant,
    ): String {
        val age = ageOf(published, now)
        return when {
            age < 1.minutes -> "just now"
            age < 1.hours -> plural(age.inWholeMinutes, "minute")
            age < 1.days -> plural(age.inWholeHours, "hour")
            else -> plural(age.inWholeDays, "day")
        }
    }

    /** A future date counts as zero age. */
    private fun ageOf(
        published: Instant,
        now: Instant,
    ): Duration = (now - published).coerceAtLeast(Duration.ZERO)

    private fun plural(
        n: Long,
        unit: String,
    ): String = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
}
