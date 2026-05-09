package com.raulshma.jellyplay.feature.syncplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayItemRequest(
    val itemId: String,
    val positionTicks: Long,
)

@HiltViewModel
class SyncPlayViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val syncPlayManager: SyncPlayManager,
) : ViewModel() {

    var groups by mutableStateOf<List<SyncPlayGroup>>(emptyList())
        private set

    var currentGroup by mutableStateOf<SyncPlayGroupInfo?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isInGroup by mutableStateOf(false)
        private set

    var showCreateDialog by mutableStateOf(false)
        private set

    var showQueueSheet by mutableStateOf(false)
        private set

    private val _navigateToPlayer = MutableStateFlow<PlayItemRequest?>(null)
    val navigateToPlayer = _navigateToPlayer.asStateFlow()

    private var commandJob: Job? = null
    private var lastHandledPlayingItemId: String? = null
    private var autoJoinGroupId: String? = null

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            isLoading = true
            error = null
            syncPlayManager.joinGroup(groupId)
                .onSuccess {
                    isInGroup = true
                    loadCurrentGroup()
                    startCommandListener()
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    private fun startCommandListener() {
        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            syncPlayManager.commands.collect { command ->
                when (command) {
                    is SyncPlayCommand.PlayQueueUpdate -> {
                        val current = currentGroup
                        if (command.playingItemId.isNotBlank() && lastHandledPlayingItemId != command.playingItemId) {
                            _navigateToPlayer.value = PlayItemRequest(
                                itemId = command.playingItemId,
                                positionTicks = command.positionTicks,
                            )
                            lastHandledPlayingItemId = command.playingItemId
                        }
                        currentGroup = (current ?: SyncPlayGroupInfo(
                            groupId = "",
                            groupName = "",
                        )).copy(
                            playingItemId = command.playingItemId,
                            isPlaying = command.isPlaying,
                            positionTicks = command.positionTicks,
                        )
                    }
                    is SyncPlayCommand.StateUpdate -> {
                        currentGroup = currentGroup?.copy(isPlaying = command.isPlaying)
                    }
                    is SyncPlayCommand.GroupUpdate -> {
                        if (command.groupName.isBlank() && command.participantCount == 0) {
                            isInGroup = false
                            currentGroup = null
                            commandJob?.cancel()
                        } else {
                            loadCurrentGroup()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun togglePlayback() {
        viewModelScope.launch {
            val group = currentGroup ?: return@launch
            if (group.isPlaying) {
                mediaRepository.syncPlayPause()
            } else {
                mediaRepository.syncPlayUnpause()
            }
        }
    }

    fun seekTo(positionTicks: Long) {
        viewModelScope.launch {
            mediaRepository.syncPlaySeek(positionTicks)
        }
    }

    fun stop() {
        viewModelScope.launch {
            mediaRepository.syncPlayStop()
        }
    }

    fun setRepeatMode(mode: SyncPlayRepeatMode) {
        viewModelScope.launch {
            mediaRepository.syncPlaySetRepeatMode(mode)
        }
    }

    fun setShuffleMode(mode: SyncPlayShuffleMode) {
        viewModelScope.launch {
            mediaRepository.syncPlaySetShuffleMode(mode)
        }
    }

    fun setIgnoreWait(ignore: Boolean) {
        viewModelScope.launch {
            mediaRepository.syncPlaySetIgnoreWait(ignore)
        }
    }

    fun updateShowCreateDialog(show: Boolean) {
        showCreateDialog = show
    }

    fun updateShowQueueSheet(show: Boolean) {
        showQueueSheet = show
    }

    fun refreshGroups() {
        viewModelScope.launch {
            mediaRepository.getSyncPlayGroups()
                .onSuccess { groups = it }
                .onFailure { }
        }
    }

    private suspend fun loadCurrentGroup() {
        mediaRepository.getSyncPlayInfo(syncPlayManager.activeGroupId)
            .onSuccess { currentGroup = it }
            .onFailure { currentGroup = null }
    }

    override fun onCleared() {
        super.onCleared()
        commandJob?.cancel()
    }
}
