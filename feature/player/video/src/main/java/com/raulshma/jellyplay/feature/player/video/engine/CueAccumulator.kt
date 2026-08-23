package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Caps the accumulated cue list length to bound memory over a long watch.
 * Matches the constant used by ExoPlayerEngine's onCues accumulation.
 */
internal const val MAX_ACCUMULATED_CUES = 500

/**
 * Per-batch ceiling on the cues folded in by a single [mergeAccumulatedCues]
 * call, and the threshold for [isPathologicalCueBatch]. A legitimate subtitle
 * stream surfaces at most a handful of simultaneously-active cues per `onCues`
 * callback (typical max ~3-4); a malformed text track (e.g. a broken SRT whose
 * timestamp/index lines parse as simultaneous cues) hands Media3 dozens or
 * hundreds at one presentation time. The engine uses this threshold to detect
 * that and auto-disable the offending track before the native `SubtitleView`
 * lays them all out (the "subtitle wall" that freezes the UI and crashes the
 * app). 32 is comfortably above any real-world simultaneous-subtitle count.
 */
internal const val MAX_INCOMING_CUES_PER_BATCH = 32

/**
 * True when a single `onCues` batch carries an implausibly large number of
 * simultaneous cues — the signature of a malformed text subtitle track that
 * would otherwise be laid out in full by the native renderer. Extracted to a
 * pure predicate so the detection logic is unit-testable without an engine.
 */
internal fun isPathologicalCueBatch(count: Int): Boolean = count > MAX_INCOMING_CUES_PER_BATCH

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
    // Defense-in-depth: cap the incoming batch so even a borderline-large
    // delivery can't blow up the per-tick merge/sort cost. The engine-level
    // detector disables truly pathological tracks before this is reached.
    val batched = if (incoming.size > MAX_INCOMING_CUES_PER_BATCH) {
        incoming.take(MAX_INCOMING_CUES_PER_BATCH)
    } else {
        incoming
    }
    if (existing.isEmpty()) return batched.distinctBy { it.text }
    val newStart = batched.first().startTimeUs
    // Close the open-ended span of any existing cue that is still "active"
    // (end == MAX) at the point the new cue begins.
    var changed = false
    val closed = existing.map { cue ->
        if (cue.endTimeUs == Long.MAX_VALUE && cue.startTimeUs < newStart) {
            changed = true
            cue.copy(endTimeUs = newStart)
        } else {
            cue
        }
    }
    // Drop an incoming line identical to the last recorded one (ExoPlayer
    // re-emits the active cue on each rendering refresh).
    val lastText = closed.lastOrNull()?.text
    val fresh = if (lastText != null && batched.all { it.text.toString() == lastText.toString() }) {
        emptyList()
    } else {
        batched.distinctBy { it.text }
    }
    // Common no-change case (re-emission of the active cue): nothing was closed
    // and nothing is fresh, so the sorted/capped result below would be
    // structurally equal to [existing]. Return it as-is so the caller's
    // StateFlow assignment short-circuits on identity instead of paying the
    // O(n) equals per onCues tick.
    if (fresh.isEmpty() && !changed) return existing
    return (closed + fresh)
        .sortedBy { it.startTimeUs }
        .takeLast(MAX_ACCUMULATED_CUES)
}
