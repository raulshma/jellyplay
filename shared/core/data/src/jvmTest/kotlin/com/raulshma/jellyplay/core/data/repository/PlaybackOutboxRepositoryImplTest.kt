package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.model.PlayMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exercises the outbox coalescence against a real in-memory Room database
 * (JVM driver — de-Robolectric port, same scenarios as the legacy suite).
 * The PROGRESS-merge and STOP-clear semantics are the load-bearing behaviour
 * that the offline-sync fix depends on.
 */
class PlaybackOutboxRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: PlaybackOutboxRepositoryImpl

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = PlaybackOutboxRepositoryImpl(database.playbackOutboxDao(), FakeTimeSource())
    }

    @AfterTest
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
    fun `enqueueStop deletes superseded PROGRESS for the same item`() = runTest {
        // A STOP carries the item's final position, so any pending PROGRESS for
        // the same item is superseded and must be cleared — otherwise a
        // mid-position PROGRESS could drain after the STOP and (if the STOP
        // dead-letters while the PROGRESS succeeds) leave the server at a stale
        // mid position. START is retained (START→STOP is a meaningful session
        // lifecycle, ordered by createdAt).
        repository.enqueueStart("item-1", "s1", PlayMethod.DIRECT_PLAY, 0L)
        repository.enqueueProgress("item-1", "s1", 500L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueStop("item-1", "s1", 900L)

        val pending = repository.drain()

        assertEquals(2, pending.size)
        val types = pending.map { it.eventType }.toSet()
        assertTrue(types.contains(PlaybackOutboxEventType.START))
        assertTrue(types.contains(PlaybackOutboxEventType.STOP))
        assertFalse(types.contains(PlaybackOutboxEventType.PROGRESS))
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
    fun `deletePlaybackTelemetryForItem clears START PROGRESS STOP but preserves played-state flips`() = runTest {
        // Reproduces the regression fixed in reportPlaybackStopped: a delivered
        // online STOP used to wipe the whole item, silently dropping a pending
        // PLAYED/UNPLAYED flip. The scoped delete must leave played-state rows.
        repository.enqueueStart("item-1", "s1", PlayMethod.DIRECT_PLAY, 0L)
        repository.enqueueProgress("item-1", "s1", 500L, false, PlayMethod.DIRECT_PLAY, null)
        repository.enqueueStop("item-1", "s1", 900L)
        repository.enqueuePlayedState("item-1", isPlayed = true)
        repository.enqueueProgress("item-2", "s2", 100L, false, PlayMethod.DIRECT_PLAY, null)

        repository.deletePlaybackTelemetryForItem("item-1")

        val pending = repository.drain()
        // item-1's PLAYED flip survives; item-2's PROGRESS is untouched.
        assertEquals(2, pending.size)
        val byItem = pending.associateBy { it.itemId }
        assertEquals(PlaybackOutboxEventType.PLAYED, byItem["item-1"]?.eventType)
        assertEquals(PlaybackOutboxEventType.PROGRESS, byItem["item-2"]?.eventType)
    }

    @Test
    fun `deletePlaybackTelemetryForItem is a no-op when only played-state rows exist`() = runTest {
        repository.enqueuePlayedState("item-1", isPlayed = false)

        repository.deletePlaybackTelemetryForItem("item-1")

        val pending = repository.drain()
        assertEquals(1, pending.size)
        assertEquals(PlaybackOutboxEventType.UNPLAYED, pending[0].eventType)
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
        // START, STOP — the STOP supersedes (deletes) the coalesced PROGRESS,
        // so it does not survive to the drain. Order preserved by createdAt.
        assertEquals(2, pending.size)
        assertEquals(PlaybackOutboxEventType.START, pending[0].eventType)
        assertEquals(PlaybackOutboxEventType.STOP, pending[1].eventType)
        assertEquals(300L, pending[1].positionTicks) // the STOP's final position
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

    // ── Dead-letter (exhausted retry budget) ──────────────────────────

    @Test
    fun `enqueueProgress after a dead-lettered PROGRESS creates a fresh live entry`() = runTest {
        // Regression: getForItem previously omitted the deadLetter = 0 filter, so
        // the coalesce path picked the dead row, .copy() preserved deadLetter=true,
        // and the new live PROGRESS inherited the dead flag — silently dropped by
        // drain and never synced. The fresh report must land as a live row.
        repository.enqueueProgress("item-1", "s1", 100L, false, PlayMethod.DIRECT_PLAY, null)
        val dead = repository.drain().single()
        repository.markDeadLetter(dead.id)
        // count()/drain() drop the dead-lettered row — no live telemetry left.
        assertEquals(0, repository.count())
        assertTrue(repository.drain().isEmpty())

        // A subsequent PROGRESS for the same item must not adopt the dead row's
        // flag — it must be drainable as a live entry.
        repository.enqueueProgress("item-1", "s2", 500L, false, PlayMethod.DIRECT_PLAY, null)

        val pending = repository.drain()
        assertEquals(1, pending.size)
        assertEquals(500L, pending[0].positionTicks)
        assertEquals("s2", pending[0].sessionId)
    }

    /**
     * Controllable [TimeSource] — same shape as the fake in
     * LyricsRepositoryImplTest (core:data deliberately hosts no shared test
     * fakes; see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
