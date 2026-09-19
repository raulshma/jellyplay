package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [ReaderBookmarkCodec] — the one paged-vs-reflowable position rule the
 * ViewModel's match, encode and jump-decode sites used to hand-write:
 * `cfi == null` marks a paged row (pageToTicks / ticksToPage), anything else
 * a reflowable row (percentToTicks / CFI anchor), with the legacy null-CFI
 * reflowable rows matching by encoded percent but deliberately not jumping.
 */
class ReaderBookmarkCodecTest {

    private fun row(ticks: Long, cfi: String?, chapterLabel: String = "") = ReaderBookmark(
        id = 1L,
        itemId = "item-1",
        positionTicks = ticks,
        cfi = cfi,
        chapterLabel = chapterLabel,
        createdAt = 0L,
    )

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    @Test
    fun `paged encode writes page ticks with a null cfi and empty label`() {
        val draft = ReaderBookmarkCodec.encode(ReaderBookmarkCodec.Location.Paged(page = 4))

        assertEquals(BookProgressPolicy.pageToTicks(4), draft.positionTicks)
        assertEquals(null, draft.cfi)
        assertEquals("", draft.chapterLabel)
    }

    @Test
    fun `reflowable encode writes percent ticks with the cfi and chapter label`() {
        val draft = ReaderBookmarkCodec.encode(
            ReaderBookmarkCodec.Location.Reflowable(
                percent = 0.5,
                cfi = "epubcfi(/6/8!/4/2)",
                chapterLabel = "Chapter 2",
            ),
        )

        assertEquals(BookProgressPolicy.percentToTicks(0.5), draft.positionTicks)
        assertEquals("epubcfi(/6/8!/4/2)", draft.cfi)
        assertEquals("Chapter 2", draft.chapterLabel)
    }

    @Test
    fun `encode round trips through matches at the same position`() {
        val locations = listOf(
            ReaderBookmarkCodec.Location.Paged(page = 11),
            ReaderBookmarkCodec.Location.Reflowable(percent = 0.25, cfi = "epubcfi(/6/4)", chapterLabel = "One"),
        )
        for (location in locations) {
            val draft = ReaderBookmarkCodec.encode(location)
            val encoded = row(draft.positionTicks, draft.cfi, draft.chapterLabel)
            assertTrue(ReaderBookmarkCodec.matches(encoded, location), "encoded row must match its own location")
        }
    }

    // ------------------------------------------------------------------
    // Match
    // ------------------------------------------------------------------

    @Test
    fun `paged match requires a null-cfi row at the same encoded page`() {
        val at = ReaderBookmarkCodec.Location.Paged(page = 2)
        assertTrue(ReaderBookmarkCodec.matches(row(BookProgressPolicy.pageToTicks(2), cfi = null), at))
        assertFalse(ReaderBookmarkCodec.matches(row(BookProgressPolicy.pageToTicks(3), cfi = null), at))
        // A reflowable-encoded row never matches a paged position, even when
        // its percent ticks collide with the page ticks.
        assertFalse(
            ReaderBookmarkCodec.matches(
                row(BookProgressPolicy.pageToTicks(2), cfi = "epubcfi(/6/8)"),
                at,
            ),
        )
    }

    @Test
    fun `reflowable match prefers the cfi anchor`() {
        val at = ReaderBookmarkCodec.Location.Reflowable(percent = 0.5, cfi = "epubcfi(/6/8)", chapterLabel = "Ch")
        assertTrue(
            ReaderBookmarkCodec.matches(
                row(BookProgressPolicy.percentToTicks(0.9), cfi = "epubcfi(/6/8)"),
                at,
            ),
            "page-start CFI anchors are exact — the stored percent may be stale",
        )
        assertFalse(ReaderBookmarkCodec.matches(row(BookProgressPolicy.percentToTicks(0.5), cfi = "epubcfi(/6/9)"), at))
    }

    @Test
    fun `legacy null-cfi reflowable row matches by encoded percent`() {
        val at = ReaderBookmarkCodec.Location.Reflowable(percent = 0.5, cfi = "epubcfi(/6/8)", chapterLabel = "Ch")
        assertTrue(ReaderBookmarkCodec.matches(row(BookProgressPolicy.percentToTicks(0.5), cfi = null), at))
        assertFalse(ReaderBookmarkCodec.matches(row(BookProgressPolicy.percentToTicks(0.4), cfi = null), at))
    }

    // ------------------------------------------------------------------
    // Decode / labels / jumps
    // ------------------------------------------------------------------

    @Test
    fun `decode label renders a one-based page for paged rows and a whole percent otherwise`() {
        assertEquals(
            "p. 12",
            ReaderBookmarkCodec.decodeLabel(row(BookProgressPolicy.pageToTicks(11), cfi = null)),
        )
        assertEquals(
            "34%",
            ReaderBookmarkCodec.decodeLabel(row(BookProgressPolicy.percentToTicks(0.34), cfi = "epubcfi(/6/4)")),
        )
    }

    @Test
    fun `decode clamps stored ticks into the display ranges`() {
        val clampedHigh = ReaderBookmarkCodec.decode(row(Long.MAX_VALUE, cfi = "epubcfi(/6/4)"))
        assertEquals(100, (clampedHigh as ReaderBookmarkCodec.DecodedPosition.Reflowable).percent)
        val clampedZero = ReaderBookmarkCodec.decode(row(0L, cfi = null))
        assertEquals(0, (clampedZero as ReaderBookmarkCodec.DecodedPosition.Paged).page)
    }

    @Test
    fun `is jumpable exactly for cfi rows the host can goToCfi`() {
        assertTrue(ReaderBookmarkCodec.isJumpable(row(0L, cfi = "epubcfi(/6/4)")))
        assertFalse(ReaderBookmarkCodec.isJumpable(row(BookProgressPolicy.pageToTicks(2), cfi = null)))
    }

    @Test
    fun `pager jump page decodes the stored ticks`() {
        assertEquals(7, ReaderBookmarkCodec.pagerJumpPage(row(BookProgressPolicy.pageToTicks(7), cfi = null)))
        // Legacy / zero rows land on page 0 (BookProgressPolicy's contract).
        assertEquals(0, ReaderBookmarkCodec.pagerJumpPage(row(0L, cfi = null)))
    }
}
