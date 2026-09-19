package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.model.BookFormat
import kotlin.math.roundToInt
import okio.Path

/**
 * How a paged page is rasterized against the reader surface. A per-session
 * VIEW toggle (settings sheet state, deliberately NOT persisted): sensible
 * default is width-fill.
 */
enum class ReaderFitMode {
    /** Fill the surface width — the classic reading default (v1 behavior). */
    FIT_WIDTH,

    /** Whole page visible: the smaller of the width-fit and height-fit scales. */
    FIT_PAGE,

    /** Native page pixels — raster-capped at [MAX_PAGE_RASTER_SCALE] × surface width. */
    ORIGINAL,
}

/**
 * Zoom re-raster ceiling: a settled zoom up to 3× re-renders the page at
 * surface width × zoom for sharpness; beyond it the graphicsLayer-scaled
 * bitmap is kept as-is (no re-raster, memory stays bounded).
 */
const val MAX_PAGE_RASTER_SCALE = 3f

/**
 * Fit-mode render-width math (pure — pinned by ReaderFitMathTest): the pixel
 * width [BookDocument.renderPage] should rasterize a page at.
 *
 *  - FIT_WIDTH → the surface width;
 *  - FIT_PAGE → the width the page's aspect needs so BOTH dimensions fit the
 *    surface (min of the per-axis scales);
 *  - ORIGINAL → the page's native pixel width, capped at
 *    [MAX_PAGE_RASTER_SCALE] × surface width for raster safety.
 *
 * Unknown page dimensions (a [BookDocument.pageSize] of null, e.g. comic
 * archives whose images only decode lazily) degrade to FIT_WIDTH —
 * [ComicArchiveDocument] ignores the render width anyway (native decode).
 */
internal fun computeRenderWidth(
    fitMode: ReaderFitMode,
    surfaceW: Int,
    surfaceH: Int,
    pageW: Float,
    pageH: Float,
): Int {
    val sw = surfaceW.coerceAtLeast(1).toFloat()
    val sh = surfaceH.coerceAtLeast(1).toFloat()
    return when (fitMode) {
        ReaderFitMode.FIT_WIDTH -> sw.roundToInt()
        ReaderFitMode.FIT_PAGE ->
            if (pageW <= 0f || pageH <= 0f) {
                sw.roundToInt()
            } else {
                val scale = minOf(sw / pageW, sh / pageH)
                (pageW * scale).roundToInt().coerceAtLeast(1)
            }
        ReaderFitMode.ORIGINAL ->
            if (pageW <= 0f) {
                sw.roundToInt()
            } else {
                minOf(pageW, sw * MAX_PAGE_RASTER_SCALE).roundToInt().coerceAtLeast(1)
            }
    }
}

/**
 * An opened, paged book (CBZ archive or PDF), already materialized to a local
 * file. Platform back-ends (android PdfRenderer / desktop PDFBox / the shared
 * ZipFile CBZ pager) implement the raster step; the screen only knows pages.
 */
interface BookDocument {

    /** Total pages; always ≥ 1 — open failures surface as `null` from [BookDocumentOpener.open]. */
    val pageCount: Int

    /**
     * The page's native size in the back-end's raster units (PDF points /
     * raster pixels), or null when unknowable before decoding (comic
     * archives). Feeds [computeRenderWidth] so the caller picks the raster
     * width BEFORE rendering (the page cache keys on it).
     */
    fun pageSize(pageIndex: Int): Size? = null

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
