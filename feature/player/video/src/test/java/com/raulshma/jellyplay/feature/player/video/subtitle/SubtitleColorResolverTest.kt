package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleColorResolverTest {

    @Test
    fun resolveTextColor_nullArgb_fallsBackToEnum() {
        val style = SubtitleStyle(fontColor = SubtitleColor.YELLOW, fontColorArgb = null)
        assertEquals(SubtitleColor.YELLOW.value, SubtitleColorResolver.resolveTextColor(style))
    }

    @Test
    fun resolveTextColor_nonNullArgb_takesPrecedence() {
        val custom = 0xFFAABBCC.toInt()
        val style = SubtitleStyle(fontColor = SubtitleColor.YELLOW, fontColorArgb = custom)
        assertEquals(custom, SubtitleColorResolver.resolveTextColor(style))
    }

    @Test
    fun resolveBackgroundColor_nullArgb_fallsBackToEnum() {
        val style = SubtitleStyle(backgroundColor = SubtitleColor.GREEN, backgroundColorArgb = null)
        assertEquals(SubtitleColor.GREEN.value, SubtitleColorResolver.resolveBackgroundColor(style))
    }

    @Test
    fun resolveBackgroundColor_nonNullArgb_takesPrecedence() {
        val custom = 0x12345678
        val style = SubtitleStyle(backgroundColorArgb = custom)
        assertEquals(custom, SubtitleColorResolver.resolveBackgroundColor(style))
    }

    @Test
    fun resolveEdgeColor_nullArgb_fallsBackToEnum() {
        val style = SubtitleStyle(edgeColor = SubtitleColor.RED, edgeColorArgb = null)
        assertEquals(SubtitleColor.RED.value, SubtitleColorResolver.resolveEdgeColor(style))
    }

    @Test
    fun resolveEdgeColor_nonNullArgb_takesPrecedence() {
        val custom = 0x77777777
        val style = SubtitleStyle(edgeColorArgb = custom)
        assertEquals(custom, SubtitleColorResolver.resolveEdgeColor(style))
    }
}
