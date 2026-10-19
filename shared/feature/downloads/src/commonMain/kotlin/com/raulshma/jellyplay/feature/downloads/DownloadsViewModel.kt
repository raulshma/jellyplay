package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncOptions
import com.raulshma.jellyplay.core.model.SelectionState
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.ConfirmationHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Immutable
data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Stable ids currently in selection mode. */
    val selection: SelectionState<String> = SelectionState(),
) {
    /** Selection reads, delegated from the shared [SelectionState] algebra. */
    val selectedIds: Set<String>
        get() = selection.ids

    val selectionMode: Boolean
        get() = selection.active
}

/**
 * A downloaded item eligible for a force resync (completed, or stalled after
 * partial artifacts landed), with enough episode context (series name + SxxExx)
 * to render the same identification line the downloads list shows, so episodes
 * are distinguishable in the force-resync picker.
 */
@Immutable
data class ForceResyncCandidate(
    val id: String,
    val name: String,
    val mediaType: com.raulshma.jellyplay.core.model.MediaType,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

class DownloadsViewModel(
    private val queue: DownloadQueue,
    private val offlineRepository: OfflineRepository,
    private val resync: OfflineResync,
    private val downloadsStore: DownloadsStore,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.flow

    // ── Delete confirmations ─────────────────────────────────────────────
    // Deleting a completed download removes the file from disk, so we confirm
    // first — matching the unified MediaDetailScreen delete confirmations.
    // Both machines use ConfirmationHost's synchronous settle arm.

    /**
     * Pending single-item delete. Settle arm: [ConfirmationHost.confirm]'s
     * synchronous clear — the deletes are fire-and-forget VM calls, so the
     * machine settles at the confirm tap and the guard's in-flight arm is
     * unreachable (dismiss/confirm pass `inFlight = false`). A confirm write
     * that never cleared is structurally impossible with the settle arm
     * inside [ConfirmationHost.confirm].
     */
    val pendingDelete = ConfirmationHost<DownloadItem>()

    /** Pending bulk delete of the current selection. Same synchronous settle as [pendingDelete]. */
    val pendingBulkDelete = ConfirmationHost<Unit>()

    /**
     * One-shot delete feedback, screen-forward seam. Buffered, single
     * collector, never replayed — [DownloadsScreen] resolves the
     * resource text and posts it to the shared UserMessageBus.
     */
    private val messageChannel = Channel<DownloadsUserMessage>(Channel.BUFFERED)
    val messages: Flow<DownloadsUserMessage> = messageChannel.receiveAsFlow()

    /** Live count of items flagged for a metadata/image resync — appbar badge. */
    val updatesAvailable: StateFlow<Int> =
        offlineRepository.getUpdatesCount().stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Items with updates, reactive — drives the resync sheet content. */
    val updateRows: StateFlow<List<OfflineSyncUpdate>> =
        offlineRepository.getItemsWithUpdates()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live batch resync progress (per-item phase + aggregate counts). */
    val resyncProgress: StateFlow<ResyncBatchProgress> = resync.batchProgress

    /** True while a batch freshness check is running. */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /**
     * Structural (status-change-only) projection of the list, shared between
     * the uiState collector and the [totalStorageBytes] combine so the
     * repository flow has a single upstream subscription.
     */
    private val structuralItems = MutableStateFlow<List<DownloadItem>>(emptyList())

    /**
     * Live per-row byte/speed for in-flight downloads, keyed by download id —
     * the moving half of the split uiState. Re-emitting the whole list (and
     * re-executing the entire screen) per progress event would be too
     * costly; rows read their moving values from here while the
     * list itself only re-emits on structural change. The backing StateFlow
     * is conflated by construction (stateIn), so a burst of ticks collapses
     * to the latest map.
     *
     * Exit-transition retention: the live query drops a row the moment its
     * status leaves the in-flight set, which can land a frame BEFORE the
     * structural list re-emits with the row's new status — without retention
     * the still-DOWNLOADING row would flash back to its stale structural
     * bytes for that frame. The scan accumulator keeps the last live value
     * for every row the structural list still shows as DOWNLOADING and
     * forgets a row once its structural status moves on (or it leaves the
     * list).
     */
    val progressById: StateFlow<Map<String, DownloadProgress>> =
        queue.getActiveDownloadProgress()
            .combine(structuralItems) { live, items -> live to items }
            .scan(emptyMap<String, DownloadProgress>()) { retained, (live, structural) ->
                val stillDownloading = structural
                    .asSequence()
                    .filter { it.status == DownloadStatus.DOWNLOADING }
                    .mapTo(HashSet()) { it.id }
                val merged = HashMap(retained)
                merged.keys.retainAll(stillDownloading)
                merged.putAll(live)
                merged
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Total bytes used, kept live across progress ticks. Lives OUTSIDE
     * [uiState] on purpose: folding it in would re-emit uiState per tick and
     * drag the whole screen body along with it. Computed as the structural
     * sum with each in-flight row's bytes overridden by its live value —
     * exactly the Σ downloadedBytes the pre-split uiState carried — and
     * collected by the screen only inside the storage-header leaf.
     */
    val totalStorageBytes: StateFlow<Long> =
        combine(structuralItems, progressById) { items, progress ->
            items.sumOf { item -> progress[item.id]?.downloadedBytes ?: item.downloadedBytes }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * The download storage ceiling as bytes, or null when no cap is set —
     * `maxDownloadStorageGb == 0` means unlimited, the same encoding
     * [com.raulshma.jellyplay.core.data.repository.StoragePolicy] enforces at
     * enqueue time. Consumed by the screen's quota bar.
     */
    val maxStorageBytes: StateFlow<Long?> =
        downloadsStore.downloads
            .map { slice ->
                slice.maxDownloadStorageGb
                    .takeIf { it > 0 }
                    ?.let { gb -> gb * 1024L * 1024L * 1024L }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    // ── List filter / sort / series grouping (gh-175) ────────────────────

    /**
     * Type filter for the downloads list — All / Movies / Series. Persisted
     * through [DownloadsStore] so the choice survives app restarts.
     */
    private val _filter = MutableStateFlow(DownloadListFilter.ALL)
    val filter: StateFlow<DownloadListFilter> = _filter.asStateFlow()

    /**
     * Sort for the downloads list. Orders series groups and flat rows relative
     * to each other only — the season → episode order INSIDE a group is always
     * numeric playback order regardless of this setting (see
     * [DownloadListSort]). Persisted through [DownloadsStore].
     */
    private val _sort = MutableStateFlow(DownloadListSort.RECENT)
    val sort: StateFlow<DownloadListSort> = _sort.asStateFlow()

    fun setFilter(value: DownloadListFilter) {
        _filter.value = value
        launch { downloadsStore.setDownloadsListFilter(value.name) }
    }

    fun setSort(value: DownloadListSort) {
        _sort.value = value
        launch { downloadsStore.setDownloadsListSort(value.name) }
    }

    /** Expanded series/season group keys — groups start collapsed (session-only). */
    private val _expandedKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedKeys: StateFlow<Set<String>> = _expandedKeys.asStateFlow()

    /** Toggles a series or season group's expanded state. */
    fun toggleExpanded(key: String) {
        _expandedKeys.update { if (key in it) it - key else it + key }
    }

    /**
     * Expands every series and season group present in the current view. The
     * season keys can't be read off the current rows — a collapsed series
     * doesn't emit its season headers — so they are derived straight from the
     * structural list using the exact key shape the rows engine emits
     * (`"<seriesKey>/season/<seasonKey>"`). Extra keys for single-season
     * groups are inert (no rendered row carries them).
     */
    fun expandAll() {
        val current = rows.value ?: return
        val seriesKeys = current.mapNotNull { row ->
            (row as? DownloadListRow.SeriesHeader)?.key
        }
        if (seriesKeys.isEmpty()) return
        val seasonKeys = HashSet<String>()
        structuralItems.value.forEach { item ->
            if (item.mediaType != com.raulshma.jellyplay.core.model.MediaType.EPISODE) return@forEach
            val position = item.episodePosition
            val seriesKey = position.seriesKey ?: return@forEach
            seasonKeys += seasonRowKey(seriesKey, position.seasonKey)
        }
        _expandedKeys.value = seriesKeys.toSet() + seasonKeys
    }

    /** Collapses every group. */
    fun collapseAll() {
        _expandedKeys.value = emptySet()
    }

    /**
     * The rendered rows — series/season headers interleaved with the flat
     * (non-episode) rows — built by the pure [downloadListRows] from the same
     * structural projection [uiState] consumes, so status flips re-derive the
     * grouping but progress ticks don't. Computation hops off Main, mirroring
     * OfflineLibraryViewModel's filter/sort pipeline.
     *
     * Null until the first derivation lands: the combine runs off Main, so on
     * screen entry there is a window where [uiState] already carries downloads
     * but no rows exist yet. The screen must treat null as "not computed" and
     * render neither the list nor the category-empty state — an initial
     * emptyList would flash "nothing in this category" while downloads exist.
     */
    internal val rows: StateFlow<List<DownloadListRow>?> =
        combine(structuralItems, _filter, _sort, _expandedKeys) { items, filter, sort, expanded ->
            withContext(Dispatchers.Default) {
                downloadListRows(items, filter, sort, expanded)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        launch {
            // Seed the persisted list state once, from the first DataStore
            // read. A slot the user already touched during that read window
            // keeps their write — the seed only fills slots still holding the
            // feature default (an explicit default pick inside the window is
            // a same-value write). An empty stored string decodes to the
            // feature default via the fromName fallbacks.
            val slice = downloadsStore.downloads.first()
            if (_filter.value == DownloadListFilter.ALL) {
                _filter.value = DownloadListFilter.fromName(slice.downloadsListFilter)
            }
            if (_sort.value == DownloadListSort.RECENT) {
                _sort.value = DownloadListSort.fromName(slice.downloadsListSort)
            }
        }
        launch {
            // Change-filtering lives in two layers. The repository's filter
            // (id order + per-item bytes/status) still forwards byte movement
            // because other consumers render live progress from the item list
            // itself (album detail's per-track bars). Bytes/speed arrive
            // through [progressById] here, so a second
            // projection drops the per-tick fields and only list-structure
            // changes (ids in order, per-item status) re-emit uiState.
            queue.getAllDownloads()
                .catch { e ->
                    _uiState.update {
                        it.copy(error = UserErrorMessages.resolve(e, "Failed to load downloads"), isLoading = false)
                    }
                }
                .distinctUntilChanged { old, new -> sameListStructure(old, new) }
                .collectLatest { items ->
                    structuralItems.value = items
                    _uiState.update {
                        it.copy(
                            downloads = items,
                            error = null,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    /**
     * True when two lists differ only in per-tick progress fields — i.e. the
     * screen has nothing structural to re-render. Every real transition
     * (queue admission, pause/resume, completion, failure, insert/delete)
     * changes some row's status or the id sequence, so id order + status is
     * the complete structural key.
     */
    private fun sameListStructure(old: List<DownloadItem>, new: List<DownloadItem>): Boolean {
        if (old.size != new.size) return false
        for (i in old.indices) {
            val o = old[i]
            val n = new[i]
            if (o.id != n.id || o.status != n.status) return false
        }
        return true
    }

    /**
     * The single dispatch for every [DownloadBulkAction]. Target selection is
     * the pure [DownloadActions] fold (scope ∩ live list ∩ admission table);
     * a no-target call is a guarded no-op. RESUME and RETRY_FAILED are the
     * queue's composite verbs (status flip + enqueue inside — see
     * [DownloadQueue.resumeDownload]); the VM dispatches one verb per target
     * rather than pairing the enqueue follow-up itself. DELETE additionally
     * clears the selection (bulk scope only) and emits the one-shot Deleted
     * message at every scope.
     */
    fun applyBulkAction(action: DownloadBulkAction, scope: DownloadActionScope) {
        val state = _uiState.value
        val targets = DownloadActions.targets(action, state.downloads, state.selectedIds, scope)
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) { item ->
                when (action) {
                    DownloadBulkAction.PAUSE -> queue.pauseDownload(item.id)
                    DownloadBulkAction.RESUME -> queue.resumeDownload(item.id)
                    DownloadBulkAction.CANCEL -> queue.cancelDownload(item.id)
                    DownloadBulkAction.RETRY_FAILED -> queue.retryDownload(item.id)
                    DownloadBulkAction.DELETE -> queue.deleteDownload(item.id)
                }
            }
            if (action == DownloadBulkAction.DELETE) {
                if (scope == DownloadActionScope.Selected) clearSelection()
                // Only claim success where deletions are real — a
                // fabricated "Deleted" toast would lie.
                if (queue.isSupported) messageChannel.trySend(DownloadsUserMessage.Deleted)
            }
        }
    }

    fun deleteDownload(item: DownloadItem) {
        launch {
            queue.deleteDownload(item.id)
            if (queue.isSupported) messageChannel.trySend(DownloadsUserMessage.Deleted)
        }
    }

    fun moveToFront(item: DownloadItem) {
        launch {
            val maxPriority = _uiState.value.downloads.maxOfOrNull { it.priority } ?: 0
            queue.setDownloadPriority(item.id, maxPriority + 1)
        }
    }

    fun lowerPriority(item: DownloadItem) {
        launch {
            val minPriority = _uiState.value.downloads.minOfOrNull { it.priority } ?: 0
            queue.setDownloadPriority(item.id, minPriority - 1)
        }
    }

    // ── Selection ────────────────────────────────────────────────────────

    fun toggleSelection(item: DownloadItem) {
        _uiState.update { it.copy(selection = it.selection.toggled(item.id)) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = it.selection.cleared()) }
    }

    /**
     * Selects every download matching the ACTIVE filter — a collapsed group's
     * episodes count too (they're part of the Series view), while items hidden
     * by the Movies/Series filter don't.
     */
    fun selectAll() {
        val activeFilter = _filter.value
        _uiState.update { state ->
            val candidates = state.downloads.filter(activeFilter::accepts).map { it.id }
            state.copy(selection = state.selection.selectAll(candidates))
        }
    }

    /**
     * Tri-state group write behind the series/season headers (gh-175): a
     * fully selected group deselects, anything else selects the whole group.
     */
    fun toggleGroupSelection(groupIds: Collection<String>) {
        if (groupIds.isEmpty()) return
        _uiState.update { state ->
            state.copy(selection = SelectionState(toggledGroupSelection(state.selectedIds, groupIds)))
        }
    }

    /**
     * The download a group header's Play action targets, resolved from the
     * header's playback-ordered episode ids: the first unplayed COMPLETED
     * episode wins, falling back to the first COMPLETED episode of the group;
     * a group with nothing finished yields null (the click is a no-op — the
     * same [DownloadActions.isPlayable] gate the row-level Play applies, see
     * [groupPlayTarget]). Played state reads the offline DB once, normalized
     * through the shared [toMediaItem] projection — the same ≥95% watched rule
     * the offline cards use — so a nearly-finished episode counts as watched
     * instead of hijacking Play.
     */
    suspend fun resolveGroupPlayTarget(episodeIds: List<String>): DownloadItem? {
        if (episodeIds.isEmpty()) return null
        val byId = _uiState.value.downloads.associateBy { it.id }
        val ordered = episodeIds.mapNotNull(byId::get)
        if (ordered.isEmpty()) return null
        val played = offlineRepository.getOfflineEpisodes()
            .first()
            .filter { it.toMediaItem().isPlayed }
            .mapTo(HashSet()) { it.id }
        return groupPlayTarget(ordered, played)
    }

    // ── Bulk actions ─────────────────────────────────────────────────────
    // Every selected/global verb folds into [applyBulkAction] above;
    // [DownloadActions] owns the shared admission table.

    /**
     * Runs [action] for every target concurrently instead of serially — each
     * repository call bundles WorkManager ops + file cleanup + a DB
     * transaction, so a 50-item selection would pay 50 sequential
     * round-trips. Same shape as OfflineRepositoryImpl.deleteArtifactsParallel.
     */
    private suspend inline fun <T> bulkMap(
        targets: List<T>,
        crossinline action: suspend (T) -> Unit,
    ) {
        coroutineScope {
            targets.map { item -> async { action(item) } }.awaitAll()
        }
    }

    // ── Freshness check / resync ────────────────────────────────────────

    /**
     * Checks every downloaded item for available updates (TTL-gated per item).
     * Safe to call repeatedly — items within their TTL are skipped. Sets
     * [checking] while the batch runs so the appbar icon can spin.
     */
    fun checkAllForUpdates() {
        if (_checking.value) return
        launch {
            _checking.value = true
            try {
                val ids = offlineRepository.getDownloadedItemIds()
                resync.checkForUpdatesBatch(ids)
            } finally {
                _checking.value = false
            }
        }
    }

    /**
     * Resyncs a single item's metadata/images. Progress flows through
     * [resyncProgress]; the item's update flag clears once its baseline refreshes.
     */
    fun resyncOne(itemId: String) {
        resync.resyncBatch(listOf(itemId))
    }

    /**
     * Resyncs every item currently flagged for an update. Sequential to keep
     * bandwidth predictable and avoid competing with active downloads. Resolves
     * the flagged ids from the repository so it works even if the sheet hasn't
     * collected [updateRows] yet.
     */
    fun resyncAll() {
        launch {
            val flagged = offlineRepository.getItemsWithUpdates().first()
            if (flagged.isNotEmpty()) resync.resyncBatch(flagged.map { it.id })
        }
    }

    /** Resyncs an explicit set of item ids (used by the sheet's per-item action). */
    fun resyncAll(itemIds: List<String>) {
        if (itemIds.isNotEmpty()) resync.resyncBatch(itemIds)
    }

    /** Clears batch progress once the resync sheet is dismissed. */
    fun clearResyncProgress() {
        resync.clearBatchProgress()
    }

    /**
     * Force-resyncs an explicit set of completed items, refreshing only the
     * data categories in [options]. Unlike [resyncAll], this is user-directed
     * (any completed item, not just flagged ones) and partial (skipped
     * categories retain their baseline). Progress flows through [resyncProgress].
     */
    fun forceResync(itemIds: List<String>, options: ResyncOptions) {
        if (itemIds.isEmpty() || options.isEmpty) return
        resync.resyncBatch(itemIds, options)
    }

    /**
     * Download statuses eligible for a force resync. COMPLETED items carry the
     * full artifact set; FAILED and PAUSED items still have their offline
     * metadata row (seeded during intake) plus whatever sidecars landed before
     * the stall, so refreshing them from the server is valid. Actively
     * transferring states (PENDING/QUEUED/DOWNLOADING) are excluded — the
     * download worker itself lands fresh sidecars there, and a concurrent
     * resync would interleave with its writes. CANCELLED never lingers: cancel
     * deletes the row and its files.
     */
    private val forceResyncEligibleStatuses = setOf(
        DownloadStatus.COMPLETED,
        DownloadStatus.FAILED,
        DownloadStatus.PAUSED,
    )

    /**
     * Completed/stalled downloads available for a force resync, deduplicated by
     * media item id. Resolved from an uncapped one-shot DB read (not the UI
     * list's 500-row reactive window) so every downloaded media item is
     * offered, and so the picker is correct even when opened before the list
     * flow has emitted. Drives the item picker in the force-resync sheet.
     * Carries the episode context (series name + SxxExx) so episodes are
     * identifiable in the picker, mirroring the context line on the downloads
     * list.
     */
    suspend fun forceResyncCandidates(): List<ForceResyncCandidate> =
        queue.getAllDownloadsSnapshot()
            .filter { it.status in forceResyncEligibleStatuses }
            .distinctBy { it.mediaItemId }
            .map {
                ForceResyncCandidate(
                    id = it.mediaItemId,
                    name = it.name,
                    mediaType = it.mediaType,
                    seriesName = it.seriesName,
                    seasonNumber = it.seasonNumber,
                    episodeNumber = it.episodeNumber,
                )
            }
}
