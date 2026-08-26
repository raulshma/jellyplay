package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleRenderDefaults
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver

/**
 * Pure FreeType typography mapping, the single source for LibVLC's `:freetype-*`
 * subtitle options. Used by both LibVLC's initial-load and reload paths — the
 * engine's [com.raulshma.jellyplay.feature.player.video.engine.LibVlcPlayerEngine.Media.applySubtitleStyle]
 * only delegates here, so the tested code IS the shipped code (no inline shadow).
 *
 * Branching on [SubtitleStyle.applyCustomStyle] lives in [freetypeOptions];
 * [colorOptions] is the unconditional custom mapping (kept test-facing) and
 * [defaultOptions] is the mpv-matching reset set emitted when the user has not
 * enabled a custom style.
 */
internal object LibVlcSubtitleStyleMapping {

    /**
     * The full `:freetype-*` color/size/opacity option list for [style],
     * dispatching on [SubtitleStyle.applyCustomStyle]. This is the entry point
     * engines should call; [colorOptions] and [defaultOptions] are exposed for
     * unit testing each branch in isolation.
     */
    fun freetypeOptions(style: SubtitleStyle): List<String> =
        if (style.applyCustomStyle) colorOptions(style) else defaultOptions()

    /**
     * LibVLC's native default caption style, sourced from the shared
     * [SubtitleRenderDefaults] table (white
     * text, transparent background, black outline + shadow, 24px reference size)
     * so it cannot drift from the other engines. The thickness/shadow-opacity
     * magnitudes are LibVLC-specific FreeType format translations of the shared
     * edge-type/opacity, not independent defaults.
     */
    fun defaultOptions(): List<String> {
        val d = SubtitleRenderDefaults.DEFAULT
        return listOf(
            ":freetype-color=${d.fontColor and 0x00FFFFFF}", // White (0xFFFFFF)
            ":freetype-background-color=0", // Black/transparent
            ":freetype-background-opacity=0", // Transparent
            ":freetype-outline-color=0", // Black
            ":freetype-outline-thickness=2",
            ":freetype-shadow-opacity=255",
            // Absolute pixel size, matching the other engines' bundled-default
            // reference size (24sp). :freetype-rel-fontsize would clamp/ignore
            // 24 here — it is a relative-size enum, not a pixel value.
            ":freetype-fontsize=${d.fontSizeSp}",
        )
    }

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
