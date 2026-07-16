package com.raulshma.jellyplay.feature.livetv.channeldetail

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: ChannelDetailViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        // getImageUrl is a non-suspend fun → stub with `every`, not `coEvery`.
        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://img/channel"
        every { imageUrlProvider.getImageUrl("prog-1", any()) } returns "https://img/prog"
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList())
        viewModel = ChannelDetailViewModel(mediaRepository, imageUrlProvider)
    }

    @Test
    fun `loadChannel populates channel meta and today programs`() = runTest {
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns Result.success(
            listOf(
                LiveTvChannel(id = "chan-1", name = "BBC One", number = "101", imageTag = "tag-1")
            )
        )
        val now = java.time.OffsetDateTime.now()
        val airing = program(
            id = "p-now", name = "Evening News",
            start = now.minusMinutes(10), end = now.plusMinutes(20),
        )
        val next = program(
            id = "p-next", name = "Late Film",
            start = now.plusMinutes(20), end = now.plusMinutes(140),
        )
        coEvery { mediaRepository.getLiveTvPrograms("chan-1", any(), any()) } returns Result.success(listOf(airing, next))

        viewModel.loadChannel("chan-1", "BBC One")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("chan-1", state.channelId)
        assertEquals("BBC One", state.channelName)
        assertEquals("101", state.channelNumber)
        assertEquals("https://img/channel", state.channelLogoUrl)
        assertNull(state.error)
        assertEquals(2, state.programs.size)
        // Airing program resolved as currentProgram.
        assertNotNull(state.currentProgram)
        assertEquals("p-now", state.currentProgram?.id)
    }

    @Test
    fun `ended programs are dropped from the timeline`() = runTest {
        val now = java.time.OffsetDateTime.now()
        val ended = program(id = "p-old", name = "Gone", start = now.minusHours(2), end = now.minusHours(1))
        val airing = program(id = "p-now", name = "Live", start = now.minusMinutes(5), end = now.plusMinutes(25))
        coEvery { mediaRepository.getLiveTvPrograms(any(), any(), any()) } returns Result.success(listOf(ended, airing))

        viewModel.loadChannel("chan-1", "Ch")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.programs.size)
        assertEquals("p-now", state.programs.first().id)
    }

    @Test
    fun `getProgramBackdropUrl prefers program image tag`() {
        val url = viewModel.getProgramBackdropUrl(
            program(id = "prog-1", name = "X", imageTag = "ptag").copy(imageUrl = null)
        )
        assertEquals("https://img/prog", url)
    }

    @Test
    fun `getProgramBackdropUrl falls back to program imageUrl when no tag`() {
        val p = program(id = "prog-2", name = "X").copy(imageTag = null, imageUrl = "https://direct/img")
        assertEquals("https://direct/img", viewModel.getProgramBackdropUrl(p))
    }

    @Test
    fun `getProgramBackdropUrl falls back to channel logo when neither present`() = runTest {
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns Result.success(
            listOf(LiveTvChannel(id = "chan-1", name = "Ch", imageTag = "tag"))
        )
        coEvery { mediaRepository.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList())
        viewModel.loadChannel("chan-1", "Ch")
        advanceUntilIdle()

        val p = program(id = "prog-3", name = "X").copy(imageTag = null, imageUrl = null)
        assertEquals("https://img/channel", viewModel.getProgramBackdropUrl(p))
    }

    @Test
    fun `programs load error sets error and keeps channel name`() = runTest {
        coEvery { mediaRepository.getLiveTvPrograms(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.loadChannel("chan-1", "BBC One")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error != null)
        assertEquals("BBC One", state.channelName)
    }

    @Test
    fun `channel meta load failure sets error and skips programs load`() = runTest {
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("no channels"))

        viewModel.loadChannel("chan-1", "BBC One")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error != null)
        assertEquals("BBC One", state.channelName)
        // Early-returned before reaching the programs fetch.
        coVerify(exactly = 0) { mediaRepository.getLiveTvPrograms(any(), any(), any()) }
    }

    @Test
    fun `channel not found in list still resolves currentProgram from programs`() = runTest {
        // Channels list does NOT contain chan-1.
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns Result.success(
            listOf(LiveTvChannel(id = "other", name = "Other", imageTag = "tag"))
        )
        val now = java.time.OffsetDateTime.now()
        val airing = program(id = "p-now", name = "Live", start = now.minusMinutes(5), end = now.plusMinutes(25))
        coEvery { mediaRepository.getLiveTvPrograms("chan-1", any(), any()) } returns Result.success(listOf(airing))

        viewModel.loadChannel("chan-1", "From Nav Arg")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Channel meta unavailable → number/logo blank, name from nav arg.
        assertNull(state.channelNumber)
        assertEquals("", state.channelLogoUrl)
        assertEquals("From Nav Arg", state.channelName)
        // currentProgram still resolved from the programs fallback path.
        assertNotNull(state.currentProgram)
        assertEquals("p-now", state.currentProgram?.id)
    }

    private fun program(
        id: String,
        name: String,
        start: java.time.OffsetDateTime = java.time.OffsetDateTime.now(),
        end: java.time.OffsetDateTime = java.time.OffsetDateTime.now().plusMinutes(30),
        imageTag: String? = "default-tag",
    ) = LiveTvProgram(
        id = id,
        name = name,
        channelId = "chan-1",
        startDate = start.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        endDate = end.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        imageTag = imageTag,
    )
}
