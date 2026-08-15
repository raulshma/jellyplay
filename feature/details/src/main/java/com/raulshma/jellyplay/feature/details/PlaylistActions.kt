package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isVideoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Playlist-owning slice of the detail screen's state. Published directly by
 * [PlaylistActions] via [state] — collected by the Add-to-Playlist sheet and
 * dialog at their composition sites, never re-flattened into
 * [DetailUiState].
 */
@Immutable
internal data class PlaylistState(
    val playlists: List<Playlist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val isAddingToPlaylist: Boolean = false,
    val showPlaylistPicker: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
)

/**
 * Owns the Add-to-Playlist concern for the detail screen. A plain helper
 * class constructed by the VM (via [Factory]): coroutines are launched on the
 * supplied [scope], playlist-owning state is published via [state], and
 * user-facing messages are pushed through the shared [messages] channel so
 * the helper owns no message channel of its own.
 *
 * The playlist repository path is fully wired (PlaylistRepository mixin on
 * [MediaRepository]); these functions load the picker list and resolve the
 * current item's ids into a playlist. Series expand to their episode ids
 * (a Jellyfin playlist only holds playable items, not a series itself).
 */
internal class PlaylistActions(
    private val scope: CoroutineScope,
    private val session: StateFlow<DetailSession?>,
    private val messages: MutableSharedFlow<DetailMessage>,
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val mediaDetailProvider: MediaDetailProvider,
) {
    /**
     * Hilt factory bundling this helper's exclusive collaborator
     * ([AppRuntimeStateStore], the Watch-Later playlist-id cache) so it never
     * appears in the [DetailViewModel] constructor.
     */
    class Factory @Inject constructor(
        private val mediaRepository: MediaRepository,
        private val appRuntimeStateStore: AppRuntimeStateStore,
    ) {
        fun create(
            scope: CoroutineScope,
            session: StateFlow<DetailSession?>,
            messages: MutableSharedFlow<DetailMessage>,
            strings: DetailStrings,
            mediaDetailProvider: MediaDetailProvider,
        ): PlaylistActions = PlaylistActions(
            scope = scope,
            session = session,
            messages = messages,
            strings = strings,
            mediaRepository = mediaRepository,
            appRuntimeStateStore = appRuntimeStateStore,
            mediaDetailProvider = mediaDetailProvider,
        )
    }

    private val _state = MutableStateFlow(PlaylistState())
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    /**
     * Clears the in-flight flag and closes whichever picker/dialog surface is
     * open. The picker and create-dialog flows are mutually exclusive (opening
     * the create dialog closes the picker), so a single helper covers both:
     * pass [closeDialog] from the create-playlist flow; the picker flow leaves
     * the (already-false) dialog flag untouched.
     */
    private fun finishAction(closeDialog: Boolean = false) {
        _state.update {
            it.copy(
                isAddingToPlaylist = false,
                showPlaylistPicker = false,
                showCreatePlaylistDialog = if (closeDialog) false else it.showCreatePlaylistDialog,
            )
        }
    }

    /**
     * Opens the Add-to-Playlist picker and loads the user's playlists on demand.
     * The list is fetched fresh each open (server playlists are not cached
     * locally, matching the music playlists flow).
     */
    fun openPlaylistPicker() {
        val detail = session.value?.detail ?: return
        // Only playable video items and series are eligible (audio already has
        // its own playlist flow in feature/music).
        val type = detail.item.mediaType
        if (!type.isVideoType && type != MediaType.SERIES) return
        _state.update { it.copy(showPlaylistPicker = true) }
        loadPlaylists()
    }

    fun dismissPlaylistPicker() {
        _state.update { it.copy(showPlaylistPicker = false) }
    }

    fun openCreatePlaylistDialog() {
        _state.update {
            it.copy(
                showPlaylistPicker = false,
                showCreatePlaylistDialog = true,
            )
        }
    }

    fun dismissCreatePlaylistDialog() {
        _state.update { it.copy(showCreatePlaylistDialog = false) }
    }

    private fun loadPlaylists() {
        val itemId = session.value?.detail?.item?.id ?: return
        _state.update { it.copy(isLoadingPlaylists = true) }
        scope.launch {
            mediaRepository.getPlaylists(limit = 100)
                .onSuccess { playlists ->
                    if (session.value?.detail?.item?.id != itemId) return@onSuccess
                    _state.update {
                        it.copy(
                            playlists = playlists.filter { p -> p.canEdit },
                            isLoadingPlaylists = false,
                        )
                    }
                }
                .onFailure {
                    if (session.value?.detail?.item?.id != itemId) return@onFailure
                    _state.update { it.copy(isLoadingPlaylists = false) }
                }
        }
    }

    /**
     * Adds the current item to an existing playlist. For a series, all fetched
     * episodes are added (Jellyfin rejects a bare series id in a playlist).
     */
    fun addToPlaylist(playlist: Playlist) {
        val detail = session.value?.detail ?: return
        scope.launch {
            _state.update { it.copy(isAddingToPlaylist = true) }
            resolvePlaylistItemIds(detail)
                .onSuccess { ids ->
                    if (ids.isEmpty()) {
                        messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued)))
                        return@onSuccess
                    }
                    mediaRepository.addItemsToPlaylist(playlist.id, ids)
                        .onSuccess {
                            messages.tryEmit(
                                DetailMessage.Text(strings.get(R.string.detail_msg_added_to_playlist, playlist.name))
                            )
                        }
                        .onFailure {
                            messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
                        }
                }
                .onFailure {
                    messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
                }
            finishAction()
        }
    }

    /**
     * Adds to the reserved "Watch Later" playlist, creating it on first use and
     * caching its id in preferences so subsequent adds reuse it.
     */
    fun addToWatchLater() {
        val detail = session.value?.detail ?: return
        val cachedId = appRuntimeStateStore.state.value.watchLaterPlaylistId
        scope.launch {
            _state.update { it.copy(isAddingToPlaylist = true) }
            val ids = resolvePlaylistItemIds(detail).getOrElse {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
                finishAction()
                return@launch
            }
            if (ids.isEmpty()) {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued)))
                finishAction()
                return@launch
            }
            if (cachedId != null) {
                mediaRepository.addItemsToPlaylist(cachedId, ids)
                    .onSuccess {
                        messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_added_to_watch_later)))
                    }
                    .onFailure {
                        messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
                    }
            } else {
                mediaRepository.createPlaylist(
                    name = strings.get(R.string.detail_playlist_watch_later),
                    overview = null,
                    itemIds = ids,
                    mediaType = playlistMediaType(detail.item.mediaType),
                ).onSuccess { newId ->
                    appRuntimeStateStore.setWatchLaterPlaylistId(newId)
                    messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_added_to_watch_later)))
                }.onFailure {
                    messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
                }
            }
            finishAction()
        }
    }

    /**
     * Creates a new playlist seeded with the current item and closes the
     * create-playlist dialog.
     */
    fun createAndAddPlaylist(name: String, overview: String) {
        val detail = session.value?.detail ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isAddingToPlaylist = true) }
            val ids = resolvePlaylistItemIds(detail).getOrElse {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
                finishAction(closeDialog = true)
                return@launch
            }
            mediaRepository.createPlaylist(
                name = trimmed,
                overview = overview.ifBlank { null },
                itemIds = ids,
                mediaType = playlistMediaType(detail.item.mediaType),
            ).onSuccess {
                messages.tryEmit(
                    DetailMessage.Text(strings.get(R.string.detail_msg_playlist_created, trimmed))
                )
            }.onFailure {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist)))
            }
            finishAction(closeDialog = true)
        }
    }

    /**
     * Resolves the current item into the Jellyfin item ids to add to a playlist.
     * Movies/episodes/music-videos resolve to themselves; a series expands to
     * its fetched episodes in canonical playback order. Prefers the sorted ids
     * already in the session snapshot; falls back to
     * [MediaDetailProvider.canonicalEpisodeIds] when the picker opened before
     * episodes resolved.
     */
    private suspend fun resolvePlaylistItemIds(
        detail: com.raulshma.jellyplay.core.model.MediaDetail,
    ): Result<List<String>> = runCatching {
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) return@runCatching listOf(item.id)
        // Prefer the canonical playback-order ids already reduced into the
        // session snapshot; fall back to the provider (serves from its session
        // or cold-loads) when the picker opened before episodes resolved. An
        // empty result is surfaced as a no-op message rather than adding the
        // (invalid) series id.
        val sortedIds = session.value?.sortedEpisodes?.takeIf { it.isNotEmpty() }?.map { it.id }
        if (!sortedIds.isNullOrEmpty()) return@runCatching sortedIds
        mediaDetailProvider.canonicalEpisodeIds(item.id)
    }

    /**
     * Maps the item's media type to the value passed to `createPlaylist`. The
     * network layer only cares whether the playlist is audio- or video-typed
     * (it branches to `SdkMediaType.AUDIO` vs `SdkMediaType.VIDEO`), so any
     * non-audio [MediaType] is equivalent here — [MediaType.MOVIE] is used as a
     * representative video type rather than adding a synthetic VIDEO constant.
     */
    private fun playlistMediaType(type: MediaType): MediaType =
        if (type.isAudioType) MediaType.AUDIO else MediaType.MOVIE
}
