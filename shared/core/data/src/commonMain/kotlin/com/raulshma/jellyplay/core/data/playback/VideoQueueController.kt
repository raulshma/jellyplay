package com.raulshma.jellyplay.core.data.playback

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One queued video item (movie or episode) in the manual up-next queue.
 * Display fields only — playback goes through the item id.
 */
@Immutable
data class VideoQueueItem(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val runTimeTicks: Long? = null,
)

/**
 * The manual up-next queue for VIDEO playback ("play next / play last" from
 * the details screen, drained by the player). Session-scoped by design —
 * unlike the audio queue there is no Room persistence, no engine playlist
 * mirror, and no shuffle/repeat: the list is the single truth, the player
 * consumes the head on advance ([consumeHead]), and the episode chain takes
 * over when the queue empties (queue-first semantics).
 *
 * Suppressed during SyncPlay by the player's advance routing (the group's
 * server-side queue owns the order there).
 *
 * Main-thread contract mirroring [AudioQueueManager]: every mutation is a
 * plain main-thread list write — there is no engine round-trip to sequence.
 */
class VideoQueueController {

    private val _queue = MutableStateFlow<List<VideoQueueItem>>(emptyList())

    /** The remaining (not yet played) queue, in play order. Head plays next. */
    val queue: StateFlow<List<VideoQueueItem>> = _queue.asStateFlow()

    /**
     * Inserts [item] at the head of the remaining queue ("Play next").
     * An item already queued is moved to the head — the queue never holds
     * the same item twice.
     */
    fun enqueueNext(item: VideoQueueItem) {
        _queue.update { queue ->
            listOf(item) + queue.filterNot { it.itemId == item.itemId }
        }
    }

    /**
     * Appends [item] to the tail of the remaining queue ("Play last").
     * An item already queued is moved to the tail — the queue never holds
     * the same item twice.
     */
    fun enqueueLast(item: VideoQueueItem) {
        _queue.update { queue ->
            queue.filterNot { it.itemId == item.itemId } + item
        }
    }

    /** Removes the item at [index] (no-op when out of bounds). */
    fun removeAt(index: Int) {
        _queue.update { queue ->
            queue.toMutableList().apply { if (index in queue.indices) removeAt(index) }
        }
    }

    /** Moves the item at [fromIndex] to [toIndex] (pure reorder — nothing plays). */
    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _queue.update { queue ->
            if (fromIndex !in queue.indices || toIndex !in queue.indices) return@update queue
            queue.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    /** Empties the queue. */
    fun clear() {
        _queue.value = emptyList()
    }

    /** The head without consuming it (what "next" would play), or null. */
    fun peekHead(): VideoQueueItem? = _queue.value.firstOrNull()

    /**
     * Removes and returns the head — the advance verb. Callers that play a
     * mid-list row use [consumeAt] instead.
     */
    fun consumeHead(): VideoQueueItem? {
        val head = _queue.value.firstOrNull() ?: return null
        _queue.update { queue -> queue.drop(1) }
        return head
    }

    /**
     * Removes [itemId] only while it is still the head — the settle-time
     * consume for the queue-first advance: the caller starts the item first
     * and consumes here once the session binds it, so an advance that dies
     * before playback (load error, player closed mid-load) leaves the queue
     * intact instead of silently dropping the item. Returns true when the
     * head was removed.
     */
    fun consumeIfHead(itemId: String): Boolean {
        if (_queue.value.firstOrNull()?.itemId != itemId) return false
        _queue.update { queue ->
            if (queue.firstOrNull()?.itemId == itemId) queue.drop(1) else queue
        }
        return true
    }

    /** Removes and returns the item at [index] ("play now" from the queue sheet). */
    fun consumeAt(index: Int): VideoQueueItem? {
        val item = _queue.value.getOrNull(index) ?: return null
        removeAt(index)
        return item
    }
}
