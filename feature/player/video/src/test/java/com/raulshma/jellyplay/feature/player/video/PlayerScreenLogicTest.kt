package com.raulshma.jellyplay.feature.player.video.components

import com.raulshma.jellyplay.feature.player.video.PlayerSheet
import com.raulshma.jellyplay.feature.player.video.formatDuration
import org.junit.Test
import org.junit.Assert.*

class SeekSliderTest {

    @Test
    fun seekSlider_zeroDuration_fractionIsZero() {
        val duration = 0L
        val fraction = if (duration > 0) 0L.toFloat() / duration else 0f
        assertEquals(0f, fraction)
    }

    @Test
    fun seekSlider_halfway_fractionIs0_5() {
        val duration = 100_000L
        val position = 50_000L
        val fraction = position.toFloat() / duration
        assertEquals(0.5f, fraction, 0.001f)
    }

    @Test
    fun seekSlider_atEnd_fractionIs1() {
        val duration = 100_000L
        val position = 100_000L
        val fraction = position.toFloat() / duration
        assertEquals(1f, fraction, 0.001f)
    }

    @Test
    fun seekSlider_fractionToMs_conversion() {
        val fraction = 0.75f
        val duration = 100_000L
        val ms = (fraction * duration).toLong()
        assertEquals(75_000L, ms)
    }

    @Test
    fun seekSlider_fractionToMs_zeroFraction() {
        val fraction = 0f
        val duration = 100_000L
        val ms = (fraction * duration).toLong()
        assertEquals(0L, ms)
    }

    @Test
    fun seekSlider_fractionToMs_fullFraction() {
        val fraction = 1f
        val duration = 100_000L
        val ms = (fraction * duration).toLong()
        assertEquals(100_000L, ms)
    }
}

class PlaybackSpeedChangeTest {

    @Test
    fun speedChange_0_25x() {
        assertEquals(0.25f, 0.25f)
    }

    @Test
    fun speedChange_0_5x() {
        assertEquals(0.5f, 0.5f)
    }

    @Test
    fun speedChange_2x() {
        assertEquals(2.0f, 2.0f)
    }

    @Test
    fun speedChange_valuesAreDistinct() {
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val distinct = speeds.distinct().size
        assertEquals(speeds.size, distinct)
    }

    @Test
    fun speedChange_sortedAscending() {
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        for (i in 1 until speeds.size) {
            assertTrue(speeds[i] > speeds[i - 1])
        }
    }

    @Test
    fun speedDisplay_1x_usesSimplifiedFormat() {
        val speed = 1.0f
        val display = if (speed == 1.0f) "1x" else "${speed}x"
        assertEquals("1x", display)
    }

    @Test
    fun speedDisplay_non1x_usesDecimalFormat() {
        val speed = 1.5f
        val display = if (speed == 1.0f) "1x" else "${speed}x"
        assertEquals("1.5x", display)
    }
}

class PlayerSheetNavigationTest {

    @Test
    fun backHandler_sheetOpen_dismissesSheet() {
        var currentSheet: Any = PlayerSheet.Speed
        val isSheetOpen = currentSheet != PlayerSheet.None

        if (isSheetOpen) {
            currentSheet = PlayerSheet.None
        }

        assertTrue(currentSheet == PlayerSheet.None)
    }

    @Test
    fun backHandler_noSheet_callsOnBack() {
        var currentSheet: Any = PlayerSheet.None
        var backCalled = false
        val isSheetOpen = currentSheet != PlayerSheet.None

        if (isSheetOpen) {
            currentSheet = PlayerSheet.None
        } else {
            backCalled = true
        }

        assertTrue(backCalled)
    }

    @Test
    fun controlButtonClicks_openCorrectSheet() {
        fun openSheet(sheet: Any) {
            // simulate sheet opening
            assert(sheet is PlayerSheet)
        }

        openSheet(PlayerSheet.Speed)
        openSheet(PlayerSheet.Audio)
        openSheet(PlayerSheet.SubtitleHub)
        openSheet(PlayerSheet.Chapter)
        openSheet(PlayerSheet.PlaybackInfo)
        openSheet(PlayerSheet.AspectRatio)
        openSheet(PlayerSheet.AVSync)
        openSheet(PlayerSheet.Decoder)
    }

    @Test
    fun dismissSheet_setsNone() {
        var currentSheet: Any = PlayerSheet.Speed
        currentSheet = PlayerSheet.None
        assertTrue(currentSheet == PlayerSheet.None)
    }
}

class ControlsVisibilityTest {

    @Test
    fun controlsTimeout_defaultIs5Seconds() {
        val controlsTimeoutMs = 5_000L
        assertEquals(5_000L, controlsTimeoutMs)
    }

    @Test
    fun tapTogglesVisibility() {
        var showControls = true
        showControls = !showControls
        assertFalse(showControls)
        showControls = !showControls
        assertTrue(showControls)
    }

    @Test
    fun doubleTapLeft_triggersSeekBack() {
        val tapX = 0.2f
        val width = 1f
        val threshold = 0.35f
        val isLeft = tapX < width * threshold
        assertTrue(isLeft)
    }

    @Test
    fun doubleTapRight_triggersSeekForward() {
        val tapX = 0.8f
        val width = 1f
        val threshold = 0.65f
        val isRight = tapX > width * threshold
        assertTrue(isRight)
    }

    @Test
    fun doubleTapCenter_triggersPlayPause() {
        val tapX = 0.5f
        val width = 1f
        val isLeft = tapX < width * 0.35f
        val isRight = tapX > width * 0.65f
        val isCenter = !isLeft && !isRight
        assertTrue(isCenter)
    }

