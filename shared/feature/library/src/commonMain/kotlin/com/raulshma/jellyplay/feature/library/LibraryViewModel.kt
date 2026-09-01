package com.raulshma.jellyplay.feature.library

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryBrowserReducer
import com.raulshma.jellyplay.core.model.LibraryBrowserState
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.toFilteredLibraryItems
import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.data_download_start_failed
import com.raulshma.jellyplay.feature.library.generated.resources.data_download_started
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.raulshma.jellyplay.core.data.util.FilterCodec
import com.raulshma.jellyplay.core.data.util.loadListWithRetry

/** Projected slice of [UserPreferences] used to derive the active library view mode. */
private data class ViewModePrefs(
    val libraryViewMode: LibraryViewMode,
    val libraryViewModes: Map<String, String>,
)

/**
 * Identity of the paged query: folder, filters, offline mode, refresh trigger.
 * Deduped before the pager is built so browser-state re-emissions that leave
 * the query untouched (view mode, poster size, grouping) don't tear it down,
 * while [LibraryViewModel.refresh] always does — the trigger is part of the key.
 */
private data class PagedQueryKey(
    val folder: LibraryFolder?,
    val filters: LibraryFilters,
    val servingOffline: Boolean,
    val refreshTrigger: Int,
)

class LibraryViewModel(
    private val mediaRepository: MediaRepository,
    private val offlineRepository: OfflineRepository,
    private val mediaDownloadActions: MediaDownloadActions,
    private val offlineModeManager: OfflineModeManager,
    private val userMessageBus: UserMessageBus,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoFolderPrefetcher: PhotoFolderPrefetcher,
    private val libraryStore: com.raulshma.jellyplay.core.datastore.library.LibraryStore,
) : JellyPlayViewModel() {

    // ---- Browser state: one value type owning {folder, filters, viewMode, ----
    // ---- groupBy, posterSize, sectionContext, title} as a consistent unit. --
    // Previously this was a swarm of 8+ separate StateFlows whose invariants
    // (view-mode precedence, "don't persist synthetic section folders") were
    // enforced by hand-scattered guards. The reducer is now the single source.
    private val _browserState = stateFlow(LibraryBrowserState())
    val browserState = _browserState.flow

    // ---- Non-browser flows that genuinely stay separate ----
    private val _folders = stateFlow<List<LibraryFolder>>(emptyList())
    val folders = _folders.flow

    private val _isLoading = stateFlow(true)
    val isLoading = _isLoading.flow

    private val _error = stateFlow<String?>(null)
    val error = _error.flow

    private val _genres = stateFlow<List<Genre>>(emptyList())
    val genres = _genres.flow

    private val _tags = stateFlow<List<String>>(emptyList())
    val tags = _tags.flow

    private val _showFilters = stateFlow(false)
    val showFilters = _showFilters.flow

    /**
     * Ids whose quick actions offer "Remove download" instead of "Download":
     * completed downloads ∪ series ids (a series card flips once any episode
     * of it is downloaded). Re-exposes the shared quick-action delegate's
     * Eagerly-started flow — one collector serves every host surface.
     */
    val downloadedIds = mediaDownloadActions.downloadedIds

    private val _photoFolderChildUrls = stateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls = _photoFolderChildUrls.flow

    /**
     * Whether the reset-all confirmation dialog is currently visible. Mirrors
     * [_confirmResetEnabled]: the dialog only ever appears while the user opted
     * to keep confirmations on.
     */
    private val _resetDialogVisible = stateFlow(false)
    val resetDialogVisible = _resetDialogVisible.flow

    /**
     * Whether the reset-all confirmation is enabled. Persisted via
     * [com.raulshma.jellyplay.core.datastore.library.LibraryStore] so a
     * "Don't show again" choice survives navigation and restarts.
     */
    private val _confirmResetEnabled = stateFlow(true)

    /**
     * True once the user has taken an explicit [setViewMode] action this session.
     * While set, the [loadViewMode] async collector refuses to overwrite the
     * browser state's viewMode — closing the race where the async store write
     * re-emits the stale persisted value and snaps the grid back moments after a
     * tap. Reset on folder/section change so the new folder loads its own mode.
     *
     * One flag with one meaning, replacing the old `_userViewModeOverride`
     * StateFlow (which conflated "the user touched it" with "here is the value").
     */
    private var userTouchedViewMode: Boolean = false

    /**
     * Per-item slice of [photoFolderChildUrls]. Lets each photo-folder card
     * collect only its own urls so a prefetch merge (which produces a new Map
     * reference) doesn't invalidate the entire [LibraryScreen] — only the one
     * card whose urls changed.
     */
    fun photoFolderChildUrlsFor(itemId: String): kotlinx.coroutines.flow.Flow<List<String>> =
        _photoFolderChildUrls.flow
            .map { it[itemId].orEmpty() }
            .distinctUntilChanged()

    /**
     * Marks the item played/unplayed. Intentionally silent (the mutator's
     * default): the paged grid is left untouched so the user keeps their scroll
     * position — the badge updates on the next natural data refresh.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(item.id, played)
        }
    }

    /**
     * Long-press Download from a browse card. Single-stream items
     * (movie/episode/music track) start inline at the default quality; series
     * route to the detail screen with the download sheet pre-presented via
     * [onOpenDetail] (their flow needs the user's season/episode selection),
     * and other non-inline types (season, album, ...) open the detail screen
     * plainly. Failures surface on the message bus.
     */
    fun downloadItem(item: MediaItem, onOpenDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit) {
        launch {
            when (val result = mediaDownloadActions.download(item)) {
                DownloadRequestResult.Started ->
                    userMessageBus.info(
                        UiText.Resource(Res.string.data_download_started)
                    )
                is DownloadRequestResult.SeriesSelectionRequired -> onOpenDetail(result.seriesId, true)
                is DownloadRequestResult.NeedsDetailScreen -> onOpenDetail(result.itemId, false)
                is DownloadRequestResult.Failed ->
                    userMessageBus.error(
                        UiText.Resource(Res.string.data_download_start_failed)
                    )
            }
        }
    }

    /**
     * Long-press Remove download — deletes the local download (artifacts +
     * offline rows) via the shared routing: a series card removes the whole
     * series download, anything else the single item. Never touches the server.
     */
    fun removeItemDownload(item: MediaItem) {
        mediaDownloadActions.removeDownload(item)
    }

    private val _refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * True while the app is offline (manual toggle or auto network loss): the
     * grid auto-switches to the downloaded-only local source (#147) and the
     * Downloaded chip renders pinned on — the library's share of the "auto
     * filter for downloaded stuff" the offline home already applies. The
     * user's real filters are never mutated, so going back online restores
     * them untouched.
     */
    val offlineAutoFilter: kotlinx.coroutines.flow.StateFlow<Boolean> = stateIn(
        initial = false,
        flow = offlineModeManager.offlineMode.map { it != OfflineMode.ONLINE },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(
        _browserState.flow,
        _refreshTrigger,
        offlineModeManager.offlineMode,
    ) { browser, refreshTrigger, mode ->
        PagedQueryKey(browser.folder, browser.filters, mode != OfflineMode.ONLINE, refreshTrigger)
    }.distinctUntilChanged().flatMapLatest { (folder, filters, servingOffline) ->
        if (filters.isDownloaded == true || servingOffline) {
            // "Downloaded" filter — or offline mode, which pins it on
            // automatically (#147): serve the grid from the local offline
            // store — instant, no server paging, and the same projection
            // offline playback uses. Folder membership matches the offline
            // row's parentId; with no folder selected ("All") the whole
            // offline library is shown. Filter dimensions and sort are
            // re-applied client-side over the stored fields (see
            // toFilteredLibraryItems).
            val items = if (folder != null) {
                offlineRepository.getOfflineLibraryInFolder(folder.id)
            } else {
                offlineRepository.getOfflineLibrary()
            }
            // Static paging only dispatches load states when explicit source
            // states are provided — without them a fresh LazyPagingItems keeps
            // its initial refresh = Loading forever, leaving the pull-to-refresh
            // spinner stuck on while offline content renders fine.
            val idleStates = LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false),
            )
            items.map { PagingData.from(it.toFilteredLibraryItems(filters), idleStates) }
        } else {
            mediaRepository.getMediaItemsPaged(
                parentId = folder?.id,
                filters = filters,
                // Section mode ("See All" from a home Latest row) shows the same
                // top-level items as the default library tab — series for a TV library,
                // movies for a movie library — just sorted by latest. (Previously this
                // returned leaf episodes for a TV library, which stacked flat episode
                // blocks; issue #113.) Filtering to a specific leaf type is still
                // possible via the Media Type filter.
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        }
    }
    .cachedIn(scope)

    init {
        loadFolders()
        loadGenres()
        loadTags()
        loadViewMode()
        loadLayoutPrefs()
        loadResetConfirmPref()
    }

    /**
     * Section-mode entry point: called once from [LibraryScreen] when opened via
     * [Route.LibrarySection]. Scopes the paged query to the section's library,
     * pre-applies the section's sort / media-type filter, hides the folder chips
     * (the section is already scoped), and skips the folder fetch.
     */
    fun configureSection(ctx: LibrarySectionContext) {
        if (_browserState.value.sectionContext == ctx) return
        userTouchedViewMode = false
        _browserState.set(LibraryBrowserReducer.configureSection(_browserState.value, ctx))
    }

    /**
     * Resets all section-mode state so the Library tab renders its default
     * browsing view. Called when the tab entry ([Route.Library]) is shown after a
     * section deep-link, because the VM is shared across both entries (see the
     * note on [browserState]). Idempotent: a no-op when not in section mode,
     * so repeated recompositions are safe.
     */
    fun clearSectionMode() {
        userTouchedViewMode = false
        _browserState.set(LibraryBrowserReducer.clearSectionMode(_browserState.value))
    }

    private fun loadLayoutPrefs() {
        launch {
            libraryStore.library
                .map { it.libraryPosterSize to it.libraryGroupBy }
                .distinctUntilChanged()
                .collect { (posterSize, groupBy) ->
                    _browserState.set(_browserState.value.copy(posterSize = posterSize, groupBy = groupBy))
                }
        }
    }

    private fun loadResetConfirmPref() {
        launch {
            libraryStore.library
                .map { it.confirmLibraryReset }
                .distinctUntilChanged()
                .collect { _confirmResetEnabled.set(it) }
        }
    }

    private fun loadViewMode() {
        // Re-derive the view mode whenever the persisted view-mode prefs or the
        // selected folder change, via the reducer's single precedence rule:
        // per-folder override > collectionType default (defaultViewMode) > global.
        //
        // This makes the layout server-driven by default — a music library shows
        // as a list, a movies library as a poster grid — while still letting the
        // user override per-folder via the toolbar toggle.
        launch {
            combine(
                libraryStore.library
                    .map { ViewModePrefs(it.libraryViewMode, it.libraryViewModes) }
                    .distinctUntilChanged(),
                _browserState.flow.map { it.folder }.distinctUntilChanged(),
            ) { viewModePrefs, folder ->
                val perLibrary = folder?.id?.let { id ->
                    viewModePrefs.libraryViewModes[id]?.let { modeName ->
                        runCatching { LibraryViewMode.valueOf(modeName) }.getOrNull()
                    }
                }
                LibraryBrowserReducer.resolveViewMode(
                    current = _browserState.value,
                    perFolderOverride = perLibrary,
                    globalDefault = viewModePrefs.libraryViewMode,
                )
            }.collect { mode ->
                // Don't clobber an explicit user choice. A view-mode tap writes
                // the store asynchronously; that store re-emission lands here and
                // would otherwise snap the grid back to the stale/derived value
                // (the "changes then switches back" bug). The flag is cleared on
                // folder/section change so each folder still loads its saved mode.
                if (!userTouchedViewMode) {
                    _browserState.set(_browserState.value.copy(viewMode = mode))
                }
            }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        userTouchedViewMode = true
        _browserState.set(LibraryBrowserReducer.setViewMode(_browserState.value, mode))
        launch {
            libraryStore.setLibraryViewMode(mode)
            val state = _browserState.value
            // Only persist a per-folder override for a real folder — a section's
            // synthetic parentId must never be written as a library key.
            if (LibraryBrowserReducer.shouldPersistPerFolder(state)) {
                libraryStore.setLibraryViewMode(state.folder!!.id, mode.name)
            }
        }
    }

    fun setPosterSize(size: Float) {
        _browserState.set(LibraryBrowserReducer.setPosterSize(_browserState.value, size))
    }

    fun persistPosterSize() {
        launch { libraryStore.setLibraryPosterSize(_browserState.value.posterSize) }
    }

    fun setGroupBy(groupBy: GroupBy) {
        _browserState.set(LibraryBrowserReducer.setGroupBy(_browserState.value, groupBy))
        launch { libraryStore.setLibraryGroupBy(groupBy) }
    }

    private fun loadFolders(force: Boolean = false) {
        launch {
            _isLoading.set(true)
            mediaRepository.getLibraryFolders(force = force)
                .onSuccess { folders ->
                    _folders.set(folders)
                    // Clear any stale error once folders load successfully.
                    _error.set(null)
                }
                .onFailure { error ->
                    // Don't blank the whole library: keep any previously-loaded
                    // folders so the user can still browse, and surface the error
                    // as a non-blocking status (the screen shows ErrorScreen only
                    // when there are also zero items). This stops a single failed
                    // fetch — e.g. a transient 403 — from making the app unusable

                    if (_folders.value.isNullOrEmpty()) {
                        _error.set(error.message ?: "${error::class.simpleName}")
                    }
                }
            _isLoading.set(false)
        }
    }

    private fun loadGenres(force: Boolean = false) {
        launch {
            // Retry once after a short delay so a transient network blip doesn't
            // leave the filter sheet permanently missing its Genres section.
            loadListWithRetry({ mediaRepository.getGenres(force = force) }) { _genres.set(it) }
        }
    }

    private fun loadTags() {
        launch {
            loadListWithRetry(mediaRepository::getTags) { _tags.set(it) }
        }
    }

    fun selectFolder(folder: LibraryFolder?) {
        userTouchedViewMode = false
        val prefs = libraryStore.library.value
        // Decode saved filters/sort for the folder (or fall back to defaults).
        var newFilters = LibraryFilters()
        if (folder != null) {
            val savedOrder = prefs.defaultLibrarySortOrders[folder.id]
            val savedFiltersJson = prefs.libraryFilters[folder.id]
            if (savedFiltersJson != null) {
                try {
                    newFilters = FilterCodec.decodeFromString<LibraryFilters>(savedFiltersJson)
                } catch (_: Exception) {
                    newFilters = LibraryFilters()
                }
            } else if (savedOrder != null) {
                val option = SortOption.entries.find { it.name == savedOrder || it.apiValue == savedOrder } ?: SortOption.YEAR_DESC
                newFilters = LibraryFilters(sortBy = option)
            }
        }
        // Single view-mode precedence rule via the reducer — fixes the old
        // divergence where this sync path omitted the collectionType default.
        val perFolderOverride = folder?.id?.let { id ->
            prefs.libraryViewModes[id]?.let { modeName ->
                runCatching { LibraryViewMode.valueOf(modeName) }.getOrNull()
            }
        }
        _browserState.set(
            LibraryBrowserReducer.selectFolder(
                current = _browserState.value,
                folder = folder,
                filters = newFilters,
                perFolderOverride = perFolderOverride,
                globalDefault = prefs.libraryViewMode,
            )
        )
    }

    fun updateFilters(newFilters: LibraryFilters) {
        _browserState.set(LibraryBrowserReducer.updateFilters(_browserState.value, newFilters))
        // Skip persistence for a synthetic section folder: its id is a section
        // parentId and persisting there would leak section state into the user's
        // real per-library filter overrides. The reducer owns that gate.
        val state = _browserState.value
        if (LibraryBrowserReducer.shouldPersistPerFolder(state)) {
            val folderId = state.folder!!.id
            launch {
                libraryStore.setDefaultLibrarySortOrder(folderId, newFilters.sortBy.name)
                libraryStore.setLibraryFilters(folderId, FilterCodec.encodeToString(newFilters))
            }
        }
    }

    fun toggleShowFilters() {
        _showFilters.set(!_showFilters.value)
    }

    fun shuffleLibrary() {
        // Shuffle is a transient action: apply RANDOM sort in-memory only so we
        // don't overwrite the folder's saved default sort order (which is what
        // a regular filter change via updateFilters persists). On the next visit
        // the user's chosen sort (e.g. Recently Added) is restored as expected.
        _browserState.set(_browserState.value.copy(filters = _browserState.value.filters.copy(sortBy = SortOption.RANDOM)))
    }

    fun clearFilters() {
        _browserState.set(LibraryBrowserReducer.updateFilters(_browserState.value, LibraryFilters()))
    }

    /**
     * Entry point for the top-bar Reset pill. Shows the confirmation dialog while
     * the user hasn't opted out; otherwise resets immediately so the pill stays a
     * one-tap action for users who dismissed the confirmation.
     */
    fun onResetClick() {
        if (_confirmResetEnabled.value) {
            _resetDialogVisible.set(true)
        } else {
            resetToDefault()
        }
    }

    /** Dismisses the reset confirmation without resetting anything. */
    fun dismissResetDialog() {
        _resetDialogVisible.set(false)
    }

    /**
     * Confirmed reset-all. Optionally persists "don't show again" so future
     * reset taps skip the dialog and reset immediately.
     */
    fun confirmResetAll(dontShowAgain: Boolean) {
        _resetDialogVisible.set(false)
        if (dontShowAgain) {
            launch {
                libraryStore.setConfirmLibraryReset(false)
                _confirmResetEnabled.set(false)
            }
        }
        resetToDefault()
    }

    /**
     * Resets the whole library screen to defaults: clears all filters, returns to
     * the "All" folder chip, restores the default view mode, resets poster size
     * and grouping, and persists the reset so it survives navigation. Any stale
     * per-folder saved filters for the currently selected folder are overwritten
     * with the defaults so re-selecting the folder shows a clean slate.
     *
     * In section mode ("See All" deep-link) the Reset pill is hidden from the
     * UI, but this method stays defensive: the reducer tears down the section
     * context so the synthetic folder/title can't linger, and the returned
     * `realFolderIdToClean` (captured *before* the clear, not the post-clear
     * state) gates per-folder persistence — the section's parentId must never be
     * written as a real library's saved filters.
     */
    fun resetToDefault() {
        userTouchedViewMode = false
        val globalDefault = libraryStore.library.value.libraryViewMode
        val result = LibraryBrowserReducer.resetToDefault(_browserState.value, globalDefault)
        _browserState.set(result.state)
        launch {
            result.realFolderIdToClean?.let { folderId ->
                libraryStore.setLibraryFilters(folderId, FilterCodec.encodeToString(LibraryFilters()))
                libraryStore.setDefaultLibrarySortOrder(folderId, SortOption.YEAR_DESC.name)
            }
            libraryStore.setLibraryPosterSize(result.state.posterSize)
            libraryStore.setLibraryGroupBy(result.state.groupBy)
        }
    }

    fun refresh() {
        launch {
            // Manual refresh bypasses the caches for the queries this screen
            // shows (folders + genres); tags are an uncached passthrough.
            loadFolders(force = true)
            loadGenres(force = true)
            loadTags()
            // Increment the trigger to force flatMapLatest to create a new Pager,
            // which avoids the duplicate-key crash that occurs when pagedItems.refresh()
            // is called concurrently on a cachedIn flow.
            _refreshTrigger.value++
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) {
        launch {
            val current = _photoFolderChildUrls.value
            val results = photoFolderPrefetcher.prefetch(items, alreadyFetched = current.keys)
            if (results.isNotEmpty()) {
                _photoFolderChildUrls.set(_photoFolderChildUrls.value + results)
            }
        }
    }
}
