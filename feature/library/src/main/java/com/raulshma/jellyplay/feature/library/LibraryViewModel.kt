package com.raulshma.jellyplay.feature.library

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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _folders = mutableStateOf<List<LibraryFolder>>(emptyList())
    val folders: androidx.compose.runtime.State<List<LibraryFolder>> get() = _folders
    private val _items = mutableStateOf<List<MediaItem>>(emptyList())
    val items: androidx.compose.runtime.State<List<MediaItem>> get() = _items
    private val _isLoading = mutableStateOf(true)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading
    private val _error = mutableStateOf<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error
    private val _selectedFolder = mutableStateOf<LibraryFolder?>(null)
    val selectedFolder: androidx.compose.runtime.State<LibraryFolder?> get() = _selectedFolder

    init {
        loadFolders()
        loadItems()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            mediaRepository.getLibraryFolders()
                .onSuccess { _folders.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load folders" }
        }
    }

    private fun loadItems(parentId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            mediaRepository.getMediaItems(parentId = parentId, limit = 100)
                .onSuccess { _items.value = it.items }
                .onFailure { _error.value = it.message ?: "Failed to load items" }
            _isLoading.value = false
        }
    }

    fun selectFolder(folder: LibraryFolder?) {
        _selectedFolder.value = folder
        loadItems(folder?.id)
    }

    fun refresh() {
        loadItems(_selectedFolder.value?.id)
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
