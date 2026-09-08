package com.raulshma.jellyplay.feature.library

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StudioDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var mediaDownloadActions: MediaDownloadActions

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        mediaDownloadActions = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        every { mediaDownloadActions.downloadedIds } returns MutableStateFlow(emptySet())
        // The deferred refresher collects this for the whole VM lifetime.
        every { mediaRepository.userDataChanges } returns MutableSharedFlow(extraBufferCapacity = 16)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        studioId: String = "studio-1",
        studioName: String = "Test Studio",
    ): StudioDetailViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Route.StudioDetail::studioId.name to studioId,
                Route.StudioDetail::studioName.name to studioName,
            )
        )
        return StudioDetailViewModel(
            savedStateHandle = savedStateHandle,
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            imageUrlProvider = imageUrlProvider,
            mediaDownloadActions = mediaDownloadActions,
        )
    }

    @Test
    fun `items flow calls getMediaItemsPaged with correct studioIds`() = runTest {
        val expectedStudioId = "studio-123"
        val viewModel = createViewModel(studioId = expectedStudioId)
        // The pager flows through flatMapLatest (deferred-refresh keying), so
        // getMediaItemsPaged fires on first collection, not at construction.
        val collector = launch { viewModel.items.collect {} }
        advanceUntilIdle()

        verify {
            mediaRepository.getMediaItemsPaged(
                studioIds = listOf(expectedStudioId),
                filters = match { it.sortBy == SortOption.SORT_NAME },
            )
        }
        collector.cancel()
    }

    @Test
    fun `getImageUrl delegates to imageUrlProvider with correct params`() {
        val viewModel = createViewModel()
        val imageUrl = viewModel.getImageUrl("item-1")

        verify { imageUrlProvider.getImageUrl("item-1") }
        assertEquals("https://example.com/image.jpg", imageUrl)
    }

    @Test
    fun `viewModel extracts studioId from savedStateHandle`() = runTest {
        val viewModel = createViewModel(studioId = "my-studio-id")
        val collector = launch { viewModel.items.collect {} }
        advanceUntilIdle()

        verify {
            mediaRepository.getMediaItemsPaged(
                studioIds = listOf("my-studio-id"),
                filters = any(),
            )
        }
        collector.cancel()
    }

    @Test
    fun `viewModel uses default sort by SortName`() = runTest {
        val viewModel = createViewModel()
        val collector = launch { viewModel.items.collect {} }
        advanceUntilIdle()

        verify {
            mediaRepository.getMediaItemsPaged(
                studioIds = any(),
                filters = match { it.sortBy == SortOption.SORT_NAME },
            )
        }
        collector.cancel()
    }

    /** Delegation one-liner (plan 03): silent grid mutations route through the mutator. */
    @Test
    fun `markItemPlayed delegates to the mutator silently`() = runTest {
        val viewModel = createViewModel()
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.markItemPlayed(item, played = false)
        advanceUntilIdle()

        coVerify {
            userDataMutator.setPlayed("m1", false, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
    }

    // ── Download quick actions (studio grid) ─────────────────────────────────

    @Test
    fun `downloadItem routes through downloadAndReport with the plain open-detail callback`() = runTest {
        val viewModel = createViewModel()
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)
        var routedTo: String? = null

        // The relaxed mock swallows downloadAndReport without ever invoking the
        // callback. Mirror the real cascade (NeedsDetailScreen → onOpenDetail
        // with the plain item id) so the routing actually happens.
        coEvery { mediaDownloadActions.downloadAndReport(any(), any()) } coAnswers {
            secondArg<(String) -> Unit>()(firstArg<MediaItem>().id)
        }

        viewModel.downloadItem(item) { routedTo = it }
        advanceUntilIdle()

        // This host has no pre-presented series sheet: the callback passes the
        // item id straight through to the shared report-and-route cascade.
        coVerify(exactly = 1) { mediaDownloadActions.downloadAndReport(item, any()) }
        assertEquals("m1", routedTo)
    }

    @Test
    fun `removeItemDownload delegates to the shared delete routing`() = runTest {
        val viewModel = createViewModel()
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.removeItemDownload(item)

        verify(exactly = 1) { mediaDownloadActions.removeDownload(item) }
    }

    @Test
    fun `downloadedIds re-exposes the shared actions flow`() = runTest {
        every { mediaDownloadActions.downloadedIds } returns MutableStateFlow(setOf("done-1"))

        val viewModel = createViewModel()

        assertEquals(setOf("done-1"), viewModel.downloadedIds.value)
    }
}
