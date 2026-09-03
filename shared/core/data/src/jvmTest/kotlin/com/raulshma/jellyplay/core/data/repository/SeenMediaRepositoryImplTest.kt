package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Exercises [SeenMediaRepositoryImpl] against a real in-memory Room database.
 * The load-bearing invariants of the seen-media (notify-once) table:
 *  - marking seen is idempotent per itemId (unique index, IGNORE);
 *  - [SeenMediaRepositoryImpl.getSeenIds] returns the already-tracked subset,
 *    chunked past SQLite's bound-parameter ceiling;
 *  - [SeenMediaRepositoryImpl.reconcileAgainstLiveItemIds] removes orphans but
 *    treats an EMPTY live set as "no information" (never a mass delete);
 *  - [SeenMediaRepositoryImpl.pruneOlderThan] bounds the table by age.
 */
class SeenMediaRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: SeenMediaRepositoryImpl

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = SeenMediaRepositoryImpl(database.seenMediaDao())
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun `markAsSeen records the item and count reflects it`() = runTest {
        assertEquals(0, repository.count())

        repository.markAsSeen(itemId = "i1", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1_000L)

        assertEquals(1, repository.count())
        assertEquals(setOf("i1"), repository.getSeenIds(listOf("i1")))
    }

    @Test
    fun `markAsSeen is idempotent for the same itemId`() = runTest {
        repository.markAsSeen("i1", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1_000L)
        repository.markAsSeen("i1", libraryId = "lib2", mediaType = "Episode", seenAtEpochMs = 2_000L)

        // The unique itemId index + IGNORE keep one row: a re-notify must not
        // be possible just because the item was seen twice.
        assertEquals(1, repository.count())
    }

    @Test
    fun `bulk markAsSeen records every record and is safe on empty input`() = runTest {
        repository.markAsSeen(
            listOf(
                SeenMediaRecord("i1", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1L),
                SeenMediaRecord("i2", libraryId = "lib", mediaType = "Audio", seenAtEpochMs = 2L),
            )
        )
        repository.markAsSeen(emptyList())

        assertEquals(2, repository.count())
        assertEquals(setOf("i1", "i2"), repository.getSeenIds(listOf("i1", "i2", "i3")))
    }

    @Test
    fun `getSeenIds on empty input returns empty without querying`() = runTest {
        repository.markAsSeen("i1", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1L)

        assertTrue(repository.getSeenIds(emptyList()).isEmpty())
    }

    @Test
    fun `getSeenIds survives more than 900 ids (SQLite param ceiling chunking)`() = runTest {
        val now = 1_000L
        val ids = (1..1900).map { "id-%04d".format(it) }
        repository.markAsSeen(ids.map { SeenMediaRecord(it, libraryId = "lib", mediaType = "Movie", seenAtEpochMs = now) })

        val seen = repository.getSeenIds(ids)

        assertEquals(ids.toSet(), seen)
    }

    @Test
    fun `pruneOlderThan removes only records older than the cutoff`() = runTest {
        repository.markAsSeen(
            listOf(
                SeenMediaRecord("old", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 100L),
                SeenMediaRecord("new", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 10_000L),
            )
        )

        repository.pruneOlderThan(cutoffEpochMs = 1_000L)

        assertEquals(setOf("new"), repository.getSeenIds(listOf("old", "new")))
        assertEquals(1, repository.count())
    }

    @Test
    fun `reconcile removes orphans and returns their count`() = runTest {
        repository.markAsSeen(
            listOf(
                SeenMediaRecord("kept", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1L),
                SeenMediaRecord("gone", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1L),
            )
        )

        val removed = repository.reconcileAgainstLiveItemIds(liveItemIds = setOf("kept", "other"))

        assertEquals(1, removed)
        assertEquals(setOf("kept"), repository.getSeenIds(listOf("kept", "gone")))
    }

    @Test
    fun `reconcile with an empty live set is a no-op`() = runTest {
        // A transient empty scan (folder failed to load) must not wipe the
        // table — "no information" is not "everything deleted".
        repository.markAsSeen("i1", libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1L)

        assertEquals(0, repository.reconcileAgainstLiveItemIds(liveItemIds = emptySet()))
        assertEquals(1, repository.count())
    }

    @Test
    fun `reconcile on an empty table is a no-op`() = runTest {
        assertEquals(0, repository.reconcileAgainstLiveItemIds(liveItemIds = setOf("a", "b")))
        assertEquals(0, repository.count())
    }

    @Test
    fun `reconcile removes more than 900 orphans (chunked deletes)`() = runTest {
        val ids = (1..1900).map { "id-%04d".format(it) }
        repository.markAsSeen(ids.map { SeenMediaRecord(it, libraryId = "lib", mediaType = "Movie", seenAtEpochMs = 1L) })

        val removed = repository.reconcileAgainstLiveItemIds(liveItemIds = setOf("id-0001"))

        assertEquals(1899, removed)
        assertEquals(setOf("id-0001"), repository.getSeenIds(ids))
    }
}
