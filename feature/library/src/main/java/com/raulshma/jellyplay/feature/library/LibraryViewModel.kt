package com.raulshma.jellyplay.feature.library

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
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
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Lenient codec for the persisted library-filter blob. `ignoreUnknownKeys`
 * keeps decode forward-compatible when fields are added later; `encodeDefaults`
 * guarantees a complete on-disk snapshot (matching the legacy mirror's output).
 * Note: `ignoreUnknownKeys` does NOT suppress unknown enum constants —
 * [selectFolder] keeps its try/catch as the resilience boundary for those.
 */
private val libraryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Projected slice of [UserPreferences] used to derive the active library view mode. */
private data class ViewModePrefs(
    val libraryViewMode: LibraryViewMode,
    val libraryViewModes: Map<String, String>,
)

/**
 * Delay before a single retry of the genre/tag filter lookups. A transient
 * network blip shouldn't leave the filter sheet permanently missing a section.
 */
private const val FILTER_RETRY_DELAY_MS: Long = 800

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
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
    // Back-compat accessors so the screen/tests can read individual slices without
    // a wider churn; they all derive from the single browser state value.
    val folder = _browserState.flow.map { it.folder }
    val filters = _browserState.flow.map { it.filters }
    val viewMode = _browserState.flow.map { it.viewMode }
    val posterSize = _browserState.flow.map { it.posterSize }
    val groupBy = _browserState.flow.map { it.groupBy }
    val sectionContext = _browserState.flow.map { it.sectionContext }
    val title = _browserState.flow.map { it.title }

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
     * Marks the item played/unplayed on the server. Intentionally silent: the
     * paged grid is left untouched so the user keeps their scroll position —
     * the badge updates on the next natural data refresh.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            if (played) mediaRepository.markPlayed(item.id) else mediaRepository.markUnplayed(item.id)
        }
    }

    private val _refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(
        _browserState.flow,
        _refreshTrigger,
    ) { browser, _ ->
        browser.folder to browser.filters
    }.flatMapLatest { (folder, filters) ->
      mediaRepository.getMediaItemsPaged(
          parentId = folder?.id,
          mediaTypes = filters.mediaTypes.ifEmpty { null },
          genres = filters.genres.ifEmpty { null },
          years = filters.years.ifEmpty { null },
          sortBy = filters.sortBy.apiValue,
          sortOrder = filters.sortBy.sortOrder,
          tags = filters.tags.ifEmpty { null },
          playedStatus = filters.playedStatus.takeIf { it != PlayedStatus.ALL },
          minRating = filters.minRating.takeIf { it > 0f },
          // Section mode ("See All" from a home Latest row) shows the same
          // top-level items as the default library tab — series for a TV library,
          // movies for a movie library — just sorted by latest. (Previously this
          // returned leaf episodes for a TV library, which stacked flat episode
          // blocks; issue #113.) Filtering to a specific leaf type is still
          // possible via the Media Type filter.
          kindFilter = ItemKindFilter.TOP_LEVEL,
      )
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
        launch { libraryStore.setLibraryPosterSize(size) }
    }

    fun setGroupBy(groupBy: GroupBy) {
        _browserState.set(LibraryBrowserReducer.setGroupBy(_browserState.value, groupBy))
        launch { libraryStore.setLibraryGroupBy(groupBy) }
    }

    private fun loadFolders() {
        launch {
            _isLoading.set(true)
            mediaRepository.getLibraryFolders()
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

    private fun loadGenres() {
        launch {
            // Retry once after a short delay so a transient network blip doesn't
            // leave the filter sheet permanently missing its Genres section.
            loadListWithRetry(mediaRepository::getGenres) { _genres.set(it) }
        }
    }

    private fun loadTags() {
        launch {
            loadListWithRetry(mediaRepository::getTags) { _tags.set(it) }
        }
    }

    /**
     * Fetches [fetch] and publishes the result via [onResult]. On failure, retries
     * once after [FILTER_RETRY_DELAY_MS] so a transient network blip doesn't leave
     * a filter section permanently empty.
     */
    private suspend fun <T> loadListWithRetry(
        fetch: suspend () -> Result<List<T>>,
        onResult: (List<T>) -> Unit,
    ) {
        var result = fetch()
        if (result.isFailure) {
            kotlinx.coroutines.delay(FILTER_RETRY_DELAY_MS)
            result = fetch()
        }
        result.onSuccess(onResult)
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
                    newFilters = libraryJson.decodeFromString<LibraryFilters>(savedFiltersJson)
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
                libraryStore.setLibraryFilters(folderId, libraryJson.encodeToString(newFilters))
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
                libraryStore.setLibraryFilters(folderId, libraryJson.encodeToString(LibraryFilters()))
                libraryStore.setDefaultLibrarySortOrder(folderId, SortOption.YEAR_DESC.name)
            }
            libraryStore.setLibraryPosterSize(result.state.posterSize)
            libraryStore.setLibraryGroupBy(result.state.groupBy)
        }
    }

    fun refresh() {
        launch {
            mediaRepository.invalidateCaches()
            loadFolders()
            loadGenres()
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
