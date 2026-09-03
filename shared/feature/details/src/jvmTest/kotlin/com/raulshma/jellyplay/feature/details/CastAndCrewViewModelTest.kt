package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastAndCrewViewModelTest {

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
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: CastAndCrewViewModel

    @BeforeTest
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        viewModel = CastAndCrewViewModel(mediaRepository, imageUrlProvider)
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(CastAndCrewUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `load success partitions cast and crew`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(
                item = person,
                people = listOf(
                    PersonInfo(id = "p1", name = "Actor", type = "Actor"),
                    PersonInfo(id = "p2", name = "Director", type = "Director"),
                ),
            ),
        )

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CastAndCrewUiState.Success)
        val success = state as CastAndCrewUiState.Success
        assertEquals("Movie 1", success.title)
        assertEquals(listOf("p1"), success.cast.map { it.id })
        assertEquals(listOf("p2"), success.crew.map { it.id })
    }

    @Test
    fun `load failure emits Error and calls the repository exactly once`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException("boom"))

        viewModel.load("m1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CastAndCrewUiState.Error)
        // Retry is owned by the data layer; this layer must not re-issue calls.
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("m1") }
    }

    @Test
    fun `load failure with null message falls back to the generic error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(IllegalStateException())

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CastAndCrewUiState.Error)
        assertEquals("Failed to load", (state as CastAndCrewUiState.Error).message)
    }

    @Test
    fun `load resets a prior Success state to Loading before resolving`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail(any()) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE),
                people = listOf(PersonInfo(id = "p1", name = "Actor", type = "Actor")),
            ),
        )

        viewModel.load("m1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CastAndCrewUiState.Success)

        // A second load for another item must flip back to Loading first — the
        // previous title's cast must never bleed into the new screen.
        val gate = kotlinx.coroutines.CompletableDeferred<Result<MediaDetail>>()
        coEvery { mediaRepository.getMediaDetail("m2") } coAnswers { gate.await() }
        viewModel.load("m2")
        advanceUntilIdle()

        assertEquals(CastAndCrewUiState.Loading, viewModel.uiState.value)

        gate.complete(
            Result.success(
                MediaDetail(
                    item = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE),
                    people = emptyList(),
                ),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CastAndCrewUiState.Success)
        assertEquals("Movie 2", (state as CastAndCrewUiState.Success).title)
    }

    @Test
    fun `getImageUrl delegates to the injected provider`() {
        every { imageUrlProvider.getImageUrl("p1") } returns "http://img/p1"

        assertEquals("http://img/p1", viewModel.getImageUrl("p1"))
    }
}
