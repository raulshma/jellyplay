@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoViewerViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — same
    // harness as LibraryViewModelTest (runTest reuses the Main TestDispatcher's
    // scheduler, so the VM's viewModelScope coroutines run on this scheduler).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoExport: PhotoExport

    private val photo1 = MediaItem(id = "p1", name = "Photo 1", mediaType = MediaType.PHOTO)
    private val photo2 = MediaItem(id = "p2", name = "Photo 2", mediaType = MediaType.PHOTO)
    private val photo3 = MediaItem(id = "p3", name = "Photo 3", mediaType = MediaType.PHOTO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        photoExport = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        // The relaxed mock's default Result would ClassCast inside the VM's
        // .getOrNull() — stub every detail read with a real Result (relaxed
        // slideshow navigations included).
        coEvery { mediaRepository.getMediaDetail(any()) } returns
            Result.success(MediaDetail(item = photo1))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PhotoViewerViewModel = PhotoViewerViewModel(
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        photoExport = photoExport,
    )

    /** Three-photo album the viewer can page through via parentId siblings. */
    private fun stubAlbumSiblings(parentId: String = "album-1") {
        coEvery {
            mediaRepository.getMediaItems(parentId = parentId, filters = any(), limit = 200)
        } returns Result.success(
            SearchResult(items = listOf(photo1, photo2, photo3), totalRecordCount = 3, startIndex = 0)
        )
        coEvery { mediaRepository.getMediaDetail(photo1.id) } returns Result.success(MediaDetail(item = photo1))
        coEvery { mediaRepository.getMediaDetail(photo2.id) } returns Result.success(MediaDetail(item = photo2))
        coEvery { mediaRepository.getMediaDetail(photo3.id) } returns Result.success(MediaDetail(item = photo3))
    }

    /** Loads the viewer onto [photo2] inside a three-photo album (index 1). */
    private fun TestScope.loadViewerInAlbum(vm: PhotoViewerViewModel): PhotoViewerViewModel {
        stubAlbumSiblings()
        vm.load(itemId = "p2", parentId = "album-1")
        advanceUntilIdle()
        return vm
    }

    // ── load ────────────────────────────────────────────────────────────────

    @Test
    fun `load success populates photo detail siblings and currentIndex`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        assertEquals(photo2, vm.photo.value)
        assertEquals(photo2, vm.photoDetail.value?.item)
        assertEquals(listOf(photo1, photo2, photo3), vm.siblings.value)
        assertEquals(1, vm.currentIndex.value)
        assertFalse(vm.isLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `load queries siblings as photos capped at 200`() = runTest {
        // The album strip must only ever contain photos, regardless of what
        // else lives in the parent folder.
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        coVerify(exactly = 1) {
            mediaRepository.getMediaItems(
                parentId = "album-1",
                filters = LibraryFilters(mediaTypes = listOf(MediaType.PHOTO)),
                limit = 200,
            )
        }
    }

    @Test
    fun `load without parentId falls back to a single-item sibling list at index 0`() = runTest {
        val vm = createViewModel()
        stubAlbumSiblings()
        vm.load(itemId = "p2", parentId = null)
        advanceUntilIdle()

        assertEquals(photo2, vm.photo.value)
        assertEquals(listOf(photo2), vm.siblings.value)
        assertEquals(0, vm.currentIndex.value)
    }

    @Test
    fun `load failure surfaces the error message and keeps photo null`() = runTest {
        coEvery { mediaRepository.getMediaDetail("broken") } returns
            Result.failure(RuntimeException("server exploded"))

        val vm = createViewModel()
        vm.load(itemId = "broken", parentId = "album-1")
        advanceUntilIdle()

        assertEquals("server exploded", vm.error.value)
        assertNull(vm.photo.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `load failure with a null exception message falls back to a default`() = runTest {
        coEvery { mediaRepository.getMediaDetail("broken") } returns Result.failure(RuntimeException())

        val vm = createViewModel()
        vm.load(itemId = "broken", parentId = null)
        advanceUntilIdle()

        assertEquals("Failed to load photo", vm.error.value)
    }

    // ── navigation ──────────────────────────────────────────────────────────

    @Test
    fun `navigateTo moves to a valid index and fetches the new photo's detail`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.navigateTo(2)
        advanceUntilIdle()

        assertEquals(2, vm.currentIndex.value)
        assertEquals(photo3, vm.photo.value)
        coVerify { mediaRepository.getMediaDetail("p3") }
    }

    @Test
    fun `navigateTo ignores out-of-bounds indices`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.navigateTo(-1)
        vm.navigateTo(3)
        advanceUntilIdle()

        // Still on photo2 (index 1), no detail re-fetch for an out-of-range id.
        assertEquals(1, vm.currentIndex.value)
        assertEquals(photo2, vm.photo.value)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail("p1") }
    }

    @Test
    fun `hasNext and hasPrevious track the current index`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        // Loaded onto photo2 (index 1 of 0..2): both directions available.
        assertTrue(vm.hasPrevious())
        assertTrue(vm.hasNext())

        vm.navigateTo(0)
        assertFalse(vm.hasPrevious())
        assertTrue(vm.hasNext())

        vm.navigateTo(2)
        assertTrue(vm.hasPrevious())
        assertFalse(vm.hasNext())
    }

    // ── slideshow ───────────────────────────────────────────────────────────

    @Test
    fun `startSlideshow advances with virtual time and wraps to index 0 at the end`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.startSlideshow()
        assertTrue(vm.isSlideshowActive.value)

        testScheduler.runCurrent() // start the loop; parked on the first delay
        assertEquals(1, vm.currentIndex.value)

        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(2, vm.currentIndex.value)

        // Past the last sibling the slideshow wraps back to the first photo.
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(0, vm.currentIndex.value)

        vm.stopSlideshow()
    }

    @Test
    fun `stopSlideshow cancels the advance loop`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.startSlideshow()
        testScheduler.runCurrent()
        vm.stopSlideshow()
        assertFalse(vm.isSlideshowActive.value)

        val indexAfterStop = vm.currentIndex.value
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        // No further ticks after the cancel — the index never moves again.
        assertEquals(indexAfterStop, vm.currentIndex.value)
    }

    @Test
    fun `setSlideshowInterval changes the delay between advances`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)
        vm.setSlideshowInterval(1_000L)
        assertEquals(1_000L, vm.slideshowIntervalMs.value)

        vm.startSlideshow()
        testScheduler.runCurrent()

        // 1s is not enough for the default 5s tick but fires the 1s one.
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(2, vm.currentIndex.value)

        vm.stopSlideshow()
    }

    // ── adjustments ─────────────────────────────────────────────────────────

    @Test
    fun `setBrightness setContrast and setSaturation clamp to the 0f to 2f range`() = runTest {
        val vm = createViewModel()

        vm.setBrightness(3f)
        assertEquals(2f, vm.brightness.value)
        vm.setBrightness(-1f)
        assertEquals(0f, vm.brightness.value)
        vm.setBrightness(1.5f)
        assertEquals(1.5f, vm.brightness.value)

        vm.setContrast(99f)
        assertEquals(2f, vm.contrast.value)
        vm.setContrast(-99f)
        assertEquals(0f, vm.contrast.value)
        vm.setContrast(0.5f)
        assertEquals(0.5f, vm.contrast.value)

        vm.setSaturation(5f)
        assertEquals(2f, vm.saturation.value)
        vm.setSaturation(-5f)
        assertEquals(0f, vm.saturation.value)
        vm.setSaturation(2f)
        assertEquals(2f, vm.saturation.value)
    }

    @Test
    fun `resetAdjustments restores the neutral 1f values`() = runTest {
        val vm = createViewModel()
        vm.setBrightness(0f)
        vm.setContrast(2f)
        vm.setSaturation(0.25f)

        vm.resetAdjustments()

        assertEquals(1f, vm.brightness.value)
        assertEquals(1f, vm.contrast.value)
        assertEquals(1f, vm.saturation.value)
    }

    // ── save / share / export gating ────────────────────────────────────────

    @Test
    fun `savePhotoToGallery reports success through the export seam`() = runTest {
        coEvery { photoExport.saveToGallery(any(), any()) } returns Unit
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.savePhotoToGallery()
        advanceUntilIdle()

        assertEquals(SaveResult.Success, vm.saveResult.value)
        assertFalse(vm.isSaving.value)
        coVerify(exactly = 1) {
            photoExport.saveToGallery("https://example.com/image.jpg", "Photo 2")
        }
    }

    @Test
    fun `savePhotoToGallery maps an export throw into a SaveResult Error with the message`() = runTest {
        coEvery { photoExport.saveToGallery(any(), any()) } throws RuntimeException("disk full")
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.savePhotoToGallery()
        advanceUntilIdle()

        assertEquals(SaveResult.Error("disk full"), vm.saveResult.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `savePhotoToGallery falls back to a default message when the throw has none`() = runTest {
        coEvery { photoExport.saveToGallery(any(), any()) } throws RuntimeException()
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.savePhotoToGallery()
        advanceUntilIdle()

        assertEquals(SaveResult.Error("Failed to save photo"), vm.saveResult.value)
    }

    @Test
    fun `savePhotoToGallery is a no-op while a save is already in flight`() = runTest {
        coEvery { photoExport.saveToGallery(any(), any()) } coAnswers { awaitCancellation() }
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.savePhotoToGallery()
        testScheduler.runCurrent() // first save parks inside the export call
        assertTrue(vm.isSaving.value)

        vm.savePhotoToGallery() // re-entrant click must be swallowed
        advanceUntilIdle()

        coVerify(exactly = 1) { photoExport.saveToGallery(any(), any()) }
    }

    @Test
    fun `savePhotoToGallery is a no-op when no photo is loaded`() = runTest {
        val vm = createViewModel()

        vm.savePhotoToGallery()
        advanceUntilIdle()

        coVerify(exactly = 0) { photoExport.saveToGallery(any(), any()) }
        assertNull(vm.saveResult.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `clearSaveResult resets the save result state`() = runTest {
        coEvery { photoExport.saveToGallery(any(), any()) } returns Unit
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.savePhotoToGallery()
        advanceUntilIdle()
        assertEquals(SaveResult.Success, vm.saveResult.value)

        vm.clearSaveResult()
        assertNull(vm.saveResult.value)
    }

    @Test
    fun `sharePhoto reports the exception message through onError`() = runTest {
        coEvery { photoExport.sharePhoto(any(), any()) } throws RuntimeException("share sheet gone")
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        var reportedError: String? = null
        vm.sharePhoto { reportedError = it }
        advanceUntilIdle()

        assertEquals("share sheet gone", reportedError)
    }

    @Test
    fun `sharePhoto does not invoke onError when the export succeeds`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        var invoked = false
        vm.sharePhoto { invoked = true }
        advanceUntilIdle()

        assertFalse(invoked)
        coVerify(exactly = 1) { photoExport.sharePhoto("https://example.com/image.jpg", "Photo 2") }
    }

    @Test
    fun `canExportPhotos mirrors PhotoExport isSupported`() {
        every { photoExport.isSupported } returns true
        assertTrue(createViewModel().canExportPhotos)

        every { photoExport.isSupported } returns false
        assertFalse(createViewModel().canExportPhotos)
    }

    // ── image URLs ──────────────────────────────────────────────────────────

    @Test
    fun `getFullImageUrl returns null without a loaded photo`() = runTest {
        val vm = createViewModel()

        assertNull(vm.getFullImageUrl())
        verify(exactly = 0) { imageUrlProvider.getImageUrl(any(), any()) }
    }

    @Test
    fun `getFullImageUrl requests the unconstrained full-size image`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        assertEquals("https://example.com/image.jpg", vm.getFullImageUrl())
        verify(exactly = 1) { imageUrlProvider.getImageUrl("p2", null) }
    }

    @Test
    fun `getThumbnailUrl requests a 200px thumbnail`() {
        val vm = createViewModel()

        assertEquals("https://example.com/image.jpg", vm.getThumbnailUrl("p1"))
        verify(exactly = 1) { imageUrlProvider.getImageUrl("p1", 200) }
    }
}
