package com.raulshma.jellyplay.core.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var seenMediaRepository: SeenMediaRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_DETAIL -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                val deepLink = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("jellyplay://media/$itemId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    setPackage(context.packageName)
                }
                context.startActivity(deepLink)
            }
            ACTION_MARK_SEEN -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                val libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID) ?: return
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: return
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        seenMediaRepository.markAsSeen(
                            itemId = itemId,
                            libraryId = libraryId,
                            mediaType = mediaType,
                        )
                    } finally {
                        pendingResult.finish()
                        scope.cancel()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_DETAIL = "com.raulshma.jellyplay.notification.OPEN_DETAIL"
        const val ACTION_MARK_SEEN = "com.raulshma.jellyplay.notification.MARK_SEEN"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_LIBRARY_ID = "library_id"
        const val EXTRA_MEDIA_TYPE = "media_type"
    }
}
