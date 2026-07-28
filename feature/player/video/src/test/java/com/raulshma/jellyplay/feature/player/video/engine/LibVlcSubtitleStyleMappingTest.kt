package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcSubtitleStyleMappingTest {

    @Test
    fun typefaceOptions_honorBothTogglesAndBundledFallback() {
        val options = LibVlcSubtitleStyleMapping.typefaceOptions(
            SubtitleStyle(bold = true, italic = true),
            bundledFallbackPath = "/fonts/subfont.ttf",
        )

        assertEquals(
            listOf(
                ":freetype-bold=true",
                ":freetype-italic=true",
                ":freetype-font=/fonts/subfont.ttf",
            ),
            options,
        )
    }

    @Test
    fun typefaceOptions_useSelectedFontAndCanDisableBothToggles() {
        val options = LibVlcSubtitleStyleMapping.typefaceOptions(
            SubtitleStyle(fontFamilyPath = "/fonts/custom.ttf"),
            bundledFallbackPath = "/fonts/subfont.ttf",
        )

        assertEquals(
            listOf(
                ":freetype-bold=false",
                ":freetype-italic=false",
                ":freetype-font=/fonts/custom.ttf",
            ),
            options,
        )
    }

    @Test
    fun colorOptions_resolveThroughSubtitleColorResolverNotEnumValue() {
        // Free-form ARGB (0xFF112233) must win over the WHITE enum. Previously
        // LibVLC read style.fontColor.value directly and dropped the ARGB field.
        val style = SubtitleStyle(
            fontSize = 32,
            fontColor = SubtitleColor.WHITE,
            fontColorArgb = 0xFF112233.toInt(),
            backgroundColor = SubtitleColor.BLACK,
            backgroundColorArgb = 0xFF445566.toInt(),
            edgeColor = SubtitleColor.BLACK,
            edgeColorArgb = 0xFF778899.toInt(),
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        // FreeType wants RGB (alpha stripped) and the Int renders as a decimal
        // string (e.g. 0xFFFFFF → 16777215); alpha is conveyed via background-opacity.
        assertEquals((0x112233).toString(), options[":freetype-color"])
        assertEquals((0x445566).toString(), options[":freetype-background-color"])
        assertEquals((0x778899).toString(), options[":freetype-outline-color"])
        assertEquals("32", options[":freetype-rel-fontsize"])
    }

    @Test
    fun colorOptions_opaqueBoxForcesFullBackgroundOpacity() {
        val style = SubtitleStyle(
            borderStyle = SubtitleBorderStyle.OPAQUE_BOX,
            backgroundOpacity = 0.0f, // ignored for OPAQUE_BOX
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        assertEquals("255", options[":freetype-background-opacity"])
    }

    @Test
    fun colorOptions_backgroundBoxHonorsBackgroundOpacity() {
        val style = SubtitleStyle(
            borderStyle = SubtitleBorderStyle.BACKGROUND_BOX,
            backgroundOpacity = 0.5f,
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        assertEquals("127", options[":freetype-background-opacity"])
    }

    @Test
    fun colorOptions_outlineAndShadowYieldsTransparentBackground() {
        val style = SubtitleStyle(
            borderStyle = SubtitleBorderStyle.OUTLINE_AND_SHADOW,
            backgroundOpacity = 1.0f, // ignored — outline/shadow is the legibility aid
            edgeType = SubtitleEdgeType.OUTLINE,
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        assertEquals("0", options[":freetype-background-opacity"])
        assertEquals("2", options[":freetype-outline-thickness"])
    }

    @Test
    fun colorOptions_dropShadowAddsShadowOpacity() {
        val options = LibVlcSubtitleStyleMapping.colorOptions(
            SubtitleStyle(edgeType = SubtitleEdgeType.DROP_SHADOW)
        )
        assertTrue("expected :freetype-shadow-opacity=255", options.any { it == ":freetype-shadow-opacity=255" })
    }

    @Test
    fun subMarginPixels_scalesFractionByFrameHeightAndClamps() {
        // 5% of a 1080p frame = 54px; clamped to [0, ∞).
        assertEquals(
            ":sub-margin=54",
            LibVlcSubtitleStyleMapping.subMarginPixels(SubtitleStyle(verticalPosition = 0.05f), 1080),
        )
        // Clamped to the 0.4 ceiling regardless of input.
        assertEquals(
            ":sub-margin=432",
            LibVlcSubtitleStyleMapping.subMarginPixels(SubtitleStyle(verticalPosition = 1.0f), 1080),
        )
        // Zero position → zero margin.
        assertEquals(
            ":sub-margin=0",
            LibVlcSubtitleStyleMapping.subMarginPixels(SubtitleStyle(verticalPosition = 0f), 2160),
        )
    }

    /** Splits a list of ":key=value" option strings into a key→value map. */
    private fun List<String>.parseFreetypeOptions(): Map<String, String> =
        associate { raw ->
            val eq = raw.indexOf('=')
            if (eq < 0) raw to "" else raw.substring(0, eq) to raw.substring(eq + 1)
        }
}

