package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exercises [SearchHistoryRepositoryImpl] against a real in-memory Room
 * database. The load-bearing invariants:
 *  - a re-searched query is deduped (unique (query, userId) index + REPLACE)
 *    and moves back to the front via its fresh `searchedAt`;
 *  - the history is capped at 50 entries per user (oldest evicted);
 *  - `getRecent` returns entries ordered most-recent-first, per-user isolated.
 *
 * `searchedAt` is wall-clock inside the impl, so saves that must order
 * deterministically are separated with a real `Thread.sleep` (runTest's
 * virtual time does not advance the wall clock).
 */
class SearchHistoryRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: SearchHistoryRepositoryImpl

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = SearchHistoryRepositoryImpl(database.searchHistoryDao(), FakeTimeSource())
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun pause() {
        // Guarantees distinct System.currentTimeMillis() stamps between saves.
        Thread.sleep(3)
    }

    @Test
    fun `saveQuery persists trimmed queries readable via getRecent`() = runTest {
        repository.saveQuery("  batman  ", userId = "u1")

        val recent = repository.getRecent("u1").first()

        assertEquals(listOf("batman"), recent.map { it.query })
        assertTrue(recent.single().id > 0)
    }

    @Test
    fun `saveQuery below 2 chars is a no-op`() = runTest {
        repository.saveQuery("a", userId = "u1")
        repository.saveQuery("   ", userId = "u1")
        repository.saveQuery("ab", userId = "u1")

        val recent = repository.getRecent("u1").first()

        assertEquals(listOf("ab"), recent.map { it.query })
    }

    @Test
    fun `re-searching the same query dedups and moves it to the front`() = runTest {
        repository.saveQuery("first", userId = "u1")
        pause()
        repository.saveQuery("second", userId = "u1")
        pause()
        repository.saveQuery("third", userId = "u1")

        assertEquals(listOf("third", "second", "first"), repository.getRecent("u1").first().map { it.query })

        pause()
        repository.saveQuery("first", userId = "u1")

        val recent = repository.getRecent("u1").first()
        assertEquals(listOf("first", "third", "second"), recent.map { it.query })
        // Dedup: the unique (query, userId) index means 3 rows, not 4 — the
        // re-search REPLACEs the original row rather than appending a copy.
        assertEquals(3, recent.size)
    }

    @Test
    fun `getRecent orders by recency descending`() = runTest {
        val queries = listOf("alpha", "bravo", "charlie", "delta")
        for (query in queries) {
            repository.saveQuery(query, userId = "u1")
            pause()
        }

        val recent = repository.getRecent("u1").first()

        assertEquals(listOf("delta", "charlie", "bravo", "alpha"), recent.map { it.query })
    }

    @Test
    fun `history is capped at 50 entries with the oldest evicted`() = runTest {
        for (i in 1..55) {
            repository.saveQuery("query-%02d".format(i), userId = "u1")
            pause()
        }

        val recent = repository.getRecent("u1", limit = 100).first()

        assertEquals(50, recent.size)
        // The 5 oldest queries (01..05) were evicted; the newest sits up front.
        assertEquals("query-55", recent.first().query)
        assertEquals("query-06", recent.last().query)
    }

    @Test
    fun `history is isolated per user`() = runTest {
        repository.saveQuery("mine", userId = "u1")
        pause()
        repository.saveQuery("theirs", userId = "u2")

        assertEquals(listOf("mine"), repository.getRecent("u1").first().map { it.query })
        assertEquals(listOf("theirs"), repository.getRecent("u2").first().map { it.query })

        repository.clearAll("u1")
        assertTrue(repository.getRecent("u1").first().isEmpty())
        assertEquals(listOf("theirs"), repository.getRecent("u2").first().map { it.query })
    }

    @Test
    fun `getRecent limit trims the list and deleteById removes one row`() = runTest {
        repository.saveQuery("one", userId = "u1")
        pause()
        repository.saveQuery("two", userId = "u1")
        pause()
        repository.saveQuery("three", userId = "u1")

        assertEquals(listOf("three", "two"), repository.getRecent("u1", limit = 2).first().map { it.query })

        val two = repository.getRecent("u1").first().first { it.query == "two" }
        repository.deleteById(two.id)

        assertEquals(listOf("three", "one"), repository.getRecent("u1").first().map { it.query })
    }

    /**
     * Controllable [TimeSource] that advances 1 ms per read, so repeated saves
     * get distinct monotone stamps (the ordering the real `Thread.sleep`
     * pauses used to buy).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = ++nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
