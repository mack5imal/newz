package app.newz.engine

/**
 * Keeps the feed's publishes in the order their snapshots were read (B46). Composes run outside
 * the repository's lock and finish in any order, so a slow compose of an older snapshot (a load
 * landing) could otherwise overwrite a newer one (the recompose after a hide) and bring the hidden
 * card back. Not thread-safe: call it under the repository's lock.
 */
internal class PublishOrder {
    enum class Decision {
        /** The newest snapshot so far: apply it. */
        APPLY,

        /** Older than what is shown, but it asked for a re-rank: re-rank what is shown instead. */
        RERANK_SHOWN,

        /** Older than what is shown: drop it. */
        DROP,
    }

    private var applied = Long.MIN_VALUE

    fun decide(
        sequence: Long,
        rerank: Boolean,
    ): Decision {
        if (sequence >= applied) {
            applied = sequence
            return Decision.APPLY
        }
        return if (rerank) Decision.RERANK_SHOWN else Decision.DROP
    }
}
