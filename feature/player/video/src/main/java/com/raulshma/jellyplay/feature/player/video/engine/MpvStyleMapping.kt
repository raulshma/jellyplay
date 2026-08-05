package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleDefaults

/**
 * Pure, testable mapping from [SubtitleStyle] to mpv `sub-*` property key/value
 * pairs. Extracted from MpvPlayerEngine's private style-apply methods so the
 * mapping can be unit-tested without a live mpv handle. The engine applies the
 * returned pairs via its own setter (safeSetPropertyString / safeSetOption).
 *
 * Also the single source for mpv/libass caption defaults consumed by the
 * Compose zoom overlay ([MpvSubtitleOverlay]) — previously the overlay kept a
 * third hand-mirrored copy of the same literals. [resolveForCompose] returns
 * the resolved magnitudes (ARGB ints + outline/shadow) for both the custom and
 * default branches, so native captions, the Compose overlay, and these unit
 * tests all read one set of values.
 */
internal object MpvStyleMapping {

    /**
     * mpv/libass native caption defaults — the **single source** for every
     * default value this object emits. Both the mpv property-string reset
     * ([defaultEntries], [defaultBorderSize], [defaultShadowOffset],
     * [defaultScale]) and the Compose-overlay resolver ([defaultResolvedValues])
     * derive from these constants, so the native reset path and the zoomed
     * Compose overlay cannot drift apart (the old bug: three hand-mirrored
     * copies of "white text, 3.0 black outline, 24px").
     */
    private object DEFAULTS {
        const val TEXT_COLOR_ARGB: Int = 0xFFFFFFFF.toInt() // white
        const val BACKGROUND_COLOR_ARGB: Int = 0xFF000000.toInt() // black
        const val BACKGROUND_ALPHA: Float = 0f // transparent
        const val EDGE_COLOR_ARGB: Int = 0xFF000000.toInt() // black
        val EDGE_TYPE: SubtitleEdgeType = SubtitleEdgeType.OUTLINE
        const val BORDER_WIDTH: Float = 3.0f
        const val SHADOW_OFFSET: Float = 0.0f
        const val FONT_SIZE: Int = SubtitleDefaults.REFERENCE_FONT_SIZE
        const val SCALE: Double = 1.0
        const val BOLD: Boolean = false
        const val ITALIC: Boolean = false
    }

    /**
     * Resolved caption magnitudes for a (Compose or engine) renderer, in ARGB
     * ints + plain floats — no mpv property strings, no Compose `Color`. The
     * custom branch resolves the user's [SubtitleStyle] via
     * [SubtitleColorResolver]; the default branch returns [DEFAULTS].
     */
    data class ResolvedMpvStyle(
        val textColorArgb: Int,
        val backgroundColorArgb: Int,
        val backgroundAlpha: Float,
        val edgeColorArgb: Int,
        val edgeType: SubtitleEdgeType,
        val borderWidth: Float,
        val shadowOffset: Float,
        val fontSize: Int,
        val bold: Boolean,
        val italic: Boolean,
    )

    /**
     * Resolves [style] into the magnitudes a Compose (or engine) renderer needs.
     * Dispatches on [SubtitleStyle.applyCustomStyle]: custom → resolved user
     * values; `false` → [defaultResolvedValues]. The native mpv reset path and
     * the Compose overlay both end up at [DEFAULTS], so captions cannot drift
     * between the native and zoomed-Compose paths.
     */
    fun resolveForCompose(style: SubtitleStyle): ResolvedMpvStyle =
        if (style.applyCustomStyle) {
            ResolvedMpvStyle(
                textColorArgb = SubtitleColorResolver.resolveTextColor(style),
                backgroundColorArgb = SubtitleColorResolver.resolveBackgroundColor(style),
                backgroundAlpha = style.backgroundOpacity.coerceIn(0f, 1f),
                edgeColorArgb = SubtitleColorResolver.resolveEdgeColor(style),
                edgeType = style.edgeType,
                borderWidth = style.borderWidth,
                shadowOffset = style.shadowOffset,
                fontSize = style.fontSize,
                bold = style.bold,
                italic = style.italic,
            )
        } else {
            defaultResolvedValues()
        }

