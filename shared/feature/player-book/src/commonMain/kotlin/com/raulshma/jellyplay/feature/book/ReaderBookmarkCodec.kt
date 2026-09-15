package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import kotlin.math.roundToInt

/**
 * The bookmark position rule in one pure place: a row's encoding is chosen by
 * its book kind — `cfi == null` marks a PAGED row whose ticks are
 * [BookProgressPolicy.pageToTicks] of a 0-based page index (paged books have
 * no chapter concept in v1, so their label is empty), anything else is a
 * REFLOWABLE row whose ticks are [BookProgressPolicy.percentToTicks] and
 * whose CFI is the exact page-start anchor. [BookProgressPolicy] owns the
 * ticks arithmetic; this codec owns the paged-vs-reflowable branch the
 * ViewModel used to hand-write at its match, encode and jump-decode sites.
 *
 * The one wrinkle is legacy: pre-Wave-3 reflowable rows stored percent ticks
 * with a NULL CFI, which by this rule read as paged rows. Matching absorbs
 * them (a null-CFI row matches by encoded percent against a reflowable
 * location); jumping cannot — the host exposes no display-by-percent after
 * boot, which is why [isJumpable] is false for them.
 */
internal object ReaderBookmarkCodec {

    /** The current reading position, shaped for [encode] / [matches]. */
    sealed interface Location {

        /** Paged: the pager's 0-based page index. */
        data class Paged(val page: Int) : Location

        /**
         * Reflowable: the relocation percent, the page-start CFI (null until
         * the first relocation that carries one) and the chapter label the
         * row stores.
         */
        data class Reflowable(val percent: Double, val cfi: String?, val chapterLabel: String) : Location
    }

    /** The repository write payload [encode] produces. */
    data class Draft(
        val positionTicks: Long,
        val cfi: String?,
        val chapterLabel: String,
    )

    /** The position a stored row decodes to, per its encoding branch. */
    sealed interface DecodedPosition {

        /** 0-based page index (render 1-based). */
        data class Paged(val page: Int) : DecodedPosition

        /** Whole-number 0..100 percent. */
        data class Reflowable(val percent: Int) : DecodedPosition
    }

    /**
     * Encodes [location] into the repository row fields. Paged rows encode
     * the page ticks with a null CFI and an empty label; reflowable rows
     * encode the relocation percent plus the exact CFI + chapter label
     * (null/blank until the host reported them).
     */
    fun encode(location: Location): Draft = when (location) {
        is Location.Paged -> Draft(
            positionTicks = BookProgressPolicy.pageToTicks(location.page),
            cfi = null,
            chapterLabel = "",
        )
        is Location.Reflowable -> Draft(
            positionTicks = BookProgressPolicy.percentToTicks(location.percent),
            cfi = location.cfi,
            chapterLabel = location.chapterLabel,
        )
    }

    /**
     * Whether [bookmark] sits at [location] — the toggle target and the
     * top-bar icon's filled state. Paged match = same encoded page;
     * reflowable match = same CFI (page-start anchors are stable), or — for
     * null-CFI rows — the same encoded percent. Item identity is the
     * caller's predicate; this is position-only.
     */
    fun matches(bookmark: ReaderBookmark, location: Location): Boolean = when (location) {
        is Location.Paged ->
            bookmark.cfi == null && bookmark.positionTicks == BookProgressPolicy.pageToTicks(location.page)
        is Location.Reflowable -> when {
            bookmark.cfi != null -> bookmark.cfi == location.cfi
            // Null-CFI rows can only match by encoded percent.
            else -> bookmark.positionTicks == BookProgressPolicy.percentToTicks(location.percent)
        }
    }

    /** Structural decode of a stored row's position (see [DecodedPosition]). */
    fun decode(bookmark: ReaderBookmark): DecodedPosition = when (bookmark.cfi) {
        null -> DecodedPosition.Paged(BookProgressPolicy.ticksToPage(bookmark.positionTicks))
        else -> DecodedPosition.Reflowable(
            (BookProgressPolicy.ticksToPercent(bookmark.positionTicks) * 100).roundToInt().coerceIn(0, 100),
        )
    }

    /**
     * The row's position as a plain (non-localized) label — "p. 12" for
     * paged rows, "34%" for reflowable ones. The sheets localize through
     * their string resources and keep their own 1-based display math; this
     * is the canonical branch for tests, exports and accessibility text.
     */
    fun decodeLabel(bookmark: ReaderBookmark): String = when (val decoded = decode(bookmark)) {
        is DecodedPosition.Paged -> "p. ${decoded.page + 1}"
        is DecodedPosition.Reflowable -> "${decoded.percent}%"
    }

    /**
     * Whether the EPUB host can jump straight to the row (`goToCfi`) — true
     * exactly for CFI-carrying rows. Paged rows are pager-jumps
     * ([pagerJumpPage], driven by the ViewModel); null-CFI rows in a
     * reflowable book have deliberately no fallback (the host exposes no
     * display-by-percent after boot), which is the false case that matters.
     */
    fun isJumpable(bookmark: ReaderBookmark): Boolean = bookmark.cfi != null

    /**
     * The pager page a row's stored ticks decode to — the jump target for a
     * paged session ([BookProgressPolicy.ticksToPage] of the stored ticks).
     */
    fun pagerJumpPage(bookmark: ReaderBookmark): Int =
        BookProgressPolicy.ticksToPage(bookmark.positionTicks)
}
