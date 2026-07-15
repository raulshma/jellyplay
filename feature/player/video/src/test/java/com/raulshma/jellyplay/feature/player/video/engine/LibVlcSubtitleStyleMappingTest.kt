package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
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
}
