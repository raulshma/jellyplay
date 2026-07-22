package com.raulshma.jellyplay.feature.player.video

import android.util.Log
import com.raulshma.jellyplay.core.data.syncplay.PlaybackCoreCallbacks
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal class SyncPlayBridge(
    private val syncPlayManager: SyncPlayManager,
    private val uiState: StateFlowHandle<VideoPlayerUiState>,
    private val getMediaEngine: () -> MediaEngine?,
    private val getCurrentItemId: () -> String?,
    private val onLoadItem: (String, Long) -> Unit,
    // No default: a previous default of `CoroutineScope(SupervisorJob() +
    // Dispatchers.Main)` was an uncancellable root scope that would leak if a
    // caller forgot to pass its own. Forcing the caller (the ViewModel) to
    // supply its viewModelScope-bound scope makes the lifecycle explicit and
    // guarantees the bridge dies with its owner.
    private val scope: CoroutineScope,
) : PlaybackCoreCallbacks {

    private var eventJob: Job? = null
    private var currentPlaylistItemId: String? = null

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    val isInSession: Boolean get() = syncPlayManager.isInSyncPlaySession
    val ignoreWait: StateFlow<Boolean> get() = syncPlayManager.playbackCore.ignoreWait

    fun start() {
        // Defensive clear before re-registering: the @Singleton
        // SyncPlayPlaybackCore retains its callbacks until clearCallbacks()
        // runs. If a previous bridge for this VM was never torn down (e.g. an
        // early init failure path, or a future registration site that forgets
        // reset()), the singleton would hold two refs — the stale one keeping
        // a dead VM alive. Clearing first makes start() idempotent.
        syncPlayManager.playbackCore.clearCallbacks()
        syncPlayManager.playbackCore.setCallbacks(this)
        if (syncPlayManager.isInSyncPlaySession) {
            val group = syncPlayManager.currentGroup
            uiState.update { it.copy(
                isInSyncPlaySession = true,
                syncPlayGroupName = group?.groupName,
                syncPlayParticipantCount = group?.participantCount ?: 0,
                syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
            ) }
            currentPlaylistItemId = group?.playingPlaylistItemId
            syncPlayManager.playbackCore.setCurrentPlaylistItemId(currentPlaylistItemId)
        }
        startEventListener()
    }

    fun joinGroup(groupId: String) {
        scope.launch {
            syncPlayManager.joinGroup(groupId)
            val group = syncPlayManager.currentGroup
            uiState.update { it.copy(
                syncPlayGroupName = group?.groupName ?: groupId,
                isInSyncPlaySession = true,
                syncPlayParticipantCount = group?.participantCount ?: 0,
                syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
            ) }
        }
    }

    fun leaveGroup() {
        scope.launch {
            syncPlayManager.leaveGroup()
            uiState.update { it.copy(
                syncPlayGroupName = null,
                syncPlayParticipantCount = 0,
                isSyncPlaySynced = false,
                isInSyncPlaySession = false,
                isSyncPlaySyncing = false,
            ) }
            currentPlaylistItemId = null
            syncPlayManager.playbackCore.reset()
        }
    }

    fun reattachSession() {
        if (!syncPlayManager.isInSyncPlaySession) return
        // Same defensive clear as start() — see the note there. reattach runs
        // after process death / mini-player reclaim where the prior bridge may
        // not have cleared cleanly.
        syncPlayManager.playbackCore.clearCallbacks()
        syncPlayManager.playbackCore.setCallbacks(this)
        val group = syncPlayManager.currentGroup
        uiState.update { it.copy(
            isInSyncPlaySession = true,
            syncPlayGroupName = group?.groupName,
            syncPlayParticipantCount = group?.participantCount ?: 0,
            syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
            syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
        ) }
        currentPlaylistItemId = group?.playingPlaylistItemId
        syncPlayManager.playbackCore.setCurrentPlaylistItemId(currentPlaylistItemId)
    }

    fun setIgnoreWait(ignore: Boolean) {
        syncPlayManager.playbackCore.setIgnoreWait(ignore)
    }

    fun sendStop() {
        scope.launch { syncPlayManager.syncPlayController.stop() }
    }

    fun sendNextItem(playlistItemId: String) {
        scope.launch { syncPlayManager.syncPlayController.nextItem(playlistItemId) }
    }

    fun sendPreviousItem(playlistItemId: String) {
        scope.launch { syncPlayManager.syncPlayController.previousItem(playlistItemId) }
    }

    fun onPlaybackStateChanged(state: Int) {
        if (!isInSession) return
        getMediaEngine() ?: return
        syncPlayManager.playbackCore.onPlaybackStateChanged(state)
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        // handled by playback state flow
    }

    fun reset() {
        syncPlayManager.playbackCore.reset()
        // Clear the callbacks held by the @Singleton playback core so it does
        // not retain this bridge (and through it the destroyed ViewModel) after
        // the player screen leaves composition. reset() is the teardown path
        // invoked from VideoPlayerViewModel.onCleared() -> releaseInternals().
        syncPlayManager.playbackCore.clearCallbacks()
        currentPlaylistItemId = null
        eventJob?.cancel()
        eventJob = null
        uiState.update { it.copy(isSyncPlaySyncing = false) }
    }

    fun togglePlayPause() {
        scope.launch {
            if (getMediaEngine()?.isPlaying?.value == true) {
                getMediaEngine()?.pause()
                uiState.update { it.copy(isPlaying = false) }
                syncPlayManager.syncPlayController.pause()
            } else {
                syncPlayManager.syncPlayController.unpause()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            getMediaEngine()?.seekTo(positionMs)
            syncPlayManager.syncPlayController.seek(positionMs * 10_000)
        }
    }

    private fun startEventListener() {
        if (eventJob?.isActive == true) return
        eventJob = scope.launch {
            syncPlayManager.events.collect { event ->
                when (event) {
                    is SyncPlayEvent.PlayQueueUpdate -> {
                        if (event.data.playingPlaylistItemId.isNotBlank()) {
                            currentPlaylistItemId = event.data.playingPlaylistItemId
                            syncPlayManager.playbackCore.setCurrentPlaylistItemId(currentPlaylistItemId)
                        }
                        val currentItemId = getCurrentItemId()
                        when {
                            currentItemId == null || currentItemId != event.data.playingItemId -> {
                                syncPlayManager.playbackCore.setPendingItemLoad(true)
                                uiState.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                                val posTicks = syncPlayManager.queueCore.getStartPositionTicks(
                                    syncPlayManager.playbackCore.lastCommand
                                )
                                onLoadItem(event.data.playingItemId, posTicks)
                            }
                            getMediaEngine() == null -> {
                                syncPlayManager.playbackCore.setPendingItemLoad(true)
                                uiState.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                            }
                            else -> {
                                val engine = getMediaEngine()
                                if (engine == null) {
                                    syncPlayManager.playbackCore.setPendingItemLoad(true)
                                    uiState.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                                } else {
                                    val posTicks = syncPlayManager.estimateCurrentTicks(
                                        event.data.startPositionTicks, event.data.whenMs
                                    )
                                    val posMs = posTicks / 10_000
                                    val durationMs = engine.durationMs
                                    val safePosMs = if (durationMs > 0) posMs.coerceIn(0, durationMs) else posMs.coerceAtLeast(0)
                                    val currentPosMs = engine.currentPositionMs
                                    if (Math.abs(safePosMs - currentPosMs) > 300) {
                                        engine.seekTo(safePosMs)
                                    }
                                    if (event.data.isPlaying && !engine.isPlaying.value) {
                                        engine.play()
                                    } else if (!event.data.isPlaying && engine.isPlaying.value) {
                                        engine.pause()
                                    }
                                }
                            }
                        }
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                        updateGroupState()
                    }
                    is SyncPlayEvent.GroupUpdate -> {
                        if (event.groupName.isBlank() && event.participantCount == 0) {
                            uiState.update { it.copy(
                                syncPlayGroupName = null,
                                syncPlayParticipantCount = 0,
                                isSyncPlaySynced = false,
                                isInSyncPlaySession = false,
                                isSyncPlaySyncing = false,
                            ) }
                        }
                        updateGroupState()
                    }
                    is SyncPlayEvent.WaitForGroup -> {
                        uiState.update { it.copy(isSyncPlaySynced = false, isSyncPlaySyncing = true) }
                    }
                    is SyncPlayEvent.Notification -> {
                        _notifications.tryEmit(event.message)
                    }
                    is SyncPlayEvent.StateUpdate -> {
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                        updateGroupState()
                    }
                    is SyncPlayEvent.PlaybackCommand -> {
                        updateGroupState()
                    }
                    is SyncPlayEvent.GroupLeft -> {
                        uiState.update { it.copy(
                            syncPlayGroupName = null,
                            syncPlayParticipantCount = 0,
                            isSyncPlaySynced = false,
                            isInSyncPlaySession = false,
                            isSyncPlaySyncing = false,
                        ) }
                    }
                }
            }
        }
    }

    private fun updateGroupState() {
        val group = syncPlayManager.currentGroup
        uiState.update { it.copy(
            syncPlayGroupName = group?.groupName ?: it.syncPlayGroupName,
            syncPlayParticipantCount = group?.participantCount ?: it.syncPlayParticipantCount,
            syncPlayRepeatMode = group?.repeatMode ?: it.syncPlayRepeatMode,
            syncPlayShuffleMode = group?.shuffleMode ?: it.syncPlayShuffleMode,
        ) }
    }

    // PlaybackCoreCallbacks implementation
    override fun localPlay() { scope.launch { getMediaEngine()?.play() } }
    override fun localPause() { scope.launch { getMediaEngine()?.pause() } }
    override fun localSeek(positionMs: Long) { scope.launch { getMediaEngine()?.seekTo(positionMs) } }
    override fun setPlaybackRate(rate: Float) { scope.launch { getMediaEngine()?.setPlaybackSpeed(rate) } }
    override fun currentPositionMs(): Long = getMediaEngine()?.currentPositionMs ?: 0L
    override fun durationMs(): Long = getMediaEngine()?.durationMs ?: 0L
    override fun isPlaying(): Boolean = getMediaEngine()?.isPlaying?.value ?: false

    override fun onSyncStateChanged(synced: Boolean, syncing: Boolean) {
        uiState.update { it.copy(
            isSyncPlaySynced = synced,
            isSyncPlaySyncing = syncing,
            isPlaying = if (synced && getMediaEngine()?.isPlaying?.value == true) true else it.isPlaying,
        ) }
    }

    companion object {
        private const val TAG = "SyncPlayBridge"
    }
}
