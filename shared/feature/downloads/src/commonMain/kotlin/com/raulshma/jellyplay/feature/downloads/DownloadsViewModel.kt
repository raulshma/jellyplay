package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import com.raulshma.jellyplay.core.model.DownloadItem
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

class DownloadsViewModel(
    private val queue: DownloadQueue,
    private val offlineRepository: OfflineRepository,
    private val resync: OfflineResync,
    private val downloadsStore: DownloadsStore,
    /** The gh-181 Ask-mode suggestion surface (observe/confirm/dismiss). */
    private val policyStore: SeriesDownloadPolicyStore,
    /** The confirm path: confirmed prompts enqueue through the normal series intake (no policy write). */
    private val intake: DownloadIntake,
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
     * Pending Cancel All (the appbar action — gh-178). Cancel is destructive
     * (in-flight workers stop, partial files are deleted) and this one sweep
     * touches every active/paused row at once, so unlike the per-row cancel it
     * confirms first. Same synchronous settle arm as the delete hosts.
     */
    /**
     * The Cancel All arm (gh-178): the payload is the status filter's visible
     * id set — the sweep the dialog confirms is exactly the list the user is
     * looking at, never hidden rows.
     */
    val pendingCancelAll = ConfirmationHost<List<String>>()

    /**
     * One-shot delete feedback, screen-forward seam. Buffered, single
     * collector, never replayed — [DownloadsScreen] resolves the
     * resource text and posts it to the shared UserMessageBus.
     */
    private val messageChannel = Channel<DownloadsUserMessage>(Channel.BUFFERED)
    val messages: Flow<DownloadsUserMessage> = messageChannel.receiveAsFlow()

    // ── Resync / freshness (DownloadsResyncActions) ──────────────────────

    /**
     * The resync/freshness family — the reactive update surface, the
     * TTL-gated check-for-updates sweep, the batch resync verbs, the force
     * resync eligibility policy and the candidate fold — behind one
     * constructed module ([DownloadsResyncActions], built from the
     * queue/repository/resync constructor deps this VM already owns). The
     * holder is a read/refresh family; the POST-action UI effects (the
     * messages fold, selection clears) stay VM-side per the DownloadActions
     * recorded rule.
     */
    internal val resyncActions = DownloadsResyncActions(
        scope = scope,
        offlineRepository = offlineRepository,
        resync = resync,
        queue = queue,
    )

    /**
     * The pending Ask-mode prompts (gh-181), newest first — the suggestion
     * card's content. Empty when the user has nothing to answer.
     */
    val suggestions: StateFlow<List<AutoDownloadSuggestion>> =
        policyStore.observeAutoDownloadSuggestions()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Confirms suggestions: groups the prompts by series → season (episodes
     * in numeric order), enqueues each season's batch through the normal
     * series download path, then clears the answered rows. [episodeIds] is
     * the confirmed selection — a per-row Download passes one id, the card's
     * Download all passes every id. A failed batch is surfaced (Raw message)
     * instead of silently reporting success; its prompts stay pending so the
     * user can retry.
     */
    fun downloadSuggestions(episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        launch {
            val rows = policyStore.autoDownloadSuggestionsByIds(episodeIds)
            if (rows.isEmpty()) return@launch
            val confirmed = mutableListOf<AutoDownloadSuggestion>()
            rows.groupBy { it.seriesId }
                .forEach { (_, seriesEpisodes) ->
                    seriesEpisodes.groupBy { it.seasonId }
                        .forEach { (seasonId, seasonEpisodes) ->
                            val result = intake.startSeries(
                                seriesId = seriesEpisodes.first().seriesId,
                                episodeIds = mapOf(
                                    seasonId to seasonEpisodes
                                        .sortedBy { it.episodeNumber }
                                        .map { it.episodeId },
                                ),
                            )
                            if (result.isSuccess) {
                                confirmed += seasonEpisodes
                            } else {
                                messageChannel.trySend(
                                    DownloadsUserMessage.Raw(
                                        result.exceptionOrNull()?.message ?: "Download failed",
                                    ),
                                )
                            }
                        }
                }
            if (confirmed.isNotEmpty()) {
                policyStore.dismissAutoDownloadSuggestions(confirmed.map { it.episodeId })
                messageChannel.trySend(DownloadsUserMessage.SuggestionsQueued)
            }
        }
    }

    /** Dismisses suggestions without downloading. */
    fun dismissSuggestions(episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        launch { policyStore.dismissAutoDownloadSuggestions(episodeIds) }
    }

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
     * to the latest map. The scan accumulator is [retainedDownloadProgress]
     * — the exit-transition retention rule (one-frame retain-then-forget)
     * lives there.
     */
    val progressById: StateFlow<Map<String, DownloadProgress>> =
        queue.getActiveDownloadProgress()
            .combine(structuralItems) { live, items -> live to items }
            .scan(emptyMap<String, DownloadProgress>()) { retained, (live, structural) ->
                retainedDownloadProgress(retained, live, structural)
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

    /**
     * Status filter for the downloads list — All / Active / Completed / Failed
     * (gh-178). Persisted through [DownloadsStore] so the choice survives app
     * restarts.
     */
    private val _statusFilter = MutableStateFlow(DownloadStatusFilter.ALL)
    val statusFilter: StateFlow<DownloadStatusFilter> = _statusFilter.asStateFlow()

    /**
     * Whether the long-press selection hint was already dismissed once — the
     * banner renders only while this is false and no selection is active.
     * Flips (and persists) the first time a selection becomes active.
     */
    private val _selectionHintSeen = MutableStateFlow(false)
    val selectionHintSeen: StateFlow<Boolean> = _selectionHintSeen.asStateFlow()

    fun setFilter(value: DownloadListFilter) {
        _filter.value = value
        launch { downloadsStore.setDownloadsListFilter(value.name) }
    }

    fun setSort(value: DownloadListSort) {
        _sort.value = value
        launch { downloadsStore.setDownloadsListSort(value.name) }
    }

    fun setStatusFilter(value: DownloadStatusFilter) {
        _statusFilter.value = value
        launch { downloadsStore.setDownloadsListStatusFilter(value.name) }
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
        combine(structuralItems, _filter, _statusFilter, _sort, _expandedKeys) { items, filter, statusFilter, sort, expanded ->
            withContext(Dispatchers.Default) {
                val filtered = items.filter { statusFilter.accepts(it) }
                downloadListRows(filtered, filter, sort, expanded, resolveSeriesArt(filtered))
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The locally-saved series posters for [items]' series groups — one-shot
     * offline-DB reads keyed by raw series id (the [downloadListRows] lookup
     * shape). A series without saved artwork is simply absent, and the group
     * header falls back to an episode still inside the grouping. Runs on every
     * rows rebuild; a handful of indexed PK lookups off Main per structural
     * change.
     */
    private suspend fun resolveSeriesArt(items: List<DownloadItem>): Map<String, SeriesArt> =
        items.mapNotNull { it.seriesId?.takeIf(String::isNotBlank) }
            .distinct()
            .mapNotNull { seriesId ->
                offlineRepository.getOfflineItem(seriesId)
                    ?.takeIf { !it.posterPath.isNullOrBlank() }
                    ?.let { seriesId to SeriesArt(it.posterPath, it.blurHashPrimary) }
            }
            .toMap()

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
            if (_statusFilter.value == DownloadStatusFilter.ALL) {
                _statusFilter.value = DownloadStatusFilter.fromName(slice.downloadsListStatusFilter)
            }
            _selectionHintSeen.value = slice.selectionHintSeen
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
        dispatchBulk(action, targets) {
            if (action == DownloadBulkAction.DELETE) {
                if (scope == DownloadActionScope.Selected) clearSelection()
                // Only claim success where deletions are real — a
                // fabricated "Deleted" toast would lie.
                if (queue.isSupported) messageChannel.trySend(DownloadsUserMessage.Deleted)
            }
        }
    }

    /**
     * Ids-scoped dispatch behind the series/season headers' pause/cancel and
     * the app bar's filter-aware global actions (gh-178): the caller hands its
     * target ids in (a header's episodes, or the status filter's visible set),
     * [DownloadActions.targets] intersects them with the live list and the
     * admission table via the Selected scope (ids ∩ list ∩ admission — no
     * selection mode needed), and the same verb fold as [applyBulkAction]
     * runs. An id set in terminal states dispatches nothing.
     */
    fun applyBulkActionToIds(action: DownloadBulkAction, targetIds: Collection<String>) {
        val state = _uiState.value
        val targets = DownloadActions.targets(action, state.downloads, targetIds.toSet(), DownloadActionScope.Selected)
        if (targets.isEmpty()) return
        dispatchBulk(action, targets)
    }

    /**
     * The one verb fold both dispatches share: [bulkMap] over the targets
     * inside a single launch, [onDone] running after every verb completed
     * (DELETE's selection clear + message ride it to keep their post-delete
     * timing).
     */
    private fun dispatchBulk(action: DownloadBulkAction, targets: List<DownloadItem>, onDone: () -> Unit = {}) {
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
            onDone()
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
        maybeMarkSelectionHintSeen()
    }

    /**
     * First entry into selection mode dismisses the long-press hint forever
     * (persisted) — the affordance is discovered, the banner row reclaimed.
     */
    private fun maybeMarkSelectionHintSeen() {
        if (_selectionHintSeen.value || !_uiState.value.selectionMode) return
        _selectionHintSeen.value = true
        launch { downloadsStore.setSelectionHintSeen(true) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = it.selection.cleared()) }
    }

    /**
     * Selects every download matching the ACTIVE filter — a collapsed group's
     * episodes count too (they're part of the Series view), while items hidden
     * by the Movies/Series or status filter don't.
     */
    fun selectAll() {
        val activeFilter = _filter.value
        val activeStatusFilter = _statusFilter.value
        _uiState.update { state ->
            val candidates = state.downloads
                .filter { activeFilter.accepts(it) && activeStatusFilter.accepts(it) }
                .map { it.id }
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
        maybeMarkSelectionHintSeen()
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
}
