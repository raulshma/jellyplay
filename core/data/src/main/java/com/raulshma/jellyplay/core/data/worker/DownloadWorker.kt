package com.raulshma.jellyplay.core.data.worker

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Named

/**
 * Downloads a single item identified by `KEY_DOWNLOAD_ID`. Resolves the runtime
 * context a transfer needs — concurrency-permit acquisition, foreground-service
 * promotion, access-token decryption, the single- vs multi-connection decision —
 * then delegates the actual byte transfer to [DownloadTransferRunner] (single
 * connection) or [MultiConnectionDownloadStrategy] (multi-connection).
 *
 * The transfer logic lives in the runner so the hot path is unit-testable via
 * the narrow [DownloadTransferClient] seam; this worker keeps only the
 * WorkManager-specific orchestration that can't move.
 *
 * **Why a thin worker shell.** Before extraction this file owned the 250-line
 * transfer method, the HTTP-status branches, and the integrity checks — all
 * welded to a concrete `OkHttpClient` and therefore untested. Pushing the
 * transfer into [DownloadTransferRunner] (which depends on the testable client
 * interface) leaves the worker with the irreducible `CoroutineWorker` concerns:
 * input, foreground, permit, token, branching. The failure-classification rule
 * is owned by `DownloadFailurePolicy` (thrown) / `decideForStatus` (HTTP status);
 * this worker's outer `catch (Throwable)` routes there too.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val userDao: UserDao,
    private val downloadsStore: DownloadsStore,
    private val serverIdentityStore: ServerIdentityStore,
    private val tokenCipher: TokenCipher,
    private val concurrencyLimiter: DownloadConcurrencyLimiter,
    private val transferClient: DownloadTransferClient,
    // Multi-connection path (MultiConnectionDownloadStrategy) still takes the
    // concrete OkHttpClient directly — migrating it onto DownloadTransferClient
    // is a follow-up. Kept here so the single/multi branching decision stays in
    // the worker; the single-connection path goes through transferClient.
    @Named("download") private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, params) {

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

        val activeUserId = serverIdentityStore.activeUserId.firstOrNull()
        val accessToken = activeUserId?.let { uid ->
            // Tokens are stored encrypted in Room. Decrypt before use as a Bearer-style
            // `X-Emby-Token` header value.
            tokenCipher.decrypt(userDao.getUserById(uid)?.accessToken)
        }

        val numConnections = downloadsStore.downloads.value.downloadConnections.coerceIn(1, 8)

        // Gate the actual transfer on a shared concurrency slot so at most
        // `maxConcurrentDownloads` run at once; the rest block here.
        return concurrencyLimiter.withPermit {
            // Re-check status now that a slot is ours: the user may have paused
            // or cancelled while the row was QUEUED.
            val statusAfterQueue = dao.getStatus(downloadId)
            if (DownloadStates.isInactive(statusAfterQueue)) {
                return@withPermit TransferOutcome.Success
            }
            dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)
            try {
                val runner = DownloadTransferRunner(
                    dao = dao,
                    client = transferClient,
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
                )
                if (existingBytes > 0L) {
                    // Resume: re-probe the authoritative size so the integrity
                    // check in the runner can catch a truncated stream. The
                    // resume path previously skipped the probe entirely (and
                    // updateTotalSize was skipped on resume), so a transcoded
                    // resume could complete short of the true size and ship a
                    // truncated file as COMPLETED.
                    val probedSize = runner.probeContentSize(entity.downloadUrl, accessToken)
                    runner.transfer(
                        entity = entity,
                        existingBytes = existingBytes,
                        notificationId = notificationId,
                        accessToken = accessToken,
                        probedTotalSize = probedSize,
                    )
                } else {
                    val totalSize = runner.probeContentSize(entity.downloadUrl, accessToken)
                    if (totalSize > DownloadTransferRunner.MIN_MULTI_SIZE && numConnections > 1) {
                        MultiConnectionDownloadStrategy.execute(
                            downloadClient = okHttpClient, // multi-conn path still on OkHttp (follow-up)
                            dao = dao,
                            downloadId = downloadId,
                            entity = entity,
                            totalSize = totalSize,
                            numConnections = numConnections,
                            notificationId = notificationId,
                            accessToken = accessToken,
                            notifications = object : DownloadTransferNotifications {
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
                            },
                        )
                    } else {
                        runner.transfer(
                            entity = entity,
                            existingBytes = 0L,
                            notificationId = notificationId,
                            accessToken = accessToken,
                            probedTotalSize = totalSize,
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Structured-concurrency control signal, not a download failure —
                // the parent scope (user navigated away, app dying) was cancelled.
                // Must propagate; never turn into Result.failure (would break
                // cancellation and burn a worker slot on dead work).
                throw e
            } catch (e: Throwable) {
                // Single home for the failure-classification rule: DownloadFailurePolicy.
                // Pre-body failures (HEAD probe, request build) wrote nothing this run,
                // so madeProgress = false; existingBytes is what the row held at start.
                val row = dao.getDownloadById(downloadId)
                val status = row?.status ?: DownloadStatus.PENDING.name
                val outcome = DownloadFailurePolicy.decide(
                    error = e,
                    madeProgress = false,
                    currentStatus = status,
                    isResumablePartial = true, // single-connection strategy for the outer path
                )
                outcome.applyTo(dao, downloadId, File(entity.downloadPath), existingBytes)
                outcome.toTransferOutcome()
            }
        }.toWorkResult()
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
