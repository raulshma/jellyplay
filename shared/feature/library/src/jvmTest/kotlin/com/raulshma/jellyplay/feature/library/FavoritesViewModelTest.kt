package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Delegation assertion (plan 03): Favorites' mark-played is intentionally
 * silent — the paged grid keeps the user's scroll position — so the whole
 * contract is "routes through the mutator with the silent defaults". The
 * mutation protocol itself is pinned by UserDataMutatorTest in :core:data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var mediaDownloadActions: MediaDownloadActions
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: FavoritesViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        mediaDownloadActions = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        viewModel = FavoritesViewModel(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            imageUrlProvider = imageUrlProvider,
            mediaDownloadActions = mediaDownloadActions,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `markItemPlayed delegates to the mutator silently`() = runTest {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.markItemPlayed(item, played = true)
        advanceUntilIdle()

        coVerify {
            userDataMutator.setPlayed("m1", true, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    @Test
    fun `downloadItem routes through the shared download actions`() = runTest {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.downloadItem(item, onOpenDetail = { })
        advanceUntilIdle()

        coVerify { mediaDownloadActions.downloadAndReport(item, any()) }
    }

    @Test
    fun `removeItemDownload delegates to the shared download actions`() = runTest {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.removeItemDownload(item)
        advanceUntilIdle()

        verify { mediaDownloadActions.removeDownload(item) }
    }

    // ── Media-type filter → paged query ──────────────────────────────────────

    /** Idle-states PagingData so a collector (and [first]) sees a generation. */
    private fun stubFavoritesPaged() {
        val idleStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = false),
            append = LoadState.NotLoading(endOfPaginationReached = false),
        )
        every { mediaRepository.getFavoritesPaged(any()) } answers {
            flowOf(PagingData.from(emptyList<MediaItem>(), idleStates))
        }
    }

    @Test
    fun `setMediaTypeFilter re-queries favorites paged with the requested type`() = runTest {
        stubFavoritesPaged()

        viewModel.setMediaTypeFilter(MediaType.MOVIE)
        val firstPage = async { viewModel.pagedItems.first() }
        advanceUntilIdle()

        assertTrue(firstPage.isCompleted)
        coVerify(exactly = 1) {
            mediaRepository.getFavoritesPaged(mediaTypes = listOf(MediaType.MOVIE))
        }
    }

    @Test
    fun `clearing the media-type filter queries unscoped favorites`() = runTest {
        stubFavoritesPaged()

        viewModel.setMediaTypeFilter(MediaType.SERIES)
        viewModel.setMediaTypeFilter(null)
        val firstPage = async { viewModel.pagedItems.first() }
        advanceUntilIdle()

        assertTrue(firstPage.isCompleted)
        coVerify(exactly = 1) { mediaRepository.getFavoritesPaged(mediaTypes = null) }
    }

    @Test
    fun `mediaTypeFilter exposes the current selection`() = runTest {
        assertEquals(null, viewModel.mediaTypeFilter.first())

        viewModel.setMediaTypeFilter(MediaType.MOVIE)
        assertEquals(MediaType.MOVIE, viewModel.mediaTypeFilter.first())
    }

    // ── Photo-folder child-url prefetch ──────────────────────────────────────

    @Test
    fun `prefetchPhotoFolderChildUrls fetches only photo folders and merges results`() = runTest {
        val folder = MediaItem(id = "pf-1", name = "Folder", mediaType = MediaType.PHOTO_FOLDER)
        val movie = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getPhotoFolderChildImageUrls("pf-1") } returns listOf("u1", "u2")

        viewModel.prefetchPhotoFolderChildUrls(listOf(folder, movie))
        advanceUntilIdle()

        // Only the photo folder is fetched — regular items never hit the seam.
        coVerify(exactly = 1) { mediaRepository.getPhotoFolderChildImageUrls("pf-1") }
        coVerify(exactly = 0) { mediaRepository.getPhotoFolderChildImageUrls("m1") }
        assertEquals(mapOf("pf-1" to listOf("u1", "u2")), viewModel.photoFolderChildUrls.value)
    }

    @Test
    fun `prefetchPhotoFolderChildUrls skips folders already in the map`() = runTest {
        val folder = MediaItem(id = "pf-1", name = "Folder", mediaType = MediaType.PHOTO_FOLDER)
        coEvery { mediaRepository.getPhotoFolderChildImageUrls("pf-1") } returns listOf("u1")

        viewModel.prefetchPhotoFolderChildUrls(listOf(folder))
        advanceUntilIdle()
        viewModel.prefetchPhotoFolderChildUrls(listOf(folder))
        advanceUntilIdle()

        // Recomposition re-fires the prefetch; the already-fetched folder is
        // not re-fetched and the merged state keeps the original urls.
        coVerify(exactly = 1) { mediaRepository.getPhotoFolderChildImageUrls("pf-1") }
        assertEquals(mapOf("pf-1" to listOf("u1")), viewModel.photoFolderChildUrls.value)
    }

    @Test
    fun `prefetchPhotoFolderChildUrls is a no-op without photo folders`() = runTest {
        val movie = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.prefetchPhotoFolderChildUrls(listOf(movie))
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.getPhotoFolderChildImageUrls(any()) }
        assertEquals(emptyMap(), viewModel.photoFolderChildUrls.value)
    }

    // ── Image url delegation ─────────────────────────────────────────────────

    @Test
    fun `getImageUrl delegates to the provider`() {
        every { imageUrlProvider.getImageUrl("i1") } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
        verify(exactly = 1) { imageUrlProvider.getImageUrl("i1") }
    }
}
