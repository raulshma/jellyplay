package com.raulshma.jellyplay.core.data.playback

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class QueueUndoStackTest {

    private lateinit var undoStack: QueueUndoStack

    @BeforeTest
    fun setUp() {
        undoStack = QueueUndoStack(capacity = 3)
    }

    @Test
    fun pushAndPop_restoresSnapshotsInLifoOrder() {
        val s1 = QueueSnapshot(queue = emptyList(), currentIndex = 0, positionMs = 100L)
        val s2 = QueueSnapshot(queue = emptyList(), currentIndex = 1, positionMs = 200L)

        undoStack.push(s1)
        undoStack.push(s2)

        assertTrue(undoStack.canUndo)
        assertEquals(2, undoStack.size)

        val popped2 = undoStack.pop()
        assertEquals(s2, popped2)
        assertEquals(1, undoStack.size)

        val popped1 = undoStack.pop()
        assertEquals(s1, popped1)
        assertFalse(undoStack.canUndo)
        assertNull(undoStack.pop())
    }

    @Test
    fun push_evictsOldestSnapshotWhenCapacityExceeded() {
        val s1 = QueueSnapshot(queue = emptyList(), currentIndex = 0, positionMs = 100L)
        val s2 = QueueSnapshot(queue = emptyList(), currentIndex = 1, positionMs = 200L)
        val s3 = QueueSnapshot(queue = emptyList(), currentIndex = 2, positionMs = 300L)
        val s4 = QueueSnapshot(queue = emptyList(), currentIndex = 3, positionMs = 400L)

        undoStack.push(s1)
        undoStack.push(s2)
        undoStack.push(s3)
        undoStack.push(s4) // capacity is 3, s1 should be evicted

        assertEquals(3, undoStack.size)
        assertEquals(s4, undoStack.pop())
        assertEquals(s3, undoStack.pop())
        assertEquals(s2, undoStack.pop())
        assertNull(undoStack.pop())
    }

    @Test
    fun clear_resetsStack() {
        val s1 = QueueSnapshot(queue = emptyList(), currentIndex = 0, positionMs = 100L)
        undoStack.push(s1)
        undoStack.clear()

        assertEquals(0, undoStack.size)
        assertFalse(undoStack.canUndo)
        assertNull(undoStack.pop())
    }
}
