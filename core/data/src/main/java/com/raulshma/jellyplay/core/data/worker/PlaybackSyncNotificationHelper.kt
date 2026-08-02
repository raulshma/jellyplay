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
import com.raulshma.jellyplay.core.data.R

/**
 * Builds and manages the playback-sync notification shown while
 * [PlaybackSyncWorker] drains the offline outbox. Mirrors
 * [DownloadNotificationHelper] but for a short, atomic drain: there is no
 * byte-level progress, so the notification is an ongoing low-priority card
 * ("Syncing watch progress…") that is cancelled when the drain completes.
 *
 * Channel dedup is via [NotificationManager.getNotificationChannel] — same
 * idempotent pattern as the download helper.
 */
internal object PlaybackSyncNotificationHelper {

    const val CHANNEL_ID = "playback_sync"
    // Stable notification id reused across drain runs (unique within the app's
    // notification id space; downloads use a separate id range).
    const val NOTIFICATION_ID = 47505

    /**
     * Builds the [ForegroundInfo] for [androidx.work.CoroutineWorker.setForeground].
     * The drain is short, but promoting to foreground while it runs avoids
     * WorkManager imposing expedited-work quotas on reconnect bursts.
     */
    fun createForegroundInfo(
        context: Context,
        pendingCount: Int,
    ): ForegroundInfo {
        createNotificationChannel(context)
        val notification = buildNotification(context, pendingCount, indeterminate = true)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Updates (or posts) the notification without promoting the worker to
     * foreground. Used to show remaining count as the drain progresses.
     */
    fun updateNotification(
        context: Context,
        pendingCount: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val notification = buildNotification(context, pendingCount, indeterminate = false)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(
        context: Context,
        pendingCount: Int,
        indeterminate: Boolean,
    ): Notification {
        // Explicit target: pinned to MainActivity in our own package. `setClassName`
        // is applied on the intent directly (not inside an `Intent().apply { }`
        // block) so CodeQL recognizes the intent as explicit and the PendingIntent
        // is not flagged as implicit.
        val intent = Intent()
            .setClassName(context.packageName, "com.raulshma.jellyplay.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(R.string.data_sync_title)
        val text = if (pendingCount > 0) context.resources.getQuantityString(
            R.plurals.data_sync_items_queued,
            pendingCount,
            pendingCount,
        ) else context.getString(R.string.data_sync_finishing)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, indeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.data_sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.data_sync_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
