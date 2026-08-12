package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineMediaDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var offlineMediaDao: OfflineMediaDao
    private lateinit var playbackStateDao: PlaybackStateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        offlineMediaDao = database.offlineMediaDao()
        playbackStateDao = database.playbackStateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createMedia(
        id: String = "media-1",
        mediaType: String = "MOVIE",
        seriesId: String? = null,
        seasonId: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Test Media",
        mediaType = mediaType,
        seriesId = seriesId,
        seasonId = seasonId,
    )

    @Test
    fun `upsert and getById`() = runTest {
        val media = createMedia()
        offlineMediaDao.upsert(media)

        val result = offlineMediaDao.getById("media-1")
        assertNotNull(result)
        assertEquals("Test Media", result!!.name)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(offlineMediaDao.getById("nonexistent"))
    }

    @Test
    fun `getTopLevelItems returns series movies audio music`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", mediaType = "MOVIE"))
        offlineMediaDao.upsert(createMedia(id = "m2", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "m3", mediaType = "AUDIO"))
        offlineMediaDao.upsert(createMedia(id = "m4", mediaType = "EPISODE"))

        val items = offlineMediaDao.getTopLevelItems().first()
        assertEquals(3, items.size)
    }

    @Test
    fun `deleteById removes media`() = runTest {
        offlineMediaDao.upsert(createMedia())
        offlineMediaDao.deleteById("media-1")

        assertNull(offlineMediaDao.getById("media-1"))
    }

    @Test
    fun `deleteBySeriesId removes all items with seriesId`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "m2", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "m3", seriesId = "series-2"))

        offlineMediaDao.deleteBySeriesId("series-1")

        assertNull(offlineMediaDao.getById("m1"))
        assertNull(offlineMediaDao.getById("m2"))
        assertNotNull(offlineMediaDao.getById("m3"))
    }

    @Test
    fun `upsertAll inserts multiple items`() = runTest {
        val items = listOf(
            createMedia(id = "m1"),
            createMedia(id = "m2"),
        )
        offlineMediaDao.upsertAll(items)

        assertNotNull(offlineMediaDao.getById("m1"))
        assertNotNull(offlineMediaDao.getById("m2"))
    }

    @Test
    fun `getOfflineItemCount counts top-level items`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", mediaType = "MOVIE"))
        offlineMediaDao.upsert(createMedia(id = "m2", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "m3", mediaType = "EPISODE"))

        val count = offlineMediaDao.getOfflineItemCount().first()
        assertEquals(2, count)
    }

    // ── applyPlayedStateToHierarchy ───────────────────────────────────

    @Test
    fun `applyPlayedStateToHierarchy marks episode and its season and series children`() = runTest {
        // Series with one season and two episodes.
        offlineMediaDao.upsert(createMedia(id = "series-1", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "season-1", mediaType = "SEASON", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))
        offlineMediaDao.upsert(createMedia(id = "ep-2", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))
        // An unrelated series/episode must be untouched.
        offlineMediaDao.upsert(createMedia(id = "series-2", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "ep-x", mediaType = "EPISODE", seriesId = "series-2", seasonId = "season-2"))

        playbackStateDao.applyPlayedStateToHierarchy(itemId = "series-1", isPlayed = true, lastPlayedDate = "2026-07-21T10:00:00Z")

        assertEquals(true, playbackStateDao.getById("series-1")?.isPlayed)
        assertEquals(true, playbackStateDao.getById("season-1")?.isPlayed)
        assertEquals(true, playbackStateDao.getById("ep-1")?.isPlayed)
        assertEquals(true, playbackStateDao.getById("ep-2")?.isPlayed)
        // Unrelated hierarchy untouched (no playback row created => effectively not played).
        assertEquals(false, playbackStateDao.getById("series-2")?.isPlayed ?: false)
        assertEquals(false, playbackStateDao.getById("ep-x")?.isPlayed ?: false)
    }

    @Test
    fun `applyPlayedStateToHierarchy keyed on seasonId cascades only that season`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "series-1", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "season-1", mediaType = "SEASON", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "season-2", mediaType = "SEASON", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))
        offlineMediaDao.upsert(createMedia(id = "ep-2", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-2"))

        playbackStateDao.applyPlayedStateToHierarchy(itemId = "season-1", isPlayed = true, lastPlayedDate = "2026-07-21T10:00:00Z")

        assertEquals(true, playbackStateDao.getById("season-1")?.isPlayed)
        assertEquals(true, playbackStateDao.getById("ep-1")?.isPlayed)
        // season-2 and its episode untouched (no playback row created => effectively not played).
        assertEquals(false, playbackStateDao.getById("season-2")?.isPlayed ?: false)
        assertEquals(false, playbackStateDao.getById("ep-2")?.isPlayed ?: false)
        assertEquals(false, playbackStateDao.getById("series-1")?.isPlayed ?: false)
    }

    @Test
    fun `applyPlayedStateToHierarchy unplayed resets percentage and clears position`() = runTest {
        // Seed the episode metadata and a played playback row with position/percentage set.
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))
        playbackStateDao.upsert(
            PlaybackStateEntity(
                id = "ep-1",
                isPlayed = true,
                playedPercentage = 100.0,
                playbackPositionTicks = 5_000_000_000L,
                lastPlayedDate = "2026-07-20T10:00:00Z",
            ),
        )

        playbackStateDao.applyPlayedStateToHierarchy(itemId = "season-1", isPlayed = false, lastPlayedDate = null)

        val row = playbackStateDao.getById("ep-1")!!
        assertEquals(false, row.isPlayed)
        assertEquals(0.0, row.playedPercentage, 0.001)
        assertEquals(null, row.playbackPositionTicks)
        assertEquals(null, row.lastPlayedDate)
    }

    @Test
    fun `applyPlayedStateToHierarchy played resets stale resume position`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))
        playbackStateDao.upsert(
            PlaybackStateEntity(
                id = "ep-1",
                playbackPositionTicks = 5_000_000_000L,
                playedPercentage = 50.0,
            ),
        )

        playbackStateDao.applyPlayedStateToHierarchy(
            itemId = "season-1",
            isPlayed = true,
            lastPlayedDate = "2026-07-21T10:00:00Z",
        )

        val row = playbackStateDao.getById("ep-1")!!
        assertTrue(row.isPlayed)
        assertEquals(100.0, row.playedPercentage, 0.001)
        assertNull(row.playbackPositionTicks)
        assertEquals("2026-07-21T10:00:00Z", row.lastPlayedDate)
    }

    @Test
    fun `applyPlayedStateToHierarchy is a no-op when no rows match`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))

        playbackStateDao.applyPlayedStateToHierarchy(itemId = "nonexistent", isPlayed = true, lastPlayedDate = "2026-07-21T10:00:00Z")

        // No playback row is created for ep-1 => effectively not played.
        assertEquals(false, playbackStateDao.getById("ep-1")?.isPlayed ?: false)
    }
}
