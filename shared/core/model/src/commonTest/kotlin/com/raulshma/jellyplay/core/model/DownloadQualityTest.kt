package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of [DownloadQuality.maxBitrate] — the transcode bitrate
 * caps (bits/s) a downloaded file is limited to per quality tier:
 *
 *  - ORIGINAL imposes NO cap (`null`), so an original-quality download is
 *    never handed a `maxBitrate` that would silently transcode it.
 *  - Every transcoded tier caps strictly below the tier above it
 *    (1080p > 720p > 480p), aligned with the player's adaptive-streaming
 *    presets.
 */
class DownloadQualityTest {

    @Test
    fun `original quality has no bitrate cap`() {
        assertNull(DownloadQuality.ORIGINAL.maxBitrate)
    }

    @Test
    fun `transcode tiers pin their documented caps`() {
        assertEquals(8_000_000, DownloadQuality.HIGH_1080P.maxBitrate)
        assertEquals(3_000_000, DownloadQuality.MEDIUM_720P.maxBitrate)
        assertEquals(1_500_000, DownloadQuality.LOW_480P.maxBitrate)
    }

    @Test
    fun `caps strictly decrease with quality`() {
        val high = DownloadQuality.HIGH_1080P.maxBitrate!!
        val medium = DownloadQuality.MEDIUM_720P.maxBitrate!!
        val low = DownloadQuality.LOW_480P.maxBitrate!!
        assertTrue(high > medium)
        assertTrue(medium > low)
    }

    @Test
    fun `every transcoded tier maps to a positive cap`() {
        // Exhaustiveness guard: adding a new DownloadQuality without a
        // maxBitrate arm is a compile error, but a new entry silently
        // defaulting to null would break this loop invariant.
        for (quality in DownloadQuality.entries) {
            if (quality != DownloadQuality.ORIGINAL) {
                val cap = quality.maxBitrate
                assertTrue(cap != null && cap > 0, quality.name)
            }
        }
    }
}
