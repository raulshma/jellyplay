package com.raulshma.jellyplay.feature.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Direct [HomeFeatureGate] tests — the shared spine of the VM's opt-in
 * decoration features (H17/H18/H19), extracted beside the suites it serves
 * (the [HomeRefresherTest] style: plain JUnit over a test-dispatcher scope,
 * no VM construction). Pins the two policies the holder owns:
 *  - the gate dedupe — one effect run per DISTINCT gate value;
 *  - the cancel-and-restart collector — a gate change cancels the previous
 *    emission's in-flight effect instead of queueing behind it (the poll
 *    loops park on their cadence and MUST restart; the bag-write effects are
 *    last-write-wins, so a cancelled stale load paints nothing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeatureGateTest {

    private val dispatcher = StandardTestDispatcher()
    private var scope: CoroutineScope? = null

    @AfterTest
    fun tearDown() {
        // No loop survives the body (runTest's teardown advances the shared
        // scheduler until idle and would otherwise never converge).
        scope?.cancel()
    }

    @Test
    fun effect_runsOncePerDistinctGateValue() = runTest(dispatcher) {
        val gate = MutableStateFlow("a")
        val runs = mutableListOf<String>()
        scope = CoroutineScope(dispatcher)
        HomeFeatureGate(scope!!, gate) { runs.add(it) }

        runCurrent()
        assertEquals(listOf("a"), runs)

        // The same value re-emitted dedupes; a genuinely new value runs.
        gate.value = "a"
        runCurrent()
        assertEquals(listOf("a"), runs)
        gate.value = "b"
        runCurrent()
        assertEquals(listOf("a", "b"), runs)
    }

    @Test
    fun gateChange_cancelsTheInFlightEffect_insteadOfQueueing() = runTest(dispatcher) {
        val gate = MutableStateFlow(1)
        val finished = mutableListOf<Int>()
        scope = CoroutineScope(dispatcher)
        HomeFeatureGate(scope!!, gate) { value ->
            delay(1_000) // the load in flight when the gate flips
            finished.add(value)
        }

        runCurrent() // emission 1 parked on its load
        assertEquals(emptyList(), finished)

        gate.value = 2
        runCurrent() // the gate flip cancels emission 1 and starts emission 2
        advanceTimeBy(1_000) // the new load's delay elapses
        runCurrent()
        // Only the NEW gate's effect landed — the stale one was cancelled
        // mid-load, not queued behind it to paint afterwards.
        assertEquals(listOf(2), finished)
    }

    @Test
    fun parkedLoop_restartsOnEveryGateChange_thePollShape() = runTest {
        // The H19 shape: the effect parks on the cadence delay; each distinct
        // gate emission cancels the parked loop and starts fresh. Owns its
        // scope LOCALLY and cancels BEFORE the body returns — the parked
        // while(true)+delay loop never lets the shared scheduler converge,
        // so the cancel must precede runTest's idle-advance teardown (the
        // @AfterTest cancel is too late for a loop that never idles).
        val gate = MutableStateFlow(1)
        val started = mutableListOf<Int>()
        val gateScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            HomeFeatureGate(gateScope, gate) { value ->
                started.add(value)
                while (true) {
                    delay(60_000)
                }
            }

            runCurrent()
            assertEquals(listOf(1), started)
            repeat(3) { advanceTimeBy(60_000) }
            assertEquals(listOf(1), started) // the loop is parked, not re-running

            gate.value = 2
            runCurrent()
            assertEquals(listOf(1, 2), started) // immediate restart, no queue wait
        } finally {
            gateScope.cancel()
        }
    }
}
