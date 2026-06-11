package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

interface AudioQueueManager {
    val queue: StateFlow<List<AudioQueueItem>>
    val currentIndex: StateFlow<Int>
    val currentPlayingItemId: StateFlow<String?>
    val shuffleMode: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>

    fun playQueue(items: List<AudioQueueItem>, startIndex: Int = 0)
    fun addToQueue(item: AudioQueueItem)
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
