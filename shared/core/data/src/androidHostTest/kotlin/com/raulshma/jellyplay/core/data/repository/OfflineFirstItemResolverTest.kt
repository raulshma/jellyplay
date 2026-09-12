package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pins the fallback ordering of the offline-first resolution that used to live
 * inline in HomeViewModel (`resolveSyncMedia`): offline row → ONLINE-guarded
 * network lookup → id-derived URL with a null item. The offline-skip case
 * asserts the network call never fires while offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstItemResolverTest {

    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private val offlineMode = MutableStateFlow<OfflineMode>(OfflineMode.ONLINE)

    private lateinit var resolver: OfflineFirstItemResolver

    @Before
    fun setUp() {
        every { offlineModeManager.offlineMode } returns offlineMode
        every { imageUrlProvider.getImageUrl(any<String>()) } returns "http://server/img"
        resolver = OfflineFirstItemResolverImpl(
            offlineRepository = offlineRepository,
            mediaRepository = mediaRepository,
            offlineModeManager = offlineModeManager,
            imageUrlProvider = imageUrlProvider,
        )
    }

    @Test
    fun `offline hit returns the adapted item with its local poster path`() = runTest {
        val offlineItem = OfflineMediaItem(
            id = "item-1",
            name = "Offline Movie",
            mediaType = MediaType.MOVIE,
            posterPath = "file:///offline/poster.jpg",
        )
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem

        val ref = resolver.resolveMediaRef("item-1")

        assertEquals("Offline Movie", ref.item?.name)
        // Offline hit must prefer the local poster path over the server URL.
        assertEquals("file:///offline/poster.jpg", ref.posterUrl)
        // Network fallback must not fire when the offline store had the row.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail("item-1") }
    }

    @Test
    fun `offline miss while online falls back to getMediaDetail`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-2") } returns null
        coEvery { mediaRepository.getMediaDetail("item-2") } returns Result.success(
            MediaDetail(item = MediaItem(id = "item-2", name = "Online Only", mediaType = MediaType.MOVIE))
        )

        val ref = resolver.resolveMediaRef("item-2")

        assertEquals("Online Only", ref.item?.name)
        assertEquals("http://server/img", ref.posterUrl)
    }

    @Test
    fun `offline miss while offline skips the network call entirely`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-3") } returns null
        offlineMode.value = OfflineMode.OFFLINE_MANUAL

        val ref = resolver.resolveMediaRef("item-3")

        // Resolves to the not-found marker (null item) with a server URL so the
        // row can still attempt to load it once back online.
        assertNull(ref.item)
        assertEquals("http://server/img", ref.posterUrl)
        // The guaranteed-failing network call must never fire while offline.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun `both-miss online returns the id-derived url with a null item`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-4") } returns null
        coEvery { mediaRepository.getMediaDetail("item-4") } returns Result.failure(RuntimeException("net"))

        val ref = resolver.resolveMediaRef("item-4")

        assertNull(ref.item)
        assertEquals("http://server/img", ref.posterUrl)
    }
}
