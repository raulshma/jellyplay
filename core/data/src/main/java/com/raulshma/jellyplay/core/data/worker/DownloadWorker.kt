package com.raulshma.jellyplay.core.data.worker

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.SocketTimeoutException
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

        if (entity.status == DownloadStatus.PAUSED.name || entity.status == DownloadStatus.CANCELLED.name) {
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
        dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)

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
            try {
            if (existingBytes > 0L) {
                performSingleConnectionDownload(
                    downloadClient = client,
                    dao = dao,
                    downloadId = downloadId,
                    entity = entity,
                    existingBytes = existingBytes,
                    notificationId = notificationId,
                    accessToken = accessToken,
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
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            val currentEntity = dao.getDownloadById(downloadId)
            if (currentEntity?.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            }
            Result.retry()
        } catch (e: java.io.IOException) {
            val currentEntity = dao.getDownloadById(downloadId)
            if (currentEntity?.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            }
            Result.retry()
        } catch (e: Exception) {
            val currentEntity = dao.getDownloadById(downloadId)
            if (currentEntity?.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            }
            Result.failure()
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
                )
            } catch (e: Exception) {
                dao.updateProgress(downloadId, 0L, DownloadStatus.FAILED.name)
                Result.retry()
            }
        }
        if (responseCode != 200 && responseCode != 206) {
            if (entity.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            }
            response.close()
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

        if (totalSize > 0 && existingBytes == 0L) {
            dao.updateTotalSize(downloadId, totalSize)
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
                            if (currentStatus == null ||
                                currentStatus == DownloadStatus.PAUSED.name ||
                                currentStatus == DownloadStatus.CANCELLED.name
                            ) {
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

                            val progress = if (totalSize > 0) {
                                (downloadedBytes * 100 / totalSize).toInt()
                            } else 0
                            DownloadNotificationHelper.updateNotification(
                                applicationContext, notificationId, entity.name, progress,
                                downloadedBytes, totalSize, speedBytesPerSec,
                            )
                        }
                    }
                }
            }
        } catch (e: java.io.IOException) {
            response.close()
            if (downloadedBytes > existingBytes) {
                dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.PAUSED.name, 0L)
            }
            return Result.retry()
        }

        response.close()

        dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING.name, 0L)

        if (totalSize > 0L && downloadedBytes < totalSize) {
            dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.FAILED.name, 0L)
            return Result.retry()
        }

        dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.COMPLETED.name, 0L)
        DownloadNotificationHelper.dismissNotification(applicationContext, notificationId)
        return Result.success()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val UNIQUE_WORK_PREFIX = "download_"
        const val WORK_TAG = "download"
        private const val BUFFER_SIZE = 65536
        private const val MIN_MULTI_SIZE = 2L * 1024 * 1024
    }
}
