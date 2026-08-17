package com.raulshma.jellyplay.feature.player.video

import android.util.Log
import com.raulshma.jellyplay.core.data.syncplay.PlaybackCoreCallbacks
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bridges the shared [SyncPlayManager] to the local playback session: forwards
 * group playback commands to the engine, reports local state back, and owns the
 * SyncPlay group-display slice [SyncPlayUiState] (group name, participant
 * count, sync status, repeat/shuffle modes) as its single home, exposed as a
 * read-only [StateFlow].
 *
 * **Item-switch semantics: the group-display state resets on every item
 * switch** — `reset()` (invoked from the ViewModel's `releaseInternals()`)
 * restores the default [SyncPlayUiState]. None of these fields were on the
 * former reset whitelist, so this explicit reset is the same semantics the
 * implicit UiState rebuild used to provide. A still-active session restores its
 * display state right after via the ViewModel's `reattachSession()` call.
 *
 * `isPlaying` is *session* state owned by the ViewModel; the two places the
 * bridge used to write it into the god-state UiState now go through the narrow
 * [setIsPlaying] lambda. `isInSyncPlaySession` lives in [SyncPlayUiState] (the
 * bridge is its home); the ViewModel mirrors it into the residual UiState for
 * the segment-overlay projection (see `VideoPlayerViewModel`).
 */
internal class SyncPlayBridge(
    private val syncPlayManager: SyncPlayManager,
    private val getMediaEngine: () -> MediaEngine?,
    private val getCurrentItemId: () -> String?,
    private val onLoadItem: (String, Long) -> Unit,
    // Narrow session-state write: the bridge no longer holds the UiState handle.
    private val setIsPlaying: (Boolean) -> Unit,
    // No default: a previous default of `CoroutineScope(SupervisorJob() +
    // Dispatchers.Main)` was an uncancellable root scope that would leak if a
    // caller forgot to pass its own. Forcing the caller (the ViewModel) to
    // supply its viewModelScope-bound scope makes the lifecycle explicit and
    // guarantees the bridge dies with its owner.
    private val scope: CoroutineScope,
) : PlaybackCoreCallbacks {

    private var eventJob: Job? = null
    private var currentPlaylistItemId: String? = null

    /**
     * Wall-clock ms of the last group-unpause request sent from this bridge
     * ([togglePlayPause] or [onIsPlayingChanged]). Used to keep the
     * "local playback started while the group was paused → tell the group"
     * propagation from double-firing when the engine starts playing as a
     * *result* of the unpause we already sent.
     */
    private var lastUnpauseRequestAtMs = 0L

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    private val _state = MutableStateFlow(SyncPlayUiState())
    val state: StateFlow<SyncPlayUiState> = _state.asStateFlow()

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
            _state.update { it.copy(
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
            _state.update { it.copy(
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
            _state.update { it.copy(
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
        _state.update { it.copy(
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

    /**
     * Queue reconciliation, first stage of every session load: when a group is
     * playing a different item, point its queue at the requested item
     * (best-effort; any failure is swallowed) so the group's transport stays
     * coherent with the local load. Runs as the [SessionLoadPipeline]'s first
     * hook, before any prefs/load work.
     */
    suspend fun reconcileQueueForItem(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        val currentGroup = syncPlayManager.currentGroup
        val groupPlayingId = currentGroup?.playingItemId
        // A null groupPlayingId means the group has no queue yet (freshly
        // created group) — point it at this item so group transport commands
        // have something to act on.
        if (syncPlayManager.isInSyncPlaySession && groupPlayingId != itemId) {
            try {
                val matchingEntry = currentGroup?.playlistItemMap?.entries?.find { it.value == itemId }
                if (matchingEntry != null) {
                    syncPlayManager.syncPlayController.setPlaylistItem(matchingEntry.key)
                } else {
                    syncPlayManager.syncPlayController.setNewQueue(
                        itemIds = listOf(itemId),
                        playingItemId = itemId,
                        mediaSourceId = mediaSourceId,
                        startPositionTicks = startPositionTicks,
                    )
                }
            } catch (_: Exception) {
            }
        }
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
        if (!isPlaying || !isInSession) return
        // Local playback just started (initial load autoplay, engine reclaim)
        // while the group is still paused — propagate it so the *whole group*
        // starts, matching the official client's behaviour when a member
        // begins playing. Without this, our player runs ahead locally, the
        // server-side group stays Paused, and other clients (including the
        // official Jellyfin client) never open their players.
        val group = syncPlayManager.currentGroup ?: return
        if (group.isPlaying) return
        // Group-driven playback arrives as an Unpause command; our engine
        // starting because of it must not echo another unpause request.
        if (syncPlayManager.playbackCore.lastCommand?.command == "Unpause") return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastUnpauseRequestAtMs < UNPAUSE_REQUEST_DEDUPE_MS) return
        lastUnpauseRequestAtMs = nowMs
        scope.launch { syncPlayManager.syncPlayController.unpause() }
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
        // Item-switch semantics: the group-display state is per-item (it was
        // never on the reset whitelist) — restore the defaults. A still-active
        // session re-populates via reattachSession() right after the switch.
        _state.value = SyncPlayUiState()
    }

    fun togglePlayPause() {
        scope.launch {
            if (getMediaEngine()?.isPlaying?.value == true) {
                getMediaEngine()?.pause()
                setIsPlaying(false)
                syncPlayManager.syncPlayController.pause()
            } else {
                lastUnpauseRequestAtMs = System.currentTimeMillis()
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
                                _state.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                                val posTicks = syncPlayManager.queueCore.getStartPositionTicks(
                                    syncPlayManager.playbackCore.lastCommand
                                )
                                onLoadItem(event.data.playingItemId, posTicks)
                            }
                            getMediaEngine() == null -> {
                                syncPlayManager.playbackCore.setPendingItemLoad(true)
                                _state.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                            }
                            else -> {
                                val engine = getMediaEngine()
                                if (engine == null) {
                                    syncPlayManager.playbackCore.setPendingItemLoad(true)
                                    _state.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
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
                        // A queue update while the group is actively playing
                        // means we're aligned with it; while paused, leave the
                        // chip untouched (a paused group is still "Synced" —
                        // forcing a flip here made queue ops pulse the chip).
                        if (event.data.isPlaying) {
                            _state.update { it.copy(isSyncPlaySynced = true, isSyncPlaySyncing = false) }
                        }
                        updateGroupState()
                    }
                    is SyncPlayEvent.GroupUpdate -> {
                        if (event.groupName.isBlank() && event.participantCount == 0) {
                            Log.d(TAG, "GroupUpdate empty: clearing session display state")
                            _state.update { it.copy(
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
                        Log.d(TAG, "WaitForGroup(${event.userName ?: "?"}): syncing")
                        _state.update { it.copy(isSyncPlaySynced = false, isSyncPlaySyncing = true) }
                    }
                    is SyncPlayEvent.Notification -> {
                        _notifications.tryEmit(event.message)
                    }
                    is SyncPlayEvent.StateUpdate -> {
                        // Waiting is the transient "parked while a client
                        // catches up" state — the only one that surfaces as
                        // "Syncing". A Paused group is still in lockstep, so
                        // it stays "Synced".
                        Log.d(TAG, "StateUpdate: state=${event.state}, reason=${event.reason}")
                        val waiting = event.state.equals("Waiting", ignoreCase = true)
                        _state.update {
                            if (waiting) it.copy(isSyncPlaySynced = false, isSyncPlaySyncing = true)
                            else it.copy(isSyncPlaySynced = true, isSyncPlaySyncing = false)
                        }
                        updateGroupState()
                    }
                    is SyncPlayEvent.PlaybackCommand -> {
                        updateGroupState()
                    }
                    is SyncPlayEvent.GroupLeft -> {
                        _state.update { it.copy(
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
        _state.update {
            it.copy(
                syncPlayGroupName = group?.groupName ?: it.syncPlayGroupName,
                syncPlayParticipantCount = group?.participantCount ?: it.syncPlayParticipantCount,
                syncPlayRepeatMode = group?.repeatMode ?: it.syncPlayRepeatMode,
                syncPlayShuffleMode = group?.shuffleMode ?: it.syncPlayShuffleMode,
            )
        }
    }

    // PlaybackCoreCallbacks implementation
    override fun localPlay() { scope.launch { getMediaEngine()?.play() } }
    override fun localPause() { scope.launch { getMediaEngine()?.pause() } }
    override fun localSeek(positionMs: Long) { scope.launch { getMediaEngine()?.seekTo(positionMs) } }
    override fun setPlaybackRate(rate: Float) { scope.launch { getMediaEngine()?.setPlaybackSpeed(rate) } }
    override fun currentPositionMs(): Long = getMediaEngine()?.currentPositionMs ?: 0L
    override fun durationMs(): Long = getMediaEngine()?.durationMs ?: 0L
    override fun isPlaying(): Boolean = getMediaEngine()?.isPlaying?.value ?: false
    override fun isBuffering(): Boolean =
        getMediaEngine()?.playbackState?.value == EnginePlaybackState.BUFFERING

    override fun onSyncStateChanged(synced: Boolean, syncing: Boolean) {
        _state.update { it.copy(
            isSyncPlaySynced = synced,
            isSyncPlaySyncing = syncing,
        ) }
        // Session-state reconciliation: when the group reports synced and the
        // engine is actually playing, make sure the play/pause icon reflects it.
        // A no-op write of the same value never re-emits a StateFlow.
        if (synced && getMediaEngine()?.isPlaying?.value == true) {
            setIsPlaying(true)
        }
    }

    companion object {
        private const val TAG = "SyncPlayBridge"

        /** Window in which a second group-unpause request is suppressed. */
        private const val UNPAUSE_REQUEST_DEDUPE_MS = 3_000L
    }
}
