package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeMediaRowsTest {

    @Test
    fun playbackProgressRatio_calculatesCorrectPercentage() {
        val playbackPositionTicks = 300_000_000L // 30 sec
        val runTimeTicks = 600_000_000L // 60 sec

        val progress = playbackPositionTicks.toFloat() / runTimeTicks.toFloat()
        assertEquals(0.5f, progress, 0.01f)
    }

    @Test
    fun playbackProgressRatio_handlesZeroRunTime() {
        val playbackPositionTicks = 100L
        val runTimeTicks = 0L

        val progress = if (runTimeTicks > 0) playbackPositionTicks.toFloat() / runTimeTicks.toFloat() else 0f
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun mediaItem_continueWatchingProgress_clampedToOne() {
        val playbackPositionTicks = 700_000_000L
        val runTimeTicks = 600_000_000L

        val progress = (playbackPositionTicks.toFloat() / runTimeTicks.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, progress, 0.001f)
    }
}
