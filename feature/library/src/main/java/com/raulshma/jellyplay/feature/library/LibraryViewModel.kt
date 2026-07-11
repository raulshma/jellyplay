package com.raulshma.jellyplay.feature.library

import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
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

@Immutable
data class LibraryFilters(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val sortBy: SortOption = SortOption.SORT_NAME,
    val playedStatus: PlayedStatus = PlayedStatus.ALL,
    val tags: List<String> = emptyList(),
    val minRating: Float = 0f,
)

@Serializable
internal data class SavedLibraryFilters(
    val mediaTypes: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val sortBy: String = "SORT_NAME",
    val playedStatus: String = "ALL",
    val tags: List<String> = emptyList(),
    val minRating: Float = 0f,
)

enum class SortOption(val displayName: String, val apiValue: String) {
    SORT_NAME("Name", "SortName"),
    YEAR_DESC("Newest", "ProductionYear,SortName"),
    YEAR_ASC("Oldest", "ProductionYear,SortName"),
    RATING("Rating", "CommunityRating,SortName"),
    DATE_ADDED("Recently Added", "DateCreated,SortName"),
    RANDOM("Random", "Random"),
    DATE_PLAYED("Recently Played", "DatePlayed,SortName"),
    PREMIERE_DATE("Release Date", "PremiereDate,SortName"),
}

enum class PlayedStatus(val displayName: String) {
    ALL("All"),
    PLAYED("Played"),
    UNPLAYED("Unplayed"),
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
    private val preferencesStore: UserPreferencesStore,
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

    private val _photoFolderChildUrls = stateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls = _photoFolderChildUrls.flow

    /**
     * Per-item slice of [photoFolderChildUrls]. Lets each photo-folder card
     * collect only its own urls so a prefetch merge (which produces a new Map
     * reference) doesn't invalidate the entire [LibraryScreen] — only the one
     * card whose urls changed. See L3.1 in the perf spec.
     */
    fun photoFolderChildUrlsFor(itemId: String): kotlinx.coroutines.flow.Flow<List<String>> =
        _photoFolderChildUrls.flow
            .map { it[itemId].orEmpty() }
            .distinctUntilChanged()

    private val _refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(_selectedFolder.flow, _filters.flow, _refreshTrigger) { folder, filters, _ ->
        folder to filters
    }.flatMapLatest { (folder, filters) ->
        mediaRepository.getMediaItemsPaged(
            parentId = folder?.id,
            mediaTypes = filters.mediaTypes.ifEmpty { null },
            genres = filters.genres.ifEmpty { null },
            years = filters.years.ifEmpty { null },
            sortBy = filters.sortBy.apiValue,
            tags = filters.tags.ifEmpty { null },
        )
    }
    .cachedIn(scope)

    init {
        loadFolders()
        loadGenres()
        loadTags()
        loadViewMode()
    }

    private fun loadViewMode() {
        // Project only the view-mode fields (avoid re-evaluating on every
        // unrelated pref write) and combine with the selected folder so a
        // folder change also triggers re-evaluation (was a latent correctness
        // edge: the old collector only read _selectedFolder inside the prefs
        // collector, so a folder-only change wouldn't re-derive the mode).
        launch {
            combine(
                preferencesStore.preferences
                    .map { ViewModePrefs(it.libraryViewMode, it.libraryViewModes) }
                    .distinctUntilChanged(),
                _selectedFolder.flow,
            ) { viewModePrefs, folder ->
                val perLibrary = folder?.id?.let { id ->
                    viewModePrefs.libraryViewModes[id]?.let { modeName ->
                        runCatching { LibraryViewMode.valueOf(modeName) }.getOrNull()
                    }
                }
                perLibrary ?: viewModePrefs.libraryViewMode
            }.collect { mode -> _viewMode.set(mode) }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _viewMode.set(mode)
        launch {
            preferencesStore.setLibraryViewMode(mode)
            val folderId = _selectedFolder.value?.id
            if (folderId != null) {
                preferencesStore.setLibraryViewMode(folderId, mode.name)
            }
        }
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
        _selectedFolder.set(folder)
        if (folder != null) {
            val prefs = preferencesStore.preferences.value
            val savedOrder = prefs.defaultLibrarySortOrders[folder.id]
            val savedFiltersJson = prefs.libraryFilters[folder.id]

            var newFilters = LibraryFilters()

            if (savedFiltersJson != null) {
                try {
                    val saved = Json.decodeFromString<SavedLibraryFilters>(savedFiltersJson)
                    newFilters = LibraryFilters(
                        mediaTypes = saved.mediaTypes.mapNotNull { runCatching { MediaType.valueOf(it) }.getOrNull() },
                        genres = saved.genres,
                        years = saved.years,
                        sortBy = SortOption.entries.find { it.name == saved.sortBy || it.apiValue == saved.sortBy } ?: SortOption.SORT_NAME,
                        playedStatus = PlayedStatus.entries.find { it.name == saved.playedStatus } ?: PlayedStatus.ALL,
                        tags = saved.tags,
                        minRating = saved.minRating,
                    )
                } catch (_: Exception) {
                    newFilters = LibraryFilters()
                }
            } else if (savedOrder != null) {
                val option = SortOption.entries.find { it.name == savedOrder || it.apiValue == savedOrder } ?: SortOption.SORT_NAME
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
        val folder = _selectedFolder.value
        if (folder != null) {
            launch {
                preferencesStore.setDefaultLibrarySortOrder(folder.id, newFilters.sortBy.name)
                val saved = SavedLibraryFilters(
                    mediaTypes = newFilters.mediaTypes.map { it.name },
                    genres = newFilters.genres,
                    years = newFilters.years,
                    sortBy = newFilters.sortBy.name,
                    playedStatus = newFilters.playedStatus.name,
                    tags = newFilters.tags,
                    minRating = newFilters.minRating,
                )
                preferencesStore.setLibraryFilters(folder.id, Json.encodeToString(saved))
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
