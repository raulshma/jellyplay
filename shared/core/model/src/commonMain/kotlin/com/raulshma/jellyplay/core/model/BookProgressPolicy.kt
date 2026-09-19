package com.raulshma.jellyplay.core.model

/**
 * The ticks↔page math for book reading progress, in one place.
 *
 * ## jellyfin-web / Fladder interop convention
 *
 * Jellyfin has no first-class "book page" field: both jellyfin-web's PDF
 * reader and Fladder encode reading progress as
 * `PlaybackPositionTicks = pageIndex * 10_000` (one nominal "tick page" per
 * page index), reported through the standard `/Sessions/Playing/Progress`
 * user-data path. jellyfin-web's PDF page index in that encoding is
 * **0-based** (`page 0` ⇒ `0` ticks), so [ticksToPage] returns the same
 * 0-based index back and `Ticks / 10_000` round-trips exactly against what
 * those clients store. PDF pages beyond the first render at their native
 * aspect ratios, so the ticks value is a page *index*, never a time.
 *
 * ## Reflowable (EPUB) percent convention
 *
 * Reflowable books have no pages, so — again matching the jellyfin-web
 * interop — the reading position persists as a **percent**:
 * `ticks = floor(percent * [TICKS_MAX_PERCENT])` with `percent` in 0..1.
 */
object BookProgressPolicy {

    /** One nominal page of reading progress, in Jellyfin ticks. */
    const val TICKS_PER_PAGE = 10_000L

    /** Ticks that represent 100% reading position (`percent 1.0`). */
    const val TICKS_MAX_PERCENT = 10_000_000L

    /** The 0-based [pageIndex] as stored ticks (`pageIndex * [TICKS_PER_PAGE]`). */
    fun pageToTicks(pageIndex: Int): Long =
        pageIndex.coerceAtLeast(0).toLong() * TICKS_PER_PAGE

    /**
     * The 0-based page index for [ticks] (`ticks / [TICKS_PER_PAGE]`).
     * Negative or zero ticks — no saved position — resolve to page 0; huge
     * ticks clamp to [Int.MAX_VALUE] (callers [clampPage] against the real
     * page count anyway).
     */
    fun ticksToPage(ticks: Long): Int =
        if (ticks <= 0L) {
            0
        } else {
            (ticks / TICKS_PER_PAGE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    /** `floor([percent] × [TICKS_MAX_PERCENT])`; [percent] clamps to 0..1 first. */
    fun percentToTicks(percent: Double): Long =
        (percent.coerceIn(0.0, 1.0) * TICKS_MAX_PERCENT).toLong()

    /**
     * The reading percent (0.0..1.0) for stored [ticks], clamped — negative or
     * absent ticks resume at 0, oversized ticks clamp to 1.
     */
    fun ticksToPercent(ticks: Long): Double =
        (ticks.toDouble() / TICKS_MAX_PERCENT).coerceIn(0.0, 1.0)

    /** Clamp a 0-based [page] into `0 until [pageCount]`; an empty book collapses to 0. */
    fun clampPage(page: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)
}
