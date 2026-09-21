package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.bookProgressFraction
import com.raulshma.jellyplay.core.model.hasMeaningfulRuntime
import com.raulshma.jellyplay.core.model.hasWatchProgress
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

/**
 * The card footer's trailing meta segment as a pure value — the whole
 * book-vs-time decision both card shells (PosterCard, WideMediaCard) used to
 * hand-derive as the same `hasValidDuration` / `hasWatchProgress` /
 * remaining-total ladder and render as the same "• label" pair, pasted
 * verbatim (down to the "Books never render runtime/time-left meta"
 * comment) in both footers. One decision here; the shells are thin
 * renderers over it.
 *
 * Precedence (both cards, unchanged): a book in progress wins over any time
 * label; then mid-playback items with a meaningful runtime show the
 * remaining time ([TimeLeft]); unstarted items with a meaningful runtime
 * show the total runtime ([Runtime]); everything else — books without
 * progress, series containers, no/sub-minute runtimes — renders nothing.
 * A mid-playback item whose remaining math comes back empty (position past
 * the runtime) yields null, exactly as the former ladder did (the total
 * arm was gated on NOT having watch progress, so there was no fallback
 * between the two).
 *
 * Each shell keeps its own typography, divider-glyph color, and
 * divider-visibility rule (the poster card always draws the "•" before the
 * label, the wide card only when a leading subtitle/year text precedes it)
 * — those are card chrome, not part of the decision.
 */
sealed interface MediaCardFooterMeta {
    /** A book's reading progress: render `"$percent% complete"` in the accent role. */
    data class BookProgress(val percent: Int) : MediaCardFooterMeta

    /** Remaining time for an in-progress item: render `"$label left"` in the accent role. */
    data class TimeLeft(val label: String) : MediaCardFooterMeta

    /** Total runtime for an unstarted item: render [label] in the plain role. */
    data class Runtime(val label: String) : MediaCardFooterMeta
}

/**
 * Resolves [item]'s footer meta segment: [bookFooterPercent] first (books
 * own their footer), then the time ladder over the model predicates
 * ([MediaItem.hasMeaningfulRuntime] + [MediaItem.hasWatchProgress] — the
 * former hand-rolled `hasValidDuration` / `playbackPositionTicks != null &&
 * > 0 && !isPlayed` copies). Null when the footer shows no meta.
 */
fun mediaCardFooterMeta(item: MediaItem, bookFractionOverride: Float? = null): MediaCardFooterMeta? {
    bookFooterPercent(item, bookFractionOverride)?.let { return MediaCardFooterMeta.BookProgress(it) }
    if (!item.hasMeaningfulRuntime) return null
    return if (item.hasWatchProgress) {
        formatRemainingTimeFromTicks(item.runTimeTicks!!, item.playbackPositionTicks!!)
            ?.let(MediaCardFooterMeta::TimeLeft)
    } else {
        formatRuntimeLabelFromTicks(item.runTimeTicks)?.let(MediaCardFooterMeta::Runtime)
    }
}
