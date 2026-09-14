package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.BookFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The detail screen's TOC probe: EPUB NCX/nav extraction from the container,
 * comic page counts, and the fail-empty contract (missing/corrupt file →
 * null, never a throw).
 */
class LocalBookTocProberTest {

    private val prober = LocalBookTocProber(PdfOutlineParser())

    // ── EPUB (EPUB 2 NCX) ──

    @Test
    fun `epub ncx toc resolves against the opf dir`() = runTest {
        val epub = epubZip(
            container = CONTAINER_XML,
            opf = OPF_NCX,
            entries = mapOf(
                "OEBPS/content.opf" to OPF_NCX,
                "OEBPS/toc.ncx" to NCX,
                "OEBPS/text/ch1.xhtml" to "<html/>",
                "OEBPS/text/ch2.xhtml" to "<html/>",
            ),
        )
        val probe = prober.probe(epub.absolutePath, BookFormat.EPUB)!!
        assertEquals(BookFormat.EPUB, probe.format)
        assertEquals(0, probe.pageCount)
        assertEquals(
            listOf(
                "Chapter One" to "OEBPS/text/ch1.xhtml",
                "Chapter Two" to "OEBPS/text/ch2.xhtml",
            ),
            probe.entries.map { it.label to it.href },
        )
        assertTrue(probe.entries.all { it.level == 0 && it.page == null })
    }

    @Test
    fun `epub 3 nav document wins over ncx`() = runTest {
        val epub = epubZip(
            container = CONTAINER_XML,
            opf = OPF_NAV,
            entries = mapOf(
                "OEBPS/content.opf" to OPF_NAV,
                "OEBPS/nav.xhtml" to NAV_XHTML,
                "OEBPS/toc.ncx" to NCX,
            ),
        )
        val probe = prober.probe(epub.absolutePath, BookFormat.EPUB)!!
        // The nav lists "Part One" with a nested chapter — nesting lands as level.
        assertEquals("Part One", probe.entries[0].label)
        assertEquals("OEBPS/part1.xhtml", probe.entries[0].href)
        assertEquals(0, probe.entries[0].level)
        assertEquals("Chapter One", probe.entries[1].label)
        assertEquals(1, probe.entries[1].level)
    }

    @Test
    fun `epub without ncx or nav has no toc`() = runTest {
        val epub = epubZip(
            container = CONTAINER_XML,
            opf = OPF_BARE,
            entries = mapOf("OEBPS/content.opf" to OPF_BARE),
        )
        assertNull(prober.probe(epub.absolutePath, BookFormat.EPUB))
    }

    // ── CBZ ──

    @Test
    fun `cbz page count counts image entries only`() = runTest {
        val cbz = zip(
            "pages.cbz",
            listOf(
                "cover.jpg" to ByteArray(1),
                "page10.png" to ByteArray(1),
                "page2.png" to ByteArray(1),
                "meta.xml" to ByteArray(1),
            ),
        )
        val probe = prober.probe(cbz.absolutePath, BookFormat.CBZ)!!
        assertEquals(3, probe.pageCount)
        assertTrue(probe.entries.isEmpty())
    }

    // ── fail-empty contract ──

    @Test
    fun `missing and blank paths are null`() = runTest {
        assertNull(prober.probe(null, BookFormat.EPUB))
        assertNull(prober.probe("", BookFormat.PDF))
        assertNull(prober.probe("/definitely/not/here.epub", BookFormat.EPUB))
    }

    @Test
    fun `corrupt epub file is null`() = runTest {
        val corrupt = File.createTempFile("corrupt", ".epub").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(prober.probe(corrupt.absolutePath, BookFormat.EPUB))
    }

    // ── fixtures ──

    private fun epubZip(container: String, opf: String, entries: Map<String, String>): File {
        val all = entries.toMutableMap()
        all["META-INF/container.xml"] = container
        return zip("sample.epub", all.map { (k, v) -> k to v.toByteArray() })
    }

    private fun zip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val file = File.createTempFile("probe", name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (path, bytes) ->
                out.putNextEntry(ZipEntry(path))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private companion object {
        val CONTAINER_XML = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()

        val OPF_NCX = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="ch1"/><itemref idref="ch2"/></spine>
            </package>
        """.trimIndent()

        val OPF_NAV = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
              </manifest>
              <spine><itemref idref="nav"/></spine>
            </package>
        """.trimIndent()

        val OPF_BARE = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest><item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="ch1"/></spine>
            </package>
        """.trimIndent()

        val NCX = """
            <?xml version="1.0"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="n1" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="text/ch1.xhtml"/></navPoint>
                <navPoint id="n2" playOrder="2"><navLabel><text>Chapter Two</text></navLabel><content src="text/ch2.xhtml"/></navPoint>
              </navMap>
            </ncx>
        """.trimIndent()

        val NAV_XHTML = """
            <?xml version="1.0"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="part1.xhtml">Part One</a>
                      <ol><li><a href="text/ch1.xhtml">Chapter One</a></li></ol>
                    </li>
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
    }
}
