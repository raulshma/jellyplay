package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class OfflineMediaDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var offlineMediaDao: OfflineMediaDao
    private lateinit var playbackStateDao: PlaybackStateDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        offlineMediaDao = database.offlineMediaDao()
        playbackStateDao = database.playbackStateDao()
    }

    @AfterTest
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
        assertEquals(result!!.name, "Test Media")
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

    // ── getUnplayedEpisodeCountsBySeriesFlow ─────────────────────────

    @Test
    fun `getUnplayedEpisodeCountsBySeriesFlow counts unplayed episodes per series`() = runTest {
        offlineMediaDao.upsertAll(
            listOf(
                createMedia(id = "series-1", mediaType = "SERIES"),
                createMedia(id = "series-2", mediaType = "SERIES"),
                createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1"),
                createMedia(id = "ep-2", mediaType = "EPISODE", seriesId = "series-1"),
                createMedia(id = "ep-3", mediaType = "EPISODE", seriesId = "series-1"),
                createMedia(id = "ep-4", mediaType = "EPISODE", seriesId = "series-2"),
                // Not an episode: must not inflate the series-1 count.
                createMedia(id = "season-1", mediaType = "SEASON", seriesId = "series-1"),
            )
        )
        // One of series-1's three episodes watched.
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-3", isPlayed = true))

        val counts = offlineMediaDao.getUnplayedEpisodeCountsBySeriesFlow(listOf("series-1", "series-2"), 95.0)
            .first()
            .associate { it.groupedId to it.unplayedCount }

        assertEquals(2, counts["series-1"])
        assertEquals(1, counts["series-2"])
    }

    @Test
    fun `getUnplayedEpisodeCountsBySeriesFlow excludes episodes at or above the watched threshold`() = runTest {
        offlineMediaDao.upsertAll(
            listOf(
                createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1"),
                createMedia(id = "ep-2", mediaType = "EPISODE", seriesId = "series-1"),
                createMedia(id = "ep-3", mediaType = "EPISODE", seriesId = "series-1"),
            )
        )
        // played = false but 96% resume — finished by the watched threshold
        // (OFFLINE_WATCHED_THRESHOLD = 0.95), so the badge must not count it.
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-2", playedPercentage = 96.0))
        // Exactly AT the threshold counts as finished too — the comparison is
        // strict (>= threshold means watched). Pins the SQL literal to the
        // model constant's documented boundary.
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-3", playedPercentage = 95.0))

        val counts = offlineMediaDao.getUnplayedEpisodeCountsBySeriesFlow(listOf("series-1"), 95.0)
            .first()
            .associate { it.groupedId to it.unplayedCount }

        assertEquals(1, counts["series-1"])
    }

    @Test
    fun `getUnplayedEpisodeCountsBySeriesFlow omits series with no unplayed episodes`() = runTest {
        offlineMediaDao.upsertAll(
            listOf(
                createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1"),
            )
        )
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-1", isPlayed = true))

        val rows = offlineMediaDao.getUnplayedEpisodeCountsBySeriesFlow(listOf("series-1"), 95.0).first()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `getUnplayedEpisodeCountsBySeriesFlow re-emits when playback state changes`() = runTest {
        offlineMediaDao.upsertAll(
            listOf(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1"))
        )
        val emissions = Channel<Int>(capacity = Channel.UNLIMITED)
        val collector = launch {
            offlineMediaDao.getUnplayedEpisodeCountsBySeriesFlow(listOf("series-1"), 95.0)
                .collect { rows -> emissions.send(rows.firstOrNull()?.unplayedCount ?: 0) }
        }
        // Initial emission: ep-1 is unplayed.
        assertEquals(1, emissions.receive())
        // A playback_state write must push a fresh emission through the SAME
        // collector — the badge follows offline watching without a
        // re-subscribe (the query reads the ⟕ playback view).
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-1", isPlayed = true))
        assertEquals(0, emissions.receive())
        collector.cancel()
    }

    // ── getUnplayedEpisodeCountsBySeasonFlow ─────────────────────────

    @Test
    fun `getUnplayedEpisodeCountsBySeasonFlow counts unplayed episodes per season`() = runTest {
        offlineMediaDao.upsertAll(
            listOf(
                createMedia(id = "series-1", mediaType = "SERIES"),
                createMedia(id = "season-1", mediaType = "SEASON", seriesId = "series-1"),
                createMedia(id = "season-2", mediaType = "SEASON", seriesId = "series-1"),
                createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"),
                createMedia(id = "ep-2", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"),
                createMedia(id = "ep-3", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-2"),
            )
        )
        // One of season-1's two episodes watched; season-2 untouched.
        playbackStateDao.upsert(PlaybackStateEntity(id = "ep-2", isPlayed = true))

        val counts = offlineMediaDao.getUnplayedEpisodeCountsBySeasonFlow(listOf("season-1", "season-2"), 95.0)
            .first()
            .associate { it.groupedId to it.unplayedCount }

        assertEquals(1, counts["season-1"])
        assertEquals(1, counts["season-2"])
    }

    @Test
    fun `getUnplayedEpisodeCountsBySeasonFlow ignores episodes of seasons outside the query`() = runTest {
        offlineMediaDao.upsertAll(
            listOf(
                createMedia(id = "season-1", mediaType = "SEASON", seriesId = "series-1"),
                createMedia(id = "season-2", mediaType = "SEASON", seriesId = "series-1"),
                createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-2"),
            )
        )

        val rows = offlineMediaDao.getUnplayedEpisodeCountsBySeasonFlow(listOf("season-1"), 95.0).first()

        assertTrue(rows.isEmpty())
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
        assertEquals(row.lastPlayedDate, "2026-07-21T10:00:00Z")
    }

    @Test
    fun `applyPlayedStateToHierarchy is a no-op when no rows match`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"))

        playbackStateDao.applyPlayedStateToHierarchy(itemId = "nonexistent", isPlayed = true, lastPlayedDate = "2026-07-21T10:00:00Z")

        // No playback row is created for ep-1 => effectively not played.
        assertEquals(false, playbackStateDao.getById("ep-1")?.isPlayed ?: false)
    }
}
