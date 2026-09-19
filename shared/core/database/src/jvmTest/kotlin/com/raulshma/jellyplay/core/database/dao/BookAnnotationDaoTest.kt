package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Round-trips [BookAnnotationDao] against a real in-memory Room database:
 * creation-order listing (an edited highlight must not jump to the top —
 * `updatedAt` diverges from `createdAt` only after an edit), nullable note
 * handling, per-item isolation, and the by-id / by-item delete paths.
 */
class BookAnnotationDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var dao: BookAnnotationDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = database.bookAnnotationDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun annotation(
        itemId: String,
        cfi: String = "epubcfi(/6/4!/4/10,/1:20,/1:40)",
        style: String = "HIGHLIGHT",
        color: String = "YELLOW",
        anchorText: String = "It was a bright cold day in April.",
        note: String? = null,
        chapterLabel: String = "Chapter 1",
        createdAt: Long = 1_000L,
        updatedAt: Long = createdAt,
    ) = BookAnnotationEntity(
        id = 0,
        itemId = itemId,
        cfi = cfi,
        style = style,
        color = color,
        anchorText = anchorText,
        note = note,
        chapterLabel = chapterLabel,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `upsert assigns ids and observeByItemId orders by creation`() = runTest {
        dao.upsert(annotation("book-1", createdAt = 3_000L))
        dao.upsert(annotation("book-1", createdAt = 1_000L))
        // An edit stamps a newer updatedAt — creation order must still hold.
        dao.upsert(annotation("book-1", createdAt = 2_000L, updatedAt = 9_000L))

        val rows = dao.observeByItemId("book-1").first()

        assertEquals(listOf(1_000L, 2_000L, 3_000L), rows.map { it.createdAt })
        assertEquals(3, rows.map { it.id }.toSet().size)
    }

    @Test
    fun `note round-trips as null for highlight-only marks`() = runTest {
        dao.upsert(annotation("book-1", note = null))
        dao.upsert(annotation("book-2", note = "double plus good"))

        assertNull(dao.observeByItemId("book-1").first().single().note)
        assertEquals("double plus good", dao.observeByItemId("book-2").first().single().note)
    }

    @Test
    fun `getById returns the stored row`() = runTest {
        dao.upsert(annotation("book-1", color = "GREEN"))

        val stored = dao.observeByItemId("book-1").first().single()
        val loaded = dao.getById(stored.id)

        assertEquals(stored, loaded)
        assertEquals(null, dao.getById(999L))
    }

    @Test
    fun `update replaces the stored fields`() = runTest {
        dao.upsert(annotation("book-1", style = "HIGHLIGHT", color = "YELLOW"))
        val stored = dao.observeByItemId("book-1").first().single()

        dao.update(stored.copy(style = "UNDERLINE", color = "BLUE", note = "re-read this"))

        val updated = dao.getById(stored.id)
        assertEquals("UNDERLINE", updated!!.style)
        assertEquals("BLUE", updated.color)
        assertEquals("re-read this", updated.note)
        // Untouched columns survive.
        assertEquals(stored.anchorText, updated.anchorText)
        assertEquals(stored.createdAt, updated.createdAt)
    }

    @Test
    fun `observeByItemId is isolated per item`() = runTest {
        dao.upsert(annotation("book-1"))
        dao.upsert(annotation("book-2"))

        assertEquals(1, dao.countByItemId("book-1"))
        assertEquals(1, dao.countByItemId("book-2"))
    }

    @Test
    fun `deleteById removes only the targeted row`() = runTest {
        dao.upsert(annotation("book-1", createdAt = 1_000L))
        dao.upsert(annotation("book-1", createdAt = 2_000L))

        val first = dao.observeByItemId("book-1").first().first()
        dao.deleteById(first.id)

        assertEquals(listOf(2_000L), dao.observeByItemId("book-1").first().map { it.createdAt })
    }

    @Test
    fun `deleteByItemId clears one item and leaves others untouched`() = runTest {
        dao.upsert(annotation("book-1"))
        dao.upsert(annotation("book-2"))

        dao.deleteByItemId("book-1")

        assertEquals(0, dao.countByItemId("book-1"))
        assertEquals(1, dao.countByItemId("book-2"))
    }
}
