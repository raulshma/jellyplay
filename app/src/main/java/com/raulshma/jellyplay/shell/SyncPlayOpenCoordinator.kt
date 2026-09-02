package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Item + resolved start position for a SyncPlay-driven player open. */
data class SyncPlayOpenRequest(
    val itemId: String,
    val startPositionTicks: Long,
)

/**
 * Owns the SyncPlay group-playback → player-open orchestration behind a
 * single [openRequests] flow. Emits at the app shell (not the SyncPlay
 * screen) so the player opens no matter which screen is foreground —
 * previously only the SyncPlay screen's ViewModel watched for this, so group
 * playback while the user browsed elsewhere silently did nothing until they
 * revisited the SyncPlay screen. The open-dedupe key (one request per
 * playing-item change, reset when the group is left) is private to this
 * module.
 */
class SyncPlayOpenCoordinator (
    private val syncPlayManager: SyncPlayManager,
) {
    private val _openRequests = MutableSharedFlow<SyncPlayOpenRequest>(extraBufferCapacity = 4)
    val openRequests: SharedFlow<SyncPlayOpenRequest> = _openRequests.asSharedFlow()

    /** Dedupe key of the last SyncPlay item an open request was emitted for. */
    private var lastOpenKey: String? = null

    /**
     * Owns this coordinator's collectors. Deliberately a direct
     * [RestartableJob] rather than the [ShellCoordinator] base: this
     * coordinator has no commands to run, so it needs neither the base's
     * [ShellCoordinator.commandScope] nor its Main-dispatcher cost.
     */
    private val lifecycleJob = RestartableJob()

    /**
     * Begins watching group playback on [scope]. Safe to call again (e.g.
     * after activity-state loss rebuilt the ViewModel): [RestartableJob]
     * cancels the previous collectors first, so they are never duplicated.
     */
    fun start(scope: CoroutineScope) {
        lifecycleJob.launchIn(scope) {
            // SyncPlay group playback → open the video player app-wide. Emits only
            // when the playing item actually changes; the navigation layer gates on
            // whether a player is already open. Joining mid-playback is covered
            // too: the server pushes the group's play-queue state to the joining
            // session as a PlayQueueUpdate.
            launch {
                syncPlayManager.events.collect { event ->
                    if (event is SyncPlayEvent.PlayQueueUpdate &&
                        event.data.playingItemId.isNotBlank() &&
                        syncPlayManager.isInSyncPlaySession
                    ) {
                        val key = event.data.playingPlaylistItemId.ifBlank { event.data.playingItemId }
                        if (key != lastOpenKey) {
                            lastOpenKey = key
                            val posTicks = if (event.data.isPlaying && event.data.whenMs > 0) {
                                syncPlayManager.estimateCurrentTicks(event.data.startPositionTicks, event.data.whenMs)
                            } else {
                                event.data.startPositionTicks
                            }
                            _openRequests.tryEmit(SyncPlayOpenRequest(event.data.playingItemId, posTicks))
                        }
                    }
                }
            }
            launch {
                syncPlayManager.currentGroupFlow.collect { group ->
                    if (group == null) lastOpenKey = null
                }
            }
        }
    }
}