    /**
     * mpv/libass native default caption style as resolved magnitudes, sourced
     * from [DEFAULTS]. The Compose zoom overlay's default branch reads this.
     */
    fun defaultResolvedValues(): ResolvedMpvStyle = ResolvedMpvStyle(
        textColorArgb = DEFAULTS.TEXT_COLOR_ARGB,
        backgroundColorArgb = DEFAULTS.BACKGROUND_COLOR_ARGB,
        backgroundAlpha = DEFAULTS.BACKGROUND_ALPHA,
        edgeColorArgb = DEFAULTS.EDGE_COLOR_ARGB,
        edgeType = DEFAULTS.EDGE_TYPE,
        borderWidth = DEFAULTS.BORDER_WIDTH,
        shadowOffset = DEFAULTS.SHADOW_OFFSET,
        fontSize = DEFAULTS.FONT_SIZE,
        bold = DEFAULTS.BOLD,
        italic = DEFAULTS.ITALIC,
    )


    data class MpvStyleValues(
        val textColor: String,
        val backgroundColor: String,
        val edgeColor: String,
        val fontSize: Int,
        val marginY: Int,
        val outlineSize: Double,
        val shadowOffset: Double,
    )

    fun computeValues(style: SubtitleStyle): MpvStyleValues {
        val marginY = 0
        val outlineSize: Double
        val shadowOffset: Double
        when (style.edgeType) {
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.NONE -> {
                outlineSize = 0.0; shadowOffset = 0.0
            }
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> {
                outlineSize = style.borderWidth.toDouble(); shadowOffset = 0.0
            }
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> {
                outlineSize = 0.0; shadowOffset = style.shadowOffset.toDouble()
            }
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.RAISED,
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DEPRESSED -> {
                outlineSize = 1.0; shadowOffset = 1.5
            }
        }
        return MpvStyleValues(
            textColor = colorToMpvHex(SubtitleColorResolver.resolveTextColor(style), 1f),
            backgroundColor = colorToMpvHex(SubtitleColorResolver.resolveBackgroundColor(style), style.backgroundOpacity),
            edgeColor = colorToMpvHex(SubtitleColorResolver.resolveEdgeColor(style), 1f),
            fontSize = style.fontSize.coerceIn(10, 72),
            marginY = marginY,
            outlineSize = outlineSize,
            shadowOffset = shadowOffset,
        )
    }

    fun customStyleEntries(style: SubtitleStyle): List<Pair<String, String>> = buildList {
        val values = computeValues(style)
        add("sub-color" to values.textColor)
        add("sub-back-color" to values.backgroundColor)
        // sub-border-color is the canonical mpv name; sub-outline-color is a
        // deprecated alias that some libass versions silently ignore. mpvkt
        // uses sub-border-color exclusively.
        add("sub-border-color" to values.edgeColor)
        add("sub-shadow-color" to values.edgeColor)
        // borderStyle drives the mpv property; BACKGROUND_BOX also folds in backgroundOpacity above.
        add("sub-border-style" to style.borderStyle.toMpvBorderStyle())
        add("sub-ass-override" to style.assOverride.toMpvAssOverride())
        // Apply justification to ASS tracks too. With sub-ass-override active,
        // sub-justify alone only affects SRT/VTT; sub-ass-justify=yes extends
        // the user's alignment to ASS/SSA events. mpvkt pairs these the same way.
        add("sub-ass-justify" to "yes")
        add("sub-bold" to if (style.bold) "yes" else "no")
        add("sub-italic" to if (style.italic) "yes" else "no")
        // sub-font is owned by the engine (set conditionally depending on
        // applyCustomStyle) so the user font family is honored only when
        // applyCustomStyle is true. It is intentionally NOT emitted here.
    }

