package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.model.PlayMethod
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the outbox coalescence against a real in-memory Room database.
 * The PROGRESS-merge and STOP-clear semantics are the load-bearing behaviour
 * that the offline-sync fix depends on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackOutboxRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: PlaybackOutboxRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaybackOutboxRepositoryImpl(database.playbackOutboxDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `enqueueProgress coalesces multiple progress entries for same item into one`() = runTest {
        repository.enqueueProgress("item-1", "s1", 100L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueProgress("item-1", "s1", 500L, true, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueProgress("item-1", "s1", 900L, false, PlayMethod.DIRECT_PLAY, null)

        val pending = repository.drain()

        assertEquals(1, pending.size)
        assertEquals(900L, pending[0].positionTicks)
        assertEquals(false, pending[0].isPaused) // latest entry was not paused
        // Coalesced entry counts as 1 — three updates collapsed to a single row.
        assertEquals(1, repository.count())
    }

    @Test
    fun `enqueueProgress keeps separate entries for different items`() = runTest {
        repository.enqueueProgress("item-1", "s1", 100L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueProgress("item-2", "s2", 200L, false, PlayMethod.DIRECT_PLAY, null)

        val pending = repository.drain()

        assertEquals(2, pending.size)
        assertEquals(2, repository.count())
    }

    @Test
    fun `enqueueStart and enqueueStop never coalesce with progress`() = runTest {
        repository.enqueueStart("item-1", "s1", PlayMethod.DIRECT_PLAY, 0L)
        repository.enqueueProgress("item-1", "s1", 500L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueStop("item-1", "s1", 900L)

        val pending = repository.drain()

        assertEquals(3, pending.size)
        val types = pending.map { it.eventType }.toSet()
        assertTrue(types.contains(PlaybackOutboxEventType.START))
        assertTrue(types.contains(PlaybackOutboxEventType.PROGRESS))
        assertTrue(types.contains(PlaybackOutboxEventType.STOP))
    }

    @Test
    fun `deleteForItem clears all event types for the item`() = runTest {
        repository.enqueueStart("item-1", "s1", PlayMethod.DIRECT_PLAY, 0L)
        repository.enqueueProgress("item-1", "s1", 500L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueStop("item-1", "s1", 900L)
        repository.enqueueProgress("item-2", "s2", 100L, false, PlayMethod.DIRECT_PLAY, null)

        repository.deleteForItem("item-1")

        val pending = repository.drain()
        assertEquals(1, pending.size)
        assertEquals("item-2", pending[0].itemId)
    }

    @Test
    fun `drain returns entries ordered oldest-first`() = runTest {
        repository.enqueueStart("item-1", "s1", PlayMethod.DIRECT_PLAY, 0L)
        repository.enqueueStop("item-2", "s2", 900L)

        val pending = repository.drain()
        assertEquals("item-1", pending[0].itemId)
        assertEquals("item-2", pending[1].itemId)
    }

    @Test
    fun `delete removes a single entry by id`() = runTest {
        repository.enqueueProgress("item-1", "s1", 500L, false, PlayMethod.DIRECT_PLAY, null)
        val entry = repository.drain().first()

        repository.delete(entry.id)

        assertEquals(0, repository.count())
    }
}
