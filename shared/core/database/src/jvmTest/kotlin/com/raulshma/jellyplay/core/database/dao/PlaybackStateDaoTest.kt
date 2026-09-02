package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackStateDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var playbackStateDao: PlaybackStateDao
    private lateinit var offlineMediaDao: OfflineMediaDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        playbackStateDao = database.playbackStateDao()
        offlineMediaDao = database.offlineMediaDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private suspend fun seedOfflineMedia(
        id: String,
        mediaType: String = "EPISODE",
        parentId: String? = null,
        seasonId: String? = null,
        seriesId: String? = null,
    ) {
        offlineMediaDao.upsert(
            OfflineMediaEntity(
                id = id,
                name = "Media $id",
                mediaType = mediaType,
                parentId = parentId,
                seasonId = seasonId,
                seriesId = seriesId,
            )
        )
    }

    @Test
    fun `upsert then getById round-trips the snapshot`() = runTest {
        val state = PlaybackStateEntity(
            id = "item-1",
            playbackPositionTicks = 600_000_000L,
            playedPercentage = 42.5,
            isPlayed = false,
            isFavorite = true,
            lastPlayedDate = "2026-01-01T00:00:00Z",
        )
        playbackStateDao.upsert(state)

        assertEquals(state, playbackStateDao.getById("item-1"))
    }

    @Test
    fun `getById returns null for missing item`() = runTest {
        assertNull(playbackStateDao.getById("missing"))
    }

    @Test
    fun `updatePlaybackProgress creates a row on first contact`() = runTest {
        playbackStateDao.updatePlaybackProgress("item-1", 120_000L, 30.0, false, "2026-01-01T00:00:00Z")

        val loaded = playbackStateDao.getById("item-1")
        assertNotNull(loaded)
        assertEquals(120_000L, loaded.playbackPositionTicks)
        assertEquals(30.0, loaded.playedPercentage)
        assertFalse(loaded.isPlayed)
        // Columns the progress path does not own resolve to defaults.
        assertFalse(loaded.isFavorite)
        assertEquals("2026-01-01T00:00:00Z", loaded.lastPlayedDate)
    }

    @Test
    fun `updatePlaybackProgress updates resume position without clobbering favorite`() = runTest {
        playbackStateDao.upsert(
            PlaybackStateEntity(id = "item-1", playbackPositionTicks = 100L, playedPercentage = 5.0, isFavorite = true)
        )

        playbackStateDao.updatePlaybackProgress("item-1", 500L, 50.0, false, "2026-02-02T00:00:00Z")

        val loaded = playbackStateDao.getById("item-1")!!
        assertEquals(500L, loaded.playbackPositionTicks)
        assertEquals(50.0, loaded.playedPercentage)
        assertFalse(loaded.isPlayed)
        assertEquals("2026-02-02T00:00:00Z", loaded.lastPlayedDate)
        // The favorite flip is preserved — a progress tick never clobbers it.
        assertTrue(loaded.isFavorite)
    }

    @Test
    fun `applyPlayedStateToHierarchy marks the whole series tree played`() = runTest {
        seedOfflineMedia("series-1", mediaType = "SERIES")
        seedOfflineMedia("season-1", mediaType = "SEASON", parentId = "series-1", seriesId = "series-1")
        seedOfflineMedia("ep-1", parentId = "season-1", seasonId = "season-1", seriesId = "series-1")
        seedOfflineMedia("ep-2", parentId = "season-1", seasonId = "season-1", seriesId = "series-1")
        seedOfflineMedia("other-series-ep", seasonId = "season-9", seriesId = "series-9")
        // ep-1 already has a progress row that the cascade must overwrite.
        playbackStateDao.upsert(
            PlaybackStateEntity(id = "ep-1", playbackPositionTicks = 999L, playedPercentage = 42.0)
        )

        playbackStateDao.applyPlayedStateToHierarchy("series-1", isPlayed = true, lastPlayedDate = "2026-03-03T00:00:00Z")

        for (id in listOf("series-1", "season-1", "ep-1", "ep-2")) {
            val state = playbackStateDao.getById(id)
            assertNotNull(state, "expected playback row for $id")
            assertTrue(state.isPlayed, "$id should be played")
            assertEquals(100.0, state.playedPercentage)
            assertNull(state.playbackPositionTicks)
            assertEquals("2026-03-03T00:00:00Z", state.lastPlayedDate)
        }
        // Items outside the hierarchy are untouched.
        assertNull(playbackStateDao.getById("other-series-ep"))
    }

    @Test
    fun `applyPlayedStateToHierarchy unplayed clears the played date`() = runTest {
        seedOfflineMedia("series-1", mediaType = "SERIES")
        seedOfflineMedia("ep-1", parentId = "season-1", seasonId = "season-1", seriesId = "series-1")
        playbackStateDao.applyPlayedStateToHierarchy("series-1", isPlayed = true, lastPlayedDate = "2026-01-01T00:00:00Z")

        playbackStateDao.applyPlayedStateToHierarchy("series-1", isPlayed = false, lastPlayedDate = null)

        val state = playbackStateDao.getById("ep-1")!!
        assertFalse(state.isPlayed)
        assertEquals(0.0, state.playedPercentage)
        assertNull(state.lastPlayedDate)
    }

    @Test
    fun `applyPlayedStateToHierarchy creates nothing for an unknown item`() = runTest {
        playbackStateDao.applyPlayedStateToHierarchy("missing", isPlayed = true, lastPlayedDate = "2026-01-01T00:00:00Z")

        // The hierarchy match runs against offline_media; no row matches, so no
        // playback_state row is inserted.
        assertNull(playbackStateDao.getById("missing"))
    }

    @Test
    fun `applyFavoriteState creates a favorite-only row on first contact`() = runTest {
        playbackStateDao.applyFavoriteState("item-1", isFavorite = true)

        val loaded = playbackStateDao.getById("item-1")!!
        assertTrue(loaded.isFavorite)
        assertEquals(0.0, loaded.playedPercentage)
        assertNull(loaded.playbackPositionTicks)
        assertFalse(loaded.isPlayed)
    }

    @Test
    fun `applyFavoriteState flips favorite without clobbering progress`() = runTest {
        playbackStateDao.updatePlaybackProgress("item-1", 300L, 25.0, false, "2026-01-01T00:00:00Z")

        playbackStateDao.applyFavoriteState("item-1", isFavorite = true)
        assertTrue(playbackStateDao.getById("item-1")!!.isFavorite)

        playbackStateDao.applyFavoriteState("item-1", isFavorite = false)

        val loaded = playbackStateDao.getById("item-1")!!
        assertFalse(loaded.isFavorite)
        // A favorite flip never clobbers progress.
        assertEquals(300L, loaded.playbackPositionTicks)
        assertEquals(25.0, loaded.playedPercentage)
    }

    @Test
    fun `deleteById removes the row`() = runTest {
        playbackStateDao.upsert(PlaybackStateEntity(id = "item-1"))

        playbackStateDao.deleteById("item-1")

        assertNull(playbackStateDao.getById("item-1"))
    }

    @Test
    fun `deleteBySeriesId removes rows for items under the series only`() = runTest {
        seedOfflineMedia("ep-1", seasonId = "season-1", seriesId = "series-1")
        seedOfflineMedia("ep-2", seasonId = "season-9", seriesId = "series-9")
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-1"))
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-2"))
        // An orphan playback row has no offline_media link, so the series
        // cascade cannot see it and must leave it alone.
        playbackStateDao.upsert(PlaybackStateEntity(id = "orphan"))

        playbackStateDao.deleteBySeriesId("series-1")

        assertNull(playbackStateDao.getById("ep-1"))
        assertNotNull(playbackStateDao.getById("ep-2"))
        assertNotNull(playbackStateDao.getById("orphan"))
    }

    @Test
    fun `deleteBySeasonId removes rows for items under the season only`() = runTest {
        seedOfflineMedia("ep-1", seasonId = "season-1", seriesId = "series-1")
        seedOfflineMedia("ep-2", seasonId = "season-9", seriesId = "series-1")
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-1"))
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-2"))

        playbackStateDao.deleteBySeasonId("season-1")

        assertNull(playbackStateDao.getById("ep-1"))
        assertNotNull(playbackStateDao.getById("ep-2"))
    }

    @Test
    fun `deleteUnreferenced removes rows whose offline media is gone`() = runTest {
        seedOfflineMedia("ep-1")
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-1"))
        playbackStateDao.upsert(PlaybackStateEntity(id = "orphan"))

        playbackStateDao.deleteUnreferenced()

        assertNotNull(playbackStateDao.getById("ep-1"))
        assertNull(playbackStateDao.getById("orphan"))
    }
}
