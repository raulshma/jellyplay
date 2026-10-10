package com.raulshma.jellyplay.feature.player.audio.sheets

import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the audio sleep-timer sheet's pure policies (the audio twin of
 * player-video's SleepTimerSheetPolicyTest): the cancel-confirmation
 * threshold and the projected stop wall-clock formatting. Locale AND
 * timezone are pinned for the label tests so the shapes are exact.
 */
class AudioSleepTimerSheetPolicyTest {

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

    @Test
    fun cancelAboveFiveMinutes_requiresConfirmation() {
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(5 * 60_000L + 1))
        assertTrue(SleepTimerSheetPolicy.requiresCancelConfirmation(90 * 60_000L))
    }

    @Test
    fun cancelAtOrBelowFiveMinutes_orNothing_cancelsImmediately() {
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(5 * 60_000L))
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(1_000L))
        // The end-of-episode arm and idle edges carry no countdown.
        assertFalse(SleepTimerSheetPolicy.requiresCancelConfirmation(0L))
    }

    @Test
    fun thresholdConstant_isFiveMinutes() {
        assertEquals(5 * 60_000L, SleepTimerSheetPolicy.CANCEL_CONFIRM_THRESHOLD_MS)
    }

    @Test
    fun projectedStop_isNowPlusRemaining_andClampsNegatives() {
        assertEquals(
            1_000_000L + 23 * 60_000L,
            SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 1_000_000L, remainingMs = 23 * 60_000L),
        )
        assertEquals(5_000L, SleepTimerSheetPolicy.projectedStopEpochMs(nowEpochMs = 5_000L, remainingMs = -1L))
    }

    @Test
    fun formatStopTime_usesTheHostTimeConvention() {
        val stop = utc(23, 45)
        assertEquals("23:45", SleepTimerSheetPolicy.formatStopTime(stop, is24Hour = true, locale = Locale.US))
        assertEquals("11:45 PM", SleepTimerSheetPolicy.formatStopTime(stop, is24Hour = false, locale = Locale.US))
    }

    private fun utc(hourOfDay: Int, minute: Int): Long =
        java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, 0, 10, hourOfDay, minute, 0)
        }.timeInMillis
}
