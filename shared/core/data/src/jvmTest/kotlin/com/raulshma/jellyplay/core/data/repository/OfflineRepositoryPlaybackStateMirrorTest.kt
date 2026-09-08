package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the offline store's playback-state MIRROR writes — the local row that
 * every offline screen (and the sync worker's reconcile ladder) reads:
 *
 *  - [OfflineRepositoryImpl.updatePlaybackProgress] must forward the exact
 *    progress payload (a null position is the server's "reset resume point"
 *    and must survive), clamp the percentage into 0..100 (a >100 value fed
 *    straight into the row would break the offline watched-threshold math),
 *    and stamp `lastPlayedDate` from the injected clock — the stamp is the
 *    reconcile ladder's "local activity" tiebreak, so a real-`now()` read
 *    here would make reconciliation nondeterministic;
 *  - [OfflineRepositoryImpl.applyFavoriteState] forwards the single-row
 *    upsert (favorite is per-item — no hierarchy cascade);
 *  - [OfflineRepositoryImpl.getOfflineItem] maps the LEFT-JOIN row's null
 *    playback columns to the "not started" defaults (an unseeded row must
 *    read unplayed/0%, or the offline home would hide it from Continue
 *    Watching) and passes live values through.
 *
 * The DAO is mocked (SQL semantics live in PlaybackStateDaoTest against a
 * real in-memory Room instance); artwork resolution is neutralized by null
 * poster/backdrop/download paths.
 */
class OfflineRepositoryPlaybackStateMirrorTest {

    /** Fixed wall-clock read the stamps are asserted against. */
    private val fakeNowMillis = 1_770_000_000_000L

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private val repository by lazy {
        OfflineRepositoryImpl(
            offlineMediaDao,
            playbackStateDao,
            syncBaselineDao,
            downloadDao,
            database,
            timeSource = object : TimeSource {
                override fun nowEpochMillis(): Long = fakeNowMillis
                override fun nowElapsedRealtimeMillis(): Long = fakeNowMillis
                override fun today(zone: ZoneId): java.time.LocalDate = java.time.LocalDate.of(2026, 2, 1)
            },
        )
    }

    /** The exact stamp the repository must produce for [fakeNowMillis]. */
    private val fakeNowStamp: String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(fakeNowMillis), ZoneId.systemDefault()).toString()

    // ── updatePlaybackProgress ──────────────────────────────────────────

    @Test
    fun `updatePlaybackProgress forwards the payload and stamps the clock's now`() = runTest {
        repository.updatePlaybackProgress(
            itemId = "ep-1",
            positionTicks = 42_000_000L,
            percentage = 70.0,
            isPlayed = false,
        )

        coVerify(exactly = 1) {
            playbackStateDao.updatePlaybackProgress(
                itemId = "ep-1",
                positionTicks = 42_000_000L,
                percentage = 70.0,
                isPlayed = false,
                lastPlayedDate = fakeNowStamp,
            )
        }
    }

    @Test
    fun `updatePlaybackProgress passes a null position through unchanged`() = runTest {
        // The reconcile ladder's server-watched branch writes position=null as
        // the "clean resume point" reset — dropping the null would resurrect
        // the stale position the ladder just decided to clear.
        repository.updatePlaybackProgress(
            itemId = "ep-1",
            positionTicks = null,
            percentage = 0.0,
            isPlayed = true,
        )

        coVerify {
            playbackStateDao.updatePlaybackProgress(
                itemId = "ep-1",
                positionTicks = null,
                percentage = 0.0,
                isPlayed = true,
                lastPlayedDate = fakeNowStamp,
            )
        }
    }

    @Test
    fun `updatePlaybackProgress clamps an over-100 percentage`() = runTest {
        // A server position beyond the runtime derives >100%; feeding that
        // into the row would break the offline watched-threshold compares.
        repository.updatePlaybackProgress("ep-1", 500L, 150.0, isPlayed = false)

        coVerify { playbackStateDao.updatePlaybackProgress("ep-1", 500L, 100.0, false, fakeNowStamp) }
    }

    @Test
    fun `updatePlaybackProgress clamps a negative percentage`() = runTest {
        repository.updatePlaybackProgress("ep-1", 0L, -3.0, isPlayed = false)

        coVerify { playbackStateDao.updatePlaybackProgress("ep-1", 0L, 0.0, false, fakeNowStamp) }
    }

    // ── applyFavoriteState ──────────────────────────────────────────────

    @Test
    fun `applyFavoriteState forwards the single-row upsert`() = runTest {
        repository.applyFavoriteState("ep-1", isFavorite = true)

        coVerify(exactly = 1) { playbackStateDao.applyFavoriteState("ep-1", true) }
    }

    // ── getOfflineItem ──────────────────────────────────────────────────

    @Test
    fun `getOfflineItem returns null when the DAO misses`() = runTest {
        coEvery { offlineMediaDao.getByIdWithPlayback("missing") } returns null

        assertNull(repository.getOfflineItem("missing"))
    }

    @Test
    fun `getOfflineItem maps unseeded playback columns to not-started defaults`() = runTest {
        // A freshly downloaded row has no playback_state entry: the LEFT JOIN
        // yields nulls, which must read as unplayed / 0% — anything else and
        // the offline home would either hide it from or pin it to Continue
        // Watching.
        coEvery { offlineMediaDao.getByIdWithPlayback("movie-1") } returns withPlayback(
            media = mediaEntity("movie-1", mediaType = "MOVIE"),
            playbackPositionTicks = null,
            playedPercentage = null,
            isPlayed = null,
            isFavorite = null,
            lastPlayedDate = null,
        )

        val item = repository.getOfflineItem("movie-1")!!

        assertEquals("movie-1", item.id)
        assertEquals("Media movie-1", item.name)
        assertFalse(item.isPlayed)
        assertFalse(item.isFavorite)
        assertEquals(0.0, item.playedPercentage)
        assertNull(item.playbackPositionTicks)
        assertNull(item.lastPlayedDate)
    }

    @Test
    fun `getOfflineItem passes live playback state through`() = runTest {
        coEvery { offlineMediaDao.getByIdWithPlayback("movie-1") } returns withPlayback(
            media = mediaEntity("movie-1", mediaType = "MOVIE"),
            playbackPositionTicks = 42_000_000L,
            playedPercentage = 70.0,
            isPlayed = false,
            isFavorite = true,
            lastPlayedDate = "2026-01-05T10:00:00Z",
        )

        val item = repository.getOfflineItem("movie-1")!!

        assertEquals(42_000_000L, item.playbackPositionTicks)
        assertEquals(70.0, item.playedPercentage)
        assertFalse(item.isPlayed)
        assertTrue(item.isFavorite)
        assertEquals("2026-01-05T10:00:00Z", item.lastPlayedDate)
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private fun mediaEntity(
        id: String,
        mediaType: String,
        parentId: String? = null,
        seasonId: String? = null,
        seriesId: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Media $id",
        mediaType = mediaType,
        parentId = parentId,
        seasonId = seasonId,
        seriesId = seriesId,
    )

    private fun withPlayback(
        media: OfflineMediaEntity,
        playbackPositionTicks: Long?,
        playedPercentage: Double?,
        isPlayed: Boolean?,
        isFavorite: Boolean?,
        lastPlayedDate: String?,
    ) = OfflineMediaWithPlayback(
        media = media,
        playbackPositionTicks = playbackPositionTicks,
        playedPercentage = playedPercentage,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        lastPlayedDate = lastPlayedDate,
    )
}
