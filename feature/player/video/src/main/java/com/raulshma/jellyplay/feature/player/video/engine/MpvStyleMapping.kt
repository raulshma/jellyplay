package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver

/**
 * Pure, testable mapping from [SubtitleStyle] to mpv `sub-*` property key/value
 * pairs. Extracted from MpvPlayerEngine's private style-apply methods so the
 * mapping can be unit-tested without a live mpv handle. The engine applies the
 * returned pairs via its own setter (safeSetPropertyString / safeSetOption).
 */
internal object MpvStyleMapping {

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
        // sub-font is owned by the engine (set unconditionally below the
        // customStyleEntries loop) so the user font family is honored even when
        // applyCustomStyle is false. It is intentionally NOT emitted here.
    }

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
