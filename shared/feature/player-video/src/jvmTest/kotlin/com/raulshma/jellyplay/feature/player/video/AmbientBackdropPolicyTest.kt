package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the ambient-backdrop policy (#12, [AmbientBackdropPolicy.kt]): the
 * aspect comparison that decides whether the video is letterboxed/pillarboxed
 * (bars exist → blurred backdrop), and the video-stream aspect derivation.
 */
class AmbientBackdropPolicyTest {

    @Test
    fun disabledIsAlwaysOff() {
        assertFalse(shouldShowAmbientBackdrop(2.35f, 16f / 9f, enabled = false))
    }

    @Test
    fun unknownGeometryStaysOff() {
        // No stream dims yet (load in flight / audio-only) — never guess.
        assertFalse(shouldShowAmbientBackdrop(null, 16f / 9f, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(16f / 9f, null, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(0f, 16f / 9f, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(16f / 9f, -1f, enabled = true))
    }

    @Test
    fun aFillingVideoHasNoBars() {
        assertFalse(shouldShowAmbientBackdrop(16f / 9f, 16f / 9f, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(1.777f, 1.778f, enabled = true), "rounding-level sameness fills")
    }

    @Test
    fun letterboxedAndPillarboxedVideosShowTheBackdrop() {
        // 2.35:1 cinemascope on a 16:9 screen — letterbox bars.
        assertTrue(shouldShowAmbientBackdrop(2.35f, 16f / 9f, enabled = true))
        // 4:3 classic TV on a 16:9 screen — pillarbox bars.
        assertTrue(shouldShowAmbientBackdrop(4f / 3f, 16f / 9f, enabled = true))
        // A phone-portrait video on a landscape screen.
        assertTrue(shouldShowAmbientBackdrop(9f / 16f, 16f / 9f, enabled = true))
        // Aspect order is irrelevant (the comparison is symmetric).
        assertTrue(shouldShowAmbientBackdrop(16f / 9f, 2.35f, enabled = true))
    }

    @Test
    fun aDifferenceInsideTheToleranceCountsAsFilling() {
        // ~1.1% spread — below the 2% tolerance, treated as no bars.
        assertFalse(shouldShowAmbientBackdrop(1.78f, 1.801f, enabled = true))
    }

    @Test
    fun theToleranceConstantMatchesTheContract() {
        assertEquals(1.02f, AMBIENT_ASPECT_TOLERANCE)
    }
}

class VideoStreamAspectTest {

    private fun videoStream(width: Int?, height: Int?) = MediaStream(
        index = 0,
        type = StreamType.VIDEO,
        width = width,
        height = height,
    )

    @Test
    fun derivesFromTheFirstVideoStream() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO),
            videoStream(1920, 1080),
            videoStream(640, 480),
        )
        assertEquals(1920f / 1080f, videoStreamAspect(streams))
    }

    @Test
    fun nullWithoutUsableGeometry() {
        assertNull(videoStreamAspect(emptyList()))
        assertNull(videoStreamAspect(listOf(MediaStream(index = 0, type = StreamType.AUDIO))))
        assertNull(videoStreamAspect(listOf(videoStream(null, 1080))))
        assertNull(videoStreamAspect(listOf(videoStream(1920, null))))
        assertNull(videoStreamAspect(listOf(videoStream(0, 1080))))
        assertNull(videoStreamAspect(listOf(videoStream(1920, 0))))
    }
}
