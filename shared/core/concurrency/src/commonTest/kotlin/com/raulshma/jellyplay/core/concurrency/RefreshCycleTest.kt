package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the refresh cycle's contract on the virtual clock (the module drives
 * real `delay`s, so every cadence assertion is EXACT — no real-time windows):
 *
 *  - round 1 fetches after exactly one gap; a fixed cadence stays fixed;
 *  - backoff grows multiplicatively and caps at [PollSpec.maxIntervalMs];
 *  - stop cancels the loop — nothing further is ever scheduled;
 *  - a repeated start replaces the running loop instead of stacking one;
 *  - a gate-closed round fetches nothing and does NOT advance the backoff
 *    (the growth measures fetches, not wall time);
 *  - a throwing fetch arm kills the loop (the hand-rolled loops' behavior,
 *    kept).
 *
 * Cycles ride [runTest]'s `backgroundScope` so the infinite loops never block
 * test completion; the throwing-arm test owns a supervised scope with a
 * recording handler instead (a backgroundScope child's uncaught exception
 * fails the whole test, which would pin the wrong thing).
 */
class RefreshCycleTest {

    @Test
    fun `round 1 fetches after exactly one gap and a fixed cadence stays fixed`() = runTest {
        val fetches = mutableListOf<Long>()
        val cycle = RefreshCycle(backgroundScope, PollSpec(intervalMs = 100)) {
            fetches += currentTime
        }

        assertFalse(cycle.isActive, "never started")
        cycle.start()
        runCurrent()
        assertTrue(cycle.isActive, "started")
        assertTrue(fetches.isEmpty(), "the first fetch waits out the gap — start never fetches immediately")

        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf(100L, 200L, 300L), fetches)
        cycle.stop()
    }

    @Test
    fun `backoff grows the gap multiplicatively and caps at maxIntervalMs`() = runTest {
        val fetches = mutableListOf<Long>()
        val cycle = RefreshCycle(
            backgroundScope,
            PollSpec(intervalMs = 100, backoffFactor = 2.0, maxIntervalMs = 300),
        ) { fetches += currentTime }

        cycle.start()
        advanceTimeBy(1_000)
        runCurrent()

        // Gaps: 100 (100 * 2^0), 200 (100 * 2^1), 300 (400 capped), 300…
        assertEquals(listOf(100L, 300L, 600L, 900L), fetches)
        cycle.stop()
    }

    @Test
    fun `stop cancels the loop - nothing further is scheduled`() = runTest {
        val fetches = mutableListOf<Long>()
        val cycle = RefreshCycle(backgroundScope, PollSpec(intervalMs = 100)) {
            fetches += currentTime
        }

        cycle.start()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf(100L, 200L), fetches)

        cycle.stop()
        assertFalse(cycle.isActive, "stop must clear the slot")
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(100L, 200L), fetches, "a stopped cycle must schedule nothing further")
    }

    @Test
    fun `a repeated start replaces the running loop instead of stacking one`() = runTest {
        val fetches = mutableListOf<Long>()
        val cycle = RefreshCycle(backgroundScope, PollSpec(intervalMs = 100)) {
            fetches += currentTime
        }

        cycle.start()
        advanceTimeBy(50) // first loop parked mid-gap at t=100
        cycle.start() // replaces it — the replacement's gap restarts from NOW
        advanceTimeBy(100)
        runCurrent()

        // The replacement fires at t=150; a stacked second loop would also
        // have fired the first loop's tick at t=100.
        assertEquals(listOf(150L), fetches)
        cycle.stop()
    }

    @Test
    fun `a gate-closed round fetches nothing and does not advance the backoff`() = runTest {
        val fetches = mutableListOf<Long>()
        var open = false
        val cycle = RefreshCycle(
            backgroundScope,
            PollSpec(intervalMs = 100, backoffFactor = 2.0, maxIntervalMs = 400),
            gate = { open },
        ) { fetches += currentTime }

        cycle.start()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(fetches.isEmpty(), "gate closed: rounds run, fetches never do")

        open = true
        advanceTimeBy(1_100) // through t=2100, the fourth post-open fetch
        runCurrent()

        // Ten closed rounds at the BASE gap consumed no backoff step: the
        // first fetch lands 100ms after the gate opened (t=1100) and the
        // growth starts from round 1 from there — inter-fetch gaps 200, 400,
        // 400… A closed round that advanced the backoff would have fired the
        // first fetch 400ms late and at the cap.
        assertEquals(listOf(1_100L, 1_300L, 1_700L, 2_100L), fetches.take(4))
        val gaps = fetches.zipWithNext { a, b -> b - a }
        assertEquals(200L, gaps.first(), "the first post-open inter-fetch gap is one backoff step past the base")
        assertTrue(gaps.drop(1).all { it == 400L }, "the capped 400ms cadence holds from the third fetch: $gaps")
        cycle.stop()
    }

    @Test
    fun `a throwing fetch arm kills the loop`() = runTest {
        val exceptions = mutableListOf<Throwable>()
        // A supervised scope of our own: a backgroundScope child's uncaught
        // exception would fail the test — the pin here is that the LOOP dies,
        // and the handler records the exception proving it died by throw.
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, e -> exceptions += e },
        )
        var attempts = 0
        val cycle = RefreshCycle(scope, PollSpec(intervalMs = 100)) {
            attempts++
            throw IllegalStateException("boom")
        }

        cycle.start()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, attempts, "the exception must kill the loop, not be swallowed")
        assertFalse(cycle.isActive)
        assertEquals(1, exceptions.size)
        assertEquals("boom", exceptions.single().message)
        scope.cancel()
    }
}
