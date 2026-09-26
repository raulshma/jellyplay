package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior

/**
 * Pure decision logic for the skip-on-forward-seek clamp: when a
 * user-initiated seek lands strictly inside a segment whose effective
 * behavior is AUTO_SKIP, the target is pulled to the segment's end instead of
 * dropping the user into the middle of what they've asked to skip. Sibling of
 * [SegmentSkipPolicy.kt] (the button/auto-skip ladders) and a fourth consumer
 * of the same segment vocabulary — but a separate policy: it is seek-direction
 * aware, not position-tick aware.
 */

/**
 * The near-duration guard's window: a segment whose end is within the last
 * 1% of the duration (`remaining * TAIL_WINDOW_DENOMINATOR < duration`) is
 * never clamped — a clamp there would seek to (effectively) the item's end
 * and race the up-next / playback-ended machinery that already owns that
 * window. Exposed for the test matrix.
 */
internal const val SEGMENT_SEEK_CLAMP_TAIL_WINDOW_DENOMINATOR = 100L

/**
 * What a clamped seek resolved to: the adjusted target (the segment's end in
 * milliseconds, truncating `ticks / 10_000` like every other ticks→ms
 * conversion in the player) and the segment type that caused it — the type
 * feeds the "Skipped …" confirmation toast, the target feeds the engine.
 */
internal data class SegmentSeekClamp(
    val adjustedTargetMs: Long,
    val segmentType: MediaSegmentType,
)

/**
 * Effective per-type behavior, mirroring
 * `SegmentCalculator.behaviorForType` (stored value wins, absence means
 * IGNORE) so the clamp can never disagree with the position-tick auto-skip
 * about what AUTO_SKIP means.
 */
private fun effectiveBehavior(
    type: MediaSegmentType,
    segmentBehaviors: Map<MediaSegmentType, SegmentBehavior>,
): SegmentBehavior = segmentBehaviors[type] ?: SegmentBehavior.IGNORE

/**
 * The clamp decision for one candidate segment: whether [segment] absorbs
 * [targetMs] (strictly inside, forward seek only, AUTO_SKIP-effective, end
 * not in the last 1% of [durationMs]).
 */
private fun MediaSegment.absorbsForwardSeek(
    targetMs: Long,
    currentPositionMs: Long,
    segmentBehaviors: Map<MediaSegmentType, SegmentBehavior>,
    durationMs: Long,
): Boolean {
    if (!hasSegment) return false
    if (effectiveBehavior(type, segmentBehaviors) != SegmentBehavior.AUTO_SKIP) return false
    // Half-open interval, matching SegmentCalculator's containsPos(): a seek
    // to exactly endMs is already outside the segment; startMs itself is
    // inside. The current position must be before the target for the seek to
    // be forward at all.
    if (targetMs <= currentPositionMs) return false
    if (targetMs <= startMs || targetMs >= endMs) return false
    // Near-duration guard: without a known duration the guard cannot fire, so
    // no clamp happens at all (live streams / unresolved containers are left
    // alone — the same conservatism as seekForwardTargetMs's duration clamp).
    if (durationMs <= 0L) return false
    val remainingMs = durationMs - endMs
    if (remainingMs * SEGMENT_SEEK_CLAMP_TAIL_WINDOW_DENOMINATOR < durationMs) return false
    return true
}

/**
 * Resolves the clamp for a forward seek, or null when the target passes
 * through unchanged. Overlapping AUTO_SKIP segments resolve in
 * [MediaSegmentType.SEGMENT_PRIORITY] order — the same priority scan
 * `SegmentCalculator.computeActiveSegment` uses, so the segment that "owns"
 * the target position on the next tick is the one the clamp honors.
 *
 * All the guard rails of the feature live here: [enabled] off passes through,
 * backward seeks never clamp, only AUTO_SKIP-effective segments clamp,
 * SHOW_BUTTON/IGNORE segments never do, and a segment ending inside the last
 * 1% of the duration is left alone (an outro that should hand off to up-next
 * is not "skipped" by a seek grazing it).
 */
internal fun resolveForwardSeekSegmentClamp(
    targetMs: Long,
    currentPositionMs: Long,
    segments: List<MediaSegment>,
    segmentBehaviors: Map<MediaSegmentType, SegmentBehavior>,
    durationMs: Long,
    enabled: Boolean,
): SegmentSeekClamp? {
    if (!enabled) return null
    if (targetMs <= currentPositionMs) return null
    if (durationMs <= 0L) return null
    val candidate = MediaSegmentType.SEGMENT_PRIORITY.firstNotNullOfOrNull { priority ->
        segments.firstOrNull { segment ->
            segment.type == priority && segment.absorbsForwardSeek(
                targetMs = targetMs,
                currentPositionMs = currentPositionMs,
                segmentBehaviors = segmentBehaviors,
                durationMs = durationMs,
            )
        }
    } ?: segments.firstOrNull { segment ->
        segment.absorbsForwardSeek(
            targetMs = targetMs,
            currentPositionMs = currentPositionMs,
            segmentBehaviors = segmentBehaviors,
            durationMs = durationMs,
        )
    } ?: return null
    return SegmentSeekClamp(
        adjustedTargetMs = candidate.endTicks / 10_000,
        segmentType = candidate.type,
    )
}
