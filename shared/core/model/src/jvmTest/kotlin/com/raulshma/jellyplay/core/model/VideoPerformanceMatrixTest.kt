package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The §5.14 matrix pin (issue #186): each tier's tuning values, HIGH as the
 * strict no-op, and the tuning objects as pure data (no store writes).
 */
class VideoPerformanceMatrixTest {

    @Test
    fun low_capsAt1080p8Mbps_andRelievesEveryEngineLayer() {
        val tuning = VideoPerformanceMatrix.forTier(DevicePerformanceTier.LOW)
        assertEquals(1080, tuning.maxVideoHeight)
        assertEquals(8_000_000L, tuning.maxVideoBitrate)
        assertEquals(MpvRenderQuality.PERFORMANCE, tuning.mpvRenderQuality)
        assertEquals(MpvScaler.BILINEAR, tuning.mpvScaler)
        assertTrue(tuning.forceMpvDebandOff)
        assertTrue(tuning.forceMpvInterpolationOff)
        assertEquals(1_500, tuning.minLibVlcNetworkCachingMs)
        assertEquals(2, tuning.libVlcDecoderThreads)
        assertEquals(1, tuning.maxLibVlcSkipLoopFilter)
        assertEquals(PreloadBufferSize.MEDIUM, tuning.maxPreloadBuffer)
        assertEquals(512, tuning.maxVideoCacheMb)
    }

    @Test
    fun medium_capsAt1440p20Mbps_andOnlyCapsTheCache() {
        val tuning = VideoPerformanceMatrix.forTier(DevicePerformanceTier.MEDIUM)
        assertEquals(1440, tuning.maxVideoHeight)
        assertEquals(20_000_000L, tuning.maxVideoBitrate)
        assertEquals(1024, tuning.maxVideoCacheMb)
        // No engine overrides on the middle tier (§5.3 matrix: user values).
        assertNull(tuning.mpvRenderQuality)
        assertNull(tuning.mpvScaler)
        assertFalse(tuning.forceMpvDebandOff)
        assertFalse(tuning.forceMpvInterpolationOff)
        assertNull(tuning.minLibVlcNetworkCachingMs)
        assertNull(tuning.libVlcDecoderThreads)
        assertNull(tuning.maxLibVlcSkipLoopFilter)
        assertNull(tuning.maxPreloadBuffer)
    }

    @Test
    fun high_hasNoCapsAndNoEngineOverrides() {
        assertEquals(VideoPerformanceTuning.NONE, VideoPerformanceMatrix.forTier(DevicePerformanceTier.HIGH))
        val tuning = VideoPerformanceMatrix.forTier(DevicePerformanceTier.HIGH)
        assertNull(tuning.maxVideoHeight)
        assertNull(tuning.maxVideoBitrate)
        assertNull(tuning.mpvRenderQuality)
        assertNull(tuning.mpvScaler)
        assertNull(tuning.maxPreloadBuffer)
        assertNull(tuning.maxVideoCacheMb)
    }

    @Test
    fun matrix_isTotalOverEveryTier() {
        // §5.2: "never persisted; derived from the tier at runtime" — the
        // tuning is plain data (no store handles to write through), and the
        // matrix must resolve every tier a detection or pinned profile can
        // produce.
        for (tier in DevicePerformanceTier.entries) {
            assertEquals(tier, VideoPerformanceMatrix.forTier(tier).tier)
        }
    }
}
