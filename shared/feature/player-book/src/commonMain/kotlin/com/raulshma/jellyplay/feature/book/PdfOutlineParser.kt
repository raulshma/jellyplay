package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.BookTocEntry
import okio.Path

/**
 * One node of a PDF document outline (the bookmark tree readers show as their
 * table of contents). [pageIndex] is the 0-based page the node's destination
 * resolves to, or null when the outline entry points somewhere unresolvable
 * (a named destination the document never defines, an off-document action) —
 * such rows still render (the title is readable) but cannot be jumped to.
 */
data class PdfOutlineNode(
    val title: String,
    val pageIndex: Int?,
    val children: List<PdfOutlineNode>,
)

/**
 * Depth-first flatten of this node's subtree into [BookTocEntry] rows for the
 * detail-screen TOC cache, nesting preserved as [BookTocEntry.level]. Blank
 * titles pad to a single space so the row keeps rendering.
 */
fun PdfOutlineNode.flattenToTocEntries(level: Int = 0): List<BookTocEntry> = buildList {
    add(BookTocEntry(label = title.ifBlank { " " }, href = null, page = pageIndex, level = level))
    children.forEach { addAll(it.flattenToTocEntries(level + 1)) }
}

/**
 * PDF outline extraction seam (the paged TOC story): desktop walks the outline
 * with Apache PDFBox, Android with pdfbox-android (the same API surface —
 * PDFBoxResourceLoader is initialized lazily from the injected application
 * context).
 *
 * `parse` is a blocking file-IO function — callers own the dispatcher hop
 * (the ViewModel parses on Dispatchers.Default). Every failure mode (missing
 * file, corrupt PDF, password-locked document, no outline at all) maps to an
 * empty list, never a throw: a book without a readable outline simply has no
 * TOC, which is not a reader-error state.
 */
expect class PdfOutlineParser {

    /**
     * Reads the outline tree of the PDF at [path]. Constraints:
     * - empty list when the document has no outline, or nothing in it resolves;
     * - never throws — a corrupt or locked PDF is an empty outline, and the
     *   paged reader keeps rendering pages regardless;
     * - titles are trimmed; nodes with blank titles render as untitled rows
     *   rather than being dropped (their children may still be reachable).
     */
    fun parse(path: Path): List<PdfOutlineNode>
}
