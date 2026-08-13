package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.InterruptedResumeRow
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import dagger.Lazy
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DownloadRepositoryImpl.resumeInterruptedDownloads] decides, per interrupted
 * row, whether it is eligible for auto-resume on a network reconnect:
 *
 * - a USER-paused download stays paused (only NETWORK interruptions resume);
 * - a row past the auto-retry budget is dead-lettered (left for a manual retry);
 * - a NETWORK-paused row resumes from its persisted byte offset (contiguous
 *   prefix), while a FAILED row resumes from 0 (its partial is gone/gapped).
 *
 * WorkManager's official test helper provides a real in-memory scheduler so
 * the enqueue path runs without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadRepositoryImplResumeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val httpClient: OkHttpClient = mockk()
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)
    private val json: Json = Json
    // downloadSeries delegates the per-episode bundle here; the resume tests
    // never exercise it, so a relaxed mock behind a dagger.Lazy is sufficient.
    private val downloadDelegate: Lazy<DownloadDelegate> = mockk(relaxed = true)
    // Storage cap + WorkManager enqueue were extracted out of the repo into
    // their own modules. The resume path exercises neither, so relaxed mocks
    // suffice.
    private val storagePolicy: StoragePolicy = mockk(relaxed = true)
    private val downloadEnqueuer: DownloadEnqueuer = mockk(relaxed = true)
    // Path-layout policy was extracted out of the repo; the resume path never
    // starts a new download, so a relaxed mock suffices.
    private val storageLayout: DownloadStorageLayout = mockk(relaxed = true)
    // The resume path never consults the offline-sync comparator; a relaxed mock
    // satisfies the constructor param added alongside the sync wiring.
    private val syncComparator: com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator = mockk(relaxed = true)
    // The resume path never loads series episodes; a relaxed mock satisfies the
    // catalogue constructor param added alongside the seasons/episodes migration.
    private val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue = mockk(relaxed = true)

    private fun repository() = DownloadRepositoryImpl(
        context = context,
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        mediaRepository = mediaRepository,
        episodeCatalogue = episodeCatalogue,
        playbackRepository = playbackRepository,
        httpClient = httpClient,
        downloadsStore = preferencesStore,
        json = json,
        downloadDelegate = downloadDelegate,
        storagePolicy = storagePolicy,
        downloadEnqueuer = downloadEnqueuer,
        storageLayout = storageLayout,
        syncComparator = syncComparator,
    )

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test
    fun `user-paused download is skipped on auto-resume`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-1", status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.USER.persistedValue),
        )

        repository().resumeInterruptedDownloads()

        // No progress reset, no enqueue — the user must resume it manually.
        coVerify(exactly = 0) { downloadDao.updateProgress(any(), any(), any()) }
    }

    @Test
    fun `network-paused download resumes preserving its byte offset`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-2", downloadedBytes = 500L, status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.NETWORK.persistedValue),
        )

        repository().resumeInterruptedDownloads()

        coVerify { downloadDao.updateProgress("dl-2", 500L, DownloadStatus.PENDING.name) }
        coVerify { downloadDao.updatePausedReason("dl-2", null) }
    }

    @Test
    fun `failed download resumes from zero bytes`() = runTest {
        // A FAILED partial was deleted at cold start (multi-connection scattered
        // writes can't be appended to), so resume from 0 — never the stale
        // downloadedBytes, which would send `Range: bytes=N-` against nothing.
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-3", downloadedBytes = 800L, status = DownloadStatus.FAILED.name, pausedReason = null),
        )

        repository().resumeInterruptedDownloads()

        coVerify { downloadDao.updateProgress("dl-3", 0L, DownloadStatus.PENDING.name) }
        coVerify(exactly = 0) { downloadDao.updateProgress("dl-3", 800L, any()) }
    }

    @Test
    fun `download past the auto-retry budget is dead-lettered`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-4", retryCount = DOWNLOAD_MAX_AUTO_RETRY),
        )

        repository().resumeInterruptedDownloads()

        // Left FAILED for a manual retry — no auto-resume this pass.
        coVerify(exactly = 0) { downloadDao.updateProgress(any(), any(), any()) }
    }

    @Test
    fun `a bad row does not abort the rest of the batch`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-bad", status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.NETWORK.persistedValue),
            row("dl-good", downloadedBytes = 200L, status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.NETWORK.persistedValue),
        )
        // The bad row's reset throws (e.g. a DB transient); the good row must
        // still be processed so one failure can't strand every interrupted
        // download until the next reconnect.
        coEvery { downloadDao.updateProgress("dl-bad", any(), any()) } throws RuntimeException("db transient")

        repository().resumeInterruptedDownloads()

        coVerify { downloadDao.updateProgress("dl-good", 200L, DownloadStatus.PENDING.name) }
    }

    private fun row(
        id: String,
        downloadedBytes: Long = 0L,
        status: String = DownloadStatus.FAILED.name,
        pausedReason: String? = null,
        retryCount: Int = 0,
    ) = InterruptedResumeRow(
        id = id,
        downloadedBytes = downloadedBytes,
        status = status,
        pausedReason = pausedReason,
        retryCount = retryCount,
    )
}
