package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode

class SyncPlayQueueCore constructor(
    private val timeSyncManager: TimeSyncManager,
) {
    @Volatile
    var lastPlayQueueUpdate: SyncPlayQueueUpdateData? = null
        private set

    val currentPlaylistItemIds: List<String>
        get() = lastPlayQueueUpdate?.playlistItemIds ?: emptyList()

    val currentItemIds: List<String>
        get() = lastPlayQueueUpdate?.itemIds ?: emptyList()

    val playingItemId: String?
        get() = lastPlayQueueUpdate?.playingItemId

    val playingPlaylistItemId: String?
        get() = lastPlayQueueUpdate?.playingPlaylistItemId

    val repeatMode: SyncPlayRepeatMode
        get() = lastPlayQueueUpdate?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE

    val shuffleMode: SyncPlayShuffleMode
        get() = lastPlayQueueUpdate?.shuffleMode ?: SyncPlayShuffleMode.SORTED

    @Volatile
    private var cachedPlaylistItemMap: Map<String, String>? = null

    val playlistItemMap: Map<String, String>
        get() {
            cachedPlaylistItemMap?.let { return it }
            val update = lastPlayQueueUpdate ?: return emptyMap()
            val map = mutableMapOf<String, String>()
            for (i in update.playlistItemIds.indices) {
                if (i < update.itemIds.size) {
                    map[update.playlistItemIds[i]] = update.itemIds[i]
                }
            }
            val result = map.toMap()
            cachedPlaylistItemMap = result
            return result
        }

    fun updatePlayQueue(data: SyncPlayQueueUpdateData): Boolean {
        val current = lastPlayQueueUpdate
        if (current != null && data.lastUpdateMs > 0 && current.lastUpdateMs > 0) {
            if (data.lastUpdateMs <= current.lastUpdateMs) {
                Log.d(TAG, "Ignoring stale queue update (new=${data.lastUpdateMs}, current=${current.lastUpdateMs})")
                return false
            }
        }
        cachedPlaylistItemMap = null
        lastPlayQueueUpdate = data
        Log.d(TAG, "Queue updated: reason=${data.reason}, playing=${data.playingItemId}, items=${data.itemIds.size}")
        return true
    }

    fun shouldLoadItem(currentItemId: String?): Boolean {
        val update = lastPlayQueueUpdate ?: return false
        if (currentItemId == null) return update.playingItemId.isNotBlank()
        return currentItemId != update.playingItemId
    }

    fun getStartPositionTicks(lastPlaybackCommand: SyncPlayPlaybackCommand?): Long {
        val update = lastPlayQueueUpdate ?: return 0L
        if (lastPlaybackCommand != null && lastPlaybackCommand.whenMs > update.whenMs) {
            return estimateCurrentTicks(lastPlaybackCommand.positionTicks, lastPlaybackCommand.whenMs)
        }
        return estimateCurrentTicks(update.startPositionTicks, update.lastUpdateMs)
    }

    fun estimateCurrentTicks(ticks: Long, whenMs: Long): Long {
        val remoteNow = timeSyncManager.remoteNow()
        val elapsedMs = remoteNow - whenMs
        return ticks + elapsedMs * 10_000
    }

    fun clear() {
        cachedPlaylistItemMap = null
        lastPlayQueueUpdate = null
    }

    companion object {
        private const val TAG = "SyncPlayQueueCore"
    }
}
