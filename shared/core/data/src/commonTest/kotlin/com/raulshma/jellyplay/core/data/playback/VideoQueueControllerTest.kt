package com.raulshma.jellyplay.core.data.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoQueueControllerTest {

    private fun item(id: String) = VideoQueueItem(itemId = id, title = id)

    @Test
    fun enqueueNext_insertsAtRemainingHead() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))

        controller.enqueueNext(item("next"))

        assertEquals(listOf("next", "a", "b"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun enqueueLast_appendsToTail() {
        val controller = VideoQueueController()
        controller.enqueueNext(item("a"))
        controller.enqueueLast(item("b"))

        assertEquals(listOf("a", "b"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun enqueueNext_movesAlreadyQueuedItemToHead() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))

        controller.enqueueNext(item("b"))

        assertEquals(listOf("b", "a"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun enqueueLast_movesAlreadyQueuedItemToTail() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))
        controller.enqueueLast(item("c"))

        controller.enqueueLast(item("a"))

        assertEquals(listOf("b", "c", "a"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun consumeIfHead_removesOnlyWhenItemIsHead() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))

        assertTrue(controller.consumeIfHead("a"), "the head is consumed")
        assertEquals(listOf("b"), controller.queue.value.map { it.itemId })

        assertFalse(controller.consumeIfHead("a"), "a non-head id is never removed")
        assertEquals(listOf("b"), controller.queue.value.map { it.itemId })

        assertTrue(controller.consumeIfHead("b"), "the remaining head consumes")
        assertTrue(controller.queue.value.isEmpty())
    }

    @Test
    fun consumeIfHead_emptyQueue_returnsFalse() {
        val controller = VideoQueueController()

        assertFalse(controller.consumeIfHead("a"))
    }

    @Test
    fun consumeAt_removesMiddleRow() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))
        controller.enqueueLast(item("c"))

        val taken = controller.consumeAt(1)

        assertEquals("b", taken?.itemId)
        assertEquals(listOf("a", "c"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun move_reordersWithoutSideEffects() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))
        controller.enqueueLast(item("c"))

        controller.move(0, 2)

        assertEquals(listOf("b", "c", "a"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun move_outOfBounds_isNoOp() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))

        controller.move(0, 5)
        controller.move(-1, 0)

        assertEquals(listOf("a"), controller.queue.value.map { it.itemId })
    }

    @Test
    fun removeAt_and_clear() {
        val controller = VideoQueueController()
        controller.enqueueLast(item("a"))
        controller.enqueueLast(item("b"))

        controller.removeAt(0)
        assertEquals(listOf("b"), controller.queue.value.map { it.itemId })

        controller.clear()
        assertTrue(controller.queue.value.isEmpty())
        assertNull(controller.peekHead())
    }
}
