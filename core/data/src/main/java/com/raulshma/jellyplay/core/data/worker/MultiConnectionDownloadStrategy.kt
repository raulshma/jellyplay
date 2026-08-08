package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.data.repository.Outcome
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.data.repository.toWorkResult
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
 */
internal object MultiConnectionDownloadStrategy {

    private const val BUFFER_SIZE = 65536
    private const val PROGRESS_UPDATE_INTERVAL_MS = 2000L

    suspend fun execute(
        context: Context,
        downloadClient: OkHttpClient,
        dao: DownloadDao,
        downloadId: String,
        entity: DownloadEntity,
        totalSize: Long,
        numConnections: Int,
        notificationId: Int,
        accessToken: String?,
        setForegroundInfo: suspend (ForegroundInfo) -> Unit,
    ): Result {
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
            setForegroundInfo(
                DownloadNotificationHelper.createForegroundInfo(
                    context, downloadId, notificationId, entity.name, 0, 0L, totalSize, 0L,
                )
            )
            DownloadNotificationHelper.refreshSummary(context, dao.getInFlightDownloadCount())
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
                        val currentEntity = dao.getDownloadById(downloadId)
                        if (currentEntity == null || DownloadStates.isInactive(currentEntity.status)) {
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
                        DownloadNotificationHelper.updateNotification(
                            context, downloadId, notificationId, entity.name, progress,
                            currentDownloaded, totalSize, speedBytesPerSec,
                        )
                        DownloadNotificationHelper.refreshSummary(context, dao.getInFlightDownloadCount())

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
                return Result.success()
            }

            val finalBytes = totalDownloaded.get()
            if (totalSize > 0L && finalBytes < totalSize) {
                runCatching { if (file.exists()) file.delete() }
                    .onFailure { Log.w("DownloadWorker", "Failed to delete incomplete partial", it) }
                dao.updateErrorMessage(downloadId, "Download incomplete")
                dao.updateProgressWithSpeed(downloadId, 0L, DownloadStatus.FAILED.name, 0L)
                return Result.retry()
            }

            dao.updateErrorMessage(downloadId, null)
            dao.updateProgressWithSpeed(downloadId, finalBytes, DownloadStatus.COMPLETED.name, 0L)
            // A successful download clears the auto-retry budget.
            dao.resetRetryCount(downloadId)
            DownloadNotificationHelper.dismissNotification(context, notificationId)
            DownloadNotificationHelper.refreshSummary(context, dao.getInFlightDownloadCount())
            Result.success()
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
            resolved.toWorkResult()
        }
    }

    private fun failureMessage(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "Network timed out"
        is java.net.UnknownHostException -> "Cannot reach server"
        is javax.net.ssl.SSLException -> "Network security error"
        is IOException -> "Network error"
        else -> "Download failed"
    }

    private fun downloadChunk(
        downloadClient: OkHttpClient,
        url: String,
        chunk: ChunkInfo,
        file: File,
        totalDownloaded: AtomicLong,
        cancelled: AtomicBoolean,
        accessToken: String?,
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "JellyPlay/1.0.0")
            .header("Range", "bytes=${chunk.start}-${chunk.end}")

        if (!accessToken.isNullOrBlank()) {
            requestBuilder.header("X-Emby-Token", accessToken)
        }

        val response = downloadClient.newCall(requestBuilder.build()).execute()

        if (response.code != 206 && response.code != 200) {
            response.close()
            throw IOException("Chunk ${chunk.index} failed with code ${response.code}")
        }

        val body = response.body ?: run {
            response.close()
            throw IOException("Chunk ${chunk.index} has no body")
        }

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(chunk.start)
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                body.byteStream().buffered().use { input ->
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
