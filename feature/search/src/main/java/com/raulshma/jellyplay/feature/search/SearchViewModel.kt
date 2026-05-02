package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchFilters(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _showFilters = MutableStateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    val pagedResults: Flow<PagingData<MediaItem>> = queryFlow
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { currentQuery ->
            if (currentQuery.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                _isSearching.value = true
                val result = mediaRepository.searchPaged(
                    query = currentQuery,
                    mediaTypes = _filters.value.mediaTypes.ifEmpty { null },
                )
                _isSearching.value = false
                result
            }
        }
        .cachedIn(viewModelScope)

    init {
        loadGenres()
    }

    fun search(newQuery: String) {
        query = newQuery
        queryFlow.value = newQuery
    }

    fun updateFilters(newFilters: SearchFilters) {
        _filters.value = newFilters
        if (query.isNotBlank()) {
            queryFlow.value = query
        }
    }

    fun toggleMediaType(mediaType: MediaType) {
        val current = _filters.value.mediaTypes
        _filters.value = _filters.value.copy(
            mediaTypes = if (mediaType in current) current - mediaType else current + mediaType,
        )
        if (query.isNotBlank()) {
            queryFlow.value = query
        }
    }

    fun toggleShowFilters() {
        _showFilters.value = !_showFilters.value
    }

    fun clearFilters() {
        _filters.value = SearchFilters()
        if (query.isNotBlank()) {
            queryFlow.value = query
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.value = it }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
