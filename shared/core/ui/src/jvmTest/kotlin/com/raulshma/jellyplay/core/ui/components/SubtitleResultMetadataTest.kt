package com.raulshma.jellyplay.core.ui.components

import kotlin.test.assertEquals
import kotlin.test.Test

class SubtitleResultMetadataTest {

    @Test
    fun underThousand_renderedAsIs() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("1", formatCompactCount(1))
        assertEquals("999", formatCompactCount(999))
    }

    @Test
    fun exactThousand_compactsToOneK() {
        assertEquals("1k", formatCompactCount(1000))
    }

    @Test
    fun lowThousands_keepOneDecimal() {
        assertEquals("1.2k", formatCompactCount(1200))
    }

    @Test
    fun highThousands_dropTrailingZero() {
        assertEquals("12k", formatCompactCount(12_000))
    }

    @Test
    fun millions_capAtK_suffix() {
        // The metadata line only uses "k" (no M/G) — a million stays in thousands.
        assertEquals("1000k", formatCompactCount(1_000_000))
    }
}
