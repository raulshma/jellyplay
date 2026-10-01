package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.bookProgressFraction
import com.raulshma.jellyplay.core.model.hasMeaningfulRuntime
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_card_percent_complete
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_card_time_left
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

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
 * Rendering is shared too ([MediaCardFooterMetaRow] — the shells had
 * hand-pasted the same `when` twice, baked with string-concatenated English
 * copy); each shell keeps its own typography and divider chrome via that
 * renderer's parameters.
 */
sealed interface MediaCardFooterMeta {
    /** A book's reading progress: render the `core_ui_card_percent_complete` label in the accent role. */
    data class BookProgress(val percent: Int) : MediaCardFooterMeta

    /** Remaining time for an in-progress item: render the `core_ui_card_time_left` label in the accent role. */
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

/**
 * The shared renderer for the card footer's trailing meta segment — the
 * optional "•" divider plus the [MediaCardFooterMeta] label, emitted inline
 * into the caller's footer Row (the poster and wide shells used to carry
 * byte-identical `when` copies of this, with the two labels baked as
 * string-concatenated English copy). The card chrome stays at the call
 * sites via parameters:
 *  - [style] — the shell's footer text style (poster: labelMedium on TV /
 *    labelSmall elsewhere; wide: labelSmall),
 *  - [showDivider] — the shell's divider rule (the poster card always draws
 *    the "•" before the label, the wide card only when a leading
 *    subtitle/year text precedes it),
 *  - [dividerColor] — the shell's divider glyph color.
 *
 * The label colors are part of the segment itself: book-progress and
 * time-left speak in the accent role (primary), runtime meta stays plain
 * (onSurfaceVariant) — the same roles the hand-copied shells rendered.
 */
@Composable
internal fun MediaCardFooterMetaRow(
    footerMeta: MediaCardFooterMeta,
    style: TextStyle,
    showDivider: Boolean,
    dividerColor: Color,
) {
    if (showDivider) {
        Text(
            text = "•",
            style = style,
            color = dividerColor,
        )
    }
    when (footerMeta) {
        is MediaCardFooterMeta.BookProgress -> Text(
            text = stringResource(Res.string.core_ui_card_percent_complete, footerMeta.percent),
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
        is MediaCardFooterMeta.TimeLeft -> Text(
            text = stringResource(Res.string.core_ui_card_time_left, footerMeta.label),
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
        is MediaCardFooterMeta.Runtime -> Text(
            text = footerMeta.label,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
