package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.model.BookFormat
import okio.Path

/**
 * An opened, paged book (CBZ archive or PDF), already materialized to a local
 * file. Platform back-ends (android PdfRenderer / desktop PDFBox / the shared
 * ZipFile CBZ pager) implement the raster step; the screen only knows pages.
 */
interface BookDocument {

    /** Total pages; always ≥ 1 — open failures surface as `null` from [BookDocumentOpener.open]. */
    val pageCount: Int

    /**
     * Render [pageIndex] sized to fit [widthPx] (aspect preserved). Returns
     * `null` for a failed page — the UI keeps the placeholder instead of
     * crashing the reader.
     */
    suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap?

    /** The reader moved to [pageIndex] — back-ends with their own page cache re-anchor here. */
    fun onPageChanged(pageIndex: Int) {}

    /** Release the underlying file handles. Idempotent. */
    fun close()
}

/**
 * Opens a local book file as a [BookDocument]; `null` means the file could not
 * be opened (corrupt archive, password PDF, …) and drives the reader's
 * cannot-open error state. Koin-provided per platform
 * (androidBookPlayerModule / desktopBookPlayerModule) — the format dispatch
 * ([BookFormat.CBZ] vs [BookFormat.PDF]) picks the platform back-end.
 */
interface BookDocumentOpener {
    suspend fun open(path: Path, format: BookFormat): BookDocument?
}
