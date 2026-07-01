package com.raulshma.jellyplay.core.data.worker

import android.Manifest
import android.app.Notification
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
import androidx.work.ForegroundInfo
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed

/**
 * Builds and manages the download progress foreground notification (channel
 * creation, foreground info, progress/speed/ETA formatting). Extracted out of
 * [DownloadWorker] so the worker orchestrates transfer instead of owning
 * presentation (startup-and-workers-architecture §7.7).
 *
 * The channel is deduplicated solely via [NotificationManager.getNotificationChannel]
 * — the previous process-global `channelCreated` flag was redundant
 * (§7.9) and is intentionally not reproduced here.
 */
internal object DownloadNotificationHelper {

    const val CHANNEL_ID = "downloads"

    fun createForegroundInfo(
        context: Context,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): ForegroundInfo {
        createNotificationChannel(context)
        val notification = buildNotification(
            context, notificationId, name, progress, downloadedBytes, totalBytes, speedBytesPerSec,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun updateNotification(
        context: Context,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val notification = buildNotification(
            context, notificationId, name, progress, downloadedBytes, totalBytes, speedBytesPerSec,
        )
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun formatProgressText(
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): String {
        val downloaded = downloadedBytes.formatBytes()
        val total = if (totalBytes > 0) totalBytes.formatBytes() else "..."
        val speed = speedBytesPerSec.formatSpeed()
        val eta = formatEta(downloadedBytes, totalBytes, speedBytesPerSec)
        return if (eta.isNotEmpty()) "$downloaded / $total · $speed · $eta" else "$downloaded / $total · $speed"
    }

    private fun buildNotification(
        context: Context,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): Notification {
        val intent = Intent().apply {
            setClassName(context.packageName, "com.raulshma.jellyplay.MainActivity")
            action = "com.raulshma.jellyplay.action.DOWNLOADS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(formatProgressText(downloadedBytes, totalBytes, speedBytesPerSec))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setSubText(speedBytesPerSec.formatSpeed())
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
}
