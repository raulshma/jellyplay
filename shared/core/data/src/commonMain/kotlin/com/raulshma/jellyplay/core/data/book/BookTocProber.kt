package com.raulshma.jellyplay.core.data.book

import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry

/**
 * The parsed table-of-contents of a book file. [pageCount] is the paged
 * book's page count (0 = reflowable/unknown); [entries] the TOC (empty for
 * CBZ/CBR — comic archives have no TOC story — and for EPUBs without one).
 */
data class BookTocProbe(
    val format: BookFormat,
    val pageCount: Int,
    val entries: List<BookTocEntry>,
)

/**
 * Parses a book's TOC / page count directly from a local file — the
 * fallback that fills the detail screen's "Contents" section for a book the
 * reader has never opened (no `book_toc_cache` row yet). The implementation
 * lives in the player-book feature, which owns the file parsers (PDF
 * outline, EPUB container/NCX/nav, comic archive sniffing); this interface
 * is the details feature's only compile-time dependency on that capability
 * (Koin resolves the binding; platforms without one inject null and the
 * probe step is skipped). The path is a plain String — the downloader's
 * on-disk location — so neither side needs a shared file-handle type.
 *
 * Deliberately read-only: parsing never writes the cache — the caller
 * decides whether a probe result is worth persisting.
 */
interface BookTocProber {

    /**
     * Parses [filePath] (absolute local path, null when the caller knows of
     * no local copy). Returns null when the file is missing, corrupt, or the
     * format has no parse story — the caller hides the section instead of
     * surfacing an error for a purely speculative read.
     */
    suspend fun probe(filePath: String?, format: BookFormat): BookTocProbe?
}
