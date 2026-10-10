package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.testfixtures.FakeTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the [SleepCountdown] core (the single fold home of the former
 * SleepTimerManager + the reader's hand-rolled delay-loop ticker): arm state,
 * tick-driven remaining, the wall-clock resync binding (video/audio's timing
 * model), the fade ramp, cancel's full-volume expiring pulse, the
 * end-of-episode trigger guard, and — the load-bearing ordering — the ramp's
 * terminal 0f preceding the expiry callback exactly once.
 *
 * Two timing models ride the injected [SleepCountdownClock]: a fixed fake
 * (wall-clock binding — remaining derives from clock reads, not tick counts)
 * and [SleepCountdownClock.perTick] (the reader's virtual-time contract),
 * driven here by coroutine virtual time exactly as the host suites do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepCountdownTest {

    @Test
    fun `arm sets active and remaining synchronously`() = runTest {
        val countdown = SleepCountdown(clock = SleepCountdownClock { 0L }, scope = backgroundScope)

        countdown.startSleepTimer(60_000L)

        assertTrue(countdown.isSleepTimerActive.value)
        assertEquals(60_000L, countdown.sleepTimerRemainingMs.value)
        assertFalse(countdown.isEndOfEpisodeMode.value)
        countdown.cancelSleepTimer()
    }

    @Test
    fun `timed arm ticks remaining down and expires exactly once`() = runTest {
        val expiring = mutableListOf<Float>()
        var expired = 0
        val countdown = SleepCountdown(
            clock = SleepCountdownClock.perTick(1_000L),
            scope = backgroundScope,
            tickMillis = 1_000L,
        )
        countdown.setOnExpiring { expiring.add(it) }
        countdown.setOnTimerExpired { expired++ }

        countdown.startSleepTimer(5_000L, fadeOutDurationMs = 0L)

        // runCurrent after advanceTimeBy: the boundary tick scheduled exactly
        // at the new clock position only runs on it.
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3_000L, countdown.sleepTimerRemainingMs.value)
        assertEquals(0, expired)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, expired)
        assertEquals(SleepCountdownState(), countdown.state.value)
        // No fade configured: the only expiring pulse is expiry's terminal 0f,
        // and it lands BEFORE the expiry callback.
        assertEquals(listOf(0f), expiring)

        // No double fire after the expiry.
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `wall-clock binding resyncs remaining to the real deadline`() = runTest {
        val timeSource = FakeTimeSource(nowMs = 0L)
        var expired = 0
        val countdown = SleepCountdown(
            clock = SleepCountdownClock { timeSource.nowElapsedRealtimeMillis() },
            scope = backgroundScope,
        )
        countdown.setOnTimerExpired { expired++ }

        countdown.startSleepTimer(60_000L, fadeOutDurationMs = 0L)

        // The fake clock has not moved: remaining derives from the clock read,
        // not from elapsed ticks (the former manager's model, kept).
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(60_000L, countdown.sleepTimerRemainingMs.value)

        timeSource.nowMs = 30_000L
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(30_000L, countdown.sleepTimerRemainingMs.value)

        timeSource.nowMs = 60_000L
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, expired)
        assertEquals(0L, countdown.sleepTimerRemainingMs.value)
    }

    @Test
    fun `fade ramp descends through the final stretch then expiry fires in order`() = runTest {
        val expiring = mutableListOf<Float>()
        var expired = 0
        val countdown = SleepCountdown(
            clock = SleepCountdownClock.perTick(100L),
            scope = backgroundScope,
            tickMillis = 100L,
        )
        countdown.setOnExpiring { expiring.add(it) }
        countdown.setOnTimerExpired { expired++ }

        // fadeStart = min(1_000, 1_000 / 2) = 500 — the ramp occupies the
        // final half, one 100 ms pulse per wake, then 0f + expired at the end.
        countdown.startSleepTimer(1_000L, fadeOutDurationMs = 1_000L)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(1.0f, 0.8f, 0.6f, 0.4f, 0.2f, 0f), expiring)
        assertEquals(1, expired)
    }

    @Test
    fun `cancel resets state and fires the full-volume expiring pulse`() = runTest {
        val expiring = mutableListOf<Float>()
        var expired = 0
        val countdown = SleepCountdown(clock = SleepCountdownClock { 0L }, scope = backgroundScope)
        countdown.setOnExpiring { expiring.add(it) }
        countdown.setOnTimerExpired { expired++ }

        countdown.startSleepTimer(60_000L)
        countdown.cancelSleepTimer()

        assertFalse(countdown.isSleepTimerActive.value)
        assertEquals(0L, countdown.sleepTimerRemainingMs.value)
        // The pre-fade restore contract: cancel pulses 1f even though no tick
        // ran (the video controller's capture/restore rides this).
        assertEquals(listOf(1f), expiring)
        assertEquals(0, expired)
    }

    @Test
    fun `end-of-episode arm holds no countdown and the trigger fires once`() = runTest {
        var expired = 0
        val countdown = SleepCountdown(clock = SleepCountdownClock { 0L }, scope = backgroundScope)
        countdown.setOnTimerExpired { expired++ }

        countdown.startEndOfEpisodeTimer()
        assertTrue(countdown.isSleepTimerActive.value)
        assertTrue(countdown.isEndOfEpisodeMode.value)
        assertEquals(0L, countdown.sleepTimerRemainingMs.value)
        assertEquals("End of episode", countdown.getSleepTimerDisplayText())

        countdown.triggerEndOfEpisode()
        assertEquals(1, expired)
        assertFalse(countdown.isSleepTimerActive.value)
        assertFalse(countdown.isEndOfEpisodeMode.value)

        // Disarmed: a stray trigger is a no-op.
        countdown.triggerEndOfEpisode()
        assertEquals(1, expired)
    }

    @Test
    fun `trigger is a no-op for a timed arm`() = runTest {
        var expired = 0
        val countdown = SleepCountdown(clock = SleepCountdownClock { 0L }, scope = backgroundScope)
        countdown.setOnTimerExpired { expired++ }

        countdown.startSleepTimer(60_000L)
        countdown.triggerEndOfEpisode()

        assertEquals(0, expired)
        assertTrue(countdown.isSleepTimerActive.value)
    }

    @Test
    fun `re-arming replaces the running countdown`() = runTest {
        var expired = 0
        val countdown = SleepCountdown(
            clock = SleepCountdownClock.perTick(1_000L),
            scope = backgroundScope,
            tickMillis = 1_000L,
        )
        countdown.setOnTimerExpired { expired++ }

        countdown.startSleepTimer(5_000L, fadeOutDurationMs = 0L)
        advanceTimeBy(2_000)
        runCurrent()
        countdown.startSleepTimer(15_000L, fadeOutDurationMs = 0L)
        assertEquals(15_000L, countdown.sleepTimerRemainingMs.value)

        // The 5-second arm is gone: advancing past it must not fire.
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(0, expired)
        assertTrue(countdown.isSleepTimerActive.value)

        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `display text formats hours minutes seconds and the idle empty string`() = runTest {
        val countdown = SleepCountdown(clock = SleepCountdownClock { 0L }, scope = backgroundScope)
        assertEquals("", countdown.getSleepTimerDisplayText())

        countdown.startSleepTimer(3_661_000L)
        assertEquals("1:01:01", countdown.getSleepTimerDisplayText())

        countdown.startSleepTimer(300_000L)
        assertEquals("5:00", countdown.getSleepTimerDisplayText())
    }

    @Test
    fun `zero-length arm expires on the launch tick`() = runTest {
        var expired = 0
        val countdown = SleepCountdown(clock = SleepCountdownClock { 0L }, scope = backgroundScope)
        countdown.setOnTimerExpired { expired++ }

        countdown.startSleepTimer(0L)
        runCurrent()

        assertEquals(1, expired)
        assertFalse(countdown.isSleepTimerActive.value)
    }
}
