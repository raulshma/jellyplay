package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _collectionDetail = composeState<MediaDetail?>(null)
    val collectionDetail: MediaDetail? get() = _collectionDetail.value

    private val _items = composeState<List<MediaItem>>(emptyList())
    val items: List<MediaItem> get() = _items.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    fun loadCollection(collectionId: String) {
        launch {
            _isLoading.value = true
            _error.value = null
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(collectionId) }
                val itemsDeferred = async { mediaRepository.getCollectionItems(collectionId, limit = 100) }
                detailDeferred.await()
                    .onSuccess { detail -> _collectionDetail.value = detail }
                    .onFailure { e -> _error.value = e.message ?: "Failed to load collection" }
                itemsDeferred.await()
                    .onSuccess { result -> _items.value = result.items }
                    .onFailure { e -> _error.value = e.message ?: "Failed to load collection items" }
            }
            _isLoading.value = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
}
