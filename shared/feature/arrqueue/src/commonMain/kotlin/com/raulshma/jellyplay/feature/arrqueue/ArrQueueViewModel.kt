package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.SelectionState
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_grab_sent
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_import_sent
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_unknown_error
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Semaphore

/**
 * Inline action dialog shown for a queue row. Drives a small confirmation
 * sheet offering "Remove" vs "Blocklist & Search".
 */
sealed interface ArrQueueAction {
    val item: ArrQueueItem?
    data class Delete(override val item: ArrQueueItem) : ArrQueueAction
    data class Grab(override val item: ArrQueueItem) : ArrQueueAction
    data class Import(override val item: ArrQueueItem) : ArrQueueAction
    /** Bulk delete: no single item; the dialog renders in bulk mode. */
    data object BulkDelete : ArrQueueAction {
        override val item: ArrQueueItem? = null
    }
}

@Immutable
data class ArrQueueUiState(
    val queue: List<ArrQueueItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Stable row keys currently in selection mode. */
    val selection: SelectionState<String> = SelectionState(),
    val actionInProgress: Boolean = false,
    /** Inline action dialog to show, if any. */
    val pendingAction: ArrQueueAction? = null,
) {
    /** Selection reads, delegated from the shared [SelectionState] algebra. */
    val selectedIds: Set<String>
        get() = selection.ids

    val selectionMode: Boolean
        get() = selection.active
}

