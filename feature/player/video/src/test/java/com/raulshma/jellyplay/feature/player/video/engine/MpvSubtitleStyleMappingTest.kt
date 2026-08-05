package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvSubtitleStyleMappingTest {

    @Test
    fun assOverrideMode_toMpvProperty() {
        assertEquals("scale", AssOverrideMode.SCALE.toMpvAssOverride())
        assertEquals("force", AssOverrideMode.FORCE.toMpvAssOverride())
    }

    @Test
    fun borderStyle_toMpvProperty() {
        assertEquals("outline-and-shadow", SubtitleBorderStyle.OUTLINE_AND_SHADOW.toMpvBorderStyle())
        assertEquals("box", SubtitleBorderStyle.OPAQUE_BOX.toMpvBorderStyle())
        assertEquals("background-box", SubtitleBorderStyle.BACKGROUND_BOX.toMpvBorderStyle())
    }

    @Test
    fun customStyle_usesOverrideModeFromStyle() {
        // STYLE path should read assOverride from the style, not hardcode "scale".
        val style = SubtitleStyle(applyCustomStyle = true, assOverride = AssOverrideMode.FORCE)
        val entries = MpvStyleMapping.customStyleEntries(style)
        val overrideEntry = entries.first { it.first == "sub-ass-override" }
        assertEquals("force", overrideEntry.second)
    }

    @Test
    fun customStyle_mapsBothTypefaceToggles() {
        val style = SubtitleStyle(applyCustomStyle = true, bold = true, italic = true)
        val entries = MpvStyleMapping.customStyleEntries(style).toMap()

        assertEquals("yes", entries["sub-bold"])
        assertEquals("yes", entries["sub-italic"])
    }

    @Test
    fun computeValues_marginYIsZeroBecauseSubPosOwnsVerticalPosition() {
        // Vertical position is owned by mpv's `sub-pos` (a 0–100 frame
        // percentage set by the engine). A non-zero margin-y would double-offset
        // captions and diverge from ExoPlayer's view-fraction basis; the old
        // `* 720` scale assumed a 720p frame and drifted on other resolutions.
        listOf(0.0f, 0.05f, 0.2f, 0.4f).forEach { pos ->
            val values = MpvStyleMapping.computeValues(SubtitleStyle(verticalPosition = pos))
            assertEquals("marginY must be 0 for verticalPosition=$pos", 0, values.marginY)
        }
    }

    @Test
    fun defaultEntries_resetToMpvNativeLibassDefaults() {
        // When applyCustomStyle is false, the engine restores mpv/libass native
        // defaults (white text, transparent background, black outline + shadow,
        // outline-and-shadow border style, no ASS override, no justification,
        // plain typeface). Previously this reset branch was inline-only in the
        // engine and untested; now the mapping owns it.
        val entries = MpvStyleMapping.defaultEntries().toMap()

        assertEquals("#FFFFFFFF", entries["sub-color"])
        assertEquals("#00000000", entries["sub-back-color"])
        assertEquals("#FF000000", entries["sub-border-color"])
        assertEquals("#FF000000", entries["sub-shadow-color"])
        assertEquals("outline-and-shadow", entries["sub-border-style"])
        assertEquals("no", entries["sub-ass-override"])
        assertEquals("no", entries["sub-ass-justify"])
        assertEquals("no", entries["sub-bold"])
        assertEquals("no", entries["sub-italic"])
    }

    @Test
    fun defaultNumericMagnitudes_restoreBorderShadowAndScale() {
        // The engine applies these via typed safeSetPropertyDouble; the literal
        // magnitudes are sourced from the mapping's single DEFAULTS table so the
        // reset branch is unit-covered end-to-end and cannot drift from the
        // Compose overlay's defaultResolvedValues.
        assertEquals(3.0, MpvStyleMapping.defaultBorderSize, 0.0)
        assertEquals(0.0, MpvStyleMapping.defaultShadowOffset, 0.0)
        assertEquals(1.0, MpvStyleMapping.defaultScale, 0.0)
    }

    @Test
    fun defaultInitEntries_subsetOfDefaultEntries_andAllMandatoryInitKeysPresent() {
        // The engine's init branch applies only string-option keys via
        // setOptionString before the mpv handle accepts runtime writes.
        // defaultInitEntries must (a) be a strict subset of defaultEntries,
        // (b) cover every key the old hand-coded init branch wrote
        // (ass-override/bold/italic), and (c) read DEFAULTS so it cannot drift
        // from the runtime reset path.
        val initEntries = MpvStyleMapping.defaultInitEntries().toMap()
        val defaultEntries = MpvStyleMapping.defaultEntries().toMap()

        assertEquals(setOf("sub-ass-override", "sub-bold", "sub-italic"), initEntries.keys)
        initEntries.forEach { (k, v) ->
            assertEquals("init value for $k must match the runtime reset value", defaultEntries[k], v)
        }
        assertEquals("no", initEntries["sub-ass-override"])
        assertEquals("no", initEntries["sub-bold"])
        assertEquals("no", initEntries["sub-italic"])
    }

    @Test
    fun resolveForCompose_customBranchMirrorsSubtitleColorResolver() {
        // ARGB fields must win over the enum via SubtitleColorResolver, matching
        // the native mpv custom path. Free-form ARGB (0xFF112233) beats WHITE.
        val style = SubtitleStyle(
            applyCustomStyle = true,
            fontColor = SubtitleColor.WHITE,
            fontColorArgb = 0xFF112233.toInt(),
            edgeColor = SubtitleColor.BLACK,
            edgeColorArgb = 0xFF778899.toInt(),
            borderWidth = 4.5f,
            shadowOffset = 2.0f,
            fontSize = 30,
            bold = true,
            italic = true,
        )
        val resolved = MpvStyleMapping.resolveForCompose(style)

        assertEquals(0xFF112233.toInt(), resolved.textColorArgb)
        assertEquals(0xFF778899.toInt(), resolved.edgeColorArgb)
        assertEquals(4.5f, resolved.borderWidth)
        assertEquals(2.0f, resolved.shadowOffset)
        assertEquals(30, resolved.fontSize)
        assertTrue(resolved.bold)
        assertTrue(resolved.italic)
    }

    @Test
    fun resolveForCompose_defaultBranchReturnsMpvLibassDefaults() {
        // The Compose zoom overlay's default-branch values are sourced from here
        // (single source, no third hand-mirrored copy). These literals mirror
        // defaultEntries / defaultNumericValues — white text, transparent black
        // background, black 3.0 outline, no shadow, 24px, no bold/italic.
        val resolved = MpvStyleMapping.resolveForCompose(SubtitleStyle(applyCustomStyle = false))

        assertEquals(0xFFFFFFFF.toInt(), resolved.textColorArgb) // white
        assertEquals(0xFF000000.toInt(), resolved.backgroundColorArgb) // black
        assertEquals(0f, resolved.backgroundAlpha) // transparent
        assertEquals(0xFF000000.toInt(), resolved.edgeColorArgb) // black outline
        assertEquals(SubtitleEdgeType.OUTLINE, resolved.edgeType)
        assertEquals(3.0f, resolved.borderWidth)
        assertEquals(0.0f, resolved.shadowOffset)
        assertEquals(24, resolved.fontSize) // SubtitleDefaults.REFERENCE_FONT_SIZE
        assertFalse(resolved.bold)
        assertFalse(resolved.italic)
    }
}
