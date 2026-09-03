package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the invariants of [ArtworkColors]' derived presentation colors — the
 * swatch-to-surface math the detail screens paint from, computed at
 * construction from the five Palette swatch slots:
 *
 *  - Fallback chains (first non-null wins):
 *      tintedBackground      = darkMuted -> muted -> dominant -> 0xFF2D1F2D
 *      tintedBackgroundLight = muted -> darkMuted -> dominant -> 0xFF3D2F3D
 *      accentColor           = lightVibrant -> vibrant -> lightMuted -> 0xFFE8B4C8
 *      pillSurface           = darkMuted -> muted -> dominant -> 0xFF3A2A3A
 *      pillSurfaceDark       = darkMuted -> dominant -> 0xFF2A1A2A
 *  - Dark tint DARKENS the base (channels scaled by 0.45/0.35/0.45); the light
 *    tint LIGHTENS it (scaled by ~0.55 and offset by 0.08/0.05/0.08) — so the
 *    two backgrounds always diverge on a swatch set with any non-black base.
 *  - Pill surfaces are translucent overlays (alpha 0.55 / 0.7), not opaque.
 */
class ArtworkColorsTest {

    private val red = Color(0xFFCC2211)   // r=0.8, g=0.1333, b=0.0667
    private val blue = Color(0xFF2244CC)

    private fun colors(
        vibrant: Color? = null,
        darkVibrant: Color? = null,
        lightVibrant: Color? = null,
        muted: Color? = null,
        darkMuted: Color? = null,
        lightMuted: Color? = null,
        dominant: Color? = null,
    ) = ArtworkColors(
        vibrant = vibrant,
        darkVibrant = darkVibrant,
        lightVibrant = lightVibrant,
        muted = muted,
        darkMuted = darkMuted,
        lightMuted = lightMuted,
        dominant = dominant,
    )

    private fun channel(c: Color) = Triple(c.red, c.green, c.blue)

    @Test
    fun `dark tint darkens the darkMuted base by the documented factors`() {
        val colors = colors(darkMuted = red)
        val expected = Color(
            red = (red.red * 0.45f).coerceIn(0f, 1f),
            green = (red.green * 0.35f).coerceIn(0f, 1f),
            blue = (red.blue * 0.45f).coerceIn(0f, 1f),
            alpha = 1f,
        )
        assertEquals(expected, colors.tintedBackground)
        assertEquals(1f, colors.tintedBackground.alpha)
    }

    @Test
    fun `light tint lightens the muted base by the documented factors`() {
        val colors = colors(muted = blue)
        val expected = Color(
            red = (blue.red * 0.55f + 0.08f).coerceIn(0f, 1f),
            green = (blue.green * 0.45f + 0.05f).coerceIn(0f, 1f),
            blue = (blue.blue * 0.55f + 0.08f).coerceIn(0f, 1f),
            alpha = 1f,
        )
        assertEquals(expected, colors.tintedBackgroundLight)
    }

    @Test
    fun `dark tint prefers darkMuted then falls back to muted`() {
        val both = colors(darkMuted = red, muted = blue)
        assertEquals(channel(colors(darkMuted = red).tintedBackground), channel(both.tintedBackground))

        val mutedOnly = colors(muted = blue)
        assertEquals(channel(colors(muted = blue).tintedBackground), channel(mutedOnly.tintedBackground))
    }

    @Test
    fun `light tint prefers muted then falls back to darkMuted`() {
        val both = colors(darkMuted = red, muted = blue)
        assertEquals(
            channel(colors(muted = blue).tintedBackgroundLight),
            channel(both.tintedBackgroundLight),
        )
    }

    @Test
    fun `tints use dominant when no muted swatch exists`() {
        val colors = colors(dominant = red)
        val expectedDark = Color(
            red = (red.red * 0.45f).coerceIn(0f, 1f),
            green = (red.green * 0.35f).coerceIn(0f, 1f),
            blue = (red.blue * 0.45f).coerceIn(0f, 1f),
            alpha = 1f,
        )
        assertEquals(expectedDark, colors.tintedBackground)
    }

    @Test
    fun `all-null swatches fall back to the documented plum constants`() {
        val colors = colors()
        // 0xFF2D1F2D scaled for the dark background
        val plumDark = Color(0xFF2D1F2D)
        assertEquals(
            channel(
                Color(
                    red = plumDark.red * 0.45f,
                    green = plumDark.green * 0.35f,
                    blue = plumDark.blue * 0.45f,
                    alpha = 1f,
                ),
            ),
            channel(colors.tintedBackground),
        )
        // 0xFF3D2F3D lightened for the light background
        val plumLight = Color(0xFF3D2F3D)
        assertEquals(
            channel(
                Color(
                    red = plumLight.red * 0.55f + 0.08f,
                    green = plumLight.green * 0.45f + 0.05f,
                    blue = plumLight.blue * 0.55f + 0.08f,
                    alpha = 1f,
                ),
            ),
            channel(colors.tintedBackgroundLight),
        )
        // accent + pills hit their constants
        assertEquals(Color(0xFFE8B4C8), colors.accentColor)
    }

    @Test
    fun `accent color prefers lightVibrant then vibrant then lightMuted`() {
        assertEquals(red, colors(lightVibrant = red, vibrant = blue).accentColor)
        assertEquals(blue, colors(vibrant = blue, lightMuted = red).accentColor)
        assertEquals(red, colors(lightMuted = red).accentColor)
    }

    @Test
    fun `pill surfaces are translucent overlays on the dark-muted chain`() {
        val colors = colors(darkMuted = red)
        // Color quantizes channels (8-bit sRGB unpack: 0.55f -> 140/255 ~= 0.54902),
        // so alphas need a loose tolerance.
        assertEquals(0.55f, colors.pillSurface.alpha, 5e-3f)
        assertEquals(0.7f, colors.pillSurfaceDark.alpha, 5e-3f)
        // Same base chain for both pills when darkMuted exists.
        assertEquals(red.red, colors.pillSurface.red, 1e-6f)
        assertEquals(red.red, colors.pillSurfaceDark.red, 1e-6f)
    }

    @Test
    fun `pillSurfaceDark skips muted and jumps to dominant`() {
        val colors = colors(muted = blue, dominant = red)
        // pillSurface: darkMuted -> muted = blue
        assertEquals(blue.red, colors.pillSurface.red, 1e-6f)
        // pillSurfaceDark: darkMuted -> dominant = red (muted NOT consulted)
        assertEquals(red.red, colors.pillSurfaceDark.red, 1e-6f)
    }
}
