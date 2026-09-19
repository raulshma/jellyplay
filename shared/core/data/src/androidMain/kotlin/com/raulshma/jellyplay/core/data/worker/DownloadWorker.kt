package com.raulshma.jellyplay.core.data.worker

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.DownloadStatus

/**
 * Downloads a single item identified by `KEY_DOWNLOAD_ID`. Resolves the runtime
 * context a transfer needs — concurrency-permit acquisition, foreground-service
 * promotion, access-token decryption, the single- vs multi-connection decision —
 * then delegates the actual byte transfer to [DownloadTransferRunner] (single
 * connection) or [MultiConnectionDownloadStrategy] (multi-connection).
 *
 * The transfer logic lives in the runner and the strategy, and both ride the
 * narrow [DownloadTransferClient] seam (one client plumbing for single- and
 * multi-connection), so the hot paths are unit-testable; this worker keeps
 * only the WorkManager-specific orchestration that can't move.
 *
 * **Why a thin worker shell.** Before extraction this file owned the 250-line
 * transfer method, the HTTP-status branches, and the integrity checks — all
 * welded to a concrete `OkHttpClient` and therefore untested. Pushing the
 * transfer into [DownloadTransferRunner] (which depends on the testable client
 * interface) leaves the worker with the irreducible `CoroutineWorker` concerns:
 * input, foreground, permit, token, branching. The failure-classification rule
 * is owned by `DownloadFailurePolicy` (thrown) / `decideForStatus` (HTTP status);
 * the gate's [DownloadTransferGate.execute] routes the outer catch there.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val dao: DownloadDao,
    private val userDao: UserDao,
    private val downloadsStore: DownloadsStore,
    private val serverIdentityStore: ServerIdentityStore,
    private val tokenCipher: TokenCipher,
    private val concurrencyLimiter: DownloadConcurrencyLimiter,
    private val transferClient: DownloadTransferClient,
) : CoroutineWorker(context, params) {

    /**
     * The shared transfer choreography (preamble + post-Prepare tail) —
     * extracted to [DownloadTransferGate] where DesktopDownloadManager runs
     * the identical code. The platform-supplied notification/stop lambdas are
     * passed at the runTransfer call site below, and the tail (HEAD probe,
     * resume vs fresh dispatch, outcome handling) runs INSIDE the gate's
     * permit via [DownloadTransferGate.execute] — `maxConcurrentDownloads`
     * must cap actual transfers, not just the preamble. The QUEUED write and
     * its foreground-promotion recovery stay in [doWork] (see the gate's
     * divergence list).
     */
    private val transferGate = DownloadTransferGate(
        dao = dao,
        userDao = userDao,
        serverIdentityStore = serverIdentityStore,
        downloadsStore = downloadsStore,
        tokenCipher = tokenCipher,
        concurrencyLimiter = concurrencyLimiter,
        transferClient = transferClient,
    )

    /**
     * Android's [DownloadTransferNotifications] adapter for the
     * multi-connection path: maps the strategy's notification calls onto
     * [DownloadNotificationHelper] + this worker's foreground surface. The
     * runner path's per-tick promotion keeps its own lambda at the runTransfer
     * call site; the start-of-transfer summary refresh BOTH paths now share
     * lives in [DownloadTransferGate.execute] (the declared heal of the former
     * runner-refreshes-multi-does-not divergence).
     */
    private inner class WorkerTransferNotifications : DownloadTransferNotifications {
        override suspend fun showForeground(
            downloadId: String,
            notificationId: Int,
            name: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
        ) {
            setForeground(
                DownloadNotificationHelper.createForegroundInfo(
                    applicationContext, downloadId, notificationId, name, progress,
                    downloadedBytes, totalBytes, speedBytesPerSec,
                )
            )
        }

        override fun updateNotification(
            downloadId: String,
            notificationId: Int,
            name: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
        ) {
            DownloadNotificationHelper.updateNotification(
                applicationContext, downloadId, notificationId, name, progress,
                downloadedBytes, totalBytes, speedBytesPerSec,
            )
        }

        override fun dismissNotification(notificationId: Int) {
            DownloadNotificationHelper.dismissNotification(applicationContext, notificationId)
        }

        override fun refreshSummary(inFlightCount: Int) {
            DownloadNotificationHelper.refreshSummary(applicationContext, inFlightCount)
        }
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()

        val entity = dao.getDownloadById(downloadId) ?: return Result.failure()

        if (DownloadStates.isInactive(entity.status)) {
            return Result.success()
        }

        // Keep the shared limiter sized to the user's preference.
        val maxConcurrent = downloadsStore.downloads.value.maxConcurrentDownloads
        concurrencyLimiter.configure(maxConcurrent)

        val notificationId = DownloadNotificationHelper.notificationIdFor(downloadId)
        val existingBytes = entity.downloadedBytes
        // Mark the row QUEUED while it waits for a concurrency slot so the UI
        // can show a distinct indicator instead of a stalled DOWNLOADING row.
        dao.updateProgress(downloadId, existingBytes, DownloadStatus.QUEUED.name)
        try {
            setForeground(
                DownloadNotificationHelper.createQueuedForegroundInfo(
                    applicationContext, downloadId, notificationId, entity.name,
                )
            )
            DownloadNotificationHelper.dismissPausedNotification(applicationContext, downloadId)
            DownloadNotificationHelper.refreshSummary(
                applicationContext, dao.getInFlightDownloadCount(),
            )
        } catch (e: Exception) {
            // On Android 12+ a background-launched worker cannot promote itself
            // to a foreground service. Continuing would let the OS kill the
            // worker within seconds (leaving the download "started but never
            // progressing"). Retry so WorkManager re-attempts when the app is
            // in a state that allows foreground promotion.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                return Result.retry()
            }
            // Other failures (e.g. notification permission missing on some
            // OEMs): fall through and attempt the download as a background
            // worker — best-effort.
        }

        val outcome = transferGate.runTransfer(
            downloadId = downloadId,
            existingBytes = existingBytes,
            isStopped = { isStopped },
            updateForeground = { name, progress, downloaded, total, speed, notifId ->
                setForeground(
                    DownloadNotificationHelper.createForegroundInfo(
                        applicationContext, downloadId, notifId, name, progress, downloaded, total, speed,
                    )
                )
                DownloadNotificationHelper.refreshSummary(
                    applicationContext, dao.getInFlightDownloadCount(),
                )
            },
            dismissForeground = { notifId ->
                DownloadNotificationHelper.dismissNotification(applicationContext, notifId)
                DownloadNotificationHelper.refreshSummary(
                    applicationContext, dao.getInFlightDownloadCount(),
                )
            },
            onInactive = { TransferOutcome.Success },
        ) { preparation ->
            transferGate.execute(
                preparation = preparation,
                entity = entity,
                existingBytes = existingBytes,
                notificationId = notificationId,
                notifications = WorkerTransferNotifications(),
            )
        }
        return outcome.toWorkResult()
    }

    /**
     * Maps the portable [TransferOutcome] the moved transfer engine returns
     * back onto the WorkManager result — the seam the desktop manager consumes
     * directly and Android adapts here (V3 downloads conveyor).
     */
    private fun TransferOutcome.toWorkResult(): Result = when (this) {
        TransferOutcome.Success -> Result.success()
        TransferOutcome.Retry -> Result.retry()
        TransferOutcome.Fail -> Result.failure()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val UNIQUE_WORK_PREFIX = "download_"
        const val WORK_TAG = "download"

        /**
         * The unique-work name for a download. Single source of truth for the
         * `"download_" + id` construction — every call site (this repo's enqueue/cancel,
         * DownloadRecoveryInitializer) must route through here so a rename never drifts
         * across modules.
         */
        fun workName(downloadId: String): String = "$UNIQUE_WORK_PREFIX$downloadId"
    }
}
