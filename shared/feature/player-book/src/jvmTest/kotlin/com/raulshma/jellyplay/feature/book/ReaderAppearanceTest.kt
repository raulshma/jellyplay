package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.PerBookAppearance
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the pure appearance math: the per-book override resolution
 * (override / global / cleared), the ReaderFontFamily → CSS stack mapping
 * (SYSTEM must clear), the margins percent → px band, and the brightness
 * dim-veil alpha.
 */
class ReaderAppearanceTest {

    private val globals = ReaderSlice(readerTheme = ReaderTheme.DARK, readerFontSizePx = 17)

    @Test
    fun `override wins over the global slice per axis`() {
        val resolved = effectiveAppearance(
            globals,
            PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 22),
        )
        assertEquals(ReaderTheme.SEPIA, resolved.theme)
        assertEquals(22, resolved.fontSizePx)
    }

    @Test
    fun `null override axes fall through to the globals`() {
        val resolved = effectiveAppearance(globals, PerBookAppearance(theme = ReaderTheme.LIGHT))
        assertEquals(ReaderTheme.LIGHT, resolved.theme)
        assertEquals(17, resolved.fontSizePx)

        val fontOnly = effectiveAppearance(globals, PerBookAppearance(fontSizePx = 20))
        assertEquals(ReaderTheme.DARK, fontOnly.theme)
        assertEquals(20, fontOnly.fontSizePx)
    }

    @Test
    fun `a cleared override resolves to the globals`() {
        assertEquals(
            effectiveAppearance(globals, PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 20)),
            EffectiveAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 20),
        )
        // The cleared state IS the null override — the global slice verbatim.
        assertEquals(
            EffectiveAppearance(theme = ReaderTheme.DARK, fontSizePx = 17),
            effectiveAppearance(globals, perBook = null),
        )
    }

    @Test
    fun `font family maps to the reader css stacks`() {
        assertNull(ReaderFontFamily.SYSTEM.epubCssStack())
        assertEquals("Georgia, 'Times New Roman', serif", ReaderFontFamily.SERIF.epubCssStack())
        assertEquals("Verdana, Arial, sans-serif", ReaderFontFamily.SANS.epubCssStack())
        assertEquals("'Courier New', monospace", ReaderFontFamily.MONO.epubCssStack())
    }

    @Test
    fun `margins percent maps linearly onto the px band`() {
        assertEquals(0, epubMarginsPx(0))
        assertEquals(5, epubMarginsPx(8)) // the default: 8 % → 5 px
        assertEquals(32, epubMarginsPx(50))
        assertEquals(64, epubMarginsPx(100))
        // Out-of-band inputs clamp, never overshoot the ceiling.
        assertEquals(64, epubMarginsPx(150))
        assertEquals(0, epubMarginsPx(-5))
    }

    @Test
    fun `line height percent maps to the multiplier`() {
        assertEquals(1.0, epubLineHeight(100))
        assertEquals(1.6, epubLineHeight(160))
        assertEquals(2.0, epubLineHeight(200))
        assertEquals(1.0, epubLineHeight(50))
    }

    @Test
    fun `brightness dim alpha is zero at 100 and capped at 75 percent black`() {
        assertEquals(0f, brightnessDimAlpha(100))
        assertEquals(0.375f, brightnessDimAlpha(50))
        assertEquals(0.75f, brightnessDimAlpha(0))
        // Clamped outside the band — never brighter, never full black.
        assertEquals(0f, brightnessDimAlpha(120))
        assertEquals(0.75f, brightnessDimAlpha(-10))
    }
}
