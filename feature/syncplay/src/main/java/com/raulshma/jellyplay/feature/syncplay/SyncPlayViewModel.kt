package com.raulshma.jellyplay.feature.syncplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class SyncPlayViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val syncPlayManager: SyncPlayManager,
) : JellyPlayViewModel() {

    var groups by composeState<List<SyncPlayGroup>>(emptyList())
        private set

    var currentGroup by composeState<SyncPlayGroupInfo?>(null)
        private set

    var isLoading by composeState(false)
        private set

    var error by composeState<String?>(null)
        private set

    var isInGroup by composeState(false)
        private set

    var showCreateDialog by composeState(false)
        private set

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
            isLoading = true
            error = null
            mediaRepository.getSyncPlayGroups()
                .onSuccess {
                    groups = it
                    if (!isInGroup) {
                        currentGroup = null
                    }
                    autoJoinGroupId?.let { gid ->
                        val target = it.find { g -> g.groupId == gid }
                        if (target != null) {
                            autoJoinGroupId = null
                            joinGroup(target.groupId)
                        }
                    }
                }
                .onFailure {
                    error = it.message ?: "Failed to load groups"
                }
            isLoading = false
        }
    }

    fun joinGroup(groupId: String) {
        launch {
            isLoading = true
            error = null
            syncPlayManager.joinGroup(groupId)
                .onSuccess {
                    isInGroup = true
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
                    error = it.message ?: "Failed to join group"
                }
            isLoading = false
        }
    }

    fun leaveGroup() {
        launch {
            syncPlayManager.leaveGroup()
                .onSuccess {
                    isInGroup = false
                    currentGroup = null
                    commandJob?.cancel()
                    lastHandledPlayingItemId = null
                    loadGroups()
                }
                .onFailure {
                    error = it.message ?: "Failed to leave group"
                }
        }
    }

    fun createGroup(name: String) {
        launch {
            isLoading = true
            error = null
            mediaRepository.createSyncPlayGroup(name)
                .onSuccess {
                    showCreateDialog = false
                    delay(500)
                    val updatedGroups = mediaRepository.getSyncPlayGroups().getOrElse { emptyList() }
                    groups = updatedGroups
                    val newGroup = updatedGroups.find { it.groupName == name }
                    if (newGroup != null) {
                        joinGroup(newGroup.groupId)
                    } else {
                        autoJoinGroupId = null
                        loadGroups()
                    }
                }
                .onFailure {
                    error = it.message ?: "Failed to create group"
                }
            isLoading = false
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
                        val current = currentGroup
                        if (event.data.playingItemId.isNotBlank() && lastHandledPlayingItemId != event.data.playingItemId) {
                            _navigateToPlayer.value = PlayItemRequest(
                                itemId = event.data.playingItemId,
                                positionTicks = event.data.startPositionTicks,
                            )
                            lastHandledPlayingItemId = event.data.playingItemId
                        }
                        currentGroup = (current ?: SyncPlayGroupInfo(
                            groupId = "",
                            groupName = "",
                        )).copy(
                            playingItemId = event.data.playingItemId,
                            isPlaying = event.data.isPlaying,
                            positionTicks = event.data.startPositionTicks,
                        )
                    }
                    is SyncPlayEvent.StateUpdate -> {
                        currentGroup = currentGroup?.copy(isPlaying = event.isPlaying)
                    }
                    is SyncPlayEvent.GroupUpdate -> {
                        if (event.groupName.isBlank() && event.participantCount == 0) {
                            isInGroup = false
                            currentGroup = null
                            commandJob?.cancel()
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
            val group = currentGroup ?: return@launch
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
        showCreateDialog = show
    }

    fun refreshGroups() {
        launch {
            mediaRepository.getSyncPlayGroups()
                .onSuccess { groups = it }
                .onFailure { }
        }
    }

    private suspend fun loadCurrentGroup() {
        val groupId = syncPlayManager.activeGroupId ?: return
        mediaRepository.getSyncPlayInfo(groupId)
            .onSuccess { currentGroup = it }
            .onFailure { currentGroup = null }
    }

    override fun onCleared() {
        super.onCleared()
        commandJob?.cancel()
    }
}
