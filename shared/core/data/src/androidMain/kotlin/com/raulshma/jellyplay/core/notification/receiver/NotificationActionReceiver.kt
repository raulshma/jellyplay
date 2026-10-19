package com.raulshma.jellyplay.core.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.raulshma.jellyplay.core.data.receiver.broadcastExtras
import com.raulshma.jellyplay.core.data.receiver.runWithBroadcastScope
import com.raulshma.jellyplay.core.data.repository.SeenMediaRecord
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.notification.di.koin
import com.raulshma.jellyplay.core.notification.dispatcher.mediaDeepLinkIntent

class NotificationActionReceiver : BroadcastReceiver() {

    // Hilt left this module — the repository single resolves from
    // the Koin container on first use (the app composition root starts it
    // long before any broadcast can arrive).
    private val seenMediaRepository: SeenMediaRepository by lazy { koin().get() }

    override fun onReceive(context: Context, intent: Intent) {
        // The verb/extras decode lives in [notificationEntry] (pure, JVM-
        // tested); this body is fold + dispatch only.
        when (val entry = notificationEntry(intent.action, intent.broadcastExtras())) {
            is NotificationEntry.OpenDetail -> {
                context.startActivity(mediaDeepLinkIntent(context, entry.itemId))
            }
            is NotificationEntry.MarkSeen -> runWithBroadcastScope(LOG_TAG) {
                seenMediaRepository.markAsSeen(
                    itemId = entry.itemId,
                    libraryId = entry.libraryId,
                    mediaType = entry.mediaType,
                )
            }
            is NotificationEntry.MarkAllSeen -> runWithBroadcastScope(LOG_TAG) {
                seenMediaRepository.markAsSeen(
                    entry.itemIds.indices.map { i ->
                        SeenMediaRecord(
                            itemId = entry.itemIds[i],
                            libraryId = entry.libraryIds[i],
                            mediaType = entry.mediaTypes[i],
                        )
                    }
                )
                entry.notificationIds?.forEach { id ->
                    NotificationManagerCompat.from(context).cancel(id)
                }
            }
            NotificationEntry.Ignored -> Unit
        }
    }

    companion object {
        private const val LOG_TAG = "NotificationActions"

        const val ACTION_OPEN_DETAIL = "com.raulshma.jellyplay.notification.OPEN_DETAIL"
        const val ACTION_MARK_SEEN = "com.raulshma.jellyplay.notification.MARK_SEEN"
        const val ACTION_MARK_ALL_SEEN = "com.raulshma.jellyplay.notification.MARK_ALL_SEEN"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_LIBRARY_ID = "library_id"
        const val EXTRA_MEDIA_TYPE = "media_type"
        // Parallel arrays carried by ACTION_MARK_ALL_SEEN.
        const val EXTRA_ITEM_IDS = "item_ids"
        const val EXTRA_LIBRARY_IDS = "library_ids"
        const val EXTRA_MEDIA_TYPES = "media_types"
        const val EXTRA_NOTIFICATION_IDS = "notification_ids"
    }
}
