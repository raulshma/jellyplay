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
import com.raulshma.jellyplay.core.data.receiver.DownloadActionReceiver
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed

/**
 * Builds and manages the download notifications: the per-transfer progress
 * foreground notification (with Pause/Cancel actions), the paused-state
 * notification (Resume/Cancel) shown once a transfer stops at the user's
 * request, and the "downloads" group **summary** that collapses N concurrent
 * transfers into one shade item.
 *
 * Channel creation, foreground info, and progress/speed/ETA formatting live
 * here; the workers orchestrate the transfer and call back in. Notification
 * ids flow through [notificationIdFor] / [pausedNotificationIdFor] — the
 * single home for the derivation so the worker, the action receiver
 * ([DownloadActionReceiver]) and the summary never drift.
 *
 * The channel is deduplicated solely via [NotificationManager.getNotificationChannel]
 * — the previous process-global `channelCreated` flag was redundant
 * and is intentionally not reproduced here.
 */
internal object DownloadNotificationHelper {

    const val CHANNEL_ID = "downloads"
    const val GROUP_KEY = "downloads"

    /**
     * Per-download progress notification id. Must match the worker's
     * ForegroundInfo. The id is masked to [ID_MASK] so the paused and summary
     * id ranges (which set the bits above it) can never alias a progress id.
     */
    fun notificationIdFor(downloadId: String): Int = downloadId.hashCode() and ID_MASK

    /**
     * Paused-state notification id — [notificationIdFor] with [PAUSED_ID_FLAG]
     * OR'd in, landing in a range strictly above every progress id and below
     * [SUMMARY_NOTIFICATION_ID], so the three spaces can never alias each other
     * (unlike an `xor`, which can fold a paused id back into the progress range).
     */
    fun pausedNotificationIdFor(downloadId: String): Int =
        notificationIdFor(downloadId) or PAUSED_ID_FLAG

