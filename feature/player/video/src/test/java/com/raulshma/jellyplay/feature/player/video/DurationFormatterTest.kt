package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun formatDuration_zero_returnsZeroMinutes() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun formatDuration_milliseconds_returnsFlooredSeconds() {
        assertEquals("0:00", formatDuration(999))
    }

    @Test
    fun formatDuration_oneSecond_returnsCorrectFormat() {
        assertEquals("0:01", formatDuration(1_000))
    }

    @Test
    fun formatDuration_thirtySeconds_returnsCorrectFormat() {
        assertEquals("0:30", formatDuration(30_000))
    }

    @Test
    fun formatDuration_oneMinute_returnsCorrectFormat() {
        assertEquals("1:00", formatDuration(60_000))
    }

    @Test
    fun formatDuration_oneMinuteThirtySeconds_returnsCorrectFormat() {
        assertEquals("1:30", formatDuration(90_000))
    }

    @Test
    fun formatDuration_tenMinutes_returnsCorrectFormat() {
        assertEquals("10:00", formatDuration(600_000))
    }

    @Test
    fun formatDuration_fiftyNineMinutesFiftyNineSeconds_returnsCorrectFormat() {
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun formatDuration_exactlyOneHour_returnsHoursFormat() {
        assertEquals("1:00:00", formatDuration(3_600_000))
    }

    @Test
    fun formatDuration_oneHourThirtyMinutes_returnsHoursFormat() {
        assertEquals("1:30:00", formatDuration(5_400_000))
    }

    @Test
    fun formatDuration_twoHours_returnsHoursFormat() {
        assertEquals("2:00:00", formatDuration(7_200_000))
    }

    @Test
    fun formatDuration_oneHourWithSeconds_returnsHoursFormat() {
        assertEquals("1:00:30", formatDuration(3_630_000))
    }

    @Test
    fun formatDuration_movieLength_returnsHoursFormat() {
        assertEquals("2:21:15", formatDuration(8_475_000))
    }

    @Test
    fun formatDuration_negativeTime_returnsNegativeResult() {
        assertEquals("-1:00", formatDuration(-60_000))
    }
}
