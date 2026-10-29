package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the generation-guarded poll machine for VLC external-subtitle
 * registration ([VlcSlaveRegistration]) without libVLC: the baseline-diff
 * pairing (a track in the pre-add snapshot or already claimed by an earlier
 * slave is never taken), the attempt budget (reschedule while budget
 * remains, then a terminal Exhausted), the serialized queue (advance begins
 * the next slave with a fresh baseline, FIFO), the generation guard (a late
 * tick scheduled under an older generation cannot consume the next media's
 * budget), and the teardown reset.
 */
class VlcSlaveRegistrationTest {

    /** Builds the holder with a recording [VlcSlaveRegistration.schedulePoll] seam. */
    private fun bench(attempts: Int = 3): Pair<VlcSlaveRegistration, MutableList<Int>> {
        val scheduled = mutableListOf<Int>()
        val holder = VlcSlaveRegistration(attempts) { gen -> scheduled += gen }
        return holder to scheduled
    }

    @Test
    fun `first tick pairs the new spu track with the pending source id`() {
        val (holder, _) = bench()

        holder.begin("external:0", knownSpuIds = setOf(1, 2), generation = 0)
        val verdict = holder.tick(
            generation = 0,
            currentGeneration = 0,
            spuTrackIds = setOf(1, 2, 7),
            claimedTrackIds = emptySet(),
        )

        assertEquals(SlaveTickVerdict.Register(vlcTrackId = 7, sourceId = "external:0"), verdict)
    }

    @Test
    fun `a track inside the pre-add baseline is not claimed`() {
        val (holder, _) = bench()

        holder.begin("external:0", knownSpuIds = setOf(1, 2), generation = 0)
        val verdict = holder.tick(
            generation = 0,
            currentGeneration = 0,
            spuTrackIds = setOf(1, 2),
            claimedTrackIds = emptySet(),
        )

        // The pre-existing tracks are the baseline, not the new slave — the
        // poll budget drains instead.
        assertTrue(verdict is SlaveTickVerdict.PollRescheduled)
    }

    @Test
    fun `a track already claimed by an earlier slave is skipped`() {
        val (holder, _) = bench()

        holder.begin("external:1", knownSpuIds = setOf(1), generation = 0)
        val skipped = holder.tick(
            generation = 0,
            currentGeneration = 0,
            spuTrackIds = setOf(1, 5),
            claimedTrackIds = setOf(5),
        )
        val paired = holder.tick(
            generation = 0,
            currentGeneration = 0,
            spuTrackIds = setOf(1, 5, 9),
            claimedTrackIds = setOf(5),
        )

        assertTrue(skipped is SlaveTickVerdict.PollRescheduled)
        assertEquals(SlaveTickVerdict.Register(vlcTrackId = 9, sourceId = "external:1"), paired)
    }

    @Test
    fun `budget exhaustion is terminal and stops rescheduling`() {
        val (holder, scheduled) = bench(attempts = 2)

        holder.begin("external:0", knownSpuIds = emptySet(), generation = 0)
        assertTrue(holder.tick(0, 0, emptySet(), emptySet()) is SlaveTickVerdict.PollRescheduled)
        assertEquals(
            SlaveTickVerdict.Exhausted("external:0"),
            holder.tick(0, 0, emptySet(), emptySet()),
        )

        // begin + one reschedule: the exhausted tick asks for no further poll.
        assertEquals(listOf(0, 0), scheduled)
    }

    @Test
    fun `a stale generation abandons the tick without touching the budget`() {
        val (holder, scheduled) = bench(attempts = 2)

        holder.begin("external:0", knownSpuIds = emptySet(), generation = 0)
        // Media teardown bumped the generation; the tick scheduled under the
        // old one fires late.
        val verdict = holder.tick(generation = 0, currentGeneration = 1, spuTrackIds = setOf(5), claimedTrackIds = emptySet())

        assertEquals(SlaveTickVerdict.Abandoned, verdict)
        // The abandoned tick consumed no budget and rescheduled nothing.
        assertEquals(listOf(0), scheduled)
    }

