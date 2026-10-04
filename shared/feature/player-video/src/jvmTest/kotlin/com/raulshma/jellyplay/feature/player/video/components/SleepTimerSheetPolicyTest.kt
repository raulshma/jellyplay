package com.raulshma.jellyplay.feature.player.video.components

import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [SleepTimerSheetPolicy] — the sleep-timer sheet's two pure
 * policies: the cancel-confirmation threshold and the projected stop
 * wall-clock formatting. Locale AND timezone are pinned for the label
 * tests (the DurationFormatterTest idiom) so the shapes are exact.
 */
class SleepTimerSheetPolicyTest {

    private val originalLocale = Locale.getDefault()
    private val originalZone = TimeZone.getDefault()

    @BeforeTest
    fun pinHostFacts() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterTest
    fun restoreHostFacts() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    // ── Cancel-confirmation threshold ──────────────────────────────────────

    @Test
    fun cancelAboveFiveMinutes_requiresConfirmation() {
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(5 * 60_000L + 1))
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(30 * 60_000L))
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(90 * 60_000L))
    }

    @Test
    fun cancelAtOrBelowFiveMinutes_cancelsImmediately() {
        // The boundary itself cancels immediately — the user is this close
        // to expiry anyway.
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(5 * 60_000L))
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(4 * 60_000L + 59_999L))
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(1_000L))
    }

    @Test
    fun cancelWithNothingRemaining_cancelsImmediately() {
        // The end-of-episode arm and idle edges carry no countdown.
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(0L))
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(-1L))
    }

    @Test
    fun thresholdConstant_isFiveMinutes() {
        assertEquals(5 * 60_000L, SleepTimerSheetPolicy.CANCEL_CONFIRM_THRESHOLD_MS)
    }

    // ── Projected stop time ─────────────────────────────────────────────────

    @Test
    fun projectedStop_isNowPlusRemaining() {
        assertEquals(
            1_000_000L + 23 * 60_000L,
            SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 1_000_000L, remainingMs = 23 * 60_000L),
        )
    }

    @Test
    fun projectedStop_clampsNegativeRemainingToNow() {
        // A straggling late tick projects "now", never the past.
        assertEquals(5_000L, SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 5_000L, remainingMs = -100L))
    }

    /** 24-hour convention renders `HH:mm` in the host zone (pinned UTC). */
    @Test
    fun formatStopTime_24HourShape() {
        assertEquals("23:45", SleepTimerSheetPolicy.formatStopTime(utc(23, 45), is24Hour = true, locale = Locale.US))
    }

    /** 12-hour convention renders `h:mm a` in the host zone (pinned UTC). */
    @Test
    fun formatStopTime_12HourShape() {
        assertEquals("11:45 PM", SleepTimerSheetPolicy.formatStopTime(utc(23, 45), is24Hour = false, locale = Locale.US))
    }

    /** The projection rolls across midnight in the wall-clock label. */
    @Test
    fun formatStopTime_rollsAcrossMidnight() {
        assertEquals("00:30", SleepTimerSheetPolicy.formatStopTime(utc(0, 30), is24Hour = true, locale = Locale.US))
        assertEquals("12:30 AM", SleepTimerSheetPolicy.formatStopTime(utc(0, 30), is24Hour = false, locale = Locale.US))
    }

    /** An evening UTC wall-clock instant of the test's choosing (2026-01-10). */
    private fun utc(hourOfDay: Int, minute: Int): Long =
        java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, 0, 10, hourOfDay, minute, 0)
        }.timeInMillis
}
