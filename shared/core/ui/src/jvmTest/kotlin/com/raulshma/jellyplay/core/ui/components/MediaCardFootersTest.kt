package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the card-footer book-progress fold — the admission rules (book gate,
 * played suppression, no-position suppression) and the percent pipeline
 * (rounding, clamp, zero suppression, override-wins) — for every card shape
 * at once (poster/wide/offline all consume [bookFooterPercent]).
 */
class MediaCardFootersTest {

    private fun book(positionTicks: Long?, played: Boolean = false) = MediaItem(
        id = "b1",
        name = "Dune",
        mediaType = MediaType.BOOK,
        playbackPositionTicks = positionTicks,
        isPlayed = played,
    )

    @Test
    fun `percent ticks decode to whole percents`() {
        val item = book(BookProgressPolicy.percentToTicks(0.34))
        assertEquals(34, bookFooterPercent(item))
    }

    @Test
    fun `non-book items never show a book footer`() {
        val movie = MediaItem(
            id = "m1",
            name = "Arrival",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 1_000_000L,
        )
        assertNull(bookFooterPercent(movie))
        assertNull(bookFooterPercent(movie, fractionOverride = 0.5f), "even a non-null override cannot admit a non-book")
    }

    @Test
    fun `played books suppress the footer`() {
        assertNull(bookFooterPercent(book(BookProgressPolicy.percentToTicks(0.5), played = true)))
    }

    @Test
    fun `unstarted books show nothing`() {
        assertNull(bookFooterPercent(book(null)))
        assertNull(
            bookFooterPercent(book(BookProgressPolicy.percentToTicks(0.0))),
            "a zero-percent decode is suppressed, not rendered as 0% complete",
        )
    }

    @Test
    fun `a sub-one-percent position rounds to nothing`() {
        val item = book(BookProgressPolicy.percentToTicks(0.001))
        assertNull(bookFooterPercent(item))
    }

    @Test
    fun `the override wins over the item decode`() {
        val item = book(BookProgressPolicy.percentToTicks(0.10))
        assertEquals(75, bookFooterPercent(item, fractionOverride = 0.75f))
    }

    @Test
    fun `percent is clamped into 0-100`() {
        val item = book(1L)
        assertEquals(
            100,
            bookFooterPercent(item, fractionOverride = 1.5f),
            "an out-of-range override clamps instead of rendering >100%",
        )
    }
}
