package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class LibVlcSubtitleStyleMappingTest {

    @Test
    fun typefaceOptions_honorBothTogglesAndBundledFallback() {
        val options = LibVlcSubtitleStyleMapping.typefaceOptions(
            SubtitleStyle(applyCustomStyle = true, bold = true, italic = true),
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
            SubtitleStyle(applyCustomStyle = true, fontFamilyPath = "/fonts/custom.ttf"),
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
    fun typefaceOptions_resetsToDefaultsWhenApplyCustomStyleIsFalse() {
        val options = LibVlcSubtitleStyleMapping.typefaceOptions(
            SubtitleStyle(applyCustomStyle = false, bold = true, italic = true, fontFamilyPath = "/fonts/custom.ttf"),
            bundledFallbackPath = "/fonts/subfont.ttf",
        )

        assertEquals(
            listOf(
                ":freetype-bold=false",
                ":freetype-italic=false",
                ":freetype-font=/fonts/subfont.ttf",
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
        // Absolute pixel size (not the relative-size enum, which rejects 32).
        assertEquals(options[":freetype-fontsize"], "32")
    }

    @Test
    fun colorOptions_opaqueBoxForcesFullBackgroundOpacity() {
        val style = SubtitleStyle(
            borderStyle = SubtitleBorderStyle.OPAQUE_BOX,
            backgroundOpacity = 0.0f, // ignored for OPAQUE_BOX
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        assertEquals(options[":freetype-background-opacity"], "255")
    }

    @Test
    fun colorOptions_backgroundBoxHonorsBackgroundOpacity() {
        val style = SubtitleStyle(
            borderStyle = SubtitleBorderStyle.BACKGROUND_BOX,
            backgroundOpacity = 0.5f,
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        assertEquals(options[":freetype-background-opacity"], "127")
    }

    @Test
    fun colorOptions_outlineAndShadowYieldsTransparentBackground() {
        val style = SubtitleStyle(
            borderStyle = SubtitleBorderStyle.OUTLINE_AND_SHADOW,
            backgroundOpacity = 1.0f, // ignored — outline/shadow is the legibility aid
            edgeType = SubtitleEdgeType.OUTLINE,
        )
        val options = LibVlcSubtitleStyleMapping.colorOptions(style).parseFreetypeOptions()

        assertEquals(options[":freetype-background-opacity"], "0")
        assertEquals(options[":freetype-outline-thickness"], "2")
    }

    @Test
    fun colorOptions_dropShadowAddsShadowOpacity() {
        val options = LibVlcSubtitleStyleMapping.colorOptions(
            SubtitleStyle(edgeType = SubtitleEdgeType.DROP_SHADOW)
        )
        assertTrue(options.any { it == ":freetype-shadow-opacity=255" }, "expected :freetype-shadow-opacity=255")
    }

    @Test
    fun defaultOptions_emitsMpvMatchingWhiteTransparentBlackOutline() {
        // When applyCustomStyle is false, LibVLC ships mpv's native default
        // caption style (white text, transparent background, black outline +
        // shadow, 24px reference size). Previously this lived inline in the
        // engine and bypassed the tests; now it is owned here.
        val options = LibVlcSubtitleStyleMapping.defaultOptions().parseFreetypeOptions()

        assertEquals(options[":freetype-color"], "16777215") // 0xFFFFFF white
        assertEquals(options[":freetype-background-color"], "0")
        assertEquals(options[":freetype-background-opacity"], "0") // transparent
        assertEquals(options[":freetype-outline-color"], "0") // black
        assertEquals(options[":freetype-outline-thickness"], "2")
        assertEquals(options[":freetype-shadow-opacity"], "255")
        assertEquals(options[":freetype-fontsize"], "24") // SubtitleDefaults.REFERENCE_FONT_SIZE
    }

    @Test
    fun freetypeOptions_dispatchesToCustomWhenApplyCustomStyleTrue() {
        // Custom path resolves ARGB through SubtitleColorResolver; ARGB must win.
        val style = SubtitleStyle(
            applyCustomStyle = true,
            fontColor = SubtitleColor.WHITE,
            fontColorArgb = 0xFF112233.toInt(),
        )
        val options = LibVlcSubtitleStyleMapping.freetypeOptions(style).parseFreetypeOptions()

        assertEquals((0x112233).toString(), options[":freetype-color"])
    }

    @Test
    fun freetypeOptions_dispatchesToDefaultsWhenApplyCustomStyleFalse() {
        // Non-custom path emits the mpv-matching defaults regardless of the
        // user's color fields (mirrors the engine's pre-fold behaviour).
        val style = SubtitleStyle(
            applyCustomStyle = false,
            fontColor = SubtitleColor.BLACK,
            fontColorArgb = 0xFF000000.toInt(),
        )
        val options = LibVlcSubtitleStyleMapping.freetypeOptions(style).parseFreetypeOptions()

        assertEquals(options[":freetype-color"], "16777215") // default white, not the user's black
    }

    @Test
    fun subMarginPixels_scalesFractionByFrameHeightAndClamps() {
        // 5% of a 1080p frame = 54px; clamped to [0, ∞).
        assertEquals(LibVlcSubtitleStyleMapping.subMarginPixels(SubtitleStyle(verticalPosition = 0.05f), 1080), ":sub-margin=54")
        // Clamped to the 0.4 ceiling regardless of input.
        assertEquals(LibVlcSubtitleStyleMapping.subMarginPixels(SubtitleStyle(verticalPosition = 1.0f), 1080), ":sub-margin=432")
        // Zero position → zero margin.
        assertEquals(LibVlcSubtitleStyleMapping.subMarginPixels(SubtitleStyle(verticalPosition = 0f), 2160), ":sub-margin=0")
    }

    /** Splits a list of ":key=value" option strings into a key→value map. */
    private fun List<String>.parseFreetypeOptions(): Map<String, String> =
        associate { raw ->
            val eq = raw.indexOf('=')
            if (eq < 0) raw to "" else raw.substring(0, eq) to raw.substring(eq + 1)
        }
}

