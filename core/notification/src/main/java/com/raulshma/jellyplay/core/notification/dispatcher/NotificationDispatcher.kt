package com.raulshma.jellyplay.core.notification.dispatcher

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.channel.NotificationChannelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelManager: NotificationChannelManager,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun dispatch(
        newItemsByLibrary: Map<LibraryFolder, List<MediaItem>>,
        prefs: NotificationPreferences,
    ) {
        if (newItemsByLibrary.isEmpty()) return
        if (!notificationManager.areNotificationsEnabled()) return

        channelManager.ensureSummaryChannel()

        val validLibraryIds = mutableSetOf<String>()
        var globalTotal = 0

        newItemsByLibrary.forEach { (library, items) ->
            validLibraryIds.add(library.id)
            channelManager.ensureChannel(library.id, library.name, prefs)
            dispatchLibrary(library, items, prefs)
            globalTotal += items.size
        }

        if (newItemsByLibrary.size > 1 && globalTotal > 0) {
            dispatchGlobalSummary(newItemsByLibrary, globalTotal)
        }

        channelManager.deleteStaleChannels(validLibraryIds)
    }

    private fun dispatchLibrary(
        library: LibraryFolder,
        items: List<MediaItem>,
        prefs: NotificationPreferences,
    ) {
        val channelId = NotificationChannelManager.channelIdFor(library.id)
        val groupId = "new_media_${library.id}"

        items.forEachIndexed { index, item ->
            val notificationId = notificationIdFor(library.id, index)
            val notification = buildItemNotification(item, channelId, groupId, notificationId)
            notificationManager.notify(notificationId, notification)
        }

        val summaryId = notificationIdFor(library.id, -1)
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small)
            .setContentTitle("${items.size} new in ${library.name}")
            .setContentText(items.joinToString(", ") { it.name })
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(library.name)
                    .also { builder ->
                        items.take(5).forEach { item ->
                            builder.addLine(item.name)
                        }
                        if (items.size > 5) {
                            builder.addLine("+${items.size - 5} more")
                        }
                    }
            )
            .setGroup(groupId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(summaryId, summary)
    }

    private fun dispatchGlobalSummary(
        newItemsByLibrary: Map<LibraryFolder, List<MediaItem>>,
        totalItems: Int,
    ) {
        val summary = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_SUMMARY)
            .setSmallIcon(com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small)
            .setContentTitle("$totalItems new items added")
            .setContentText("Across ${newItemsByLibrary.size} libraries")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .also { builder ->
                        newItemsByLibrary.forEach { (library, items) ->
                            builder.addLine("${items.size} new in ${library.name}")
                        }
                    }
            )
            .setGroup(GROUP_GLOBAL)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_GLOBAL, summary)
    }

    private fun buildItemNotification(
        item: MediaItem,
        channelId: String,
        groupId: String,
        notificationId: Int,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("jellyplay://media/${item.id}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val mediaTypeLabel = item.mediaType.name.lowercase().replaceFirstChar { it.uppercase() }
        val subText = buildString {
            append(mediaTypeLabel)
            item.year?.let { append(" \u00B7 $it") }
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small)
            .setContentTitle(item.name)
            .setContentText(subText)
            .setGroup(groupId)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .build()
    }

    companion object {
        private const val GROUP_GLOBAL = "new_media_global"
        private const val NOTIFICATION_ID_GLOBAL = 5000
        private const val NOTIFICATION_ID_BASE = 5001

        private fun notificationIdFor(libraryId: String, itemIndex: Int): Int {
            val base = NOTIFICATION_ID_BASE + (libraryId.hashCode() and 0x0000FFFF)
            return if (itemIndex == -1) base + 100 else base + itemIndex
        }
    }
}
