package com.raulshma.jellyplay.core.notification.receiver

import com.raulshma.jellyplay.core.data.receiver.BroadcastExtras

/**
 * The decoded intent of a notification-action broadcast — the entry fold of
 * GLOSSARY's "Receiver intake fold": [NotificationActionReceiver]'s onReceive
 * verb/extras decode lifted out of the receiver into pure Kotlin (no Android
 * imports beyond none at all), so the contract the dispatcher's
 * PendingIntents write against is testable on the JVM without Robolectric.
 *
 * Rejection semantics are the hand-rolled ones, byte for byte:
 *  - a missing required extra (or an unknown / absent action) is
 *    [NotificationEntry.Ignored] — the receiver returns without touching any
 *    dependency, exactly as the inline `?: return` guards did;
 *  - [NotificationEntry.MarkAllSeen] keeps the three parallel arrays'
 *    hand-validated shape — `item_ids` must be present AND non-empty, the
 *    other two arrays merely present, and all three equal in length (an
 *    empty `library_ids`/`media_types` next to a non-empty `item_ids` dies
 *    on the length check, as before). `notification_ids` stays optional and
 *    unvalidated — it names shade ids to cancel, never seen-media data.
 *
 * The decode is intentionally arrays-in/arrays-out (consumer-shaped
 * `SeenMediaRecord`s are built by the receiver's dispatch lambda): record
 * stamping keeps its dispatch-time position, so `seenAtEpochMs` semantics
 * are untouched by the extraction.
 */
sealed interface NotificationEntry {
    /** ACTION_OPEN_DETAIL — jump to the item's detail screen. */
    data class OpenDetail(val itemId: String) : NotificationEntry

    /** ACTION_MARK_SEEN — record one item's identity triple as seen. */
    data class MarkSeen(
        val itemId: String,
        val libraryId: String,
        val mediaType: String,
    ) : NotificationEntry

    /**
     * ACTION_MARK_ALL_SEEN — every item of a per-library / global summary.
     * [itemIds]/[libraryIds]/[mediaTypes] are equal-length by construction
     * (the fold validated them); [notificationIds] are the shade ids to
     * dismiss in the same tap, null when the producer sent none.
     */
    data class MarkAllSeen(
        val itemIds: List<String>,
        val libraryIds: List<String>,
        val mediaTypes: List<String>,
        val notificationIds: List<Int>?,
    ) : NotificationEntry

    /** Unknown action, or a known one missing required extras — a no-op. */
    data object Ignored : NotificationEntry
}

/**
 * Folds a notification-action broadcast's [action] + [extras] into the
 * verb to dispatch. Action strings are [NotificationActionReceiver]'s
 * companion constants — the same pair-local literals the manifest intent
 * filter and the dispatcher's PendingIntents carry; they must stay
 * byte-identical.
 */
fun notificationEntry(
    action: String?,
    extras: BroadcastExtras,
): NotificationEntry {
    val itemId = extras.string(NotificationActionReceiver.EXTRA_ITEM_ID)
    return when (action) {
        NotificationActionReceiver.ACTION_OPEN_DETAIL ->
            itemId?.let(NotificationEntry::OpenDetail) ?: NotificationEntry.Ignored
        NotificationActionReceiver.ACTION_MARK_SEEN -> {
            val libraryId = extras.string(NotificationActionReceiver.EXTRA_LIBRARY_ID)
            val mediaType = extras.string(NotificationActionReceiver.EXTRA_MEDIA_TYPE)
            if (itemId == null || libraryId == null || mediaType == null) {
                NotificationEntry.Ignored
            } else {
                NotificationEntry.MarkSeen(itemId, libraryId, mediaType)
            }
        }
        NotificationActionReceiver.ACTION_MARK_ALL_SEEN -> {
            // Per-library / global summary "Mark all seen": three parallel
            // arrays describe every item in the summary; marking them seen
            // prevents re-notification on the next scan. The notification-ids
            // array lets the receiver dismiss the tapped summary plus its
            // child notifications so the shade clears in one tap.
            val itemIds = extras.stringArray(NotificationActionReceiver.EXTRA_ITEM_IDS)
            val libraryIds = extras.stringArray(NotificationActionReceiver.EXTRA_LIBRARY_IDS)
            val mediaTypes = extras.stringArray(NotificationActionReceiver.EXTRA_MEDIA_TYPES)
            if (itemIds.isNullOrEmpty() ||
                libraryIds == null ||
                mediaTypes == null ||
                itemIds.size != libraryIds.size ||
                itemIds.size != mediaTypes.size
            ) {
                NotificationEntry.Ignored
            } else {
                NotificationEntry.MarkAllSeen(
                    itemIds = itemIds.toList(),
                    libraryIds = libraryIds.toList(),
                    mediaTypes = mediaTypes.toList(),
                    notificationIds = extras.intArray(NotificationActionReceiver.EXTRA_NOTIFICATION_IDS)?.toList(),
                )
            }
        }
        else -> NotificationEntry.Ignored
    }
}
