package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.bookProgressFraction
import kotlin.math.roundToInt

/**
 * The media-card footer's book-progress fold — the ONE admission + percent
 * derivation for every card shape (poster, wide, offline). Written once
 * here because the card sites hand-copied it and had drifted: the poster
 * card accepted a TOC-accurate [fractionOverride] while the wide card
 * always fell back to the item's approximate fraction.
 *
 * Books never render runtime/time-left meta (RunTimeTicks is absent or
 * meaningless for them, and their position ticks encode reading progress —
 * page index or percent — not time); a book in progress shows "% complete"
 * from the [bookProgressFraction] decode, a played or unstarted book shows
 * nothing. The override admits the same values for every card: callers pass
 * their row fraction directly and THIS fold owns the `mediaType == BOOK`
 * admission, so a non-book override can never produce a label and the
 * per-site `takeIf` guards are gone.
 */
fun bookFooterPercent(item: MediaItem, fractionOverride: Float? = null): Int? {
    if (item.mediaType != MediaType.BOOK || item.isPlayed) return null
    val fraction = fractionOverride ?: item.bookProgressFraction() ?: return null
    return (fraction * 100).roundToInt().coerceIn(0, 100).takeIf { it > 0 }
}
