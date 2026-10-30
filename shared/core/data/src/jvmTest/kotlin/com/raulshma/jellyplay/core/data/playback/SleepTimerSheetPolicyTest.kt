package com.raulshma.jellyplay.core.data.playback

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [SleepTimerSheetPolicy] + [SLEEP_TIMER_PRESET_MS] — the ONE
 * sleep-timer sheet truth table (the former per-host twins in player-video /
 * player-audio, folded into core:data beside [SleepCountdown]).
 */
class SleepTimerSheetPolicyTest {

    // ── requiresCancelConfirmation ─────────────────────────────────────────

    @Test
    fun `long remaining asks for confirmation`() {
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(5 * 60_000L + 1L))
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(45 * 60_000L))
    }

    @Test
    fun `boundary and below cancel immediately`() {
        // Exactly 5:00 is this close to expiry — no confirmation.
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(5 * 60_000L))
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(0L))
        // The end-of-episode arm renders remaining 0 (no countdown).
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(-1L))
    }

    // ── projectedStopEpochMs ───────────────────────────────────────────────

    @Test
    fun `projection adds remaining to now`() {
        assertEquals(
            1_000L + 60_000L,
            SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 1_000L, remainingMs = 60_000L),
        )
    }

    @Test
    fun `straggling late tick projects now, never the past`() {
        assertEquals(
            1_000L,
            SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 1_000L, remainingMs = -500L),
        )
        assertEquals(
            1_000L,
            SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 1_000L, remainingMs = 0L),
        )
    }

    // ── formatStopTime ─────────────────────────────────────────────────────

    @Test
    fun `24-hour convention renders HHmm`() {
        // 23:45 local — construct via the epoch of a known wall time.
        val stop = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 30, 23, 45, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val rendered = SleepTimerSheetPolicy.formatStopTime(stop, is24Hour = true, locale = Locale.US)
        assertTrue(rendered.endsWith("23:45"), "got: $rendered")
    }

    @Test
    fun `12-hour convention renders h mm a`() {
        val stop = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 30, 23, 45, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val rendered = SleepTimerSheetPolicy.formatStopTime(stop, is24Hour = false, locale = Locale.US)
        assertTrue(rendered.contains("11:45"), "got: $rendered")
        assertTrue(rendered.contains("PM"), "got: $rendered")
    }

    // ── SLEEP_TIMER_PRESET_MS ──────────────────────────────────────────────

    @Test
    fun `preset ladder is the shared 15-90 minute rungs in order`() {
        assertEquals(
            listOf(
                15 * 60_000L,
                30 * 60_000L,
                45 * 60_000L,
                60 * 60_000L,
                90 * 60_000L,
            ),
            SLEEP_TIMER_PRESET_MS,
        )
    }

    @Test
    fun `cancel threshold stays at five minutes`() {
        assertEquals(5 * 60_000L, SleepTimerSheetPolicy.CANCEL_CONFIRM_THRESHOLD_MS)
    }

    // formatStopTime never throws for in-range inputs; SimpleDateFormat with a
    // valid pattern cannot fail on a finite epoch — this pins that assumption.
    @Test
    fun `formatting a negative epoch stays well-formed`() {
        SleepTimerSheetPolicy.formatStopTime(-1L, is24Hour = true, locale = Locale.US)
    }
}