    /**
     * The string-typed `sub-*` reset pairs emitted when the user has disabled a
     * custom subtitle style, restoring mpv/libass native defaults (white text,
     * transparent background, black outline + shadow, outline-and-shadow border
     * style, no ASS override, no justification, plain typeface). Color hexes
     * derive from [DEFAULTS] via [colorToMpvHex]; numeric reset magnitudes the
     * engine applies via typed setters come from [defaultBorderSize] /
     * [defaultShadowOffset] / [defaultScale]. The whole reset branch —
     * previously inline and untested — is now exercised by unit tests.
     */
    fun defaultEntries(): List<Pair<String, String>> = listOf(
        "sub-color" to colorToMpvHex(DEFAULTS.TEXT_COLOR_ARGB, 1f),
        "sub-back-color" to colorToMpvHex(DEFAULTS.BACKGROUND_COLOR_ARGB, DEFAULTS.BACKGROUND_ALPHA),
        "sub-border-color" to colorToMpvHex(DEFAULTS.EDGE_COLOR_ARGB, 1f),
        "sub-shadow-color" to colorToMpvHex(DEFAULTS.EDGE_COLOR_ARGB, 1f),
        "sub-border-style" to "outline-and-shadow",
        "sub-ass-override" to "no",
        "sub-ass-justify" to "no",
        "sub-bold" to if (DEFAULTS.BOLD) "yes" else "no",
        "sub-italic" to if (DEFAULTS.ITALIC) "yes" else "no",
    )

    /**
     * The string-typed `sub-*` reset pairs the engine applies at **init time**
     * via `setOptionString` (before the mpv handle accepts runtime property
     * writes) — the keys whose default is a plain string option. Derived by
     * filtering [defaultEntries] so the init path reads the same [DEFAULTS]
     * table and cannot drift from the runtime reset branch. Previously the
     * engine's init branch hand-coded `"no"` literals here that silently
     * duplicated the [DEFAULTS] booleans.
     */
    fun defaultInitEntries(): List<Pair<String, String>> {
        val initKeys = setOf("sub-ass-override", "sub-bold", "sub-italic")
        return defaultEntries().filter { it.first in initKeys }
    }

    /** Reset magnitude for `sub-border-size` (sourced from [DEFAULTS]). */
    val defaultBorderSize: Double = DEFAULTS.BORDER_WIDTH.toDouble()

    /** Reset magnitude for `sub-shadow-offset` (sourced from [DEFAULTS]). */
    val defaultShadowOffset: Double = DEFAULTS.SHADOW_OFFSET.toDouble()

    /** Reset magnitude for `sub-scale` (sourced from [DEFAULTS]). */
    val defaultScale: Double = DEFAULTS.SCALE


    private fun colorToMpvHex(color: Int, opacity: Float): String {
        val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        val rgb = color and 0x00FFFFFF
        val chars = "0123456789ABCDEF"
        val s = CharArray(9)
        s[0] = '#'
        s[1] = chars[(alpha shr 4) and 0xF]; s[2] = chars[alpha and 0xF]
        var v = rgb
        for (i in 8 downTo 3) { s[i] = chars[v and 0xF]; v = v shr 4 }
        return String(s)
    }
}

internal fun AssOverrideMode.toMpvAssOverride(): String = when (this) {
    AssOverrideMode.SCALE -> "scale"
    AssOverrideMode.FORCE -> "force"
}

internal fun SubtitleBorderStyle.toMpvBorderStyle(): String = when (this) {
    SubtitleBorderStyle.OUTLINE_AND_SHADOW -> "outline-and-shadow"
    SubtitleBorderStyle.OPAQUE_BOX -> "box"
    SubtitleBorderStyle.BACKGROUND_BOX -> "background-box"
}
