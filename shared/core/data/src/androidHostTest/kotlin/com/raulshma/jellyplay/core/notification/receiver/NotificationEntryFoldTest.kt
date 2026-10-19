package com.raulshma.jellyplay.core.notification.receiver

import com.raulshma.jellyplay.core.data.receiver.BroadcastExtras
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [notificationEntry] — the GLOSSARY "Receiver intake fold" decode of a
 * notification-action broadcast — on the pure JVM (no Robolectric: the fold
 * sees plain [BroadcastExtras], not an Intent). This is the first seam for
 * [NotificationActionReceiver], whose onReceive is now fold + dispatch.
 *
 * Invariants (the receiver's hand-rolled guards, byte for byte):
 * - `ACTION_OPEN_DETAIL` needs only `item_id`; missing → [NotificationEntry.Ignored].
 * - `ACTION_MARK_SEEN` needs the full (item_id, library_id, media_type)
 *   triple; any missing member → Ignored.
 * - `ACTION_MARK_ALL_SEEN` keeps the parallel-array contract: `item_ids`
 *   must be present AND non-empty, `library_ids`/`media_types` merely
 *   present, all three equal-length (so an empty pair next to a non-empty
 *   `item_ids` still dies — on the length check); `notification_ids` is
 *   optional and unvalidated.
 * - Unknown or absent actions are Ignored — the receiver's dispatch never
 *   touches a dependency for them.
 */
class NotificationEntryFoldTest {

    private fun extras(
        strings: Map<String, String?> = emptyMap(),
        stringArrays: Map<String, Array<String>?> = emptyMap(),
        intArrays: Map<String, IntArray?> = emptyMap(),
    ): BroadcastExtras = object : BroadcastExtras {
        override fun string(key: String): String? = strings[key]
        override fun stringArray(key: String): Array<String>? = stringArrays[key]
        override fun intArray(key: String): IntArray? = intArrays[key]
    }

    // ── ACTION_OPEN_DETAIL ───────────────────────────────────────────────

    @Test
    fun `open detail decodes the item id`() {
        val entry = notificationEntry(
            NotificationActionReceiver.ACTION_OPEN_DETAIL,
            extras(strings = mapOf(NotificationActionReceiver.EXTRA_ITEM_ID to "i1")),
        )

        assertEquals(NotificationEntry.OpenDetail("i1"), entry)
    }

    @Test
    fun `open detail without an item id is ignored`() {
        val entry = notificationEntry(
            NotificationActionReceiver.ACTION_OPEN_DETAIL,
            extras(),
        )

        assertEquals(NotificationEntry.Ignored, entry)
    }

    // ── ACTION_MARK_SEEN ─────────────────────────────────────────────────

    @Test
    fun `mark seen decodes the identity triple`() {
        val entry = notificationEntry(
            NotificationActionReceiver.ACTION_MARK_SEEN,
            extras(
                strings = mapOf(
                    NotificationActionReceiver.EXTRA_ITEM_ID to "i1",
                    NotificationActionReceiver.EXTRA_LIBRARY_ID to "lib-1",
                    NotificationActionReceiver.EXTRA_MEDIA_TYPE to "MOVIE",
                ),
            ),
        )

        assertEquals(NotificationEntry.MarkSeen("i1", "lib-1", "MOVIE"), entry)
    }

    @Test
    fun `mark seen with any member of the triple missing is ignored`() {
        val full = mapOf(
            NotificationActionReceiver.EXTRA_ITEM_ID to "i1",
            NotificationActionReceiver.EXTRA_LIBRARY_ID to "lib-1",
            NotificationActionReceiver.EXTRA_MEDIA_TYPE to "MOVIE",
        )
        listOf(
            NotificationActionReceiver.EXTRA_ITEM_ID,
            NotificationActionReceiver.EXTRA_LIBRARY_ID,
            NotificationActionReceiver.EXTRA_MEDIA_TYPE,
        ).forEach { missing ->
            val entry = notificationEntry(
                NotificationActionReceiver.ACTION_MARK_SEEN,
                extras(strings = full - missing),
            )
            assertEquals("missing $missing should ignore", NotificationEntry.Ignored, entry)
        }
    }

    // ── ACTION_MARK_ALL_SEEN ─────────────────────────────────────────────

    private fun markAllExtras(
        itemIds: Array<String>? = arrayOf("i1", "i2"),
        libraryIds: Array<String>? = arrayOf("lib-1", "lib-1"),
        mediaTypes: Array<String>? = arrayOf("MOVIE", "EPISODE"),
        notificationIds: IntArray? = intArrayOf(7, 8, 9),
    ) = extras(
        stringArrays = mapOf(
            NotificationActionReceiver.EXTRA_ITEM_IDS to itemIds,
            NotificationActionReceiver.EXTRA_LIBRARY_IDS to libraryIds,
            NotificationActionReceiver.EXTRA_MEDIA_TYPES to mediaTypes,
        ),
        intArrays = mapOf(NotificationActionReceiver.EXTRA_NOTIFICATION_IDS to notificationIds),
    )

    @Test
    fun `mark all seen decodes the parallel arrays and the cancel set`() {
        val entry = notificationEntry(
            NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
            markAllExtras(),
        )

        assertEquals(
            NotificationEntry.MarkAllSeen(
                itemIds = listOf("i1", "i2"),
                libraryIds = listOf("lib-1", "lib-1"),
                mediaTypes = listOf("MOVIE", "EPISODE"),
                notificationIds = listOf(7, 8, 9),
            ),
            entry,
        )
    }

    @Test
    fun `mark all seen without notification ids still decodes`() {
        val entry = notificationEntry(
            NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
            markAllExtras(notificationIds = null),
        )

        assertEquals(
            NotificationEntry.MarkAllSeen(
                itemIds = listOf("i1", "i2"),
                libraryIds = listOf("lib-1", "lib-1"),
                mediaTypes = listOf("MOVIE", "EPISODE"),
                notificationIds = null,
            ),
            entry,
        )
    }

    @Test
    fun `mark all seen rejects absent and empty item ids`() {
        // Absent array — the producer never sent the batch.
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry(
                NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
                markAllExtras(itemIds = null),
            ),
        )
        // Empty array — the hand-rolled isNullOrEmpty() guard rejected it too
        // (an empty batch is a producer bug, not a zero-item summary).
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry(
                NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
                markAllExtras(itemIds = arrayOf()),
            ),
        )
    }

    @Test
    fun `mark all seen rejects a missing parallel array`() {
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry(
                NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
                markAllExtras(libraryIds = null),
            ),
        )
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry(
                NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
                markAllExtras(mediaTypes = null),
            ),
        )
    }

    @Test
    fun `mark all seen rejects unequal parallel arrays`() {
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry(
                NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
                markAllExtras(libraryIds = arrayOf("lib-1")),
            ),
        )
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry(
                NotificationActionReceiver.ACTION_MARK_ALL_SEEN,
                markAllExtras(mediaTypes = arrayOf("MOVIE", "EPISODE", "SERIES")),
            ),
        )
    }

    // ── unknown verbs ────────────────────────────────────────────────────

    @Test
    fun `unknown and absent actions are ignored`() {
        assertEquals(
            NotificationEntry.Ignored,
            notificationEntry("com.example.NOT_OUR_ACTION", extras()),
        )
        assertEquals(NotificationEntry.Ignored, notificationEntry(null, extras()))
    }
}
