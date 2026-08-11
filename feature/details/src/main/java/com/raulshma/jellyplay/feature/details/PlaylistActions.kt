package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isVideoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Playlist-owning slice of the detail screen's state. Mirrors the subset of
 * [DetailUiState] fields that [PlaylistActions] mutates so the ViewModel can
 * fold this [StateFlow] into its aggregated uiState without owning any
 * playlist logic itself.
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
 * Owns the Add-to-Playlist concern for the detail screen. A plain helper class
 * (no `@Inject`) constructed by [DetailViewModel]: coroutines are launched on
 * the supplied [scope], playlist-owning state is published via [state], and
 * user-facing messages are pushed through [messageSink] so the helper owns no
 * message channel of its own.
 *
 * The playlist repository path is fully wired (PlaylistRepository mixin on
 * [MediaRepository]); these functions load the picker list and resolve the
 * current item's ids into a playlist. Series expand to their episode ids
 * (a Jellyfin playlist only holds playable items, not a series itself).
 */
internal class PlaylistActions(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val context: Context,
    private val detailProvider: () -> MediaDetail?,
    private val sortedEpisodesProvider: () -> List<MediaItem>,
    private val canonicalEpisodeIds: suspend (String) -> List<String>,
    private val messageSink: (DetailMessage) -> Unit,
) {
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
        val detail = detailProvider() ?: return
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
        val itemId = detailProvider()?.item?.id ?: return
        _state.update { it.copy(isLoadingPlaylists = true) }
        scope.launch {
            mediaRepository.getPlaylists(limit = 100)
                .onSuccess { playlists ->
                    if (detailProvider()?.item?.id != itemId) return@onSuccess
                    _state.update {
                        it.copy(
                            playlists = playlists.filter { p -> p.canEdit },
                            isLoadingPlaylists = false,
                        )
                    }
                }
                .onFailure {
                    if (detailProvider()?.item?.id != itemId) return@onFailure
                    _state.update { it.copy(isLoadingPlaylists = false) }
                }
        }
    }

    /**
     * Adds the current item to an existing playlist. For a series, all fetched
     * episodes are added (Jellyfin rejects a bare series id in a playlist).
     */
    fun addToPlaylist(playlist: Playlist) {
        val detail = detailProvider() ?: return
        scope.launch {
            _state.update { it.copy(isAddingToPlaylist = true) }
            resolvePlaylistItemIds(detail)
                .onSuccess { ids ->
                    if (ids.isEmpty()) {
                        messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_no_episodes_queued)))
                        return@onSuccess
                    }
                    mediaRepository.addItemsToPlaylist(playlist.id, ids)
                        .onSuccess {
                            messageSink(
                                DetailMessage.Text(context.getString(R.string.detail_msg_added_to_playlist, playlist.name))
                            )
                        }
                        .onFailure {
                            messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                        }
                }
                .onFailure {
                    messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                }
            finishAction()
        }
    }

    /**
     * Adds to the reserved "Watch Later" playlist, creating it on first use and
     * caching its id in preferences so subsequent adds reuse it.
     */
    fun addToWatchLater() {
        val detail = detailProvider() ?: return
        val cachedId = appRuntimeStateStore.state.value.watchLaterPlaylistId
        scope.launch {
            _state.update { it.copy(isAddingToPlaylist = true) }
            val ids = resolvePlaylistItemIds(detail).getOrElse {
                messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                finishAction()
                return@launch
            }
            if (ids.isEmpty()) {
                messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_no_episodes_queued)))
                finishAction()
                return@launch
            }
            if (cachedId != null) {
                mediaRepository.addItemsToPlaylist(cachedId, ids)
                    .onSuccess {
                        messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_added_to_watch_later)))
                    }
                    .onFailure {
                        messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                    }
            } else {
                mediaRepository.createPlaylist(
                    name = context.getString(R.string.detail_playlist_watch_later),
                    overview = null,
                    itemIds = ids,
                    mediaType = playlistMediaType(detail.item.mediaType),
                ).onSuccess { newId ->
                    appRuntimeStateStore.setWatchLaterPlaylistId(newId)
                    messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_added_to_watch_later)))
                }.onFailure {
                    messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
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
        val detail = detailProvider() ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isAddingToPlaylist = true) }
            val ids = resolvePlaylistItemIds(detail).getOrElse {
                messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                finishAction(closeDialog = true)
                return@launch
            }
            mediaRepository.createPlaylist(
                name = trimmed,
                overview = overview.ifBlank { null },
                itemIds = ids,
                mediaType = playlistMediaType(detail.item.mediaType),
            ).onSuccess {
                messageSink(
                    DetailMessage.Text(context.getString(R.string.detail_msg_playlist_created, trimmed))
                )
            }.onFailure {
                messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
            }
            finishAction(closeDialog = true)
        }
    }

    /**
     * Resolves the current item into the Jellyfin item ids to add to a playlist.
     * Movies/episodes/music-videos resolve to themselves; a series expands to
     * its fetched episodes in canonical playback order. Prefers the sorted ids
     * already in the UI snapshot; falls back to [canonicalEpisodeIds] when the
     * picker opened before episodes resolved.
     */
    private suspend fun resolvePlaylistItemIds(
        detail: MediaDetail,
    ): Result<List<String>> = runCatching {
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) return@runCatching listOf(item.id)
        // Prefer the canonical playback-order ids already reduced into the UI
        // snapshot; fall back to the supplied resolver (serves from its session
        // or cold-loads) when the picker opened before episodes resolved. An
        // empty result is surfaced as a no-op message rather than adding the
        // (invalid) series id.
        val sortedIds = sortedEpisodesProvider().takeIf { it.isNotEmpty() }?.map { it.id }
        if (!sortedIds.isNullOrEmpty()) return@runCatching sortedIds
        canonicalEpisodeIds(item.id)
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
