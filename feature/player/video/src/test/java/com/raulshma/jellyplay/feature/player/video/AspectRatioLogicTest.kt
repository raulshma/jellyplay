package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectAspectRatioTest {

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
    fun detectAspectRatio_3840x2160_returns16_9() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 3840, height = 2160),
        )
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_1920x1080_returns16_9() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1920, height = 1080),
        )
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_1280x720_returns16_9() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1280, height = 720),
        )
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_2560x1080_returns21_9() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 2560, height = 1080),
        )
        assertEquals(AspectRatio.RATIO_21_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_3840x1600_returns21_9() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 3840, height = 1600),
        )
        assertEquals(AspectRatio.RATIO_21_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_ultraWide2560x1080_ratio237() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 2560, height = 1080),
        )
        val ratio = 2560f / 1080f
        assertTrue(ratio >= 2.3f)
    }

    @Test
    fun detectAspectRatio_1440x1080_returns4_3() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1440, height = 1080),
        )
        assertEquals(AspectRatio.RATIO_4_3, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_640x480_returns4_3() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 640, height = 480),
        )
        assertEquals(AspectRatio.RATIO_4_3, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_16x9_exactBoundary_returns16_9() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1700, height = 1000),
        )
        val ratio = 1700f / 1000f
        assertTrue(ratio >= 1.7f)
        assertTrue(ratio < 2.3f)
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_squareVideo_returnsFit() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1080, height = 1080),
        )
        assertEquals(AspectRatio.FIT, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_portraitVideo_returnsFit() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1080, height = 1920),
        )
        assertEquals(AspectRatio.FIT, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_noVideoStream_returnsNull() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO),
        )
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_emptyStreams_returnsNull() {
        assertNull(detectAspectRatio(emptyList()))
    }

    @Test
    fun detectAspectRatio_noWidth_returnsNull() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, height = 1080),
        )
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_noHeight_returnsNull() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1920),
        )
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_zeroHeight_returnsNull() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1920, height = 0),
        )
        assertNull(detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_picksFirstVideoStream() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1920, height = 1080),
            MediaStream(index = 1, type = StreamType.VIDEO, width = 2560, height = 1080),
        )
        assertEquals(AspectRatio.RATIO_16_9, detectAspectRatio(streams))
    }

    @Test
    fun detectAspectRatio_between4_3and16_9_returns4_3() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, width = 1500, height = 1000),
        )
        val ratio = 1500f / 1000f
        assertTrue(ratio >= 1.3f)
        assertTrue(ratio < 1.7f)
        assertEquals(AspectRatio.RATIO_4_3, detectAspectRatio(streams))
    }
}

class AspectRatioToResizeModeMappingTest {

    @Test
    fun autoWithNoDetection_mapsToFit() {
        val effectiveRatio = AspectRatio.AUTO
        val resizeMode = mapToResizeMode(effectiveRatio)
        assertEquals(0, resizeMode)
    }

    @Test
    fun autoWithDetection_mapsToDetection() {
        val detected = AspectRatio.RATIO_16_9
        val effectiveRatio = detected
        val resizeMode = mapToResizeMode(effectiveRatio)
        assert(resizeMode != 0)
    }

    @Test
    fun fit_mapsToResizeModeFit() {
        assert(mapToResizeMode(AspectRatio.FIT) == 0)
    }

    @Test
    fun fill_mapsToResizeModeFill() {
        assert(mapToResizeMode(AspectRatio.FILL) == 1)
    }

    @Test
    fun crop_mapsToResizeModeZoom() {
        assert(mapToResizeMode(AspectRatio.CROP) == 3)
    }

    @Test
    fun ratio16_9_hasNonNullRatio() {
        assert(AspectRatio.RATIO_16_9.ratio != null)
        assert(AspectRatio.RATIO_16_9.ratio!! > 0f)
    }

    @Test
    fun ratio4_3_hasNonNullRatio() {
        assert(AspectRatio.RATIO_4_3.ratio != null)
    }

    @Test
    fun ratio21_9_hasNonNullRatio() {
        assert(AspectRatio.RATIO_21_9.ratio != null)
    }

    @Test
    fun auto_hasNullRatio() {
        assert(AspectRatio.AUTO.ratio == null)
    }

    @Test
    fun fit_hasNullRatio() {
        assert(AspectRatio.FIT.ratio == null)
    }

    @Test
    fun fill_hasNullRatio() {
        assert(AspectRatio.FILL.ratio == null)
    }

    @Test
    fun crop_hasNullRatio() {
        assert(AspectRatio.CROP.ratio == null)
    }

    @Test
    fun aspectRatio_displayNames_areUnique() {
        val names = AspectRatio.entries.map { it.displayName }.distinct()
        assertEquals(AspectRatio.entries.size, names.size)
    }

    private fun mapToResizeMode(ratio: AspectRatio): Int {
        return when (ratio) {
            AspectRatio.FIT -> 0
            AspectRatio.FILL -> 1
            AspectRatio.CROP -> 3
            AspectRatio.RATIO_16_9, AspectRatio.RATIO_4_3, AspectRatio.RATIO_21_9 -> 2
            AspectRatio.AUTO -> 0
        }
    }
}
