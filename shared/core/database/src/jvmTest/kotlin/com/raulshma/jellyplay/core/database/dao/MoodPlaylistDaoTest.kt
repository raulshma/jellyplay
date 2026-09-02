package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoodPlaylistDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var moodPlaylistDao: MoodPlaylistDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        moodPlaylistDao = database.moodPlaylistDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createPlaylist(
        id: String,
        createdAt: Long,
        name: String = "Mood $id",
        genreKeywordsJson: String = """["chill","lofi"]""",
        excludedGenresJson: String? = """["horror"]""",
    ) = MoodPlaylistEntity(
        id = id,
        name = name,
        emoji = "🌙",
        description = "Description for $id",
        genreKeywordsJson = genreKeywordsJson,
        excludedGenresJson = excludedGenresJson,
        minRating = 7.5f,
        sortBy = "RATING",
        maxItems = 25,
        themeColorHex = "#1E88E5",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `insert then getById returns the playlist`() = runTest {
        val playlist = createPlaylist("mood-1", createdAt = 1_000L)
        moodPlaylistDao.insert(playlist)

        val loaded = moodPlaylistDao.getById("mood-1")
        assertNotNull(loaded)
        assertEquals(playlist, loaded)
    }

    @Test
    fun `getById returns null for missing playlist`() = runTest {
        assertNull(moodPlaylistDao.getById("missing"))
    }

    @Test
    fun `getAll and observeAll order by createdAt desc`() = runTest {
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L))
        moodPlaylistDao.insert(createPlaylist("mood-3", createdAt = 3_000L))
        moodPlaylistDao.insert(createPlaylist("mood-2", createdAt = 2_000L))

        assertEquals(listOf("mood-3", "mood-2", "mood-1"), moodPlaylistDao.getAll().map { it.id })
        assertEquals(
            listOf("mood-3", "mood-2", "mood-1"),
            moodPlaylistDao.observeAll().first().map { it.id },
        )
    }

    @Test
    fun `insert with REPLACE overwrites an existing id`() = runTest {
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L, name = "Old name"))
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L, name = "New name"))

        val loaded = moodPlaylistDao.getById("mood-1")
        assertNotNull(loaded)
        assertEquals("New name", loaded.name)
        assertEquals(1, moodPlaylistDao.getAll().size)
    }

    @Test
    fun `update changes fields of an existing row`() = runTest {
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L))

        moodPlaylistDao.update(
            createPlaylist("mood-1", createdAt = 1_000L, name = "Renamed", excludedGenresJson = null)
        )

        val loaded = moodPlaylistDao.getById("mood-1")
        assertNotNull(loaded)
        assertEquals("Renamed", loaded.name)
        assertNull(loaded.excludedGenresJson)
    }

    @Test
    fun `criteria columns round-trip unchanged`() = runTest {
        val keywords = """["rock","indie","alt, with comma"]"""
        val excluded = """["pop"]"""
        moodPlaylistDao.insert(
            createPlaylist("mood-1", createdAt = 1_000L, genreKeywordsJson = keywords, excludedGenresJson = excluded)
        )

        val loaded = moodPlaylistDao.getById("mood-1")!!
        assertEquals(keywords, loaded.genreKeywordsJson)
        assertEquals(excluded, loaded.excludedGenresJson)
    }

    @Test
    fun `deleteById removes only that playlist`() = runTest {
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L))
        moodPlaylistDao.insert(createPlaylist("mood-2", createdAt = 2_000L))

        moodPlaylistDao.deleteById("mood-1")

        assertNull(moodPlaylistDao.getById("mood-1"))
        assertNotNull(moodPlaylistDao.getById("mood-2"))
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L))
        moodPlaylistDao.insert(createPlaylist("mood-2", createdAt = 2_000L))

        moodPlaylistDao.deleteAll()

        assertTrue(moodPlaylistDao.getAll().isEmpty())
    }

    @Test
    fun `upsertPreference then getPreference round-trips`() = runTest {
        val preference = MoodPlaylistPreferenceEntity(
            playlistId = "mood-1",
            isEnabled = true,
            isFavorite = true,
            lastPlayedAt = 5_000L,
            updatedAt = 5_000L,
        )
        moodPlaylistDao.upsertPreference(preference)

        assertEquals(preference, moodPlaylistDao.getPreference("mood-1"))
    }

    @Test
    fun `getPreference returns null for missing playlist`() = runTest {
        assertNull(moodPlaylistDao.getPreference("missing"))
    }

    @Test
    fun `upsertPreference with REPLACE flips fields without duplicating`() = runTest {
        moodPlaylistDao.upsertPreference(MoodPlaylistPreferenceEntity(playlistId = "mood-1", isFavorite = true))
        moodPlaylistDao.upsertPreference(MoodPlaylistPreferenceEntity(playlistId = "mood-1", isFavorite = false))

        val loaded = moodPlaylistDao.getPreference("mood-1")
        assertNotNull(loaded)
        assertTrue(!loaded.isFavorite)
        assertEquals(1, moodPlaylistDao.getAllPreferences().size)
        assertEquals(1, moodPlaylistDao.observePreferences().first().size)
    }

    @Test
    fun `deleteAll clears playlists but leaves preference rows`() = runTest {
        moodPlaylistDao.insert(createPlaylist("mood-1", createdAt = 1_000L))
        moodPlaylistDao.upsertPreference(MoodPlaylistPreferenceEntity(playlistId = "mood-1"))

        moodPlaylistDao.deleteAll()

        assertNull(moodPlaylistDao.getById("mood-1"))
        // Preferences live in their own table and outlive the playlist rows.
        assertNotNull(moodPlaylistDao.getPreference("mood-1"))
    }
}
