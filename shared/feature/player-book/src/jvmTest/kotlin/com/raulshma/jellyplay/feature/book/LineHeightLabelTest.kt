package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the line-height slider's × multiplier caption: percent-of-base in,
 * one decimal out, rounded half-up — the caption replaces the resource
 * pipeline's `%1$.1f` conversion, which compose-resources cannot render.
 */
class LineHeightLabelTest {

    @Test
    fun `whole and half multipliers render as one decimal`() {
        assertEquals("1.0", lineHeightLabel(100))
        assertEquals("1.5", lineHeightLabel(150))
        assertEquals("2.0", lineHeightLabel(200))
    }

    @Test
    fun `intermediate values round half-up instead of truncating`() {
        // 1.05 truncates to "1.0"; rounding matches the old %.1f semantics.
        assertEquals("1.1", lineHeightLabel(105))
        assertEquals("1.1", lineHeightLabel(114))
        assertEquals("1.2", lineHeightLabel(115))
        assertEquals("2.0", lineHeightLabel(199))
    }

    @Test
    fun `the store default renders exactly`() {
        assertEquals("1.6", lineHeightLabel(160))
    }
}
