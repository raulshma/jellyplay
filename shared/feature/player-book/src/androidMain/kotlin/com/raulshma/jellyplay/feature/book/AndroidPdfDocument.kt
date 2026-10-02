package com.raulshma.jellyplay.feature.book

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android PDF pager over the platform [PdfRenderer] (no dependency — the
 * renderer ships with android.jar). The renderer permits only ONE open page
 * at a time, so [renderPage] serializes through [renderLock]; pages raster to
 * ARGB_8888 scaled to fit [widthPx] over a white base (PDFs have no paper
 * color of their own). A password-protected PDF throws from the constructor —
 * [open] maps every open failure to null (the opener's cannot-open contract).
 */
class AndroidPdfDocument private constructor(
    private val renderer: PdfRenderer,
) : BookDocument {

    override val pageCount: Int = renderer.pageCount

    private val renderLock = Any()

    /**
     * Page boxes filled on demand under [renderLock] (static per document);
     * filled slots read without the lock — a stale null just re-fills.
     */
    private val pageSizes = arrayOfNulls<Size>(pageCount)

    /** Page box in PDF points (the raster unit [renderPage]'s scale divides by). */
    override suspend fun pageSize(pageIndex: Int): Size? {
        if (pageIndex !in 0 until pageCount) return null
        pageSizes[pageIndex]?.let { return it }
        return withContext(Dispatchers.IO) {
            pageSizeLocked(pageIndex)
        }
    }

    /**
     * The pure page-box read — non-suspend on purpose (the ratchet keeps bare
     * runCatching out of suspend bodies, the [renderPageLocked] precedent):
     * a read failure maps to null, the same cannot-open contract [open]
     * hands back.
     */
    private fun pageSizeLocked(pageIndex: Int): Size? =
        synchronized(renderLock) {
            runCatching {
                renderer.openPage(pageIndex).use { page ->
                    Size(page.width.toFloat(), page.height.toFloat())
                        .also { pageSizes[pageIndex] = it }
                }
            }.getOrNull()
        }

    override suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (pageIndex !in 0 until pageCount) return@withContext null
            renderPageLocked(pageIndex, widthPx)
        }

    /**
     * The pure raster step — non-suspend on purpose (the ratchet keeps bare
     * runCatching out of suspend bodies): a render failure maps to null, the
     * same cannot-open contract [open] hands back.
     */
    private fun renderPageLocked(pageIndex: Int, widthPx: Int): ImageBitmap? =
        synchronized(renderLock) {
            runCatching {
                renderer.openPage(pageIndex).use { page ->
                    val scale = widthPx.toFloat() / page.width
                    val w = widthPx.coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    val matrix = Matrix().apply { setScale(scale, scale) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }

    override fun close() {
        runCatching { renderer.close() }
    }

    companion object {
        fun open(file: File): AndroidPdfDocument? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                AndroidPdfDocument(PdfRenderer(pfd))
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
        }.getOrNull()
    }
}
