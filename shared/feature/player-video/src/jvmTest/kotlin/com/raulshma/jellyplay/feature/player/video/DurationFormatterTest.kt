package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.Test

class DurationFormatterTest {

    @Test
    fun formatDuration_zero_returnsZeroMinutes() {
        assertEquals(formatDuration(0), "0:00")
    }

    @Test
    fun formatDuration_milliseconds_returnsFlooredSeconds() {
        assertEquals(formatDuration(999), "0:00")
    }

    @Test
    fun formatDuration_oneSecond_returnsCorrectFormat() {
        assertEquals(formatDuration(1_000), "0:01")
    }

    @Test
    fun formatDuration_thirtySeconds_returnsCorrectFormat() {
        assertEquals(formatDuration(30_000), "0:30")
    }

    @Test
    fun formatDuration_oneMinute_returnsCorrectFormat() {
        assertEquals(formatDuration(60_000), "1:00")
    }

    @Test
    fun formatDuration_oneMinuteThirtySeconds_returnsCorrectFormat() {
        assertEquals(formatDuration(90_000), "1:30")
    }

    @Test
    fun formatDuration_tenMinutes_returnsCorrectFormat() {
        assertEquals(formatDuration(600_000), "10:00")
    }

    @Test
    fun formatDuration_fiftyNineMinutesFiftyNineSeconds_returnsCorrectFormat() {
        assertEquals(formatDuration(3_599_000), "59:59")
    }

    @Test
    fun formatDuration_exactlyOneHour_returnsHoursFormat() {
        assertEquals(formatDuration(3_600_000), "1:00:00")
    }

    @Test
    fun formatDuration_oneHourThirtyMinutes_returnsHoursFormat() {
        assertEquals(formatDuration(5_400_000), "1:30:00")
    }

    @Test
    fun formatDuration_twoHours_returnsHoursFormat() {
        assertEquals(formatDuration(7_200_000), "2:00:00")
    }

    @Test
    fun formatDuration_oneHourWithSeconds_returnsHoursFormat() {
        assertEquals(formatDuration(3_630_000), "1:00:30")
    }

    @Test
    fun formatDuration_movieLength_returnsHoursFormat() {
        assertEquals(formatDuration(8_475_000), "2:21:15")
    }

    @Test
    fun formatDuration_negativeTime_returnsNegativeResult() {
        assertEquals(formatDuration(-60_000), "-1:00")
    }
}
