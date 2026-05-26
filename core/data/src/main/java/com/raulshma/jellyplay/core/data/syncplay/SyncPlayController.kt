package com.raulshma.jellyplay.core.data.syncplay

import android.util.Log
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayController @Inject constructor(
    private val apiClient: JellyfinApiClient,
) {
    suspend fun unpause() {
        try {
            apiClient.syncPlayUnpause()
        } catch (e: Exception) {
            Log.w(TAG, "unpause failed", e)
        }
    }

    suspend fun pause() {
        try {
            apiClient.syncPlayPause()
        } catch (e: Exception) {
            Log.w(TAG, "pause failed", e)
        }
    }

    suspend fun seek(positionTicks: Long) {
        try {
            apiClient.syncPlaySeek(positionTicks)
        } catch (e: Exception) {
            Log.w(TAG, "seek failed", e)
        }
    }

    suspend fun stop() {
        try {
            apiClient.syncPlayStop()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        }
    }

    suspend fun setNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ) {
        try {
            apiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)
        } catch (e: Exception) {
            Log.w(TAG, "setNewQueue failed", e)
        }
    }

    suspend fun queue(itemIds: List<String>, mode: String = "Queue") {
        try {
            apiClient.syncPlayQueue(itemIds, mode)
        } catch (e: Exception) {
            Log.w(TAG, "queue failed", e)
        }
    }

    suspend fun nextItem(playlistItemId: String) {
        try {
            apiClient.syncPlayNextItem(playlistItemId)
        } catch (e: Exception) {
            Log.w(TAG, "nextItem failed", e)
        }
    }

    suspend fun previousItem(playlistItemId: String) {
        try {
            apiClient.syncPlayPreviousItem(playlistItemId)
        } catch (e: Exception) {
            Log.w(TAG, "previousItem failed", e)
        }
    }

    suspend fun setPlaylistItem(playlistItemId: String) {
        try {
            apiClient.syncPlaySetPlaylistItem(playlistItemId)
        } catch (e: Exception) {
            Log.w(TAG, "setPlaylistItem failed", e)
        }
    }

    suspend fun removeFromPlaylist(playlistItemId: String) {
        try {
            apiClient.syncPlayRemoveFromPlaylist(playlistItemId)
        } catch (e: Exception) {
            Log.w(TAG, "removeFromPlaylist failed", e)
        }
    }

    suspend fun movePlaylistItem(playlistItemId: String, newIndex: Int) {
        try {
            apiClient.syncPlayMovePlaylistItem(playlistItemId, newIndex)
        } catch (e: Exception) {
            Log.w(TAG, "movePlaylistItem failed", e)
        }
    }

    suspend fun setRepeatMode(mode: SyncPlayRepeatMode) {
        try {
            apiClient.syncPlaySetRepeatMode(mode)
        } catch (e: Exception) {
            Log.w(TAG, "setRepeatMode failed", e)
        }
    }

    suspend fun setShuffleMode(mode: SyncPlayShuffleMode) {
        try {
            apiClient.syncPlaySetShuffleMode(mode)
        } catch (e: Exception) {
            Log.w(TAG, "setShuffleMode failed", e)
        }
    }

    suspend fun setIgnoreWait(ignore: Boolean) {
        try {
            apiClient.syncPlaySetIgnoreWait(ignore)
        } catch (e: Exception) {
            Log.w(TAG, "setIgnoreWait failed", e)
        }
    }

    suspend fun reportReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long,
    ) {
        try {
            apiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId, whenMs)
        } catch (e: Exception) {
            Log.w(TAG, "reportReady failed", e)
        }
    }

    suspend fun reportBuffering(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long,
    ) {
        try {
            apiClient.syncPlayBuffering(positionTicks, isPlaying, playlistItemId, whenMs)
        } catch (e: Exception) {
            Log.w(TAG, "reportBuffering failed", e)
        }
    }

    companion object {
        private const val TAG = "SyncPlayController"
    }
}
