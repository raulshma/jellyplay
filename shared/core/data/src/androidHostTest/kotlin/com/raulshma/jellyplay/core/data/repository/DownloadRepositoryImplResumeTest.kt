package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.InterruptedResumeRow
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
 * V3 downloads conveyor: the impl moved to :shared:core:data jvmShared; this
 * legacy-side suite now constructs it through the seam ctor (mock coordinator /
 * notifier, a simple MediaRepositoryAccess fake, and a relaxed delegate —
 * the D6 writer-core extraction removed both the Lazy and the repo's own
 * write cluster). Robolectric stays because the shared Log
 * facade's Android actual delegates to android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadRepositoryImplResumeTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)
    // downloadSeries delegates the per-episode bundle here; the resume tests
    // never exercise it, so a relaxed mock is sufficient. (D6: no longer a
    // kotlin Lazy — the writer-core extraction dissolved the construction
    // cycle the Lazy used to break.)
    private val downloadDelegate: DownloadDelegate = mockk(relaxed = true)
    // Storage cap + WorkManager enqueue were extracted out of the repo into
    // their own modules. The resume path exercises neither, so relaxed mocks
    // (the enqueue seam's interface) suffice.
    private val storagePolicy: StoragePolicy = mockk(relaxed = true)
    private val downloadEnqueuer: DownloadEnqueueCoordinator = mockk(relaxed = true)
    // The resume path never loads series episodes; a relaxed mock satisfies the
    // catalogue constructor param added alongside the seasons/episodes migration.
    private val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue = mockk(relaxed = true)
    // Notification summary seam: relaxed mock — the resume path refreshes the
    // summary but never renders notifications here.
    private val progressNotifier: DownloadProgressNotifier = mockk(relaxed = true)
    // D6: the artifact-write cluster (start/metadata seeding/sidecars) lives in
    // the writer core; the resume path touches only DAO reads + batch updates,
    // so a relaxed mock suffices.
    private val writer: OfflineDownloadWriterCore = mockk(relaxed = true)

    private fun repository() = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        mediaRepository = MediaRepositoryAccess { mediaRepository },
        episodeCatalogue = episodeCatalogue,
        playbackRepository = playbackRepository,
        downloadsStore = preferencesStore,
        storagePolicy = storagePolicy,
        downloadEnqueuer = downloadEnqueuer,
        progressNotifier = progressNotifier,
        writer = writer,
        downloadDelegate = downloadDelegate,
    )

    @Test
    fun `user-paused download is skipped on auto-resume`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-1", status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.USER.persistedValue),
        )

        repository().resumeInterruptedDownloads()

        // No status flip, no enqueue — the user must resume it manually.
        coVerify(exactly = 0) { downloadDao.updateStatusWithPausedReasonForIds(any(), any(), any()) }
        coVerify(exactly = 0) { downloadDao.markResumedFromZeroForIds(any(), any(), any()) }
    }

    @Test
    fun `network-paused download resumes preserving its byte offset`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-2", downloadedBytes = 500L, status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.NETWORK.persistedValue),
        )

        repository().resumeInterruptedDownloads()

        // Paused rows keep their contiguous byte prefix: the batch UPDATE
        // touches only status + pause reason, leaving downloadedBytes at 500.
        coVerify { downloadDao.updateStatusWithPausedReasonForIds(listOf("dl-2"), DownloadStatus.PENDING.name, null) }
        coVerify(exactly = 0) { downloadDao.markResumedFromZeroForIds(any(), any(), any()) }
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

        coVerify { downloadDao.markResumedFromZeroForIds(listOf("dl-3"), DownloadStatus.PENDING.name, null) }
        coVerify(exactly = 0) { downloadDao.updateStatusWithPausedReasonForIds(any(), any(), any()) }
    }

    @Test
    fun `download past the auto-retry budget is dead-lettered`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-4", retryCount = DOWNLOAD_MAX_AUTO_RETRY),
        )

        repository().resumeInterruptedDownloads()

        // Left FAILED for a manual retry — no auto-resume this pass.
        coVerify(exactly = 0) { downloadDao.updateStatusWithPausedReasonForIds(any(), any(), any()) }
        coVerify(exactly = 0) { downloadDao.markResumedFromZeroForIds(any(), any(), any()) }
    }

    @Test
    fun `a failing resume batch does not abort the other batch`() = runTest {
        coEvery { downloadDao.getInterruptedResumeRows(any()) } returns listOf(
            row("dl-bad", status = DownloadStatus.FAILED.name, pausedReason = null),
            row("dl-good", downloadedBytes = 200L, status = DownloadStatus.PAUSED.name, pausedReason = DownloadPauseReason.NETWORK.persistedValue),
        )
        // The from-zero batch UPDATE throws (e.g. a DB transient); the
        // keep-bytes batch must still flip the paused row so one failure
        // can't strand every interrupted download until the next reconnect.
        coEvery { downloadDao.markResumedFromZeroForIds(any(), any(), any()) } throws RuntimeException("db transient")

        repository().resumeInterruptedDownloads()

        coVerify { downloadDao.updateStatusWithPausedReasonForIds(listOf("dl-good"), DownloadStatus.PENDING.name, null) }
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
