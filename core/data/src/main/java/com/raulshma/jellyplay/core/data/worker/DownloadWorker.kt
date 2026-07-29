package com.raulshma.jellyplay.core.data.worker

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.data.repository.toWorkResult
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Named

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val userDao: UserDao,
    private val preferencesStore: UserPreferencesStore,
    // Pre-tuned singleton (connect=30s, read=60s, write=30s) shared across
    // all concurrent DownloadWorker invocations. Previously each doWork()
    // call cloned the base client via newBuilder().build(), multiplying the
    // interceptor-list allocation when several workers ran at once.
    @Named("download") private val client: OkHttpClient,
    private val tokenCipher: TokenCipher,
    private val concurrencyLimiter: DownloadConcurrencyLimiter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()

        val entity = dao.getDownloadById(downloadId) ?: return Result.failure()

        if (DownloadStates.isInactive(entity.status)) {
            return Result.success()
        }

        // Keep the shared limiter sized to the user's preference.
        val maxConcurrent = preferencesStore.preferences.firstOrNull()?.maxConcurrentDownloads
            ?: DownloadConcurrencyLimiter.DEFAULT_MAX
        concurrencyLimiter.configure(maxConcurrent)

        val notificationId = downloadId.hashCode() and 0x7FFFFFFF
        try {
            setForeground(
                DownloadNotificationHelper.createForegroundInfo(
                    applicationContext, notificationId, entity.name, 0, 0L, entity.totalSizeBytes, 0L,
                )
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

        val existingBytes = entity.downloadedBytes
        // Mark the row QUEUED while it waits for a concurrency slot so the UI
        // can show a distinct indicator instead of a stalled DOWNLOADING row.
        dao.updateProgress(downloadId, existingBytes, DownloadStatus.QUEUED.name)

        val activeUserId = preferencesStore.activeUserId.firstOrNull()
        val accessToken = activeUserId?.let { uid ->
            // Tokens are stored encrypted in Room. Decrypt before use as a Bearer-style
            // `X-Emby-Token` header value.
            tokenCipher.decrypt(userDao.getUserById(uid)?.accessToken)
        }

        val numConnections = preferencesStore.preferences.firstOrNull()?.downloadConnections?.coerceIn(1, 8) ?: 1

        // Gate the actual transfer on a shared concurrency slot so at most
        // `maxConcurrentDownloads` run at once; the rest block here.
        return concurrencyLimiter.withPermit {
            // Re-check status now that a slot is ours: the user may have paused
            // or cancelled while the row was QUEUED.
            val statusAfterQueue = dao.getStatus(downloadId)
            if (DownloadStates.isInactive(statusAfterQueue)) {
                return@withPermit Result.success()
            }
            dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)
            try {
            if (existingBytes > 0L) {
                // Resume: re-probe the authoritative size so the integrity
                // check in performDownload can catch a truncated stream. The
                // resume path previously skipped the probe entirely (and
                // updateTotalSize was skipped on resume), so a transcoded
                // resume could complete short of the true size and ship a
                // truncated file as COMPLETED.
                val probedSize = probeContentSize(client, entity.downloadUrl, accessToken)
                performSingleConnectionDownload(
                    downloadClient = client,
                    dao = dao,
                    downloadId = downloadId,
                    entity = entity,
                    existingBytes = existingBytes,
                    notificationId = notificationId,
                    accessToken = accessToken,
                    probedTotalSize = probedSize,
                )
            } else {
                val totalSize = probeContentSize(client, entity.downloadUrl, accessToken)
                if (totalSize > MIN_MULTI_SIZE && numConnections > 1) {
                    MultiConnectionDownloadStrategy.execute(
                        context = applicationContext,
                        downloadClient = client,
                        dao = dao,
                        downloadId = downloadId,
                        entity = entity,
                        totalSize = totalSize,
                        numConnections = numConnections,
                        notificationId = notificationId,
                        accessToken = accessToken,
                        setForegroundInfo = { info -> setForeground(info) },
                    )
                } else {
                    performSingleConnectionDownload(
                        downloadClient = client,
                        dao = dao,
                        downloadId = downloadId,
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
            outcome.applyTo(dao, downloadId, existingBytes)
            outcome.toWorkResult()
        }
        }
    }

    private suspend fun probeContentSize(
        downloadClient: OkHttpClient,
        url: String,
        accessToken: String?,
    ): Long {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "JellyPlay/1.0.0")
            if (!accessToken.isNullOrBlank()) {
                requestBuilder.header("X-Emby-Token", accessToken)
            }
            val response = downloadClient.newCall(requestBuilder.build()).awaitResponse()
            val size = if (response.isSuccessful) {
                response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
            } else {
                0L
            }
            response.close()
            size
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun performSingleConnectionDownload(
        downloadClient: OkHttpClient,
        dao: DownloadDao,
        downloadId: String,
        entity: com.raulshma.jellyplay.core.database.entity.DownloadEntity,
        existingBytes: Long,
        notificationId: Int,
        accessToken: String?,
        probedTotalSize: Long = 0L,
    ): Result {
        val requestBuilder = Request.Builder()
            .url(entity.downloadUrl)
            .header("User-Agent", "JellyPlay/1.0.0")

        if (!accessToken.isNullOrBlank()) {
            requestBuilder.header("X-Emby-Token", accessToken)
        }

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val response = downloadClient.newCall(requestBuilder.build()).awaitResponse()

        val responseCode = response.code
        if (responseCode == 416) {
            response.close()
            dao.updateProgress(downloadId, 0L, DownloadStatus.PENDING.name)
            return try {
                val retryRequest = Request.Builder()
                    .url(entity.downloadUrl)
                    .header("User-Agent", "JellyPlay/1.0.0")
                if (!accessToken.isNullOrBlank()) {
                    retryRequest.header("X-Emby-Token", accessToken)
                }
                val retryResponse = downloadClient.newCall(retryRequest.build()).awaitResponse()
                if (retryResponse.code != 200 && retryResponse.code != 206) {
                    dao.updateProgress(downloadId, 0L, DownloadStatus.FAILED.name)
                    retryResponse.close()
                    return Result.failure()
                }
                performDownload(
                    downloadClient = downloadClient,
                    dao = dao,
                    downloadId = downloadId,
                    entity = entity,
                    response = retryResponse,
                    existingBytes = 0L,
                    notificationId = notificationId,
                    accessToken = accessToken,
                    probedTotalSize = probedTotalSize,
                )
            } catch (e: Exception) {
                dao.updateProgress(downloadId, 0L, DownloadStatus.FAILED.name)
                Result.retry()
            }
        }
        if (responseCode != 200 && responseCode != 206) {
            response.close()
            // 401/403 = the access token was revoked or expired mid-download
            // (admin forced logout, password change, server session cycle).
            // Retrying is pointless — every attempt gets the same 401 and burns
            // the WorkManager retry budget. Fail the row with a user-facing
            // "session expired" message (surfaced under the FAILED state in the
            // Downloads UI via DownloadEntity.errorMessage) so the user knows to
            // sign in again, rather than seeing a generic retry loop.
            if (responseCode == 401 || responseCode == 403) {
                if (entity.status != DownloadStatus.PAUSED.name) {
                    dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
                    dao.updateErrorMessage(downloadId, SESSION_EXPIRED_ERROR)
                }
                return Result.failure()
            }
            // Other transient non-2xx (503, 429, …): retry, but reset
            // existingBytes to 0 and delete the partial file first. Previously
            // the retry re-sent the same stale `Range: bytes=N-` header, hit the
            // same transient error, and burned retries until Result.failure() —
            // leaving a FAILED row with a non-zero byte count and no usable
            // partial for that path. Starting clean guarantees the next attempt
            // re-downloads from byte 0 instead of looping on the stale Range.
            if (entity.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, 0L, DownloadStatus.FAILED.name)
                dao.updateErrorMessage(downloadId, null)
            }
            runCatching {
                val partial = File(entity.downloadPath)
                if (partial.exists()) partial.delete()
            }
            return Result.retry()
        }

        return performDownload(
            downloadClient = downloadClient,
            dao = dao,
            downloadId = downloadId,
            entity = entity,
            response = response,
            existingBytes = existingBytes,
            notificationId = notificationId,
            accessToken = accessToken,
            probedTotalSize = probedTotalSize,
        )
    }

    private suspend fun performDownload(
        downloadClient: OkHttpClient,
        dao: DownloadDao,
        downloadId: String,
        entity: com.raulshma.jellyplay.core.database.entity.DownloadEntity,
        response: okhttp3.Response,
        existingBytes: Long,
        notificationId: Int,
        accessToken: String?,
        probedTotalSize: Long = 0L,
    ): Result {
        val isPartial = response.code == 206

        val totalSize = if (isPartial && existingBytes > 0) {
            val contentRange = response.header("Content-Range")
            if (contentRange != null) {
                val totalPart = contentRange.substringAfter("/")
                totalPart.toLongOrNull()
                    ?: (existingBytes + (response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L))
            } else {
                existingBytes + (response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L)
            }
        } else {
            response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
        }

        // Authoritative size: prefer the GET response's Content-Length/Range
        // (always accurate for ORIGINAL + honor Range on resume), and fall back
        // to the HEAD probe when the body is chunked/transcoded (Content-Length
        // == -1 → 0). Without this fallback, a transcoded stream that closed
        // early without throwing could ship a truncated file as COMPLETED — the
        // reported bug ("downloads show finished but media unavailable offline").
        val effectiveTotalSize = if (totalSize > 0L) totalSize else probedTotalSize

        if (effectiveTotalSize > 0 && existingBytes == 0L) {
            dao.updateTotalSize(downloadId, effectiveTotalSize)
        }

        val file = File(entity.downloadPath)
        file.parentFile?.mkdirs()

        val buffer = ByteArray(BUFFER_SIZE)
        var downloadedBytes = if (isPartial) existingBytes else 0L
        var lastProgressUpdate = System.currentTimeMillis()
        var lastSpeedBytes = downloadedBytes
        var speedBytesPerSec = 0L
        val progressUpdateIntervalMs = 2000L

        val body = response.body ?: run {
            dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            return Result.failure()
        }

        try {
            body.byteStream().buffered().use { input ->
                val outputStream = if (isPartial) {
                    java.io.FileOutputStream(file, true)
                } else {
                    file.outputStream()
                }
                outputStream.buffered().use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            dao.updateProgress(downloadId, downloadedBytes, DownloadStatus.PAUSED.name)
                            response.close()
                            return Result.success()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= progressUpdateIntervalMs) {
                            // Read only the status column (was a 23-col SELECT *)
                            // — the loop only needs to detect a pause/cancel
                            // transition written by another process.
                            val currentStatus = dao.getStatus(downloadId)
                            if (currentStatus == null || DownloadStates.isInactive(currentStatus)) {
                                response.close()
                                return Result.success()
                            }
                            val elapsed = now - lastProgressUpdate
                            val bytesDelta = downloadedBytes - lastSpeedBytes
                            speedBytesPerSec = if (elapsed > 0) {
                                (bytesDelta * 1000L) / elapsed
                            } else 0L
                            lastSpeedBytes = downloadedBytes
                            lastProgressUpdate = now

                            dao.updateProgressWithSpeed(
                                downloadId, downloadedBytes,
                                DownloadStatus.DOWNLOADING.name, speedBytesPerSec,
                            )

                            val progress = if (effectiveTotalSize > 0) {
                                (downloadedBytes * 100 / effectiveTotalSize).toInt()
                            } else 0
                            DownloadNotificationHelper.updateNotification(
                                applicationContext, notificationId, entity.name, progress,
                                downloadedBytes, effectiveTotalSize, speedBytesPerSec,
                            )
                        }
                    }
                }
            }
        } catch (e: java.io.IOException) {
            response.close()
            val outcome = DownloadFailurePolicy.decide(
                error = e,
                madeProgress = downloadedBytes > existingBytes,
                currentStatus = dao.getDownloadById(downloadId)?.status ?: DownloadStatus.DOWNLOADING.name,
                isResumablePartial = true,
            )
            outcome.applyTo(dao, downloadId, downloadedBytes)
            return outcome.toWorkResult()
        }

        response.close()

        dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING.name, 0L)

        // Integrity check. When an authoritative size is known — either from
        // the GET response's Content-Length/Range (ORIGINAL) or the HEAD probe
        // (transcoded/chunked streams whose body carries no Content-Length) —
        // verify the final byte count matches exactly, so a server that closed
        // the connection early without an error still fails + retries instead
        // of shipping a truncated file as COMPLETED.
        if (effectiveTotalSize > 0L && downloadedBytes != effectiveTotalSize) {
            dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.FAILED.name, 0L)
            dao.updateErrorMessage(downloadId, SIZE_MISMATCH_ERROR)
            return Result.retry()
        }
        // Size unknown (effectiveTotalSize == 0): a clean stream end is the
        // only completion signal, so a 0-byte or truncated response (e.g. an
        // empty 200 body) would otherwise be marked COMPLETED and play as a
        // corrupt file. Reject 0-byte results as FAILED + retry.
        if (downloadedBytes <= 0L) {
            dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.FAILED.name, 0L)
            dao.updateErrorMessage(downloadId, SIZE_MISMATCH_ERROR)
            return Result.retry()
        }

        dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.COMPLETED.name, 0L)
        // A successful download clears the auto-retry budget so a later
        // network interruption starts the dead-letter count from 0.
        dao.resetRetryCount(downloadId)
        DownloadNotificationHelper.dismissNotification(applicationContext, notificationId)
        return Result.success()
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
        private const val BUFFER_SIZE = 65536
        private const val MIN_MULTI_SIZE = 2L * 1024 * 1024
        // User-facing message written to DownloadEntity.errorMessage when the
        // server returns 401/403 mid-download, signalling the access token was
        // revoked/expired. The Downloads UI renders errorMessage under the
        // FAILED state so the user knows to sign in again.
        const val SESSION_EXPIRED_ERROR = "Session expired — please sign in again"
        // User-facing message written when the final byte count does not match
        // the Content-Length (or the body was empty with no Content-Length),
        // indicating a truncated/empty download — possible network truncation.
        const val SIZE_MISMATCH_ERROR = "File size mismatch — possible network truncation"
    }
}
