package app.newz.engine

import app.newz.dedup.StoryGroup

/** One page of the feed: [groups] from position `index * pageSize`. */
data class Page(
    val index: Int,
    val groups: List<StoryGroup>,
    val endReached: Boolean,
)

/**
 * The endless feed over a ranked list (SPEC S16). Pages are served in order until the last
 * eligible group; every group is served once.
 *
 * [submit] merges a newer ranked list (after a refresh or a preference change) without moving or
 * duplicating what was already served: a served group stays in place, updated to the new group
 * that holds its lead, or is dropped when no new group holds it (hidden, muted). New groups
 * that share any article with a served group are left out, and the rest queue up behind the
 * served ones in ranked order.
 */
class FeedPager(
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }

    private val served = mutableListOf<StoryGroup>()
    private val pending = ArrayDeque<StoryGroup>()

    /** The groups served so far, in feed order. */
    val servedGroups: List<StoryGroup> get() = served.toList()

    /** True once every eligible group has been served. */
    val endReached: Boolean get() = pending.isEmpty()

    /** Starts over with [ranked]: nothing served yet. */
    fun reset(ranked: List<StoryGroup>) {
        served.clear()
        pending.clear()
        pending += ranked.distinctByMembers(emptySet())
    }

    /** Merges [ranked] into the feed, keeping served groups in place (see the class doc). */
    fun submit(ranked: List<StoryGroup>) {
        val byMember = HashMap<String, StoryGroup>()
        for (g in ranked) for (m in g.members()) byMember.getOrPut(m.id) { g }
        val kept = mutableListOf<StoryGroup>()
        val claimed = HashSet<StoryGroup>()
        val keptIds = HashSet<String>()
        for (old in served) {
            val new = byMember[old.lead.id] ?: continue
            val next = if (new !in claimed && new.memberIds().none { it in keptIds }) new else old
            if (next.memberIds().any { it in keptIds }) continue
            if (next === new) claimed += new
            kept += next
            keptIds += next.memberIds()
        }
        served.clear()
        served += kept
        pending.clear()
        pending += ranked.filter { it !in claimed }.distinctByMembers(keptIds)
    }

    /**
     * Page [n] of the feed: served groups at those positions are returned again, and the page
     * marks the groups it reaches as served. Pages are meant to be asked for in order.
     */
    fun page(n: Int): Page {
        require(n >= 0) { "page index must be >= 0" }
        val from = n * pageSize
        val to = from + pageSize
        while (served.size < to && pending.isNotEmpty()) served += pending.removeFirst()
        val groups = if (from >= served.size) emptyList() else served.subList(from, minOf(to, served.size)).toList()
        return Page(n, groups, endReached)
    }

    /** Serves the next [pageSize] groups after everything served so far. */
    fun next(): Page {
        val start = served.size
        val groups = List(minOf(pageSize, pending.size)) { pending.removeFirst() }
        served += groups
        return Page(start / pageSize, groups, endReached)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        private fun StoryGroup.memberIds(): List<String> = members().map { it.id }

        /** Keeps groups in order, dropping any that shares an article id with an earlier one or with [taken]. */
        private fun List<StoryGroup>.distinctByMembers(taken: Set<String>): List<StoryGroup> {
            val seen = taken.toHashSet()
            return filter { g ->
                val ids = g.memberIds()
                if (ids.any { it in seen }) {
                    false
                } else {
                    seen += ids
                    true
                }
            }
        }
    }
}
