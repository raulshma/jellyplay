package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
class SearchState {
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set

    fun updateQuery(query: String) { this.query = query }
    fun updateResults(results: List<MediaItem>) { this.results = results }
    fun setSearching(searching: Boolean) { isSearching = searching }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _state = SearchState()
    val query get() = _state.query
    val results get() = _state.results
    val isSearching get() = _state.isSearching

    private var searchJob: Job? = null

    fun search(query: String) {
        _state.updateQuery(query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.updateResults(emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            _state.setSearching(true)
            delay(300)
            mediaRepository.search(query)
                .onSuccess { _state.updateResults(it.items) }
                .onFailure { _state.updateResults(emptyList()) }
            _state.setSearching(false)
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
