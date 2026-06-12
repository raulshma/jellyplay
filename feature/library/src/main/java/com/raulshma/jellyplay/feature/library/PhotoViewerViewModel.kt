package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _photo = composeState<MediaItem?>(null)
    val photo: androidx.compose.runtime.State<MediaItem?> get() = _photo.asState()

    private val _siblings = composeState<List<MediaItem>>(emptyList())
    val siblings: androidx.compose.runtime.State<List<MediaItem>> get() = _siblings.asState()

    private val _currentIndex = composeState(0)
    val currentIndex: androidx.compose.runtime.State<Int> get() = _currentIndex.asState()

    private val _isLoading = composeState(true)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading.asState()

    private val _error = composeState<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error.asState()

    fun load(itemId: String, parentId: String?) {
        launch {
            _isLoading.value = true
            _error.value = null

            val detailResult = mediaRepository.getMediaDetail(itemId)
            val item = detailResult.getOrNull()?.item
            if (item == null) {
                _error.value = detailResult.exceptionOrNull()?.message ?: "Failed to load photo"
                _isLoading.value = false
                return@launch
            }

            _photo.value = item

            if (parentId != null) {
                val siblingsResult = mediaRepository.getMediaItems(
                    parentId = parentId,
                    mediaTypes = listOf(MediaType.PHOTO),
                    limit = 200,
                )
                val items = siblingsResult.getOrNull()?.items ?: emptyList()
                _siblings.value = items
                _currentIndex.value = items.indexOfFirst { it.id == itemId }.coerceAtLeast(0)
            } else {
                _siblings.value = listOf(item)
                _currentIndex.value = 0
            }

            _isLoading.value = false
        }
    }

    fun navigateTo(index: Int) {
        val items = _siblings.value
        if (index in items.indices) {
            _currentIndex.value = index
            _photo.value = items[index]
        }
    }

    fun hasNext(): Boolean = _currentIndex.value < _siblings.value.lastIndex

    fun hasPrevious(): Boolean = _currentIndex.value > 0

    fun getImageUrl(itemId: String, maxWidth: Int = 1920): String =
        playbackRepository.getImageUrl(itemId, maxWidth = maxWidth)
}
