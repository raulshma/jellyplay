@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.photos

import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

    private lateinit var detailFeed: DetailFeed
    private lateinit var libraryApiClient: LibraryApiClient
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoExport: PhotoExport

    private val photo1 = MediaItem(id = "p1", name = "Photo 1", mediaType = MediaType.PHOTO)
    private val photo2 = MediaItem(id = "p2", name = "Photo 2", mediaType = MediaType.PHOTO)
    private val photo3 = MediaItem(id = "p3", name = "Photo 3", mediaType = MediaType.PHOTO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        detailFeed = mockk(relaxed = true)

        libraryApiClient = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        photoExport = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        // The relaxed mock's default Result would ClassCast inside the VM's
        // .getOrNull() — stub every detail read with a real Result (relaxed
        // slideshow navigations included).
        coEvery { detailFeed.getMediaDetail(any(), force = false) } returns
            Result.success(MediaDetail(item = photo1))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PhotoViewerViewModel = PhotoViewerViewModel(
        detailFeed = detailFeed,
        libraryApiClient = libraryApiClient,
        imageUrlProvider = imageUrlProvider,
        artworkUrls = ArtworkUrls.EMPTY,
        photoExport = photoExport,
    )

    /** Three-photo album the viewer can page through via parentId siblings. */
    private fun stubAlbumSiblings(parentId: String = "album-1") {
        coEvery {
            libraryApiClient.getMediaItems(parentId = parentId, filters = any(), limit = PHOTO_SIBLINGS_WINDOW)
        } returns Result.success(
            SearchResult(items = listOf(photo1, photo2, photo3), totalRecordCount = 3, startIndex = 0)
        )
        coEvery { detailFeed.getMediaDetail(photo1.id, force = false) } returns Result.success(MediaDetail(item = photo1))
        coEvery { detailFeed.getMediaDetail(photo2.id, force = false) } returns Result.success(MediaDetail(item = photo2))
        coEvery { detailFeed.getMediaDetail(photo3.id, force = false) } returns Result.success(MediaDetail(item = photo3))
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

        assertEquals(photo2, vm.state.value.photo)
        assertEquals(photo2, vm.state.value.photoDetail?.item)
        assertEquals(listOf(photo1, photo2, photo3), vm.state.value.siblings)
        assertEquals(1, vm.state.value.currentIndex)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `load queries the first strip window as photos`() = runTest {
        // The album strip must only ever contain photos, regardless of what
        // else lives in the parent folder — and the initial window is a single
        // startIndex-0 page (first paint stays one round trip).
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        coVerify(exactly = 1) {
            libraryApiClient.getMediaItems(
                parentId = "album-1",
                filters = LibraryFilters(mediaTypes = listOf(MediaType.PHOTO)),
                startIndex = 0,
                limit = PHOTO_SIBLINGS_WINDOW,
            )
        }
    }

    @Test
    fun `load pages the strip forward until the opened photo is found`() = runTest {
        // The opened photo sits past the first window: discovery walks the
        // strip's stable sort page by page until it resolves.
        val pageOne = List(PHOTO_SIBLINGS_WINDOW) { i -> MediaItem(id = "early-$i", name = "Early $i", mediaType = MediaType.PHOTO) }
        val target = MediaItem(id = "deep", name = "Deep", mediaType = MediaType.PHOTO)
        val pageTwo = listOf(target)
        coEvery {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = 0, limit = any())
        } returns Result.success(
            SearchResult(items = pageOne, totalRecordCount = PHOTO_SIBLINGS_WINDOW + 1, startIndex = 0)
        )
        coEvery {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = PHOTO_SIBLINGS_WINDOW, limit = any())
        } returns Result.success(
            SearchResult(items = pageTwo, totalRecordCount = PHOTO_SIBLINGS_WINDOW + 1, startIndex = PHOTO_SIBLINGS_WINDOW)
        )
        coEvery { detailFeed.getMediaDetail("deep", force = false) } returns Result.success(MediaDetail(item = target))

        val vm = createViewModel()
        vm.load(itemId = "deep", parentId = "album-1")
        advanceUntilIdle()

        assertEquals(PHOTO_SIBLINGS_WINDOW + 1, vm.state.value.siblings.size)
        assertEquals(target, vm.state.value.photo)
        assertEquals(PHOTO_SIBLINGS_WINDOW, vm.state.value.currentIndex)
        assertEquals(PHOTO_SIBLINGS_WINDOW + 1, vm.state.value.totalSiblings)
    }

    @Test
    fun `load degrades to the pages that resolved when a page fails`() = runTest {
        val pageOne = List(PHOTO_SIBLINGS_WINDOW) { i -> MediaItem(id = "early-$i", name = "Early $i", mediaType = MediaType.PHOTO) }
        coEvery {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = 0, limit = any())
        } returns Result.success(
            SearchResult(items = pageOne, totalRecordCount = 400, startIndex = 0)
        )
        coEvery {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = PHOTO_SIBLINGS_WINDOW, limit = any())
        } returns Result.failure(RuntimeException("flaky page"))

        val vm = createViewModel()
        vm.load(itemId = "missing-from-loaded", parentId = "album-1")
        advanceUntilIdle()

        // The strip keeps the resolved page; the opened photo stays current
        // (index 0) and the error surfaces.
        assertEquals(PHOTO_SIBLINGS_WINDOW, vm.state.value.siblings.size)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.photo != null)
    }

    @Test
    fun `navigating near the strip end widens the strip by one window`() = runTest {
        coEvery {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = 0, limit = any())
        } returns Result.success(
            SearchResult(items = listOf(photo1, photo2, photo3), totalRecordCount = 250, startIndex = 0)
        )
        // The first widening page: a full window past the loaded strip, which
        // pushes the loaded end beyond the widen margin (index + 25).
        val widenedPage = List(PHOTO_SIBLINGS_WINDOW) { i ->
            MediaItem(id = "later-$i", name = "Later $i", mediaType = MediaType.PHOTO)
        }
        coEvery {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = 3, limit = any())
        } returns Result.success(
            SearchResult(items = widenedPage, totalRecordCount = 250, startIndex = 3)
        )
        coEvery { detailFeed.getMediaDetail(photo3.id, force = false) } returns Result.success(MediaDetail(item = photo3))

        val vm = createViewModel()
        vm.load(itemId = "p3", parentId = "album-1")
        advanceUntilIdle()
        // The opened photo sits at index 2 of a 3-item first window — within
        // PHOTO_SIBLINGS_WIDEN_MARGIN of the loaded end, so the load's own
        // widening pass appends the next window immediately.
        assertEquals(3 + PHOTO_SIBLINGS_WINDOW, vm.state.value.siblings.size)
        assertEquals(photo1, vm.state.value.siblings.first())
        assertEquals(250, vm.state.value.totalSiblings)

        // Navigating inside the (now wider) loaded area — past the widen
        // margin, no further fetch fires.
        vm.navigateTo(1)
        advanceUntilIdle()

        assertEquals(3 + PHOTO_SIBLINGS_WINDOW, vm.state.value.siblings.size)
        coVerify(exactly = 1) {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = 0, limit = any())
        }
        coVerify(exactly = 1) {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = 3, limit = any())
        }
    }

    @Test
    fun `widening is skipped once the strip covers the album total`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        // totalSiblings = 3 and the strip already holds 3 — no further fetch.
        vm.navigateTo(2)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            libraryApiClient.getMediaItems(parentId = "album-1", filters = any(), startIndex = any(), limit = any())
        }
    }

    @Test
    fun `load without parentId falls back to a single-item sibling list at index 0`() = runTest {
        val vm = createViewModel()
        stubAlbumSiblings()
        vm.load(itemId = "p2", parentId = null)
        advanceUntilIdle()

        assertEquals(photo2, vm.state.value.photo)
        assertEquals(listOf(photo2), vm.state.value.siblings)
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun `load failure surfaces the error message and keeps photo null`() = runTest {
        coEvery { detailFeed.getMediaDetail("broken", force = false) } returns
            Result.failure(RuntimeException("server exploded"))

        val vm = createViewModel()
        vm.load(itemId = "broken", parentId = "album-1")
        advanceUntilIdle()

        assertEquals("server exploded", vm.state.value.error)
        assertNull(vm.state.value.photo)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `load failure with a null exception message falls back to a default`() = runTest {
        coEvery { detailFeed.getMediaDetail("broken", force = false) } returns Result.failure(RuntimeException())

        val vm = createViewModel()
        vm.load(itemId = "broken", parentId = null)
        advanceUntilIdle()

        assertEquals("Failed to load photo", vm.state.value.error)
    }

    // ── navigation ──────────────────────────────────────────────────────────

    @Test
    fun `navigateTo moves to a valid index and fetches the new photo's detail`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.navigateTo(2)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.currentIndex)
        assertEquals(photo3, vm.state.value.photo)
        coVerify { detailFeed.getMediaDetail("p3", force = false) }
    }

    @Test
    fun `navigateTo ignores out-of-bounds indices`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        vm.navigateTo(-1)
        vm.navigateTo(3)
        advanceUntilIdle()

        // Still on photo2 (index 1), no detail re-fetch for an out-of-range id.
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals(photo2, vm.state.value.photo)
        coVerify(exactly = 0) { detailFeed.getMediaDetail("p1", force = false) }
    }

    @Test
    fun `a superseded detail fetch is cancelled and cannot publish its result`() = runTest {
        val vm = createViewModel()
        loadViewerInAlbum(vm)

        // Park the first navigation's detail fetch mid-flight.
        val gate = CompletableDeferred<Unit>()
        coEvery { detailFeed.getMediaDetail("p1", force = false) } coAnswers { gate.await(); Result.success(MediaDetail(item = photo1)) }
        vm.navigateTo(0)
        runCurrent() // the p1 fetch is now parked inside the detail read

        // The second navigation supersedes it: the parked fetch is cancelled
        // before the new one launches.
        vm.navigateTo(2)
        advanceUntilIdle()

        assertEquals(photo3, vm.state.value.photoDetail?.item)
        gate.complete(Unit)
        advanceUntilIdle()
        // The late-completing superseded fetch never publishes over photo3.
        assertEquals(photo3, vm.state.value.photoDetail?.item)
    }

    @Test
    fun `requestOriginalResolution is idempotent and accumulates per photo`() = runTest {
        val vm = createViewModel()

        vm.requestOriginalResolution("p1")
        vm.requestOriginalResolution("p1")
        vm.requestOriginalResolution("p2")

        assertEquals(setOf("p1", "p2"), vm.originalResolutionIds.value)
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
        assertEquals(1, vm.state.value.currentIndex)

        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(2, vm.state.value.currentIndex)

        // Past the last sibling the slideshow wraps back to the first photo.
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(0, vm.state.value.currentIndex)

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

        val indexAfterStop = vm.state.value.currentIndex
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        // No further ticks after the cancel — the index never moves again.
        assertEquals(indexAfterStop, vm.state.value.currentIndex)
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
        assertEquals(2, vm.state.value.currentIndex)

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
