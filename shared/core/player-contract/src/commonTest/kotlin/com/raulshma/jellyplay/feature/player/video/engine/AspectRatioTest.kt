package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of [AspectRatio] — the engine-neutral aspect-ratio
 * contract no media3 `RESIZE_MODE_*` integer may cross:
 *
 *  - The numeric [AspectRatio.ratio] is carried ONLY by the fixed-ratio
 *    entries (16:9, 4:3, 21:9) and is exactly the named fraction; the
 *    behaviour entries (AUTO/FIT/FILL/CROP) carry `null` so an engine can
 *    distinguish "no ratio requested" from any numeric override.
 *  - Every entry has a user-facing display name.
 */
class AspectRatioTest {

    @Test
    fun `fixed ratios carry their exact fractions`() {
        assertEquals(16f / 9f, AspectRatio.RATIO_16_9.ratio)
        assertEquals(4f / 3f, AspectRatio.RATIO_4_3.ratio)
        assertEquals(21f / 9f, AspectRatio.RATIO_21_9.ratio)
    }

    @Test
    fun `behaviour modes carry no numeric ratio`() {
        assertNull(AspectRatio.AUTO.ratio)
        assertNull(AspectRatio.FIT.ratio)
        assertNull(AspectRatio.FILL.ratio)
        assertNull(AspectRatio.CROP.ratio)
    }

    @Test
    fun `every entry has a display name`() {
        for (ratio in AspectRatio.entries) {
            assertTrue(ratio.displayName.isNotBlank(), ratio.name)
        }
    }

    @Test
    fun `display names match the picker labels`() {
        assertEquals("Auto", AspectRatio.AUTO.displayName)
        assertEquals("Fit", AspectRatio.FIT.displayName)
        assertEquals("Fill", AspectRatio.FILL.displayName)
        assertEquals("16:9", AspectRatio.RATIO_16_9.displayName)
        assertEquals("4:3", AspectRatio.RATIO_4_3.displayName)
        assertEquals("21:9", AspectRatio.RATIO_21_9.displayName)
        assertEquals("Crop", AspectRatio.CROP.displayName)
    }
}
