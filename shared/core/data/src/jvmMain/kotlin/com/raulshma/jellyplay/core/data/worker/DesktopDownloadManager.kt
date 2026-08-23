package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop download engine (V3 downloads conveyor): the in-process coroutine
 * replacement for Android's WorkManager + [DownloadWorker] orchestration.
 * Where Android enqueues a unique worker per download, desktop runs one
 * supervisor on the application scope that
 *
 *  - observes `PENDING` [DownloadDao] rows (the repository's `enqueueDownload`
 *    routes here through the [DownloadEnqueueCoordinator] seam — [enqueue] is
 *    the kick),
 *  - caps concurrency via the shared [DownloadConcurrencyLimiter] sized from
 *    `DownloadsStore.maxConcurrentDownloads`,
 *  - executes transfers with the ported [DownloadTransferRunner] — branching to
 *    [MultiConnectionDownloadStrategy] when the HEAD probe exceeds
 *    [DownloadTransferRunner.MIN_MULTI_SIZE] and `downloadConnections > 1`,
 *    exactly like the Android worker's branch,
 *  - mirrors the worker's per-row lifecycle (QUEUED → DOWNLOADING →
 *    COMPLETED/FAILED/PAUSED, resume via `Range:` from persisted bytes,
 *    failure classification through [DownloadFailurePolicy]),
 *  - cooperatively stops an in-flight transfer on [cancelWork] (the repository's
 *    pause/cancel call the seam; the runner notices the stop flag at its next
 *    buffer read and marks the row PAUSED — the WorkManager-cancel equivalent),
 *  - resumes interrupted downloads on start and on the Offline/Local → Online
 *    reconnect edge (the [com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener]
 *    semantics), and
 *  - re-kicks retryable outcomes after the shared
 *    [DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS] backoff (WorkManager's
 *    exponential retry equivalent, capped at 5 h like WorkManager).
 *
 * Cold-start recovery mirrors the Android DownloadRecoveryInitializer's stuck
 * -row pass: rows left DOWNLOADING/QUEUED by a dead process are reset to
 * PENDING (bytes preserved) and picked up by the observer.
 *
 * Construction is side-effect free; [start] launches the loops and is
 * idempotent across callers.
 */
