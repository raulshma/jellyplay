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
}
