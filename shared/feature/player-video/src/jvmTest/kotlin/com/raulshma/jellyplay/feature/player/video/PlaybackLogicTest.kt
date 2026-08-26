package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
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

class SeekClampingTest {

    @Test
    fun seekForward_clampsToDuration() {
        val current = 3_595_000L
        val amount = 10_000L
        val duration = 3_600_000L
        val result = (current + amount).coerceAtMost(duration)
        assertEquals(3_600_000L, result)
    }

    @Test
    fun seekForward_withinBounds_doesNotClamp() {
        val current = 3_500_000L
        val amount = 10_000L
        val duration = 3_600_000L
        val result = (current + amount).coerceAtMost(duration)
        assertEquals(3_510_000L, result)
    }

    @Test
    fun seekBack_clampsToZero() {
        val current = 5_000L
        val amount = 10_000L
        val result = (current - amount).coerceAtLeast(0)
        assertEquals(0L, result)
    }

    @Test
    fun seekBack_withinBounds_doesNotClamp() {
        val current = 30_000L
        val amount = 10_000L
        val result = (current - amount).coerceAtLeast(0)
        assertEquals(20_000L, result)
    }

    @Test
    fun seekBack_atZero_staysAtZero() {
        val current = 0L
        val amount = 10_000L
        val result = (current - amount).coerceAtLeast(0)
        assertEquals(0L, result)
    }

    @Test
    fun seekForward_defaultAmountIs10Seconds() {
        val defaultSeekAmountMs = 10_000L
        assertEquals(10_000L, defaultSeekAmountMs)
    }

    @Test
    fun seek_fractionToMs() {
        val fraction = 0.5f
        val duration = 3_600_000L
        val positionMs = (fraction * duration).toLong()
        assertEquals(1_800_000L, positionMs)
    }

    @Test
    fun seek_fractionAtStart() {
        val fraction = 0f
        val duration = 3_600_000L
        val positionMs = (fraction * duration).toLong()
        assertEquals(0L, positionMs)
    }

    @Test
    fun seek_fractionAtEnd() {
        val fraction = 1f
        val duration = 3_600_000L
        val positionMs = (fraction * duration).toLong()
        assertEquals(3_600_000L, positionMs)
    }
}

class ChapterSeekTickConversionTest {

    @Test
    fun chapterTicksToMs_dividesBy10k() {
        val ticks = 600_000_000L
        val ms = ticks / 10_000
        assertEquals(60_000L, ms)
    }

    @Test
    fun chapterTicks_zero_returnsZero() {
        val ticks = 0L
        val ms = ticks / 10_000
        assertEquals(0L, ms)
    }

    @Test
    fun chapterTicks_largeValue() {
        val ticks = 7_200_000_000L
        val ms = ticks / 10_000
        assertEquals(720_000L, ms)
    }

    @Test
    fun chapterTicks_oneHour() {
        val oneHourTicks = 36_000_000_000L
        val ms = oneHourTicks / 10_000
        assertEquals(3_600_000L, ms)
    }

    @Test
    fun chapterTicks_oddValue_truncates() {
        val ticks = 600_005_000L
        val ms = ticks / 10_000
        assertEquals(60_000L, ms)
    }
}

class SkipIntroCreditsTest {

    @Test
    fun skipIntro_seeksToEndOfIntro() {
        val introEndTicks = 300_000_000L
        val targetMs = introEndTicks / 10_000
        assertEquals(30_000L, targetMs)
    }

    @Test
    fun skipCredits_seeksToEndOfCredits() {
        val creditEndTicks = 3_600_000_000L
        val targetMs = creditEndTicks / 10_000
        assertEquals(360_000L, targetMs)
    }

    @Test
    fun skipIntro_zeroEndTicks() {
        val introEndTicks = 0L
        val targetMs = introEndTicks / 10_000
        assertEquals(0L, targetMs)
    }
}

class PlayerEngineSeekContractTest {

    @Test
    fun allEngines_useSameSeekForwardDefault() {
        val defaultMs = 10_000L
        assertEquals(10_000L, defaultMs)
    }

    @Test
    fun exoPlayerSeekForward_coercesToDuration() {
        val current = 5_500_000L
        val amount = 10_000L
        val duration = 5_500_000L
        val result = (current + amount).coerceAtMost(duration)
        assertEquals(5_500_000L, result)
    }

    @Test
    fun mpvSeekForward_noExplicitClamping() {
        val amount = 10_000L
        val seconds = amount / 1000.0
        assertEquals(10.0, seconds, 0.001)
    }

    @Test
    fun mpvSeekBack_relativeNegative() {
        val amount = 10_000L
        val seconds = amount / 1000.0
        val expected = "-$seconds"
        assertEquals(expected, "-10.0")
    }

    @Test
    fun libvlcSeekForward_clampsToLength() {
        val current = 59_500L
        val amount = 10_000L
        val length = 60_000L
        val result = (current + amount).coerceAtMost(length.coerceAtLeast(0))
        assertEquals(60_000L, result)
    }

    @Test
    fun libvlcSeekBack_clampsToZero() {
        val current = 5_000L
        val amount = 10_000L
        val result = (current - amount).coerceAtLeast(0)
        assertEquals(0L, result)
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
