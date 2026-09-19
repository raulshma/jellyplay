package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trips [BookBookmarkDao] against a real in-memory Room database: the
 * observe ordering (positionTicks, then createdAt), per-item isolation, and
 * the by-id / by-item delete paths. Toggle-on-tap logic stays in callers —
 * the DAO contract under test is persist + observe + delete only.
 */
class BookBookmarkDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var dao: BookBookmarkDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = database.bookBookmarkDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun bookmark(
        itemId: String,
        positionTicks: Long,
        cfi: String? = null,
        chapterLabel: String = "Chapter 1",
        createdAt: Long = 1_000L,
    ) = BookBookmarkEntity(
        id = 0,
        itemId = itemId,
        positionTicks = positionTicks,
        cfi = cfi,
        chapterLabel = chapterLabel,
        createdAt = createdAt,
    )

    @Test
    fun `upsert assigns ids and observeByItemId returns rows ordered by position`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 30_000L))
        dao.upsert(bookmark("book-1", positionTicks = 10_000L))
        dao.upsert(bookmark("book-1", positionTicks = 20_000L))

        val rows = dao.observeByItemId("book-1").first()

        assertEquals(listOf(10_000L, 20_000L, 30_000L), rows.map { it.positionTicks })
        // AutoGenerate assigned distinct positive ids (the sheet's delete path
        // depends on them).
        assertEquals(3, rows.map { it.id }.toSet().size)
        assertTrue(rows.all { it.id > 0 })
    }

    @Test
    fun `ties on positionTicks fall back to createdAt order`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 10_000L, createdAt = 3_000L))
        dao.upsert(bookmark("book-1", positionTicks = 10_000L, createdAt = 1_000L))

        val rows = dao.observeByItemId("book-1").first()

        assertEquals(listOf(1_000L, 3_000L), rows.map { it.createdAt })
    }

    @Test
    fun `upsert over the same id replaces the stored position`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 10_000L))
        val inserted = dao.observeByItemId("book-1").first().single()

        dao.upsert(inserted.copy(positionTicks = 50_000L, chapterLabel = "Chapter 5"))

        val updated = dao.observeByItemId("book-1").first().single()
        assertEquals(50_000L, updated.positionTicks)
        assertEquals("Chapter 5", updated.chapterLabel)
        assertEquals(1, dao.countByItemId("book-1"))
    }

    @Test
    fun `observeByItemId is isolated per item`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 10_000L))
        dao.upsert(bookmark("book-2", positionTicks = 20_000L))

        assertEquals(listOf(10_000L), dao.observeByItemId("book-1").first().map { it.positionTicks })
        assertEquals(listOf(20_000L), dao.observeByItemId("book-2").first().map { it.positionTicks })
    }

    @Test
    fun `cfi round-trips for epub bookmarks and stays null for paged ones`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 4_200_000L, cfi = "epubcfi(/6/4!/4/10,/1:20,/1:40)"))
        dao.upsert(bookmark("book-2", positionTicks = 40_000L, cfi = null))

        val epub = dao.observeByItemId("book-1").first().single()
        val paged = dao.observeByItemId("book-2").first().single()
        assertEquals("epubcfi(/6/4!/4/10,/1:20,/1:40)", epub.cfi)
        assertEquals(null, paged.cfi)
    }

    @Test
    fun `deleteById removes only the targeted row`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 10_000L))
        dao.upsert(bookmark("book-1", positionTicks = 20_000L))
        val first = dao.observeByItemId("book-1").first().first()

        dao.deleteById(first.id)

        assertEquals(listOf(20_000L), dao.observeByItemId("book-1").first().map { it.positionTicks })
    }

    @Test
    fun `deleteByItemId clears one item and leaves others untouched`() = runTest {
        dao.upsert(bookmark("book-1", positionTicks = 10_000L))
        dao.upsert(bookmark("book-2", positionTicks = 20_000L))

        dao.deleteByItemId("book-1")

        assertEquals(0, dao.countByItemId("book-1"))
        assertEquals(1, dao.countByItemId("book-2"))
    }
}
