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

    // ── Coalescence semantics ────────────────────────────────────────

    @Test
    fun `enqueueProgress coalesced entry holds latest session id`() = runTest {
        repository.enqueueProgress("item-1", "session-A", 100L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueProgress("item-1", "session-B", 200L, false, PlayMethod.DIRECT_PLAY, null)

        val pending = repository.drain()
        assertEquals(1, pending.size)
        assertEquals("session-B", pending[0].sessionId)
        assertEquals(200L, pending[0].positionTicks)
    }

    @Test
    fun `enqueueProgress coalesced entry holds latest paused state`() = runTest {
        repository.enqueueProgress("item-1", "s1", 100L, isPaused = false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueProgress("item-1", "s1", 100L, isPaused = true, PlayMethod.DIRECT_STREAM, "src")

        val entry = repository.drain().single()
        assertEquals(true, entry.isPaused)
        assertEquals(PlayMethod.DIRECT_STREAM, entry.playMethod)
        assertEquals("src", entry.mediaSourceId)
    }

    @Test
    fun `enqueueProgress after STOP for same item creates a fresh entry`() = runTest {
        // A STOP is terminal but does not block a subsequent PROGRESS (e.g. the
        // user resumes after the stop). Both must survive until drain.
        repository.enqueueStop("item-1", "s1", 500L)
        repository.enqueueProgress("item-1", "s2", 100L, false, PlayMethod.DIRECT_PLAY, null)

        val pending = repository.drain()
        assertEquals(2, pending.size)
    }

    @Test
    fun `multiple STOPs for same item are not coalesced`() = runTest {
        repository.enqueueStop("item-1", "s1", 100L)
        repository.enqueueStop("item-1", "s1", 200L)
        repository.enqueueStop("item-1", "s1", 300L)

        assertEquals(3, repository.drain().size)
    }

    @Test
    fun `mixed events drain preserves enqueue order`() = runTest {
        repository.enqueueStart("item-1", "s1", PlayMethod.DIRECT_PLAY, 0L)
        repository.enqueueProgress("item-1", "s1", 100L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueProgress("item-1", "s1", 200L, false, PlayMethod.DIRECT_PLAY, null) // coalesces
        repository.enqueueStop("item-1", "s1", 300L)

        val pending = repository.drain()
        // START, PROGRESS (coalesced), STOP — order preserved by createdAt.
        assertEquals(3, pending.size)
        assertEquals(PlaybackOutboxEventType.START, pending[0].eventType)
        assertEquals(PlaybackOutboxEventType.PROGRESS, pending[1].eventType)
        assertEquals(200L, pending[1].positionTicks) // latest progress won
        assertEquals(PlaybackOutboxEventType.STOP, pending[2].eventType)
    }

    @Test
    fun `deleteForItem is idempotent when no entries match`() = runTest {
        repository.enqueueProgress("item-1", "s1", 1L, false, PlayMethod.DIRECT_PLAY, null)

        repository.deleteForItem("nonexistent")
        repository.deleteForItem("nonexistent")

        assertEquals(1, repository.count())
    }

    // ── count reflects enqueue and delete ────────────────────────────
    // (countFlow's reactivity is Room/Flow plumbing; verified manually via
    // the home header wiring. The count contract itself is covered here.)

    @Test
    fun `count tracks enqueue and delete`() = runTest {
        assertEquals(0, repository.count())

        repository.enqueueProgress("item-1", "s1", 1L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueStop("item-2", "s2", 2L)
        assertEquals(2, repository.count())

        repository.deleteForItem("item-1")
        assertEquals(1, repository.count())
    }

    // ── Played-state (mark watched / unwatched) channel ──────────────

    @Test
    fun `enqueuePlayedState latest intent wins for same item`() = runTest {
        repository.enqueuePlayedState("item-1", isPlayed = true)
        repository.enqueuePlayedState("item-1", isPlayed = false)
        repository.enqueuePlayedState("item-1", isPlayed = true)

        val pending = repository.drain()

        // One row — the deterministic id caused REPLACE-in-place. The final
        // target state (PLAYED) is what survives.
        assertEquals(1, pending.size)
        assertEquals(PlaybackOutboxEventType.PLAYED, pending[0].eventType)
    }

    @Test
    fun `enqueuePlayedState does not affect progress events for same item`() = runTest {
        // A played-state flip and a playback progress event are orthogonal —
        // both must survive until drain.
        repository.enqueueProgress("item-1", "s1", 500L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueuePlayedState("item-1", isPlayed = true)

        val pending = repository.drain()

        assertEquals(2, pending.size)
        val types = pending.map { it.eventType }.toSet()
        assertTrue(types.contains(PlaybackOutboxEventType.PROGRESS))
        assertTrue(types.contains(PlaybackOutboxEventType.PLAYED))
    }

    @Test
    fun `enqueuePlayedState for distinct items keeps separate rows`() = runTest {
        repository.enqueuePlayedState("item-1", isPlayed = true)
        repository.enqueuePlayedState("item-2", isPlayed = false)

        val pending = repository.drain()

        assertEquals(2, pending.size)
        val byItem = pending.associateBy { it.itemId }
        assertEquals(PlaybackOutboxEventType.PLAYED, byItem["item-1"]?.eventType)
        assertEquals(PlaybackOutboxEventType.UNPLAYED, byItem["item-2"]?.eventType)
    }
}
