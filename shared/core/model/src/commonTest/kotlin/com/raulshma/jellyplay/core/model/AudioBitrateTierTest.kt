package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the two invariants the adaptive bitrate selector relies on:
 *  - [AudioBitrateTier.DEFAULT] is [AudioBitrateTier.HIGH] (the out-of-the-box cap).
 *  - targetKbps is strictly monotonically increasing in declaration order, so
 *    ordinal order == quality order for the selector's stepped UI.
 */
class AudioBitrateTierTest {

    @Test
    fun default_isHigh() {
        assertEquals(AudioBitrateTier.HIGH, AudioBitrateTier.DEFAULT)
    }

    @Test
    fun targetKbps_strictlyIncreasesAcrossEntries() {
        val tiers = AudioBitrateTier.entries
        for (i in 1 until tiers.size) {
            assertTrue(
                tiers[i].targetKbps > tiers[i - 1].targetKbps,
                "${tiers[i]} (${tiers[i].targetKbps}) must exceed ${tiers[i - 1]} " +
                    "(${tiers[i - 1].targetKbps})",
            )
        }
    }
}
