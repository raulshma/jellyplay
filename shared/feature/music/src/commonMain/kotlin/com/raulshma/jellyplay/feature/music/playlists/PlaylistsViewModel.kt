package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.collection.SimpleListCollection
import kotlinx.coroutines.flow.StateFlow

/**
 * The standalone playlists route's ViewModel. The load/refresh/error ladder
 * rides the chassis ([SimpleListCollection]); everything below the load —
 * the create/edit/delete dialog state machine and the mutation commands —
 * is genuinely per-collection and deliberately stays here (the playlists
 * adapter keeps its own screen scaffolding around it). The chassis surface is
 * exposed as [StateFlow]s (the screen collects them — `StateFlow.value` is
 * not Compose-observed); the dialog/mutation surface stays Compose snapshot
 * state (snapshot reads in composition are observed).
 */
class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
) : JellyPlayViewModel() {

    private val collection = SimpleListCollection<Playlist>(
        scope = scope,
    ) { _ ->
        playlistRepository.getPlaylists(limit = 100)
    }

    val playlists: StateFlow<List<Playlist>> = collection.items

    val isLoading: StateFlow<Boolean> = collection.isLoading

    /** Last chassis load failure, or null — the screen renders its message with the kind's fallback under it. */
    val loadError: StateFlow<Throwable?> = collection.error

    /**
     * Command-guard errors (read-only / undeletable playlist) surface beside
     * the chassis load error — the freshest command error wins.
     * [PlaylistCommandError.Reported] carries server text verbatim;
     * [PlaylistCommandError.Declared] resolves at render.
     */
    private val _commandError = composeState<PlaylistCommandError?>(null)
    val commandError: PlaylistCommandError? get() = _commandError.value

    private val _dialogState = composeState<PlaylistDialogState>(PlaylistDialogState.None)
    val dialogState: PlaylistDialogState get() = _dialogState.value

    private val _isMutating = composeState(false)
    val isMutating: Boolean get() = _isMutating.value

    /**
     * Forced reload (pull-to-refresh, retry, post-mutation). The initial load
     * is the chassis's own cache-honouring `refresh(force = false)`.
     */
    fun load() {
        _commandError.value = null
        collection.refresh(force = true)
    }

    fun openCreateDialog() {
        _dialogState.value = PlaylistDialogState.Create()
    }

    fun openEditDialog(playlist: Playlist) {
        if (!playlist.canEdit) {
            _commandError.value = PlaylistCommandError.Declared.ReadOnly
            return
        }
        _dialogState.value = PlaylistDialogState.Edit(playlist)
    }

    fun openDeleteDialog(playlist: Playlist) {
        if (!playlist.canDelete) {
            _commandError.value = PlaylistCommandError.Declared.NotDeletable
            return
        }
        _dialogState.value = PlaylistDialogState.Delete(playlist)
    }

    fun dismissDialog() {
        _dialogState.value = PlaylistDialogState.None
    }

    /**
     * The shared mutation choreography: mutate-guard flag, clear the stale
     * command error, run [command], then close the dialog and reload on
     * success or surface [declaredFailure] when the server sent no message.
     */
    private fun mutate(declaredFailure: PlaylistCommandError.Declared, command: suspend () -> Result<*>) {
        launch {
            _isMutating.value = true
            _commandError.value = null
            command()
                .onSuccess {
                    _dialogState.value = PlaylistDialogState.None
                    load()
                }
                .onFailure {
                    _commandError.value = it.message
                        ?.let(PlaylistCommandError::Reported)
                        ?: declaredFailure
                }
            _isMutating.value = false
        }
    }

    fun createPlaylist(name: String, overview: String) {
        if (name.isBlank()) return
        mutate(PlaylistCommandError.Declared.CreateFailed) {
            playlistRepository.createPlaylist(name.trim(), overview.trim().ifBlank { null })
        }
    }

    fun updatePlaylist(playlistId: String, name: String, overview: String) {
        if (name.isBlank()) return
        mutate(PlaylistCommandError.Declared.UpdateFailed) {
            playlistRepository.updatePlaylist(
                playlistId = playlistId,
                name = name.trim(),
                overview = overview.trim().ifBlank { null },
            )
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        mutate(PlaylistCommandError.Declared.DeleteFailed) {
            playlistRepository.deletePlaylist(playlist.id)
        }
    }

    fun clearError() {
        _commandError.value = null
    }
}

/**
 * A playlists command/mutation failure. [Reported] carries the server's own
 * message verbatim; [Declared] are the guard/fallback cases that resolve to
 * localized strings at render (the `ConnectionProbe.Failure` shape).
 */
sealed interface PlaylistCommandError {
    data class Reported(val message: String) : PlaylistCommandError

    enum class Declared : PlaylistCommandError {
        ReadOnly,
        NotDeletable,
        CreateFailed,
        UpdateFailed,
        DeleteFailed,
    }
}

sealed interface PlaylistDialogState {
    data object None : PlaylistDialogState
    data class Create(val name: String = "", val overview: String = "") : PlaylistDialogState
    data class Edit(val playlist: Playlist, val name: String = playlist.name, val overview: String = playlist.overview.orEmpty()) : PlaylistDialogState
    data class Delete(val playlist: Playlist) : PlaylistDialogState
}
