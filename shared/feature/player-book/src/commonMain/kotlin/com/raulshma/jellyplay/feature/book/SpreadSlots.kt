package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReadingLayout

/**
 * Pure page↔slot mapping for the paged reader's double-page (spread) mode.
 *
 * Slots are what the pager steps through; pages stay the universal currency —
 * progress ticks, bookmarks and TOC/page jumps remain page-indexed and the
 * mapping happens only at the pager boundary. The cover-alone convention
 * (jellyfin-web / standard manga readers): page 0 renders alone, then pages
 * pair up (1,2), (3,4), …  In SINGLE mode every page is its own slot and all
 * functions degenerate to the identity, so callers can use them unconditionally.
 */
object SpreadSlots {

    /** Number of pager slots for [pageCount] pages under [layout]. */
    fun slotCount(pageCount: Int, layout: ReadingLayout): Int = when (layout) {
        ReadingLayout.SINGLE -> pageCount.coerceAtLeast(0)
        // Page 0 alone + ceil((pageCount-1)/2) pairs — integer math: 1 + pageCount/2.
        ReadingLayout.DOUBLE -> if (pageCount <= 0) 0 else 1 + pageCount / 2
    }

    /** The first page shown in [slot] (the slot's left/lead page). */
    fun firstPageOfSlot(slot: Int, pageCount: Int, layout: ReadingLayout): Int = when (layout) {
        ReadingLayout.SINGLE -> slot.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        ReadingLayout.DOUBLE -> {
            val slots = slotCount(pageCount, layout)
            val clamped = slot.coerceIn(0, (slots - 1).coerceAtLeast(0))
            if (clamped == 0) 0 else (clamped * 2 - 1)
        }
    }

    /** The pages rendered in [slot] — one or two page indices, in lead→trailing order. */
    fun pagesOfSlot(slot: Int, pageCount: Int, layout: ReadingLayout): List<Int> = when (layout) {
        ReadingLayout.SINGLE -> {
            if (slot in 0 until pageCount) listOf(slot) else emptyList()
        }
        ReadingLayout.DOUBLE -> {
            val slots = slotCount(pageCount, layout)
            if (slot !in 0 until slots) return emptyList()
            val lead = firstPageOfSlot(slot, pageCount, layout)
            val trailing = lead + 1
            if (slot == 0) listOf(0)
            else if (trailing < pageCount) listOf(lead, trailing)
            else listOf(lead)
        }
    }

    /** The slot [page] lives in. */
    fun slotForPage(page: Int, pageCount: Int, layout: ReadingLayout): Int = when (layout) {
        ReadingLayout.SINGLE -> page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        // Odd pages own the pair slot ((p+1)/2); even pages share it (p/2).
        ReadingLayout.DOUBLE -> {
            if (pageCount <= 0 || page <= 0) 0
            else (page + (page and 1)) / 2
        }
    }
}
