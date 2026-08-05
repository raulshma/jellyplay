package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver

/**
 * Zoom-safe subtitle overlay for engines whose native rendering cannot be
 * reparented out of the video surface (mpv: libass composites into the GPU
 * video surface). Renders the single live line exposed via
 * [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine.liveSubtitleCue]
 * as a bottom-aligned [Text], pinned to the screen (a sibling of the zoomed
 * video, outside the pinch/crop transform).
 *
 * Style is resolved from the user's [SubtitleStyle] via [SubtitleColorResolver]
 * so it visually matches the native captions the engine renders at zoom == 1.
 * ASS in-line positioning/styling is not representable in plain text and is
 * lost while this overlay is shown — the documented trade-off of the mpv path.
 * At zoom == 1 this composable is not composed at all (native libass renders
 * with full fidelity), so there is zero degradation in the normal case.
 *
 * Only shown for engines advertising
 * [com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities.supportsCueSubtitleOverlay]
 * (mpv). ExoPlayer uses the screen-pinned native SubtitleView host instead.
 *
 * @param cue the current subtitle line; blank -> renders nothing.
 */
@Composable
internal fun MpvSubtitleOverlay(
    cue: CharSequence?,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    if (cue.isNullOrBlank()) return
    // Transparent box: never intercepts the pointer events the gesture overlay
    // beneath it needs. Only the Text is drawn.
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            // mpv's sub-text is a CharSequence (CharSequence? on the flow);
            // Material3 Text requires String/AnnotatedString. toString() is
            // lossless here — sub-text is already plain text (ASS override tags
            // stripped by mpv).
            text = cue.toString(),
            // resolveTextColor folds the ARGB-over-enum fallback; mirror the
            // native SubtitleView styling path (ExoPlayerEngine) exactly.
            color = Color(SubtitleColorResolver.resolveTextColor(style)),
            fontSize = style.fontSize.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            // Drop-shadow / outline via a TextStyle shadow, matching the native
            // edge type for visual consistency. Compose Text has no `shadow`
            // parameter, so the shadow rides on the style. RAISED/DEPRESSED
            // collapse to "no shadow" since Compose has no direct equivalent.
            style = androidx.compose.ui.text.TextStyle(shadow = shadowFor(style)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 32.dp,
                    // verticalPosition is a fraction the native renderer uses as
                    // bottom padding; lift the overlay the same fraction off the
                    // bottom so the two stay aligned.
                    vertical = (style.verticalPosition * 64f).dp.coerceAtLeast(8.dp),
                )
                .then(
                    if (style.backgroundOpacity > 0f) {
                        Modifier.background(
                            Color(SubtitleColorResolver.resolveBackgroundColor(style))
                                .copy(alpha = style.backgroundOpacity.coerceIn(0f, 1f))
                        )
                    } else {
                        Modifier
                    }
                ),
        )
    }
}

/**
 * Maps the user's [SubtitleEdgeType] to a Compose text [Shadow]. OUTLINE is
 * approximated with a tight, low-offset shadow in the edge color (Compose text
 * has no stroke API); DROP_SHADOW uses a larger offset. NONE/RAISED/DEPRESSED
 * return `null` (no shadow), matching the "no edge" / unrepresentable cases.
 */
private fun shadowFor(style: SubtitleStyle): Shadow? {
    val edgeColor = Color(SubtitleColorResolver.resolveEdgeColor(style))
    return when (style.edgeType) {
        SubtitleEdgeType.NONE -> null
        SubtitleEdgeType.OUTLINE ->
            Shadow(color = edgeColor, blurRadius = 3f, offset = Offset(1f, 1f))
        SubtitleEdgeType.DROP_SHADOW ->
            Shadow(color = edgeColor, blurRadius = 4f, offset = Offset(2f, 2f))
        SubtitleEdgeType.RAISED,
        SubtitleEdgeType.DEPRESSED -> null
    }
}
