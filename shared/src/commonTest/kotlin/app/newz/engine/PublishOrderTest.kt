package app.newz.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** B46 (review R27): a compose of an older snapshot never replaces a newer one. */
class PublishOrderTest {
    @Test
    fun anOlderSnapshotFinishingLaterIsDropped() {
        val order = PublishOrder()
        assertEquals(PublishOrder.Decision.APPLY, order.decide(sequence = 2, rerank = false))
        assertEquals(PublishOrder.Decision.DROP, order.decide(sequence = 1, rerank = false))
        assertEquals(PublishOrder.Decision.APPLY, order.decide(sequence = 3, rerank = false))
    }

    @Test
    fun anOlderSnapshotThatAskedForAReRankReRanksWhatIsShown() {
        val order = PublishOrder()
        order.decide(sequence = 5, rerank = false)
        assertEquals(PublishOrder.Decision.RERANK_SHOWN, order.decide(sequence = 4, rerank = true))
    }

    @Test
    fun theSameSnapshotPublishedAgainIsApplied() {
        val order = PublishOrder()
        order.decide(sequence = 7, rerank = false)
        assertEquals(PublishOrder.Decision.APPLY, order.decide(sequence = 7, rerank = true))
    }
}
