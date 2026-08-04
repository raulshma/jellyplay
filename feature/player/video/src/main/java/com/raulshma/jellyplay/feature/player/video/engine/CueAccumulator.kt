package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Caps the accumulated cue list length to bound memory over a long watch.
 * Matches the constant used by ExoPlayerEngine's onCues accumulation.
 */
internal const val MAX_ACCUMULATED_CUES = 500

/**
 * Folds [incoming] (cues from one onCues callback, all sharing a start time)
 * into [existing] — the running accumulated list. ExoPlayer surfaces only the
 * *currently displayed* cue(s) per callback, so the preview is built
 * incrementally as subs play.
 *
 * Rules:
 *  - The prior cue's open-ended (Long.MAX_VALUE) end time is resolved to the
 *    new cue's start, turning the placeholder into a real span.
 *  - An incoming line identical to the last recorded one is dropped (ExoPlayer
 *    re-emits the active cue on each rendering refresh).
 *  - The list stays sorted by startTimeUs and is capped to the most recent
 *    [MAX_ACCUMULATED_CUES] entries.
 *
 * Extracted to a top-level internal function so the merge logic is unit-
 * testable without an ExoPlayer instance.
 */
internal fun mergeAccumulatedCues(
    existing: List<TimedCue>,
    incoming: List<TimedCue>,
): List<TimedCue> {
    if (existing.isEmpty()) return incoming.distinctBy { it.text }
    val newStart = incoming.first().startTimeUs
    // Close the open-ended span of any existing cue that is still "active"
    // (end == MAX) at the point the new cue begins.
    val closed = existing.map { cue ->
        if (cue.endTimeUs == Long.MAX_VALUE && cue.startTimeUs < newStart) {
            cue.copy(endTimeUs = newStart)
        } else {
            cue
        }
    }
    // Drop an incoming line identical to the last recorded one (ExoPlayer
    // re-emits the active cue on each rendering refresh).
    val lastText = closed.lastOrNull()?.text
    val fresh = if (lastText != null && incoming.all { it.text.toString() == lastText.toString() }) {
        emptyList()
    } else {
        incoming.distinctBy { it.text }
    }
    return (closed + fresh)
        .sortedBy { it.startTimeUs }
        .takeLast(MAX_ACCUMULATED_CUES)
}
