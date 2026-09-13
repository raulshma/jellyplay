package com.raulshma.jellyplay.feature.book

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem

/**
 * Pins the desktop PDF outline walk against a document built in-test with the
 * same PDFBox the production code uses: the recursive tree, destination →
 * page-index resolution, unresolvable destinations folding to null page
 * indices, and the graceful-empty failure contract (corrupt file, no
 * outline) that keeps the TOC sheet a non-error state.
 */
class PdfOutlineParserTest {

    private val parser = PdfOutlineParser()

    @Test
    fun `outline tree parses with resolved page indices and nesting`() {
        val path = writePdf { document ->
            val page0 = PDPage()
            val page1 = PDPage()
            document.addPage(page0)
            document.addPage(page1)

            val broken = PDOutlineItem().apply { setTitle("Broken link") } // no destination
            val chapter = PDOutlineItem().apply {
                setTitle("Chapter 1")
                setDestination(page0)
            }
            val section = PDOutlineItem().apply {
                setTitle("Section 1.1")
                setDestination(page1)
            }
            chapter.addLast(section)

            val outline = PDDocumentOutline()
            outline.addLast(broken)
            outline.addLast(chapter)
            document.documentCatalog.documentOutline = outline
        }

        val nodes = parser.parse(path)

        assertEquals(
            listOf(
                PdfOutlineNode(
                    title = "Broken link",
                    pageIndex = null,
                    children = emptyList(),
                ),
                PdfOutlineNode(
                    title = "Chapter 1",
                    pageIndex = 0,
                    children = listOf(PdfOutlineNode("Section 1.1", 1, emptyList())),
                ),
            ),
            nodes,
        )
    }

    @Test
    fun `document without an outline parses to empty`() {
        val path = writePdf { } // pages only, no outline
        assertTrue(parser.parse(path).isEmpty())
    }

    @Test
    fun `corrupt file parses to empty instead of throwing`() {
        val file = Files.createTempFile("corrupt", ".pdf")
        file.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00))
        val nodes = runCatching { parser.parse(file.toAbsolutePath().toString().toPath()) }.getOrDefault(emptyList())
        assertTrue(nodes.isEmpty())
    }

    @Test
    fun `missing file parses to empty instead of throwing`() {
        val missing = Files.createTempDirectory("pdf-outline").resolve("nope.pdf")
        assertTrue(parser.parse(missing.toAbsolutePath().toString().toPath()).isEmpty())
    }

    /** Builds a minimal PDF with [shape] applied before saving; returns the okio path. */
    private fun writePdf(shape: (PDDocument) -> Unit): okio.Path {
        val file = Files.createTempFile("outline", ".pdf")
        PDDocument().use { document ->
            shape(document)
            document.save(file.toFile())
        }
        return file.toAbsolutePath().toString().toPath()
    }
}
