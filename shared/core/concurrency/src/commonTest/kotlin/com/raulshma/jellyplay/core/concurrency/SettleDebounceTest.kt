package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the two-phase contract of [settleDebounce]: arrival is immediate,
 * settlement waits out the full quiet window, and any value arriving
 * mid-window cancels the pending settlement (refocus drops the pending fetch).
 */
class SettleDebounceTest {

    @Test
    fun `emits the arrival immediately and the settlement exactly at the window end`() = runTest {
        val emissions = mutableListOf<Settlement<Int>>()
        val job = launch { flowOf(7).settleDebounce(400).collect { emissions += it } }
        runCurrent()

        assertEquals(listOf<Settlement<Int>>(Settlement.Arrived(7)), emissions, "arrival must not wait the window")

        advanceTimeBy(399)
        assertEquals(listOf<Settlement<Int>>(Settlement.Arrived(7)), emissions, "still inside the window")

        advanceTimeBy(1)
        // advanceTimeBy leaves tasks scheduled exactly AT the destination
        // virtual time unexecuted — the settle continuation due at t=400 runs
        // on runCurrent, proving it was due exactly at the window end.
        runCurrent()
        assertEquals(
            listOf(Settlement.Arrived(7), Settlement.Settled(7)),
            emissions,
            "settlement lands exactly on the deadline",
        )
        job.cancel()
    }

    @Test
    fun `a value arriving mid-window cancels the pending settlement and restarts the clock`() = runTest {
        val source = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val emissions = mutableListOf<Settlement<Int>>()
        val job = launch { source.settleDebounce(400).collect { emissions += it } }
        runCurrent()

        source.tryEmit(1)
        runCurrent()
        advanceTimeBy(300)
        source.tryEmit(2)
        runCurrent()
        // 1's settle window was broken by 2; only arrivals so far.
        assertEquals(
            listOf<Settlement<Int>>(Settlement.Arrived(1), Settlement.Arrived(2)),
            emissions,
        )

        // 2's window restarted at the refocus: 399ms more is still pending.
        advanceTimeBy(399)
        assertEquals(
            listOf<Settlement<Int>>(Settlement.Arrived(1), Settlement.Arrived(2)),
            emissions,
        )

        advanceTimeBy(1)
        // Due exactly at t=700; runCurrent executes the deadline task.
        runCurrent()
        assertEquals(
            listOf(Settlement.Arrived(1), Settlement.Arrived(2), Settlement.Settled(2)),
            emissions,
            "only the LAST value of the burst settles — 1's settlement was cancelled",
        )
        job.cancel()
    }

    @Test
    fun `a fast sweep settles only the final value`() = runTest {
        val source = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val emissions = mutableListOf<Settlement<Int>>()
        val job = launch { source.settleDebounce(400).collect { emissions += it } }
        runCurrent()

        source.tryEmit(1)
        source.tryEmit(2)
        source.tryEmit(3)
        runCurrent()
        advanceTimeBy(400)
        // Due exactly at t=400; runCurrent executes the deadline task.
        runCurrent()

        assertEquals(
            listOf(
                Settlement.Arrived(1),
                Settlement.Arrived(2),
                Settlement.Arrived(3),
                Settlement.Settled(3),
            ),
            emissions,
        )
        assertTrue(emissions.none { it is Settlement.Settled<*> && it.value != 3 })
        job.cancel()
    }

    @Test
    fun `cancelling the collector cancels a pending settlement`() = runTest {
        val source = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val emissions = mutableListOf<Settlement<Int>>()
        val job = launch { source.settleDebounce(400).collect { emissions += it } }
        runCurrent()
        source.tryEmit(1)
        runCurrent()

        job.cancelAndJoin()
        advanceTimeBy(1_000)
        assertTrue(emissions.none { it is Settlement.Settled<*> }, "no settlement escapes the cancelled collector")
    }
}
