package com.raulshma.jellyplay.feature.player.video

/**
 * Computes a subtitle-sync delay from two press-and-hold measurements
 * (modelled on mpvKt's `SubtitleDelayPanel` "voice heard" / "text seen" helper).
 *
 * UX: the user holds "🔊 Voice heard" the instant they hear the spoken line,
 * then holds "💬 Text seen" the instant the matching subtitle appears. The
 * elapsed time between the two *release* moments is the misalignment; the
 * sign convention is "shift subtitles so text aligns with voice":
 *  - If the subtitle appeared *after* the voice (textSeen > voiceHeard), subs
 *    are late → delay should be negative (move subs earlier). Wait — that
 *    means we need to *subtract* from the current delay, so the delta to apply
 *    is `(voiceHeard - textSeen)`.
 *
 * Put simply: `suggestedDelayDelta = voiceHeardMs - textSeenMs`.
 *  - text late (textSeen > voiceHeard) → negative delta → subs move earlier. ✓
 *  - text early (textSeen < voiceHeard) → positive delta → subs move later. ✓
 *
 * Pure so it is fully unit-testable; the UI feeds the two timestamps.
 */
object SubtitleSyncCalculator {

    /**
     * @param voiceHeardMs wall-clock ms (e.g. SystemClock.elapsedRealtime) when
     *  the "voice heard" button was released.
     * @param textSeenMs wall-clock ms when the "text seen" button was released.
     * @return the delay delta (ms) to apply to the current subtitle delay:
     *  positive = shift subs later, negative = shift subs earlier. Returned
     *  value is in **wall-clock** ms; at non-1.0 playback speed the caller must
     *  divide by the speed to convert to media-time ms before applying.
     */
    fun computeDelayDelta(voiceHeardMs: Long, textSeenMs: Long): Long =
        voiceHeardMs - textSeenMs

    /**
     * Convenience: applies the delta to a current delay, clamped to a sane bound
     * (±[maxAbsMs], default ±30 s) so a stray double-press can't push subs
     * absurdly far. Matches the slider range on AVSyncSheet and
     * SubtitleStyleControls so a computed delta is always representable.
     */
    fun applyDelta(currentDelayMs: Long, deltaMs: Long, maxAbsMs: Long = 30_000L): Long =
        (currentDelayMs + deltaMs).coerceIn(-maxAbsMs, maxAbsMs)
}
