package com.raulshma.jellyplay.feature.player.video.components

import com.raulshma.jellyplay.core.model.LyricsLine
import kotlin.test.assertEquals
import kotlin.test.Test

class CompanionDashboardTest {

    @Test
    fun activeLyricLineIndex_emptyLyrics_returnsNegativeOrZero() {
        val lyricsLines = emptyList<LyricsLine>()
        val positionMs = 5000L
        val activeLineIndex = lyricsLines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        assertEquals(0, activeLineIndex)
    }

    @Test
    fun activeLyricLineIndex_findsCorrectActiveLine() {
        val lyricsLines = listOf(
            LyricsLine(timeMs = 0L, text = "Line 1"),
            LyricsLine(timeMs = 3000L, text = "Line 2"),
            LyricsLine(timeMs = 6000L, text = "Line 3"),
            LyricsLine(timeMs = 9000L, text = "Line 4")
        )

        // At 2000ms, should be Line 1 (index 0)
        val idx1 = lyricsLines.indexOfLast { it.timeMs <= 2000L }.coerceAtLeast(0)
        assertEquals(0, idx1)

        // At 5000ms, should be Line 2 (index 1)
        val idx2 = lyricsLines.indexOfLast { it.timeMs <= 5000L }.coerceAtLeast(0)
        assertEquals(1, idx2)

        // At 10000ms, should be Line 4 (index 3)
        val idx3 = lyricsLines.indexOfLast { it.timeMs <= 10000L }.coerceAtLeast(0)
        assertEquals(3, idx3)
    }

    @Test
    fun formatDuration_lessThanHour_returnsMinutesAndSeconds() {
        assertEquals(formatDuration(0L), "0:00")
        assertEquals(formatDuration(5000L), "0:05")
        assertEquals(formatDuration(75000L), "1:15")
        assertEquals(formatDuration(3599000L), "59:59")
    }

    @Test
    fun formatDuration_moreThanHour_returnsHoursMinutesAndSeconds() {
        assertEquals(formatDuration(3600000L), "1:00:00")
        assertEquals(formatDuration(9015000L), "2:30:15")
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
