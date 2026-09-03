package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioQueueDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var audioQueueDao: AudioQueueDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        audioQueueDao = database.audioQueueDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createItem(
        id: String,
        position: Int,
        name: String = "Track $id",
        createdAt: Long = 1_000L,
    ) = AudioQueueEntity(
        id = id,
        position = position,
        name = name,
        artist = "Artist $id",
        durationMs = 180_000L,
        createdAt = createdAt,
    )

    @Test
    fun `insertAll then getQueue returns rows ordered by position`() = runTest {
        // Insert deliberately out of order to pin the ORDER BY position ASC.
        audioQueueDao.insertAll(
            listOf(
                createItem("t-3", position = 2),
                createItem("t-1", position = 0),
                createItem("t-2", position = 1),
            )
        )

        assertEquals(listOf("t-1", "t-2", "t-3"), audioQueueDao.getQueue().map { it.id })
    }

    @Test
    fun `observeQueue emits the same ordered queue as a Flow`() = runTest {
        audioQueueDao.insertAll(
            listOf(createItem("t-1", position = 0), createItem("t-2", position = 1))
        )

        assertEquals(listOf("t-1", "t-2"), audioQueueDao.observeQueue().first().map { it.id })
    }

    @Test
    fun `insertAll with REPLACE overwrites an existing id instead of duplicating`() = runTest {
        audioQueueDao.insertAll(listOf(createItem("t-1", position = 0)))
        audioQueueDao.insertAll(listOf(createItem("t-1", position = 5)))

        val queue = audioQueueDao.getQueue()
        assertEquals(1, queue.size)
        assertEquals(5, queue[0].position)
    }

    @Test
    fun `replaceQueue clears old rows and stores the new order atomically`() = runTest {
        audioQueueDao.replaceQueue(listOf(createItem("old-1", position = 0), createItem("old-2", position = 1)))

        audioQueueDao.replaceQueue(
            listOf(
                createItem("new-2", position = 1),
                createItem("new-1", position = 0),
            )
        )

        val queue = audioQueueDao.getQueue()
        assertEquals(listOf("new-1", "new-2"), queue.map { it.id })
    }

    @Test
    fun `deleteById removes only the targeted row`() = runTest {
        audioQueueDao.insertAll(
            listOf(createItem("t-1", position = 0), createItem("t-2", position = 1))
        )

        audioQueueDao.deleteById("t-1")

        val queue = audioQueueDao.getQueue()
        assertEquals(listOf("t-2"), queue.map { it.id })
    }

    @Test
    fun `clearQueue empties the table`() = runTest {
        audioQueueDao.insertAll(listOf(createItem("t-1", position = 0), createItem("t-2", position = 1)))

        audioQueueDao.clearQueue()

        assertTrue(audioQueueDao.getQueue().isEmpty())
    }

    @Test
    fun `getState returns null before any state is saved`() = runTest {
        assertNull(audioQueueDao.getState())
        assertNull(audioQueueDao.observeState().first())
    }

    @Test
    fun `saveState persists the playback position and flags`() = runTest {
        val state = AudioQueueStateEntity(
            id = 1,
            currentIndex = 2,
            currentPositionMs = 42_000L,
            isPlaying = true,
            repeatMode = 1,
            shuffleEnabled = true,
            playbackSpeed = 1.5f,
            updatedAt = 5_000L,
        )

        audioQueueDao.saveState(state)

        val loaded = audioQueueDao.getState()
        assertNotNull(loaded)
        assertEquals(2, loaded.currentIndex)
        assertEquals(42_000L, loaded.currentPositionMs)
        assertTrue(loaded.isPlaying)
        assertEquals(1, loaded.repeatMode)
        assertTrue(loaded.shuffleEnabled)
        assertEquals(1.5f, loaded.playbackSpeed)
    }

    @Test
    fun `saveState replaces the singleton row instead of duplicating`() = runTest {
        audioQueueDao.saveState(AudioQueueStateEntity(id = 1, currentIndex = 0, currentPositionMs = 1_000L))
        audioQueueDao.saveState(AudioQueueStateEntity(id = 1, currentIndex = 3, currentPositionMs = 9_000L))

        val loaded = audioQueueDao.getState()
        assertNotNull(loaded)
        assertEquals(3, loaded.currentIndex)
        assertEquals(9_000L, loaded.currentPositionMs)
    }
}
