package com.raulshma.jellyplay.feature.book

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

class ComicArchiveDocumentTest {

    /** Write a fixture CBZ: entries in deliberately non-sorted, non-natural order. */
    private fun buildArchive(entries: List<Pair<String, ByteArray>>): File {
        val file = File.createTempFile("comic-test", ".cbz")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun naturalCompareOrdersDigitRunsNumerically() {
        assertTrue(ComicArchiveDocument.naturalCompare("page2.jpg", "page10.jpg") < 0)
        assertTrue(ComicArchiveDocument.naturalCompare("page10.jpg", "page2.jpg") > 0)
        assertEquals(0, ComicArchiveDocument.naturalCompare("same.jpg", "same.jpg"))
        // Leading-zero variants compare numerically equal — order falls to length.
        assertEquals(0, ComicArchiveDocument.naturalCompare("001", "1"))
        // Non-digit prefixes still compare char-wise.
        assertTrue(ComicArchiveDocument.naturalCompare("a.jpg", "b.jpg") < 0)
        // Mixed digit/non-digit: the digit path only fires when BOTH sides are digits.
        assertTrue(ComicArchiveDocument.naturalCompare("1x", "x1") < 0)
        // Shorter string is the prefix-order tiebreak.
        assertTrue(ComicArchiveDocument.naturalCompare("page", "page1") < 0)
    }

    @Test
    fun openFiltersNonImagesAndSortsNaturally() {
        val file = buildArchive(
            listOf(
                "10.jpg" to byteArrayOf(1),
                "cover.txt" to byteArrayOf(2),
                "2.jpg" to byteArrayOf(3),
                "META-INF/container.xml" to byteArrayOf(4),
                "cover.png" to byteArrayOf(5),
                "001.jpg" to byteArrayOf(6),
                "artwork.WEBP" to byteArrayOf(7),
            ),
        )
        val doc = ComicArchiveDocument.open(file.absolutePath.toPath())
        assertNotNull(doc)
        try {
            assertEquals(5, doc.pageCount)
            assertEquals(
                listOf("001.jpg", "2.jpg", "10.jpg", "artwork.WEBP", "cover.png"),
                doc.pageEntryNames,
            )
        } finally {
            doc.close()
        }
    }

    @Test
    fun openReturnsNullWhenArchiveHoldsNoImages() {
        val file = buildArchive(
            listOf(
                "notes.txt" to byteArrayOf(1),
                "META-INF/container.xml" to byteArrayOf(2),
            ),
        )
        assertNull(ComicArchiveDocument.open(file.absolutePath.toPath()))
    }

    @Test
    fun openReturnsNullForMissingFile() {
        assertNull(ComicArchiveDocument.open(File("definitely-not-here-${System.nanoTime()}.cbz").absolutePath.toPath()))
    }

    // The junrar (true RAR) positive path has no test here — it needs a real
    // .rar fixture, which we don't ship. junrar calls are isolated behind
    // RarEntryReader; the pure filter/sort core is pinned in
    // pageNamesFiltersAndSortsRarEntries below.

    @Test
    fun openReturnsNullForGarbageCbrWithoutCrashing() {
        // Neither zip nor rar — the sniff falls through both and must yield null.
        val garbage = File.createTempFile("comic-test", ".cbr")
        garbage.deleteOnExit()
        garbage.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        assertNull(ComicArchiveDocument.open(garbage.absolutePath.toPath()))
    }

    @Test
    fun openSniffsZipInsideCbrExtension() {
        // Some .cbr files are actually zip — content beats extension trust.
        val file = buildArchive(listOf("p1.jpg" to byteArrayOf(1), "p2.jpg" to byteArrayOf(2)))
        val cbr = File(file.parentFile, file.nameWithoutExtension + ".cbr")
        assertTrue(file.renameTo(cbr))
        cbr.deleteOnExit()
        val doc = ComicArchiveDocument.open(cbr.absolutePath.toPath())
        assertNotNull(doc)
        try {
            assertEquals(listOf("p1.jpg", "p2.jpg"), doc.pageEntryNames)
        } finally {
            doc.close()
        }
    }

    @Test
    fun pageNamesFiltersAndSortsRarEntries() {
        assertEquals(
            listOf("001.jpg", "2.jpg", "10.jpg", "cover.png", "sub/9.jpg"),
            ComicArchiveDocument.pageNames(
                listOf("10.jpg", "notes.txt", "2.jpg", "cover.png", "001.jpg", "sub/9.jpg"),
                ComicArchiveDocument.IMAGE_EXTENSIONS,
            ),
        )
    }

    @Test
    fun renderPageOutOfRangeIsNull() {
        val file = buildArchive(listOf("p1.jpg" to byteArrayOf(1)))
        val doc = ComicArchiveDocument.open(file.absolutePath.toPath())
        assertNotNull(doc)
        try {
            // The out-of-range guard fires before any decode, so no real
            // image bytes are needed — just a suspend run loop.
            kotlinx.coroutines.test.runTest {
                assertNull(doc.renderPage(-1, 100))
                assertNull(doc.renderPage(5, 100))
            }
        } finally {
            doc.close()
        }
    }
}