class DesktopDownloadManager(
    private val downloadDao: DownloadDao,
    private val userDao: UserDao,
    private val downloadsStore: DownloadsStore,
    private val serverIdentityStore: ServerIdentityStore,
    private val tokenCipher: TokenCipher,
    private val concurrencyLimiter: DownloadConcurrencyLimiter,
    private val transferClient: DownloadTransferClient,
    private val downloadHttpClient: OkHttpClient,
    /**
     * Lazy to break the construction cycle this manager sits inside: the
     * repository's coordinator seam IS this manager, so an eager
     * DownloadRepository param would re-enter the repository single's own
     * construction (`DownloadRepositoryImpl → DownloadEnqueueCoordinator →
     * DesktopDownloadManager → DownloadRepositoryImpl`). Memoizing
     * kotlin.Lazy defers resolution to the first resume pass — the same
     * pattern the repository itself uses for its DownloadDelegate edge.
     */
    private val downloadRepository: Lazy<DownloadRepository>,
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    /** The process-wide application scope (DatastoreQualifiers.applicationScope in Koin). */
    private val scope: CoroutineScope,
) : DownloadEnqueueCoordinator {

    /** Cooperative stop flag for one in-flight transfer (WorkManager's isStopped equivalent). */
    private class TransferHandle {
        val stopped = AtomicBoolean(false)
    }

    private val activeTransfers = ConcurrentHashMap<String, TransferHandle>()
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val retryJobs = ConcurrentHashMap<String, Job>()
    private var loopJob: Job? = null
    private var reconnectJob: Job? = null

    // ── DownloadEnqueueCoordinator (the repository's enqueue/cancel seam) ───

    /** Kicks processing for [downloadId] — no-op when a transfer is already active. */
    override fun enqueue(downloadId: String) {
        kick(downloadId)
    }

    /**
     * Cooperatively stops any in-flight transfer and scheduled retry for
     * [downloadId]. The runner marks the row PAUSED at its next buffer read;
     * the repository's pause/cancel DAO writes then land as the source of
     * truth (same ordering as the Android WorkManager cancel).
     */
    override fun cancelWork(downloadId: String) {
        retryAttempts.remove(downloadId)
        retryJobs.remove(downloadId)?.cancel()
        activeTransfers[downloadId]?.stopped?.set(true)
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    /** Starts the observer + reconnect loops. Idempotent. */
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            // Cold-start recovery mirror: rows a dead process left mid-flight
            // are reset to PENDING (bytes preserved so the transfer resumes
            // from its persisted offset). The observer below picks them up.
            runCatching { recoverStaleRows() }
                .onFailure { Log.w(TAG, "Failed to recover stale download rows", it) }
            // One resume pass at start (PAUSED/NETWORK + FAILED rows past the
            // eligibility rules; safe no-op when nothing qualifies).
            runCatching { downloadRepository.value.resumeInterruptedDownloads() }
                .onFailure { Log.w(TAG, "Startup resume of interrupted downloads failed", it) }
            downloadDao.getPendingDownloads()
                .map { rows -> rows.filter { it.status == DownloadStatus.PENDING.name } }
                .collect { pending ->
                    pending.forEach { kick(it.id) }
                }
        }
        reconnectJob = scope.launch { watchReconnect() }
    }

    /** Cancels the loops and in-flight transfers (process shutdown / tests). */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        activeTransfers.values.forEach { it.stopped.set(true) }
    }

    /**
     * Offline/Local → Online edge detection over both the network status and
     * the app-level offline mode (the legacy DownloadReconnectListener /
     * ReconnectTrigger semantics: a manual offline-mode toggle back online must
     * also fire). On the edge, eligible interrupted downloads are resumed by
     * the repository, which re-enqueues through this coordinator.
     */
    private suspend fun watchReconnect() {
        var wasReady = isReady(
            networkMonitor.networkStatus.value,
            offlineModeManager.offlineMode.value,
        )
        combine(
            networkMonitor.networkStatus,
            offlineModeManager.offlineMode,
        ) { networkStatus, offlineMode -> isReady(networkStatus, offlineMode) }
            .collect { ready ->
                if (ready && !wasReady) {
                    runCatching { downloadRepository.value.resumeInterruptedDownloads() }
                        .onFailure { Log.w(TAG, "Reconnect resume of interrupted downloads failed", it) }
                }
                wasReady = ready
            }
    }

    private fun isReady(networkStatus: NetworkStatus, offlineMode: OfflineMode): Boolean =
        networkStatus == NetworkStatus.Online && offlineMode == OfflineMode.ONLINE

    // ── transfer orchestration (mirrors DownloadWorker.doWork) ──────────────

    private fun kick(downloadId: String) {
        // Reserve the slot atomically BEFORE launching: the pending-rows
        // observer re-emits on every download-table change (each 2 s progress
        // tick of any other row) and re-kicks every still-PENDING row, so a
        // concurrent kick must see the reservation even before processRow
        // performs its first DB read. putIfAbsent is the ExistingWorkPolicy
        // .KEEP equivalent — only one transfer per row, ever.
        val handle = TransferHandle()
        if (activeTransfers.putIfAbsent(downloadId, handle) != null) return
        scope.launch { processRow(downloadId, handle) }
    }

    private suspend fun recoverStaleRows() {
        val stale = downloadDao.getRecoveryRows(DownloadStatus.DOWNLOADING.name) +
            downloadDao.getRecoveryRows(DownloadStatus.QUEUED.name)
        for (row in stale) {
            downloadDao.updateProgress(row.id, row.downloadedBytes, DownloadStatus.PENDING.name)
        }
    }

    private suspend fun processRow(downloadId: String, handle: TransferHandle) {
        // The handle was registered by kick(); every exit path — including the
        // early returns below and cancellation — must release it or the row
        // could never be re-kicked.
        try {
            val entity = downloadDao.getDownloadById(downloadId) ?: return

            if (DownloadStates.isInactive(entity.status)) return

            // Keep the shared limiter sized to the user's preference.
            val maxConcurrent = downloadsStore.downloads.value.maxConcurrentDownloads
            concurrencyLimiter.configure(maxConcurrent)

            // Desktop has no notification ids; a stable dummy keeps the moved
            // machinery (which threads one through) unchanged.
            val notificationId = 0
            val existingBytes = entity.downloadedBytes
            // Mark the row QUEUED while it waits for a concurrency slot so the UI
            // can show a distinct indicator instead of a stalled DOWNLOADING row.
            downloadDao.updateProgress(downloadId, existingBytes, DownloadStatus.QUEUED.name)

            val activeUserId = serverIdentityStore.activeUserId.firstOrNull()
            val accessToken = activeUserId?.let { uid ->
                // Tokens are stored encrypted in Room. Decrypt before use as a Bearer-style
                // `X-Emby-Token` header value.
                tokenCipher.decrypt(userDao.getUserById(uid)?.accessToken)
            }

            val numConnections = downloadsStore.downloads.value.downloadConnections.coerceIn(1, 8)

            // Gate the actual transfer on a shared concurrency slot so at most
            // `maxConcurrentDownloads` run at once; the rest block here.
            concurrencyLimiter.withPermit {
                // Re-check status now that a slot is ours: the user may have
                // paused or cancelled while the row was QUEUED.
                val statusAfterQueue = downloadDao.getStatus(downloadId)
                if (DownloadStates.isInactive(statusAfterQueue)) {
                    return@withPermit
                }
                downloadDao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)
                try {
                    val runner = DownloadTransferRunner(
                        dao = downloadDao,
                        client = transferClient,
                        isStopped = { handle.stopped.get() },
                        updateForeground = { _, _, _, _, _, _ -> /* no foreground surface on desktop */ },
                        dismissForeground = { /* no foreground surface on desktop */ },
                    )
                    val outcome: TransferOutcome
                    if (existingBytes > 0L) {
                        // Resume: re-probe the authoritative size so the
                        // integrity check in the runner can catch a truncated
                        // stream (same rationale as the Android worker).
                        val probedSize = runner.probeContentSize(entity.downloadUrl, accessToken)
                        outcome = runner.transfer(
                            entity = entity,
                            existingBytes = existingBytes,
                            notificationId = notificationId,
                            accessToken = accessToken,
                            probedTotalSize = probedSize,
                        )
                    } else {
                        val totalSize = runner.probeContentSize(entity.downloadUrl, accessToken)
                        outcome = if (totalSize > DownloadTransferRunner.MIN_MULTI_SIZE && numConnections > 1) {
                            MultiConnectionDownloadStrategy.execute(
                                downloadClient = downloadHttpClient,
                                dao = downloadDao,
                                downloadId = downloadId,
                                entity = entity,
                                totalSize = totalSize,
                                numConnections = numConnections,
                                notificationId = notificationId,
                                accessToken = accessToken,
                                notifications = DesktopTransferNotifications,
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
                    if (outcome == TransferOutcome.Retry) {
                        scheduleRetry(downloadId)
                    } else if (outcome == TransferOutcome.Success) {
                        retryAttempts.remove(downloadId)
                    }
                } catch (e: CancellationException) {
                    // Structured-concurrency control signal, not a download
                    // failure — must propagate (same rule as the worker).
                    throw e
                } catch (e: Throwable) {
                    // Single home for the failure-classification rule:
                    // DownloadFailurePolicy. Pre-body failures (HEAD probe,
                    // request build) wrote nothing this run.
                    val row = downloadDao.getDownloadById(downloadId)
                    val status = row?.status ?: DownloadStatus.PENDING.name
                    val outcome = DownloadFailurePolicy.decide(
                        error = e,
                        madeProgress = false,
                        currentStatus = status,
                        isResumablePartial = true, // single-connection strategy for the outer path
                    )
                    outcome.applyTo(downloadDao, downloadId, File(entity.downloadPath), existingBytes)
                    if (outcome.shouldRetry) scheduleRetry(downloadId)
                }
            }
        } finally {
            activeTransfers.remove(downloadId)
        }
    }

    /**
     * WorkManager's `Result.retry()` equivalent: re-kick after the shared
     * backoff base, doubling per attempt and capping at 5 h (WorkManager's
     * retry-delay cap). A row the user paused/cancelled meanwhile is skipped
     * by the re-kick's own status checks.
     */
    private fun scheduleRetry(downloadId: String) {
        val attempt = retryAttempts.getOrDefault(downloadId, 0)
        retryAttempts[downloadId] = attempt + 1
        val backoffMs = minOf(
            DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS shl attempt.coerceAtMost(16),
            MAX_BACKOFF_MS,
        )
        retryJobs.remove(downloadId)?.cancel()
        retryJobs[downloadId] = scope.launch {
            delay(backoffMs)
            retryJobs.remove(downloadId)
            kick(downloadId)
        }
    }

    private companion object {
        const val TAG = "DesktopDownloads"

        /** WorkManager caps each retry delay at 5 h; mirror that ceiling. */
        const val MAX_BACKOFF_MS = 5L * 60 * 60 * 1000
    }
}

/**
 * Desktop no-op for the multi-connection strategy's notification seam —
 * desktop has no shade/foreground surface; progress is visible through the DB
 * -backed UI flows the runner already updates.
 */
private object DesktopTransferNotifications : DownloadTransferNotifications {
    override suspend fun showForeground(
        downloadId: String,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) = Unit

    override fun updateNotification(
        downloadId: String,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) = Unit

    override fun dismissNotification(notificationId: Int) = Unit

    override fun refreshSummary(inFlightCount: Int) = Unit
}
