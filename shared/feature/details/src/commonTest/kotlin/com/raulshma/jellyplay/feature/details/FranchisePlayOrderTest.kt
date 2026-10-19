package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure tests for [franchisePlayOrder] — the "Play collection in order"
 * plan. Pins the first-unwatched start rule and the queue tail that the
 * caller appends via `VideoQueueController.enqueueLast` (queue-first
 * semantics; the manual queue is suppressed under SyncPlay by the player's
 * advance routing — the contract documented on VideoQueueController itself).
 * No Android / Compose.
 */
class FranchisePlayOrderTest {

    private fun member(id: String, played: Boolean = false) =
        MediaItem(id = id, name = id, mediaType = MediaType.MOVIE, isPlayed = played)

    @Test
    fun `starts at the first unwatched member and queues the rest in order`() {
        val order = franchisePlayOrder(
            listOf(
                member("a", played = true),
                member("b", played = true),
                member("c"),
                member("d"),
            ),
        )!!

        assertEquals("c", order.start.id)
        // The tail after the start entry — the play order the queue drains.
        assertEquals(listOf("d"), order.queuedTail.map { it.id })
    }

    @Test
    fun `all-unwatched collection starts at the head`() {
        val order = franchisePlayOrder(listOf(member("a"), member("b"), member("c")))!!

        assertEquals("a", order.start.id)
        assertEquals(listOf("b", "c"), order.queuedTail.map { it.id })
    }

    @Test
    fun `fully watched collection replays from the top`() {
        val order = franchisePlayOrder(listOf(member("a", played = true), member("b", played = true)))!!

        // Nothing unwatched → the first member starts (replay semantics);
        // the tail still queues in order.
        assertEquals("a", order.start.id)
        assertEquals(listOf("b"), order.queuedTail.map { it.id })
    }

    @Test
    fun `members without user data read as unwatched - plain in-order play`() {
        // getCollectionItems members carry server user data on every list
        // projection; a payload that somehow arrives without it (isPlayed
        // default false) degrades to the plain in-order play — documented
        // fallback, not a special case.
        val order = franchisePlayOrder(listOf(member("a"), member("b")))!!

        assertEquals("a", order.start.id)
        assertEquals(listOf("b"), order.queuedTail.map { it.id })
    }

    @Test
    fun `single-member collection starts it with an empty tail`() {
        val order = franchisePlayOrder(listOf(member("solo")))!!

        assertEquals("solo", order.start.id)
        assertEquals(emptyList(), order.queuedTail)
    }

    @Test
    fun `empty members yield no plan`() {
        assertNull(franchisePlayOrder(emptyList()))
    }
}
