package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHistoryDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var searchHistoryDao: SearchHistoryDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        searchHistoryDao = database.searchHistoryDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createEntry(
        query: String,
        userId: String = "user-1",
        searchedAt: Long = 1_000L,
    ) = SearchHistoryEntity(query = query, userId = userId, searchedAt = searchedAt)

    @Test
    fun `getRecent orders by searchedAt desc`() = runTest {
        searchHistoryDao.insert(createEntry("batman", searchedAt = 1_000L))
        searchHistoryDao.insert(createEntry("superman", searchedAt = 3_000L))
        searchHistoryDao.insert(createEntry("wonder woman", searchedAt = 2_000L))

        val recent = searchHistoryDao.getRecent("user-1", limit = 10).first()
        assertEquals(listOf("superman", "wonder woman", "batman"), recent.map { it.query })
    }

    @Test
    fun `getRecent is scoped to the user`() = runTest {
        searchHistoryDao.insert(createEntry("mine", userId = "user-1", searchedAt = 1_000L))
        searchHistoryDao.insert(createEntry("theirs", userId = "user-2", searchedAt = 2_000L))

        val user1 = searchHistoryDao.getRecent("user-1", limit = 10).first()
        assertEquals(listOf("mine"), user1.map { it.query })
    }

    @Test
    fun `getRecent applies the requested limit`() = runTest {
        for (i in 0 until 5) {
            searchHistoryDao.insert(createEntry("query-$i", searchedAt = i.toLong()))
        }

        assertEquals(
            listOf("query-4", "query-3", "query-2"),
            searchHistoryDao.getRecent("user-1", limit = 3).first().map { it.query },
        )
    }

    @Test
    fun `getRecent default limit caps at 50 newest`() = runTest {
        for (i in 0 until 52) {
            searchHistoryDao.insert(createEntry("query-$i", searchedAt = i.toLong()))
        }

        val recent = searchHistoryDao.getRecent("user-1").first()
        assertEquals(50, recent.size)
        assertEquals("query-51", recent.first().query)
        assertEquals("query-2", recent.last().query)
    }

    @Test
    fun `re-searching the same query replaces rather than duplicates`() = runTest {
        searchHistoryDao.insert(createEntry("batman", searchedAt = 1_000L))
        searchHistoryDao.insert(createEntry("superman", searchedAt = 2_000L))

        // Same (query, userId) hits the unique index; REPLACE drops the old row
        // and the new searchedAt wins the ordering.
        searchHistoryDao.insert(createEntry("batman", searchedAt = 3_000L))

        val recent = searchHistoryDao.getRecent("user-1", limit = 10).first()
        assertEquals(listOf("batman", "superman"), recent.map { it.query })
        assertEquals(2, searchHistoryDao.getCount("user-1"))
        assertEquals(3_000L, recent[0].searchedAt)
    }

    @Test
    fun `same query for different users does not collide`() = runTest {
        searchHistoryDao.insert(createEntry("batman", userId = "user-1", searchedAt = 1_000L))
        searchHistoryDao.insert(createEntry("batman", userId = "user-2", searchedAt = 2_000L))

        assertEquals(1, searchHistoryDao.getCount("user-1"))
        assertEquals(1, searchHistoryDao.getCount("user-2"))
    }

    @Test
    fun `evictOldest keeps only the newest keepCount rows`() = runTest {
        for (i in 0 until 5) {
            searchHistoryDao.insert(createEntry("query-$i", searchedAt = i.toLong()))
        }

        searchHistoryDao.evictOldest("user-1", keepCount = 3)

        assertEquals(
            listOf("query-4", "query-3", "query-2"),
            searchHistoryDao.getRecent("user-1", limit = 10).first().map { it.query },
        )
        assertEquals(3, searchHistoryDao.getCount("user-1"))
    }

    @Test
    fun `evictOldest is scoped to the user`() = runTest {
        searchHistoryDao.insert(createEntry("user1-old", userId = "user-1", searchedAt = 1_000L))
        searchHistoryDao.insert(createEntry("user1-new", userId = "user-1", searchedAt = 3_000L))
        searchHistoryDao.insert(createEntry("user2-entry", userId = "user-2", searchedAt = 2_000L))

        searchHistoryDao.evictOldest("user-1", keepCount = 1)

        assertEquals(listOf("user1-new"), searchHistoryDao.getRecent("user-1", limit = 10).first().map { it.query })
        assertEquals(1, searchHistoryDao.getCount("user-2"))
    }

    @Test
    fun `insertAndEvict inserts and trims in one step`() = runTest {
        for (i in 0 until 3) {
            searchHistoryDao.insert(createEntry("query-$i", searchedAt = i.toLong()))
        }

        searchHistoryDao.insertAndEvict(createEntry("query-new", searchedAt = 100L), keepCount = 3)

        val recent = searchHistoryDao.getRecent("user-1", limit = 10).first()
        assertEquals(listOf("query-new", "query-2", "query-1"), recent.map { it.query })
        assertEquals(3, searchHistoryDao.getCount("user-1"))
    }

    @Test
    fun `deleteById removes a single row`() = runTest {
        searchHistoryDao.insert(createEntry("keep", searchedAt = 1_000L))
        val removable = createEntry("remove", searchedAt = 2_000L)
        searchHistoryDao.insert(removable)
        val loaded = searchHistoryDao.getRecent("user-1", limit = 10).first().first { it.query == "remove" }

        searchHistoryDao.deleteById(loaded.id)

        assertEquals(
            listOf("keep"),
            searchHistoryDao.getRecent("user-1", limit = 10).first().map { it.query },
        )
    }

    @Test
    fun `clearAll wipes only the target user`() = runTest {
        searchHistoryDao.insert(createEntry("user-1 query", userId = "user-1"))
        searchHistoryDao.insert(createEntry("user-2 query", userId = "user-2"))

        searchHistoryDao.clearAll("user-1")

        assertEquals(0, searchHistoryDao.getCount("user-1"))
        assertEquals(1, searchHistoryDao.getCount("user-2"))
        assertTrue(searchHistoryDao.getRecent("user-1", limit = 10).first().isEmpty())
    }
}
