package com.raulshma.jellyplay.feature.library

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    @ApplicationContext private val appContext: Context,
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
                val imageLoader = coil3.SingletonImageLoader.get(appContext)
                val imageUrl = getImageUrl(photo.id, maxWidth = null)
                val request = ImageRequest.Builder(appContext)
                    .data(imageUrl)
                    .allowHardware(false)
                    // Decode a private Bitmap (not the shared cache instance)
                    // before compressing it to the gallery. Without this,
                    // toBitmap() returns Coil's shared Bitmap, which the
                    // BitmapPool can recycle mid-compress if the photo grid
                    // evicts the ORIGINAL cache entry during the IO write.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()

                val result = imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()

                if (bitmap != null) {
                    saveBitmapToMediaStore(appContext, bitmap, photo.name)
                    _saveResult.value = SaveResult.Success
                } else {
                    _saveResult.value = SaveResult.Error("Failed to download image")
                }
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

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, displayName: String) {
        val filename = "${displayName}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JellyPlay")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver: ContentResolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    fun getFullImageUrl(): String? {
        val photo = _photo.value ?: return null
        return getImageUrl(photo.id, maxWidth = null)
    }

    fun sharePhoto(context: Context, onError: (String) -> Unit) {
        val photo = _photo.value ?: return
        launch {
            try {
                val imageLoader = coil3.SingletonImageLoader.get(appContext)
                val imageUrl = getImageUrl(photo.id, maxWidth = null)
                val request = ImageRequest.Builder(appContext)
                    .data(imageUrl)
                    .allowHardware(false)
                    // Private decode — see savePhotoToGallery for rationale.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()

                val result = imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()

                if (bitmap != null) {
                    val cachePath = File(context.cacheDir, "shared_images")
                    cachePath.mkdirs()
                    val file = File(cachePath, "${photo.name.replace(" ", "_")}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, photo.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                    } else {
                        onError("Failed to generate share link")
                    }
                } else {
                    onError("Failed to download image")
                }
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
