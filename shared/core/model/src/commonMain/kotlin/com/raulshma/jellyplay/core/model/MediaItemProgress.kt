package com.raulshma.jellyplay.core.model

/**
 * The single home for resume-progress math: `position / runtime` coerced into
 * 0..1. Null when either ticks value is missing or invalid (position null;
 * runtime null or <= 0) so callers can distinguish "no progress to show" from
 * a real fraction. A zero position is NOT null — it resolves to 0f, keeping
 * "show the bar only when there is progress" a caller-side `> 0f` check.
 *
 * [positionTicks] lets smart-play / next-up call sites divide by the
 * resolver's start position (which can differ from the episode's saved
 * [MediaItem.playbackPositionTicks]) under the same null/clamp rules; the
 * default reads the item's own saved position.
 */
fun MediaItem.progressFraction(positionTicks: Long? = playbackPositionTicks): Float? {
    val position = positionTicks ?: return null
    val runtime = runTimeTicks?.takeIf { it > 0 } ?: return null
    return (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
}
