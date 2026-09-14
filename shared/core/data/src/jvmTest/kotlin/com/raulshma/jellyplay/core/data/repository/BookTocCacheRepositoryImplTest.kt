package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.BookTocCacheDao
import com.raulshma.jellyplay.core.database.entity.BookTocCacheEntity
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [BookTocCacheRepositoryImpl] against an in-memory DAO: the JSON round-trip
 * of the entry list, the upsert-replaces semantics the reader relies on, and
 * the corrupt-row degradation (bad JSON / unknown format → null or empty, a
 * Flow that never throws).
 */
class BookTocCacheRepositoryImplTest {

    private val table = MutableStateFlow<Map<String, BookTocCacheEntity>>(emptyMap())
    private val dao = object : BookTocCacheDao {
        override suspend fun upsert(cache: BookTocCacheEntity) {
            table.value = table.value + (cache.itemId to cache)
        }

        override fun observeByItemId(itemId: String): Flow<BookTocCacheEntity?> =
            table.map { it[itemId] }

        override suspend fun getByItemId(itemId: String): BookTocCacheEntity? = table.value[itemId]

        override suspend fun deleteByItemId(itemId: String) {
            table.value = table.value - itemId
        }

        override suspend fun clear() {
            table.value = emptyMap()
        }
    }

    private val repository = BookTocCacheRepositoryImpl(dao, EpochMillisSource { 1_000L })

    @Test
    fun `entries round-trip through the row`() = runTest {
        val entries = listOf(
            BookTocEntry(label = "Chapter One", href = "text/ch1.xhtml", level = 0),
            BookTocEntry(label = "Section", href = "text/ch1.xhtml#s2", level = 1),
            BookTocEntry(label = "Part II", page = 42, level = 0),
        )
        repository.putToc("item1", BookFormat.EPUB, pageCount = 0, entries = entries)

        val cached = repository.getToc("item1")!!
        assertEquals(BookFormat.EPUB, cached.format)
        assertEquals(1_000L, cached.updatedAt)
        assertEquals(entries, cached.entries)
        assertEquals(entries, repository.observeToc("item1").first()?.entries)
    }

    @Test
    fun `upsert replaces the previous row wholesale`() = runTest {
        repository.putToc("item1", BookFormat.CBZ, pageCount = 128, entries = emptyList())
        repository.putToc("item1", BookFormat.CBZ, pageCount = 130, entries = emptyList())

        val cached = repository.getToc("item1")!!
        assertEquals(130, cached.pageCount)
    }

    @Test
    fun `unknown stored format degrades to null`() = runTest {
        dao.upsert(
            BookTocCacheEntity(
                itemId = "item1",
                format = "MOBI",
                pageCount = 0,
                entriesJson = "[]",
                updatedAt = 1L,
            ),
        )
        assertNull(repository.getToc("item1"))
        assertNull(repository.observeToc("item1").first())
    }

    @Test
    fun `corrupt entries json degrades to empty entries`() = runTest {
        dao.upsert(
            BookTocCacheEntity(
                itemId = "item1",
                format = "PDF",
                pageCount = 10,
                entriesJson = "{not json",
                updatedAt = 1L,
            ),
        )
        val cached = repository.getToc("item1")!!
        assertEquals(10, cached.pageCount)
        assertTrue(cached.entries.isEmpty())
    }

    @Test
    fun `delete removes the row`() = runTest {
        repository.putToc("item1", BookFormat.EPUB, pageCount = 0, entries = emptyList())
        repository.deleteToc("item1")
        assertNull(repository.getToc("item1"))
    }
}
