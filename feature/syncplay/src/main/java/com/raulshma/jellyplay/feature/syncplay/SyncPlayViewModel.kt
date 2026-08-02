package com.raulshma.jellyplay.feature.syncplay

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.syncplay.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PlayItemRequest(
    val itemId: String,
    val positionTicks: Long,
)

@Immutable
data class SyncPlayUiState(
    val groups: List<SyncPlayGroup> = emptyList(),
    val currentGroup: SyncPlayGroupInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isInGroup: Boolean = false,
    val showCreateDialog: Boolean = false,
    val pendingJoin: SyncPlayGroup? = null,
)

@HiltViewModel
class SyncPlayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val syncPlayManager: SyncPlayManager,
    private val syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(SyncPlayUiState())
    val uiState: StateFlow<SyncPlayUiState> = _uiState.flow

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    private val _navigateToPlayer = MutableStateFlow<PlayItemRequest?>(null)
    val navigateToPlayer: StateFlow<PlayItemRequest?> = _navigateToPlayer.asStateFlow()

    private var commandJob: Job? = null
    private var lastHandledPlayingItemId: String? = null
    private var autoJoinGroupId: String? = null

    init {
        loadGroups()
    }

    fun loadGroups() {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            mediaRepository.getSyncPlayGroups()
                .onSuccess { result ->
                    _uiState.update { state ->
                        val currentGroup = if (state.isInGroup) state.currentGroup else null
                        state.copy(groups = result, currentGroup = currentGroup)
                    }
                    autoJoinGroupId?.let { gid ->
                        val target = result.find { g -> g.groupId == gid }
                        if (target != null) {
                            autoJoinGroupId = null
                            joinGroup(target.groupId)
                        }
                    }
                    if (autoJoinGroupId == null && result.isNotEmpty() && !_uiState.value.isInGroup) {
                        val prefs = syncPlayCastStore.syncPlayCast.value
                        if (prefs.syncPlayAutoAcceptInvites) {
                            joinGroup(result.first().groupId)
                        }
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(error = it.message ?: context.getString(R.string.syncplay_error_load_groups)) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Initiates a join request honouring the [SyncPlayJoinBehavior] preference:
     * - [SyncPlayJoinBehavior.ALWAYS_JOIN] joins immediately.
     * - [SyncPlayJoinBehavior.ASK] surfaces a confirmation dialog via [SyncPlayUiState.pendingJoin].
     * - [SyncPlayJoinBehavior.NEVER_JOIN] emits a notification instead of joining.
     */
    fun requestJoin(group: SyncPlayGroup) {
        when (syncPlayCastStore.syncPlayCast.value.syncPlayJoinBehavior) {
            SyncPlayJoinBehavior.ALWAYS_JOIN -> joinGroup(group.groupId)
            SyncPlayJoinBehavior.ASK -> _uiState.update { it.copy(pendingJoin = group) }
            SyncPlayJoinBehavior.NEVER_JOIN -> _notifications.tryEmit(context.getString(R.string.syncplay_join_disabled))
        }
    }

    fun confirmJoin() {
        val pending = _uiState.value.pendingJoin
        _uiState.update { it.copy(pendingJoin = null) }
        if (pending != null) joinGroup(pending.groupId)
    }

    fun cancelJoin() {
        _uiState.update { it.copy(pendingJoin = null) }
    }

    fun joinGroup(groupId: String) {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            syncPlayManager.joinGroup(groupId)
                .onSuccess {
                    _uiState.update { it.copy(isInGroup = true) }
                    loadCurrentGroup()
                    startEventListener()
                    val group = syncPlayManager.currentGroup
                    val playingId = group?.playingItemId
                    if (!playingId.isNullOrBlank()) {
                        _navigateToPlayer.value = PlayItemRequest(
                            itemId = playingId,
                            positionTicks = group?.positionTicks ?: 0L,
                        )
                        lastHandledPlayingItemId = playingId
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(error = it.message ?: context.getString(R.string.syncplay_error_join_group)) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun leaveGroup() {
        launch {
            syncPlayManager.leaveGroup()
                .onSuccess {
                    _uiState.update { it.copy(isInGroup = false, currentGroup = null) }
                    commandJob?.cancel()
                    lastHandledPlayingItemId = null
                    loadGroups()
                }
                .onFailure {
                    _uiState.update { state -> state.copy(error = it.message ?: context.getString(R.string.syncplay_error_leave_group)) }
                }
        }
    }

    fun createGroup(name: String) {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            mediaRepository.createSyncPlayGroup(name)
                .onSuccess {
                    _uiState.update { it.copy(showCreateDialog = false) }
                    delay(500)
                    val updatedGroups = mediaRepository.getSyncPlayGroups().getOrElse { emptyList() }
                    val newGroup = updatedGroups.find { it.groupName == name }
                    if (newGroup != null) {
                        _uiState.update { it.copy(groups = updatedGroups) }
                        joinGroup(newGroup.groupId)
                    } else {
                        autoJoinGroupId = null
                        _uiState.update { it.copy(groups = updatedGroups) }
                        loadGroups()
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(error = it.message ?: context.getString(R.string.syncplay_error_create_group)) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onNavigateToPlayerHandled() {
        _navigateToPlayer.value = null
    }

    private fun startEventListener() {
        commandJob?.cancel()
        commandJob = launch {
            syncPlayManager.events.collect { event ->
                when (event) {
                    is SyncPlayEvent.PlayQueueUpdate -> {
                        if (event.data.playingItemId.isNotBlank() && lastHandledPlayingItemId != event.data.playingItemId) {
                            _navigateToPlayer.value = PlayItemRequest(
                                itemId = event.data.playingItemId,
                                positionTicks = event.data.startPositionTicks,
                            )
                            lastHandledPlayingItemId = event.data.playingItemId
                        }
                        _uiState.update { state ->
                            val current = state.currentGroup ?: SyncPlayGroupInfo(
                                groupId = "",
                                groupName = "",
                            )
                            state.copy(
                                currentGroup = current.copy(
                                    playingItemId = event.data.playingItemId,
                                    isPlaying = event.data.isPlaying,
                                    positionTicks = event.data.startPositionTicks,
                                ),
                            )
                        }
                    }
                    is SyncPlayEvent.StateUpdate -> {
                        _uiState.update { state ->
                            state.copy(currentGroup = state.currentGroup?.copy(isPlaying = event.isPlaying))
                        }
                    }
                    is SyncPlayEvent.GroupUpdate -> {
                        if (event.groupName.isBlank() && event.participantCount == 0) {
                            // An empty GroupUpdate normally means the server ejected us.
                            // But the server can emit a transient empty update right after
                            // a WebSocket reconnect (before membership is re-asserted); in
                            // that window treat it as a soft signal and re-confirm via the
                            // live group info rather than flipping to "left".
                            val lastReconnect = syncPlayManager.lastReconnectMs
                            val recentlyReconnected = lastReconnect > 0L &&
                                System.currentTimeMillis() - lastReconnect < RECONNECT_GRACE_MS
                            if (recentlyReconnected) {
                                loadCurrentGroup()
                            } else {
                                _uiState.update { it.copy(isInGroup = false, currentGroup = null) }
                                commandJob?.cancel()
                            }
                        } else {
                            loadCurrentGroup()
                        }
                    }
                    is SyncPlayEvent.Notification -> {
                        _notifications.tryEmit(event.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun togglePlayback() {
        launch {
            val group = _uiState.value.currentGroup ?: return@launch
            if (group.isPlaying) {
                mediaRepository.syncPlayPause()
            } else {
                mediaRepository.syncPlayUnpause()
            }
        }
    }

    fun seekTo(positionTicks: Long) {
        launch {
            mediaRepository.syncPlaySeek(positionTicks)
        }
    }

    fun stop() {
        launch {
            mediaRepository.syncPlayStop()
        }
    }

    fun setRepeatMode(mode: SyncPlayRepeatMode) {
        launch {
            mediaRepository.syncPlaySetRepeatMode(mode)
        }
    }

    fun setShuffleMode(mode: SyncPlayShuffleMode) {
        launch {
            mediaRepository.syncPlaySetShuffleMode(mode)
        }
    }

    fun setIgnoreWait(ignore: Boolean) {
        launch {
            mediaRepository.syncPlaySetIgnoreWait(ignore)
        }
    }

    fun updateShowCreateDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateDialog = show) }
    }

    fun refreshGroups() {
        launch {
            mediaRepository.getSyncPlayGroups()
                .onSuccess { groups -> _uiState.update { it.copy(groups = groups) } }
                .onFailure { }
        }
    }

    private suspend fun loadCurrentGroup() {
        val groupId = syncPlayManager.activeGroupId ?: return
        mediaRepository.getSyncPlayInfo(groupId)
            .onSuccess { currentGroup -> _uiState.update { it.copy(currentGroup = currentGroup) } }
            .onFailure { _uiState.update { it.copy(currentGroup = null) } }
    }

    override fun onCleared() {
        super.onCleared()
        commandJob?.cancel()
    }

    companion object {
        /**
         * Window after a WebSocket reconnect during which an empty GroupUpdate is
         * treated as a transient server hiccup rather than an ejection.
         */
        private const val RECONNECT_GRACE_MS = 5_000L
    }
}