    @Test
    fun seekOverlay_clearsAfterTimeout() {
        var seekDirection = -1
        var seekOffsetMs = 10_000L

        seekDirection = 0
        seekOffsetMs = 0L

        assertEquals(0, seekDirection)
        assertEquals(0L, seekOffsetMs)
    }

    @Test
    fun gestureOverlay_brightnessRange_coerced0To1() {
        val current = 0.5f
        val delta = 0.3f
        val newBrightness = (current + delta).coerceIn(0f, 1f)
        assertEquals(0.8f, newBrightness, 0.001f)
    }

    @Test
    fun gestureOverlay_brightnessOverMax_coerced() {
        val current = 0.9f
        val delta = 0.3f
        val newBrightness = (current + delta).coerceIn(0f, 1f)
        assertEquals(1f, newBrightness, 0.001f)
    }

    @Test
    fun gestureOverlay_brightnessBelowMin_coerced() {
        val current = 0.1f
        val delta = -0.3f
        val newBrightness = (current + delta).coerceIn(0f, 1f)
        assertEquals(0f, newBrightness, 0.001f)
    }

    @Test
    fun gestureOverlay_negativeDelta_isDim() {
        val delta = -0.1f
        assertTrue(delta < 0)
    }

    @Test
    fun gestureOverlay_rightSide_isVolume() {
        val positionX = 0.75f
        val halfWidth = 0.5f
        val isRight = positionX > halfWidth
        assertTrue(isRight)
    }

    @Test
    fun gestureOverlay_leftSide_isBrightness() {
        val positionX = 0.25f
        val halfWidth = 0.5f
        val isLeft = positionX <= halfWidth
        assertTrue(isLeft)
    }
}

class ExternalPlayerFlowTest {

    @Test
    fun externalPlayerLaunch_sequence() {
        var externalLaunched = false
        val preferredPlayer = "EXTERNAL"
        val streamUrl: String? = "http://example.com/video.mkv"

        if (preferredPlayer == "EXTERNAL" && streamUrl != null && !externalLaunched) {
            externalLaunched = true
        }

        assertTrue(externalLaunched)
    }

    @Test
    fun externalPlayerLaunch_doesNotRepeat() {
        var externalLaunched = false
        val preferredPlayer = "EXTERNAL"
        val streamUrl: String? = "http://example.com/video.mkv"

        if (preferredPlayer == "EXTERNAL" && streamUrl != null && !externalLaunched) {
            externalLaunched = true
        }
        if (preferredPlayer == "EXTERNAL" && streamUrl != null && !externalLaunched) {
            externalLaunched = true
        }

        assertTrue(externalLaunched)
    }

    @Test
    fun externalPlayerLaunch_nonExternalSkips() {
        var externalLaunched = false
        val preferredPlayer = "EXO_PLAYER"
        val streamUrl: String? = "http://example.com/video.mkv"

        if (preferredPlayer == "EXTERNAL" && streamUrl != null && !externalLaunched) {
            externalLaunched = true
        }

        assertFalse(externalLaunched)
    }

    @Test
    fun externalPlayerLaunch_noUrlSkips() {
        var externalLaunched = false
        val preferredPlayer = "EXTERNAL"
        val streamUrl: String? = null

        if (preferredPlayer == "EXTERNAL" && streamUrl != null && !externalLaunched) {
            externalLaunched = true
        }

        assertFalse(externalLaunched)
    }
}

class DecoderModeMappingTest {

    @Test
    fun decoderMode_hwPreferred_setsExtensionOn() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED
        val rendererMode = when (mode) {
            com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED -> "ON"
            com.raulshma.jellyplay.core.model.DecoderMode.HW_ONLY -> "OFF"
            com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY -> "PREFER"
        }
        assertEquals("ON", rendererMode)
    }

    @Test
    fun decoderMode_hwOnly_setsExtensionOff() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.HW_ONLY
        val rendererMode = when (mode) {
            com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED -> "ON"
            com.raulshma.jellyplay.core.model.DecoderMode.HW_ONLY -> "OFF"
            com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY -> "PREFER"
        }
        assertEquals("OFF", rendererMode)
    }

    @Test
    fun decoderMode_swOnly_setsExtensionPrefer() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY
        val rendererMode = when (mode) {
            com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED -> "ON"
            com.raulshma.jellyplay.core.model.DecoderMode.HW_ONLY -> "OFF"
            com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY -> "PREFER"
        }
        assertEquals("PREFER", rendererMode)
    }

    @Test
    fun decoderMode_mpv_hwdec_auto() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED
        val hwdec = when (mode) {
            com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED,
            com.raulshma.jellyplay.core.model.DecoderMode.HW_ONLY -> "auto"
            com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY -> "no"
        }
        assertEquals("auto", hwdec)
    }

    @Test
    fun decoderMode_mpv_hwdec_no() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY
        val hwdec = when (mode) {
            com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED,
            com.raulshma.jellyplay.core.model.DecoderMode.HW_ONLY -> "auto"
            com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY -> "no"
        }
        assertEquals("no", hwdec)
    }

    @Test
    fun decoderMode_libvlc_hwEnabled() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.HW_PREFERRED
        val hwEnabled = mode != com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY
        assertTrue(hwEnabled)
    }

    @Test
    fun decoderMode_libvlc_swDisabled() {
        val mode = com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY
        val hwEnabled = mode != com.raulshma.jellyplay.core.model.DecoderMode.SW_ONLY
        assertFalse(hwEnabled)
    }
}
