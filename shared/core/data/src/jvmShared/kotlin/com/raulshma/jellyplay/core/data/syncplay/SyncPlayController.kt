package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import kotlinx.coroutines.CancellationException

/**
 * The ONE fire-and-forget wrapper home for SyncPlay wire commands that player
 * plumbing issues without awaiting a result (`SyncPlayBridge`,
 * `SyncPlayPlaybackCore`, `VideoPlayerViewModel`): every member is a
 * [safe]-wrapped one-liner over [SyncPlayApiClient] (the family seam, not the
 * JellyfinApiClient union — the PlaybackRepositoryImpl ctor precedent; the
 * family single composes the same impl the union delegates to), and nothing
 * else in the app wraps these calls. Group lifecycle (join/leave) is NOT
 * here — `SyncPlayManager` calls the api client directly because it must
 * observe the results — and the repository seam (`SyncPlayRepository`) only
 * carries the commands its UI consumers await.
 *
 * Members here are pruned to the called vocabulary: `queue`,
 * `removeFromPlaylist`, and `movePlaylistItem` were removed after a census
 * (working tree and HEAD) found zero call sites.
 */
class SyncPlayController constructor(
    private val syncPlayApiClient: SyncPlayApiClient,
) {
    suspend fun unpause() = safe("unpause") { syncPlayApiClient.syncPlayUnpause() }
    suspend fun pause() = safe("pause") { syncPlayApiClient.syncPlayPause() }
    suspend fun seek(positionTicks: Long) = safe("seek") { syncPlayApiClient.syncPlaySeek(positionTicks) }
    suspend fun stop() = safe("stop") { syncPlayApiClient.syncPlayStop() }

    suspend fun setNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ) = safe("setNewQueue") {
        syncPlayApiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)
    }

    suspend fun nextItem(playlistItemId: String) =
        safe("nextItem") { syncPlayApiClient.syncPlayNextItem(playlistItemId) }

    suspend fun previousItem(playlistItemId: String) =
        safe("previousItem") { syncPlayApiClient.syncPlayPreviousItem(playlistItemId) }

    suspend fun setPlaylistItem(playlistItemId: String) =
        safe("setPlaylistItem") { syncPlayApiClient.syncPlaySetPlaylistItem(playlistItemId) }

    suspend fun setRepeatMode(mode: SyncPlayRepeatMode) =
        safe("setRepeatMode") { syncPlayApiClient.syncPlaySetRepeatMode(mode) }

    suspend fun setShuffleMode(mode: SyncPlayShuffleMode) =
        safe("setShuffleMode") { syncPlayApiClient.syncPlaySetShuffleMode(mode) }

    suspend fun setIgnoreWait(ignore: Boolean) =
        safe("setIgnoreWait") { syncPlayApiClient.syncPlaySetIgnoreWait(ignore) }

    /**
     * Reports local readiness to the group. [whenMs] MUST be the
     * TimeSyncManager-based remote-now (`timeSyncManager.remoteNow()`), never
     * an uncorrected wall clock: the server stamps its schedule from this
     * instant, and a local-clock value would skew every projection. It is
     * required (non-nullable) on purpose — the api client's nullable
     * `whenMs` falls back to `LocalDateTime.now(UTC)`, which is exactly the
     * uncorrected clock this contract forbids. Position/tick conversions also
     * flow through [TimeSyncManager] (`msToTicks`/`ticksToMs`), the single
     * home for SyncPlay clock math.
     */
    suspend fun reportReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long,
    ) = safe("reportReady") {
        syncPlayApiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId, whenMs)
    }

    /** Same [whenMs] clock contract as [reportReady]: remote-now only. */
    suspend fun reportBuffering(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long,
    ) = safe("reportBuffering") {
        syncPlayApiClient.syncPlayBuffering(positionTicks, isPlaying, playlistItemId, whenMs)
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
