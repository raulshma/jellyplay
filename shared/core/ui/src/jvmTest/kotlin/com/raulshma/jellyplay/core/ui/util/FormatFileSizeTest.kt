package com.raulshma.jellyplay.core.ui.util

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins formatFileSize's one-decimal SI outputs (the `%.1f` JVM formatting
 * contract) ahead of the wasmJs commonMain extraction, so any drift in the
 * shared replacement shows up as a test failure instead of a UI string change.
 */
class FormatFileSizeTest {

    private val originalLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun pinUsLocale() {
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `below one KB renders raw bytes`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1 B", formatFileSize(1))
        assertEquals("999 B", formatFileSize(999))
    }

    @Test
    fun `one KB boundary keeps one decimal`() {
        assertEquals("1.0 KB", formatFileSize(1_000))
        assertEquals("1.5 KB", formatFileSize(1_500))
    }

    @Test
    fun `MB range formats against SI divisor`() {
        assertEquals("1.0 MB", formatFileSize(1_000_000))
        // 1048576 / 10^6 = 1.048576 -> "%.1f" rounds to 1.0.
        assertEquals("1.0 MB", formatFileSize(1_048_576))
        assertEquals("500.0 MB", formatFileSize(500_000_000))
    }

    @Test
    fun `GB range rounds half-up at the decimal`() {
        assertEquals("2.5 GB", formatFileSize(2_500_000_000L))
        // 3612000000 / 10^9 = 3.612 -> 3.6.
        assertEquals("3.6 GB", formatFileSize(3_612_000_000L))
        // 2.25 GB is a binary-exact true tie: HALF_UP -> 2.3 where HALF_EVEN
        // (or truncation) would print 2.2 — this line actually discriminates.
        assertEquals("2.3 GB", formatFileSize(2_250_000_000L))
    }
}
