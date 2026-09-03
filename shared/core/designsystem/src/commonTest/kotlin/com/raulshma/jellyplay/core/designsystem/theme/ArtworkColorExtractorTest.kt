package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.ColorStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the invariants of [ArtworkColorExtractor] — the pure (platform-free)
 * half of artwork theming:
 *
 *  - [ArtworkColorExtractor.generateColorScheme] resolves its seed in this
 *    order: a recognised, non-DYNAMIC accent swatch name overrides everything;
 *    otherwise the artwork seed chain vibrant -> dominant -> muted; an
 *    unrecognised swatch name behaves exactly like "dynamic" (never throws).
 *  - With NO usable seed at all (all swatches null), the extractor returns the
 *    stock Material baseline scheme for the requested darkness — never a
 *    half-populated scheme.
 *  - Darkness, OLED and color style are forwarded to the generator unchanged,
 *    so the extractor is a seed resolver, not a second scheme builder.
 *  - [ArtworkColorExtractor.bestContrast] is a GAMMA-SPACE weighted-luminance
 *    pick with the threshold at 0.5: light backgrounds take the dark option,
 *    dark backgrounds the light one — including the mid-gray boundary case
 *    (0xFF808080 has gamma luminance ~0.502, so it takes the DARK option,
 *    unlike a linearized luminance which would sit far below the threshold).
 *
 * Scheme equality is asserted over the core color roles (ColorScheme does not
 * implement structural equals) via [assertSameScheme].
 */
class ArtworkColorExtractorTest {

    private val vibrant = Color(0xFF7C4DFF)