    @Test
    fun `a late stale tick cannot consume the next slave's budget`() {
        val (holder, _) = bench(attempts = 2)

        holder.begin("external:0", knownSpuIds = emptySet(), generation = 1)
        holder.reset()
        holder.begin("external:1", knownSpuIds = emptySet(), generation = 2)

        // The gen-1 tick (still in flight when the media swapped) fires
        // after the new chain began: abandoned, and the gen-2 chain's budget
        // is intact.
        assertEquals(SlaveTickVerdict.Abandoned, holder.tick(1, 2, setOf(4), emptySet()))
        assertEquals(
            SlaveTickVerdict.Register(vlcTrackId = 4, sourceId = "external:1"),
            holder.tick(2, 2, setOf(4), emptySet()),
        )
    }

    @Test
    fun `tick with no pending registration is abandoned`() {
        val (holder, scheduled) = bench()

        assertEquals(SlaveTickVerdict.Abandoned, holder.tick(0, 0, setOf(1), emptySet()))
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `advance begins the next queued slave with the fresh baseline`() {
        val (holder, scheduled) = bench()

        holder.begin("external:0", knownSpuIds = setOf(1), generation = 0)
        holder.enqueue("external:1")
        holder.enqueue("external:2")

        holder.advance(knownSpuIds = setOf(1, 3), generation = 0)

        // FIFO: the first queued id is now pending, its diff window opened
        // over the fresh baseline and the next poll is scheduled.
        assertEquals(
            SlaveTickVerdict.Register(vlcTrackId = 4, sourceId = "external:1"),
            holder.tick(0, 0, spuTrackIds = setOf(1, 3, 4), claimedTrackIds = emptySet()),
        )
        holder.advance(knownSpuIds = setOf(1, 3, 4), generation = 0)
        assertEquals(
            SlaveTickVerdict.Register(vlcTrackId = 5, sourceId = "external:2"),
            holder.tick(0, 0, spuTrackIds = setOf(1, 3, 4, 5), claimedTrackIds = emptySet()),
        )
        // begin + two advance-begin schedules (the pairing verdicts reschedule
        // nothing themselves).
        assertEquals(listOf(0, 0, 0), scheduled)
    }

    @Test
    fun `advance with an empty queue retires the chain`() {
        val (holder, scheduled) = bench()

        holder.begin("external:0", knownSpuIds = emptySet(), generation = 0)
        holder.advance(knownSpuIds = emptySet(), generation = 0)

        assertTrue(holder.acceptsBegin)
        assertEquals(SlaveTickVerdict.Abandoned, holder.tick(0, 0, setOf(1), emptySet()))
        assertEquals(listOf(0), scheduled)
    }

    @Test
    fun `a queued add does not begin while a chain is in flight`() {
        val (holder, _) = bench()

        holder.begin("external:0", knownSpuIds = emptySet(), generation = 0)

        assertFalse(holder.acceptsBegin)
        holder.enqueue("external:1")
        // A surfacing track pairs with the IN-FLIGHT slave; the queued id is
        // not pending yet and is never served by this chain's ticks.
        assertEquals(
            SlaveTickVerdict.Register(vlcTrackId = 3, sourceId = "external:0"),
            holder.tick(0, 0, setOf(3), emptySet()),
        )
    }

    @Test
    fun `reset clears the pending chain and the queue`() {
        val (holder, _) = bench()

        holder.begin("external:0", knownSpuIds = emptySet(), generation = 0)
        holder.enqueue("external:1")
        holder.reset()

        assertTrue(holder.acceptsBegin)
        assertEquals(SlaveTickVerdict.Abandoned, holder.tick(0, 0, setOf(1), emptySet()))
    }
}
