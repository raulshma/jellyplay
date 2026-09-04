package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.isAudioType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_added_to_playlist
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_added_to_watch_later
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_add_to_playlist
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_no_episodes_queued
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_playlist_created
import com.raulshma.jellyplay.feature.details.generated.resources.detail_playlist_watch_later

/**
 * Playlist adapter over [AddToTargetActions]: fetches the playlist list
 * (editable-only), and maps the generic add/create calls onto the playlist
 * endpoints — create keeps its media-type tagging (the network layer branches
 * audio vs video) and overview field.
 */
internal class PlaylistAddTarget(
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
) : AddTargetAdapter<Playlist> {
    override suspend fun fetchTargets(): Result<List<Playlist>> = mediaRepository.getPlaylists(limit = 100)

    override fun filterFetched(targets: List<Playlist>): List<Playlist> = targets.filter { it.canEdit }

    override fun nameOf(target: Playlist): String = target.name

    override fun idOf(target: Playlist): String = target.id

    override suspend fun addToTarget(targetId: String, ids: List<String>): Result<Unit> =
        mediaRepository.addItemsToPlaylist(targetId, ids)

    override suspend fun createTarget(
        name: String,
        overview: String?,
        ids: List<String>,
        itemType: MediaType,
    ): Result<Unit> = mediaRepository.createPlaylist(
        name = name,
        overview = overview,
        itemIds = ids,
        mediaType = playlistMediaType(itemType),
    ).map { }

    override suspend fun addedMessage(targetName: String): String =
        strings.get(Res.string.detail_msg_added_to_playlist, targetName)

    override suspend fun createdMessage(name: String): String =
        strings.get(Res.string.detail_msg_playlist_created, name)

    override suspend fun couldntAddMessage(): String =
        strings.get(Res.string.detail_msg_couldnt_add_to_playlist)

    override suspend fun noEpisodesMessage(): String =
        strings.get(Res.string.detail_msg_no_episodes_queued)
}

/**
 * Owns the reserved Watch-Later quick action: adds to the cached Watch-Later
 * playlist, creating it on first use and persisting its id in preferences so
 * subsequent adds reuse it. Deliberately NOT part of [AddToTargetActions] —
 * there is no picker and no create dialog; the container is resolved
 * implicitly — but it shares [resolveTargetItemIds] and the empty-ids guard
 * policy, and it rides [playlistPicker]'s collaborators (scope, session,
 * message channel, id resolution) and in-flight/sheet-close choreography: the
 * Watch-Later row lives in the playlist picker sheet, so the add shows that
 * sheet's spinner and closes the sheet when it settles.
 */
internal class WatchLaterActions(
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val playlistPicker: AddToTargetActions<Playlist>,
) {
    fun addToWatchLater() {
        val detail = playlistPicker.session.value?.detail ?: return
        val cachedId = appRuntimeStateStore.state.value.watchLaterPlaylistId
        playlistPicker.scope.launch {
            playlistPicker.markAdding()
            val ids = resolveTargetItemIds(playlistPicker.session, playlistPicker.mediaDetailProvider, detail).getOrElse {
                messages(Res.string.detail_msg_couldnt_add_to_playlist)
                playlistPicker.finishAndDismiss()
                return@launch
            }
            if (ids.isEmpty()) {
                messages(Res.string.detail_msg_no_episodes_queued)
                playlistPicker.finishAndDismiss()
                return@launch
            }
            if (cachedId != null) {
                mediaRepository.addItemsToPlaylist(cachedId, ids)
                    .onSuccess { messages(Res.string.detail_msg_added_to_watch_later) }
                    .onFailure { messages(Res.string.detail_msg_couldnt_add_to_playlist) }
            } else {
                mediaRepository.createPlaylist(
                    name = strings.get(Res.string.detail_playlist_watch_later),
                    overview = null,
                    itemIds = ids,
                    mediaType = playlistMediaType(detail.item.mediaType),
                ).onSuccess { newId ->
                    appRuntimeStateStore.setWatchLaterPlaylistId(newId)
                    messages(Res.string.detail_msg_added_to_watch_later)
                }.onFailure {
                    messages(Res.string.detail_msg_couldnt_add_to_playlist)
                }
            }
            playlistPicker.finishAndDismiss()
        }
    }

    private suspend fun messages(stringRes: StringResource) {
        playlistPicker.messages.tryEmit(DetailMessage.Text(strings.get(stringRes)))
    }
}

/**
 * The playlist target bundle the VM exposes: the shared picker/create module
 * plus the Watch-Later quick action. Constructed via [Factory] so the
 * helper-exclusive collaborator ([AppRuntimeStateStore], the Watch-Later
 * playlist-id cache) never appears in the [DetailViewModel] constructor.
 */
internal class PlaylistTargets(
    val picker: AddToTargetActions<Playlist>,
    val watchLater: WatchLaterActions,
) {
    class Factory constructor(
        private val mediaRepository: MediaRepository,
        private val appRuntimeStateStore: AppRuntimeStateStore,
    ) {
        fun create(
            scope: CoroutineScope,
            session: StateFlow<DetailSession?>,
            messages: MutableSharedFlow<DetailMessage>,
            strings: DetailStrings,
            mediaDetailProvider: MediaDetailProvider,
        ): PlaylistTargets {
            val picker = AddToTargetActions(
                scope = scope,
                session = session,
                messages = messages,
                adapter = PlaylistAddTarget(strings, mediaRepository),
                mediaDetailProvider = mediaDetailProvider,
            )
            return PlaylistTargets(
                picker = picker,
                watchLater = WatchLaterActions(
                    strings = strings,
                    mediaRepository = mediaRepository,
                    appRuntimeStateStore = appRuntimeStateStore,
                    playlistPicker = picker,
                ),
            )
        }
    }
}

/**
 * Maps the item's media type to the value passed to `createPlaylist`. The
 * network layer only cares whether the playlist is audio- or video-typed
 * (it branches to `SdkMediaType.AUDIO` vs `SdkMediaType.VIDEO`), so any
 * non-audio [MediaType] is equivalent here — [MediaType.MOVIE] is used as a
 * representative video type rather than adding a synthetic VIDEO constant.
 */
internal fun playlistMediaType(type: MediaType): MediaType =
    if (type.isAudioType) MediaType.AUDIO else MediaType.MOVIE
