package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType

/**
 * Pure decision logic behind the ambient backdrop (#12): when the video is
 * letterboxed/pillarboxed (its native aspect differs from the screen's), the
 * player draws a heavily blurred, scaled copy of the item's artwork behind
 * the video surface so the black bars carry a soft glow instead of pure
 * black. Extracted from composition so the aspect comparison, the artwork
 * decode bound and the blur gate are all JVM-testable
 * ([AmbientBackdropPolicyTest]); the composable
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

/**
 * The ambient backdrop's artwork decode bound, in px per side: the layer
 * requests its artwork inside a square 720² box, and Coil fits the source
 * WITHIN both bounds with the aspect preserved — a 4K 16:9 still decodes at
 * roughly 720×405, a portrait poster at roughly 480×720 — so the backdrop's
 * bitmap costs at most ~720²×4 B ≈ 2 MB of ARGB no matter the source
 * resolution, against a full-resolution decode that a 4K still can push into
 * the tens of MB.
 *
 * The value sits just under the image posture's own full-bleed tier
 * ([DECODE_FULL_BLEED_TIER_PX] = 768 — the size the pipeline already accepts
 * as "sharp-enough for a full-screen image"); a copy that an 80 dp blur and
 * a 55% black dim then soften can afford to sit a notch below even that.
 *
 * The bound is REQUESTED EXPLICITLY, not inherited: the layer opts out of
 * the posture clamp (`performanceModeAware = false`, see core:ui's
 * `mediaImageDecodeSize` — with the clamp armed, a request whose largest
 * side sits below the 1080 px full-bleed threshold collapses to the 256²
 * floor tier, the "crushed backdrop" failure the opt-out exists to avoid).
 * The explicit size keeps the opt-out and still bounds the decode: the
 * unaware branch passes the request through byte-identically under every
 * posture, so the bound cannot drift with the user's performance settings.
 */
internal const val AMBIENT_BACKDROP_DECODE_PX = 720

/**
 * Whether the ambient copy applies its blur for [tier] — the per-frame half
 * of the layer's cost policy. The DECODE is a one-shot cost (bounded by
 * [AMBIENT_BACKDROP_DECODE_PX]); the blur is not: an 80 dp full-screen
 * RenderEffect runs a gaussian under the playing video on every composited
 * frame, on hardware whose GPU budget the video itself is already stretching.
 *
 * LOW tier (the AUTO detector folds low-RAM hardware into it —
 * [com.raulshma.jellyplay.core.model.PerformanceReason.LOW_RAM] is one of its
 * LOW signals) drops the blur but KEEPS the layer: the downscaled artwork
 * plus the black scrim still glow behind the bars, so the letterbox treatment
 * survives as a cheap static composite, without paying a per-frame full-screen
 * effect on the weakest devices. MEDIUM/HIGH keep the full blurred treatment.
 *
 * The caller supplies the session's resolved tier
 * (`uiState.uiPrefs.performanceTuning.tier` — the same resolution the engine
 * config consumes). No null branch: the state's pre-resolution posture is
 * [com.raulshma.jellyplay.core.model.VideoPerformanceTuning.NONE], whose
 * tier is HIGH, so an unresolved session reads as "unrestricted" and keeps
 * the treatment.
 */
internal fun shouldBlurAmbientBackdrop(tier: DevicePerformanceTier): Boolean = when (tier) {
    DevicePerformanceTier.LOW -> false
    DevicePerformanceTier.MEDIUM, DevicePerformanceTier.HIGH -> true
}
