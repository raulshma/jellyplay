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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver
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
 * - Resolves font typeface (custom user font or bundled fallback) via [FontProvider].
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
    modifier: Modifier = Modifier,
) {
    if (cue.isNullOrBlank()) return

    val context = LocalContext.current
    val fontProvider = remember(context) { FontProvider(context.applicationContext) }
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
    return if (style.applyCustomStyle) {
        EffectiveMpvSubtitleStyle(
            rawStyle = style,
            textColor = Color(SubtitleColorResolver.resolveTextColor(style)),
            backgroundColor = Color(SubtitleColorResolver.resolveBackgroundColor(style)),
            backgroundAlpha = style.backgroundOpacity.coerceIn(0f, 1f),
            edgeColor = Color(SubtitleColorResolver.resolveEdgeColor(style)),
            edgeType = style.edgeType,
            borderWidth = style.borderWidth,
            shadowOffset = style.shadowOffset,
            fontSize = style.fontSize,
            verticalPosition = style.verticalPosition,
            bold = style.bold,
            italic = style.italic,
        )
    } else {
        // Native mpv default fallback when applyCustomStyle == false
        EffectiveMpvSubtitleStyle(
            rawStyle = style.copy(bold = false, italic = false, fontFamilyName = null, fontFamilyPath = null),
            textColor = Color.White,
            backgroundColor = Color.Black,
            backgroundAlpha = 0f,
            edgeColor = Color.Black,
            edgeType = SubtitleEdgeType.OUTLINE,
            borderWidth = 3.0f,
            shadowOffset = 0.0f,
            fontSize = SubtitleDefaults.REFERENCE_FONT_SIZE,
            verticalPosition = style.verticalPosition,
            bold = false,
            italic = false,
        )
    }
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

