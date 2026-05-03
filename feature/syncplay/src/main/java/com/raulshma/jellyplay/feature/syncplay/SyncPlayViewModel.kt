package com.raulshma.jellyplay.feature.syncplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncPlayViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
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

    private var pollJob: Job? = null

    fun loadGroups() {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getSyncPlayGroups()
                .onSuccess {
                    groups = it
                    isInGroup = false
                    currentGroup = null
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
            mediaRepository.joinSyncPlayGroup(groupId)
                .onSuccess {
                    isInGroup = true
                    loadCurrentGroup()
                    startPolling()
                }
                .onFailure {
                    error = it.message ?: "Failed to join group"
                }
            isLoading = false
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            mediaRepository.leaveSyncPlayGroup()
                .onSuccess {
                    isInGroup = false
                    currentGroup = null
                    pollJob?.cancel()
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
                    isInGroup = true
                    showCreateDialog = false
                    loadCurrentGroup()
                    startPolling()
                }
                .onFailure {
                    error = it.message ?: "Failed to create group"
                }
            isLoading = false
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

    fun updateShowCreateDialog(show: Boolean) {
        showCreateDialog = show
    }

    private fun loadCurrentGroup() {
        viewModelScope.launch {
            mediaRepository.getSyncPlayInfo()
                .onSuccess { currentGroup = it }
                .onFailure { currentGroup = null }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    delay(2000)
                    mediaRepository.getSyncPlayInfo()
                        .onSuccess { currentGroup = it }
                } catch (_: Exception) {
                    delay(5000)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