    /** ColorScheme has no structural equals — compare the core roles instead. */
    private fun assertSameScheme(expected: ColorScheme, actual: ColorScheme) {
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.onPrimary, actual.onPrimary)
        assertEquals(expected.primaryContainer, actual.primaryContainer)
        assertEquals(expected.secondary, actual.secondary)
        assertEquals(expected.tertiary, actual.tertiary)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.surfaceVariant, actual.surfaceVariant)
    }

    private fun artwork(
        vibrant: Color? = null,
        dominant: Color? = null,
        muted: Color? = null,
    ) = ArtworkColors(
        vibrant = vibrant,
        darkVibrant = null,
        lightVibrant = null,
        muted = muted,
        darkMuted = null,
        lightMuted = null,
        dominant = dominant,
    )

    // ── generateColorScheme ──────────────────────────────────────────────────

    @Test
    fun `no seed at all returns the stock baseline scheme`() {
        val empty = artwork()
        assertSameScheme(
            lightColorScheme(),
            ArtworkColorExtractor.generateColorScheme(empty, darkTheme = false),
        )
        assertSameScheme(
            darkColorScheme(),
            ArtworkColorExtractor.generateColorScheme(empty, darkTheme = true),
        )
    }

    @Test
    fun `dynamic swatch uses the artwork seed chain`() {
        val extractor = ArtworkColorExtractor.generateColorScheme(
            artwork(vibrant = vibrant),
            darkTheme = false,
            accentColorSwatch = "dynamic",
        )
        val direct = ColorGenerator.generateColorScheme(
            seedColor = vibrant,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = false,
            oledMode = false,
        )
        assertSameScheme(direct, extractor)
    }

    @Test
    fun `seed chain is vibrant then dominant then muted`() {
        val dominant = Color(0xFF33691E)
        val muted = Color(0xFF8D6E63)

        val fromDominant = ArtworkColorExtractor.generateColorScheme(
            artwork(dominant = dominant),
            darkTheme = false,
        )
        assertSameScheme(
            ColorGenerator.generateColorScheme(dominant, ColorStyle.TONAL_SPOT, darkTheme = false, oledMode = false),
            fromDominant,
        )

        val fromMuted = ArtworkColorExtractor.generateColorScheme(
            artwork(muted = muted),
            darkTheme = false,
        )
        assertSameScheme(
            ColorGenerator.generateColorScheme(muted, ColorStyle.TONAL_SPOT, darkTheme = false, oledMode = false),
            fromMuted,
        )

        // Vibrant outranks dominant when both exist.
        val both = ArtworkColorExtractor.generateColorScheme(
            artwork(vibrant = vibrant, dominant = dominant),
            darkTheme = false,
        )
        assertSameScheme(
            ColorGenerator.generateColorScheme(vibrant, ColorStyle.TONAL_SPOT, darkTheme = false, oledMode = false),
            both,
        )
    }

    @Test
    fun `a recognised non-dynamic swatch overrides the artwork`() {
        val sapphire = AccentColorSwatch.SAPPHIRE
        val lightScheme = ArtworkColorExtractor.generateColorScheme(
            artwork(vibrant = vibrant),
            darkTheme = false,
            accentColorSwatch = "sapphire",
        )
        assertSameScheme(
            ColorGenerator.generateColorScheme(
                Color(sapphire.lightColor),
                ColorStyle.TONAL_SPOT,
                darkTheme = false,
                oledMode = false,
            ),
            lightScheme,
        )
        // And it really differs from the artwork-seeded scheme.
        assertNotEquals(
            ColorGenerator.generateColorScheme(vibrant, ColorStyle.TONAL_SPOT, darkTheme = false, oledMode = false).primary,
            lightScheme.primary,
        )
    }

    @Test
    fun `swatch selection follows dark and light halves`() {
        val crimson = AccentColorSwatch.CRIMSON
        val dark = ArtworkColorExtractor.generateColorScheme(
            artwork(vibrant = vibrant),
            darkTheme = true,
            accentColorSwatch = "crimson",
        )
        assertSameScheme(
            ColorGenerator.generateColorScheme(
                Color(crimson.darkColor),
                ColorStyle.TONAL_SPOT,
                darkTheme = true,
                oledMode = false,
            ),
            dark,
        )
    }

    @Test
    fun `an unrecognised swatch name behaves like dynamic`() {
        val fromUnknown = ArtworkColorExtractor.generateColorScheme(
            artwork(vibrant = vibrant),
            darkTheme = true,
            accentColorSwatch = "not_a_swatch",
        )
        val fromDynamic = ArtworkColorExtractor.generateColorScheme(
            artwork(vibrant = vibrant),
            darkTheme = true,
            accentColorSwatch = "dynamic",
        )
        assertSameScheme(fromDynamic, fromUnknown)
    }

    @Test
    fun `oled and colorStyle are forwarded to the generator`() {
        val input = artwork(vibrant = vibrant)
        val forwarded = ColorGenerator.generateColorScheme(
            seedColor = vibrant,
            style = ColorStyle.VIBRANT,
            darkTheme = true,
            oledMode = true,
        )
        assertSameScheme(
            forwarded,
            ArtworkColorExtractor.generateColorScheme(
                input,
                darkTheme = true,
                oledMode = true,
                colorStyle = ColorStyle.VIBRANT,
            ),
        )
    }

    // ── bestContrast ─────────────────────────────────────────────────────────

    @Test
    fun `light backgrounds take the dark option`() {
        assertEquals(
            Color.Black,
            ArtworkColorExtractor.bestContrast(Color.White, light = Color.White, dark = Color.Black),
        )
    }

    @Test
    fun `dark backgrounds take the light option`() {
        assertEquals(
            Color.White,
            ArtworkColorExtractor.bestContrast(Color.Black, light = Color.White, dark = Color.Black),
        )
    }

    @Test
    fun `mid gray sits above the gamma threshold and takes the dark option`() {
        // Gamma-space luminance of 0xFF808080 is ~0.502 (> 0.5). Pinned because
        // a future switch to linearized luminance would flip this branch.
        val midGray = Color(0xFF808080)
        assertEquals(
            Color.Black,
            ArtworkColorExtractor.bestContrast(midGray, light = Color.White, dark = Color.Black),
        )
    }

    @Test
    fun `just-below-mid gray takes the light option`() {
        val darker = Color(0xFF7F7F7F)
        assertEquals(
            Color.White,
            ArtworkColorExtractor.bestContrast(darker, light = Color.White, dark = Color.Black),
        )
        assertTrue(darker.red < 0.502f)
    }
}
