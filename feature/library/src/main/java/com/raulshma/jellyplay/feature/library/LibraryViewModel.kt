package com.raulshma.jellyplay.feature.library

import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.defaultViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
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

@Immutable
@Serializable
data class LibraryFilters(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    // Newest (highest production year first) is the most useful landing sort for
    // a media library — a user opening the tab wants to see fresh content, not an
    // alphabetical list. Overridden per-folder by the persisted filter blob.
    val sortBy: SortOption = SortOption.YEAR_DESC,
    val playedStatus: PlayedStatus = PlayedStatus.ALL,
    val tags: List<String> = emptyList(),
    val minRating: Float = 0f,
)

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

/** Factory-default poster-size multiplier (matches the toolbar slider's 1.0 center). */
private const val DEFAULT_POSTER_SIZE = 1.0f

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoFolderPrefetcher: PhotoFolderPrefetcher,
    private val libraryStore: com.raulshma.jellyplay.core.datastore.library.LibraryStore,
) : JellyPlayViewModel() {

    private val _folders = stateFlow<List<LibraryFolder>>(emptyList())
    val folders = _folders.flow

    private val _isLoading = stateFlow(true)
    val isLoading = _isLoading.flow

    private val _error = stateFlow<String?>(null)
    val error = _error.flow

    private val _selectedFolder = stateFlow<LibraryFolder?>(null)
    val selectedFolder = _selectedFolder.flow

    private val _filters = stateFlow(LibraryFilters())
    val filters = _filters.flow

    private val _genres = stateFlow<List<Genre>>(emptyList())
    val genres = _genres.flow

    private val _tags = stateFlow<List<String>>(emptyList())
    val tags = _tags.flow

    private val _showFilters = stateFlow(false)
    val showFilters = _showFilters.flow

    private val _viewMode = stateFlow(LibraryViewMode.GRID)
    val viewMode = _viewMode.flow

    /**
     * The user's explicit in-memory view-mode choice (set by [setViewMode]).
     * While non-null, [loadViewMode]'s collector refuses to overwrite
     * [_viewMode] — closing a race where the async store write re-emits the
     * old persisted value (or the collectionType-derived default) and snaps the
     * grid back to the previous mode moments after a tap. Reset on folder
     * change so the new folder loads its own saved mode.
     */
    private val _userViewModeOverride = kotlinx.coroutines.flow.MutableStateFlow<LibraryViewMode?>(null)

    private val _photoFolderChildUrls = stateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls = _photoFolderChildUrls.flow

    /**
     * Non-null when this VM is driving a home-section "See All" deep-link. While
     * set, the folder chips are hidden, folder loading is skipped, and the
     * pre-applied sort / media-type filter from the source section is used
     * instead of the persisted per-folder state.
     *
     * NOTE: this VM is shared across the Library tab ([Route.Library]) and the
     * section deep-link ([Route.LibrarySection]) because there is no per-entry
     * ViewModel store — `hiltViewModel()` resolves to the Activity scope. So
     * section state is NOT cleared implicitly; the tab entry must call
     * [clearSectionMode] on entry to reset to the default browsing view,
     * otherwise stale "Latest X" filters leak into the Library tab (issue #113).
     */
    private val _sectionContext = stateFlow<LibrarySectionContext?>(null)
    val sectionContext = _sectionContext.flow

    /** Title to show in the toolbar. Null ⇒ the default "Library" string. */
    private val _title = stateFlow<String?>(null)
    val title = _title.flow

    /** Poster-size multiplier persisted globally (see [LibraryStore]). */
    private val _posterSize = stateFlow(1.0f)
    val posterSize = _posterSize.flow

    /** Client-side grouping dimension persisted globally (see [LibraryStore]). */
    private val _groupBy = stateFlow(GroupBy.NONE)
    val groupBy = _groupBy.flow

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
        _selectedFolder.flow,
        _filters.flow,
        _sectionContext.flow,
        _refreshTrigger,
    ) { folder, filters, _, _ ->
        folder to filters
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
        if (_sectionContext.value == ctx) return
        _sectionContext.set(ctx)
        _title.set(ctx.title)
        val folder = ctx.parentId?.let { LibraryFolder(id = it, name = ctx.title, collectionType = ctx.collectionType) }
        _selectedFolder.set(folder)
        val sectionSort = ctx.sortBy?.let { api ->
            SortOption.entries.firstOrNull { it.apiValue == api || it.name == api }
        } ?: SortOption.DATE_ADDED
        // "See All" from a home Latest row should mirror the default library tab
        // view (top-level items: series for TV, movies for a movie library, …)
        // and only differ in sort order (latest first). Previously this defaulted
        // to leaf episodes for a TV library, which produced large blocks of flat
        // episode rows — unintuitive and not what the user expects from "Latest".
        // Explicit ctx.mediaTypes (if ever passed) still win; otherwise we show
        // top-level items sorted by latest. See pagedItems' kindFilter below.
        _userViewModeOverride.value = null
        _filters.set(
            LibraryFilters(
                sortBy = sectionSort,
                mediaTypes = ctx.mediaTypes,
            )
        )
    }

    /**
     * Resets all section-mode state so the Library tab renders its default
     * browsing view. Called when the tab entry ([Route.Library]) is shown after a
     * section deep-link, because the VM is shared across both entries (see the
     * note on [_sectionContext]). Idempotent: a no-op when not in section mode,
     * so repeated recompositions are safe.
     *
     * Restores the default filter set and clears the synthetic folder/title so
     * the folder chips reload and the toolbar shows "Library" again. Per-folder
     * persisted filters are re-applied the next time a folder is selected.
     */
    fun clearSectionMode() {
        if (_sectionContext.value == null) return
        _sectionContext.set(null)
        _title.set(null)
        _selectedFolder.set(null)
        _filters.set(LibraryFilters())
        _userViewModeOverride.value = null
    }

    private fun loadLayoutPrefs() {
        launch {
            libraryStore.library
                .map { it.libraryPosterSize to it.libraryGroupBy }
                .distinctUntilChanged()
                .collect { (posterSize, groupBy) ->
                    _posterSize.set(posterSize)
                    _groupBy.set(groupBy)
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
        // Project only the view-mode fields (avoid re-evaluating on every
        // unrelated pref write) and combine with the selected folder so a
        // folder change also triggers re-evaluation (was a latent correctness
        // edge: the old collector only read _selectedFolder inside the prefs
        // collector, so a folder-only change wouldn't re-derive the mode).
        //
        // Precedence: explicit per-folder user override > collectionType-driven
        // default (see LibraryFolder.defaultViewMode) > global pref default.
        // This makes the layout server-driven by default — a music library shows
        // as a list, a movies library as a poster grid — while still letting the
        // user override per-folder via the toolbar toggle.
        launch {
            combine(
                libraryStore.library
                    .map { ViewModePrefs(it.libraryViewMode, it.libraryViewModes) }
                    .distinctUntilChanged(),
                _selectedFolder.flow,
            ) { viewModePrefs, folder ->
                val perLibrary = folder?.id?.let { id ->
                    viewModePrefs.libraryViewModes[id]?.let { modeName ->
                        runCatching { LibraryViewMode.valueOf(modeName) }.getOrNull()
                    }
                }
                perLibrary ?: folder?.defaultViewMode() ?: viewModePrefs.libraryViewMode
            }.collect { mode ->
                // Don't clobber an explicit user choice. A view-mode tap writes
                // the store asynchronously; that store re-emission lands here and
                // would otherwise snap the grid back to the stale/derived value
                // (the "changes then switches back" bug). The override is cleared
                // on folder change so each folder still loads its saved mode.
                if (_userViewModeOverride.value == null) {
                    _viewMode.set(mode)
                }
            }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _viewMode.set(mode)
        // Record the choice so the loadViewMode collector yields until the store
        // settles, then clear it — subsequent folder switches re-derive normally.
        _userViewModeOverride.value = mode
        launch {
            libraryStore.setLibraryViewMode(mode)
            val folderId = _selectedFolder.value?.id
            // In section mode the synthetic folder id is the section's parentId;
            // persisting a per-folder view-mode override there would leak the
            // section scope into the user's real per-library settings, so only
            // persist the global default in section mode.
            if (folderId != null && _sectionContext.value == null) {
                libraryStore.setLibraryViewMode(folderId, mode.name)
            }
        }
    }

    fun setPosterSize(size: Float) {
        _posterSize.set(size)
        launch { libraryStore.setLibraryPosterSize(size) }
    }

    fun setGroupBy(groupBy: GroupBy) {
        _groupBy.set(groupBy)
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
        // Clear any explicit override from the previous folder so the new folder
        // loads its own saved/derived view mode (otherwise the override would
        // suppress the loadViewMode collector for the new selection).
        _userViewModeOverride.value = null
        _selectedFolder.set(folder)
        if (folder != null) {
            val prefs = libraryStore.library.value
            val savedOrder = prefs.defaultLibrarySortOrders[folder.id]
            val savedFiltersJson = prefs.libraryFilters[folder.id]

            var newFilters = LibraryFilters()

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

            _filters.set(newFilters)

            val savedViewMode = prefs.libraryViewModes[folder.id]?.let { modeName ->
                runCatching { LibraryViewMode.valueOf(modeName) }.getOrNull()
            }
            if (savedViewMode != null) {
                _viewMode.set(savedViewMode)
            } else {
                _viewMode.set(prefs.libraryViewMode)
            }
        }
    }

    fun updateFilters(newFilters: LibraryFilters) {
        _filters.set(newFilters)
        // Skip persistence in section mode: the folder id is synthetic (a
        // section parentId) and persisting there would leak section state into
        // the user's real per-library filter overrides.
        val folder = _selectedFolder.value
        if (folder != null && _sectionContext.value == null) {
            launch {
                libraryStore.setDefaultLibrarySortOrder(folder.id, newFilters.sortBy.name)
                libraryStore.setLibraryFilters(folder.id, libraryJson.encodeToString(newFilters))
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
        _filters.set(_filters.value.copy(sortBy = SortOption.RANDOM))
    }

    fun clearFilters() {
        _filters.set(LibraryFilters())
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
     * UI, but this method stays defensive: it also tears down the section
     * context so the synthetic folder/title can't linger after the reset, and
     * the captured [wasInSection] flag (not the post-clear state) gates
     * per-folder persistence — the section's parentId must never be written as
     * a real library's saved filters.
     */
    fun resetToDefault() {
        val currentFolder = _selectedFolder.value
        val wasInSection = _sectionContext.value != null
        if (wasInSection) {
            _sectionContext.set(null)
            _title.set(null)
        }
        // Clear the override so loadViewMode's collector is free to re-derive.
        _userViewModeOverride.value = null
        _selectedFolder.set(null)
        _filters.set(LibraryFilters())
        _posterSize.set(DEFAULT_POSTER_SIZE)
        _groupBy.set(GroupBy.NONE)
        // Optimistic snapshot so the grid doesn't flash the stale mode before
        // loadViewMode re-emits (folder is null now, so this is the global pref).
        _viewMode.set(libraryStore.library.value.libraryViewMode)
        launch {
            if (currentFolder != null && !wasInSection) {
                libraryStore.setLibraryFilters(currentFolder.id, libraryJson.encodeToString(LibraryFilters()))
                libraryStore.setDefaultLibrarySortOrder(currentFolder.id, SortOption.YEAR_DESC.name)
            }
            libraryStore.setLibraryPosterSize(DEFAULT_POSTER_SIZE)
            libraryStore.setLibraryGroupBy(GroupBy.NONE)
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
