package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.DownloadPolicyDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.PlayedFlagRow
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.TimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the retention sweep's keep-days boundary against the injected
 * [TimeSource] (the PlayedStateSyncImplTest fake-clock pattern): the cutoff
 * passed to [DownloadDao.getCompletedOlderThan] must be exactly
 * `injected-now − keepDays × 24h` — a sweep that reads the process wall clock
 * instead fails the exact-argument verify — and `autoDownloadKeepDays = 0`
 * stays the documented off-switch (no age query at all).
 *
 * Both arms stop before the deletion choreography, so the DAO collaborators
 * stay relaxed mocks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadRepositoryImplSweepTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadPolicyDao: DownloadPolicyDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val detailFeed: DetailFeed = mockk(relaxed = true)
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)

    /** A wall-clock instant a live clock would never reproduce. */
    private val fixedNowMillis = 1_700_000_000_123L

    @Before
    fun setup() {
        // The impl resolves the policy DAO from the database handle (line 150
        // of the impl); pin it to this suite's mock so the arm-table verifies
        // observe the calls the sweep makes (or deliberately doesn't make).
        every { database.downloadPolicyDao() } returns downloadPolicyDao
    }

    private val fakeTimeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = fixedNowMillis
        override fun nowElapsedRealtimeMillis(): Long = fixedNowMillis
        override fun today(zone: java.time.ZoneId): java.time.LocalDate = java.time.LocalDate.now(zone)
    }

    private fun repository() = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        mediaRepository = MediaRepositoryAccess { detailFeed },
        episodeCatalogue = mockk(relaxed = true),
        imageUrlProvider = mockk(relaxed = true),
        downloadsStore = preferencesStore,
        storagePolicy = mockk(relaxed = true),
        downloadEnqueuer = mockk(relaxed = true),
        timeSource = fakeTimeSource,
        writer = mockk(relaxed = true),
        downloadDelegate = mockk(relaxed = true),
    )

    @Test
    fun `keep-days sweep cutoff is the fake clock minus the keep-days window`() = runTest {
        every { preferencesStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadKeepDays = 7))
        // Nothing older than the cutoff this pass — the boundary value the age
        // query receives is the assertion; the sweep then reports EMPTY.
        coEvery { downloadDao.getCompletedOlderThan(fixedNowMillis - 7L * 24 * 60 * 60 * 1000) } returns emptyList()

        val result = repository().sweepExpiredAutoDownloads()

        assertEquals(AutoDownloadSweepResult.EMPTY, result)
        coVerify(exactly = 1) {
            downloadDao.getCompletedOlderThan(fixedNowMillis - 7L * 24 * 60 * 60 * 1000)
        }
    }

    @Test
    fun `keep-days zero keeps the sweep a no-op`() = runTest {
        every { preferencesStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadKeepDays = 0))

        val result = repository().sweepExpiredAutoDownloads()

        assertEquals(AutoDownloadSweepResult.EMPTY, result)
        coVerify(exactly = 0) { downloadDao.getCompletedOlderThan(any()) }
    }

    @Test
    fun `a sweep that empties a series keeps its arm (ADR-0015 watch-boundary)`() = runTest {
        // The sweep is retention hygiene — a watch-boundary deletion — so
        // sweeping a series to zero rows must NOT clear its arm (gh-225: the
        // weekly series that stopped downloading once its watched episodes
        // were swept).
        every { preferencesStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadKeepDays = 7))
        val swept = listOf(
            DownloadEntity(
                id = "dl-1",
                mediaItemId = "ep-1",
                name = "Episode 1",
                mediaType = "EPISODE",
                downloadPath = "",
                downloadUrl = "https://stream/ep-1",
                totalSizeBytes = 0L,
                downloadedBytes = 0L,
                status = DownloadStatus.COMPLETED.name,
                seriesId = "s1",
                completedAt = fixedNowMillis - 8L * 24 * 60 * 60 * 1000,
            ),
        )
        coEvery { downloadDao.getCompletedOlderThan(fixedNowMillis - 7L * 24 * 60 * 60 * 1000) } returns swept
        coEvery { playbackStateDao.getPlayedFlagsFor(listOf("ep-1")) } returns listOf(PlayedFlagRow("ep-1", isPlayed = true))
        coEvery { downloadDao.getTotalBytesFor(listOf("dl-1")) } returns 1_000L
        // Run the deletion choreography inline (the OfflineRepositoryImplArtworkTest idiom).
        mockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }

        val result = repository().sweepExpiredAutoDownloads()

        assertEquals(1, result.deletedCount)
        assertEquals(1_000L, result.bytesReclaimed)
        // The arm table is untouched by the sweep — no policy delete, no
        // suggestion delete, for any swept series.
        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }
}
