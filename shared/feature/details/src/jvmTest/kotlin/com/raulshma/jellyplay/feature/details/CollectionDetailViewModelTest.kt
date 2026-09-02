package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import org.junit.Assert.assertSame
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    // Legacy :core:testing MainDispatcherRule, inlined (conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: FakeUserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: CollectionDetailViewModel

    @BeforeTest
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        userDataMutator = FakeUserDataMutator()
        imageUrlProvider = mockk(relaxed = true)
        every { imageUrlProvider.getImageUrl(any()) } returns "img"
        every { imageUrlProvider.getBackdropUrl(any()) } returns "backdrop"

        viewModel = CollectionDetailViewModel(
            mediaRepository,
            userDataMutator,
            imageUrlProvider,
            mockk<com.raulshma.jellyplay.core.data.download.MediaDownloadActions>(relaxed = true),
        )
    }

    @Test
    fun `loadCollection success emits Success with detail and items`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val detail = MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION))
        val items = listOf(
            MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE),
            MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE),
        )
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(detail)
        coEvery { mediaRepository.getCollectionItems("c1", any(), any()) } returns Result.success(
            SearchResult(items = items, totalRecordCount = 2, startIndex = 0),
        )

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollectionDetailUiState.Success)
        val success = state as CollectionDetailUiState.Success
        assertEquals("c1", success.detail.item.id)
        assertEquals(items, success.items)
    }

    @Test
    fun `loadCollection detail failure emits Error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.failure(RuntimeException("boom"))
        coEvery { mediaRepository.getCollectionItems("c1", any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollectionDetailUiState.Error)
        assertEquals("boom", (state as CollectionDetailUiState.Error).message)
    }

    @Test
    fun `loadCollection items failure surfaces error even if detail succeeded`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any()) } returns Result.failure(RuntimeException("items boom"))

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollectionDetailUiState.Error)
        assertEquals("items boom", (state as CollectionDetailUiState.Error).message)
    }

    @Test
    fun `loadCollection resets to Loading before resolving`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        // No stubs → relaxed mock returns success(null) which getOrThrow would fail;
        // stub a slow-ish path so we can observe the initial Loading emission.
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadCollection("c1")

        assertEquals(CollectionDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `getImageUrl and getBackdropUrl delegate to ImageUrlProvider`() {
        viewModel.getImageUrl("x")
        viewModel.getBackdropUrl("y")

        io.mockk.verify(exactly = 1) { imageUrlProvider.getImageUrl("x") }
        io.mockk.verify(exactly = 1) { imageUrlProvider.getBackdropUrl("y") }
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(CollectionDetailUiState.Loading, viewModel.uiState.value)
    }

    /**
     * The one container-adapter test (plan 03): a successful optimistic
     * mutation flips ONLY the matching item — resume zeroed by the resolved
     * patch — and leaves non-matching items referentially equal.
     */
    @Test
    fun `markItemPlayed flips only the matching item in the success items`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val withProgress = MediaItem(
            id = "m1",
            name = "Movie 1",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 5_000_000_000L,
        )
        val untouched = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any()) } returns Result.success(
            SearchResult(items = listOf(withProgress, untouched), totalRecordCount = 2, startIndex = 0),
        )
        viewModel.loadCollection("c1")
        advanceUntilIdle()

        viewModel.markItemPlayed(withProgress, played = true)
        advanceUntilIdle()

        assertEquals(listOf(Triple("m1", true, null as String?)), userDataMutator.playedCalls)
        val items = (viewModel.uiState.value as CollectionDetailUiState.Success).items
        assertTrue(items.first { it.id == "m1" }.isPlayed)
        assertEquals(0L, items.first { it.id == "m1" }.playbackPositionTicks)
        assertSame(untouched, items.last())
    }

    @Test
    fun `failure with null message falls back to generic error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.failure(RuntimeException())
        coEvery { mediaRepository.getCollectionItems("c1", any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as CollectionDetailUiState.Error
        assertEquals("Failed to load collection", state.message)
    }
}
