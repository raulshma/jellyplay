package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.MpvStyleMapping
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleDefaults

/**
 * Zoom-safe subtitle overlay for engines whose native rendering cannot be
 * reparented out of the video surface (mpv: libass composites into the GPU
 * video surface). Renders the single live line exposed via
 * [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine.liveSubtitleCue]
 * as a bottom-aligned [Text], pinned to the screen (a sibling of the zoomed
 * video, outside the pinch/crop transform).
 *
 * Style is resolved from [SubtitleStyle] to match native mpv (libass) captions
 * rendered at zoom == 1:
 * - Honors [SubtitleStyle.applyCustomStyle]: when false, falls back to mpv's
 *   default native caption style (white text, black 3.0 outline, bold=false, italic=false).
 * - Resolves font typeface (custom user font or bundled fallback) via the injected
 *   [FontProvider] singleton, so its LRU cache and startup prewarm serve the overlay too.
 * - Scales font size proportionally to video container height (matching mpv's `sub-font-size=55`
 *   libass 720p reference canvas ratio).
 * - Places bottom margin at container height * [SubtitleStyle.verticalPosition] (matching `sub-pos`).
 * - Renders crisp 360° stroke outline for [SubtitleEdgeType.OUTLINE] and wraps background
 *   boxes tightly around text lines.
 */
@Composable
internal fun MpvSubtitleOverlay(
    cue: CharSequence?,
    style: SubtitleStyle,
    fontProvider: AndroidFontProvider,
    modifier: Modifier = Modifier,
) {
    if (cue.isNullOrBlank()) return

    val effectiveStyle = remember(style) { resolveEffectiveStyle(style) }

    val typeface = remember(effectiveStyle.rawStyle, fontProvider) {
        fontProvider.typefaceFor(effectiveStyle.rawStyle)
    }
    val fontFamily = remember(typeface) { FontFamily(typeface) }
    val fontWeight = if (effectiveStyle.bold) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (effectiveStyle.italic) FontStyle.Italic else FontStyle.Normal

    // Transparent outer box: never intercepts pointer events needed by gestures.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val density = LocalDensity.current

        val fontSizeSp = effectiveStyle.fontSize.sp
        val bottomPaddingDp = maxHeight * effectiveStyle.verticalPosition.coerceIn(0f, 0.5f)
        val strokeWidthDp = (effectiveStyle.borderWidth * (effectiveStyle.fontSize.toFloat() / SubtitleDefaults.REFERENCE_FONT_SIZE.toFloat())).dp
        val strokeWidthPx = with(density) { strokeWidthDp.toPx() }

        val cueText = cue.toString()

        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = bottomPaddingDp.coerceAtLeast(8.dp))
                .then(
                    if (effectiveStyle.backgroundAlpha > 0f) {
                        Modifier
                            .background(
                                color = effectiveStyle.backgroundColor.copy(alpha = effectiveStyle.backgroundAlpha),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Background stroke layer for crisp 360° outline.
            if (effectiveStyle.edgeType == SubtitleEdgeType.OUTLINE && strokeWidthPx > 0f) {
                Text(
                    text = cueText,
                    color = effectiveStyle.edgeColor,
                    fontSize = fontSizeSp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        drawStyle = Stroke(
                            width = strokeWidthPx,
                            join = StrokeJoin.Round,
                        ),
                    ),
                )
            }

            // Foreground fill layer + drop shadow.
            Text(
                text = cueText,
                color = effectiveStyle.textColor,
                fontSize = fontSizeSp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    drawStyle = Fill,
                    shadow = computeShadow(effectiveStyle),
                ),
            )
        }
    }
}

private data class EffectiveMpvSubtitleStyle(
    val rawStyle: SubtitleStyle,
    val textColor: Color,
    val backgroundColor: Color,
    val backgroundAlpha: Float,
    val edgeColor: Color,
    val edgeType: SubtitleEdgeType,
    val borderWidth: Float,
    val shadowOffset: Float,
    val fontSize: Int,
    val verticalPosition: Float,
    val bold: Boolean,
    val italic: Boolean,
)

private fun resolveEffectiveStyle(style: SubtitleStyle): EffectiveMpvSubtitleStyle {
    // The resolved caption magnitudes (colors, edge type/width, shadow, size,
    // bold/italic) come from MpvStyleMapping — the single source shared with the
    // native mpv path and the style-mapping unit tests. Previously this overlay
    // kept a third hand-mirrored copy of mpv/libass defaults; a drift here made
    // captions visibly change on pinch-zoom. verticalPosition + typeface fields
    // are overlay-local (Compose layout concerns, not mpv properties).
    val resolved = MpvStyleMapping.resolveForCompose(style)
    return EffectiveMpvSubtitleStyle(
        rawStyle = style.copy(bold = resolved.bold, italic = resolved.italic),
        textColor = Color(resolved.textColorArgb),
        backgroundColor = Color(resolved.backgroundColorArgb),
        backgroundAlpha = resolved.backgroundAlpha,
        edgeColor = Color(resolved.edgeColorArgb),
        edgeType = resolved.edgeType,
        borderWidth = resolved.borderWidth,
        shadowOffset = resolved.shadowOffset,
        fontSize = resolved.fontSize,
        verticalPosition = style.verticalPosition,
        bold = resolved.bold,
        italic = resolved.italic,
    )
}

private fun computeShadow(style: EffectiveMpvSubtitleStyle): Shadow? {
    return when (style.edgeType) {
        SubtitleEdgeType.NONE,
        SubtitleEdgeType.OUTLINE -> null
        SubtitleEdgeType.DROP_SHADOW -> {
            val offsetPx = style.shadowOffset * 2f
            Shadow(
                color = style.edgeColor,
                offset = Offset(offsetPx, offsetPx),
                blurRadius = 3f,
            )
        }
        SubtitleEdgeType.RAISED -> {
            Shadow(
                color = style.edgeColor.copy(alpha = 0.8f),
                offset = Offset(1.5f, 1.5f),
                blurRadius = 1.5f,
            )
        }
        SubtitleEdgeType.DEPRESSED -> {
            Shadow(
                color = style.edgeColor.copy(alpha = 0.8f),
                offset = Offset(-1.5f, -1.5f),
                blurRadius = 1.5f,
            )
        }
    }
}

