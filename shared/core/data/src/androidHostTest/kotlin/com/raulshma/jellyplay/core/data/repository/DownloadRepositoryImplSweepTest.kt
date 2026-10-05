package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.TimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)

    /** A wall-clock instant a live clock would never reproduce. */
    private val fixedNowMillis = 1_700_000_000_123L

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
        mediaRepository = MediaRepositoryAccess { mediaRepository },
        episodeCatalogue = mockk(relaxed = true),
        imageUrlProvider = mockk(relaxed = true),
        downloadsStore = preferencesStore,
        storagePolicy = mockk(relaxed = true),
        downloadEnqueuer = mockk(relaxed = true),
        progressNotifier = mockk(relaxed = true),
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
}
