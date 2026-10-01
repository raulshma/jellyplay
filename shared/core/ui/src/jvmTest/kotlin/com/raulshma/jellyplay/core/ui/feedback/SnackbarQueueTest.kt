package com.raulshma.jellyplay.core.ui.feedback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The [SnackbarQueue] ordering contract, pinned as pure JVM logic (the class
 * is Compose-free on purpose — snapshot state reads fine without a
 * composition):
 *
 *  1. FIFO: queue N messages, the head/pop sequence replays the enqueue order.
 *  2. Pop-after-show invariant: the head is sticky — enqueues during an
 *     in-flight show never displace it, and only an explicit [SnackbarQueue.popHead]
 *     (the "show returned" signal) advances the queue.
 *  3. Duplicate payloads are separate entries ([3, 3] shows twice) — the
 *     companion effect's `size` join, pinned at the data level.
 *  4. The residual ordering edge the detail screen's comment documents, now
 *     pinned as accepted behaviour: a DIRECTLY-following message shown
 *     through another channel can win the host's internal mutex before the
 *     queue's next frame. The queue serializes only its own sequence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarQueueTest {

    @Test
    fun queueN_dequeuesInFifoOrder() {
        val queue = SnackbarQueue<Int>()
        (1..5).forEach(queue::enqueue)

        assertEquals(listOf(1, 2, 3, 4, 5), queue.pending)
        val dequeued = buildList {
            while (queue.head != null) add(queue.popHead()!!)
        }
        assertEquals(listOf(1, 2, 3, 4, 5), dequeued)
        assertNull(queue.head)
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun enqueueDuringInFlightShow_neverDisplacesTheHead() {
        val queue = SnackbarQueue<Int>()
        queue.enqueue(1)

        // Simulated show-in-flight window: head stays sticky while new
        // messages queue behind it.
        assertEquals(1, queue.head)
        queue.enqueue(2)
        queue.enqueue(3)
        assertEquals(1, queue.head, "enqueue must not preempt the in-flight head")
        assertEquals(3, queue.size)

        // Only the explicit pop-after-show advances the queue.
        assertEquals(1, queue.popHead())
        assertEquals(2, queue.head)
        assertEquals(2, queue.popHead())
        assertEquals(3, queue.head)
        assertEquals(3, queue.popHead())
        assertNull(queue.popHead())
    }

    @Test
    fun duplicateCounts_eachGetTheirOwnShowing() {
        val queue = SnackbarQueue<Int>()
        queue.enqueue(3)
        queue.enqueue(3)

        assertEquals(3, queue.head)
        assertEquals(3, queue.popHead())
        // The head repeats after the first pop — the reason the companion
        // effect keys its relaunch on size, not head-equals alone.
        assertEquals(3, queue.head)
        assertEquals(3, queue.popHead())
        assertNull(queue.head)
    }

    @Test
    fun popHeadOnEmptyQueue_isADefensiveNoOp() {
        val queue = SnackbarQueue<String>()
        assertNull(queue.popHead())
        queue.enqueue("only")
        assertEquals("only", queue.popHead())
        assertNull(queue.popHead())
    }

    @Test
    fun directlyFollowingExternalShow_winsTheHostMutex_queueNeverPreemptsAndKeepsItsFifo() = runTest {
        // SnackbarHostState serializes shows behind an internal mutex. The
        // documented residual edge: a message shown directly through the host
        // (DetailMessage.Text path) can acquire that mutex before the queue
        // effect's next frame. Pinned as accepted behaviour: the queue can
        // neither preempt an in-flight external show nor lose its own
        // sequence — its head waits for the host, and pops only after its
        // own show returned.
        val hostMutex = Mutex()
        val externalShowInFlight = CompletableDeferred<Unit>()
        val releaseExternalShow = CompletableDeferred<Unit>()
        val shown = mutableListOf<String>()
        val queue = SnackbarQueue<Int>()

        queue.enqueue(1)
        queue.enqueue(2)

        // External message wins the mutex first and holds it.
        val external = launch {
            hostMutex.withLock {
                shown += "external:text"
                externalShowInFlight.complete(Unit)
                releaseExternalShow.await()
            }
        }
        externalShowInFlight.await()

        // The queue effect: show head 1 — blocked on the host mutex until the
        // external show finishes. The head is NOT popped while waiting.
        val effect = launch {
            val head = queue.head!!
            hostMutex.withLock {
                shown += "queued:$head"
            }
            queue.popHead()
        }

        advanceUntilIdle()
        assertTrue(queue.head == 1, "waiting for the host must not advance the queue")
        releaseExternalShow.complete(Unit)
        advanceUntilIdle()
        external.join()
        effect.join()

        assertEquals(listOf("external:text", "queued:1"), shown)
        // The pop-after-show advanced exactly one position — 2 still owed.
        assertEquals(2, queue.head)
    }

    @Test
    fun popAfterShowSequence_showsEveryQueuedMessageExactlyOnce() = runTest {
        // End-to-end driver of the contract: the one-at-a-time show→pop loop.
        val queue = SnackbarQueue<Int>()
        (1..4).forEach(queue::enqueue)
        val shown = mutableListOf<Int>()

        withTimeout(5_000) {
            while (queue.head != null) {
                val head = queue.head!!
                shown.add(head) // the "showSnackbar" step
                queue.popHead() // strictly after the show returned
            }
        }

        assertEquals(listOf(1, 2, 3, 4), shown)
        assertNull(queue.popHead())
    }
}
