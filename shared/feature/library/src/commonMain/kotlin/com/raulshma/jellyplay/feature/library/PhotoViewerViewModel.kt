package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
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

    /**
     * The viewer's single content snapshot: the current photo + its detail
     * (content), the album strip (siblings), the strip cursor (index), and the
     * load/error pair (loadState) — ONE value behind ONE compose state instead
     * of six hand-synced flows, so a navigation can never publish a torn
     * combination. Slideshow/export/adjustment concerns deliberately stay OUT
     * of the snapshot (their own states below): the adjustment sliders are hot
     * and must not copy the whole content state per tick.
     */
    private val _state = composeState(PhotoViewerState())
    val state: androidx.compose.runtime.State<PhotoViewerState> get() = _state.asState()

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
            _state.value = _state.value.copy(isLoading = true, error = null)

            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrNull()
            val item = detail?.item
            if (item == null) {
                _state.value = _state.value.copy(
                    error = UserErrorMessages.resolve(detailResult, "Failed to load photo"),
                    isLoading = false,
                )
                return@launch
            }

            if (parentId != null) {
                val siblingsResult = mediaRepository.getMediaItems(
                    parentId = parentId,
                    filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                        mediaTypes = listOf(MediaType.PHOTO),
                    ),
                    limit = 200,
                )
                val items = siblingsResult.getOrNull()?.items ?: emptyList()
                _state.value = _state.value.copy(
                    photo = item,
                    photoDetail = detail,
                    siblings = items,
                    currentIndex = items.indexOfFirst { it.id == itemId }.coerceAtLeast(0),
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(
                    photo = item,
                    photoDetail = detail,
                    siblings = listOf(item),
                    currentIndex = 0,
                    isLoading = false,
                )
            }
        }
    }

    fun navigateTo(index: Int) {
        val items = _state.value.siblings
        if (index in items.indices) {
            _state.value = _state.value.copy(currentIndex = index, photo = items[index])
            loadDetailForCurrentPhoto(items[index].id)
        }
    }

    private fun loadDetailForCurrentPhoto(itemId: String) {
        launch {
            val detailResult = mediaRepository.getMediaDetail(itemId)
            detailResult.getOrNull()?.let { detail ->
                _state.value = _state.value.copy(photoDetail = detail)
            }
        }
    }

    fun hasNext(): Boolean = _state.value.currentIndex < _state.value.siblings.lastIndex

    fun hasPrevious(): Boolean = _state.value.currentIndex > 0

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
                    navigateTo(_state.value.currentIndex + 1)
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
        val photo = _state.value.photo ?: return
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
                _saveResult.value = SaveResult.Error(UserErrorMessages.resolve(e, "Failed to save photo"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun getFullImageUrl(): String? {
        val photo = _state.value.photo ?: return null
        return getImageUrl(photo.id, maxWidth = null)
    }

    fun sharePhoto(onError: (String) -> Unit) {
        val photo = _state.value.photo ?: return
        launch {
            try {
                // Platform share (FileProvider + ACTION_SEND on Android) — see
                // savePhotoToGallery for the fetch/download failure contract.
                photoExport.sharePhoto(
                    imageUrl = getImageUrl(photo.id, maxWidth = null),
                    displayName = photo.name,
                )
            } catch (e: Exception) {
                onError(UserErrorMessages.resolve(e, "Failed to share photo"))
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

/**
 * The viewer's content snapshot value type: the current photo + its detail
 * (content), the album strip (siblings), the strip cursor (index), and the
 * load/error pair (loadState). One value so a navigation can never publish a
 * torn combination (photo from one sibling list, index from another).
 */
data class PhotoViewerState(
    val photo: MediaItem? = null,
    val photoDetail: MediaDetail? = null,
    val siblings: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)
