package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
) : JellyPlayViewModel() {

    private val _playlists = composeState<List<Playlist>>(emptyList())
    val playlists: List<Playlist> get() = _playlists.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _dialogState = composeState<PlaylistDialogState>(PlaylistDialogState.None)
    val dialogState: PlaylistDialogState get() = _dialogState.value

    private val _isMutating = composeState(false)
    val isMutating: Boolean get() = _isMutating.value

    init {
        load()
    }

    fun load() {
        launch {
            _isLoading.value = true
            _error.value = null
            playlistRepository.getPlaylists(limit = 100)
                .onSuccess { _playlists.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun openCreateDialog() {
        _dialogState.value = PlaylistDialogState.Create()
    }

    fun openEditDialog(playlist: Playlist) {
        if (!playlist.canEdit) {
            _error.value = "This playlist is read-only"
            return
        }
        _dialogState.value = PlaylistDialogState.Edit(playlist)
    }

    fun openDeleteDialog(playlist: Playlist) {
        if (!playlist.canDelete) {
            _error.value = "This playlist cannot be deleted"
            return
        }
        _dialogState.value = PlaylistDialogState.Delete(playlist)
    }

    fun dismissDialog() {
        _dialogState.value = PlaylistDialogState.None
    }

    fun createPlaylist(name: String, overview: String) {
        if (name.isBlank()) return
        launch {
            _isMutating.value = true
            _error.value = null
            playlistRepository.createPlaylist(name.trim(), overview.trim().ifBlank { null })
                .onSuccess {
                    _dialogState.value = PlaylistDialogState.None
                    load()
                }
                .onFailure { _error.value = it.message ?: "Failed to create playlist" }
            _isMutating.value = false
        }
    }

    fun updatePlaylist(playlistId: String, name: String, overview: String) {
        if (name.isBlank()) return
        launch {
            _isMutating.value = true
            _error.value = null
            playlistRepository.updatePlaylist(
                playlistId = playlistId,
                name = name.trim(),
                overview = overview.trim().ifBlank { null },
            )
                .onSuccess {
                    _dialogState.value = PlaylistDialogState.None
                    load()
                }
                .onFailure { _error.value = it.message ?: "Failed to update playlist" }
            _isMutating.value = false
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        launch {
            _isMutating.value = true
            _error.value = null
            playlistRepository.deletePlaylist(playlist.id)
                .onSuccess {
                    _dialogState.value = PlaylistDialogState.None
                    load()
                }
                .onFailure { _error.value = it.message ?: "Failed to delete playlist" }
            _isMutating.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}

sealed interface PlaylistDialogState {
    data object None : PlaylistDialogState
    data class Create(val name: String = "", val overview: String = "") : PlaylistDialogState
    data class Edit(val playlist: Playlist, val name: String = playlist.name, val overview: String = playlist.overview.orEmpty()) : PlaylistDialogState
    data class Delete(val playlist: Playlist) : PlaylistDialogState
}
