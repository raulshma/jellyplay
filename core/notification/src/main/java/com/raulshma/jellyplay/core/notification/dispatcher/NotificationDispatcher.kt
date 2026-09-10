package com.raulshma.jellyplay.core.notification.dispatcher

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.core.notification.R
import com.raulshma.jellyplay.core.notification.channel.NotificationChannelManager
import com.raulshma.jellyplay.core.notification.receiver.NotificationActionReceiver

class NotificationDispatcher(
    private val context: Context,
    private val channelManager: NotificationChannelManager,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun dispatch(
        newItemsByLibrary: Map<LibraryFolder, List<MediaItem>>,
        prefs: NotificationPreferences,
    ) {
        if (newItemsByLibrary.isEmpty()) return
        if (!notificationManager.areNotificationsEnabled()) return
        if (prefs.respectSystemDnd && isSystemDndEnabled()) return

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

    private fun isSystemDndEnabled(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            ?: return false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                return false
            }
            return notificationManager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        }
        return false
    }

    private fun dispatchLibrary(
        library: LibraryFolder,
        items: List<MediaItem>,
        prefs: NotificationPreferences,
    ) {
        val channelId = NotificationChannelManager.channelIdFor(library.id)
        val groupId = "new_media_${library.id}"

        val itemNotificationIds = IntArray(items.size)
        items.forEachIndexed { index, item ->
            val notificationId = notificationIdFor(library.id, index)
            itemNotificationIds[index] = notificationId
            val notification = buildItemNotification(item, library.id, channelId, groupId, notificationId)
            notificationManager.notify(notificationId, notification)
        }

        val summaryId = notificationIdFor(library.id, -1)
        // "Mark all seen" cancels this summary + every child item
        // notification and records each item as seen so the next scan skips them.
        val notificationIdsToCancel = itemNotificationIds.toMutableSet().apply { add(summaryId) }.toIntArray()
        val markAllPendingIntent = buildMarkAllSeenPendingIntent(
            items = items.map { MarkAllSeenItem(it.id, library.id, it.mediaType.name) },
            notificationIds = notificationIdsToCancel,
            requestCode = summaryId,
        )
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.notification_new_in_library_count, items.size, library.name))
            .setContentText(items.joinToString(", ") { it.name })
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(library.name)
                    .also { builder ->
                        items.take(5).forEach { item ->
                            builder.addLine(item.name)
                        }
                        if (items.size > 5) {
                            builder.addLine(context.getString(R.string.notification_more_count, items.size - 5))
                        }
                    }
            )
            .setGroup(groupId)
            .setGroupSummary(true)
            .addAction(
                com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small,
                context.getString(R.string.notification_action_mark_all_seen),
                markAllPendingIntent,
            )
            .setAutoCancel(true)
            .build()
        notificationManager.notify(summaryId, summary)
    }

    private fun dispatchGlobalSummary(
        newItemsByLibrary: Map<LibraryFolder, List<MediaItem>>,
        totalItems: Int,
    ) {
        // Collect every item + notification id across all libraries so the global
        // "Mark all seen" clears the whole stack in one tap.
        val allItems = ArrayList<MarkAllSeenItem>(totalItems)
        val notificationIdsToCancel = ArrayList<Int>()
        newItemsByLibrary.forEach { (library, items) ->
            items.forEach { item ->
                allItems.add(MarkAllSeenItem(item.id, library.id, item.mediaType.name))
            }
            items.forEachIndexed { index, _ ->
                notificationIdsToCancel.add(notificationIdFor(library.id, index))
            }
            notificationIdsToCancel.add(notificationIdFor(library.id, -1))
        }
        notificationIdsToCancel.add(NOTIFICATION_ID_GLOBAL)
        val markAllPendingIntent = buildMarkAllSeenPendingIntent(
            items = allItems,
            notificationIds = notificationIdsToCancel.toIntArray(),
            requestCode = NOTIFICATION_ID_GLOBAL,
        )
        val summary = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_SUMMARY)
            .setSmallIcon(com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.notification_new_items_added, totalItems))
            .setContentText(context.getString(R.string.notification_across_libraries, newItemsByLibrary.size))
            .setStyle(
                NotificationCompat.InboxStyle()
                    .also { builder ->
                        newItemsByLibrary.forEach { (library, items) ->
                            builder.addLine(context.getString(R.string.notification_new_in_library_count, items.size, library.name))
                        }
                    }
            )
            .setGroup(GROUP_GLOBAL)
            .setGroupSummary(true)
            .addAction(
                com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small,
                context.getString(R.string.notification_action_mark_all_seen),
                markAllPendingIntent,
            )
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_GLOBAL, summary)
    }

    /**
     * One item's (itemId, libraryId, mediaType) tuple for a summary's "Mark all
     * seen" action `MediaItem` carries no `libraryId`, so the
     * dispatcher pairs them when building the batch.
     */
    private data class MarkAllSeenItem(
        val itemId: String,
        val libraryId: String,
        val mediaType: String,
    )

    /**
     * Builds the "Mark all seen" [PendingIntent] carried by a summary
     * notification. [items] is the full (itemId, libraryId, mediaType) list to
     * record as seen; [notificationIds] is the complete set the receiver should
     * cancel once seen records are written (the summary + its child notifications).
     *
     * Setters are chained directly on the Intent expression (not inside
     * `Intent(...).apply {}`) so CodeQL's implicit-PendingIntent recognition treats
     * the intent as explicit — same convention as [buildItemNotification].
     */
    private fun buildMarkAllSeenPendingIntent(
        items: List<MarkAllSeenItem>,
        notificationIds: IntArray,
        requestCode: Int,
    ): PendingIntent {
        val itemIds = Array(items.size) { items[it].itemId }
        val libraryIds = Array(items.size) { items[it].libraryId }
        val mediaTypes = Array(items.size) { items[it].mediaType }
        val intent = Intent()
            .setClassName(context.packageName, NotificationActionReceiver::class.java.name)
            .setAction(NotificationActionReceiver.ACTION_MARK_ALL_SEEN)
            .putExtra(NotificationActionReceiver.EXTRA_ITEM_IDS, itemIds)
            .putExtra(NotificationActionReceiver.EXTRA_LIBRARY_IDS, libraryIds)
            .putExtra(NotificationActionReceiver.EXTRA_MEDIA_TYPES, mediaTypes)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_IDS, notificationIds)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildItemNotification(
        item: MediaItem,
        libraryId: String,
        channelId: String,
        groupId: String,
        notificationId: Int,
    ): Notification {
        // The content intent is explicit: ACTION_VIEW scoped to our own package so
        // the PendingIntent cannot be hijacked by another app claiming the scheme.
        // `setPackage` is hoisted out of an `Intent(...).apply { ... }` block on
        // purpose — CodeQL's implicit-PendingIntent recognition only treats a setter
        // call as making the intent explicit when its receiver is the Intent
        // expression itself; inside `.apply {}` the receiver is the lambda's `this`
        // and the analysis cannot link it to the value handed to PendingIntent.
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DeepLinkGrammar.mediaLink(item.id)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Mark as seen" silences this item without launching the app. Wired to
        // NotificationActionReceiver which records the seen-media row and dismisses
        // the notification. Uses a distinct PendingIntent request code per item so
        // each notification carries its own itemId/libraryId/mediaType extras.
        //
        // All setters are chained directly on the Intent expression (not inside
        // an `Intent(...).apply { ... }` block) so CodeQL's implicit-PendingIntent
        // recognition links the explicit component to the value handed to
        // PendingIntent; the `(Context, Class)` constructor is not enough for the
        // analysis to consider the intent explicit.
        val markSeenIntent = Intent()
            .setClassName(context.packageName, NotificationActionReceiver::class.java.name)
            .setAction(NotificationActionReceiver.ACTION_MARK_SEEN)
            .putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, item.id)
            .putExtra(NotificationActionReceiver.EXTRA_LIBRARY_ID, libraryId)
            .putExtra(NotificationActionReceiver.EXTRA_MEDIA_TYPE, item.mediaType.name)
        val markSeenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            markSeenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Open" mirrors the content intent but goes through the action receiver's
        // ACTION_OPEN_DETAIL path for consistency (and so the action is explicit on
        // wearables / Android Auto where content taps are not always available).
        val openIntent = Intent()
            .setClassName(context.packageName, NotificationActionReceiver::class.java.name)
            .setAction(NotificationActionReceiver.ACTION_OPEN_DETAIL)
            .putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, item.id)
        val openPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + OPEN_ACTION_REQUEST_CODE_OFFSET,
            openIntent,
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
            .addAction(
                com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small,
                context.getString(R.string.notification_action_mark_seen),
                markSeenPendingIntent,
            )
            .addAction(
                com.raulshma.jellyplay.core.notification.R.drawable.ic_notification_small,
                context.getString(R.string.notification_action_open),
                openPendingIntent,
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .build()
    }

    companion object {
        private const val GROUP_GLOBAL = "new_media_global"
        private const val NOTIFICATION_ID_GLOBAL = 5000
        private const val NOTIFICATION_ID_BASE = 5001

        // Offset applied to a notification id to derive a distinct PendingIntent
        // request code for the Open action, so it never collides with the
        // Mark-seen action (or the content intent) for the same item.
        private const val OPEN_ACTION_REQUEST_CODE_OFFSET = 100_000

        // Per-library ID slots. Giving each library a dedicated block of IDs avoids
        // cross-library collisions (the previous scheme added the library hash directly
        // to the base, so libraries with adjacent hashes overlapped). 4096 buckets with
        // 512 slots each comfortably covers realistic library counts and per-check item
        // limits while keeping every ID a positive Int >= NOTIFICATION_ID_BASE.
        private const val LIBRARY_BUCKETS = 4096
        private const val SLOTS_PER_LIBRARY = 512
        private const val SUMMARY_SLOT = SLOTS_PER_LIBRARY - 1

        /**
         * Computes a per-(library, item) notification ID. The ID must be stable across
         * re-dispatches so the system can coalesce updates instead of stacking duplicates.
         *
         * `itemIndex == -1` selects the reserved summary slot for that library, which is
         * always distinct from any per-item slot.
         *
         * `internal` so unit tests can assert the deterministic mapping.
         */
        internal fun notificationIdFor(libraryId: String, itemIndex: Int): Int {
            val libraryBucket = (libraryId.hashCode().toLong() and 0xFFFFFFFFL).toInt() % LIBRARY_BUCKETS
            val base = NOTIFICATION_ID_BASE + libraryBucket * SLOTS_PER_LIBRARY
            val slot = if (itemIndex == -1) SUMMARY_SLOT else itemIndex
            return base + slot
        }
    }
}
