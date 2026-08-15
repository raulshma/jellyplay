package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: FakeUserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: PersonDetailViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        userDataMutator = FakeUserDataMutator()
        imageUrlProvider = mockk(relaxed = true)
        viewModel = PersonDetailViewModel(mediaRepository, userDataMutator, imageUrlProvider)
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(PersonDetailUiState.Loading, viewModel.uiState.value)
    }

    /**
     * The one container-adapter test (plan 03): a successful optimistic
     * mutation flips ONLY the matching filmography item — resume zeroed by the
     * resolved patch — and leaves non-matching items referentially equal.
     */
    @Test
    fun `markItemPlayed flips only the matching item in the filmography`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "An actor.")
        val withProgress = MediaItem(
            id = "m1",
            name = "Movie 1",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 5_000_000_000L,
        )
        val untouched = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { mediaRepository.getItemsByPerson("p1", any()) } returns Result.success(listOf(withProgress, untouched))

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        viewModel.markItemPlayed(withProgress, played = true)
        advanceUntilIdle()

        assertEquals(listOf(Triple("m1", true, null)), userDataMutator.playedCalls)
        val filmography = (viewModel.uiState.value as PersonDetailUiState.Success).filmography
        assertTrue(filmography.first { it.id == "m1" }.isPlayed)
        assertEquals(0L, filmography.first { it.id == "m1" }.playbackPositionTicks)
        assertSame(untouched, filmography.last())
    }

    @Test
    fun `loadPerson success emits Success with name filmography biography and profile image`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "An actor.")
        val filmography = listOf(
            MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE),
        )
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { mediaRepository.getItemsByPerson("p1") } returns Result.success(filmography)
        every { imageUrlProvider.getImageUrl("p1") } returns "profile-url"

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PersonDetailUiState.Success)
        val success = state as PersonDetailUiState.Success
        assertEquals("Person One", success.name)
        assertEquals(filmography, success.filmography)
        assertEquals("An actor.", success.biography)
        assertEquals("profile-url", success.profileImageUrl)
    }

    @Test
    fun `loadPerson null biography is not emitted`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "")
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { mediaRepository.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertNull(success.biography)
    }

    @Test
    fun `loadPerson blank profile url is not emitted`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "bio")
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { mediaRepository.getItemsByPerson("p1") } returns Result.success(emptyList())
        every { imageUrlProvider.getImageUrl("p1") } returns ""

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertNull(success.profileImageUrl)
    }

    @Test
    fun `loadPerson detail failure emits Error`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException("detail boom"))
        coEvery { mediaRepository.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PersonDetailUiState.Error)
        assertEquals("detail boom", (state as PersonDetailUiState.Error).message)
    }

    @Test
    fun `loadPerson items failure emits Error preferring items message`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException("detail boom"))
        coEvery { mediaRepository.getItemsByPerson("p1") } returns Result.failure(RuntimeException("items boom"))

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        // itemsError is preferred (first in the ?: chain) when both fail.
        assertEquals("items boom", (viewModel.uiState.value as PersonDetailUiState.Error).message)
    }

    @Test
    fun `loadPerson failure with null messages falls back to generic error`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException())
        coEvery { mediaRepository.getItemsByPerson("p1") } returns Result.failure(RuntimeException())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        assertEquals("Failed to load", (viewModel.uiState.value as PersonDetailUiState.Error).message)
    }

    @Test
    fun `getImageUrl delegates to ImageUrlProvider`() {
        viewModel.getImageUrl("p1")
        io.mockk.verify(exactly = 1) { imageUrlProvider.getImageUrl("p1") }
    }
}
