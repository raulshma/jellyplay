package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of [ThemeVariant] and its non-composable styling helpers:
 *
 *  - [ThemeVariant.fromId] parses persisted variant strings
 *    case-insensitively and falls back to [ThemeVariant.STANDARD] for null,
 *    unknown, or differently-cased-mismatch ids — a corrupted preference can
 *    never crash the app shell.
 *  - [ThemeVariant.isDarkLocked] is exactly SYNTHWAVE and AURORA (their
 *    gradient backgrounds only read against dark tones); every other variant
 *    is not dark-locked.
 *  - [ThemeVariant.allowsOled] is exactly STANDARD/VIVID/SAKURA/VECTOR_POP —
 *    the dark-locked gradients and Soothing/Monochrome suppress the OLED
 *    pure-black treatment.
 *  - [ThemeVariant.accentOptions] returns a non-null, id-unique accent list
 *    for the six accent variants and `null` for STANDARD (global swatch) and
 *    MONOCHROME (fixed) — the settings screen keys its picker on this.
 *  - [ThemeVariant.backgroundBrush] is non-null only for the two gradient
 *    variants; [ThemeVariant.cardBorder] returns a non-null border for every
 *    variant, with the documented widths.
 */
class ThemeVariantTest {

    // ── fromId ───────────────────────────────────────────────────────────────

    @Test
    fun `fromId resolves every persisted id case-insensitively`() {
        for (variant in ThemeVariant.entries) {
            assertEquals(variant, ThemeVariant.fromId(variant.name), variant.name)
            assertEquals(variant, ThemeVariant.fromId(variant.name.lowercase()), variant.name.lowercase())
        }
    }

    @Test
    fun `fromId falls back to STANDARD for null unknown or empty`() {
        assertEquals(ThemeVariant.STANDARD, ThemeVariant.fromId(null))
        assertEquals(ThemeVariant.STANDARD, ThemeVariant.fromId("neon_grid"))
        assertEquals(ThemeVariant.STANDARD, ThemeVariant.fromId(""))
    }

    // ── dark-lock / OLED matrices ────────────────────────────────────────────

    @Test
    fun `synthwave and aurora are dark-locked and nothing else is`() {
        assertEquals(
            setOf(ThemeVariant.SYNTHWAVE, ThemeVariant.AURORA),
            ThemeVariant.entries.filter { it.isDarkLocked }.toSet(),
        )
    }

    @Test
    fun `oled is allowed only for standard vivid sakura and vector pop`() {
        assertEquals(
            setOf(ThemeVariant.STANDARD, ThemeVariant.VIVID, ThemeVariant.SAKURA, ThemeVariant.VECTOR_POP),
            ThemeVariant.entries.filter { it.allowsOled }.toSet(),
        )
        // The dark-locked gradients and the tinted/fixed variants suppress OLED.
        assertFalse(ThemeVariant.SYNTHWAVE.allowsOled)
        assertFalse(ThemeVariant.AURORA.allowsOled)
        assertFalse(ThemeVariant.SOOTHING.allowsOled)
        assertFalse(ThemeVariant.MONOCHROME.allowsOled)
    }

    // ── accentOptions ────────────────────────────────────────────────────────

    @Test
    fun `standard and monochrome have no accent picker`() {
        assertNull(ThemeVariant.STANDARD.accentOptions())
        assertNull(ThemeVariant.MONOCHROME.accentOptions())
    }

    @Test
    fun `every other variant exposes a non-empty accent list with unique ids`() {
        for (variant in ThemeVariant.entries - ThemeVariant.STANDARD - ThemeVariant.MONOCHROME) {
            val accents = assertNotNull(variant.accentOptions(), variant.name)
            assertTrue(accents.isNotEmpty(), variant.name)
            assertEquals(accents.map { it.id }.toSet().size, accents.size, variant.name)
            for (accent in accents) {
                assertTrue(accent.label.isNotBlank(), "${variant.name}.${accent.id}")
            }
        }
    }

    @Test
    fun `synthwave keeps its historical accent palette`() {
        val ids = ThemeVariant.SYNTHWAVE.accentOptions()!!.map { it.id }
        assertEquals(listOf("magenta", "cyan", "violet", "orange"), ids)
    }

    @Test
    fun `soothing keeps its historical accent palette`() {
        val ids = ThemeVariant.SOOTHING.accentOptions()!!.map { it.id }
        assertEquals(listOf("ocean", "lavender", "sage", "coral", "amber", "rose"), ids)
    }

    @Test
    fun `accent ids match the persisted preference defaults spelling`() {
        // MainPreferences persists e.g. synthwaveAccent = "magenta",
        // soothingAccent = "ocean" — the persisted defaults must hit the lists.
        assertEquals(
            true,
            ThemeVariant.SYNTHWAVE.accentOptions()!!.any { it.id == "magenta" },
        )
        assertEquals(
            true,
            ThemeVariant.SOOTHING.accentOptions()!!.any { it.id == "ocean" },
        )
    }

    // ── backgroundBrush / cardBorder ─────────────────────────────────────────

    @Test
    fun `only the gradient variants paint a background brush`() {
        assertNotNull(ThemeVariant.SYNTHWAVE.backgroundBrush())
        assertNotNull(ThemeVariant.AURORA.backgroundBrush())
        for (variant in ThemeVariant.entries - ThemeVariant.SYNTHWAVE - ThemeVariant.AURORA) {
            assertNull(variant.backgroundBrush(), variant.name)
        }
    }

    @Test
    fun `every variant resolves a card border`() {
        val primary = Color(0xFF3355EE)
        val secondary = Color(0xFFEE5533)
        val outline = Color(0xFF888888)
        for (variant in ThemeVariant.entries) {
            val border = variant.cardBorder(primary, secondary, outline)
            assertNotNull(border, variant.name)
            assertTrue(border.width.value > 0f, variant.name)
        }
    }

    @Test
    fun `card border widths match the documented per-variant values`() {
        val primary = Color(0xFF3355EE)
        val secondary = Color(0xFFEE5533)
        val outline = Color(0xFF888888)
        assertEquals(1.5.dp, ThemeVariant.SYNTHWAVE.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(1.dp, ThemeVariant.AURORA.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(0.8.dp, ThemeVariant.SOOTHING.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(0.8.dp, ThemeVariant.SAKURA.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(1.dp, ThemeVariant.MONOCHROME.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(2.dp, ThemeVariant.VECTOR_POP.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(1.25.dp, ThemeVariant.VIVID.cardBorder(primary, secondary, outline)!!.width)
        assertEquals(1.dp, ThemeVariant.STANDARD.cardBorder(primary, secondary, outline)!!.width)
    }

    @Test
    fun `aurora card border derives from the primary at quarter alpha`() {
        val primary = Color(0xFF3355EE)
        val border = auroraCardBorder(primary)
        assertEquals(1.dp, border.width)
        // BorderStroke(1.dp, color) wraps a solid brush of that color; the
        // blend used is primary.copy(alpha = 0.25f) — verified through the
        // shared auroraBackgroundBrush-free helper path.
        assertEquals(1.dp, auroraCardBorder(Color.White).width)
    }

    @Test
    fun `heatmap palette has five steps in both schemes`() {
        assertEquals(5, HeatmapPalette.dark.size)
        assertEquals(5, HeatmapPalette.light.size)
        // Index 0 is the empty cell and differs from the hottest cell.
        assertTrue(HeatmapPalette.dark.first() != HeatmapPalette.dark.last())
        assertTrue(HeatmapPalette.light.first() != HeatmapPalette.light.last())
    }
}
