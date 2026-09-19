package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The holder's single observable snapshot; individual flows stay private.
 */
@Immutable
data class PlaylistPickerState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val adding: Boolean = false,
    val message: String? = null,
)

/**
 * Deep module for the audio player's "add to playlist" picker (the
 * [com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder] shape):
 * ONE [state] snapshot plus the [open]/[dismiss]/[addTo]/[clearMessage]
 * commands owning the whole lifecycle — the no-current-item open guard, the
 * editable-only [PlaylistRepository.getPlaylists] fetch, the add
 * choreography (success closes the picker and posts the playlist NAME as the
 * message; failure keeps it open and surfaces the error's message), and the
 * dismiss fence while an add is in flight. Extracted from
 * [AudioPlayerViewModel], which kept the five state fields and four commands
 * hand-synced across its uiState.
 *
 * [currentItemId] is the queue's playing-item read as a constructor seam —
 * both the open guard and the add key off "the current track". It is read
 * ONCE per command at entry, so a mid-flight track change never reroutes an
 * in-flight add (and there is deliberately no delayed message auto-clear:
 * the screen owns surfacing/dismissing it). Not a Koin type: the ViewModel
 * constructs it directly over its own [scope].
 */
internal class PlaylistPickerStateHolder(
    private val scope: CoroutineScope,
    private val playlistRepository: PlaylistRepository,
    private val currentItemId: () -> String?,
) {
    private val _state = MutableStateFlow(PlaylistPickerState())
    val state: StateFlow<PlaylistPickerState> = _state.asStateFlow()

    /**
     * Opens the picker and loads the user's editable playlists. A no-op when
     * there is no current item — the picker only ever adds the playing
     * track. Failure keeps the picker open at an empty list (the sheet
     * renders its "no playlists" branch); a dismissal mid-load is not
     * fenced, matching the pre-extraction behaviour.
     */
    fun open() {
        if (currentItemId() == null) return
        _state.update { it.copy(visible = true, loading = true) }
        scope.launch {
            playlistRepository.getPlaylists(limit = 100)
                .onSuccess { all ->
                    val editable = all.filter { it.canEdit }
                    _state.update { it.copy(playlists = editable, loading = false) }
                }
                .onFailure {
                    _state.update { it.copy(loading = false) }
                }
        }
    }

    /** Closes and resets the picker — fenced while an add is in flight. */
    fun dismiss() {
        if (_state.value.adding) return
        _state.update { it.copy(visible = false, playlists = emptyList(), message = null) }
    }

    /**
     * Adds the current track to [playlist]: success closes the picker and
     * posts its name as [PlaylistPickerState.message]; failure keeps the
     * picker open and surfaces the error's message. The item id is captured
     * at entry (see the class doc).
     */
    fun addTo(playlist: Playlist) {
        val itemId = currentItemId() ?: return
        _state.update { it.copy(adding = true) }
        scope.launch {
            playlistRepository.addItemsToPlaylist(playlist.id, listOf(itemId))
                .onSuccess {
                    _state.update {
                        it.copy(
                            adding = false,
                            visible = false,
                            playlists = emptyList(),
                            message = playlist.name,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(adding = false, message = err.message) }
                }
        }
    }

    /** Clears the surfaced message so a repeat add re-fires it. */
    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
