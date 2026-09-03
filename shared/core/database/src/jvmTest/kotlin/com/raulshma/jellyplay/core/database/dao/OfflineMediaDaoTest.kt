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
        parentId: String? = null,
        seriesId: String? = null,
        seasonId: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Test Media",
        mediaType = mediaType,
        parentId = parentId,
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

    // ── hierarchy browse queries ─────────────────────────────────────

    @Test
    fun `getTopLevelItemsInLibrary filters by parentId and top-level media types`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", mediaType = "MOVIE", parentId = "lib-1"))
        offlineMediaDao.upsert(createMedia(id = "m2", mediaType = "SERIES", parentId = "lib-1"))
        offlineMediaDao.upsert(createMedia(id = "m3", mediaType = "MOVIE", parentId = "lib-2"))
        offlineMediaDao.upsert(createMedia(id = "ep-1", mediaType = "EPISODE", parentId = "lib-1"))

        val items = offlineMediaDao.getTopLevelItemsInLibrary("lib-1").first()

        assertEquals(setOf("m1", "m2"), items.map { it.media.id }.toSet())
    }

    @Test
    fun `getSeasonsForSeries returns only seasons ordered by seasonNumber`() = runTest {
        fun season(id: String, number: Int) = OfflineMediaEntity(
            id = id, name = "Season $number", mediaType = "SEASON",
            seriesId = "series-1", seasonNumber = number,
        )
        offlineMediaDao.upsert(season("s3", 3))
        offlineMediaDao.upsert(season("s1", 1))
        offlineMediaDao.upsert(season("s2", 2))
        // An episode of the same series must not appear in the seasons query.
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "ep-1", name = "Ep", mediaType = "EPISODE", seriesId = "series-1"),
        )

        val seasons = offlineMediaDao.getSeasonsForSeries("series-1").first()

        assertEquals(listOf("s1", "s2", "s3"), seasons.map { it.media.id })
    }

    @Test
    fun `getEpisodesForSeason returns only episodes ordered by episodeNumber`() = runTest {
        fun episode(id: String, seasonId: String, number: Int) = OfflineMediaEntity(
            id = id, name = "Ep $number", mediaType = "EPISODE",
            seriesId = "series-1", seasonId = seasonId, episodeNumber = number,
        )
        offlineMediaDao.upsert(episode("e4", "season-1", 4))
        offlineMediaDao.upsert(episode("e2", "season-1", 2))
        offlineMediaDao.upsert(episode("e3", "season-1", 3))
        offlineMediaDao.upsert(episode("e9", "season-2", 9))

        val episodes = offlineMediaDao.getEpisodesForSeason("season-1").first()

        assertEquals(listOf("e2", "e3", "e4"), episodes.map { it.media.id })
    }

    @Test
    fun `getEpisodesForSeries returns every episode season- then episode-ordered`() = runTest {
        fun episode(id: String, seasonNumber: Int, episodeNumber: Int) = OfflineMediaEntity(
            id = id, name = "Ep $id", mediaType = "EPISODE",
            seriesId = "series-1", seasonId = "season-$seasonNumber",
            seasonNumber = seasonNumber, episodeNumber = episodeNumber,
        )
        offlineMediaDao.upsert(episode("s1e2", 1, 2))
        offlineMediaDao.upsert(episode("s2e1", 2, 1))
        offlineMediaDao.upsert(episode("s1e1", 1, 1))
        // A SEASON row shares the seriesId but must be excluded.
        offlineMediaDao.upsert(
            OfflineMediaEntity(
                id = "season-1", name = "Season 1", mediaType = "SEASON",
                seriesId = "series-1", seasonNumber = 1,
            ),
        )

        val episodes = offlineMediaDao.getEpisodesForSeries("series-1")

        assertEquals(listOf("s1e1", "s1e2", "s2e1"), episodes.map { it.media.id })
    }

    @Test
    fun `getDownloadedEpisodes returns episodes of every series and no other type`() = runTest {
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "e1", name = "Ep 1", mediaType = "EPISODE", seriesId = "series-1"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "e2", name = "Ep 2", mediaType = "EPISODE", seriesId = "series-2"),
        )
        offlineMediaDao.upsert(createMedia(id = "mv", mediaType = "MOVIE"))

        val episodes = offlineMediaDao.getDownloadedEpisodes().first()

        assertEquals(setOf("e1", "e2"), episodes.map { it.media.id }.toSet())
    }

    @Test
    fun `getChildrenByParent returns the parent's rows ordered by indexNumber`() = runTest {
        fun child(id: String, parent: String, index: Int?) = OfflineMediaEntity(
            id = id, name = id, mediaType = "MOVIE", parentId = parent, indexNumber = index,
        )
        offlineMediaDao.upsert(child("c3", "p-1", 3))
        offlineMediaDao.upsert(child("c1", "p-1", 1))
        offlineMediaDao.upsert(child("c2", "p-1", 2))
        offlineMediaDao.upsert(child("other", "p-2", 0))

        val children = offlineMediaDao.getChildrenByParent("p-1").first()

        assertEquals(listOf("c1", "c2", "c3"), children.map { it.media.id })
    }

    // ── metadata lookups ─────────────────────────────────────────────

    @Test
    fun `getByIds returns only the requested rows`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1"))
        offlineMediaDao.upsert(createMedia(id = "m2"))
        offlineMediaDao.upsert(createMedia(id = "m3"))

        val rows = offlineMediaDao.getByIds(listOf("m1", "m3"))

        assertEquals(setOf("m1", "m3"), rows.map { it.id }.toSet())
    }

    @Test
    fun `getByIdFlow re-emits after an upsert`() = runTest {
        val emissions = Channel<String?>(capacity = Channel.UNLIMITED)
        val collector = launch {
            offlineMediaDao.getByIdFlow("m1").collect { emissions.send(it?.name) }
        }
        assertEquals(null, emissions.receive())

        offlineMediaDao.upsert(createMedia(id = "m1", mediaType = "MOVIE").copy(name = "Renamed"))
        assertEquals("Renamed", emissions.receive())
        collector.cancel()
    }

    @Test
    fun `getByIdWithPlayback joins the playback state row`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1"))
        playbackStateDao.upsert(
            PlaybackStateEntity(
                id = "m1",
                playedPercentage = 55.0,
                isFavorite = true,
                playbackPositionTicks = 1_000L,
                lastPlayedDate = "2026-07-21T10:00:00Z",
            ),
        )

        val row = offlineMediaDao.getByIdWithPlayback("m1")

        assertNotNull(row)
        assertEquals("m1", row!!.media.id)
        assertEquals(55.0, row.playedPercentage!!, 0.0)
        assertEquals(true, row.isFavorite)
        assertEquals(1_000L, row.playbackPositionTicks)
    }

    @Test
    fun `getByIdWithPlayback returns null playback columns for a row without state`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1"))

        val row = offlineMediaDao.getByIdWithPlayback("m1")

        assertNotNull(row)
        assertNull(row!!.playedPercentage)
        assertNull(row.isPlayed)
        assertNull(row.isFavorite)
    }

    @Test
    fun `getByIdWithPlayback returns null for a missing id`() = runTest {
        assertNull(offlineMediaDao.getByIdWithPlayback("nonexistent"))
    }

    @Test
    fun `getByIdWithPlaybackFlow emits the joined row`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1"))

        val row = offlineMediaDao.getByIdWithPlaybackFlow("m1").first()

        assertNotNull(row)
        assertEquals("m1", row!!.media.id)
    }

    // ── deletes ──────────────────────────────────────────────────────

    @Test
    fun `deleteBySeasonId removes that season's episodes only`() = runTest {
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "e1", name = "Ep 1", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "e2", name = "Ep 2", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-2"),
        )

        offlineMediaDao.deleteBySeasonId("season-1")

        assertNull(offlineMediaDao.getById("e1"))
        assertNotNull(offlineMediaDao.getById("e2"))
    }

    @Test
    fun `cleanupOrphans removes childless seasons and series but keeps intact chains`() = runTest {
        // Intact chain: series-1 → season-1 → ep-1.
        offlineMediaDao.upsert(createMedia(id = "series-1", mediaType = "SERIES"))
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "season-1", name = "S1", mediaType = "SEASON", seriesId = "series-1"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "ep-1", name = "Ep", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"),
        )
        // Orphans: a childless series and a childless season.
        offlineMediaDao.upsert(createMedia(id = "series-2", mediaType = "SERIES"))
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "season-2", name = "S2", mediaType = "SEASON", seriesId = "series-1"),
        )

        offlineMediaDao.cleanupOrphans()

        assertNull(offlineMediaDao.getById("season-2"), "childless season must be removed")
        assertNull(offlineMediaDao.getById("series-2"), "childless series must be removed")
        assertNotNull(offlineMediaDao.getById("series-1"))
        assertNotNull(offlineMediaDao.getById("season-1"))
        assertNotNull(offlineMediaDao.getById("ep-1"))
    }

    // ── search ───────────────────────────────────────────────────────

    @Test
    fun `search matches name and series name case-insensitively with prefix ranking`() = runTest {
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "a", name = "Interstellar", mediaType = "MOVIE"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "b", name = "Battle Interlude", mediaType = "MOVIE"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "c", name = "Ep One", mediaType = "EPISODE", seriesName = "Interlude Show"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "d", name = "Unrelated", mediaType = "MOVIE"),
        )

        // Lowercase pattern: default LIKE case-insensitivity + ESCAPE clause.
        val results = offlineMediaDao.search("%inte%", "inte%", 10)

        // Prefix hit first, then the contains-hits alphabetically (NOCASE).
        assertEquals(listOf("a", "b", "c"), results.map { it.media.id })
    }

    @Test
    fun `search escapes LIKE wildcards in the pattern`() = runTest {
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "pct", name = "Discount %Deal", mediaType = "MOVIE"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "plain", name = "Discount Deal", mediaType = "MOVIE"),
        )

        // The escaped pattern matches only a LITERAL '%' in the name.
        val results = offlineMediaDao.search("%\\%%", "\\%%", 10)

        assertEquals(listOf("pct"), results.map { it.media.id })
    }

    @Test
    fun `search respects the limit`() = runTest {
        offlineMediaDao.upsert(OfflineMediaEntity(id = "a", name = "Interstellar", mediaType = "MOVIE"))
        offlineMediaDao.upsert(OfflineMediaEntity(id = "b", name = "Battle Interlude", mediaType = "MOVIE"))
        offlineMediaDao.upsert(OfflineMediaEntity(id = "c", name = "Final Interlude", mediaType = "MOVIE"))

        val results = offlineMediaDao.search("%inte%", "inte%", 2)

        assertEquals(2, results.size)
    }

    // ── projections / batch reads ────────────────────────────────────

    @Test
    fun `getDownloadedItemIds includes episodes unlike the top-level queries`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "mv", mediaType = "MOVIE"))
        offlineMediaDao.upsert(createMedia(id = "series-1", mediaType = "SERIES"))
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "season-1", name = "S1", mediaType = "SEASON", seriesId = "series-1"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "ep-1", name = "Ep", mediaType = "EPISODE", seriesId = "series-1", seasonId = "season-1"),
        )

        val ids = offlineMediaDao.getDownloadedItemIds()

        // SEASON is intentionally excluded (it is not a downloadable item);
        // EPISODE is intentionally included (freshness checks cover episodes).
        assertEquals(setOf("mv", "series-1", "ep-1"), ids.toSet())
    }

    @Test
    fun `getLocalImagePaths returns the persisted paths`() = runTest {
        offlineMediaDao.upsert(
            createMedia(id = "m1").copy(posterPath = "/data/poster.jpg", backdropPath = "/data/backdrop.jpg"),
        )

        val paths = offlineMediaDao.getLocalImagePaths("m1")

        assertNotNull(paths)
        assertEquals("/data/poster.jpg", paths!!.posterPath)
        assertEquals("/data/backdrop.jpg", paths.backdropPath)
    }

    @Test
    fun `getLocalImagePaths returns nulls for a row without images`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1"))

        val paths = offlineMediaDao.getLocalImagePaths("m1")

        assertNotNull(paths)
        assertNull(paths!!.posterPath)
        assertNull(paths.backdropPath)
    }

    @Test
    fun `getAllPeopleJson returns id and peopleJson for every row`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1").copy(peopleJson = """[{"name":"Alice"}]"""))
        offlineMediaDao.upsert(createMedia(id = "m2"))

        val rows = offlineMediaDao.getAllPeopleJson()

        assertEquals(2, rows.size)
        assertEquals("""[{"name":"Alice"}]""", rows.first { it.id == "m1" }.peopleJson)
        assertNull(rows.first { it.id == "m2" }.peopleJson)
    }

    @Test
    fun `getRelatedByGenre matches a CSV genre without substring false positives`() = runTest {
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "cur", name = "Current", mediaType = "MOVIE", genres = "Action,SciFi"),
        )
        // "ActionAdventure" must NOT match a search for "Action" (the CSV wrap
        // prevents the substring false positive).
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "fused", name = "Fused", mediaType = "MOVIE", genres = "ActionAdventure"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "yes", name = "Match", mediaType = "MOVIE", genres = "Drama,Action"),
        )
        // Only MOVIE/SERIES participate — an episode with the genre is excluded.
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "ep", name = "Ep", mediaType = "EPISODE", seriesId = "s", genres = "Action"),
        )

        val related = offlineMediaDao.getRelatedByGenre("cur", "Action", 10)

        assertEquals(listOf("yes"), related.map { it.media.id })
    }

    @Test
    fun `getRelatedByStudio matches a CSV studio`() = runTest {
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "cur", name = "Current", mediaType = "MOVIE", studios = "A24,Netflix"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "fused", name = "Fused", mediaType = "MOVIE", studios = "A24Studios"),
        )
        offlineMediaDao.upsert(
            OfflineMediaEntity(id = "yes", name = "Match", mediaType = "MOVIE", studios = "Hulu,A24"),
        )

        val related = offlineMediaDao.getRelatedByStudio("cur", "A24", 10)

        assertEquals(listOf("yes"), related.map { it.media.id })
    }

    @Test
    fun `getRelatedByGenre respects the limit`() = runTest {
        offlineMediaDao.upsert(OfflineMediaEntity(id = "cur", name = "Current", mediaType = "MOVIE", genres = "Action"))
        offlineMediaDao.upsert(OfflineMediaEntity(id = "r1", name = "R1", mediaType = "MOVIE", genres = "Action"))
        offlineMediaDao.upsert(OfflineMediaEntity(id = "r2", name = "R2", mediaType = "MOVIE", genres = "Action"))

        val related = offlineMediaDao.getRelatedByGenre("cur", "Action", 1)

        assertEquals(1, related.size)
    }
}
