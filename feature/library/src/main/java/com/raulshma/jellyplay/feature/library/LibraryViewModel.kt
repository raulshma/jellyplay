package com.raulshma.jellyplay.feature.library

import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
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
import kotlinx.coroutines.flow.flatMapLatest
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
)

@Serializable
internal data class SavedLibraryFilters(
    val mediaTypes: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val sortBy: String = "SORT_NAME",
    val playedStatus: String = "ALL",
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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
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

    private val _showFilters = stateFlow(false)
    val showFilters = _showFilters.flow

    private val _viewMode = stateFlow(LibraryViewMode.GRID)
    val viewMode = _viewMode.flow

    private val _photoFolderChildUrls = stateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls = _photoFolderChildUrls.flow

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(_selectedFolder.flow, _filters.flow) { folder, filters ->
        folder to filters
    }.flatMapLatest { (folder, filters) ->
        mediaRepository.getMediaItemsPaged(
            parentId = folder?.id,
            mediaTypes = filters.mediaTypes.ifEmpty { null },
            genres = filters.genres.ifEmpty { null },
            years = filters.years.ifEmpty { null },
            sortBy = filters.sortBy.apiValue,
        )
    }
    .cachedIn(scope)

    init {
        loadFolders()
        loadGenres()
        loadViewMode()
    }

    private fun loadViewMode() {
        launch {
            preferencesStore.preferences.collect { prefs ->
                val folderId = _selectedFolder.value?.id
                val perLibrary = folderId?.let { id ->
                    prefs.libraryViewModes[id]?.let { modeName ->
                        runCatching { LibraryViewMode.valueOf(modeName) }.getOrNull()
                    }
                }
                _viewMode.set(perLibrary ?: prefs.libraryViewMode)
            }
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
                .onSuccess { _folders.set(it) }
                .onFailure { _error.set(it.message ?: "${it::class.simpleName}") }
            _isLoading.set(false)
        }
    }

    private fun loadGenres() {
        launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.set(it) }
        }
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
                )
                preferencesStore.setLibraryFilters(folder.id, Json.encodeToString(saved))
            }
        }
    }

    fun toggleShowFilters() {
        _showFilters.set(!_showFilters.value)
    }

    fun shuffleLibrary() {
        updateFilters(_filters.value.copy(sortBy = SortOption.RANDOM))
    }

    fun clearFilters() {
        _filters.set(LibraryFilters())
    }

    fun refresh() {
        loadFolders()
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) {
        launch {
            val current = _photoFolderChildUrls.value
            items.filter { it.mediaType == MediaType.PHOTO_FOLDER && it.id !in current }
                .forEach { folder ->
                    val urls = mediaRepository.getPhotoFolderChildImageUrls(folder.id)
                    _photoFolderChildUrls.set(_photoFolderChildUrls.value + (folder.id to urls))
                }
        }
    }
}
