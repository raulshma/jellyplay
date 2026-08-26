package com.raulshma.jellyplay.core.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.di.koin
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Routes the download notification actions back into the repository — the
 * same `goAsync()` + injected-suspend pattern as the new-media
 * NotificationActionReceiver. Each action is an explicit broadcast with the
 * `download_id` extra; the repository methods it calls are the exact ones the
 * Downloads screen uses, so in-shade and in-app controls stay in sync.
 *
 * Talks only to [DownloadRepository]: the repository owns the notification
 * group-summary refresh on every state change, so this receiver never reaches
 * into the DAO or posts summary updates itself.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    // Wave 8A: Hilt left this module — the repository single resolves from
    // the Koin container on first use (the app composition root starts it
    // long before any broadcast can arrive).
    private val downloadRepository: DownloadRepository by lazy { koin().get() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        when (intent.action) {
            ACTION_PAUSE -> launchPending {
                // Cancels the worker (foreground stops promptly) and marks the
                // row USER-paused (the repo also refreshes the group summary),
                // then keeps a shade handle alive so the user can Resume/Cancel
                // without reopening the app.
                downloadRepository.pauseDownload(downloadId)
                val name = downloadRepository.getDownloadName(downloadId)
                if (name != null) {
                    DownloadNotificationHelper.postPausedNotification(context, downloadId, name)
                }
            }
            ACTION_RESUME -> launchPending {
                // Same pair the in-app resume uses: mark PENDING, then enqueue
                // so WorkManager picks it up honoring schedule/network prefs.
                // Both refresh the summary; the paused shade handle goes away.
                downloadRepository.resumeDownload(downloadId)
                downloadRepository.enqueueDownload(downloadId)
                DownloadNotificationHelper.dismissPausedNotification(context, downloadId)
            }
            ACTION_CANCEL -> launchPending {
                // cancelDownload tears down the worker + files and refreshes the
                // summary; the shade handles for both progress and paused states
                // are dismissed here.
                downloadRepository.cancelDownload(downloadId)
                DownloadNotificationHelper.dismissNotification(
                    context, DownloadNotificationHelper.notificationIdFor(downloadId),
                )
                DownloadNotificationHelper.dismissPausedNotification(context, downloadId)
            }
        }
    }

    /**
     * Runs [block] on this receiver's coroutine scope, keeping the broadcast
     * alive ([goAsync]) until it completes. Mirrors NotificationActionReceiver.
     */
    private fun launchPending(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.raulshma.jellyplay.download.PAUSE"
        const val ACTION_RESUME = "com.raulshma.jellyplay.download.RESUME"
        const val ACTION_CANCEL = "com.raulshma.jellyplay.download.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
