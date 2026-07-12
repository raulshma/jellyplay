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
}
