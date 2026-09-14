package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the BOOK reading-progress decode ([bookProgressFraction]) the Continue
 * Reading row renders: exact page fractions for paged books (the page count
 * comes from the local TOC cache), the percent encoding otherwise (EPUB
 * reflowable, or an unknown page count). Books carry no `runTimeTicks`, so
 * the video [progressFraction] math never applies — these tests pin why the
 * separate helper exists.
 */
class BookProgressFractionTest {

    private fun book(positionTicks: Long?) = MediaItem(
        id = "b1",
        name = "Book",
        mediaType = MediaType.BOOK,
        playbackPositionTicks = positionTicks,
    )

    @Test
    fun `no position decodes to null`() {
        assertNull(book(null).bookProgressFraction())
        assertNull(book(0L).bookProgressFraction())
    }

    @Test
    fun `reflowable percent encoding decodes from ticks`() {
        // percent 0.35 → floor(0.35 * 10_000_000) = 3_500_000 ticks.
        assertEquals(0.35f, book(3_500_000L).bookProgressFraction())
    }

    @Test
    fun `paged books decode the page fraction when the page count is known`() {
        // Page index 4 (0-based) of a 200-page book → (4+1)/200.
        val ticks = BookProgressPolicy.pageToTicks(4)
        assertEquals(5f / 200f, book(ticks).bookProgressFraction(pageCount = 200))
    }

    @Test
    fun `paged books without a page count fall back to the percent reading`() {
        val ticks = BookProgressPolicy.pageToTicks(50)
        assertEquals(BookProgressPolicy.ticksToPercent(ticks).toFloat(), book(ticks).bookProgressFraction())
    }

    @Test
    fun `a zero or negative page count is treated as unknown`() {
        val ticks = BookProgressPolicy.percentToTicks(0.5)
        assertEquals(0.5f, book(ticks).bookProgressFraction(pageCount = 0))
        assertEquals(0.5f, book(ticks).bookProgressFraction(pageCount = -3))
    }

    @Test
    fun `fractions clamp into the 0 to 1 range`() {
        assertEquals(1f, book(Long.MAX_VALUE).bookProgressFraction())
        assertEquals(1f, book(BookProgressPolicy.pageToTicks(999)).bookProgressFraction(pageCount = 10))
    }
}
