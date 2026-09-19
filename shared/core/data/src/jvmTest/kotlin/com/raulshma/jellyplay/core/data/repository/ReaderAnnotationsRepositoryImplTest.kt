package com.raulshma.jellyplay.core.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [ReaderAnnotationsRepositoryImpl] against a real in-memory Room
 * database (the sibling repository-test pattern — SearchHistory/SeenMedia).
 * The load-bearing invariants:
 *  - bookmarks observe in position order, annotations in creation order
 *    (an edit stamps updatedAt and must NOT reorder the sheet);
 *  - [ReaderAnnotationsRepositoryImpl.updateAnnotation] is a partial update:
 *    NULL keeps the stored value, an empty note clears it;
 *  - the two exports are stable, self-describing documents (Markdown shape
 *    asserted verbatim; JSON parsed back for its version + content).
 */
class ReaderAnnotationsRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: ReaderAnnotationsRepositoryImpl
    private var nowMs = 1_000L

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        // Monotone clock fake: repeated writes get distinct, ordered stamps.
        repository = ReaderAnnotationsRepositoryImpl(
            bookmarkDao = database.bookBookmarkDao(),
            annotationDao = database.bookAnnotationDao(),
            timeSource = EpochMillisSource { ++nowMs },
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun `addBookmark persists and observes in position order`() = runTest {
        repository.addBookmark("book-1", positionTicks = 30_000L, cfi = null, chapterLabel = "Page 4")
        repository.addBookmark("book-1", positionTicks = 10_000L, cfi = null, chapterLabel = "Page 2")

        val bookmarks = repository.observeBookmarks("book-1").first()

        assertEquals(listOf(10_000L, 30_000L), bookmarks.map { it.positionTicks })
        assertEquals("Page 2", bookmarks.first().chapterLabel)
        assertNull(bookmarks.first().cfi)
    }

    @Test
    fun `removeBookmark deletes by id`() = runTest {
        repository.addBookmark("book-1", positionTicks = 10_000L, cfi = null, chapterLabel = "Page 2")
        repository.addBookmark("book-1", positionTicks = 20_000L, cfi = null, chapterLabel = "Page 3")

        val older = repository.observeBookmarks("book-1").first().first()
        repository.removeBookmark(older.id)

        assertEquals(listOf(20_000L), repository.observeBookmarks("book-1").first().map { it.positionTicks })
    }

    @Test
    fun `addAnnotation persists typed style and color and observes in creation order`() = runTest {
        repository.addAnnotation(
            "book-1", cfi = "epubcfi(/6/4!/4/10,/1:20,/1:40)",
            style = ReaderAnnotationStyle.HIGHLIGHT, color = ReaderAnnotationColor.YELLOW,
            anchorText = "first", note = null, chapterLabel = "Chapter 1",
        )
        repository.addAnnotation(
            "book-1", cfi = "epubcfi(/6/4!/4/20,/1:0,/1:10)",
            style = ReaderAnnotationStyle.UNDERLINE, color = ReaderAnnotationColor.BLUE,
            anchorText = "second", note = "a note", chapterLabel = "Chapter 2",
        )

        val annotations = repository.observeAnnotations("book-1").first()

        assertEquals(listOf("first", "second"), annotations.map { it.anchorText })
        assertEquals(ReaderAnnotationStyle.UNDERLINE, annotations[1].style)
        assertEquals(ReaderAnnotationColor.BLUE, annotations[1].color)
        assertEquals("a note", annotations[1].note)
        assertNull(annotations[0].note)
    }

    @Test
    fun `a corrupt stored style or color degrades to the documented default`() = runTest {
        // Hand-edited / older-install row shape: raw DAO write bypasses the
        // repository's typed mapping (its whole point is to be the seam).
        database.bookAnnotationDao().upsert(
            com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity(
                itemId = "book-1",
                cfi = "epubcfi(/6/4!/4/10,/1:20,/1:40)",
                style = "SCRIBBLE",
                color = "PUCE",
                anchorText = "excerpt",
                note = null,
                chapterLabel = "Chapter 1",
                createdAt = 1L,
                updatedAt = 1L,
            )
        )

        val annotation = repository.observeAnnotations("book-1").first().single()

        assertEquals(ReaderAnnotationStyle.HIGHLIGHT, annotation.style)
        assertEquals(ReaderAnnotationColor.YELLOW, annotation.color)
    }

    @Test
    fun `updateAnnotation applies partial edits without reordering and stamps updatedAt`() = runTest {
        repository.addAnnotation(
            "book-1", cfi = "epubcfi(/6/4!/4/10,/1:20,/1:40)",
            style = ReaderAnnotationStyle.HIGHLIGHT, color = ReaderAnnotationColor.YELLOW,
            anchorText = "excerpt", note = null, chapterLabel = "Chapter 1",
        )
        repository.addAnnotation(
            "book-1", cfi = "epubcfi(/6/4!/4/12,/1:0,/1:5)",
            style = ReaderAnnotationStyle.UNDERLINE, color = ReaderAnnotationColor.RED,
            anchorText = "later", note = null, chapterLabel = "Chapter 1",
        )
        val first = repository.observeAnnotations("book-1").first().first()

        // Only the note changes; style/color stay.
        repository.updateAnnotation(first.id, note = "marginally interesting")

        val updated = repository.observeAnnotations("book-1").first().first()
        assertEquals("marginally interesting", updated.note)
        assertEquals(ReaderAnnotationStyle.HIGHLIGHT, updated.style)
        assertEquals(ReaderAnnotationColor.YELLOW, updated.color)
        assertTrue(updated.updatedAt > updated.createdAt)

        // Color + style edits on the same row; the earlier note survives.
        repository.updateAnnotation(first.id, color = ReaderAnnotationColor.GREEN, style = ReaderAnnotationStyle.UNDERLINE)

        val recolored = repository.observeAnnotations("book-1").first().first()
        assertEquals(ReaderAnnotationColor.GREEN, recolored.color)
        assertEquals(ReaderAnnotationStyle.UNDERLINE, recolored.style)
        assertEquals("marginally interesting", recolored.note)
        // Creation order holds across edits.
        assertEquals(listOf("excerpt", "later"), repository.observeAnnotations("book-1").first().map { it.anchorText })
    }

    @Test
    fun `updateAnnotation with an empty note clears it and a missing id is a no-op`() = runTest {
        repository.addAnnotation(
            "book-1", cfi = "epubcfi(/6/4!/4/10,/1:20,/1:40)",
            style = ReaderAnnotationStyle.HIGHLIGHT, color = ReaderAnnotationColor.YELLOW,
            anchorText = "excerpt", note = "to be cleared", chapterLabel = "Chapter 1",
        )
        val annotation = repository.observeAnnotations("book-1").first().single()

        repository.updateAnnotation(annotation.id, note = "")

        assertNull(repository.observeAnnotations("book-1").first().single().note)

        repository.updateAnnotation(999L, note = "ghost") // no row, no throw.
        assertEquals(1, repository.observeAnnotations("book-1").first().size)
    }

    @Test
    fun `deleteAnnotation and deleteAllForItem scope deletes correctly`() = runTest {
        repository.addAnnotation(
            "book-1", cfi = "cfi-a", style = ReaderAnnotationStyle.HIGHLIGHT,
            color = ReaderAnnotationColor.YELLOW, anchorText = "a", note = null, chapterLabel = "c",
        )
        repository.addAnnotation(
            "book-2", cfi = "cfi-b", style = ReaderAnnotationStyle.HIGHLIGHT,
            color = ReaderAnnotationColor.YELLOW, anchorText = "b", note = null, chapterLabel = "c",
        )
        repository.addBookmark("book-2", positionTicks = 1_000L, cfi = null, chapterLabel = "Page 1")

        val book1 = repository.observeAnnotations("book-1").first().single()
        repository.deleteAnnotation(book1.id)
        assertTrue(repository.observeAnnotations("book-1").first().isEmpty())
        assertEquals(1, repository.observeAnnotations("book-2").first().size)

        repository.deleteAllForItem("book-2")
        assertTrue(repository.observeAnnotations("book-2").first().isEmpty())
        assertTrue(repository.observeBookmarks("book-2").first().isEmpty())
    }

    @Test
    fun `exportMarkdown renders bookmarks and highlights`() = runTest {
        val markdown = repository.exportMarkdown(
            title = "Nineteen Eighty-Four",
            bookmarks = listOf(
                ReaderBookmark(1, "book-1", positionTicks = 20_000L, cfi = null, chapterLabel = "Chapter 1", createdAt = 1L),
                ReaderBookmark(2, "book-1", positionTicks = 4_200_000L, cfi = "epubcfi(...)", chapterLabel = "Chapter 2", createdAt = 2L),
            ),
            annotations = listOf(
                ReaderAnnotation(
                    1, "book-1", "epubcfi(...)", ReaderAnnotationStyle.HIGHLIGHT, ReaderAnnotationColor.YELLOW,
                    anchorText = "It was a bright cold day in April,", note = null, chapterLabel = "Chapter 1",
                    createdAt = 1L, updatedAt = 1L,
                ),
                ReaderAnnotation(
                    2, "book-1", "epubcfi(...)", ReaderAnnotationStyle.UNDERLINE, ReaderAnnotationColor.BLUE,
                    anchorText = "War is peace", note = "double plus good", chapterLabel = "Chapter 2",
                    createdAt = 2L, updatedAt = 3L,
                ),
            ),
        )

        assertEquals(
            """
            # Nineteen Eighty-Four

            ## Bookmarks

            - Chapter 1 — Page 3
            - Chapter 2 — 42%

            ## Highlights & Notes

            > It was a bright cold day in April,

            > War is peace

            Note: double plus good
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `exportMarkdown omits empty sections`() = runTest {
        assertEquals(
            "# Only Title\n",
            repository.exportMarkdown(title = "Only Title", bookmarks = emptyList(), annotations = emptyList()),
        )
        assertEquals(
            """
            # B

            ## Bookmarks

            - Chapter 1 — Page 1
            """.trimIndent() + "\n",
            repository.exportMarkdown(
                title = "B",
                bookmarks = listOf(ReaderBookmark(1, "b", 0L, cfi = null, chapterLabel = "Chapter 1", createdAt = 1L)),
                annotations = emptyList(),
            ),
        )
    }

    @Test
    fun `exportJson is self-describing with a version field and full content`() = runTest {
        val json = repository.exportJson(
            itemId = "book-1",
            title = "Nineteen Eighty-Four",
            bookmarks = listOf(
                ReaderBookmark(7, "book-1", positionTicks = 20_000L, cfi = null, chapterLabel = "Chapter 1", createdAt = 5L),
            ),
            annotations = listOf(
                ReaderAnnotation(
                    9, "book-1", "epubcfi(...)", ReaderAnnotationStyle.UNDERLINE, ReaderAnnotationColor.GREEN,
                    anchorText = "excerpt", note = null, chapterLabel = "Chapter 2", createdAt = 5L, updatedAt = 6L,
                ),
            ),
        )

        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(1, root["version"]!!.jsonPrimitive.content.toInt())
        assertEquals("book-1", root["itemId"]!!.jsonPrimitive.content)
        assertEquals("Nineteen Eighty-Four", root["title"]!!.jsonPrimitive.content)
        val bookmark = root["bookmarks"]!!.jsonArray.single().jsonObject
        assertEquals(20000, bookmark["positionTicks"]!!.jsonPrimitive.content.toInt())
        assertEquals("Chapter 1", bookmark["chapterLabel"]!!.jsonPrimitive.content)
        val annotation = root["annotations"]!!.jsonArray.single().jsonObject
        assertEquals("UNDERLINE", annotation["style"]!!.jsonPrimitive.content)
        assertEquals("GREEN", annotation["color"]!!.jsonPrimitive.content)
        assertEquals("excerpt", annotation["anchorText"]!!.jsonPrimitive.content)
        // A note-less annotation serializes an explicit null (self-describing).
        assertEquals("null", annotation["note"]!!.jsonPrimitive.content)
    }
}
