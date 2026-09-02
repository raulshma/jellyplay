package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test

class LyricsCacheDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var lyricsCacheDao: LyricsCacheDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        lyricsCacheDao = database.lyricsCacheDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createEntry(
        itemId: String = "item-1",
        provider: String = "LRCLIB",
        syncedLyrics: String? = null,
        plainLyrics: String? = null,
    ) = LyricsCacheEntity(
        itemId = itemId,
        provider = provider,
        artistName = "Artist",
        trackName = "Track",
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        fetchedAt = System.currentTimeMillis(),
    )

    @Test
    fun `insert and getByItemId`() = runTest {
        val entry = createEntry()
        lyricsCacheDao.upsert(entry)

        val result = lyricsCacheDao.getByItemId("item-1")
        assertNotNull(result)
        assertEquals(result!!.provider, "LRCLIB")
    }

    @Test
    fun `getByItemId returns null for non-existent`() = runTest {
        assertNull(lyricsCacheDao.getByItemId("nonexistent"))
    }

    @Test
    fun `getByItemAndProvider finds matching entry`() = runTest {
        lyricsCacheDao.upsert(createEntry(provider = "LRCLIB"))

        val result = lyricsCacheDao.getByItemAndProvider("item-1", "LRCLIB")
        assertNotNull(result)
    }

    @Test
    fun `getByItemAndProvider returns null for different provider`() = runTest {
        lyricsCacheDao.upsert(createEntry(provider = "LRCLIB"))

        assertNull(lyricsCacheDao.getByItemAndProvider("item-1", "EMBEDDED"))
    }

    @Test
    fun `insert replaces on conflict`() = runTest {
        lyricsCacheDao.upsert(createEntry(provider = "LRCLIB"))
        lyricsCacheDao.upsert(createEntry(provider = "EMBEDDED"))

        val result = lyricsCacheDao.getByItemId("item-1")
        assertEquals(result!!.provider, "EMBEDDED")
    }

    @Test
    fun `deleteByItemId removes entry`() = runTest {
        lyricsCacheDao.upsert(createEntry())
        lyricsCacheDao.deleteByItemId("item-1")

        assertNull(lyricsCacheDao.getByItemId("item-1"))
    }

    @Test
    fun `deleteOlderThan removes old entries`() = runTest {
        val oldTimestamp = System.currentTimeMillis() - 100_000L
        lyricsCacheDao.upsert(createEntry(itemId = "old").copy(fetchedAt = oldTimestamp))
        lyricsCacheDao.upsert(createEntry(itemId = "new").copy(fetchedAt = System.currentTimeMillis()))

        val deleted = lyricsCacheDao.deleteOlderThan(System.currentTimeMillis() - 50_000L)
        assertEquals(1, deleted)
        assertNull(lyricsCacheDao.getByItemId("old"))
        assertNotNull(lyricsCacheDao.getByItemId("new"))
    }
}
