package com.raulshma.jellyplay.core.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val userDao: UserDao,
    private val preferencesStore: UserPreferencesStore,
    private val client: OkHttpClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()

        val entity = dao.getDownloadById(downloadId) ?: return Result.failure()

        if (entity.status == DownloadStatus.PAUSED.name || entity.status == DownloadStatus.CANCELLED.name) {
            return Result.success()
        }

        val notificationId = downloadId.hashCode() and 0x7FFFFFFF
        try {
            setForeground(createForegroundInfo(notificationId, entity.name, 0, 0L, entity.totalSizeBytes, 0L))
        } catch (_: Exception) {
        }

        val existingBytes = entity.downloadedBytes
        dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)

        val activeUserId = preferencesStore.activeUserId.firstOrNull()
        val accessToken = activeUserId?.let { uid ->
            userDao.getUserById(uid)?.accessToken
        }

        val downloadClient = client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val numConnections = preferencesStore.preferences.firstOrNull()?.downloadConnections?.coerceIn(1, 8) ?: 1

        return try {
            if (existingBytes > 0L) {
                performSingleConnectionDownload(
                    downloadClient = downloadClient,
                    dao = dao,
                    downloadId = downloadId,
                    entity = entity,
                    existingBytes = existingBytes,
                    notificationId = notificationId,
                    accessToken = accessToken,
                )
            } else {
                val totalSize = probeContentSize(downloadClient, entity.downloadUrl, accessToken)
                if (totalSize > MIN_MULTI_SIZE && numConnections > 1) {
                    performMultiConnectionDownload(
                        downloadClient = downloadClient,
                        dao = dao,
                        downloadId = downloadId,
                        entity = entity,
                        totalSize = totalSize,
                        numConnections = numConnections,
                        notificationId = notificationId,
                        accessToken = accessToken,
                    )
                } else {
                    performSingleConnectionDownload(
                        downloadClient = downloadClient,
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

    private fun probeContentSize(
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
            val response = downloadClient.newCall(requestBuilder.build()).execute()
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

    private suspend fun performMultiConnectionDownload(
        downloadClient: OkHttpClient,
        dao: DownloadDao,
        downloadId: String,
        entity: com.raulshma.jellyplay.core.database.entity.DownloadEntity,
        totalSize: Long,
        numConnections: Int,
        notificationId: Int,
        accessToken: String?,
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
            setForeground(createForegroundInfo(notificationId, entity.name, 0, 0L, totalSize, 0L))
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
                        if (currentEntity == null ||
                            currentEntity.status == DownloadStatus.PAUSED.name ||
                            currentEntity.status == DownloadStatus.CANCELLED.name
                        ) {
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
                        updateNotification(
                            notificationId, entity.name, progress,
                            currentDownloaded, totalSize, speedBytesPerSec,
                        )

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
                val finalBytes = totalDownloaded.get()
                val currentEntity = dao.getDownloadById(downloadId)
                val cancelStatus = if (currentEntity == null || currentEntity.status == DownloadStatus.CANCELLED.name) {
                    DownloadStatus.CANCELLED.name
                } else {
                    DownloadStatus.PAUSED.name
                }
                dao.updateProgressWithSpeed(downloadId, finalBytes, cancelStatus, 0L)
                return Result.success()
            }

            val finalBytes = totalDownloaded.get()
            if (totalSize > 0L && finalBytes < totalSize) {
                dao.updateProgressWithSpeed(downloadId, finalBytes, DownloadStatus.FAILED.name, 0L)
                return Result.retry()
            }

            dao.updateProgressWithSpeed(downloadId, finalBytes, DownloadStatus.COMPLETED.name, 0L)
            dismissNotification(notificationId)
            Result.success()
        } catch (e: java.io.IOException) {
            if (totalDownloaded.get() > 0) {
                dao.updateProgressWithSpeed(downloadId, totalDownloaded.get(), DownloadStatus.PAUSED.name, 0L)
            }
            Result.retry()
        } catch (e: Exception) {
            if (totalDownloaded.get() > 0) {
                dao.updateProgressWithSpeed(downloadId, totalDownloaded.get(), DownloadStatus.FAILED.name, 0L)
            }
            Result.failure()
        }
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
            throw java.io.IOException("Chunk ${chunk.index} failed with code ${response.code}")
        }

        val body = response.body ?: run {
            response.close()
            throw java.io.IOException("Chunk ${chunk.index} has no body")
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

        val response = downloadClient.newCall(requestBuilder.build()).execute()

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
                val retryResponse = downloadClient.newCall(retryRequest.build()).execute()
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
                            val currentEntity = dao.getDownloadById(downloadId)
                            if (currentEntity == null ||
                                currentEntity.status == DownloadStatus.PAUSED.name ||
                                currentEntity.status == DownloadStatus.CANCELLED.name
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
                            updateNotification(
                                notificationId, entity.name, progress,
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
        dismissNotification(notificationId)
        return Result.success()
    }

    private fun createForegroundInfo(
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification(
            notificationId, name, progress, downloadedBytes, totalBytes, speedBytesPerSec,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val notification = buildNotification(
            notificationId, name, progress, downloadedBytes, totalBytes, speedBytesPerSec,
        )
        NotificationManagerCompat.from(applicationContext)
            .notify(notificationId, notification)
    }

    private fun buildNotification(
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): android.app.Notification {
        val intent = Intent().apply {
            setClassName(applicationContext.packageName, "com.raulshma.jellyplay.MainActivity")
            action = "com.raulshma.jellyplay.action.DOWNLOADS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(formatProgressText(downloadedBytes, totalBytes, speedBytesPerSec))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setSubText(formatSpeed(speedBytesPerSec))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun dismissNotification(notificationId: Int) {
        NotificationManagerCompat.from(applicationContext).cancel(notificationId)
    }

    private fun formatProgressText(
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): String {
        val downloaded = formatBytes(downloadedBytes)
        val total = if (totalBytes > 0) formatBytes(totalBytes) else "..."
        val speed = formatSpeed(speedBytesPerSec)
        val eta = formatEta(downloadedBytes, totalBytes, speedBytesPerSec)
        return if (eta.isNotEmpty()) "$downloaded / $total · $speed · $eta" else "$downloaded / $total · $speed"
    }

    private fun formatSpeed(speedBytesPerSec: Long): String = when {
        speedBytesPerSec <= 0 -> ""
        speedBytesPerSec < 1024 -> "$speedBytesPerSec B/s"
        speedBytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(speedBytesPerSec / 1024.0)
        speedBytesPerSec < 1024 * 1024 * 1024 -> "%.1f MB/s".format(speedBytesPerSec / (1024.0 * 1024))
        else -> "%.1f GB/s".format(speedBytesPerSec / (1024.0 * 1024 * 1024))
    }

    private fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String {
        if (totalBytes <= 0 || speedBytesPerSec <= 0) return ""
        val remainingBytes = totalBytes - downloadedBytes
        if (remainingBytes <= 0) return ""
        val secondsRemaining = remainingBytes / speedBytesPerSec
        return when {
            secondsRemaining < 60 -> "${secondsRemaining}s left"
            secondsRemaining < 3600 -> "${secondsRemaining / 60}m ${secondsRemaining % 60}s left"
            else -> {
                val hours = secondsRemaining / 3600
                val minutes = (secondsRemaining % 3600) / 60
                "${hours}h ${minutes}m left"
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    private fun createNotificationChannel() {
        if (channelCreated) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        channelCreated = true
    }

    private data class ChunkInfo(
        val index: Int,
        val start: Long,
        val end: Long,
    )

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val UNIQUE_WORK_PREFIX = "download_"
        private const val CHANNEL_ID = "downloads"
        private const val BUFFER_SIZE = 65536
        private const val MIN_MULTI_SIZE = 2L * 1024 * 1024
        private const val PROGRESS_UPDATE_INTERVAL_MS = 2000L

        @Volatile
        private var channelCreated = false
    }
}
