package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.data.repository.Outcome
import com.raulshma.jellyplay.core.data.repository.SIZE_MISMATCH_ERROR
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

/**
 * Owns the single-connection download transfer lifecycle end-to-end: content
 * probe (HEAD), HTTP-status policy dispatch (416 recovery / 401-403 / transient),
 * the throttled byte-transfer loop with DB-status polling, and the integrity
 * checks. Extracted out of [DownloadWorker] so the hot path — previously
 * untested because it was welded to a concrete `OkHttpClient` — depends on the
 * narrow [DownloadTransferClient] seam and is unit-testable with a fake.
 *
 * The worker's `doWork()` resolves the runtime bits (input, foreground
 * promotion, access token, concurrency permit) and hands off here; this module
 * owns everything from "I have a row + a permit" to "the file is on disk and
 * the row is COMPLETED/FAILED/PAUSED". Every failure path — thrown or HTTP
 * status — routes through [DownloadFailurePolicy], so there is one home for
 * "what does a failure do to the row?"
 *
 * **Parity table** — each inline branch in the old `performSingleConnectionDownload` /
 * `performDownload` maps to a policy outcome here (asserted by tests):
 *
 * | condition | old inline behaviour | now |
 * | --------- | -------------------- | --- |
 * | 416 stale range | reset to PENDING/0, re-issue without Range, retry on error | unchanged — 416 is a *recovery action*, stays in [transfer] |
 * | 401/403 | FAILED + SESSION_EXPIRED_ERROR, no retry | [DownloadFailurePolicy.decideForStatus] → MarkFailed(msg, retry=false) |
 * | other non-2xx | reset 0, delete partial, retry | decideForStatus → MarkFailed(null, delete, retry=true) |
 * | mid-transfer IOException | DownloadFailurePolicy.decide | unchanged |
 * | size mismatch | FAILED + SIZE_MISMATCH_ERROR, retry | MarkFailed(SIZE_MISMATCH_ERROR, retry=true) via [sizeMismatch] |
 * | 0-byte result | FAILED + SIZE_MISMATCH_ERROR, retry | [emptyBody] via sizeMismatch |
 *
 * **Not in scope here.** Multi-connection transfer stays in
 * [MultiConnectionDownloadStrategy] (it already routes throwables through the
 * policy); migrating it onto [DownloadTransferClient] is a follow-up. The byte,
 * notify, and poll cadence (64 KiB buffer, 2 s status-only poll) is reproduced
 * exactly from the pre-extraction loop — this is a behaviour-preserving move.
 *
 * V3 downloads conveyor: moved verbatim from the legacy :core:data shim (same
 * package). Transforms: `androidx.work.ListenableWorker.Result` returns became
 * [TransferOutcome] (mapped by the staying-legacy Android DownloadWorker back
 * onto WorkManager results); the `ForegroundInfo` import the file carried but
 * never used was dropped; visibility was `internal` in the legacy module and
 * is public since the move (same precedent as DownloadArtifacts /
 * Call.awaitResponse) because the staying-legacy DownloadWorker still
 * constructs it.
 */
