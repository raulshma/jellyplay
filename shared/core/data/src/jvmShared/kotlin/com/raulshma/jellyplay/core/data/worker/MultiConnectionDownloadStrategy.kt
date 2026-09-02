package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.data.repository.Outcome
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Platform notification/foreground surface driven by the multi-connection
 * transfer strategy (V3 downloads conveyor). Replaces the direct
 * `DownloadNotificationHelper` + `setForeground(ForegroundInfo)` calls the
 * legacy module made — both Android-only surfaces. The Android worker supplies
 * an adapter over the helper; the desktop manager supplies a no-op (or log)
 * implementation.
 */
interface DownloadTransferNotifications {

    /**
     * Promotes the transfer to the platform's foreground/progress surface at
     * [progress] (0 on start). Suspends like the Android worker's
     * `setForeground` — strategy call sites wrap it best-effort.
     */
    suspend fun showForeground(
        downloadId: String,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    )

    /** Fire-and-forget progress update from the 2 s ticker. Best-effort. */
    fun updateNotification(
        downloadId: String,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    )

    /** Dismisses the per-transfer progress surface. Best-effort. */
    fun dismissNotification(notificationId: Int)

    /** Refreshes the collapsed summary surface with [inFlightCount] active rows. */
    fun refreshSummary(inFlightCount: Int)
}

/**
 * Multi-connection download strategy: splits the content into N byte ranges
 * and transfers them concurrently via `Range` requests, scattering the bytes
 * into a single [RandomAccessFile]. Extracted out of [DownloadWorker] so the
 * worker orchestrates the overall flow instead of owning every transfer
 * strategy.
 *
 * Cancel/pause detection mirrors the original inline implementation: the
 * progress loop polls the DB row's status (PAUSED/CANCELLED) and signals the
 * chunk coroutines via a shared [AtomicBoolean].
 *
 * V3 downloads conveyor: moved verbatim from the legacy :core:data shim (same
 * package). Transforms: the `Context` + `setForegroundInfo(ForegroundInfo)`
 * params collapsed into the [DownloadTransferNotifications] seam;
 * `androidx.work.ListenableWorker.Result` returns became [TransferOutcome];
 * `android.util.Log` routes through the module's Log facade. Chunk requests
 * ride the same [DownloadTransferClient] seam as [DownloadTransferRunner]
 * (both paths share one underlying OkHttp client through the production
 * adapter — this is a seam/testability decoupling, not a client swap).
 * Visibility was `internal` in the legacy module and is public since the move
 * (same precedent as DownloadArtifacts / Call.awaitResponse) because the
 * staying-legacy DownloadWorker still calls it.
 */
object MultiConnectionDownloadStrategy {

    private const val BUFFER_SIZE = 65536
    private const val PROGRESS_UPDATE_INTERVAL_MS = 2000L

