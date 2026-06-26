package com.raulshma.jellyplay.core.notification.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression tests for §4.11: [NotificationDispatcher.notificationIdFor] must produce
 * deterministic IDs per (library, item) pair so the system can coalesce notifications
 * instead of stacking duplicates on re-dispatch.
 */
class NotificationDispatcherIdTest {

    @Test
    fun `same library and item index produce the same ID`() {
        val a = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 0)
        val b = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 0)
        assertEquals(a, b)
    }

    @Test
    fun `different libraries produce different IDs`() {
        val a = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 0)
        val b = NotificationDispatcher.notificationIdFor(libraryId = "lib-2", itemIndex = 0)
        assertNotEquals(a, b)
    }

    @Test
    fun `different item indexes within same library produce different IDs`() {
        val a = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 0)
        val b = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `summary ID (itemIndex = -1) is distinct from per-item IDs`() {
        val summary = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = -1)
        val item0 = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 0)
        val item99 = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 99)
        assertNotEquals(summary, item0)
        assertNotEquals(summary, item99)
    }

    @Test
    fun `summary ID is distinct from high item indexes`() {
        // Regression: the old formula used base + 100 for the summary, which collided
        // with item index 100. The slot-based scheme reserves a dedicated summary slot.
        val summary = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = -1)
        val item100 = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 100)
        val item200 = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 200)
        assertNotEquals(summary, item100)
        assertNotEquals(summary, item200)
    }

    @Test
    fun `different libraries never share an ID slot range`() {
        // Regression: the old scheme added the library hash directly to the base, so
        // libraries with adjacent hashes overlapped. Slot blocks must be disjoint.
        val a0 = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 0)
        val aLast = NotificationDispatcher.notificationIdFor(libraryId = "lib-1", itemIndex = 511)
        val b0 = NotificationDispatcher.notificationIdFor(libraryId = "lib-2", itemIndex = 0)
        val bLast = NotificationDispatcher.notificationIdFor(libraryId = "lib-2", itemIndex = 511)
        val aRange = a0..aLast
        val bRange = b0..bLast
        assert(!aRange.contains(b0)) { "lib-2 slot 0 ($b0) must not fall inside lib-1's range $aRange" }
        assert(!bRange.contains(a0)) { "lib-1 slot 0 ($a0) must not fall inside lib-2's range $bRange" }
    }

    @Test
    fun `ID is always greater than or equal to base`() {
        // Sanity: must stay clear of the global-summary ID (5000) so they never collide.
        repeat(100) { idx ->
            val id = NotificationDispatcher.notificationIdFor(libraryId = "any", itemIndex = idx)
            assert(id >= 5001) { "ID $id should be >= 5001 (NOTIFICATION_ID_BASE)" }
        }
    }
}
