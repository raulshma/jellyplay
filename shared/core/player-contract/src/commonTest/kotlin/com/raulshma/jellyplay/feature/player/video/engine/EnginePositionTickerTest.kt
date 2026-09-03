package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the invariants of [EnginePositionTicker] — the shared polling loop whose
 * "subtle concurrency reasoning" was previously triplicated (and subtly broken)
 * across the three engine implementations:
 *
 *  - While playing, [EnginePositionTicker.onActive] fires once per
 *    [EnginePositionTicker.pollingIntervalMs]; the interval is re-read from the
 *    StateFlow each iteration, so a mid-flight change takes effect from the next
 *    scheduled gap on.
 *  - While paused, onActive is suppressed entirely (no identical-position churn).
 *  - The paused wait is bounded by [POSITION_PAUSED_RECHECK_MS] but wakes
 *    IMMEDIATELY on resume via isPlayingFlow (no 2.5s latency on play).
 *  - Play-state edges (play->pause) emit exactly ONE final tick — the UI gets the
 *    final position even though playback stopped.
 *  - Config changed while paused is honoured at the next loop re-check: the loop
 *    keeps cycling every POSITION_PAUSED_RECHECK_MS instead of suspending forever
 *    on `isPlaying.first { it }` (the historical bug this helper fixed).
 *  - Cancelling the launched Job stops ticks.
 *
 * All tests run on runTest's virtual clock, so the suites cover minutes of paused
 * wall-clock in microseconds.
 */
class EnginePositionTickerTest {

    /** Mutable knobs + tick recording for one ticker under test. */
    private class Harness(scope: TestScope) {
        val scheduler: TestCoroutineScheduler = scope.testScheduler
        val interval = MutableStateFlow(500L)
        val playingFlow = MutableStateFlow(true)
        var playing: Boolean = true
        val tickTimes = mutableListOf<Long>()

        fun ticker(scope: TestScope): EnginePositionTicker = EnginePositionTicker(
            scope = scope,
            pollingIntervalMs = interval,
            isPlayingFlow = playingFlow,
            isCurrentlyPlaying = { playing },
            onActive = { tickTimes.add(scheduler.currentTime) },
        )

        fun pause() {
            playing = false
            playingFlow.value = false
        }

        fun resume() {
            playing = true
            playingFlow.value = true
        }
    }

