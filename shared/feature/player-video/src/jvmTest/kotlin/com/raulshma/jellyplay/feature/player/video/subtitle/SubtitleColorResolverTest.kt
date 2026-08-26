package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.assertEquals
import kotlin.test.Test

class SubtitleColorResolverTest {

    @Test
    fun resolveTextColor_returnsArgbWhenPresent_fallsBackToEnumWhenNull() {
        val styleWithArgb = SubtitleStyle(
            fontColor = SubtitleColor.WHITE,
            fontColorArgb = 0xFF123456.toInt(),
        )
        assertEquals(0xFF123456.toInt(), SubtitleColorResolver.resolveTextColor(styleWithArgb))

        val styleWithoutArgb = SubtitleStyle(
            fontColor = SubtitleColor.YELLOW,
            fontColorArgb = null,
        )
        assertEquals(SubtitleColor.YELLOW.value, SubtitleColorResolver.resolveTextColor(styleWithoutArgb))
    }

    @Test
    fun resolveBackgroundColor_returnsArgbWhenPresent_fallsBackToEnumWhenNull() {
        val styleWithArgb = SubtitleStyle(
            backgroundColor = SubtitleColor.BLACK,
            backgroundColorArgb = 0x80000000.toInt(),
        )
        assertEquals(0x80000000.toInt(), SubtitleColorResolver.resolveBackgroundColor(styleWithArgb))

        val styleWithoutArgb = SubtitleStyle(
            backgroundColor = SubtitleColor.BLACK,
            backgroundColorArgb = null,
        )
        assertEquals(SubtitleColor.BLACK.value, SubtitleColorResolver.resolveBackgroundColor(styleWithoutArgb))
    }

    @Test
    fun resolveEdgeColor_returnsArgbWhenPresent_fallsBackToEnumWhenNull() {
        val styleWithArgb = SubtitleStyle(
            edgeColor = SubtitleColor.BLACK,
            edgeColorArgb = 0xFF00FF00.toInt(),
        )
        assertEquals(0xFF00FF00.toInt(), SubtitleColorResolver.resolveEdgeColor(styleWithArgb))

        val styleWithoutArgb = SubtitleStyle(
            edgeColor = SubtitleColor.WHITE,
            edgeColorArgb = null,
        )
        assertEquals(SubtitleColor.WHITE.value, SubtitleColorResolver.resolveEdgeColor(styleWithoutArgb))
    }
}
