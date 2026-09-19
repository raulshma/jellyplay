package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [BookFormat.fromPath]: books carry no MediaSources, so the page-based
 * format is resolved from the item `Path` alone — case-insensitively, and
 * after stripping any download-URL query/fragment suffix.
 */
class BookFormatTest {

    @Test
    fun `extensions match case-insensitively`() {
        assertEquals(BookFormat.CBZ, BookFormat.fromPath("/books/My Comic.cbz"))
        assertEquals(BookFormat.CBZ, BookFormat.fromPath("/books/x.CBZ"))
        assertEquals(BookFormat.PDF, BookFormat.fromPath("C:\\books\\manual.PDF"))
        assertEquals(BookFormat.CBR, BookFormat.fromPath("/books/x.CbR"))
        assertEquals(BookFormat.EPUB, BookFormat.fromPath("/books/novel.EPUB"))
    }

    @Test
    fun `query strings are stripped before matching`() {
        assertEquals(
            BookFormat.CBZ,
            BookFormat.fromPath("/books/issue1.cbz?download=true&api_key=k"),
        )
        assertEquals(
            BookFormat.PDF,
            BookFormat.fromPath("/books/manual.pdf?download=true&api_key=k#frag"),
        )
        assertNull(
            BookFormat.fromPath("/Items/b1/Download?api_key=k"),
            "no extension in the path itself — the query never supplies one",
        )
    }

    @Test
    fun `literal hash in a filesystem path is not a url fragment`() {
        // Regression: stripUrlSuffixes treated '#' as a fragment, truncating
        // every raw server path containing "C#" and erasing the extension —
        // the whole book showed as download-only on the device.
        assertEquals(
            BookFormat.EPUB,
            BookFormat.fromPath(
                "/media/eBook/JellyfinBooks/Building CLI Applications with C# and .NET (23)/" +
                    "Building CLI Applications with C# and .NET - Tidjani Belmansour.epub",
            ),
        )
        assertEquals(
            BookFormat.PDF,
            BookFormat.fromPath("/books/C# Primer.pdf"),
        )
    }

    @Test
    fun `null blank and extensionless paths yield null`() {
        assertNull(BookFormat.fromPath(null))
        assertNull(BookFormat.fromPath(""))
        assertNull(BookFormat.fromPath("   "))
        assertNull(BookFormat.fromPath("/books/no-extension"))
        assertNull(BookFormat.fromPath("/mnt/books"))
    }

    @Test
    fun `only epub is reflowable`() {
        assertFalse(BookFormat.CBZ.isReflowable)
        assertFalse(BookFormat.PDF.isReflowable)
        assertFalse(BookFormat.CBR.isReflowable)
        assertTrue(BookFormat.EPUB.isReflowable)
    }

    @Test
    fun `unknown extensions yield null`() {
        assertNull(BookFormat.fromPath("/books/novel.mobi"))
        assertNull(BookFormat.fromPath("/books/novel.azw3"))
        assertNull(BookFormat.fromPath("/books/cover.jpg"))
        assertNull(BookFormat.fromPath("/books/notes.txt"))
    }

    // ── fromDownloadMetadata: the header-side fallback ────────────────────────

    @Test
    fun `download filename wins over content type`() {
        // Measured: Jellyfin 10.9–12 serves Content-Disposition filenames on
        // /Items/{id}/Download; the filename is the server's own file name.
        assertEquals(
            BookFormat.EPUB,
            BookFormat.fromDownloadMetadata(
                contentType = "application/octet-stream",
                fileName = "Flat Book.epub",
            ),
        )
    }

    @Test
    fun `content type maps when filename is absent`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromDownloadMetadata("application/epub+zip", null))
        assertEquals(BookFormat.PDF, BookFormat.fromDownloadMetadata("application/pdf", null))
        assertEquals(BookFormat.CBZ, BookFormat.fromDownloadMetadata("application/vnd.comicbook+zip", null))
        assertEquals(BookFormat.CBR, BookFormat.fromDownloadMetadata("application/vnd.comicbook-rar", null))
        assertEquals(BookFormat.CBR, BookFormat.fromDownloadMetadata("application/x-rar-compressed", null))
    }

    @Test
    fun `content type tolerates charset params and case`() {
        assertEquals(
            BookFormat.EPUB,
            BookFormat.fromDownloadMetadata("application/epub+zip; charset=utf-8", null),
        )
        assertEquals(BookFormat.PDF, BookFormat.fromDownloadMetadata("  Application/PDF ", null))
    }

    @Test
    fun `kindle and absent metadata yield null`() {
        // Measured: .azw3 downloads answer application/vnd.amazon.ebook with
        // an .azw3 filename — a known-but-unsupported format, not an error.
        assertNull(BookFormat.fromDownloadMetadata("application/vnd.amazon.ebook", "Kindle Book.azw3"))
        assertNull(BookFormat.fromDownloadMetadata("application/x-mobipocket-ebook", "x.mobi"))
        assertNull(BookFormat.fromDownloadMetadata(null, null))
        assertNull(BookFormat.fromDownloadMetadata(null, "metadata.opf"))
    }

    @Test
    fun `content disposition filename parsing covers both rfc forms`() {
        // Jellyfin serves both forms; RFC 5987 (filename*) wins.
        assertEquals(
            "Flat Book.epub",
            parseContentDispositionFileName(
                """attachment; filename="Flat Book.epub"; filename*=UTF-8''Flat%20Book.epub""",
            ),
        )
        assertEquals(
            "manual.pdf",
            parseContentDispositionFileName("""attachment; filename="manual.pdf""""),
        )
        assertEquals(
            "Comic Issue #7.cbz",
            parseContentDispositionFileName(
                """attachment; filename*=UTF-8''Comic%20Issue%20%237.cbz""",
            ),
        )
        assertNull(parseContentDispositionFileName("attachment"))
        assertNull(parseContentDispositionFileName(null))
        assertNull(parseContentDispositionFileName("""attachment; filename*="""))
    }
}
