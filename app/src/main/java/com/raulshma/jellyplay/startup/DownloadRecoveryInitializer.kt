package com.raulshma.jellyplay.startup

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
        recoverPendingDownloads()
        cleanupStuckDownloads()
    }

    private suspend fun recoverPendingDownloads() {
        try {
            // KEEP (not REPLACE): the unique-work name is stable across process
            // restarts, so if the previous worker is still in-flight WorkManager
            // will keep it. Replacing here would cancel an active download and
            // risk orphaned partial bytes between the cancel and the new
            // worker's setForeground call.
            val pending = downloadDao.getDownloadsByStatus(DownloadStatus.PENDING.name)
            for (download in pending) {
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                            .build()
                    )
                    .addTag(DownloadWorker.WORK_TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "${DownloadWorker.UNIQUE_WORK_PREFIX}${download.id}",
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
            val stale = downloadDao.getDownloadsByStatus(DownloadStatus.DOWNLOADING.name)
            for (download in stale) {
                downloadDao.updateProgress(download.id, download.downloadedBytes, DownloadStatus.PENDING.name)
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                            .build()
                    )
                    .addTag(DownloadWorker.WORK_TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "${DownloadWorker.UNIQUE_WORK_PREFIX}${download.id}",
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
