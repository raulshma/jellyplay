package com.raulshma.jellyplay.core.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun downloadDao(): DownloadDao
        fun serverDao(): com.raulshma.jellyplay.core.database.dao.ServerDao
        fun userDao(): com.raulshma.jellyplay.core.database.dao.UserDao
        fun preferencesStore(): com.raulshma.jellyplay.core.datastore.UserPreferencesStore
        fun okHttpClient(): OkHttpClient
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java,
        )
        val dao = entryPoint.downloadDao()
        val prefs = entryPoint.preferencesStore()
        val client = entryPoint.okHttpClient()

        val entity = dao.getDownloadById(downloadId) ?: return Result.failure()

        if (entity.status == DownloadStatus.PAUSED.name || entity.status == DownloadStatus.CANCELLED.name) {
            return Result.success()
        }

        val notificationId = downloadId.hashCode() and 0x7FFFFFFF
        setForeground(createForegroundInfo(notificationId, entity.name, 0, 0L, entity.totalSizeBytes, 0L))

        val existingBytes = entity.downloadedBytes
        dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)

        val activeUserId = prefs.activeUserId.firstOrNull()
        val accessToken = activeUserId?.let { uid ->
            entryPoint.userDao().getUserById(uid)?.accessToken
        }

        return try {
            val requestBuilder = Request.Builder()
                .url(entity.downloadUrl)
                .header("User-Agent", "JellyPlay/1.0.0")

            if (!accessToken.isNullOrBlank()) {
                requestBuilder.header("X-Emby-Token", accessToken)
            }

            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val downloadClient = client.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val response = downloadClient.newCall(requestBuilder.build()).execute()

            val responseCode = response.code
            if (responseCode != 200 && responseCode != 206) {
                if (entity.status != DownloadStatus.PAUSED.name) {
                    dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
                }
                response.close()
                return Result.failure()
            }

            val isPartial = responseCode == 206

            val totalSize = if (existingBytes > 0) {
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
            var downloadedBytes = existingBytes
            var lastProgressUpdate = System.currentTimeMillis()
            var lastSpeedBytes = existingBytes
            var speedBytesPerSec = 0L
            val progressUpdateIntervalMs = 500L

            val body = response.body ?: run {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
                return Result.failure()
            }

            val source = body.byteStream().buffered()
            source.use { input ->
                val outputStream = if (isPartial) {
                    java.io.FileOutputStream(file, true)
                } else {
                    downloadedBytes = 0
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
                            if (currentEntity?.status == DownloadStatus.PAUSED.name ||
                                currentEntity?.status == DownloadStatus.CANCELLED.name
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

            response.close()

            dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING.name, 0L)

            if (totalSize > 0L && downloadedBytes < totalSize) {
                dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.FAILED.name, 0L)
                return Result.failure()
            }

            dao.updateProgressWithSpeed(downloadId, downloadedBytes, DownloadStatus.COMPLETED.name, 0L)
            dismissNotification(notificationId)
            Result.success()
        } catch (e: Exception) {
            val currentEntity = dao.getDownloadById(downloadId)
            if (currentEntity?.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            }
            Result.failure()
        }
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
        return ForegroundInfo(notificationId, notification)
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
    ) = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setContentTitle(name)
        .setContentText(formatProgressText(downloadedBytes, totalBytes, speedBytesPerSec))
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress, false)
        .setSubText(formatSpeed(speedBytesPerSec))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

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
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val UNIQUE_WORK_PREFIX = "download_"
        private const val CHANNEL_ID = "downloads"
        private const val BUFFER_SIZE = 65536
    }
}
