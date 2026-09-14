package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NameGuidPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure book-detail helpers the reading-aware body renders from:
 * author extraction (person vs artist-item sources) and the format label /
 * jumpability predicates behind the Contents row.
 */
class BookDetailHelpersTest {

    private fun item(artists: List<NameGuidPair> = emptyList()) = MediaItem(
        id = "b1",
        name = "A Book",
        mediaType = MediaType.BOOK,
        artistItems = artists,
    )

    @Test
    fun `author from a person typed Author wins`() {
        val detail = MediaDetail(
            item = item(),
            people = listOf(
                com.raulshma.jellyplay.core.model.PersonInfo(id = "p1", name = "Narrator", role = "Narrator", type = "Narrator"),
                com.raulshma.jellyplay.core.model.PersonInfo(id = "p2", name = "Jane Writer", role = "Author", type = "Author"),
            ),
        )
        assertEquals("Jane Writer", bookAuthorLabel(detail, item()))
    }

    @Test
    fun `writer role matches case-insensitively`() {
        val detail = MediaDetail(
            item = item(),
            people = listOf(
                com.raulshma.jellyplay.core.model.PersonInfo(id = "p1", name = "J. Author", role = "writer", type = "Author"),
            ),
        )
        assertEquals("J. Author", bookAuthorLabel(detail, item()))
    }

    @Test
    fun `author falls back to artist items`() {
        val withArtist = item(artists = listOf(NameGuidPair("Ada Lovelace", "a1")))
        val detail = MediaDetail(item = withArtist)
        assertEquals("Ada Lovelace", bookAuthorLabel(detail, withArtist))
    }

    @Test
    fun `no author source is null`() {
        val detail = MediaDetail(item = item())
        assertNull(bookAuthorLabel(detail, item()))
    }

    @Test
    fun `format labels - epub pdf comic`() {
        assertEquals("EPUB", bookFormatLabel(BookFormat.EPUB, "Comic"))
        assertEquals("PDF", bookFormatLabel(BookFormat.PDF, "Comic"))
        assertEquals("Comic", bookFormatLabel(BookFormat.CBZ, "Comic"))
        assertEquals("Comic", bookFormatLabel(BookFormat.CBR, "Comic"))
    }

    @Test
    fun `jumpability requires href or page`() {
        assertTrue(BookTocEntry(label = "a", href = "ch1.xhtml").isJumpable)
        assertTrue(BookTocEntry(label = "a", page = 3).isJumpable)
        assertFalse(BookTocEntry(label = "a", href = "").isJumpable)
        assertFalse(BookTocEntry(label = "a").isJumpable)
    }
}
