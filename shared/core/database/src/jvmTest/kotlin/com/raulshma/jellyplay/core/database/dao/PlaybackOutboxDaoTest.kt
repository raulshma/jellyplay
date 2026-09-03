package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackOutboxDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var playbackOutboxDao: PlaybackOutboxDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        playbackOutboxDao = database.playbackOutboxDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createEntry(
        id: String,
        itemId: String = "item-1",
        eventType: String = "PROGRESS",
        createdAt: Long = 1_000L,
        deadLetter: Boolean = false,
        positionTicks: Long = 0L,
    ) = PlaybackOutboxEntity(
        id = id,
        itemId = itemId,
        eventType = eventType,
        sessionId = "session-1",
        positionTicks = positionTicks,
        isPaused = false,
        playMethod = "DIRECT_PLAY",
        recordedAt = createdAt,
        createdAt = createdAt,
        deadLetter = deadLetter,
    )

    @Test
    fun `upsert then getById round-trips`() = runTest {
        val entry = createEntry("outbox-1", positionTicks = 65_000L)
        playbackOutboxDao.upsert(entry)

        assertEquals(entry, playbackOutboxDao.getById("outbox-1"))
    }

    @Test
    fun `getById returns null for missing entry`() = runTest {
        assertNull(playbackOutboxDao.getById("missing"))
    }

    @Test
    fun `upsert with REPLACE overwrites an existing id`() = runTest {
        playbackOutboxDao.upsert(createEntry("outbox-1", positionTicks = 1_000L))
        playbackOutboxDao.upsert(createEntry("outbox-1", eventType = "STOP", positionTicks = 9_000L))

        val loaded = playbackOutboxDao.getById("outbox-1")
        assertNotNull(loaded)
        assertEquals("STOP", loaded.eventType)
        assertEquals(9_000L, loaded.positionTicks)
        assertEquals(1, playbackOutboxDao.count())
    }

    @Test
    fun `getForItem returns live rows oldest first and skips dead letters`() = runTest {
        playbackOutboxDao.upsert(createEntry("outbox-3", createdAt = 3_000L))
        playbackOutboxDao.upsert(createEntry("outbox-1", createdAt = 1_000L))
        playbackOutboxDao.upsert(createEntry("outbox-2", createdAt = 2_000L, deadLetter = true))

        val entries = playbackOutboxDao.getForItem("item-1")
        assertEquals(listOf("outbox-1", "outbox-3"), entries.map { it.id })
    }

    @Test
    fun `getForItemByType returns the oldest live row of that type`() = runTest {
        playbackOutboxDao.upsert(createEntry("progress-2", eventType = "PROGRESS", createdAt = 2_000L))
        playbackOutboxDao.upsert(createEntry("progress-1", eventType = "PROGRESS", createdAt = 1_000L))
        playbackOutboxDao.upsert(createEntry("start-1", eventType = "START", createdAt = 500L))

        assertEquals("progress-1", playbackOutboxDao.getForItemByType("item-1", "PROGRESS")?.id)
    }

    @Test
    fun `getForItemByType skips dead-lettered rows`() = runTest {
        playbackOutboxDao.upsert(createEntry("progress-1", eventType = "PROGRESS", createdAt = 1_000L, deadLetter = true))
        playbackOutboxDao.upsert(createEntry("progress-2", eventType = "PROGRESS", createdAt = 2_000L))

        assertEquals("progress-2", playbackOutboxDao.getForItemByType("item-1", "PROGRESS")?.id)
    }

    @Test
    fun `getForItemByType returns null when nothing matches`() = runTest {
        assertNull(playbackOutboxDao.getForItemByType("missing", "PROGRESS"))
    }

    @Test
    fun `getAll count and countFlow exclude dead-lettered rows but keep them queryable by id`() = runTest {
        playbackOutboxDao.upsert(createEntry("outbox-1", createdAt = 1_000L))
        playbackOutboxDao.upsert(createEntry("outbox-2", createdAt = 2_000L))
        playbackOutboxDao.upsert(createEntry("dead-1", createdAt = 3_000L, deadLetter = true))

        assertEquals(listOf("outbox-1", "outbox-2"), playbackOutboxDao.getAll().map { it.id })
        assertEquals(listOf("outbox-1", "outbox-2"), playbackOutboxDao.getAllFlow().first().map { it.id })
        assertEquals(2, playbackOutboxDao.count())
        assertEquals(2, playbackOutboxDao.countFlow().first())
    }

    @Test
    fun `markDeadLetter flags the row for audit without deleting it`() = runTest {
        playbackOutboxDao.upsert(createEntry("outbox-1"))

        playbackOutboxDao.markDeadLetter("outbox-1")

        // Excluded from the drain queries…
        assertTrue(playbackOutboxDao.getAll().isEmpty())
        assertEquals(0, playbackOutboxDao.count())
        // …but retained, flagged, for audit.
        val retained = playbackOutboxDao.getById("outbox-1")
        assertNotNull(retained)
        assertTrue(retained.deadLetter)
    }

    @Test
    fun `markDeadLetter on an unknown id is a no-op`() = runTest {
        playbackOutboxDao.markDeadLetter("missing")
        assertEquals(0, playbackOutboxDao.count())
    }

    @Test
    fun `deleteById removes a single row`() = runTest {
        playbackOutboxDao.upsert(createEntry("outbox-1"))
        playbackOutboxDao.upsert(createEntry("outbox-2"))

        playbackOutboxDao.deleteById("outbox-1")

        assertNull(playbackOutboxDao.getById("outbox-1"))
        assertEquals(1, playbackOutboxDao.count())
    }

    @Test
    fun `deleteForItem removes every row for the item including dead letters`() = runTest {
        playbackOutboxDao.upsert(createEntry("outbox-1", itemId = "item-1"))
        playbackOutboxDao.upsert(createEntry("outbox-2", itemId = "item-1", deadLetter = true))
        playbackOutboxDao.upsert(createEntry("outbox-3", itemId = "item-2"))

        playbackOutboxDao.deleteForItem("item-1")

        assertEquals(listOf("item-2"), playbackOutboxDao.getAll().map { it.itemId })
    }

    @Test
    fun `deleteForItemByType removes only the matching event type`() = runTest {
        playbackOutboxDao.upsert(createEntry("progress-1", eventType = "PROGRESS"))
        playbackOutboxDao.upsert(createEntry("start-1", eventType = "START"))

        playbackOutboxDao.deleteForItemByType("item-1", "PROGRESS")

        assertNull(playbackOutboxDao.getById("progress-1"))
        assertNotNull(playbackOutboxDao.getById("start-1"))
    }

    @Test
    fun `deletePlaybackTelemetryForItem keeps played-state flips`() = runTest {
        playbackOutboxDao.upsert(createEntry("start-1", eventType = "START"))
        playbackOutboxDao.upsert(createEntry("progress-1", eventType = "PROGRESS"))
        playbackOutboxDao.upsert(createEntry("stop-1", eventType = "STOP"))
        playbackOutboxDao.upsert(createEntry("played-1", eventType = "PLAYED"))
        playbackOutboxDao.upsert(createEntry("other-item-start", itemId = "item-2", eventType = "START"))

        playbackOutboxDao.deletePlaybackTelemetryForItem("item-1")

        // Delivered STOP supersedes telemetry, but the user's played intent survives…
        assertNull(playbackOutboxDao.getById("start-1"))
        assertNull(playbackOutboxDao.getById("progress-1"))
        assertNull(playbackOutboxDao.getById("stop-1"))
        assertNotNull(playbackOutboxDao.getById("played-1"))
        assertFalse(playbackOutboxDao.getById("played-1")!!.deadLetter)
        // …and other items are untouched.
        assertNotNull(playbackOutboxDao.getById("other-item-start"))
    }
}
