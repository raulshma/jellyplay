package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver

/** Pure FreeType typography mapping, shared by LibVLC's initial and reload paths. */
internal object LibVlcSubtitleStyleMapping {

    fun typefaceOptions(style: SubtitleStyle, bundledFallbackPath: String): List<String> = if (style.applyCustomStyle) {
        listOf(
            ":freetype-bold=${style.bold}",
            ":freetype-italic=${style.italic}",
            ":freetype-font=${style.fontFamilyPath ?: bundledFallbackPath}",
        )
    } else {
        listOf(
            ":freetype-bold=false",
            ":freetype-italic=false",
            ":freetype-font=$bundledFallbackPath",
        )
    }

    fun colorOptions(style: SubtitleStyle): List<String> {
        val options = mutableListOf<String>()
        val fontColor = SubtitleColorResolver.resolveTextColor(style) and 0x00FFFFFF
        val backgroundColor = SubtitleColorResolver.resolveBackgroundColor(style) and 0x00FFFFFF
        val edgeColor = SubtitleColorResolver.resolveEdgeColor(style) and 0x00FFFFFF

        options.add(":freetype-color=$fontColor")
        options.add(":freetype-background-color=$backgroundColor")
        options.add(":freetype-outline-color=$edgeColor")
        // freetype-fontsize is the ABSOLUTE size in pixels (non-zero overrides
        // the relative-size enum); use it for the user's sp value. Do NOT feed
        // sp values into :freetype-rel-fontsize — that option is a small-integer
        // enum (Auto/Smaller/Small/Normal/Large/Larger = 0/20/18/16/12/6), so a
        // value like 24 or style.fontSize falls outside its domain and LibVLC
        // silently ignores it. See VLC modules/text_renderer/freetype/freetype.c.
        options.add(":freetype-fontsize=${style.fontSize}")

        val bgOpacity = when (style.borderStyle) {
            SubtitleBorderStyle.OPAQUE_BOX -> 255
            SubtitleBorderStyle.OUTLINE_AND_SHADOW -> 0
            else -> (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        }
        options.add(":freetype-background-opacity=$bgOpacity")

        val outlineThickness = if (style.borderStyle == SubtitleBorderStyle.OUTLINE_AND_SHADOW && style.edgeType == SubtitleEdgeType.OUTLINE) {
            2
        } else {
            when (style.edgeType) {
                SubtitleEdgeType.NONE -> 0
                SubtitleEdgeType.OUTLINE -> 2
                SubtitleEdgeType.DROP_SHADOW -> 0
                SubtitleEdgeType.RAISED, SubtitleEdgeType.DEPRESSED -> 1
            }
        }
        options.add(":freetype-outline-thickness=$outlineThickness")

        if (style.edgeType == SubtitleEdgeType.DROP_SHADOW ||
            style.edgeType == SubtitleEdgeType.RAISED ||
            style.edgeType == SubtitleEdgeType.DEPRESSED
        ) {
            options.add(":freetype-shadow-opacity=255")
        }

        return options
    }

    fun subMarginPixels(style: SubtitleStyle, frameHeight: Int): String {
        val margin = (style.verticalPosition.coerceIn(0f, 0.4f) * frameHeight).toInt()
        return ":sub-margin=$margin"
    }
}
