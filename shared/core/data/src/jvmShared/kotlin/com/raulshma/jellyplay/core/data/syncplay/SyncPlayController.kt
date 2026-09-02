package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CancellationException

class SyncPlayController constructor(
    private val apiClient: JellyfinApiClient,
) {
    suspend fun unpause() = safe("unpause") { apiClient.syncPlayUnpause() }
    suspend fun pause() = safe("pause") { apiClient.syncPlayPause() }
    suspend fun seek(positionTicks: Long) = safe("seek") { apiClient.syncPlaySeek(positionTicks) }
    suspend fun stop() = safe("stop") { apiClient.syncPlayStop() }

    suspend fun setNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ) = safe("setNewQueue") {
        apiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)
    }

    suspend fun queue(itemIds: List<String>, mode: String = "Queue") =
        safe("queue") { apiClient.syncPlayQueue(itemIds, mode) }

    suspend fun nextItem(playlistItemId: String) =
        safe("nextItem") { apiClient.syncPlayNextItem(playlistItemId) }

    suspend fun previousItem(playlistItemId: String) =
        safe("previousItem") { apiClient.syncPlayPreviousItem(playlistItemId) }

    suspend fun setPlaylistItem(playlistItemId: String) =
        safe("setPlaylistItem") { apiClient.syncPlaySetPlaylistItem(playlistItemId) }

    suspend fun removeFromPlaylist(playlistItemId: String) =
        safe("removeFromPlaylist") { apiClient.syncPlayRemoveFromPlaylist(playlistItemId) }

    suspend fun movePlaylistItem(playlistItemId: String, newIndex: Int) =
        safe("movePlaylistItem") { apiClient.syncPlayMovePlaylistItem(playlistItemId, newIndex) }

    suspend fun setRepeatMode(mode: SyncPlayRepeatMode) =
        safe("setRepeatMode") { apiClient.syncPlaySetRepeatMode(mode) }

    suspend fun setShuffleMode(mode: SyncPlayShuffleMode) =
        safe("setShuffleMode") { apiClient.syncPlaySetShuffleMode(mode) }

    suspend fun setIgnoreWait(ignore: Boolean) =
        safe("setIgnoreWait") { apiClient.syncPlaySetIgnoreWait(ignore) }

    suspend fun reportReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long,
    ) = safe("reportReady") {
        apiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId, whenMs)
    }

    suspend fun reportBuffering(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long,
    ) = safe("reportBuffering") {
        apiClient.syncPlayBuffering(positionTicks, isPlaying, playlistItemId, whenMs)
    }

    /**
     * Run [block] and log any non-fatal exception. [CancellationException] is always rethrown
     * so structured concurrency (scope/job cancellation) propagates correctly when the user
     * leaves SyncPlay or logs out.
     */
    private suspend fun safe(tag: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "$tag failed", e)
        }
    }

    companion object {
        private const val TAG = "SyncPlayController"
    }
}
