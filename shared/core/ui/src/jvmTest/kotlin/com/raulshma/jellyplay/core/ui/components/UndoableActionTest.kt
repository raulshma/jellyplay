package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the [UndoableAction] value contract and the [undoActionChannel] factory:
 * the returned channel is `Channel.BUFFERED`, so ViewModels may `trySend` any
 * number of actions non-suspendingly before (or without) a collector, and the
 * screen later receives every action exactly once in FIFO order with its
 * `onUndo` lambda intact. The composition-bound snackbar plumbing
 * ([CollectUndoActions] / [UndoSnackbarOverlay]) is not covered here.
 */
class UndoableActionTest {

    @Test
    fun action_defaults_labelNullAndShortDuration() {
        val action = UndoableAction(message = "Removed 'Pilot'", onUndo = {})

        assertEquals(null, action.actionLabel, "actionLabel defaults to null so the shared core_undo label applies")
        assertEquals(SnackbarDuration.Short, action.duration, "recoverable actions stay brief by default")
    }

    @Test
    fun action_carriesMessageLabelDurationAndUndoLambda() {
        var undoCount = 0
        val action = UndoableAction(
            message = "m",
            onUndo = { undoCount++ },
            actionLabel = "Restore",
            duration = SnackbarDuration.Long,
        )

        assertEquals("m", action.message)
        assertEquals("Restore", action.actionLabel)
        assertEquals(SnackbarDuration.Long, action.duration)
        // The captured snapshot lambda is the revert mechanism: invoking it runs.
        action.onUndo()
        assertEquals(1, undoCount)
    }

    @Test
    fun factory_returnsBufferedChannel_acceptingOffersWithoutReceiver() {
        val channel: Channel<UndoableAction> = undoActionChannel()

        // No receiver attached at all: BUFFERED must accept every offer
        // non-suspendingly (emit-side ViewModels never block).
        repeat(5) { i ->
            val result = channel.trySend(UndoableAction(message = "a$i", onUndo = {}))
            assertTrue(result.isSuccess, "offer $i must be accepted into the buffer")
        }
    }

    @Test
    fun factory_offersThenReceive_roundTripsInFifoOrder() = runTest {
        val channel = undoActionChannel()
        val posted = (1..3).map { UndoableAction(message = "a$it", onUndo = {}) }

        posted.forEach { action -> channel.trySend(action) }

        assertEquals(posted[0], channel.receive())
        assertEquals(posted[1], channel.receive())
        assertEquals(posted[2], channel.receive())
    }

    @Test
    fun factory_receiveAsFlow_deliversEachActionExactlyOnceInOrder() = runTest {
        val channel = undoActionChannel()
        val posted = (1..3).map { UndoableAction(message = "a$it", onUndo = {}) }
        posted.forEach { action -> channel.trySend(action) }

        val received = channel.receiveAsFlow().take(3).toList()

        assertEquals(posted, received)
    }
}