class DownloadTransferRunner(
    private val dao: DownloadDao,
    private val client: DownloadTransferClient,
    private val isStopped: () -> Boolean,
    /**
     * Posts a foreground notification showing transfer progress. Built by the
     * caller (the worker) via [DownloadNotificationHelper]; injected so this
     * module has no `Context`/`NotificationManager` dependency and is testable
     * on a pure JVM. Failures are swallowed by the caller (best-effort notify).
     */
    private val updateForeground: suspend (name: String, progress: Int, downloaded: Long, total: Long, speed: Long, notificationId: Int) -> Unit,
    /** Cancels the progress notification on completion; best-effort, swallowed by the caller. */
    private val dismissForeground: suspend (notificationId: Int) -> Unit,
) {

    /**
     * Probes the authoritative content size for [url] via HEAD, used on both
     * resume (to catch truncated transcoded streams) and fresh starts (to
     * decide single- vs multi-connection and to size the integrity check).
     * Swallows all failures to 0 — a failed probe must not abort the transfer.
     */
    suspend fun probeContentSize(url: String, accessToken: String?): Long =
        runCatching {
            val response = client.execute(TransferRequest(url, head = true, accessToken = accessToken))
            val size = if (response.code in 200..299) response.totalSize ?: 0L else 0L
            response.close()
            size
        }.getOrDefault(0L)

    /**
     * Runs the single-connection transfer for [entity] (resume when
     * [existingBytes] > 0). Returns the portable [TransferOutcome]. The caller
     * owns the concurrency permit and has already set the row to DOWNLOADING.
     *
     * HTTP-status policy (except 416 recovery) and integrity failures route
     * through [DownloadFailurePolicy]; thrown transport errors route through
     * [DownloadFailurePolicy.decide]. The single-connection strategy is always
     * `isResumablePartial = true` (a contiguous prefix is a valid resume point).
     */
    suspend fun transfer(
        entity: DownloadEntity,
        existingBytes: Long,
        notificationId: Int,
        accessToken: String?,
        probedTotalSize: Long,
    ): TransferOutcome {
        val response = try {
            client.execute(
                TransferRequest(
                    url = entity.downloadUrl,
                    accessToken = accessToken,
                    range = if (existingBytes > 0L) "bytes=$existingBytes-" else null,
                )
            )
        } catch (e: IOException) {
            // Pre-body transport failure (request build / connect). No bytes
            // written this run, so madeProgress = false; existingBytes is what
            // the row held at start. Single-connection strategy.
            return routeThrowable(e, entity.id, madeProgress = false, preservedBytes = existingBytes, downloadPath = entity.downloadPath)
        }

        // 416 "stale range": the server can't honour our resume offset. Recover
        // by restarting from byte 0 without a Range header. NOT a policy
        // outcome — it's a retry-with-different-request, kept inline because it
        // needs to issue a second request and re-enter the happy path.
        if (response.code == 416) {
            response.close()
            dao.updateProgress(entity.id, 0L, DownloadStatus.PENDING.name)
            return recoverFromStaleRange(
                entity = entity,
                notificationId = notificationId,
                accessToken = accessToken,
                probedTotalSize = probedTotalSize,
            )
        }

        // Non-2xx (other than 416, handled above): a failure to classify.
        if (response.code !in 200..299) {
            response.close()
            val outcome = DownloadFailurePolicy.decideForStatus(
                responseCode = response.code,
                currentStatus = dao.getStatus(entity.id) ?: DownloadStatus.DOWNLOADING.name,
                isResumablePartial = true,
            )
            // applyAndRoute owns the file wipe + byte reset + error-message
            // clear: the transient branch (deletePartial = true) zeroes the row
            // AND deletes the partial in one place, so the row can never say
            // "N bytes present" while the file is gone (which would make the
            // next retry append a Range tail to a fresh file).
            return outcome.applyAndRoute(dao, entity.id, File(entity.downloadPath), existingBytes)
        }

        return performDownload(
            entity = entity,
            response = response,
            existingBytes = existingBytes,
            notificationId = notificationId,
            probedTotalSize = probedTotalSize,
        )
    }

    /**
     * The 416 recovery: re-issue the GET without a `Range` header and transfer
     * from byte 0. If the retry response is itself non-2xx, fail; any thrown
     * exception → FAILED + retry (preserves the old "don't burn the budget on
     * a loop" intent).
     */
    private suspend fun recoverFromStaleRange(
        entity: DownloadEntity,
        notificationId: Int,
        accessToken: String?,
        probedTotalSize: Long,
    ): TransferOutcome {
        return try {
            val retryResponse = client.execute(
                TransferRequest(url = entity.downloadUrl, accessToken = accessToken, range = null)
            )
            if (retryResponse.code !in 200..299) {
                dao.updateProgress(entity.id, 0L, DownloadStatus.FAILED.name)
                retryResponse.close()
                return TransferOutcome.Fail
            }
            performDownload(
                entity = entity,
                response = retryResponse,
                existingBytes = 0L,
                notificationId = notificationId,
                probedTotalSize = probedTotalSize,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dao.updateProgress(entity.id, 0L, DownloadStatus.FAILED.name)
            TransferOutcome.Retry
        }
    }

    /**
     * The throttled byte-transfer loop + integrity checks. Streams the response
     * body to the target file, polling the row's status column every
     * [PROGRESS_INTERVAL_MS] for a pause/cancel transition written by another
     * process, updating progress + speed + the foreground notification, then
     * verifying the final byte count.
     *
     * Reproduced exactly from the pre-extraction loop: 64 KiB buffer, 2 s
     * status-only poll (was a 23-col `SELECT *`), append on 206 / overwrite on
     * 200. The total-size derivation prefers the GET response's Content-Length
     * / Content-Range and falls back to the HEAD probe when the body is
     * chunked/transcoded — the fix for "downloads show finished but media
     * unavailable offline".
     */
    private suspend fun performDownload(
        entity: DownloadEntity,
        response: TransferResponse,
        existingBytes: Long,
        notificationId: Int,
        probedTotalSize: Long,
    ): TransferOutcome {
        val downloadId = entity.id
        val isPartial = response.code == 206

        val responseSize = response.totalSize
        val totalSize: Long = when {
            // 206 resume with a parseable Content-Range total: use it directly.
            isPartial && existingBytes > 0 && responseSize != null && responseSize > 0L -> responseSize
            // 206 resume without a usable total: best-effort existing + chunk length.
            isPartial && existingBytes > 0 -> responseSize?.let { existingBytes + it } ?: 0L
            // 200 fresh / probe: Content-Length, or 0 when unknown (chunked).
            else -> responseSize ?: 0L
        }

        // Authoritative size: prefer the GET response's Content-Length/Range,
        // fall back to the HEAD probe when the body is chunked/transcoded.
        val effectiveTotalSize = if (totalSize > 0L) totalSize else probedTotalSize

        if (effectiveTotalSize > 0L && existingBytes == 0L) {
            dao.updateTotalSize(downloadId, effectiveTotalSize)
        }

        val file = File(entity.downloadPath)
        file.parentFile?.mkdirs()

        val buffer = ByteArray(BUFFER_SIZE)
        var downloadedBytes = if (isPartial) existingBytes else 0L
        var lastProgressUpdate = System.currentTimeMillis()
        var lastSpeedBytes = downloadedBytes
        var speedBytesPerSec = 0L

        try {
            response.openBody().use { input ->
                val append = isPartial
                val output = (if (append) java.io.FileOutputStream(file, true) else file.outputStream())
                output.buffered().use { out ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped()) {
                            dao.updateProgress(downloadId, downloadedBytes, DownloadStatus.PAUSED.name)
                            response.close()
                            return TransferOutcome.Success
                        }

                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= PROGRESS_INTERVAL_MS) {
                            // Status-only poll (was a 23-col SELECT *) — detects
                            // a pause/cancel written by another process.
                            val currentStatus = dao.getStatus(downloadId)
                            if (currentStatus == null || DownloadStates.isInactive(currentStatus)) {
                                response.close()
                                return TransferOutcome.Success
                            }
                            val elapsed = now - lastProgressUpdate
                            val bytesDelta = downloadedBytes - lastSpeedBytes
                            speedBytesPerSec = if (elapsed > 0) (bytesDelta * 1000L) / elapsed else 0L
                            lastSpeedBytes = downloadedBytes
                            lastProgressUpdate = now

                            dao.updateProgressWithSpeed(
                                downloadId, downloadedBytes,
                                DownloadStatus.DOWNLOADING.name, speedBytesPerSec,
                            )

                            val progress = if (effectiveTotalSize > 0) {
                                (downloadedBytes * 100 / effectiveTotalSize).toInt()
                            } else 0
                            runCatching {
                                updateForeground(
                                    entity.name, progress, downloadedBytes,
                                    effectiveTotalSize, speedBytesPerSec, notificationId,
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            response.close()
            return routeThrowable(
                e,
                downloadId,
                madeProgress = downloadedBytes > existingBytes,
                preservedBytes = downloadedBytes,
                downloadPath = entity.downloadPath,
            )
        } catch (e: CancellationException) {
            response.close()
            throw e
        }

        response.close()

        dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING.name, 0L)

        // Integrity checks. When an authoritative size is known — from the GET
        // Content-Length/Range (ORIGINAL) or the HEAD probe (transcoded/chunked
        // bodies with no Content-Length) — verify the final byte count matches
        // exactly, so a server that closed early without an error still fails +
        // retries instead of shipping a truncated file as COMPLETED.
        if (effectiveTotalSize > 0L && downloadedBytes != effectiveTotalSize) {
            return sizeMismatch(downloadId, entity.downloadPath, downloadedBytes)
        }
        // Size unknown: a clean stream end is the only completion signal, so a
        // 0-byte / truncated response would otherwise be marked COMPLETED and
        // play as a corrupt file. Reject 0-byte results as FAILED + retry.
        if (downloadedBytes <= 0L) {
            return sizeMismatch(downloadId, entity.downloadPath, downloadedBytes)
        }

        dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.COMPLETED.name, 0L)
        dao.resetRetryCount(downloadId)
        dismissForeground(notificationId)
        return TransferOutcome.Success
    }

    /**
     * Routes a thrown [Throwable] through [DownloadFailurePolicy.decide], applies
     * the outcome to the row, and maps it to a [TransferOutcome]. The single
     * connection strategy is `isResumablePartial = true`, so the applicator
     * preserves [preservedBytes] (the contiguous prefix written so far).
     */
    private suspend fun routeThrowable(
        e: Throwable,
        downloadId: String,
        madeProgress: Boolean,
        preservedBytes: Long,
        downloadPath: String,
    ): TransferOutcome {
        val outcome = DownloadFailurePolicy.decide(
            error = e,
            madeProgress = madeProgress,
            currentStatus = dao.getStatus(downloadId) ?: DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        )
        return outcome.applyAndRoute(dao, downloadId, File(downloadPath), preservedBytes)
    }

    /**
     * Integrity-failure outcome: FAILED + [SIZE_MISMATCH_ERROR] + retry. Built
     * directly as a [Outcome.MarkFailed] because it's a fixed decision (not a
     * classification of a throwable or status code), then applied via the shared
     * applicator so the DAO write path stays single-sourced.
     */
    private suspend fun sizeMismatch(downloadId: String, downloadPath: String, downloadedBytes: Long): TransferOutcome {
        val outcome = Outcome.MarkFailed(
            errorMessage = SIZE_MISMATCH_ERROR,
            deletePartial = false, // single-connection partial is a valid resume prefix
            shouldRetry = true,
        )
        return outcome.applyAndRoute(dao, downloadId, File(downloadPath), downloadedBytes)
    }

    companion object {
        const val BUFFER_SIZE = 65536
        const val MIN_MULTI_SIZE = 2L * 1024 * 1024
        const val PROGRESS_INTERVAL_MS = 2000L
    }
}

/**
 * Applies [this] outcome to the single-connection row and maps it to a
 * [TransferOutcome] in one call — the portable equivalent of the legacy
 * `Outcome.applyAndRoute` androidx.work extension (which stays in the legacy
 * module for the Android worker's outer catch). File-private so it cannot
 * clash with the legacy module's same-named extension.
 */
private suspend fun Outcome.applyAndRoute(
    dao: DownloadDao,
    downloadId: String,
    partialFile: File,
    preservedBytes: Long,
): TransferOutcome {
    applyTo(dao, downloadId, partialFile, preservedBytes)
    return toTransferOutcome()
}
