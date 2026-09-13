package com.raulshma.jellyplay.feature.book

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import okio.Path

/**
 * Android PDF outline parser over pdfbox-android (catalog `pdfboxAndroid`,
 * Apache-2.0). The API mirrors desktop PDFBox's outline surface verbatim
 * (one `com.tom_roush.pdfbox` package under the hood) — only the document
 * load and the one-time resource init differ:
 *
 * - [PDFBoxResourceLoader.init] must run once before any pdfbox call on
 *   Android (it points the library at the app's assets/resources). Done
 *   lazily under [resourceLoaderReady] from the Koin-injected
 *   [applicationContext] (androidBookPlayerModule precedent), so nothing
 *   touches the context until the first PDF TOC is actually requested.
 *
 * - `PDDocument.load(File)` is the 2.0.x loading entry (desktop uses the
 *   3.x `Loader.loadPDF`).
 *
 * Failure contract identical to desktop: corrupt/locked/outline-less files
 * fold to an empty list, unresolvable destinations to null page indices.
 */
actual class PdfOutlineParser(private val applicationContext: Context) {

    private val resourceLoaderReady: Unit by lazy {
        PDFBoxResourceLoader.init(applicationContext)
    }

    actual fun parse(path: Path): List<PdfOutlineNode> = runCatching {
        resourceLoaderReady
        PDDocument.load(path.toFile()).use { document ->
            val outline = document.documentCatalog.documentOutline
                ?: return emptyList()
            outline.walk(document)
        }
    }.getOrDefault(emptyList())

    private fun PDOutlineNode.walk(document: PDDocument): List<PdfOutlineNode> =
        children().asSequence().mapNotNull { item ->
            // Per-item tolerance: a broken subtree must not sink its siblings.
            runCatching {
                PdfOutlineNode(
                    title = item.title?.trim().orEmpty(),
                    pageIndex = runCatching { document.pages.indexOf(item.findDestinationPage(document)) }
                        .getOrNull()
                        ?.takeIf { it >= 0 },
                    children = if (item.hasChildren()) item.walk(document) else emptyList(),
                )
            }.getOrNull()
        }.toList()
}
