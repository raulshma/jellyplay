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

/**
 * Reading progress (0..1) for a BOOK item, decoded from the
 * [BookProgressPolicy] ticks encodings the reader reports — [progressFraction]
 * cannot apply here because books carry no `runTimeTicks`, so the video
 * position/runtime math never leaves 0.
 *
 * A known `pageCount` (paged PDF/CBZ, from the book TOC cache) resolves the
 * page index encoding exactly; everything else — EPUB reflowable percent
 * ticks, or an unknown page count — falls back to the percent decoding
 * (`ticksToPercent`), which is also the correct reading of percent-encoded
 * paged positions within a page. Null when the item has no saved position, so
 * "no progress to show" stays distinguishable from a 0-position start.
 */
fun MediaItem.bookProgressFraction(pageCount: Int? = null): Float? {
    val position = playbackPositionTicks ?: return null
    if (position <= 0L) return null
    val fraction = if (pageCount != null && pageCount > 0) {
        (BookProgressPolicy.ticksToPage(position) + 1f) / pageCount
    } else {
        BookProgressPolicy.ticksToPercent(position).toFloat()
    }
    return fraction.coerceIn(0f, 1f)
}
