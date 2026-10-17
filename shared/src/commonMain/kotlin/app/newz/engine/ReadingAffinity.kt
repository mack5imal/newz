package app.newz.engine

import app.newz.store.HistoryEntry

/**
 * How often the user opened each outlet and topic, counted from reading history. Used to pick a
 * story group's lead card and (phase 9) to suggest outlets and topics. Empty when history is off.
 */
class ReadingAffinity(
    history: List<HistoryEntry>,
) {
    val outletCounts: Map<String, Int> = history.groupingBy { it.outletId }.eachCount()

    val topicCounts: Map<String, Int> = history.flatMap { it.topics.distinct() }.groupingBy { it }.eachCount()

    private val maxOutletCount = outletCounts.values.maxOrNull() ?: 0

    /** [outletId]'s share of the most-read outlet's opens, from 0.0 (never read) to 1.0. */
    fun outletScore(outletId: String): Double =
        if (maxOutletCount == 0) 0.0 else (outletCounts[outletId] ?: 0).toDouble() / maxOutletCount

    companion object {
        val EMPTY = ReadingAffinity(emptyList())
    }
}
