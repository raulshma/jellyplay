package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pins [OfflineFirstItemResolverImpl]'s three-branch resolution:
 *  1. an offline (downloaded) row always wins, and its stored poster path is
 *     used verbatim (a local file path, not a server URL);
 *  2. an online-only item falls back to `detailFeed.getMediaDetail` —
 *     but ONLY while the device is online (offline, the guaranteed-failing
 *     call is skipped);
 *  3. when nothing resolves anywhere, a null item ref with the server poster
 *     URL is still returned (callers render the poster row).
 */
class OfflineFirstItemResolverImplTest {

    private lateinit var offlineRepository: OfflineRepository
    private lateinit var detailFeed: DetailFeed
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var resolver: OfflineFirstItemResolverImpl

    @BeforeTest
    fun setup() {
        offlineRepository = mockk()
        detailFeed = mockk()
        offlineModeManager = mockk()
        imageUrlProvider = mockk()
        every { offlineModeManager.offlineMode } returns MutableStateFlow(OfflineMode.ONLINE)
        every { imageUrlProvider.getImageUrl(any()) } returns "https://server/Items/$ITEM_ID/Images/Primary"
        resolver = OfflineFirstItemResolverImpl(
            offlineRepository = offlineRepository,
            detailFeed = detailFeed,
            offlineModeManager = offlineModeManager,
            imageUrlProvider = imageUrlProvider,
        )
    }

    @Test
    fun `an offline row wins and its stored poster path is used verbatim`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            OfflineMediaItem(id = ITEM_ID, name = "Movie", mediaType = MediaType.MOVIE, posterPath = "/data/posters/i1.jpg")

        val ref = resolver.resolveMediaRef(ITEM_ID)

        assertNotNull(ref.item)
        assertEquals("/data/posters/i1.jpg", ref.posterUrl)
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), any()) }
    }

    @Test
    fun `an offline row without a poster falls back to the server URL`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            OfflineMediaItem(id = ITEM_ID, name = "Movie", mediaType = MediaType.MOVIE)

        val ref = resolver.resolveMediaRef(ITEM_ID)

        assertNotNull(ref.item)
        assertEquals("https://server/Items/$ITEM_ID/Images/Primary", ref.posterUrl)
    }

    @Test
    fun `an online-only item resolves through the media repository`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns null
        coEvery { detailFeed.getMediaDetail(ITEM_ID, force = false) } returns Result.success(
            detailFixture(),
        )

        val ref = resolver.resolveMediaRef(ITEM_ID)

        assertNotNull(ref.item)
        assertEquals("Online Movie", ref.item!!.name)
    }

    @Test
    fun `a failed online lookup still yields a poster-only ref`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns null
        coEvery { detailFeed.getMediaDetail(ITEM_ID, force = false) } returns
            Result.failure(java.io.IOException("gone"))

        val ref = resolver.resolveMediaRef(ITEM_ID)

        assertNull(ref.item)
        assertEquals("https://server/Items/$ITEM_ID/Images/Primary", ref.posterUrl)
    }

    @Test
    fun `an offline device never issues the online fallback call`() = runTest {
        every { offlineModeManager.offlineMode } returns MutableStateFlow(OfflineMode.OFFLINE_MANUAL)
        every { offlineModeManager.isOffline } returns true
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns null

        val ref = resolver.resolveMediaRef(ITEM_ID)

        assertNull(ref.item)
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), any()) }
    }

    private fun detailFixture() = com.raulshma.jellyplay.core.model.MediaDetail(
        item = com.raulshma.jellyplay.core.model.MediaItem(
            id = ITEM_ID,
            name = "Online Movie",
            mediaType = MediaType.MOVIE,
        ),
    )

    private companion object {
        const val ITEM_ID = "item-1"
    }
}
