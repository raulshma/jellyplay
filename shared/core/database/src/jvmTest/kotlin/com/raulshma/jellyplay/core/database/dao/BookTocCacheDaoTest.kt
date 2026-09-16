package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookTocCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Round-trips [BookTocCacheDao] against a real in-memory Room database: the
 * per-item keying (`itemId` is the primary key — one row per book, so a
 * re-upsert replaces rather than appends), the Flow/suspend observe pair, and
 * the targeted/full delete paths. The JSON payload is opaque to the DAO — it
 * must persist and return the bytes verbatim; decode logic is repository-side.
 */
class BookTocCacheDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var dao: BookTocCacheDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = database.bookTocCacheDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun cache(itemId: String, updatedAt: Long = 1_000L) = BookTocCacheEntity(
        itemId = itemId,
        format = "EPUB",
        pageCount = 0,
        entriesJson = """[{"title":"Chapter 1"}]""",
        updatedAt = updatedAt,
    )

    @Test
    fun `upsert stores the row and both reads return it verbatim`() = runTest {
        dao.upsert(cache("book-1"))

        assertEquals(cache("book-1"), dao.observeByItemId("book-1").first())
        assertEquals(cache("book-1"), dao.getByItemId("book-1"))
    }

    @Test
    fun `upsert over the same itemId replaces the cached row`() = runTest {
        dao.upsert(cache("book-1", updatedAt = 1_000L))

        dao.upsert(cache("book-1").copy(format = "PDF", pageCount = 42, entriesJson = "[]", updatedAt = 2_000L))

        val row = dao.observeByItemId("book-1").first()
        assertEquals("PDF", row?.format)
        assertEquals(42, row?.pageCount)
        assertEquals("[]", row?.entriesJson)
        assertEquals(2_000L, row?.updatedAt)
    }

    @Test
    fun `reads are keyed per item and null before the first cache write`() = runTest {
        dao.upsert(cache("book-1"))

        assertEquals(null, dao.observeByItemId("book-2").first())
        assertEquals(null, dao.getByItemId("book-2"))
        assertEquals(cache("book-1"), dao.getByItemId("book-1"))
    }

    @Test
    fun `deleteByItemId removes only the targeted item's cache`() = runTest {
        dao.upsert(cache("book-1"))
        dao.upsert(cache("book-2"))

        dao.deleteByItemId("book-1")

        assertEquals(null, dao.getByItemId("book-1"))
        assertEquals(cache("book-2"), dao.observeByItemId("book-2").first())
    }

    @Test
    fun `clear empties every cached row`() = runTest {
        dao.upsert(cache("book-1"))
        dao.upsert(cache("book-2"))

        dao.clear()

        assertEquals(null, dao.getByItemId("book-1"))
        assertEquals(null, dao.observeByItemId("book-2").first())
    }
}
