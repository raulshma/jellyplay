package com.raulshma.jellyplay.core.ui.feedback

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf

/**
 * FIFO snackbar queue with one-at-a-time serialization — the pure-logic half
 * of the queued-episodes pattern (extracted from the media-detail screen,
 * which burst-queues plural "N episodes queued" messages).
 *
 * Why not [SnackbarHostState.showSnackbar] per message: a burst of enqueues
 * must queue, not overwrite — a bare "current message" field loses earlier
 * counts before the consumer's recomposition drops them, and an eager
 * show-per-collect loop interleaves arbitrary host messages between them.
 * The queue restores the collect-loop serialization with an explicit
 * pop-after-show invariant:
 *
 *  - [enqueue] appends; it never reorders and never preempts the in-flight
 *    head (while a message is showing, it stays the [head] until popped).
 *  - the consumer shows [head] and calls [popHead] ONLY after the show
 *    returned — dropping the head earlier would silently skip a message.
 *  - duplicate payloads are separate queue entries ([3, 3] shows twice),
 *    which is why the companion effect keys its relaunch on [size] too.
 *
 * Residual ordering edge, pinned as accepted behaviour (see
 * [SnackbarQueueEffect]): between the effect's frames a DIRECTLY-following
 * message shown through another channel (plain `showSnackbar`) can still win
 * the host's internal mutex first. The queue serializes only its own
 * sequence — that transient inversion is display-order cosmetic only.
 *
 * Compose-free ordering logic (snapshot state reads fine on plain JVM), so
 * the FIFO discipline is testable without a composition ([SnackbarQueueTest]).
 * The payload is generic: the detail screen queues counts and resolves them
 * to plural text in composition; a host that already holds strings enqueues
 * them directly.
 */
class SnackbarQueue<T : Any> {

    private val _pending = mutableStateListOf<T>()

    /** Snapshot of the queued-not-yet-shown messages, head first. */
    val pending: List<T> get() = _pending.toList()

    /** Number of queued messages — the head's duplicates included. */
    val size: Int get() = _pending.size

    /** The message currently owed a showing; null when the queue is empty. */
    val head: T? get() = _pending.firstOrNull()

    /**
     * Queues [message] behind every message already owed a showing. The head
     * is never displaced by an enqueue.
     */
    fun enqueue(message: T) {
        _pending.add(message)
    }

    /**
     * The pop-after-show invariant's second half: drop the head. Call ONLY
     * after the head's snackbar finished showing (the show call returned) —
     * returns the popped message, or null when the queue was already empty
     * (a defensive no-op, not a contract to rely on).
     */
    fun popHead(): T? = _pending.removeFirstOrNull()
}

/**
 * The composition glue that drives a [SnackbarQueue] into a
 * [SnackbarHostState]: shows the [head] (resolved to text in composition via
 * [resolve]) and pops it only after the show returns.
 *
 * The [resolve] injection exists because CMP's suspend plural resolver is
 * internal: a count payload cannot resolve its plural string inside a plain
 * coroutine, so the effect resolves it here (the @Composable
 * pluralStringResource) and hands the host only finished text.
 *
 * [queue.size] joins the relaunch key so duplicate payloads ([3, 3]) each get
 * their own showing after the previous pop: when the head repeats, head-equals
 * alone would not restart the effect. The same key means an enqueue during an
 * in-flight show restarts the effect too — the head is not displaced, so the
 * SAME snackbar displays again from scratch once the restart lands — a
 * harmless repetition (the alternative, keying on head alone, would drop the
 * duplicate's second showing). An empty queue cancels the in-flight effect —
 * harmless, because the show already returned before the pop that emptied
 * the queue.
 */
@Composable
fun <T : Any> SnackbarQueueEffect(
    queue: SnackbarQueue<T>,
    snackbarHostState: SnackbarHostState,
    resolve: @Composable (T) -> String,
) {
    val head = queue.head
    if (head != null) {
        val text = resolve(head)
        LaunchedEffect(head, queue.size) {
            snackbarHostState.showSnackbar(text)
            queue.popHead()
        }
    }
}
