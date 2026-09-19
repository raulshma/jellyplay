package com.raulshma.jellyplay.feature.book

import okio.Path
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode

/**
 * Desktop PDF outline parser (jvmMain — catalog `pdfbox`, Apache-2.0): walks
 * the catalog's [PDDocumentOutline] recursively, resolving each item's
 * destination to a page index through `findDestinationPage` + the page tree's
 * `indexOf`. Unresolvable destinations (named destinations the document never
 * defines, action-only entries) map to a null [PdfOutlineNode.pageIndex] —
 * the row renders but cannot be jumped to.
 *
 * Every failure (corrupt file, locked document, no outline) folds to an empty
 * list: a TOC-less PDF is not a reader error.
 */
actual class PdfOutlineParser {

    actual fun parse(path: Path): List<PdfOutlineNode> = runCatching {
        Loader.loadPDF(path.toFile()).use { document ->
            val outline = document.documentCatalog.documentOutline
                ?: return emptyList()
            outline.walk(document)
        }
    }.getOrDefault(emptyList())

    private fun PDOutlineNode.walk(document: PDDocument): List<PdfOutlineNode> =
        children().asSequence().mapNotNull { item ->
            // A broken subtree must not sink its siblings: per-item failures
            // drop the row (or keep it with a null page when only the
            // destination fails to resolve).
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
