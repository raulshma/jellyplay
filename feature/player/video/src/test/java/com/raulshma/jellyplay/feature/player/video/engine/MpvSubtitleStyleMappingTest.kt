package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
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
}
