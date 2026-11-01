package com.raulshma.jellyplay.feature.details

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the `detail_section_order` reconcile ([applyDetailSectionOrder]) — the
 * nav-order contract over the section registry: unknown tokens dropped,
 * duplicates collapsed, missing sections appended in canonical order, and the
 * chrome kinds (header / action row / book reading card) pinned ahead of any
 * stored order. An empty token list is the shipped byte-identical emission.
 */
class DetailSectionOrderTest {

    /** A canonical admitted list standing in for a movie's typical admission. */
    private val canonical = listOf(
        DetailSectionKind.HEADER,
        DetailSectionKind.ACTION_ROW,
        DetailSectionKind.MEDIA_INFO,
        DetailSectionKind.OVERVIEW,
        DetailSectionKind.CHAPTERS_OR_TOC,
        DetailSectionKind.CAST,
        DetailSectionKind.MORE_LIKE_THIS,
        DetailSectionKind.TMDB_REVIEWS,
        DetailSectionKind.DOWNLOAD_FOOTER,
    )

    @Test
    fun `empty order tokens keep the canonical emission`() {
        assertEquals(canonical, applyDetailSectionOrder(canonical, emptyList()))
    }

    @Test
    fun `stored permutation reorders the admitted kinds`() {
        val ordered = applyDetailSectionOrder(
            canonical,
            listOf("CAST", "OVERVIEW", "MEDIA_INFO"),
        )
        // The pinned chrome stays ahead; the stored tokens follow in stored
        // order; the rest trail in canonical order.
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.CAST,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.CHAPTERS_OR_TOC,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.TMDB_REVIEWS,
                DetailSectionKind.DOWNLOAD_FOOTER,
            ),
            ordered,
        )
    }

    @Test
    fun `unknown tokens are dropped and duplicates collapse`() {
        val ordered = applyDetailSectionOrder(
            canonical,
            listOf("NOT_A_SECTION", "CAST", "CAST", "CAST", "OVERVIEW"),
        )
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.CAST,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.CHAPTERS_OR_TOC,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.TMDB_REVIEWS,
                DetailSectionKind.DOWNLOAD_FOOTER,
            ),
            ordered,
        )
    }

    @Test
    fun `pinned kinds never move even when the stored order names them`() {
        val ordered = applyDetailSectionOrder(
            canonical,
            listOf("TMDB_REVIEWS", "HEADER", "CAST"),
        )
        // HEADER is named first in the stored order but stays pinned ahead of
        // everything; TMDB_REVIEWS and CAST follow in stored order.
        assertEquals(DetailSectionKind.HEADER, ordered.first())
        assertEquals(DetailSectionKind.ACTION_ROW, ordered[1])
        assertEquals(
            listOf(DetailSectionKind.TMDB_REVIEWS, DetailSectionKind.CAST),
            ordered.drop(2).take(2),
        )
    }

    @Test
    fun `stored tokens naming unadmitted sections are ignored`() {
        // SEASONS/MORE_IN_COLLECTION are not admitted for this movie-shaped
        // list: the tokens name sections the registry did not admit, so they
        // vanish and the admitted rest keep their canonical order.
        val ordered = applyDetailSectionOrder(
            canonical,
            listOf("SEASONS", "MORE_IN_COLLECTION"),
        )
        assertEquals(canonical, ordered)
    }
}
