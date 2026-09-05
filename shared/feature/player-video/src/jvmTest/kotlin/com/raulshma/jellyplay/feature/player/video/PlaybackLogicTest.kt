package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class TrackOptionTest {

    @Test
    fun trackOption_defaultValues() {
        val option = TrackOption(index = 0, label = "Test", language = "eng", isSelected = false)
        assertEquals(0, option.index)
        assertEquals(option.label, "Test")
        assertEquals(option.language, "eng")
        assertFalse(option.isSelected)
    }

    @Test
    fun trackOption_dataClassEquality() {
        val a = TrackOption(1, "English", "eng", true)
        val b = TrackOption(1, "English", "eng", true)
        assertEquals(a, b)
    }

    @Test
    fun trackOption_dataClassCopy() {
        val original = TrackOption(1, "English", "eng", true)
        val modified = original.copy(isSelected = false)
        assertTrue(original.isSelected)
        assertFalse(modified.isSelected)
    }

    @Test
    fun trackOption_negativeIndex_forDefaultTrack() {
        val option = TrackOption(-1, "Default", null, true)
        assertEquals(-1, option.index)
    }
}

class PlaybackPositionTicksTest {

    @Test
    fun positionMsToTicks_multiplyBy10k() {
        assertEquals(0L, 0L * 10_000)
        assertEquals(10_000_000L, 1_000L * 10_000)
        assertEquals(600_000_000L, 60_000L * 10_000)
        assertEquals(3_600_000_000L, 360_000L * 10_000)
    }

    @Test
    fun ticksToMs_divideBy10k() {
        assertEquals(0L, 0L / 10_000)
        assertEquals(1_000L, 10_000_000L / 10_000)
        assertEquals(60_000L, 600_000_000L / 10_000)
    }

    @Test
    fun startPositionTicksToMs_dividesBy10k() {
        val startPositionTicks = 1_200_000_000L
        val ms = startPositionTicks / 10_000
        assertEquals(120_000L, ms)
    }

    @Test
    fun startPositionTicks_zero() {
        assertEquals(0L, 0L / 10_000)
    }
}
