package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private var _collectionDetail by mutableStateOf<MediaDetail?>(null)
    val collectionDetail get() = _collectionDetail

    private var _items by mutableStateOf<List<MediaItem>>(emptyList())
    val items get() = _items

    private var _isLoading by mutableStateOf(true)
    val isLoading get() = _isLoading

    private var _error by mutableStateOf<String?>(null)
    val error get() = _error

    fun loadCollection(collectionId: String) {
        viewModelScope.launch {
            _isLoading = true
            _error = null
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(collectionId) }
                val itemsDeferred = async { mediaRepository.getCollectionItems(collectionId, limit = 100) }
                detailDeferred.await()
                    .onSuccess { detail -> _collectionDetail = detail }
                    .onFailure { e -> _error = e.message ?: "Failed to load collection" }
                itemsDeferred.await()
                    .onSuccess { result -> _items = result.items }
                    .onFailure { e -> _error = e.message ?: "Failed to load collection items" }
            }
            _isLoading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
}
