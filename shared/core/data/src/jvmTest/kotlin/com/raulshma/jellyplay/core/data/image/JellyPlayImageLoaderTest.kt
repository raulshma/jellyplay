package com.raulshma.jellyplay.core.data.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the pure disk-cache sizing fold (`imageDiskCacheBytes`) both JVM
 * shells' Coil builders share: the user-configured MB wins when positive,
 * everything else — unset (0), corrupt negative — degrades to the same
 * 256 MB default the shells ran inline before the fold.
 */
class JellyPlayImageLoaderTest {

    @Test
    fun zero_fallsBackToThe256MbDefault() {
        assertEquals(256L * 1024 * 1024, imageDiskCacheBytes(0))
    }

    @Test
    fun negative_fallsBackToThe256MbDefault_corruptValueNeverSizesToZeroOrNegative() {
        assertEquals(256L * 1024 * 1024, imageDiskCacheBytes(-1))
        assertEquals(256L * 1024 * 1024, imageDiskCacheBytes(Int.MIN_VALUE))
    }

    @Test
    fun oneMb_isTheSmallestConfiguredSize() {
        assertEquals(1024L * 1024, imageDiskCacheBytes(1))
    }

    @Test
    fun positiveMegabytes_convertToBytesExactly() {
        assertEquals(256L * 1024 * 1024, imageDiskCacheBytes(256))
        assertEquals(512L * 1024 * 1024, imageDiskCacheBytes(512))
    }

    @Test
    fun largeConfiguredSizes_stayLong_mathDoesNotOverflowInt() {
        // 4096 MB = 4 GiB — beyond Int.MAX_VALUE bytes; the MB→bytes fold
        // must stay in Long math (a 32-bit bytes conversion would wrap).
        assertEquals(4096L * 1024 * 1024, imageDiskCacheBytes(4096))
    }
}