    fun createForegroundInfo(
        context: Context,
        downloadId: String,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): ForegroundInfo {
        createNotificationChannel(context)
        val notification = buildNotification(
            context, downloadId, name, progress, downloadedBytes, totalBytes, speedBytesPerSec,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /**
     * Queued-state foreground notification — shown while a transfer waits for a
     * concurrency slot. Distinct from [createForegroundInfo] so the shade shows
     * "Queued…" with an indeterminate bar instead of the misleading "0 B / …"
     * that the byte-formatted progress text renders at zero downloaded bytes.
     */
    fun createQueuedForegroundInfo(
        context: Context,
        downloadId: String,
        notificationId: Int,
        name: String,
    ): ForegroundInfo {
        createNotificationChannel(context)
        val notification = buildQueuedNotification(context, downloadId, name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun updateNotification(
        context: Context,
        downloadId: String,
        notificationId: Int,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) {
        if (!canPostNotifications(context)) return
        val notification = buildNotification(
            context, downloadId, name, progress, downloadedBytes, totalBytes, speedBytesPerSec,
        )
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Posts the paused-state notification for a download stopped by the user,
     * so the shade keeps a Resume/Cancel handle after the worker is gone.
     * Best-effort: silently skipped when notification permission was revoked.
     */
    fun postPausedNotification(context: Context, downloadId: String, name: String) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)
        val notificationId = pausedNotificationIdFor(downloadId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.data_download_paused))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openDownloadsPendingIntent(context))
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_notification_play,
                context.getString(R.string.data_download_action_resume),
                actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_RESUME, ACTION_RESUME_OFFSET),
            )
            .addAction(
                R.drawable.ic_notification_stop,
                context.getString(R.string.data_download_action_cancel),
                actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_CANCEL, ACTION_CANCEL_OFFSET),
            )
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun dismissPausedNotification(context: Context, downloadId: String) {
        NotificationManagerCompat.from(context).cancel(pausedNotificationIdFor(downloadId))
    }

    /**
     * Last count handed to [refreshSummary] (null = never posted this process).
     * The count only changes on download lifecycle transitions, but the 2 s
     * progress ticks re-invoke [refreshSummary] with the same value — the memo
     * skips the notification rebuild + re-post until the count actually changes.
     */
    private var lastSummaryCount: Int? = null

    /**
     * Posts (or dismisses when [inFlightCount] == 0) the group summary. Called
     * from every download lifecycle transition so the shade collapses the active
     * transfers into a single row and clears itself when the last one ends.
     */
    fun refreshSummary(context: Context, inFlightCount: Int) {
        if (inFlightCount == lastSummaryCount) return
        if (inFlightCount <= 0) {
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID)
            lastSummaryCount = 0
            return
        }
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.data_download_summary_active, inFlightCount, inFlightCount)
            )
            .setContentIntent(openDownloadsPendingIntent(context))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        lastSummaryCount = inFlightCount
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
        downloadId: String,
        name: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(formatProgressText(downloadedBytes, totalBytes, speedBytesPerSec))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setSubText(speedBytesPerSec.formatSpeed())
            .setContentIntent(openDownloadsPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_KEY)
            .addAction(
                R.drawable.ic_notification_pause,
                context.getString(R.string.data_download_action_pause),
                actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_PAUSE, ACTION_PAUSE_OFFSET),
            )
            .addAction(
                R.drawable.ic_notification_stop,
                context.getString(R.string.data_download_action_cancel),
                actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_CANCEL, ACTION_CANCEL_OFFSET),
            )
            .build()
    }

    /**
     * Queued variant: indeterminate progress bar and a "Queued…" body, with the
     * same Pause/Cancel actions so the user can still hold or cancel while
     * waiting for a slot.
     */
    private fun buildQueuedNotification(
        context: Context,
        downloadId: String,
        name: String,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.data_download_queued))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(openDownloadsPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_KEY)
            .addAction(
                R.drawable.ic_notification_pause,
                context.getString(R.string.data_download_action_pause),
                actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_PAUSE, ACTION_PAUSE_OFFSET),
            )
            .addAction(
                R.drawable.ic_notification_stop,
                context.getString(R.string.data_download_action_cancel),
                actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_CANCEL, ACTION_CANCEL_OFFSET),
            )
            .build()
    }

    // ── Intents ──────────────────────────────────────────────────────────

    /**
     * Explicit PendingIntent targeting [DownloadActionReceiver]. The request
     * code incorporates the per-download notification id so PendingIntent
     * matching never collapses two distinct downloads into one broadcast — the
     * extras are not part of PendingIntent's identity.
     */
    private fun actionPendingIntent(
        context: Context,
        downloadId: String,
        action: String,
        offset: Int,
    ): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, DownloadActionReceiver::class.java.name)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        return PendingIntent.getBroadcast(
            context,
            notificationIdFor(downloadId) + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Explicit target: MainActivity in our own package with the DOWNLOADS action.
     * `setClassName` is applied on the intent directly (not inside an
     * `Intent().apply { }` block) so CodeQL recognizes the intent as explicit
     * and the PendingIntent is not flagged as implicit.
     */
    private fun openDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, "com.raulshma.jellyplay.MainActivity")
            .setAction("com.raulshma.jellyplay.action.DOWNLOADS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ── Channel / permissions ────────────────────────────────────────────

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

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

    // Every broadcast action above must resolve to a distinct PendingIntent
    // identity (request code + component + action), so give each action its own
    // offset on top of the per-download notification id.
    private const val ACTION_PAUSE_OFFSET = 0
    private const val ACTION_RESUME_OFFSET = 1
    private const val ACTION_CANCEL_OFFSET = 2

    // The three notification-id spaces are kept disjoint by bit partitioning:
    //   progress ids:   bits 0..26  (ID_MASK)
    //   paused ids:     bit 27 set  (PAUSED_ID_FLAG)  — always > any progress id
    //   summary id:     bit 28 set  (SUMMARY_BIT)     — always > any paused id
    // A plain `xor` flag is unsafe here: it can fold a paused id back into the
    // progress range for a different download, so we OR the flags instead and
    // mask the base id so it can never reach them.
    private const val ID_MASK = 0x07FFFFFF
    private const val PAUSED_ID_FLAG = 0x08000000
    private const val SUMMARY_BIT = 0x10000000

    // Fixed summary id — strictly greater than every possible progress and
    // paused id by construction (only the SUMMARY bit is set).
    const val SUMMARY_NOTIFICATION_ID = SUMMARY_BIT
}
