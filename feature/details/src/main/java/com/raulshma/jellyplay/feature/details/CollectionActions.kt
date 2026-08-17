package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isVideoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collection-owning slice of the detail screen's state. Published directly by
 * [CollectionActions] via [state] — collected by the Add-to-Collection sheet
 * and dialog at their composition sites, never re-flattened into
 * [DetailUiState].
 */
@Immutable
internal data class CollectionState(
    val collections: List<CollectionSummary> = emptyList(),
    val isLoadingCollections: Boolean = false,
    val isAddingToCollection: Boolean = false,
    val showCollectionPicker: Boolean = false,
    val showCreateCollectionDialog: Boolean = false,
)

/**
 * Owns the Add-to-Collection concern for the detail screen. A plain helper
 * class constructed by the VM, structurally a mirror of [PlaylistActions]:
 * coroutines launch on the supplied [scope], collection-owning state
 * publishes via [state], and user-facing messages push through the shared
 * [messages] channel so the helper owns no message channel of its own.
 *
 * Unlike playlists, collections have no reserved "Watch Later" bucket and no
 * media-type tagging — the Jellyfin `createCollection` endpoint takes only a
 * name (+ seed ids) — so this helper is correspondingly simpler (and needs no
 * factory: every collaborator is already a [DetailViewModel] dependency).
 */
internal class CollectionActions(
    private val scope: CoroutineScope,
    private val session: StateFlow<DetailSession?>,
    private val messages: MutableSharedFlow<DetailMessage>,
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
    private val mediaDetailProvider: MediaDetailProvider,
) {
    private val _state = MutableStateFlow(CollectionState())
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    /**
     * Clears the in-flight flag and closes whichever picker/dialog surface is
     * open. The picker and create-dialog flows are mutually exclusive (opening
     * the create dialog closes the picker), so a single helper covers both:
     * pass [closeDialog] from the create-collection flow; the picker flow
     * leaves the (already-false) dialog flag untouched.
     */
    private fun finishAction(closeDialog: Boolean = false) {
        _state.update {
            it.copy(
                isAddingToCollection = false,
                showCollectionPicker = false,
                showCreateCollectionDialog = if (closeDialog) false else it.showCreateCollectionDialog,
            )
        }
    }

    /**
     * Opens the Add-to-Collection picker and loads the user's collections on
     * demand. The list is fetched fresh each open (server collections are not
     * cached locally) so a collection just created is immediately selectable.
     * Only playable video items and series are eligible — a series expands to
     * its episode ids in [addToCollection] / [createAndAddCollection].
     */
    fun openCollectionPicker() {
        val detail = session.value?.detail ?: return
        val type = detail.item.mediaType
        if (!type.isVideoType && type != MediaType.SERIES) return
        _state.update { it.copy(showCollectionPicker = true) }
        loadCollections()
    }

    fun dismissCollectionPicker() {
        _state.update { it.copy(showCollectionPicker = false) }
    }

    fun openCreateCollectionDialog() {
        _state.update {
            it.copy(
                showCollectionPicker = false,
                showCreateCollectionDialog = true,
            )
        }
    }

    fun dismissCreateCollectionDialog() {
        _state.update { it.copy(showCreateCollectionDialog = false) }
    }

    private fun loadCollections() {
        val itemId = session.value?.detail?.item?.id ?: return
        _state.update { it.copy(isLoadingCollections = true) }
        scope.launch {
            mediaRepository.getCollections(limit = 100)
                .onSuccess { collections ->
                    if (session.value?.detail?.item?.id != itemId) return@onSuccess
                    _state.update {
                        it.copy(
                            collections = collections,
                            isLoadingCollections = false,
                        )
                    }
                }
                .onFailure {
                    if (session.value?.detail?.item?.id != itemId) return@onFailure
                    _state.update { it.copy(isLoadingCollections = false) }
                }
        }
    }

    /**
     * Adds the current item to an existing collection. For a series, all
     * fetched episodes are added (a Jellyfin collection of a bare series id is
     * not meaningful — collections hold the concrete playable items).
     */
    fun addToCollection(collection: CollectionSummary) {
        val detail = session.value?.detail ?: return
        scope.launch {
            _state.update { it.copy(isAddingToCollection = true) }
            resolveItemIds(detail)
                .onSuccess { ids ->
                    if (ids.isEmpty()) {
                        messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued)))
                        return@onSuccess
                    }
                    mediaRepository.addItemsToCollection(collection.id, ids)
                        .onSuccess {
                            messages.tryEmit(
                                DetailMessage.Text(strings.get(R.string.detail_msg_added_to_collection, collection.name))
                            )
                        }
                        .onFailure {
                            messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_collection)))
                        }
                }
                .onFailure {
                    messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_collection)))
                }
            finishAction()
        }
    }

    /**
     * Creates a new collection seeded with the current item and closes the
     * create-collection dialog. The Jellyfin create endpoint takes only a name
     * (no overview), so the create dialog collects a name alone.
     */
    fun createAndAddCollection(name: String) {
        val detail = session.value?.detail ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isAddingToCollection = true) }
            val ids = resolveItemIds(detail).getOrElse {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_collection)))
                finishAction(closeDialog = true)
                return@launch
            }
            // Guard the same unresolved-series case as [addToCollection]: a
            // series whose episodes haven't resolved yields no seed ids, so
            // creating a collection here would be empty while the success
            // message claims an item was added. Surface it as a no-op instead.
            if (ids.isEmpty()) {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued)))
                finishAction(closeDialog = true)
                return@launch
            }
            mediaRepository.createCollection(
                name = trimmed,
                itemIds = ids,
            ).onSuccess {
                messages.tryEmit(
                    DetailMessage.Text(strings.get(R.string.detail_msg_collection_created, trimmed))
                )
            }.onFailure {
                messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_collection)))
            }
            finishAction(closeDialog = true)
        }
    }

    /**
     * Resolves the current item into the Jellyfin item ids to add to a
     * collection. Movies/episodes/music-videos resolve to themselves; a series
     * expands to its fetched episodes in canonical playback order. Prefers the
     * sorted ids already in the session snapshot; falls back to
     * [MediaDetailProvider.canonicalEpisodeIds] when the picker opened before
     * episodes resolved.
     */
    private suspend fun resolveItemIds(
        detail: MediaDetail,
    ): Result<List<String>> = runCatching {
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) return@runCatching listOf(item.id)
        val sortedIds = session.value?.sortedEpisodes?.takeIf { it.isNotEmpty() }?.map { it.id }
        if (!sortedIds.isNullOrEmpty()) return@runCatching sortedIds
        mediaDetailProvider.canonicalEpisodeIds(item.id)
    }
}
