package com.raulshma.jellyplay.feature.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
class LibraryState {
    var folders by mutableStateOf<List<LibraryFolder>>(emptyList())
        private set
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedFolder by mutableStateOf<LibraryFolder?>(null)
        private set

    fun updateFolders(folders: List<LibraryFolder>) { this.folders = folders }
    fun updateItems(items: List<MediaItem>) { this.items = items }
    fun setLoading(loading: Boolean) { isLoading = loading }
    fun setError(error: String?) { this.error = error }
    fun selectFolder(folder: LibraryFolder?) { selectedFolder = folder }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _state = LibraryState()
    val folders get() = _state.folders
    val items get() = _state.items
    val isLoading get() = _state.isLoading
    val error get() = _state.error
    val selectedFolder get() = _state.selectedFolder

    init {
        loadFolders()
        loadItems()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            mediaRepository.getLibraryFolders()
                .onSuccess { _state.updateFolders(it) }
                .onFailure { _state.setError(it.message ?: "Failed to load folders") }
        }
    }

    private fun loadItems(parentId: String? = null) {
        viewModelScope.launch {
            _state.setLoading(true)
            mediaRepository.getMediaItems(parentId = parentId, limit = 100)
                .onSuccess { _state.updateItems(it.items) }
                .onFailure { _state.setError(it.message ?: "Failed to load items") }
            _state.setLoading(false)
        }
    }

    fun selectFolder(folder: LibraryFolder?) {
        _state.selectFolder(folder)
        loadItems(folder?.id)
    }

    fun refresh() {
        loadItems(_state.selectedFolder?.id)
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
