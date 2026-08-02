package com.raulshma.jellyplay.feature.arrqueue

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

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
    val selectedIds: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
    /** R3: brief success message (grab/import sent) for a snackbar. */
    val actionSuccess: String? = null,
    /** Inline action dialog to show, if any. */
    val pendingAction: ArrQueueAction? = null,
)

@HiltViewModel
class ArrQueueViewModel @Inject constructor(
    private val arrRepository: ArrRepository,
    @ApplicationContext private val context: Context,
    experimentalStore: com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore,
) : JellyPlayViewModel() {

    private val _state = composeState(ArrQueueUiState())
    val state: State<ArrQueueUiState> = _state.asState()

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
        val key = item.rowKey
        val current = _state.value.selectedIds
        val next = if (key in current) current - key else current + key
        _state.value = _state.value.copy(
            selectedIds = next,
            selectionMode = next.isNotEmpty(),
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedIds = emptySet(), selectionMode = false)
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selectedIds = _state.value.queue.map { it.rowKey }.toSet(),
            selectionMode = true,
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
            _state.value = _state.value.copy(actionInProgress = true, actionError = null, pendingAction = null)
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
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    /** Bulk-delete every selected row. */
    fun deleteSelected(blocklist: Boolean, searchAgain: Boolean) {
        val selected = _state.value.queue.filter { it.rowKey in _state.value.selectedIds }
        if (selected.isEmpty()) return
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null, pendingAction = null)
            val options = ArrQueueDeleteOptions(
                removeFromClient = true,
                blocklist = blocklist,
                skipRedownload = !searchAgain,
            )
            arrRepository.deleteQueueItems(selected, options)
                .onSuccess {
                    if (searchAgain) {
                        // Fire-and-forget per-item searches; grouped bulk
                        // search is not exposed by the repository.
                        selected.forEach { item ->
                            item.tmdbId?.let { arrRepository.searchForTmdb(it, item.serverKind) }
                        }
                    }
                    clearSelection()
                }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun grabItem(item: ArrQueueItem) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null, actionSuccess = null, pendingAction = null)
            arrRepository.grabQueueItem(item)
                .onSuccess {
                    _state.value = _state.value.copy(actionSuccess = context.getString(R.string.arrqueue_grab_sent, item.title))
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun importItem(item: ArrQueueItem) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null, actionSuccess = null, pendingAction = null)
            arrRepository.importQueueItem(item)
                .onSuccess {
                    _state.value = _state.value.copy(actionSuccess = context.getString(R.string.arrqueue_import_sent, item.title))
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionError = null)
    }

    fun clearActionSuccess() {
        _state.value = _state.value.copy(actionSuccess = null)
    }

    private val ArrQueueItem.rowKey: String
        get() = "${serverKind.name}|$queueId|${serverId.ifEmpty { "_" }}"
}
