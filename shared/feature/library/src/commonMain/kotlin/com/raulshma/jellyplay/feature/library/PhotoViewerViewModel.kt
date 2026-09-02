package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class PhotoViewerViewModel(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoExport: PhotoExport,
) : JellyPlayViewModel() {

    private val _photo = composeState<MediaItem?>(null)
    val photo: androidx.compose.runtime.State<MediaItem?> get() = _photo.asState()

    private val _photoDetail = composeState<MediaDetail?>(null)
    val photoDetail: androidx.compose.runtime.State<MediaDetail?> get() = _photoDetail.asState()

    private val _siblings = composeState<List<MediaItem>>(emptyList())
    val siblings: androidx.compose.runtime.State<List<MediaItem>> get() = _siblings.asState()

    private val _currentIndex = composeState(0)
    val currentIndex: androidx.compose.runtime.State<Int> get() = _currentIndex.asState()

    private val _isLoading = composeState(true)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading.asState()

    private val _error = composeState<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error.asState()

    private val _isSlideshowActive = composeState(false)
    val isSlideshowActive: androidx.compose.runtime.State<Boolean> get() = _isSlideshowActive.asState()

    private val _slideshowIntervalMs = composeState(5000L)
    val slideshowIntervalMs: androidx.compose.runtime.State<Long> get() = _slideshowIntervalMs.asState()

    private val _isSaving = composeState(false)
    val isSaving: androidx.compose.runtime.State<Boolean> get() = _isSaving.asState()

    private val _saveResult = composeState<SaveResult?>(null)
    val saveResult: androidx.compose.runtime.State<SaveResult?> get() = _saveResult.asState()

    private val _showAdjustments = composeState(false)
    val showAdjustments: androidx.compose.runtime.State<Boolean> get() = _showAdjustments.asState()

    private val _brightness = composeState(1f)
    val brightness: androidx.compose.runtime.State<Float> get() = _brightness.asState()

    private val _contrast = composeState(1f)
    val contrast: androidx.compose.runtime.State<Float> get() = _contrast.asState()

    private val _saturation = composeState(1f)
    val saturation: androidx.compose.runtime.State<Float> get() = _saturation.asState()

    private var slideshowJob: Job? = null

    /**
     * Whether this platform can export the viewed photo (save to gallery /
     * share). Gates the viewer's save/share action buttons — false on desktop
     * until a gallery/share-sheet story lands there (voice-search seam pattern).
     */
    val canExportPhotos: Boolean get() = photoExport.isSupported

    fun load(itemId: String, parentId: String?) {
        launch {
            _isLoading.value = true
            _error.value = null

            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrNull()
            val item = detail?.item
            if (item == null) {
                _error.value = detailResult.exceptionOrNull()?.message ?: "Failed to load photo"
                _isLoading.value = false
                return@launch
            }

            _photo.value = item
            _photoDetail.value = detail

            if (parentId != null) {
                val siblingsResult = mediaRepository.getMediaItems(
                    parentId = parentId,
                    filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                        mediaTypes = listOf(MediaType.PHOTO),
                    ),
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
            val item = items[index]
            _photo.value = item
            loadDetailForCurrentPhoto(item.id)
        }
    }

    private fun loadDetailForCurrentPhoto(itemId: String) {
        launch {
            val detailResult = mediaRepository.getMediaDetail(itemId)
            detailResult.getOrNull()?.let { _photoDetail.value = it }
        }
    }

    fun hasNext(): Boolean = _currentIndex.value < _siblings.value.lastIndex

    fun hasPrevious(): Boolean = _currentIndex.value > 0

    fun getImageUrl(itemId: String, maxWidth: Int? = null): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)

    fun getThumbnailUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = 200)

    fun toggleSlideshow() {
        if (_isSlideshowActive.value) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    fun startSlideshow() {
        _isSlideshowActive.value = true
        slideshowJob = launch {
            while (isActive) {
                delay(_slideshowIntervalMs.value)
                if (hasNext()) {
                    navigateTo(_currentIndex.value + 1)
                } else {
                    navigateTo(0)
                }
            }
        }
    }

    fun stopSlideshow() {
        _isSlideshowActive.value = false
        slideshowJob?.cancel()
        slideshowJob = null
    }

    fun toggleAdjustments() {
        _showAdjustments.value = !_showAdjustments.value
    }

    fun hideAdjustments() {
        _showAdjustments.value = false
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(0f, 2f)
    }

    fun setContrast(value: Float) {
        _contrast.value = value.coerceIn(0f, 2f)
    }

    fun setSaturation(value: Float) {
        _saturation.value = value.coerceIn(0f, 2f)
    }

    fun resetAdjustments() {
        _brightness.value = 1f
        _contrast.value = 1f
        _saturation.value = 1f
    }

    fun setSlideshowInterval(intervalMs: Long) {
        _slideshowIntervalMs.value = intervalMs
    }

    fun savePhotoToGallery() {
        val photo = _photo.value ?: return
        if (_isSaving.value) return

        launch {
            _isSaving.value = true
            _saveResult.value = null

            try {
                // The Coil fetch + MediaStore insert live in the platform
                // PhotoExport actual (androidMain); failures throw and land in
                // the shared catch below with the same messages as before.
                photoExport.saveToGallery(
                    imageUrl = getImageUrl(photo.id, maxWidth = null),
                    displayName = photo.name,
                )
                _saveResult.value = SaveResult.Success
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Failed to save photo")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun getFullImageUrl(): String? {
        val photo = _photo.value ?: return null
        return getImageUrl(photo.id, maxWidth = null)
    }

    fun sharePhoto(onError: (String) -> Unit) {
        val photo = _photo.value ?: return
        launch {
            try {
                // Platform share (FileProvider + ACTION_SEND on Android) — see
                // savePhotoToGallery for the fetch/download failure contract.
                photoExport.sharePhoto(
                    imageUrl = getImageUrl(photo.id, maxWidth = null),
                    displayName = photo.name,
                )
            } catch (e: Exception) {
                onError(e.message ?: "Failed to share photo")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
    }
}

sealed class SaveResult {
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}
