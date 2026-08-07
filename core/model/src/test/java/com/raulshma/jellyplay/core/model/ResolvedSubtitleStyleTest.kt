package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the shared subtitle resolution entry point ([resolveAgainst]) and
 * the [SubtitleRenderDefaults] table — the single source every engine + the
 * Compose overlay + the onboarding preview consume.
 */
class ResolvedSubtitleStyleTest {

    // ---- SubtitleRenderDefaults table ----

    @Test
    fun `defaults table matches SubtitleStyle model defaults`() {
        // "Defaults" must mean one thing. The table mirrors SubtitleStyle's
        // zero-arg constructor so a no-edit user sees consistent values.
        val d = SubtitleRenderDefaults.DEFAULT
        val model = SubtitleStyle()
        assertEquals(SubtitleRenderDefaults.REFERENCE_FONT_SIZE, d.fontSizeSp) // 24
        assertEquals(model.fontSize, d.fontSizeSp)
        assertEquals(SubtitleColor.WHITE.value, d.fontColor)
        assertEquals(SubtitleColor.BLACK.value, d.backgroundColor)
        assertEquals(0f, d.backgroundAlpha, 0.0001f)
        assertEquals(SubtitleColor.BLACK.value, d.edgeColor)
        assertEquals(SubtitleEdgeType.OUTLINE, d.edgeType)
        assertEquals(model.borderWidth, d.borderWidth, 0.0001f)
        assertEquals(model.shadowOffset, d.shadowOffset, 0.0001f)
        assertFalse(d.bold)
        assertFalse(d.italic)
    }

    // ---- resolveAgainst: custom branch ----

    @Test
    fun `custom branch uses ARGB over enum`() {
        val style = SubtitleStyle(
            applyCustomStyle = true,
            fontColor = SubtitleColor.WHITE,
            fontColorArgb = 0xFF112233.toInt(),
            edgeColor = SubtitleColor.BLACK,
            edgeColorArgb = 0xFF778899.toInt(),
        )
        val resolved = style.resolveAgainst()
        assertEquals(0xFF112233.toInt(), resolved.fontColorArgb)
        assertEquals(0xFF778899.toInt(), resolved.edgeColorArgb)
    }

    @Test
    fun `custom branch falls back to enum value when ARGB null`() {
        val style = SubtitleStyle(applyCustomStyle = true, fontColor = SubtitleColor.CYAN)
        assertEquals(SubtitleColor.CYAN.value, style.resolveAgainst().fontColorArgb)
    }

    @Test
    fun `custom branch carries user styling fields`() {
        val style = SubtitleStyle(
            applyCustomStyle = true,
            borderWidth = 4.5f,
            shadowOffset = 2.0f,
            fontSize = 30,
            bold = true,
            italic = true,
            edgeType = SubtitleEdgeType.DROP_SHADOW,
        )
        val resolved = style.resolveAgainst()
        assertEquals(4.5f, resolved.borderWidth, 0.0001f)
        assertEquals(2.0f, resolved.shadowOffset, 0.0001f)
        assertEquals(30, resolved.fontSizeSp)
        assertTrue(resolved.bold)
        assertTrue(resolved.italic)
        assertEquals(SubtitleEdgeType.DROP_SHADOW, resolved.edgeType)
    }

    // ---- resolveAgainst: default branch ----

    @Test
    fun `default branch returns the defaults table`() {
        val resolved = SubtitleStyle(applyCustomStyle = false, fontSize = 99).resolveAgainst()
        val d = SubtitleRenderDefaults.DEFAULT
        assertEquals(d.fontColor, resolved.fontColorArgb)
        assertEquals(d.backgroundColor, resolved.backgroundColorArgb)
        assertEquals(d.edgeColor, resolved.edgeColorArgb)
        assertEquals(d.edgeType, resolved.edgeType)
        assertEquals(d.borderWidth, resolved.borderWidth, 0.0001f)
        assertEquals(d.fontSizeSp, resolved.fontSizeSp) // 99 ignored
    }

    @Test
    fun `default branch preserves verticalPosition and offsetMs`() {
        // Layout fields carry over even when styling falls back to defaults.
        val style = SubtitleStyle(applyCustomStyle = false, verticalPosition = 0.15f, offsetMs = 1200L)
        val resolved = style.resolveAgainst()
        assertEquals(0.15f, resolved.verticalPosition, 0.0001f)
        assertEquals(1200L, resolved.offsetMs)
    }

    @Test
    fun `custom branch preserves verticalPosition and offsetMs`() {
        val style = SubtitleStyle(applyCustomStyle = true, verticalPosition = 0.2f, offsetMs = 500L)
        val resolved = style.resolveAgainst()
        assertEquals(0.2f, resolved.verticalPosition, 0.0001f)
        assertEquals(500L, resolved.offsetMs)
    }

    // ---- custom defaults table override ----

    @Test
    fun `default branch honours a custom defaults table`() {
        // Engines with a documented override (e.g. mpv border=3.0) pass their own table.
        val mpvDefaults = SubtitleRenderDefaults.DEFAULT.copy(borderWidth = 3.0f, shadowOffset = 0.0f)
        val resolved = SubtitleStyle(applyCustomStyle = false).resolveAgainst(mpvDefaults)
        assertEquals(3.0f, resolved.borderWidth, 0.0001f)
        assertEquals(0.0f, resolved.shadowOffset, 0.0001f)
    }
}