class ArrQueueViewModel(
    private val arrRepository: ArrRepository,
    experimentalStore: com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore,
) : JellyPlayViewModel() {

    private val _state = composeState(ArrQueueUiState())
    val state: State<ArrQueueUiState> = _state.asState()

    /**
     * One-shot action ack/failure feedback, screen-forward seam replacing the
     * legacy UserMessageBus + Context ctor deps (the bus + string resources
     * live in the Android-only layer and are not visible from commonMain).
     * Same one-shot semantics as the bus: buffered, single collector, never
     * replayed — [ArrQueueScreen] resolves the resource text (compose-resources
     * suspend getString, args included) and forwards through the
     * ArrQueueMessenger actual.
     */
    private val messageChannel = Channel<ArrQueueMessage>(Channel.BUFFERED)
    val messages: Flow<ArrQueueMessage> = messageChannel.receiveAsFlow()

    /**
     * Whether the Direct *arr Integration experimental flag is enabled.
     *
     * Eagerly shared (not `WhileSubscribed`) so the value is always available
     * to [loadQueue] / [refresh] reads via `.value`; mirrors the rationale in
     * `RequestsViewModel.directArrEnabled`.
     */
    private val directArrEnabled: StateFlow<Boolean> = experimentalStore.experimental
        .map { it.enabledExperimentalFeatures.contains(ExperimentalFeature.DIRECT_ARR_INTEGRATION) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Hot stream of the combined queue, mirrored into UI state. */
    val featureEnabled: StateFlow<Boolean> = directArrEnabled

    init {
        // Mirror the repository's queue flow into UI state. Empty until the
        // first successful refresh; safe to collect regardless of flag state.
        launch {
            arrRepository.queue().collect { items ->
                _state.value = _state.value.copy(queue = items)
            }
        }
        refresh()
    }

    fun refresh() {
        if (!directArrEnabled.value) {
            _state.value = _state.value.copy(isLoading = false, error = null)
            return
        }
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            arrRepository.refreshQueue()
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    // ── Selection ────────────────────────────────────────────────────────

    fun toggleSelection(item: ArrQueueItem) {
        _state.value = _state.value.copy(selection = _state.value.selection.toggled(item.rowKey))
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = _state.value.selection.cleared())
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selection = _state.value.selection.selectAll(_state.value.queue.map { it.rowKey }),
        )
    }

    // ── Per-row actions ──────────────────────────────────────────────────

    fun showDeleteDialog(item: ArrQueueItem) {
        _state.value = _state.value.copy(pendingAction = ArrQueueAction.Delete(item))
    }

    fun showBulkDeleteDialog() {
        _state.value = _state.value.copy(pendingAction = ArrQueueAction.BulkDelete)
    }

    fun showGrabDialog(item: ArrQueueItem) {
        _state.value = _state.value.copy(pendingAction = ArrQueueAction.Grab(item))
    }

    fun showImportDialog(item: ArrQueueItem) {
        _state.value = _state.value.copy(pendingAction = ArrQueueAction.Import(item))
    }

    fun dismissAction() {
        _state.value = _state.value.copy(pendingAction = null)
    }

    /**
     * Deletes a single queue row. [blocklist] adds the release to the *arr
     * blocklist; [searchAgain] triggers a fresh search for a replacement.
     */
    fun deleteItem(item: ArrQueueItem, blocklist: Boolean, searchAgain: Boolean) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, pendingAction = null)
            val options = ArrQueueDeleteOptions(
                removeFromClient = true,
                blocklist = blocklist,
                skipRedownload = !searchAgain,
            )
            arrRepository.deleteQueueItem(item, options)
                .onSuccess {
                    if (searchAgain) {
                        val tmdb = item.tmdbId
                        if (tmdb != null) arrRepository.searchForTmdb(tmdb, item.serverKind)
                    }
                }
                .onFailure { e ->
                    messageChannel.trySend(e.message?.let { ArrQueueMessage.Raw(it) } ?: ArrQueueMessage.Error(Res.string.arrqueue_unknown_error))
                }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    /**
     * Bounds [deleteSelected]'s search-again fan-out — same idiom and width
     * ([DEFAULT_FANOUT_PARALLELISM]) as WatchProgressHeatmapViewModel's
     * resolveSemaphore and MusicHomeViewModel's fetchSemaphore.
     */
    private val searchSemaphore = Semaphore(DEFAULT_FANOUT_PARALLELISM)

    /** Bulk-delete every selected row. */
    fun deleteSelected(blocklist: Boolean, searchAgain: Boolean) {
        val selected = _state.value.queue.filter { it.rowKey in _state.value.selectedIds }
        if (selected.isEmpty()) return
        launch {
            _state.value = _state.value.copy(actionInProgress = true, pendingAction = null)
            val options = ArrQueueDeleteOptions(
                removeFromClient = true,
                blocklist = blocklist,
                skipRedownload = !searchAgain,
            )
            arrRepository.deleteQueueItems(selected, options)
                .onSuccess {
                    if (searchAgain) {
                        // Per-item searches at bounded parallelism — a
                        // 30-row selection used to pay 30 sequential
                        // round-trips while actionInProgress blocked
                        // further actions. mapConcurrent awaits every
                        // search before returning, so clearSelection()
                        // stays behind the whole fan-out exactly like the
                        // old forEach; each searchForTmdb Result is
                        // discarded per item (a failed search never aborts
                        // the rest) and rows without a tmdbId are skipped.
                        // Grouped bulk search is not exposed by the repository.
                        searchSemaphore.mapConcurrent(selected) { item ->
                            item.tmdbId?.let { arrRepository.searchForTmdb(it, item.serverKind) }
                        }
                    }
                    clearSelection()
                }
                .onFailure { e ->
                    messageChannel.trySend(e.message?.let { ArrQueueMessage.Raw(it) } ?: ArrQueueMessage.Error(Res.string.arrqueue_unknown_error))
                }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun grabItem(item: ArrQueueItem) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, pendingAction = null)
            arrRepository.grabQueueItem(item)
                .onSuccess {
                    messageChannel.trySend(ArrQueueMessage.Info(Res.string.arrqueue_grab_sent, listOf(item.title)))
                    refresh()
                }
                .onFailure { e ->
                    messageChannel.trySend(e.message?.let { ArrQueueMessage.Raw(it) } ?: ArrQueueMessage.Error(Res.string.arrqueue_unknown_error))
                }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun importItem(item: ArrQueueItem) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, pendingAction = null)
            arrRepository.importQueueItem(item)
                .onSuccess {
                    messageChannel.trySend(ArrQueueMessage.Info(Res.string.arrqueue_import_sent, listOf(item.title)))
                    refresh()
                }
                .onFailure { e ->
                    messageChannel.trySend(e.message?.let { ArrQueueMessage.Raw(it) } ?: ArrQueueMessage.Error(Res.string.arrqueue_unknown_error))
                }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    private val ArrQueueItem.rowKey: String
        get() = "${serverKind.name}|$queueId|${serverId.ifEmpty { "_" }}"
}
