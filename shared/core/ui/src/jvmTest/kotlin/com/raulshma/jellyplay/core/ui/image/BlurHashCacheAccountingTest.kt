package com.raulshma.jellyplay.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression tests for [BlurHashCache] byte-budget accounting: `put` on an
 * existing key used to ADD the new entry's bytes without subtracting the
 * replaced entry's old bytes, so `totalBytes` inflated monotonically and
 * evicted live entries long before the 2 MB budget was truly reached.
 *
 * Budget math: MAX_BYTES = 2 * 1024 * 1024 = 2_097_152 and per-entry bytes =
 * width * height * 4. All scenarios stay strictly observable through
 * `get()` (evicted entries read back as null); bitmap instances are shared
 * because the cache never inspects them, only the dimension-derived size.
 */
class BlurHashCacheAccountingTest {

    // 64 x 64 x 4 = 16_384 bytes per entry.
    private fun smallBitmap(): ImageBitmap = ImageBitmap(64, 64)

    // 500 x 100 x 4 = 200_000 bytes per entry.
    private fun mediumBitmap(): ImageBitmap = ImageBitmap(500, 100)

    private fun putSmall(key: String) = BlurHashCache.put(key, smallBitmap(), 64, 64)
    private fun putMedium(key: String) = BlurHashCache.put(key, mediumBitmap(), 500, 100)

    @Test
    fun `re-putting same key at same size keeps byte total exact - untouched canary survives`() {
        putSmall("canary")
        // 100 * 16_384 + 16_384 = 1_654_784 <= budget: everything genuinely fits.
        repeat(100) { putSmall("k$it") }
        // Every replace once added 16 KB of phantom bytes again (pre-fix total
        // would climb ~41 MB) -> the never-refreshed canary, always eldest,
        // was evicted within the first churn round.
        repeat(25) { round -> repeat(100) { i -> putSmall("k${(round * 100 + i) % 100}") } }
        assertNotNull(BlurHashCache.get("canary"))
    }

    @Test
    fun `re-putting same key repeatedly keeps every cached entry readable`() {
        repeat(100) { putSmall("k$it") }
        repeat(25) { repeat(100) { i -> putSmall("k$i") } }
        repeat(100) { assertNotNull(BlurHashCache.get("k$it")) }
    }

    @Test
    fun `replacing entry with larger one that still fits true budget evicts nothing`() {
        // Ten mediums = 2_000_000 <= 2_097_152 fits.
        repeat(10) { putMedium("m$it") }
        // Pre-fix the m0 replacement also fails to retire m0's old 200_000
        // bytes (phantom total 2_200_000 > budget) -> eldest m0 evicted
        // although the real sum 2_000_000 leaves headroom.
        putMedium("m0")
        repeat(10) { assertNotNull(BlurHashCache.get("m$it"), "m$it must survive") }
    }

    @Test
    fun `replacement pushing real total past budget evicts exactly the arithmetic shortfall`() {
        // Three 400 x 400 entries (640_000 each) = 1_920_000 fits.
        BlurHashCache.put("a", ImageBitmap(400, 400), 400, 400)
        BlurHashCache.put("b", ImageBitmap(400, 400), 400, 400)
        BlurHashCache.put("c", ImageBitmap(400, 400), 400, 400)
        // Re-place 'a' bigger: 1_920_000 - 640_000 + 1_000_000 = 2_280_000 >
        // budget by 182_848 -> exactly one 640_000-byte eviction (eldest 'b'),
        // landing on 1_640_000. Pre-fix the phantom total kept evicting ('c'
        // next, then 'a' itself hits the self-eviction break) leaving only 'c'.
        BlurHashCache.put("a", ImageBitmap(500, 500), 500, 500)
        assertNotNull(BlurHashCache.get("a"), "'a' is freshly inserted MRU")
        assertNull(BlurHashCache.get("b"), "'b' pays the exact eviction cost")
        assertNotNull(BlurHashCache.get("c"), "'c' predates 'a', is newer than 'b'")
    }

    @Test
    fun `fresh-entry overflow still evicts eldest exactly at the budget line`() {
        // Positive control: eviction machinery unchanged for plain inserts.
        // 512 x 512 x 4 = 1_048_576 per entry; third insert exceeds budget,
        // exactly the first entry is evicted, remaining pair sums to exactly
        // 2_097_152 so the loop halts.
        BlurHashCache.put("big1", ImageBitmap(512, 512), 512, 512)
        BlurHashCache.put("big2", ImageBitmap(512, 512), 512, 512)
        BlurHashCache.put("big3", ImageBitmap(512, 512), 512, 512)
        assertNull(BlurHashCache.get("big1"))
        assertNotNull(BlurHashCache.get("big2"))
        assertNotNull(BlurHashCache.get("big3"))
    }
}
