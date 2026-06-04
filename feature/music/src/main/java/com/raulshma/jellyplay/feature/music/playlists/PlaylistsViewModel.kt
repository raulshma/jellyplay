package com.raulshma.jellyplay.feature.music.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var dialogState by mutableStateOf<PlaylistDialogState>(PlaylistDialogState.None)
        private set

    var isMutating by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getPlaylists(limit = 100)
                .onSuccess { playlists = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun openCreateDialog() {
        dialogState = PlaylistDialogState.Create()
    }

    fun openEditDialog(playlist: Playlist) {
        if (!playlist.canEdit) {
            error = "This playlist is read-only"
            return
        }
        dialogState = PlaylistDialogState.Edit(playlist)
    }

    fun openDeleteDialog(playlist: Playlist) {
        if (!playlist.canDelete) {
            error = "This playlist cannot be deleted"
            return
        }
        dialogState = PlaylistDialogState.Delete(playlist)
    }

    fun dismissDialog() {
        dialogState = PlaylistDialogState.None
    }

    fun createPlaylist(name: String, overview: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            isMutating = true
            error = null
            mediaRepository.createPlaylist(name.trim(), overview.trim().ifBlank { null })
                .onSuccess {
                    dialogState = PlaylistDialogState.None
                    load()
                }
                .onFailure { error = it.message ?: "Failed to create playlist" }
            isMutating = false
        }
    }

    fun updatePlaylist(playlistId: String, name: String, overview: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            isMutating = true
            error = null
            mediaRepository.updatePlaylist(
                playlistId = playlistId,
                name = name.trim(),
                overview = overview.trim().ifBlank { null },
            )
                .onSuccess {
                    dialogState = PlaylistDialogState.None
                    load()
                }
                .onFailure { error = it.message ?: "Failed to update playlist" }
            isMutating = false
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            isMutating = true
            error = null
            mediaRepository.deletePlaylist(playlist.id)
                .onSuccess {
                    dialogState = PlaylistDialogState.None
                    load()
                }
                .onFailure { error = it.message ?: "Failed to delete playlist" }
            isMutating = false
        }
    }

    fun clearError() {
        error = null
    }
}

sealed interface PlaylistDialogState {
    data object None : PlaylistDialogState
    data class Create(val name: String = "", val overview: String = "") : PlaylistDialogState
    data class Edit(val playlist: Playlist, val name: String = playlist.name, val overview: String = playlist.overview.orEmpty()) : PlaylistDialogState
    data class Delete(val playlist: Playlist) : PlaylistDialogState
}
