package com.raulshma.jellyplay.core.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.di.koin
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper

/**
 * Routes the download notification actions back into the repository — the
 * same fold + goAsync-scope pattern as the new-media
 * NotificationActionReceiver (both decode through a GLOSSARY
 * "Receiver intake fold" — [downloadEntry] here — and share the one
 * [runWithBroadcastScope] window). Each action is an explicit broadcast with
 * the `download_id` extra; the repository methods it calls are the exact
 * ones the Downloads screen uses, so in-shade and in-app controls stay in
 * sync.
 *
 * Talks only to [DownloadRepository]: the repository owns the notification
 * group-summary refresh on every state change, so this receiver never reaches
 * into the DAO or posts summary updates itself.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    // Hilt left this module — the repository single resolves from
    // the Koin container on first use (the app composition root starts it
    // long before any broadcast can arrive).
    private val downloadRepository: DownloadRepository by lazy { koin().get() }

    override fun onReceive(context: Context, intent: Intent) {
        // The verb/id decode lives in [downloadEntry] (pure, JVM-tested);
        // this body is fold + dispatch only.
        when (val entry =
            downloadEntry(intent.action, intent.getStringExtra(EXTRA_DOWNLOAD_ID))) {
            is DownloadEntry.Pause -> runWithBroadcastScope(LOG_TAG) {
                // Cancels the worker (foreground stops promptly) and marks the
                // row USER-paused (the repo also refreshes the group summary),
                // then keeps a shade handle alive so the user can Resume/Cancel
                // without reopening the app.
                downloadRepository.pauseDownload(entry.downloadId)
                val name = downloadRepository.getDownloadName(entry.downloadId)
                if (name != null) {
                    DownloadNotificationHelper.postPausedNotification(context, entry.downloadId, name)
                }
            }
            is DownloadEntry.Resume -> runWithBroadcastScope(LOG_TAG) {
                // The composite resume verb (mark PENDING, then enqueue so
                // WorkManager picks it up honoring schedule/network prefs) —
                // the same single dispatch the Downloads screen's VM makes,
                // not a receiver-local resume+enqueue pair. Both halves refresh
                // the summary; the paused shade handle goes away.
                downloadRepository.resumeDownload(entry.downloadId)
                DownloadNotificationHelper.dismissPausedNotification(context, entry.downloadId)
            }
            is DownloadEntry.Cancel -> runWithBroadcastScope(LOG_TAG) {
                // cancelDownload tears down the worker + files and refreshes the
                // summary; the shade handles for both progress and paused states
                // are dismissed here.
                downloadRepository.cancelDownload(entry.downloadId)
                DownloadNotificationHelper.dismissNotification(
                    context, DownloadNotificationHelper.notificationIdFor(entry.downloadId),
                )
                DownloadNotificationHelper.dismissPausedNotification(context, entry.downloadId)
            }
            DownloadEntry.Ignored -> Unit
        }
    }

    companion object {
        private const val LOG_TAG = "DownloadActions"

        const val ACTION_PAUSE = "com.raulshma.jellyplay.download.PAUSE"
        const val ACTION_RESUME = "com.raulshma.jellyplay.download.RESUME"
        const val ACTION_CANCEL = "com.raulshma.jellyplay.download.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
