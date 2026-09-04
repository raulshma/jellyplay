package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
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
 * Target-container slice of the detail screen's state (playlists and
 * collections each instantiate it with their item type). Published directly by
 * [AddToTargetActions] via [state] — collected by the picker sheet and create
 * dialog at their composition sites, never re-flattened into [DetailUiState].
 */
@Immutable
internal data class AddToTargetState<T>(
    val targets: List<T> = emptyList(),
    val isLoadingTargets: Boolean = false,
    val isAdding: Boolean = false,
    val showPicker: Boolean = false,
    val showCreateDialog: Boolean = false,
)

/**
 * The one seam that varies between the Add-to-Playlist and Add-to-Collection
 * flows: which container list to fetch, how to mutate the container, and which
 * user-facing strings to emit. Everything else — the picker/create-dialog
 * choreography, the stale-load guard, the empty-ids guard, the series→episode
 * id resolution — is [AddToTargetActions], written once.
 */
internal interface AddTargetAdapter<T> {
    /** Fetches the user's containers for the picker (fresh, never cached). */
    suspend fun fetchTargets(): Result<List<T>>

    /** Picker-visibility filter over the fetched list (playlists: `canEdit`). */
    fun filterFetched(targets: List<T>): List<T> = targets

    /** Container name for the "added to X" message. */
    fun nameOf(target: T): String

    /** Container id for the add-to-existing call. */
    fun idOf(target: T): String

    suspend fun addToTarget(targetId: String, ids: List<String>): Result<Unit>

    /**
     * Creates a new container seeded with [ids]. Adapters fold their
     * endpoint's shape (playlist media-type tagging, collection's name-only
     * create) — the caller guarantees `ids` is non-empty. [overview] is null
     * when blank; endpoints that take no overview ignore it.
     */
    suspend fun createTarget(
        name: String,
        overview: String?,
        ids: List<String>,
        itemType: MediaType,
    ): Result<Unit>

    suspend fun addedMessage(targetName: String): String
    suspend fun createdMessage(name: String): String
    suspend fun couldntAddMessage(): String
    suspend fun noEpisodesMessage(): String
}

/**
 * Owns the add-current-item-to-a-picked-container concern for the detail
 * screen, for any container type ([Playlist] via the playlist adapter,
 * [CollectionSummary] via the collection adapter — two adapters, one seam).
 * A plain helper constructed by the VM: coroutines launch on the supplied
 * [scope], container state publishes via [state], user-facing messages push
 * through the shared [messages] channel so the helper owns no channel of its
 * own.
 *
 * Policies written once here (they used to live as near-verbatim mirrors in
 * the playlist/collection pair, and had already drifted on the empty-ids
 * guard — the playlist create path could create an empty playlist while
 * claiming success):
 *  - eligibility: only playable video items and series open the picker;
 *  - stale-load guard: a list load that resolves after the item changed is
 *    dropped, not written;
 *  - empty-ids guard: a series whose episodes never resolved is a no-op with
 *    the no-episodes message — on add AND create, both targets.
 */
