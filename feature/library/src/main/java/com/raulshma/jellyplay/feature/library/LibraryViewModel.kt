package com.raulshma.jellyplay.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryFilters(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val sortBy: SortOption = SortOption.SORT_NAME,
    val playedStatus: PlayedStatus = PlayedStatus.ALL,
)

enum class SortOption(val displayName: String, val apiValue: String) {
    SORT_NAME("Name", "SortName"),
    YEAR_DESC("Newest", "ProductionYear,SortName"),
    YEAR_ASC("Oldest", "ProductionYear,SortName"),
    RATING("Rating", "CommunityRating,SortName"),
    DATE_ADDED("Recently Added", "DateCreated,SortName"),
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
) : ViewModel() {

    private val _folders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val folders: StateFlow<List<LibraryFolder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedFolder = MutableStateFlow<LibraryFolder?>(null)
    val selectedFolder: StateFlow<LibraryFolder?> = _selectedFolder.asStateFlow()

    private val _filters = MutableStateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters.asStateFlow()

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _showFilters = MutableStateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(_selectedFolder, _filters) { folder, filters ->
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
    .cachedIn(viewModelScope)

    init {
        loadFolders()
        loadGenres()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            mediaRepository.getLibraryFolders()
                .onSuccess { _folders.value = it }
                .onFailure { _error.value = it.message ?: "${it::class.simpleName}" }
            _isLoading.value = false
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.value = it }
        }
    }

    fun selectFolder(folder: LibraryFolder?) {
        _selectedFolder.value = folder
    }

    fun updateFilters(newFilters: LibraryFilters) {
        _filters.value = newFilters
    }

    fun toggleShowFilters() {
        _showFilters.value = !_showFilters.value
    }

    fun clearFilters() {
        _filters.value = LibraryFilters()
    }

    fun refresh() {
        loadFolders()
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
