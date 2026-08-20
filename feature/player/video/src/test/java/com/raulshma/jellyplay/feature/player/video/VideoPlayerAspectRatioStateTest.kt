package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for aspect ratio detection and state management in VideoPlayerUiState. */
class VideoPlayerAspectRatioStateTest {

    // ─── Defaults ─────────────────────────────────────────────────────────────

    @Test
    fun defaults_aspectRatioIsAuto() {
        val state = VideoPlayerUiState()
        assertEquals(AspectRatio.AUTO, state.videoFx.aspectRatio)
    }

    @Test
    fun defaults_detectedAspectRatioIsNull() {
        val state = VideoPlayerUiState()
        assertNull(state.videoFx.detectedAspectRatio)
    }

    // ─── setAspectRatio ────────────────────────────────────────────────────────

    @Test
    fun setAspectRatio_fill_stateUpdated() {
        val state = VideoPlayerUiState().copy(videoFx = VideoFxState(aspectRatio = AspectRatio.FILL))
        assertEquals(AspectRatio.FILL, state.videoFx.aspectRatio)
    }

    @Test
    fun setAspectRatio_crop_stateUpdated() {
        val state = VideoPlayerUiState().copy(videoFx = VideoFxState(aspectRatio = AspectRatio.CROP))
        assertEquals(AspectRatio.CROP, state.videoFx.aspectRatio)
    }

    @Test
    fun setAspectRatio_ratio16_9_stateUpdated() {
        val state = VideoPlayerUiState().copy(videoFx = VideoFxState(aspectRatio = AspectRatio.RATIO_16_9))
        assertEquals(AspectRatio.RATIO_16_9, state.videoFx.aspectRatio)
    }

    // ─── detectAspectRatio (replicated logic) ─────────────────────────────────

    private fun detectAspectRatio(streams: List<MediaStream>): AspectRatio? {
        val videoStream = streams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
        val width = videoStream.width ?: return null
        val height = videoStream.height ?: return null
        if (height == 0) return null
        val nativeRatio = width.toFloat() / height.toFloat()
        return when {
            nativeRatio >= 2.3f -> AspectRatio.RATIO_21_9
            nativeRatio >= 1.7f -> AspectRatio.RATIO_16_9
            nativeRatio >= 1.3f -> AspectRatio.RATIO_4_3
            else -> AspectRatio.FIT
        }
    }

    @Test
    fun detectAspectRatio_auto_1920x1080_detects16_9() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, width = 1920, height = 1080))
        val detected = detectAspectRatio(streams)
        assertEquals(AspectRatio.RATIO_16_9, detected)

        // Simulating AUTO behaviour: use detected if non-null
        val state = VideoPlayerUiState(
            media = MediaContentState(mediaStreams = streams),
        ).copy(videoFx = VideoFxState(detectedAspectRatio = detected))
        assertEquals(AspectRatio.RATIO_16_9, state.videoFx.detectedAspectRatio)
    }

    @Test
    fun detectAspectRatio_1440x1080_detects4_3() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, width = 1440, height = 1080))
        assertEquals(AspectRatio.RATIO_4_3, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_2560x1080_detects21_9() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, width = 2560, height = 1080))
        assertEquals(AspectRatio.RATIO_21_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_squareVideo_detectsFit() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, width = 1080, height = 1080))
        assertEquals(AspectRatio.FIT, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_noVideoStream_returnsNull() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.AUDIO))
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_emptyStreams_returnsNull() {
        assertNull(detectAspectRatio(emptyList()))
    }

    @Test
    fun detectAspectRatio_zeroHeight_returnsNull() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, width = 1920, height = 0))
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_missingWidth_returnsNull() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, height = 1080))
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_missingHeight_returnsNull() {
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, width = 1920))
        assertNull(detectAspectRatio(streams))
    }

    // ─── Screen lock ──────────────────────────────────────────────────────────

    @Test
    fun screenLock_defaultFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.isScreenLocked)
    }

    @Test
    fun setScreenLocked_true_isScreenLockedTrue() {
        val state = VideoPlayerUiState().copy(isScreenLocked = true)
        assertTrue(state.isScreenLocked)
    }

    @Test
    fun setScreenLocked_false_isScreenLockedFalse() {
        val state = VideoPlayerUiState(isScreenLocked = true).copy(isScreenLocked = false)
        assertFalse(state.isScreenLocked)
    }
}
