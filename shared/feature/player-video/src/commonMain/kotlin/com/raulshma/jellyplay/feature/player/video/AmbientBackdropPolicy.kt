package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType

/**
 * Pure decision logic behind the ambient backdrop (#12): when the video is
 * letterboxed/pillarboxed (its native aspect differs from the screen's), the
 * player draws a heavily blurred, scaled copy of the item's artwork behind
 * the video surface so the black bars carry a soft glow instead of pure
 * black. Extracted from composition so the aspect comparison is
 * JVM-testable ([AmbientBackdropPolicyTest]); the composable
 * (`components/AmbientBackdropLayer.kt`) stays a thin renderer. No Compose
 * types in these signatures.
 */

/**
 * Relative aspect difference below which the video is considered to FILL the
 * screen (no visible bars → no backdrop). 2% absorbs engine rounding and
 * off-by-a-pixel stream dimensions; a 2.35:1 movie on a 16:9 screen (~32%
 * difference) clears it by an order of magnitude.
 */
internal const val AMBIENT_ASPECT_TOLERANCE = 1.02f

/**
 * The video stream's native aspect (width/height of the first video stream),
 * or null when the session has no usable stream geometry (nothing loaded,
 * audio-only, malformed dimensions) — the backdrop stays off rather than
 * guessing.
 */
internal fun videoStreamAspect(streams: List<MediaStream>): Float? {
    val videoStream = streams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
    val width = videoStream.width ?: return null
    val height = videoStream.height ?: return null
    if (width <= 0 || height <= 0) return null
    return width.toFloat() / height.toFloat()
}

/**
 * The ambient-backdrop gate: on only when the pref is on AND both aspects are
 * known AND they differ beyond [AMBIENT_ASPECT_TOLERANCE] (a matching aspect
 * means the video fills the surface — there are no bars to decorate).
 * The caller folds the user's aspect-OVERRIDE modes (FILL/CROP, which fill
 * the surface regardless of the stream geometry) into [enabled] at the call
 * site.
 */
internal fun shouldShowAmbientBackdrop(
    videoAspect: Float?,
    screenAspect: Float?,
    enabled: Boolean,
): Boolean {
    if (!enabled) return false
    if (videoAspect == null || screenAspect == null) return false
    if (videoAspect <= 0f || screenAspect <= 0f) return false
    val spread = maxOf(videoAspect, screenAspect) / minOf(videoAspect, screenAspect)
    return spread > AMBIENT_ASPECT_TOLERANCE
}
