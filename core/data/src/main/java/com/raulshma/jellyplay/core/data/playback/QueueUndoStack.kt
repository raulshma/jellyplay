package com.raulshma.jellyplay.core.data.playback

import androidx.compose.runtime.Immutable

/**
 * Captured state of the audio queue at the moment just before a destructive
 * queue operation, used to restore it on undo.
 */
@Immutable
data class QueueSnapshot(
    val queue: List<AudioQueueItem>,
    val currentIndex: Int,
    val positionMs: Long,
)

/**
 * Bounded LIFO ring buffer of the most recent [QueueSnapshot]s, enabling undo
 * of destructive queue operations (clear / remove / skip / move). Capped at
 * [capacity] (default 10) so memory stays bounded across a long session.
 *
 * Not thread-safe: every access happens on the application main thread under
 * the [AudioQueueManager] contract, so no synchronization is needed.
 */
class QueueUndoStack(private val capacity: Int = DEFAULT_CAPACITY) {

    private val deque = ArrayDeque<QueueSnapshot>()

    /** Number of undoable operations currently buffered. */
    val size: Int get() = deque.size

    /** True if [undo] has a snapshot to restore. */
    val canUndo: Boolean get() = deque.isNotEmpty()

    /** Records a pre-mutation [snapshot]. Evicts the oldest when full. */
    fun push(snapshot: QueueSnapshot) {
        if (deque.size >= capacity) deque.removeFirst()
        deque.addLast(snapshot)
    }

    /** Removes and returns the most recent snapshot, or null if empty. */
    fun pop(): QueueSnapshot? = if (deque.isEmpty()) null else deque.removeLast()

    /** Discards all buffered snapshots (e.g. when a fresh queue is loaded). */
    fun clear() = deque.clear()

    companion object {
        const val DEFAULT_CAPACITY = 10
    }
}

/**
 * Describes a destructive queue operation so the UI can surface an "Undo"
 * affordance. Emitted as a one-shot event by [AudioPlaybackManager].
 */
@Immutable
sealed interface QueueUndoEvent {
    data object QueueCleared : QueueUndoEvent
    data class ItemRemoved(val item: AudioQueueItem) : QueueUndoEvent
    data class ItemMoved(val item: AudioQueueItem) : QueueUndoEvent
    data object SkippedToNext : QueueUndoEvent
    data object SkippedToPrevious : QueueUndoEvent
}
