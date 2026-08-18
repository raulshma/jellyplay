package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for mutating the audio play queue.
 *
 * **Thread contract:** every method on this interface MUST be invoked from
 * the application main thread. The implementation forwards mutations
 * directly to the underlying [androidx.media3.exoplayer.ExoPlayer] which
 * throws `IllegalStateException` when accessed off the application
 * `Looper`. In DEBUG builds the implementation asserts this contract at
 * the entry of each method so off-main callers fail loudly instead of
 * crashing inside ExoPlayer.
 *
 * Background-thread callers (e.g. SyncPlay queue mutations, work-manager
 * callbacks) must hop to `Dispatchers.Main` before invoking any method
 * here.
 */
interface AudioQueueManager {
    val queue: StateFlow<List<AudioQueueItem>>
    val currentIndex: StateFlow<Int>
    val currentPlayingItemId: StateFlow<String?>
    val shuffleMode: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>

    fun playQueue(items: List<AudioQueueItem>, startIndex: Int = 0)
    fun addToQueue(item: AudioQueueItem)

    /**
     * Bulk append. One queue emission + one player mutation instead of N, so
     * the queue is persisted (full-list DELETE+INSERT) once rather than
     * O(N²) rows across N transactions.
     */
    fun addToQueueAll(items: List<AudioQueueItem>)
    fun removeFromQueue(index: Int)
    fun clearQueue()
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun playFromQueue(index: Int)
    fun skipToNext()
    fun skipToPrevious()
    fun toggleShuffle()
    fun cycleRepeatMode()
    fun setRepeatMode(mode: Int)
    fun setShuffleMode(enabled: Boolean)
}
