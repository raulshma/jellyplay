package com.raulshma.jellyplay.core.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.repository.SeenMediaRecord
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.core.notification.di.koin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    // Wave 8A: Hilt left this module — the repository single resolves from
    // the Koin container on first use (the app composition root starts it
    // long before any broadcast can arrive).
    private val seenMediaRepository: SeenMediaRepository by lazy { koin().get() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_DETAIL -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                val deepLink = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DeepLinkGrammar.mediaLink(itemId))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    setPackage(context.packageName)
                }
                context.startActivity(deepLink)
            }
            ACTION_MARK_SEEN -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                val libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID) ?: return
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: return
                launchPending {
                    seenMediaRepository.markAsSeen(
                        itemId = itemId,
                        libraryId = libraryId,
                        mediaType = mediaType,
                    )
                }
            }
            ACTION_MARK_ALL_SEEN -> {
                // Per-library / global summary "Mark all seen"
                // Three parallel arrays describe every item in the summary; marking
                // them seen prevents re-notification on the next scan. The
                // notification-ids array lets us dismiss the tapped summary plus
                // its child notifications so the shade clears in one tap.
                val itemIds = intent.getStringArrayExtra(EXTRA_ITEM_IDS)
                val libraryIds = intent.getStringArrayExtra(EXTRA_LIBRARY_IDS)
                val mediaTypes = intent.getStringArrayExtra(EXTRA_MEDIA_TYPES)
                val notificationIds = intent.getIntArrayExtra(EXTRA_NOTIFICATION_IDS)
                if (itemIds.isNullOrEmpty() ||
                    libraryIds == null ||
                    mediaTypes == null ||
                    itemIds.size != libraryIds.size ||
                    itemIds.size != mediaTypes.size
                ) {
                    return
                }
                launchPending {
                    seenMediaRepository.markAsSeen(
                        itemIds.indices.map { i ->
                            SeenMediaRecord(
                                itemId = itemIds[i],
                                libraryId = libraryIds[i],
                                mediaType = mediaTypes[i],
                            )
                        }
                    )
                    notificationIds?.forEach { id ->
                        androidx.core.app.NotificationManagerCompat.from(context).cancel(id)
                    }
                }
            }
        }
    }

    /**
     * Runs [block] on this receiver's coroutine scope, keeping the broadcast
     * alive ([goAsync]) until it completes. Both mark-seen actions share this
     * shape — the only difference is the work they do — so it lives here once.
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
