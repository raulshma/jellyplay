package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests [detectAspectRatio] — the pure aspect-ratio derivation previously
 * trapped as a private method on VideoPlayerViewModel (untestable through the
 * VM interface). This is the first unit test in the player:video module.
 */
class AspectRatioDetectorTest {

    private fun video(width: Int?, height: Int?) =
        MediaStream(index = 0, type = StreamType.VIDEO, width = width, height = height)

    @Test
    fun `returns null when no video stream present`() {
        assertNull(detectAspectRatio(listOf(MediaStream(index = 0, type = StreamType.AUDIO))))
        assertNull(detectAspectRatio(emptyList()))
    }

    @Test
    fun `returns null when dimensions missing`() {
        assertNull(detectAspectRatio(listOf(video(null, 1080))))
        assertNull(detectAspectRatio(listOf(video(1920, null))))
    }

    @Test
    fun `returns null when height is zero to avoid divide by zero`() {
        assertNull(detectAspectRatio(listOf(video(1920, 0))))
    }

    @Test
    fun `cinemascope ratio maps to 21_9`() {
        // 2560x1080 ≈ 2.37
        assertEquals(AspectRatio.RATIO_21_9, detectAspectRatio(listOf(video(2560, 1080))))
    }

    @Test
    fun `widescreen HD maps to 16_9`() {
        // 1920x1080 ≈ 1.78
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(listOf(video(1920, 1080))))
    }

    @Test
    fun `classic TV maps to 4_3`() {
        // 1440x1080 = 1.33
        assertEquals(AspectRatio.RATIO_4_3, detectAspectRatio(listOf(video(1440, 1080))))
    }

    @Test
    fun `portrait or sub-4_3 maps to FIT`() {
        // 1080x1920 = 0.5625 (portrait phone)
        assertEquals(AspectRatio.FIT, detectAspectRatio(listOf(video(1080, 1920))))
        // 1.0 (square)
        assertEquals(AspectRatio.FIT, detectAspectRatio(listOf(video(1080, 1080))))
    }

    @Test
    fun `boundary at 2_3 includes 21_9`() {
        // Exactly 2.3 → 21:9 (>= is inclusive)
        // 23 : 10 → 2.3
        assertEquals(AspectRatio.RATIO_21_9, detectAspectRatio(listOf(video(2300, 1000))))
    }

    @Test
    fun `boundary at 1_7 includes 16_9`() {
        // 17:10 = 1.7
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(listOf(video(1700, 1000))))
    }

    @Test
    fun `audio streams are ignored even when listed first`() {
        val audio = MediaStream(index = 0, type = StreamType.AUDIO)
        val videoStream = video(1920, 1080)
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(listOf(audio, videoStream)))
    }
}
