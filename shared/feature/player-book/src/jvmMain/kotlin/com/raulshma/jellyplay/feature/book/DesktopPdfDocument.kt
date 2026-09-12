package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer

/**
 * Desktop PDF pager over PDFBox (jvmMain only — catalog `pdfbox`, Apache-2.0).
 * [Loader.loadPDF] decrypts empty-password documents itself; a
 * password-protected or corrupt file throws from the constructor — [open]
 * maps every open failure to null (the opener's cannot-open contract).
 * Pages raster via [PDFRenderer] scaled to fit [widthPx], then cross the
 * ARGB-int → ImageBitmap bridge.
 * PDFBox documents are single-threaded for rendering — [renderLock]
 * serializes pages like the Android renderer path does.
 */
class DesktopPdfDocument private constructor(
    private val document: PDDocument,
) : BookDocument {

    override val pageCount: Int = document.numberOfPages

    private val renderer = PDFRenderer(document)
    private val renderLock = Any()

    override suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (pageIndex !in 0 until pageCount) return@withContext null
            synchronized(renderLock) {
                runCatching {
                    val pageWidth = document.getPage(pageIndex).mediaBox.width
                    val scale = if (pageWidth > 0f) widthPx / pageWidth else 1f
                    renderer.renderImage(pageIndex, scale).toImageBitmap()
                }.getOrNull()
            }
        }

    override fun close() {
        runCatching { document.close() }
    }

    companion object {
        fun open(file: File): DesktopPdfDocument? = runCatching {
            DesktopPdfDocument(Loader.loadPDF(file))
        }.getOrNull()
    }
}
