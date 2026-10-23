package com.raulshma.jellyplay.core.data.image

import com.raulshma.jellyplay.core.model.CACHE_SIZE_DISABLED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure disk-cache sizing fold (`imageDiskCacheBytes`) both JVM
 * shells' Coil builders share: the user-configured MB wins when positive,
 * `0` is the picker's "Unlimited" (unbounded cache), and any other negative
 * — only reachable as a corrupt value, since the [CACHE_SIZE_DISABLED]
 * sentinel becomes `CachePolicy.DISABLED` in the builder before sizing runs
 * — degrades to the same 256 MB default the shells ran inline before the
 * fold.
 */
class JellyPlayImageLoaderTest {

    @Test
    fun zero_isUnlimited() {
        assertEquals(Long.MAX_VALUE, imageDiskCacheBytes(0))
    }

    @Test
    fun negative_fallsBackToThe256MbDefault_corruptValueNeverSizesToZeroOrNegative() {
        assertEquals(256L * 1024 * 1024, imageDiskCacheBytes(-2))
        assertEquals(256L * 1024 * 1024, imageDiskCacheBytes(Int.MIN_VALUE))
        // The Disabled sentinel never reaches this fold in production (the
        // builder intercepts it), but the fold must still refuse to size a
        // cache to zero or negative for it.
        assertTrue(imageDiskCacheBytes(CACHE_SIZE_DISABLED) > 0)
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
