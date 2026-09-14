package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.book.BookTocProbe
import com.raulshma.jellyplay.core.data.book.BookTocProber
import com.raulshma.jellyplay.core.model.BookFormat
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

/**
 * The player-book implementation of the detail screen's TOC probe — parses
 * the book file the way the reader would, without opening the reader:
 *
 * - **PDF**: the platform [PdfOutlineParser] (PDFBox outline walk), entries
 *   flattened depth-first with the node tree's nesting preserved.
 * - **EPUB**: [EpubTocParser] over the container (NCX/nav). No page count —
 *   reflowable books are percent-positioned (BookProgressPolicy).
 * - **CBZ**: image-entry count only (comic archives have no TOC story) —
 *   this is what lets the detail screen say "Page N of M" before the book
 *   was ever opened.
 * - **CBR**: null — RAR walking lives in junrar's streaming archive open
 *   (ComicArchiveDocument); the count arrives via the cache the first time
 *   the book is read.
 *
 * Everything runs on [Dispatchers.IO] and folds every failure to null — a
 * probe is a speculative enrichment, never an error surface.
 */
class LocalBookTocProber(
    private val pdfOutlineParser: PdfOutlineParser,
) : BookTocProber {

    override suspend fun probe(filePath: String?, format: BookFormat): BookTocProbe? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!file.isFile) return null
        return withContext(Dispatchers.IO) {
            when (format) {
                BookFormat.PDF -> {
                    val nodes = runCatching {
                        pdfOutlineParser.parse(filePath.toPath())
                    }.getOrDefault(emptyList())
                    BookTocProbe(format = format, pageCount = 0, entries = nodes.flatMap { it.flattenToTocEntries() })
                }

                BookFormat.EPUB -> {
                    val entries = EpubTocParser.parse(file)
                    if (entries.isEmpty()) null else BookTocProbe(format = format, pageCount = 0, entries = entries)
                }

                BookFormat.CBZ -> probeComicPageCount(file, BookFormat.CBZ)
                // junrar's Archive open is a streaming object with its own
                // lifecycle; the page count comes from the cache on first read.
                BookFormat.CBR -> null
            }
        }
    }

    private fun probeComicPageCount(file: File, format: BookFormat): BookTocProbe? = runCatching {
        ZipFile(file).use { zip ->
            val pageCount = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .count { entry ->
                    val name = entry.name.substringAfterLast('/')
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in ComicArchiveDocument.IMAGE_EXTENSIONS
                }
            if (pageCount > 0) BookTocProbe(format = format, pageCount = pageCount, entries = emptyList()) else null
        }
    }.getOrNull()
}