    suspend fun execute(
        downloadClient: DownloadTransferClient,
        dao: DownloadDao,
        downloadId: String,
        entity: DownloadEntity,
        totalSize: Long,
        numConnections: Int,
        notificationId: Int,
        accessToken: String?,
        notifications: DownloadTransferNotifications,
    ): TransferOutcome {
        dao.updateTotalSize(downloadId, totalSize)

        val file = File(entity.downloadPath)
        file.parentFile?.mkdirs()

        val chunkSize = totalSize / numConnections
        val chunks = (0 until numConnections).map { i ->
            val start = i * chunkSize
            val end = if (i == numConnections - 1) totalSize - 1 else (i + 1) * chunkSize - 1
            ChunkInfo(i, start, end)
        }

        val totalDownloaded = AtomicLong(0L)
        val cancelled = AtomicBoolean(false)

        try {
            notifications.showForeground(
                downloadId, notificationId, entity.name, 0, 0L, totalSize, 0L,
            )
            notifications.refreshSummary(dao.getInFlightDownloadCount())
        } catch (_: Exception) {
        }

        return try {
            coroutineScope {
                val progressJob = launch(Dispatchers.Default) {
                    var lastProgressUpdate = System.currentTimeMillis()
                    var lastSpeedBytes = 0L
                    var speedBytesPerSec = 0L
                    delay(PROGRESS_UPDATE_INTERVAL_MS)
                    while (isActive && !cancelled.get()) {
                        val now = System.currentTimeMillis()
                        val currentDownloaded = totalDownloaded.get()
                        // Only the status is consumed — the projected query
                        // avoids deserializing the full 23-column entity per
                        // tick (same as DownloadTransferRunner).
                        val status = dao.getStatus(downloadId)
                        if (status == null || DownloadStates.isInactive(status)) {
                            cancelled.set(true)
                            break
                        }
                        val elapsed = now - lastProgressUpdate
                        val bytesDelta = currentDownloaded - lastSpeedBytes
                        speedBytesPerSec = if (elapsed > 0) (bytesDelta * 1000L) / elapsed else 0L
                        lastSpeedBytes = currentDownloaded
                        lastProgressUpdate = now

                        dao.updateProgressWithSpeed(
                            downloadId, currentDownloaded,
                            DownloadStatus.DOWNLOADING.name, speedBytesPerSec,
                        )

                        val progress = if (totalSize > 0) {
                            (currentDownloaded * 100 / totalSize).toInt()
                        } else 0
                        notifications.updateNotification(
                            downloadId, notificationId, entity.name, progress,
                            currentDownloaded, totalSize, speedBytesPerSec,
                        )
                        // Per-download notification only from this worker; the
                        // shared summary is refreshed on lifecycle transitions
                        // (start/complete) by DownloadWorker — with N concurrent
                        // workers each re-posting the identical summary on its
                        // own tick, this was N redundant notify() + count
                        // queries per 2 s window.

                        delay(PROGRESS_UPDATE_INTERVAL_MS)
                    }
                }

                try {
                    val jobs = chunks.map { chunk ->
                        async(Dispatchers.IO) {
                            downloadChunk(
                                downloadClient = downloadClient,
                                url = entity.downloadUrl,
                                chunk = chunk,
                                file = file,
                                totalDownloaded = totalDownloaded,
                                cancelled = cancelled,
                                accessToken = accessToken,
                            )
                        }
                    }
                    jobs.awaitAll()
                } finally {
                    progressJob.cancel()
                }
            }

            if (cancelled.get()) {
                val currentEntity = dao.getDownloadById(downloadId)
                val cancelStatus = if (currentEntity == null || currentEntity.status == DownloadStatus.CANCELLED.name) {
                    DownloadStatus.CANCELLED.name
                } else {
                    DownloadStatus.PAUSED.name
                }
                // Multi-connection writes bytes at scattered offsets via
                // RandomAccessFile.seek(); the cumulative byte count is NOT a
                // valid resumable prefix. Delete the partial and reset bytes
                // to 0 so the next attempt starts fresh (a single-connection
                // resume would otherwise append to a gapped file and corrupt it).
                runCatching { if (file.exists()) file.delete() }
                    .onFailure { Log.w("DownloadWorker", "Failed to delete corrupt partial", it) }
                dao.updateProgressWithSpeed(downloadId, 0L, cancelStatus, 0L)
                return TransferOutcome.Success
            }

            val finalBytes = totalDownloaded.get()
            if (totalSize > 0L && finalBytes < totalSize) {
                runCatching { if (file.exists()) file.delete() }
                    .onFailure { Log.w("DownloadWorker", "Failed to delete incomplete partial", it) }
                dao.updateErrorMessage(downloadId, "Download incomplete")
                dao.updateProgressWithSpeed(downloadId, 0L, DownloadStatus.FAILED.name, 0L)
                return TransferOutcome.Retry
            }

            dao.updateErrorMessage(downloadId, null)
            dao.updateProgressWithSpeed(downloadId, finalBytes, DownloadStatus.COMPLETED.name, 0L)
            // A successful download clears the auto-retry budget.
            dao.resetRetryCount(downloadId)
            notifications.dismissNotification(notificationId)
            notifications.refreshSummary(dao.getInFlightDownloadCount())
            TransferOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Multi-connection partials are never resumable (scattered
            // RandomAccessFile offsets), so isResumablePartial = false and the
            // applicator deletes the partial + resets bytes on every outcome.
            val outcome = DownloadFailurePolicy.decide(
                error = e,
                madeProgress = totalDownloaded.get() > 0,
                currentStatus = dao.getDownloadById(downloadId)?.status ?: DownloadStatus.DOWNLOADING.name,
                isResumablePartial = false,
            )
            // Override the default error message with the richer per-exception
            // mapping this strategy already owned.
            val resolved = if (outcome is Outcome.MarkFailed && outcome.errorMessage != null) {
                outcome.copy(errorMessage = failureMessage(e))
            } else outcome
            resolved.applyTo(dao, downloadId, file)
            resolved.toTransferOutcome()
        }
    }

    private fun failureMessage(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "Network timed out"
        is java.net.UnknownHostException -> "Cannot reach server"
        is javax.net.ssl.SSLException -> "Network security error"
        is IOException -> "Network error"
        else -> "Download failed"
    }

    /**
     * Transfers one `[start, end]` byte range into [file] at its scattered
     * offset. Rides the [DownloadTransferClient] seam: the adapter emits the
     * same headers this method used to hand-build (User-Agent JellyPlay/1.0.0,
     * `Range: bytes=start-end`, `X-Emby-Token` when the token is non-blank), so
     * only the response handling maps onto [TransferResponse]. Runs on
     * [Dispatchers.IO]; `execute` suspends instead of the old blocking
     * `Call.execute()` (cancellation-collapsible) and the per-buffer-read
     * [cancelled] check is kept exactly.
     */
    private suspend fun downloadChunk(
        downloadClient: DownloadTransferClient,
        url: String,
        chunk: ChunkInfo,
        file: File,
        totalDownloaded: AtomicLong,
        cancelled: AtomicBoolean,
        accessToken: String?,
    ) {
        val response = downloadClient.execute(
            TransferRequest(
                url = url,
                accessToken = accessToken,
                range = "bytes=${chunk.start}-${chunk.end}",
            )
        )

        // 206 (Range honoured) or 200 (server ignored Range) both deliver the
        // chunk bytes; anything else — including the 416 a single-connection
        // resume recovers from — fails this chunk. No 416 recovery here.
        if (response.code != 206 && response.code != 200) {
            response.close()
            throw IOException("Chunk ${chunk.index} failed with code ${response.code}")
        }

        val body: InputStream = try {
            response.openBody()
        } catch (e: IOException) {
            throw IOException("Chunk ${chunk.index} has no body", e)
        }

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(chunk.start)
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                body.buffered().use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancelled.get()) {
                            response.close()
                            return
                        }
                        raf.write(buffer, 0, bytesRead)
                        totalDownloaded.addAndGet(bytesRead.toLong())
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private data class ChunkInfo(
        val index: Int,
        val start: Long,
        val end: Long,
    )
}
