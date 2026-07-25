package com.raulshma.jellyplay.startup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Cold-start download recovery. Extracted out of `JellyPlayApplication` so the
 * Application class *composes* startup steps rather than *containing* them.
 *
 * Re-enqueues `PENDING` downloads and resets `DOWNLOADING` rows back to
 * `PENDING` on every cold start, then deletes orphaned partial bytes for
 * `FAILED` rows. Both enqueues use `ExistingWorkPolicy.KEEP` so an in-flight
 * worker is never cancelled by a process restart.
 */
class DownloadRecoveryInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
) {
    suspend fun recover() {
        // Must run first: reconciliation resets truncated/missing completed
        // downloads to PENDING so recoverPendingDownloads() re-enqueues them
        // (KEEP policy) on the same pass, self-healing the offline library.
        reconcileCompletedDownloads()
        recoverPendingDownloads()
        cleanupStuckDownloads()
    }

    /**
     * Re-validates every `COMPLETED` download against the filesystem. A
     * transcoded/chunked stream that closed early (without throwing) could
     * previously be marked COMPLETED short of its true size; a file later
     * removed by Android media eviction or cache clearing would also leave a
     * stale COMPLETED row. Reset such rows to `PENDING` so they re-download.
     * Rows with an unknown size (`totalSizeBytes == 0`) and an existing file
     * are left untouched — we cannot verify them and they pre-date size
     * tracking, so resetting them would force needless re-downloads.
     */
    private suspend fun reconcileCompletedDownloads() {
        try {
            val completed = downloadDao.getCompletedForReconciliation()
            for (download in completed) {
                if (download.downloadPath.isBlank()) {
                    // No path recorded — nothing to verify against. Leave it;
                    // playback will fall back to streaming.
                    continue
                }
                val file = File(download.downloadPath)
                val actualSize = if (file.exists()) file.length() else 0L
                val expected = download.totalSizeBytes
                val truncatedOrMissing = expected > 0L && actualSize < expected
                if (truncatedOrMissing) {
                    if (file.exists()) file.delete()
                    downloadDao.updateProgress(download.id, 0L, DownloadStatus.PENDING.name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reconcile completed downloads", e)
        }
    }

    private suspend fun recoverPendingDownloads() {
        try {
            // KEEP (not REPLACE): the unique-work name is stable across process
            // restarts, so if the previous worker is still in-flight WorkManager
            // will keep it. Replacing here would cancel an active download and
            // risk orphaned partial bytes between the cancel and the new
            // worker's setForeground call.
            val pending = downloadDao.getRecoveryRows(DownloadStatus.PENDING.name)
            for (download in pending) {
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS,
                        TimeUnit.MILLISECONDS,
                    )
                    .setInputData(
                        Data.Builder()
                            .putString(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                            .build()
                    )
                    .addTag(DownloadWorker.WORK_TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    DownloadWorker.workName(download.id),
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
            // Both DOWNLOADING and QUEUED rows belong to workers that were
            // interrupted mid-flight (transferring or waiting on a concurrency
            // slot). Reset them to PENDING and re-enqueue so they resume from
            // their persisted byte offset.
            val stale = downloadDao.getRecoveryRows(DownloadStatus.DOWNLOADING.name) +
                downloadDao.getRecoveryRows(DownloadStatus.QUEUED.name)
            for (download in stale) {
                downloadDao.updateProgress(download.id, download.downloadedBytes, DownloadStatus.PENDING.name)
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS,
                        TimeUnit.MILLISECONDS,
                    )
                    .setInputData(
                        Data.Builder()
                            .putString(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                            .build()
                    )
                    .addTag(DownloadWorker.WORK_TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    DownloadWorker.workName(download.id),
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to recover pending downloads", e)
        }
    }

    private suspend fun cleanupStuckDownloads() {
        try {
            downloadDao.resetStuckDownloading()
            val failed = downloadDao.getFailedDownloads()
            for (download in failed) {
                if (download.downloadPath.isNotBlank()) {
                    val file = File(download.downloadPath)
                    // Delete partial files unconditionally. Multi-connection
                    // downloads use RandomAccessFile scattered writes that
                    // cannot be resumed (DownloadWorker deletes the partial
                    // file on cancel/failure for the same reason), so a non-
                    // zero FAILED file is wasted storage. The DB row stays
                    // FAILED so the user sees the failure in the UI and can
                    // retry manually. Previously only 0-byte files were
                    // deleted, which left e.g. a 50 MB file that failed at
                    // 80 % sitting on disk forever.
                    if (file.exists()) {
                        file.delete()
                    }
                    // Reset the byte offset to 0 so a later resume/retry can't
                    // send `Range: bytes=N-` against the now-deleted (or, for
                    // a multi-connection row, gapped) partial. The worker
                    // resumes solely on `downloadedBytes > 0`, so leaving the
                    // stale count would corrupt the output.
                    if (download.downloadedBytes > 0L) {
                        downloadDao.updateProgress(download.id, 0L, DownloadStatus.FAILED.name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup stuck downloads", e)
        }
    }

    companion object {
        private const val TAG = "DownloadRecovery"
    }
}