internal class AddToTargetActions<T>(
    internal val scope: CoroutineScope,
    internal val session: StateFlow<DetailSession?>,
    internal val messages: MutableSharedFlow<DetailMessage>,
    private val adapter: AddTargetAdapter<T>,
    internal val mediaDetailProvider: MediaDetailProvider,
) {
    private val _state = MutableStateFlow(AddToTargetState<T>())
    val state: StateFlow<AddToTargetState<T>> = _state.asStateFlow()

    /** Marks an action in flight for this target's sheet surfaces. */
    internal fun markAdding() {
        _state.update { it.copy(isAdding = true) }
    }

    /**
     * Clears the in-flight flag and closes whichever picker/dialog surface is
     * open. The picker and create-dialog flows are mutually exclusive (opening
     * the create dialog closes the picker), so a single helper covers both:
     * pass [closeDialog] from the create flow; the picker flow leaves the
     * (already-false) dialog flag untouched.
     */
    internal fun finishAndDismiss(closeDialog: Boolean = false) {
        _state.update {
            it.copy(
                isAdding = false,
                showPicker = false,
                showCreateDialog = if (closeDialog) false else it.showCreateDialog,
            )
        }
    }

    /**
     * Opens the picker and loads the user's containers on demand. The list is
     * fetched fresh each open (server containers are not cached locally), so a
     * container just created is immediately selectable.
     */
    fun openPicker() {
        val detail = session.value?.detail ?: return
        // Only playable video items and series are eligible (audio has its own
        // playlist flow in feature/music).
        val type = detail.item.mediaType
        if (!type.isVideoType && type != MediaType.SERIES) return
        _state.update { it.copy(showPicker = true) }
        loadTargets()
    }

    fun dismissPicker() {
        _state.update { it.copy(showPicker = false) }
    }

    fun openCreateDialog() {
        _state.update {
            it.copy(
                showPicker = false,
                showCreateDialog = true,
            )
        }
    }

    fun dismissCreateDialog() {
        _state.update { it.copy(showCreateDialog = false) }
    }

    private fun loadTargets() {
        val itemId = session.value?.detail?.item?.id ?: return
        _state.update { it.copy(isLoadingTargets = true) }
        scope.launch {
            adapter.fetchTargets()
                .onSuccess { targets ->
                    if (session.value?.detail?.item?.id != itemId) return@onSuccess
                    _state.update {
                        it.copy(
                            targets = adapter.filterFetched(targets),
                            isLoadingTargets = false,
                        )
                    }
                }
                .onFailure {
                    if (session.value?.detail?.item?.id != itemId) return@onFailure
                    _state.update { it.copy(isLoadingTargets = false) }
                }
        }
    }

    /**
     * Adds the current item to an existing container. For a series, all
     * fetched episodes are added (containers hold concrete playable items, not
     * a bare series id).
     */
    fun addTo(target: T) {
        val detail = session.value?.detail ?: return
        scope.launch {
            _state.update { it.copy(isAdding = true) }
            resolveTargetItemIds(session, mediaDetailProvider, detail)
                .onSuccess { ids ->
                    if (ids.isEmpty()) {
                        messages.tryEmit(DetailMessage.Text(adapter.noEpisodesMessage()))
                        return@onSuccess
                    }
                    adapter.addToTarget(adapter.idOf(target), ids)
                        .onSuccess {
                            messages.tryEmit(DetailMessage.Text(adapter.addedMessage(adapter.nameOf(target))))
                        }
                        .onFailure {
                            messages.tryEmit(DetailMessage.Text(adapter.couldntAddMessage()))
                        }
                }
                .onFailure {
                    messages.tryEmit(DetailMessage.Text(adapter.couldntAddMessage()))
                }
            finishAndDismiss()
        }
    }

    /**
     * Creates a new container seeded with the current item and closes the
     * create dialog. The empty-ids guard runs BEFORE the create call on every
     * adapter — creating an empty container while the success message claims
     * an item was added is the drift this module exists to make impossible.
     */
    fun createAndAdd(name: String, overview: String = "") {
        val detail = session.value?.detail ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            _state.update { it.copy(isAdding = true) }
            val ids = resolveTargetItemIds(session, mediaDetailProvider, detail).getOrElse {
                messages.tryEmit(DetailMessage.Text(adapter.couldntAddMessage()))
                finishAndDismiss(closeDialog = true)
                return@launch
            }
            if (ids.isEmpty()) {
                messages.tryEmit(DetailMessage.Text(adapter.noEpisodesMessage()))
                finishAndDismiss(closeDialog = true)
                return@launch
            }
            adapter.createTarget(trimmed, overview.ifBlank { null }, ids, detail.item.mediaType)
                .onSuccess {
                    messages.tryEmit(DetailMessage.Text(adapter.createdMessage(trimmed)))
                }
                .onFailure {
                    messages.tryEmit(DetailMessage.Text(adapter.couldntAddMessage()))
                }
            finishAndDismiss(closeDialog = true)
        }
    }
}

/**
 * Resolves the current item into the Jellyfin item ids to add to a container.
 * Movies/episodes/music-videos resolve to themselves; a series expands to its
 * fetched episodes in canonical playback order. Prefers the sorted ids already
 * in the session snapshot; falls back to
 * [MediaDetailProvider.canonicalEpisodeIds] when the picker opened before
 * episodes resolved.
 */
internal suspend fun resolveTargetItemIds(
    session: StateFlow<DetailSession?>,
    mediaDetailProvider: MediaDetailProvider,
    detail: MediaDetail,
): Result<List<String>> = runCatching {
    val item = detail.item
    if (item.mediaType != MediaType.SERIES) return@runCatching listOf(item.id)
    val sortedIds = session.value?.sortedEpisodes?.takeIf { it.isNotEmpty() }?.map { it.id }
    if (!sortedIds.isNullOrEmpty()) return@runCatching sortedIds
    mediaDetailProvider.canonicalEpisodeIds(item.id)
}
