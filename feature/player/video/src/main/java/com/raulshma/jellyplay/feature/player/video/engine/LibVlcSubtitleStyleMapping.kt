package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleStyle

/** Pure FreeType typography mapping, shared by LibVLC's initial and reload paths. */
internal object LibVlcSubtitleStyleMapping {

    fun typefaceOptions(style: SubtitleStyle, bundledFallbackPath: String): List<String> = listOf(
        ":freetype-bold=${style.bold}",
        ":freetype-italic=${style.italic}",
        ":freetype-font=${style.fontFamilyPath ?: bundledFallbackPath}",
    )
}
