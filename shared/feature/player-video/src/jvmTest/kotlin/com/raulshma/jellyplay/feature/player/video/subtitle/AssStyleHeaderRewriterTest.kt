package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssStyleHeaderRewriterTest {

    private val header = """
        [Script Info]
        ScriptType: v4.00+
        PlayResY: 720

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Noto Sans,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,1,2,60,60,60,1
        Style: Signs,Arial Bold,56,&H0000FFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,3,0,8,60,60,60,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello
    """.trimIndent()

    private fun forceStyle(transform: SubtitleStyle.() -> SubtitleStyle = { this }): SubtitleStyle =
        SubtitleStyle(
            applyCustomStyle = true,
            assOverride = AssOverrideMode.FORCE,
        ).transform()

    private fun defaultLineOf(result: String): String =
        result.lineSequence().first { it.startsWith("Style: Default") }

    /** The untouched-force-defaults rewrite of [header]'s Default line. */
    private val defaultForceLine =
        "Style: Default,Noto Sans,48,&H00FFFFFF,&H000000FF,&H00000000,&HFF000000,0,0,0,0,100,100,0,0,1,2.0,1.0,2,60,60,60,1"

    // ── ARGB → ASS color conversion (BGR order + inverted alpha) ──

    @Test
    fun forceDefaultsWithModelDefaults_producesTheCanonicalLine() {
        val result = AssStyleHeaderRewriter.rewrite(header, forceStyle())
        assertEquals(defaultForceLine, defaultLineOf(result))
        // Authored sign/song style untouched.
        assertTrue(
            result.contains(
                "Style: Signs,Arial Bold,56,&H0000FFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,3,0,8,60,60,60,1",
            ),
        )
    }

    @Test
    fun redText_swapsToBgrOrder() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(fontColorArgb = 0xFFFF0000.toInt()) },
        )
        // RRGGBB 0xFF0000 (red) → &H BB GG RR = &H000000FF.
        assertTrue(defaultLineOf(result).contains("&H000000FF,&H000000FF,"))
    }

    @Test
    fun blueText_swapsToBgrOrder() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(fontColorArgb = 0xFF0000FF.toInt()) },
        )
        assertTrue(defaultLineOf(result).contains("&H00FF0000,"))
    }

    @Test
    fun backgroundOpacity_mapsToInvertedAssAlpha() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(backgroundOpacity = 0.5f) },
        )
        // 0.5 opacity → 255 - round(0.5 * 255) = 255 - 128 = 127 = 0x7F.
        assertTrue(defaultLineOf(result).contains("&H7F000000"))
    }

    @Test
    fun fullyTransparentBackground_becomesAssFfAlpha() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(backgroundOpacity = 0f) },
        )
        assertTrue(defaultLineOf(result).contains("&HFF000000"))
    }

    @Test
    fun argbEnumFallbacks_holdWhenFreeFormFieldsAreNull() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(fontColor = SubtitleColor.YELLOW, fontColorArgb = null) },
        )
        // Yellow 0xFFFFFF00 → &H0000FFFF.
        assertTrue(defaultLineOf(result).contains("&H0000FFFF"))
    }

    // ── Style-line targeting ──

    @Test
    fun fallsBackToFirstStyleLineWhenNoDefault() {
        val noDefault = header.replace("Style: Default,", "Style: Main,")
        val result = AssStyleHeaderRewriter.rewrite(
            noDefault,
            forceStyle { copy(bold = true) },
        )
        val rewritten = result.lineSequence().first { it.startsWith("Style: Main") }
        assertTrue(
            rewritten.endsWith(",-1,0,0,0,100,100,0,0,1,2.0,1.0,2,60,60,60,1"),
            "unexpected line: $rewritten",
        )
    }

    @Test
    fun styleNameMatchingIsCaseInsensitive() {
        val lower = header.replace("Style: Default,", "style: DEFAULT,")
        val result = AssStyleHeaderRewriter.rewrite(
            lower,
            forceStyle { copy(italic = true) },
        )
        assertTrue(
            result.lineSequence().any {
                it.startsWith("style: DEFAULT,") && it.split(',')[8].trim() == "-1"
            },
        )
    }

    // ── Font size scaling ──

    @Test
    fun fontSizeScalesRelativeToHeaderSize() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(fontSize = 12) },
        )
        // Header Fontsize 48 * (12 / 24) = 24 — PlayRes-relative, not absolute px.
        assertTrue(defaultLineOf(result).contains("Noto Sans,24,"))
    }

    @Test
    fun defaultFontSizeLeavesHeaderSizeUnchanged() {
        val result = AssStyleHeaderRewriter.rewrite(header, forceStyle())
        assertTrue(defaultLineOf(result).contains("Noto Sans,48,"))
    }

    // ── Font family ──

    @Test
    fun userFontRewritesFontname() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle {
                copy(fontFamilyPath = "file:///fonts/x.ttf", fontFamilyName = "Custom Sans")
            },
        )
        assertTrue(defaultLineOf(result).contains("Style: Default,Custom Sans,"))
    }

    @Test
    fun noUserFontKeepsAuthoredFontname() {
        val result = AssStyleHeaderRewriter.rewrite(header, forceStyle())
        assertTrue(defaultLineOf(result).contains("Style: Default,Noto Sans,"))
    }

    // ── Border mapping ──

    @Test
    fun opaqueBoxMapsToBorderStyleThree() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(borderStyle = SubtitleBorderStyle.OPAQUE_BOX, borderWidth = 4f) },
        )
        val fields = defaultLineOf(result).split(',')
        // BorderStyle/Outline/Shadow are fields 15/16/17 in this header's Format line.
        assertEquals("3", fields[15].trim())
        assertEquals("4.0", fields[16].trim())
        assertEquals("1.0", fields[17].trim())
    }

    @Test
    fun backgroundBoxMapsToBorderStyleFour() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(borderStyle = SubtitleBorderStyle.BACKGROUND_BOX) },
        )
        assertEquals("4", defaultLineOf(result).split(',')[15].trim())
    }

    @Test
    fun outlineAndShadowKeepsAssBorderStyleOne() {
        val result = AssStyleHeaderRewriter.rewrite(
            header,
            forceStyle { copy(borderStyle = SubtitleBorderStyle.OUTLINE_AND_SHADOW) },
        )
        assertEquals("1", defaultLineOf(result).split(',')[15].trim())
    }

    // ── SCALE vs FORCE gating ──

    @Test
    fun scaleModeReturnsHeaderUnchanged() {
        val scaleStyle = SubtitleStyle(
            applyCustomStyle = true,
            assOverride = AssOverrideMode.SCALE,
            fontColorArgb = 0xFFFF0000.toInt(),
            fontSize = 40,
        )
        assertEquals(header, AssStyleHeaderRewriter.rewrite(header, scaleStyle))
    }

    @Test
    fun applyCustomStyleOffNeverForceOverrides() {
        assertFalse(
            AssStyleHeaderRewriter.isForceOverride(
                SubtitleStyle(applyCustomStyle = false, assOverride = AssOverrideMode.FORCE),
            ),
        )
    }

    @Test
    fun forceRequiresBothFlags() {
        assertTrue(
            AssStyleHeaderRewriter.isForceOverride(
                SubtitleStyle(applyCustomStyle = true, assOverride = AssOverrideMode.FORCE),
            ),
        )
        assertFalse(
            AssStyleHeaderRewriter.isForceOverride(
                SubtitleStyle(applyCustomStyle = true, assOverride = AssOverrideMode.SCALE),
            ),
        )
    }

    // ── Malformed-header passthrough ──

    @Test
    fun emptyHeaderPassesThrough() {
        assertEquals("", AssStyleHeaderRewriter.rewrite("", forceStyle()))
    }

    @Test
    fun headerWithoutStylesSectionPassesThrough() {
        val noStyles = "[Script Info]\nPlayResY: 720\n\n[Events]\nFormat: Layer, Start, End\n"
        assertEquals(noStyles, AssStyleHeaderRewriter.rewrite(noStyles, forceStyle()))
    }

    @Test
    fun stylesSectionWithoutFormatLinePassesThrough() {
        val noFormat = "[V4+ Styles]\nStyle: Default,Arial,20,&H00FFFFFF\n"
        assertEquals(noFormat, AssStyleHeaderRewriter.rewrite(noFormat, forceStyle()))
    }

    @Test
    fun truncatedStyleLinePassesThrough() {
        val truncated = "[V4+ Styles]\nFormat: Name, Fontname, Fontsize\nStyle: Default,Arial\n"
        assertEquals(truncated, AssStyleHeaderRewriter.rewrite(truncated, forceStyle()))
    }

    @Test
    fun nonNumericFontSizePassesThrough() {
        val bad = "[V4+ Styles]\nFormat: Name, Fontname, Fontsize\nStyle: Default,Arial,auto\n"
        assertEquals(bad, AssStyleHeaderRewriter.rewrite(bad, forceStyle()))
    }

    @Test
    fun nulTerminatedHeaderKeepsTerminator() {
        // jellyfin-ffmpeg#506-style CodecPrivate: the header ends with a NUL and
        // the style line is the last line. AssHeaderParser appends the events
        // Format line only when that trailing NUL survives the rewrite, so the
        // terminator must be preserved (and must not fold into the last field).
        val nulHeader =
            "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour\n" +
                "Style: Default,Arial,20,&H00FFFFFF\u0000"
        val result = AssStyleHeaderRewriter.rewrite(
            nulHeader,
            forceStyle { copy(fontColorArgb = 0xFFFF0000.toInt()) },
        )
        assertTrue(result.endsWith("\u0000"))
        assertTrue(
            result.contains("Style: Default,Arial,20,&H000000FF\u0000"),
            "unexpected line: $result",
        )
    }

    // ── Format-line-driven field indices ──

    @Test
    fun fieldOrderComesFromTheHeaderFormatLine() {
        val reordered = """
            [V4+ Styles]
            Format: Name, BorderStyle, Outline, Shadow, Bold, PrimaryColour, Fontsize, Fontname
            Style: Default,1,2,1,0,&H00FFFFFF,30,Arial
        """.trimIndent()
        val result = AssStyleHeaderRewriter.rewrite(
            reordered,
            forceStyle {
                copy(
                    fontColorArgb = 0xFF00FF00.toInt(),
                    borderStyle = SubtitleBorderStyle.OPAQUE_BOX,
                    fontSize = 48,
                )
            },
        )
        assertEquals(
            "Style: Default,3,2.0,1.0,0,&H0000FF00,60,Arial",
            result.lineSequence().first { it.startsWith("Style:") },
        )
    }

    @Test
    fun crlfHeaderRejoinsWithLf() {
        val crlf = header.replace("\n", "\r\n")
        val result = AssStyleHeaderRewriter.rewrite(
            crlf,
            forceStyle { copy(bold = true) },
        )
        assertTrue(
            defaultLineOf(result).endsWith(",-1,0,0,0,100,100,0,0,1,2.0,1.0,2,60,60,60,1"),
        )
    }

    @Test
    fun ssaV4HeaderMapsEdgeColorToTertiaryColour() {
        val v4 = """
            [V4 Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, TertiaryColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment
            Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,1,2,1,2
        """.trimIndent()
        val result = AssStyleHeaderRewriter.rewrite(
            v4,
            forceStyle { copy(edgeColorArgb = 0xFFFF0000.toInt()) },
        )
        // OutlineColour does not exist in SSA V4 — the edge color rides
        // TertiaryColour instead (red → BGR 0000FF). BackColour still rewrites
        // like any other mapped field (black + opacity 0 → &HFF000000).
        assertEquals(
            "Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H000000FF,&HFF000000,0,0,1,2.0,1.0,2",
            result.lineSequence().first { it.startsWith("Style:") },
        )
    }
}