    @Test
    fun activePlayback_ticksOncePerPollingInterval_andHonorsIntervalChanges() = runTest {
        val harness = Harness(this)
        val job = harness.ticker(this).launch()

        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L), harness.tickTimes, "first tick exactly one interval after launch")

        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L, 1000L), harness.tickTimes)

        // Mid-gap probes: t=1200 sits inside the third 500ms gap (tick due at 1500)...
        harness.scheduler.advanceTimeBy(200)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L, 1000L), harness.tickTimes)
        // ...and t=1500 is exactly the third tick.
        harness.scheduler.advanceTimeBy(300)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L, 1000L, 1500L), harness.tickTimes)

        // Change the interval at t=1500, right after the third tick. The delay for the
        // in-flight gap was already scheduled with the old 500ms (the value is read when
        // the delay starts), so the tick at t=2000 still fires; from the next iteration
        // on, the loop must use the new 1000ms cadence.
        harness.interval.value = 1000L
        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L, 1000L, 1500L, 2000L), harness.tickTimes)

        // Old 500ms cadence would fire again at t=2500 — it must not.
        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(
            listOf(500L, 1000L, 1500L, 2000L),
            harness.tickTimes,
            "old 500ms cadence must not continue past the change",
        )

        // New 1000ms cadence fires the next tick at t=3000.
        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L, 1000L, 1500L, 2000L, 3000L), harness.tickTimes)

        job.cancel()
    }

    @Test
    fun pausedPlayback_suppressesOnActiveEntirely() = runTest {
        val harness = Harness(this)
        harness.pause() // pause BEFORE construction so the seeded state is paused
        val job = harness.ticker(this).launch()

        // Several full paused re-check cycles (recheck timeout + delay each) — not a
        // single tick may fire while the play state never changes.
        harness.scheduler.advanceTimeBy((POSITION_PAUSED_RECHECK_MS + 500L) * 3 + 500L)
        harness.scheduler.runCurrent()
        assertEquals(emptyList(), harness.tickTimes)

        job.cancel()
    }

    @Test
    fun playToPauseEdge_emitsExactlyOneFinalTickWhilePaused() = runTest {
        val harness = Harness(this)
        // Constructed while playing (seeds lastPlayingState = true), paused before launch.
        val ticker = harness.ticker(this)
        harness.pause()
        val job = ticker.launch()

        // First paused re-check: 2500ms bounded wait, then the interval delay, then the
        // edge (playing -> paused) fires the final-position tick at 3000ms...
        harness.scheduler.advanceTimeBy(POSITION_PAUSED_RECHECK_MS + 500L)
        harness.scheduler.runCurrent()
        assertEquals(listOf(POSITION_PAUSED_RECHECK_MS + 500L), harness.tickTimes)

        // ...and subsequent paused cycles see no state change, so no more ticks.
        harness.scheduler.advanceTimeBy((POSITION_PAUSED_RECHECK_MS + 500L) * 2)
        harness.scheduler.runCurrent()
        assertEquals(listOf(POSITION_PAUSED_RECHECK_MS + 500L), harness.tickTimes)

        job.cancel()
    }

    @Test
    fun intervalChangedWhilePaused_isHonoredByTheNextLoopPass() = runTest {
        val harness = Harness(this)
        harness.pause() // paused from the start, so no pause-edge tick will fire
        val job = harness.ticker(this).launch()

        // Settle into the paused rhythm: bounded re-check (timeout at 2500) followed by
        // the interval delay ends iteration 1 at t=3000 with no tick (no edge, seeded
        // paused); iteration 2 is then suspended in its re-check wait.
        harness.scheduler.advanceTimeBy(3000)
        harness.scheduler.runCurrent()
        assertEquals(emptyList(), harness.tickTimes)

        // Change the interval NOW, while the ticker is suspended in the paused wait.
        // The historical bug: the loop suspended on `isPlaying.first { it }` with no
        // timeout, so a config change like this was invisible until playback resumed.
        harness.interval.value = 3000L

        // Advance into the middle of the same re-check wait and resume there: the wait
        // wakes immediately at t=3300 and the following delay re-reads the CHANGED
        // interval -> the first tick lands at 3300 + 3000 = 6300. A stale 500ms
        // interval would have produced a tick at 3800.
        harness.scheduler.advanceTimeBy(300)
        harness.scheduler.runCurrent()
        assertEquals(emptyList(), harness.tickTimes)

        harness.resume()
        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(
            emptyList(),
            harness.tickTimes,
            "resume must use the changed interval, not the pre-change 500ms",
        )

        harness.scheduler.advanceTimeBy(2500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(6300L), harness.tickTimes)

        job.cancel()
    }

    @Test
    fun resumeWhilePaused_wakesImmediately_withoutWaitingForTheRecheckTimeout() = runTest {
        val harness = Harness(this)
        harness.pause() // paused from the start
        val job = harness.ticker(this).launch()

        // Halfway into the first bounded wait (timeout would fire at 2500).
        harness.scheduler.advanceTimeBy(1000)
        harness.scheduler.runCurrent()
        assertEquals(emptyList(), harness.tickTimes)

        // Resuming must interrupt the paused wait at once; the tick then only waits for
        // the interval delay -> fires at 1500, not 2500 + interval.
        harness.resume()
        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(1500L), harness.tickTimes)

        job.cancel()
    }

    @Test
    fun cancellingTheLaunchedJob_stopsTicks() = runTest {
        val harness = Harness(this)
        val job = harness.ticker(this).launch()
        harness.scheduler.advanceTimeBy(500)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L), harness.tickTimes)

        job.cancel()
        harness.scheduler.advanceTimeBy(POSITION_PAUSED_RECHECK_MS * 4)
        harness.scheduler.runCurrent()
        assertEquals(listOf(500L), harness.tickTimes, "cancelled ticker must never tick again")
    }
}
