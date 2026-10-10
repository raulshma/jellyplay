package com.raulshma.jellyplay.feature.player.video.state

/**
 * Pure decision logic for the double-tap-and-hold continuous
 * seek on the player surface's tap zones. Extracted from
 * `VideoPlayerScreenGestures.playerTapAndZoomGestures` (which moved off
 * `detectTapGestures` onto `awaitEachGesture` precisely because this gesture
 * cannot be expressed there) so the zone split and the repeat cadence are
 * JVM-testable with zero Compose deps.
 *
 * Semantics (pinned by `DoubleTapHoldSeekPolicyTest`):
 *  - the zone split matches the long-standing inline double-tap thresholds
 *    (x < 35% = back, x > 65% = forward, else center) — ONE vocabulary for
 *    the double-tap step and the hold repeat;
 *  - the hold fires the first step seek the moment the hold is detected, then
 *    repeats at [repeatIntervalMs] — slow at first, accelerating after a few
 *    repeats so a long hold closes big distances (the SubtitleDelayOverlay
 *    hold-repeat shape, gentler: seek commits are engine seeks, not local
 *    ticks). Every repeat commits immediately (addOffset + step seek — the
 *    existing double-tap commit pattern; scroll/keyboard-style input has no
 *    clean release event to defer a commit to).
 */
internal object DoubleTapHoldSeekPolicy {

    /** Left seek zone: x < 35% of surface width (the inline double-tap constant). */
    const val SEEK_ZONE_LEFT_FRACTION = 0.35f

    /** Right seek zone: x > 65% of surface width (the inline double-tap constant). */
    const val SEEK_ZONE_RIGHT_FRACTION = 0.65f

    /**
     * Delay between the hold being detected (the first step fires there) and
     * the first repeat. Longer than a fast-forward would want, shorter than a
     * user's "wait, is it repeating?" threshold.
     */
    const val FIRST_REPEAT_DELAY_MS = 400L

    /** Steady-state repeat cadence once accelerating has kicked in. */
    const val FAST_REPEAT_MS = 150L

    /** Initial repeat cadence — one step every [NORMAL_REPEAT_MS] at first. */
    const val NORMAL_REPEAT_MS = 400L

    /** Repeats completed before the cadence accelerates to [FAST_REPEAT_MS]. */
    const val ACCELERATION_AFTER_REPEATS = 4

    /**
     * Zone of an x coordinate in `[0, width]`: −1 back, 0 center, +1 forward —
     * the exact split the inline double-tap handler used.
     */
    fun seekZone(x: Float, widthPx: Int): Int = when {
        x < widthPx * SEEK_ZONE_LEFT_FRACTION -> -1
        x > widthPx * SEEK_ZONE_RIGHT_FRACTION -> +1
        else -> 0
    }

    /**
     * Delay before the NEXT repeat after [completedRepeats] repeats have
     * fired: [NORMAL_REPEAT_MS] at first, [FAST_REPEAT_MS] after
     * [ACCELERATION_AFTER_REPEATS]. Monotonically non-increasing in
     * [completedRepeats] and always positive.
     */
    fun repeatIntervalMs(completedRepeats: Int): Long =
        if (completedRepeats < ACCELERATION_AFTER_REPEATS) NORMAL_REPEAT_MS else FAST_REPEAT_MS
}
