package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.SeenMediaEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeenMediaDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var seenMediaDao: SeenMediaDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        seenMediaDao = database.seenMediaDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createEntry(
        itemId: String,
        libraryId: String = "lib-1",
        mediaType: String = "MOVIE",
        seenAt: Long = 1_000L,
    ) = SeenMediaEntity(
        id = 0,
        itemId = itemId,
        libraryId = libraryId,
        mediaType = mediaType,
        seenAt = seenAt,
    )

    @Test
    fun `insertAll then getAllSeenItemIds returns every stored id`() = runTest {
        seenMediaDao.insertAll(
            listOf(
                createEntry("item-1", seenAt = 1_000L),
                createEntry("item-2", seenAt = 2_000L),
                createEntry("item-3", seenAt = 3_000L),
            )
        )

        assertEquals(setOf("item-1", "item-2", "item-3"), seenMediaDao.getAllSeenItemIds().toSet())
    }

    @Test
    fun `getSeenIds returns only the ids already marked seen`() = runTest {
        seenMediaDao.insertAll(listOf(createEntry("item-1"), createEntry("item-2")))

        // item-3 was never seen; item-1 and item-2 come back.
        assertEquals(
            setOf("item-1", "item-2"),
            seenMediaDao.getSeenIds(listOf("item-1", "item-2", "item-3")).toSet(),
        )
    }

    @Test
    fun `insertAll with IGNORE drops a duplicate itemId`() = runTest {
        seenMediaDao.insertAll(listOf(createEntry("item-1", seenAt = 1_000L)))

        // Second notification for the same item must not duplicate the row
        // (unique index on itemId) nor refresh the original seenAt.
        seenMediaDao.insertAll(listOf(createEntry("item-1", seenAt = 9_000L)))

        assertEquals(1, seenMediaDao.count())
        // The surviving row still carries the original seenAt: pruning below
        // 9_000 would fail if the duplicate insert had replaced it.
        assertEquals(1, seenMediaDao.pruneOlderThan(9_000L))
        assertTrue(seenMediaDao.getAllSeenItemIds().isEmpty())
    }

    @Test
    fun `pruneOlderThan deletes strictly older rows and returns the count`() = runTest {
        seenMediaDao.insertAll(
            listOf(
                createEntry("item-1", seenAt = 1_000L),
                createEntry("item-2", seenAt = 2_000L),
                createEntry("item-3", seenAt = 3_000L),
            )
        )

        // Boundary is exclusive: a row exactly at the cutoff survives.
        val deleted = seenMediaDao.pruneOlderThan(2_000L)

        assertEquals(1, deleted)
        assertEquals(setOf("item-2", "item-3"), seenMediaDao.getAllSeenItemIds().toSet())
    }

    @Test
    fun `pruneOlderThan with nothing older deletes nothing`() = runTest {
        seenMediaDao.insertAll(listOf(createEntry("item-1", seenAt = 5_000L)))

        assertEquals(0, seenMediaDao.pruneOlderThan(1_000L))
        assertEquals(1, seenMediaDao.count())
    }

    @Test
    fun `deleteByItemIds removes only the given ids and returns the count`() = runTest {
        seenMediaDao.insertAll(
            listOf(
                createEntry("item-1"),
                createEntry("item-2"),
                createEntry("item-3"),
            )
        )

        val deleted = seenMediaDao.deleteByItemIds(listOf("item-1", "item-3", "item-never-seen"))

        assertEquals(2, deleted)
        assertEquals(listOf("item-2"), seenMediaDao.getAllSeenItemIds())
    }

    @Test
    fun `count reflects stored rows`() = runTest {
        assertEquals(0, seenMediaDao.count())

        seenMediaDao.insertAll(listOf(createEntry("item-1"), createEntry("item-2")))

        assertEquals(2, seenMediaDao.count())
    }

    @Test
    fun `re-adding a deleted item id can be re-inserted`() = runTest {
        seenMediaDao.insertAll(listOf(createEntry("item-1", seenAt = 1_000L)))
        seenMediaDao.deleteByItemIds(listOf("item-1"))

        // Reconciliation deletes orphans so a re-add of the same id re-notifies.
        seenMediaDao.insertAll(listOf(createEntry("item-1", seenAt = 2_000L)))

        assertEquals(listOf("item-1"), seenMediaDao.getAllSeenItemIds())
    }

    @Test
    fun `bulk insert keeps distinct item ids even when one id repeats within the batch`() = runTest {
        // IGNORE applies per-row: the first insert of item-1 lands, its repeat
        // inside the same batch is skipped, and item-2 still lands.
        seenMediaDao.insertAll(
            listOf(createEntry("item-1", seenAt = 1_000L), createEntry("item-1", seenAt = 2_000L), createEntry("item-2", seenAt = 3_000L))
        )

        assertEquals(2, seenMediaDao.count())
        assertEquals(setOf("item-1", "item-2"), seenMediaDao.getAllSeenItemIds().toSet())
        assertTrue(seenMediaDao.getSeenIds(listOf("item-1")).isNotEmpty())
    }
}
