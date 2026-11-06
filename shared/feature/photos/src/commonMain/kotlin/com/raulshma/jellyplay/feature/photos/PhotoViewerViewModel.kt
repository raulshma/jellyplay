package com.raulshma.jellyplay.feature.photos

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class PhotoViewerViewModel(
    /** The detail-read family seam. */
    private val detailFeed: DetailFeed,
    /** The SearchResult-shaped reads (the sibling-photos query — off the union). */
    private val libraryApiClient: LibraryApiClient,
    private val imageUrlProvider: ImageUrlProvider,
    /** The item-based artwork accessor set (filmstrip thumbs via [ArtworkUrls.poster]). */
    val artworkUrls: ArtworkUrls,
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
     * The in-flight per-photo detail read. Superseded navigations cancel the
     * previous fetch before launching the next, so a fast swipe-through can
     * never stack concurrent detail calls (nor land an older result after a
     * newer one — the write guard below re-checks currency on completion).
     */
    private var detailJob: Job? = null

    /**
     * The in-flight strip-widening fetch (one at a time; a new trigger while
     * one runs is dropped — the widening re-fires on the next navigation if
     * the edge is still close).
     */
    private var widenJob: Job? = null

    /** The parent folder of the loaded strip — the widening fetch's scope. */
    private var siblingsParentId: String? = null

    /**
     * Photos whose display request was upgraded to the original resolution
     * after a zoom past fit. Once a photo is here it stays for the viewer's
     * lifetime: the original is in Coil's disk cache from the upgrade, so
     * swiping back to the photo re-requests the SAME url (no downgrade to the
     * fitted rendition, no second download), and its fitted decode serves as
     * the placeholder while it decodes.
     */
    private val _originalResolutionIds = composeState(emptySet<String>())
    val originalResolutionIds: androidx.compose.runtime.State<Set<String>> get() = _originalResolutionIds.asState()

    /**
     * Whether this platform can export the viewed photo (save to gallery /
     * share). Gates the viewer's save/share action buttons — false on desktop
     * until a gallery/share-sheet story lands there (voice-search seam pattern).
     */
    val canExportPhotos: Boolean get() = photoExport.isSupported

    fun load(itemId: String, parentId: String?) {
        detailJob?.cancel()
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val detailResult = detailFeed.getMediaDetail(itemId, force = false)
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
                siblingsParentId = parentId
                val (items, total) = loadSiblingsWindow(itemId, parentId)
                _state.value = _state.value.copy(
                    photo = item,
                    photoDetail = detail,
                    siblings = items,
                    totalSiblings = total,
                    currentIndex = items.indexOfFirst { it.id == itemId }.coerceAtLeast(0),
                    isLoading = false,
                )
                ensureSiblingsWindow()
            } else {
                siblingsParentId = null
                _state.value = _state.value.copy(
                    photo = item,
                    photoDetail = detail,
                    siblings = listOf(item),
                    totalSiblings = 1,
                    currentIndex = 0,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * The initial strip window: pages the parent's photos forward from the
     * strip start until the opened photo is found (the server gives no
     * "position of item" read, so discovery walks the same stable sort the
     * strip displays), the album is exhausted, or a page fails (degrade to
     * whatever loaded — the strip just starts shorter). The first window is
     * the fast path: a photo near the strip start paints after one round
     * trip, unchanged from the former single capped fetch.
     *
     * Returns the deduped strip and the server's total (null when nothing
     * resolved — the strip then has no widening target).
     */
    private suspend fun loadSiblingsWindow(
        itemId: String,
        parentId: String,
    ): Pair<List<com.raulshma.jellyplay.core.model.MediaItem>, Int?> {
        val loaded = mutableListOf<com.raulshma.jellyplay.core.model.MediaItem>()
        val seenIds = mutableSetOf<String>()
        var total: Int? = null
        var startIndex = 0
        while (loaded.none { it.id == itemId }) {
            val page = fetchSiblingsPage(parentId, startIndex) ?: break
            total = page.totalRecordCount.takeIf { it > 0 } ?: total
            val fresh = page.items.filterNot { it.id in seenIds }
            seenIds.addAll(fresh.map { it.id })
            loaded.addAll(fresh)
            val lastPage = page.items.isEmpty() || (total != null && startIndex + page.items.size >= total)
            if (lastPage) break
            startIndex += page.items.size
        }
        return loaded to total
    }

    private suspend fun fetchSiblingsPage(
        parentId: String,
        startIndex: Int,
    ): com.raulshma.jellyplay.core.model.SearchResult? = libraryApiClient.getMediaItems(
        parentId = parentId,
        filters = com.raulshma.jellyplay.core.model.LibraryFilters(
            mediaTypes = listOf(MediaType.PHOTO),
        ),
        startIndex = startIndex,
        limit = PHOTO_SIBLINGS_WINDOW,
    ).getOrNull()

    fun navigateTo(index: Int) {
        val items = _state.value.siblings
        if (index in items.indices) {
            _state.value = _state.value.copy(currentIndex = index, photo = items[index])
            loadDetailForCurrentPhoto(items[index].id)
            ensureSiblingsWindow()
        }
    }

    private fun loadDetailForCurrentPhoto(itemId: String) {
        detailJob?.cancel()
        detailJob = launch {
            val detailResult = detailFeed.getMediaDetail(itemId, force = false)
            // A completed-but-superseded fetch must not overwrite the newer
            // photo's detail (the cancel above covers the in-flight case; this
            // re-check covers the completion that raced it).
            if (itemId == _state.value.photo?.id) {
                detailResult.getOrNull()?.let { detail ->
                    _state.value = _state.value.copy(photoDetail = detail)
                }
            }
        }
    }

    /**
     * Strip widening: while the user navigates within [PHOTO_SIBLINGS_WIDEN_MARGIN]
     * of the loaded strip's end and the album has more pages, fetch the next
     * window and append it (deduped — the album may have changed between
     * fetches). One fetch in flight at a time; a failed page leaves the strip
     * as-is and the next edge-approach retries.
     */
    private fun ensureSiblingsWindow() {
        val state = _state.value
        val parentId = siblingsParentId ?: return
        val total = state.totalSiblings ?: return
        if (state.siblings.size >= total) return
        if (state.currentIndex < state.siblings.lastIndex - PHOTO_SIBLINGS_WIDEN_MARGIN) return
        if (widenJob?.isActive == true) return
        widenJob = launch {
            val page = fetchSiblingsPage(parentId, startIndex = _state.value.siblings.size) ?: return@launch
            val current = _state.value
            val seenIds = current.siblings.mapTo(mutableSetOf()) { it.id }
            val fresh = page.items.filterNot { it.id in seenIds }
            if (fresh.isEmpty()) return@launch
            _state.value = current.copy(siblings = current.siblings + fresh)
        }
    }

    /**
     * Marks [itemId] as upgraded to the original-resolution request (the
     * zoom-settle hook in the display path). Idempotent — the set only grows
     * for the viewer's lifetime.
     */
    fun requestOriginalResolution(itemId: String) {
        if (itemId !in _originalResolutionIds.value) {
            _originalResolutionIds.value = _originalResolutionIds.value + itemId
        }
    }

    fun hasNext(): Boolean = _state.value.currentIndex < _state.value.siblings.lastIndex

    fun hasPrevious(): Boolean = _state.value.currentIndex > 0

    fun getImageUrl(itemId: String, maxWidth: Int? = null): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)

    fun getThumbnailUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = 200)

    /**
     * The item-based full-image URL: same target id ([MediaItem.id]) and the
     * same null-[maxWidth]-means-original contract as the raw overload, with
     * the photo's own Primary tag as the `?tag=` cache-buster.
     */
    fun getImageUrl(item: MediaItem, maxWidth: Int? = null): String =
        imageUrlProvider.getImageUrl(item.id, maxWidth = maxWidth, tag = item.imageTags.primary)

    /** The item-based filmstrip thumb: same 200px width, the photo's own tag. */
    fun getThumbnailUrl(item: MediaItem): String =
        artworkUrls.poster(item, maxWidth = 200)

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
                    imageUrl = getImageUrl(photo, maxWidth = null),
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
        return getImageUrl(photo, maxWidth = null)
    }

    fun sharePhoto(onError: (String) -> Unit) {
        val photo = _state.value.photo ?: return
        launch {
            try {
                // Platform share (FileProvider + ACTION_SEND on Android) — see
                // savePhotoToGallery for the fetch/download failure contract.
                photoExport.sharePhoto(
                    imageUrl = getImageUrl(photo, maxWidth = null),
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
        detailJob?.cancel()
        widenJob?.cancel()
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
 *
 * [totalSiblings] is the album's server-side total behind the strip — the
 * widening target that tells the strip when to stop growing (null before the
 * first window resolves, or for the parentless single-photo mode).
 */
data class PhotoViewerState(
    val photo: MediaItem? = null,
    val photoDetail: MediaDetail? = null,
    val siblings: List<MediaItem> = emptyList(),
    val totalSiblings: Int? = null,
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)
