package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.PlaylistCriterion
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.core.model.SmartPlaylistSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exercises [SmartPlaylistRepository] and [MoodPlaylistRepository] against a
 * real in-memory Room database. The load-bearing logic is the criteria/keyword
 * JSON codec (the only stateful part beyond DAO delegation): a persisted
 * playlist must round-trip its criteria, sort mode and excluded genres, and a
 * corrupted/legacy row must degrade gracefully instead of throwing.
 */
class PlaylistRepositoriesTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var smart: SmartPlaylistRepository
    private lateinit var mood: MoodPlaylistRepository
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        smart = SmartPlaylistRepository(database.smartPlaylistDao(), json)
        mood = MoodPlaylistRepository(database.moodPlaylistDao(), json, FakeTimeSource())
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun pause() = Thread.sleep(3)

    // ── SmartPlaylistRepository ─────────────────────────────────────────

    @Test
    fun `smart playlist round-trips criteria, maxItems and sort`() = runTest {
        val playlist = SmartPlaylist(
            id = "sp1",
            name = "90s Rock",
            criteria = listOf(
                PlaylistCriterion(CriterionType.GENRE, "Rock", CriterionOperator.CONTAINS),
                PlaylistCriterion(CriterionType.YEAR, "1990", CriterionOperator.GREATER_THAN),
            ),
            maxItems = 25,
            sortBy = SmartPlaylistSort.YEAR,
        )

        smart.upsert(playlist)

        assertEquals(playlist, smart.getById("sp1"))
        assertEquals(listOf(playlist), smart.getAll())
        assertEquals(listOf(playlist), smart.observeSmartPlaylists().first())
    }

    @Test
    fun `upsert replaces an existing smart playlist`() = runTest {
        smart.upsert(SmartPlaylist(id = "sp1", name = "Old", criteria = emptyList()))
        smart.upsert(SmartPlaylist(id = "sp1", name = "New", criteria = listOf(PlaylistCriterion(CriterionType.ALBUM, "Abbey Road"))))

        val loaded = smart.getById("sp1")!!
        assertEquals("New", loaded.name)
        assertEquals(1, loaded.criteria.size)
        assertEquals(1, smart.getAll().size)
    }

    @Test
    fun `delete removes only the targeted smart playlist`() = runTest {
        smart.upsert(SmartPlaylist(id = "sp1", name = "A", criteria = emptyList()))
        smart.upsert(SmartPlaylist(id = "sp2", name = "B", criteria = emptyList()))

        smart.delete("sp1")

        assertNull(smart.getById("sp1"))
        assertEquals("sp2", smart.getAll().single().id)
    }

    @Test
    fun `getAll orders by creation recency descending`() = runTest {
        smart.upsert(SmartPlaylist(id = "sp1", name = "First", criteria = emptyList()))
        pause()
        smart.upsert(SmartPlaylist(id = "sp2", name = "Second", criteria = emptyList()))

        assertEquals(listOf("sp2", "sp1"), smart.getAll().map { it.id })
    }

    @Test
    fun `a corrupted criteria row degrades to an empty criteria list`() = runTest {
        database.smartPlaylistDao().insert(
            com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity(
                id = "sp-bad",
                name = "Broken",
                criteriaJson = "{not json",
            )
        )

        val loaded = smart.getById("sp-bad")!!

        assertEquals("Broken", loaded.name)
        assertTrue(loaded.criteria.isEmpty())
    }

    @Test
    fun `an unknown persisted sort value falls back to RANDOM`() = runTest {
        database.smartPlaylistDao().insert(
            com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity(
                id = "sp-sort",
                name = "Legacy",
                criteriaJson = "[]",
                sortBy = "NEWNESS",
            )
        )

        assertEquals(SmartPlaylistSort.RANDOM, smart.getById("sp-sort")!!.sortBy)
    }

    // ── MoodPlaylistRepository ──────────────────────────────────────────

    @Test
    fun `mood playlist round-trips keywords, excluded genres and presentation`() = runTest {
        val playlist = MoodPlaylist(
            id = "focus",
            name = "Deep Focus",
            emoji = "🧘",
            description = "Concentration instrumental",
            genreKeywords = listOf("classical", "ambient"),
            excludedGenres = listOf("metal", "punk"),
            minRating = 3.5f,
            sortBy = MoodPlaylistSort.RATING,
            maxItems = 30,
            themeColorHex = "#9370DB",
        )

        mood.upsert(playlist)

        assertEquals(playlist, mood.getById("focus"))
        assertEquals(listOf(playlist), mood.getAll())
        assertEquals(listOf(playlist), mood.observeMoodPlaylists().first())
    }

    @Test
    fun `an empty excluded-genres list round-trips as empty`() = runTest {
        val playlist = MoodPlaylist(
            id = "happy",
            name = "Happy Vibes",
            emoji = "🌟",
            description = "Upbeat",
            genreKeywords = listOf("pop", "dance"),
        )

        mood.upsert(playlist)

        val loaded = mood.getById("happy")!!
        assertTrue(loaded.excludedGenres.isEmpty())
        assertNull(loaded.minRating)
        assertEquals(MoodPlaylistSort.RANDOM, loaded.sortBy)
        assertEquals(50, loaded.maxItems)
    }

    @Test
    fun `mood playlist delete removes only the targeted row`() = runTest {
        mood.upsert(MoodPlaylist(id = "m1", name = "A", emoji = "a", description = "d", genreKeywords = listOf("pop")))
        mood.upsert(MoodPlaylist(id = "m2", name = "B", emoji = "b", description = "d", genreKeywords = listOf("rock")))

        mood.delete("m1")

        assertNull(mood.getById("m1"))
        assertEquals("m2", mood.getAll().single().id)
    }

    @Test
    fun `an unknown persisted mood sort falls back to RANDOM`() = runTest {
        database.moodPlaylistDao().insert(
            com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity(
                id = "m-sort",
                name = "Legacy",
                emoji = "e",
                description = "d",
                genreKeywordsJson = """["pop"]""",
                sortBy = "SHUFFLE",
            )
        )

        assertEquals(MoodPlaylistSort.RANDOM, mood.getById("m-sort")!!.sortBy)
    }

    // ── Mood playlist preferences ───────────────────────────────────────

    @Test
    fun `setPreference then getPreference round-trips per playlist`() = runTest {
        mood.setPreference(playlistId = "m1", isEnabled = true, isFavorite = true)

        val pref = mood.getPreference("m1")!!
        assertEquals("m1", pref.playlistId)
        assertEquals(true, pref.isEnabled)
        assertEquals(true, pref.isFavorite)
        assertTrue(pref.lastPlayedAt > 0)
    }

    @Test
    fun `setPreference defaults to enabled and not favorite`() = runTest {
        mood.setPreference(playlistId = "m1")

        val pref = mood.getPreference("m1")!!
        assertEquals(true, pref.isEnabled)
        assertEquals(false, pref.isFavorite)
    }

    @Test
    fun `getPreference on a playlist without a preference returns null`() = runTest {
        assertNull(mood.getPreference("never-set"))
    }

    @Test
    fun `upsertPreference replaces the prior preference for the same playlist`() = runTest {
        mood.setPreference("m1", isEnabled = true, isFavorite = false)
        mood.setPreference("m1", isEnabled = false, isFavorite = true)

        val pref = mood.getPreference("m1")!!
        assertEquals(false, pref.isEnabled)
        assertEquals(true, pref.isFavorite)
        assertEquals(1, mood.getAllPreferences().size)
    }

    @Test
    fun `preferences are visible through observe and getAll`() = runTest {
        mood.setPreference("m1", isFavorite = true)
        mood.setPreference("m2", isEnabled = false)

        assertEquals(setOf("m1", "m2"), mood.getAllPreferences().map { it.playlistId }.toSet())
        assertEquals(setOf("m1", "m2"), mood.observePreferences().first().map { it.playlistId }.toSet())
        assertEquals(false, mood.getAllPreferences().first { it.playlistId == "m2" }.isEnabled)
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
