package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the TOC tick rail's derivation helpers (see [ReaderTocTicks]): the
 * EPUB href→current-entry matcher (reader.js `chapterLabelFor` semantics),
 * the PDF outline flatten + current-tick fold, and the ±2 visible window
 * with its end clamps. Pure functions — no Compose, no dispatcher.
 */
class ReaderTocTicksTest {

    // ---- epubTocTicks / epubCurrentTocIndex ----

    private val items = listOf(
        EpubTocItem(label = "Cover", href = "cover.xhtml"),
        EpubTocItem(label = "Chapter 1", href = "text/ch1.xhtml"),
        EpubTocItem(label = "Chapter 1b", href = "text/ch1.xhtml#section-2"),
        EpubTocItem(label = "Chapter 2", href = "OEBPS/text/ch2.xhtml"),
    )

    @Test
    fun epub_ticks_map_one_to_one() {
        val ticks = epubTocTicks(items)
        assertEquals(items.size, ticks.size)
        assertEquals("Chapter 1", ticks[1].label)
        assertEquals("text/ch1.xhtml", ticks[1].href)
        assertNull(ticks[1].page)
    }

    @Test
    fun epub_current_matches_exact_hash_stripped_href() {
        assertEquals(0, epubCurrentTocIndex(items, "cover.xhtml"))
        // Fragment on either side is ignored.
        assertEquals(1, epubCurrentTocIndex(items, "text/ch1.xhtml#p-7"))
        assertEquals(2, epubCurrentTocIndex(items, "text/ch1.xhtml#section-2"))
    }

    @Test
    fun epub_current_falls_back_to_slash_boundary_suffix_match() {
        // Spine href resolves from a different base than the TOC href (either
        // side may carry the extra path segments).
        assertEquals(3, epubCurrentTocIndex(items, "EPUB/OEBPS/text/ch2.xhtml"))
        val baseless = listOf(EpubTocItem(label = "Chapter 1", href = "ch1.xhtml"))
        assertEquals(0, epubCurrentTocIndex(baseless, "text/ch1.xhtml"))
        assertEquals(0, epubCurrentTocIndex(baseless, "EPUB/text/ch1.xhtml"))
    }

    @Test
    fun epub_suffix_match_never_fires_without_a_slash_boundary() {
        // "ch1.xhtml" is not a boundary-suffix of "chapter1.xhtml".
        val other = listOf(EpubTocItem(label = "A", href = "text/ch1.xhtml"))
        assertNull(epubCurrentTocIndex(other, "text/other-ch1.xhtml"))
        assertNull(epubCurrentTocIndex(other, "text/ch1.xhtmlx"))
    }

    @Test
    fun epub_current_returns_first_match_on_duplicate_destinations() {
        val dupes = listOf(
            EpubTocItem(label = "Part 1", href = "ch.xhtml"),
            EpubTocItem(label = "Part 2", href = "ch.xhtml"),
        )
        assertEquals(0, epubCurrentTocIndex(dupes, "ch.xhtml"))
    }

    @Test
    fun epub_current_is_null_without_a_usable_href() {
        assertNull(epubCurrentTocIndex(items, null))
        assertNull(epubCurrentTocIndex(items, ""))
        assertNull(epubCurrentTocIndex(items, "missing.xhtml"))
        assertNull(epubCurrentTocIndex(emptyList(), "cover.xhtml"))
    }

    // ---- pdfTocTicks / pdfCurrentTickIndex ----

    private val outline = listOf(
        PdfOutlineNode(
            title = "Cover",
            pageIndex = 0,
            children = emptyList(),
        ),
        PdfOutlineNode(
            title = "Part I",
            pageIndex = null, // heading without a destination — dropped
            children = listOf(
                PdfOutlineNode(title = "Ch 1", pageIndex = 2, children = emptyList()),
                PdfOutlineNode(title = "Ch 2", pageIndex = 7, children = emptyList()),
            ),
        ),
        PdfOutlineNode(title = "Part II", pageIndex = 11, children = emptyList()),
    )

    @Test
    fun pdf_ticks_flatten_depth_first_dropping_pageless_nodes() {
        val ticks = pdfTocTicks(outline)
        assertEquals(listOf("Cover", "Ch 1", "Ch 2", "Part II"), ticks.map { it.label })
        assertEquals(listOf(0, 2, 7, 11), ticks.map { it.page })
        assertNull(ticks[0].href)
    }

    @Test
    fun pdf_current_is_the_last_tick_at_or_before_the_page() {
        val ticks = pdfTocTicks(outline)
        assertEquals(0, pdfCurrentTickIndex(ticks, currentPage = 0))
        assertEquals(0, pdfCurrentTickIndex(ticks, currentPage = 1))
        assertEquals(1, pdfCurrentTickIndex(ticks, currentPage = 2))
        assertEquals(2, pdfCurrentTickIndex(ticks, currentPage = 9))
        assertEquals(3, pdfCurrentTickIndex(ticks, currentPage = 999))
    }

    @Test
    fun pdf_current_is_null_before_the_first_outline_target() {
        // An outline that starts past the cover: early pages anchor nothing.
        val late = pdfTocTicks(listOf(PdfOutlineNode(title = "Ch 1", pageIndex = 3, children = emptyList())))
        assertNull(pdfCurrentTickIndex(late, currentPage = 2))
        assertEquals(0, pdfCurrentTickIndex(late, currentPage = 3))
    }

    @Test
    fun pdf_current_handles_empty_and_pageless_ticks() {
        assertNull(pdfCurrentTickIndex(emptyList(), currentPage = 5))
        val pageless = pdfTocTicks(listOf(PdfOutlineNode(title = "X", pageIndex = null, children = emptyList())))
        assertTrue(pageless.isEmpty())
        assertNull(pdfCurrentTickIndex(pageless, currentPage = 5))
    }

    // ---- tocTickWindow ----

    private val windowIndices = List(20) { it }

    @Test
    fun window_shows_two_neighbors_on_each_side() {
        assertEquals(listOf(3, 4, 5, 6, 7), tocTickWindow(windowIndices.size, currentIndex = 5))
    }

    @Test
    fun window_clamps_at_both_ends_instead_of_sliding_past() {
        assertEquals(listOf(0, 1, 2), tocTickWindow(windowIndices.size, currentIndex = 0))
        assertEquals(listOf(0, 1, 2, 3, 4), tocTickWindow(windowIndices.size, currentIndex = 2))
        assertEquals(listOf(17, 18, 19), tocTickWindow(windowIndices.size, currentIndex = 19))
        assertEquals(listOf(15, 16, 17, 18, 19), tocTickWindow(windowIndices.size, currentIndex = 17))
    }

    @Test
    fun window_shows_everything_for_a_small_toc() {
        assertEquals(listOf(0, 1, 2), tocTickWindow(3, currentIndex = 1))
        assertEquals(listOf(0), tocTickWindow(1, currentIndex = 0))
    }

    @Test
    fun window_is_empty_without_a_known_current_entry() {
        assertTrue(tocTickWindow(windowIndices.size, currentIndex = null).isEmpty())
        assertTrue(tocTickWindow(windowIndices.size, currentIndex = -1).isEmpty())
        assertTrue(tocTickWindow(windowIndices.size, currentIndex = 20).isEmpty())
        assertTrue(tocTickWindow(0, currentIndex = 0).isEmpty())
    }
}
