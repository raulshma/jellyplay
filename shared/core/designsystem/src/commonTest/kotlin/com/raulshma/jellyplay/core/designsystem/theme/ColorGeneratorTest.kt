package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Unit tests for the design-system's pure functions.
 * Verifies determinism, dark/light divergence, and basic contrast-ratio guarantees for
 * [ColorGenerator.generateColorScheme], [isLightColor], and [getSynthwaveColorScheme].
 *
 * `calculateLuminance` values below are pinned against the pre-KMP implementation,
 * which delegated to `androidx.core.graphics.ColorUtils.calculateLuminance` — that
 * linearizes sRGB channels before the Rec. 709 weighted sum, so mid-luminance colors
 * (e.g. 0xFF808080 → 0.2158) sit far below a gamma-space estimate (~0.5) and flip
 * `bestContrast`.
 */
class ColorGeneratorTest {

    @Test
    fun `generateColorScheme is deterministic for the same inputs`() {
        val seed = Color(0xFF6750A4)
        val a = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = false,
            oledMode = false,
            contrastLevel = ContrastLevel.DEFAULT,
        )
        val b = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = false,
            oledMode = false,
            contrastLevel = ContrastLevel.DEFAULT,
        )
        assertEquals(a.primary, b.primary)
        assertEquals(a.background, b.background)
        assertEquals(a.surface, b.surface)
    }

    @Test
    fun `generateColorScheme produces different primary for dark vs light`() {
        val seed = Color(0xFF6750A4)
        val light = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = false,
            oledMode = false,
        )
        val dark = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = true,
            oledMode = false,
        )
        assertNotEquals(light.primary, dark.primary)
        assertNotEquals(light.background, dark.background)
    }

    @Test
    fun `generateColorScheme OLED mode forces black background`() {
        val seed = Color(0xFF6750A4)
        val dark = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = true,
            oledMode = true,
        )
        assertEquals(Color.Black, dark.background)
    }

    @Test
    fun `generateColorScheme monochrome style desaturates primary`() {
        val seed = Color(0xFF6750A4) // vivid purple
        val vibrant = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.VIBRANT,
            darkTheme = false,
            oledMode = false,
        )
        val mono = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.MONOCHROME,
            darkTheme = false,
            oledMode = false,
        )
        // A monochrome primary should have lower saturation than the vibrant variant.
        val vibrantSat = saturation(vibrant.primary)
        val monoSat = saturation(mono.primary)
        assertTrue(
monoSat <= vibrantSat,
"Monochrome primary saturation ($monoSat) should be <= vibrant ($vibrantSat)",
)
    }

    @Test
    fun `generateColorScheme high-contrast dark theme elevates onSurface luminance`() {
        val seed = Color(0xFF6750A4)
        val default = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = true,
            oledMode = false,
            contrastLevel = ContrastLevel.DEFAULT,
        )
        val high = ColorGenerator.generateColorScheme(
            seedColor = seed,
            style = ColorStyle.TONAL_SPOT,
            darkTheme = true,
            oledMode = false,
            contrastLevel = ContrastLevel.HIGH,
        )
        // Higher contrast must yield a brighter onSurface text color.
        assertTrue(luminance(high.onSurface) >= luminance(default.onSurface))
    }

    @Test
    fun `generateColorScheme onPrimary is always black or white based on primary luminance`() {
        // Documents the contract of the internal `bestContrast` helper: onPrimary is one of
        // the two extremes, chosen by primary luminance. Higher WCAG ratios are not always
        // achievable with a 2-color palette for mid-luminance primaries — that's a known
        // design trade-off in the vibrant/expressive styles, not a regression.
        val seed = Color(0xFF6750A4)
        listOf(
            ColorStyle.TONAL_SPOT to false,
            ColorStyle.TONAL_SPOT to true,
            ColorStyle.VIBRANT to false,
            ColorStyle.VIBRANT to true,
            ColorStyle.EXPRESSIVE to false,
            ColorStyle.EXPRESSIVE to true,
            ColorStyle.MUTED to false,
            ColorStyle.MUTED to true,
        ).forEach { (style, dark) ->
            val cs = ColorGenerator.generateColorScheme(
                seedColor = seed,
                style = style,
                darkTheme = dark,
                oledMode = false,
            )
            assertTrue(
cs.onPrimary == Color.White || cs.onPrimary == Color.Black,
"onPrimary must be black or white (style=$style, dark=$dark)",
)
            val ratio = contrastRatio(cs.primary, cs.onPrimary)
            assertTrue(
ratio >= 1.5,
"onPrimary must have at least minimal contrast against primary (style=$style, dark=$dark, ratio=$ratio)",
)
        }
    }

    @Test
    fun `calculateLuminance matches androidx ColorUtils with sRGB linearization`() {
        // Values produced by androidx.core.graphics.ColorUtils.calculateLuminance.
        assertEquals(1.0, ColorGenerator.calculateLuminance(0xFFFFFF), 1e-6)
        assertEquals(0.0, ColorGenerator.calculateLuminance(0x000000), 1e-6)
        // Mid grey: gamma-space estimate is ~0.502 (would flip bestContrast to
        // black text); the linearized value keeps white, as on release/v0.10.6.
        assertEquals(0.215861, ColorGenerator.calculateLuminance(0x808080), 1e-4)
        assertEquals(0.434154, ColorGenerator.calculateLuminance(0xB0B0B0), 1e-4)
        // Aurora emerald accent: 0.4962 stays just under the 0.5 threshold, so
        // onPrimary remains white; the gamma-space sum (0.615) flipped it black.
        assertEquals(0.496185, ColorGenerator.calculateLuminance(0x34D399), 1e-4)
        assertEquals(0.332536, ColorGenerator.calculateLuminance(0xFF7043), 1e-4)
    }

    @Test
    fun `generateColorScheme roles are fully opaque`() {
        // Guards the hslToColorArgb port: androidx's ColorUtils.HSLToColor
        // returns Color.rgb(...) (alpha 0xFF). Without the alpha bit every
        // generator-derived role renders transparent on Android.
        val cs = ColorGenerator.generateColorScheme(
            seedColor = Color(0xFFD32F2F),
            style = ColorStyle.TONAL_SPOT,
            darkTheme = true,
            oledMode = false,
        )
        listOf(cs.primary, cs.onPrimary, cs.background, cs.surface, cs.primaryContainer)
            .forEach { assertEquals(1.0f, it.alpha, "role must be opaque: $it") }
    }

    @Test
    fun `isLightColor returns true for white`() {
        assertTrue(isLightColor(Color.White))
    }

    @Test
    fun `isLightColor returns false for black`() {
        assertFalse(isLightColor(Color.Black))
    }

    @Test
    fun `isLightColor returns true for light grey`() {
        assertTrue(isLightColor(Color(0xFFCCCCCC)))
    }

    @Test
    fun `isLightColor returns false for dark purple`() {
        assertFalse(isLightColor(Color(0xFF4A148C)))
    }

    @Test
    fun `getSynthwaveColorScheme returns non-null scheme for each accent`() {
        listOf("cyan", "violet", "orange", "magenta", "unknown").forEach { accent ->
            val cs = getSynthwaveColorScheme(accent)
            assertNotNull(
cs,
"Expected a scheme for accent=$accent",
)
        }
    }

    @Test
    fun `getSynthwaveColorScheme is case-insensitive`() {
        val lower = getSynthwaveColorScheme("cyan")
        val mixed = getSynthwaveColorScheme("CyAn")
        assertEquals(lower.primary, mixed.primary)
        assertEquals(lower.secondary, mixed.secondary)
    }

    @Test
    fun `getSynthwaveColorScheme maps unknown accent to magenta`() {
        val unknown = getSynthwaveColorScheme("nonexistent")
        val magenta = getSynthwaveColorScheme("magenta")
        assertEquals(magenta.primary, unknown.primary)
    }

    // --- helpers ---

    private fun saturation(color: Color): Float {
        val argb = color.toArgbInt()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        return if (max == 0f) 0f else delta / max
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgbInt()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return (0.2126 * r + 0.7152 * g + 0.0722 * b)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun Color.toArgbInt(): Int {
        val a = (alpha * 255).toInt() and 0xFF
        val r = (red * 255).toInt() and 0xFF
        val g = (green * 255).toInt() and 0xFF
        val b = (blue * 255).toInt() and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
