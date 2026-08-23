package com.raulshma.jellyplay.feature.livetv.channeldetail

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: ChannelDetailViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        // getImageUrl is a non-suspend fun → stub with `every`, not `coEvery`.
        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://img/channel"
        every { imageUrlProvider.getImageUrl("prog-1", any()) } returns "https://img/prog"
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList())
        viewModel = ChannelDetailViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadChannel_populates_channel_meta_and_today_programs() = runTest(mainDispatcher) {
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
    fun ended_programs_are_dropped_from_the_timeline() = runTest(mainDispatcher) {
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
    fun getProgramBackdropUrl_prefers_program_image_tag() {
        val url = viewModel.getProgramBackdropUrl(
            program(id = "prog-1", name = "X", imageTag = "ptag").copy(imageUrl = null)
        )
        assertEquals("https://img/prog", url)
    }

    @Test
    fun getProgramBackdropUrl_falls_back_to_program_imageUrl_when_no_tag() {
        val p = program(id = "prog-2", name = "X").copy(imageTag = null, imageUrl = "https://direct/img")
        assertEquals("https://direct/img", viewModel.getProgramBackdropUrl(p))
    }

    @Test
    fun getProgramBackdropUrl_falls_back_to_channel_logo_when_neither_present() = runTest(mainDispatcher) {
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
    fun programs_load_error_sets_error_and_keeps_channel_name() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getLiveTvPrograms(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.loadChannel("chan-1", "BBC One")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error != null)
        assertEquals("BBC One", state.channelName)
    }

    @Test
    fun channel_meta_load_failure_sets_error_and_skips_programs_load() = runTest(mainDispatcher) {
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
    fun channel_not_found_in_list_still_resolves_currentProgram_from_programs() = runTest(mainDispatcher) {
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

    // ── Bus→flow seam (V3 conveyor) ─────────────────────────────────────────
    // The legacy suite constructed a real UserMessageBus and never asserted on
    // it; the commonMain port pins the replacement one-shot message flow.

    @Test
    fun recordProgram_success_emits_RecordSuccess_message() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createTimer("prog-9") } returns Result.success(Unit)

        viewModel.recordProgram(program(id = "prog-9", name = "X"))
        advanceUntilIdle()

        assertEquals(LiveTvUserMessage.RecordSuccess, viewModel.messages.first())
    }

    @Test
    fun cancelTimer_failure_emits_Raw_message_with_exception_text() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelTimer(any()) } returns Result.failure(RuntimeException("nope"))

        viewModel.cancelTimer(program(id = "prog-9", name = "X", timerId = "timer-9"))
        advanceUntilIdle()

        assertEquals(
            LiveTvUserMessage.Raw("nope"),
            viewModel.messages.first(),
        )
    }

    @Test
    fun cancelTimer_failure_with_null_message_emits_fallback_literal() = runTest(mainDispatcher) {
        // Throwable.message == null must hit the drift-prone fallback literal
        // (kept byte-identical from the legacy bus call sites).
        coEvery { mediaRepository.cancelTimer(any()) } returns Result.failure(RuntimeException(null as String?))

        viewModel.cancelTimer(program(id = "prog-9", name = "X", timerId = "timer-9"))
        advanceUntilIdle()

        assertEquals(
            LiveTvUserMessage.Raw("Failed to cancel recording"),
            viewModel.messages.first(),
        )
    }

    private fun program(
        id: String,
        name: String,
        start: java.time.OffsetDateTime = java.time.OffsetDateTime.now(),
        end: java.time.OffsetDateTime = java.time.OffsetDateTime.now().plusMinutes(30),
        imageTag: String? = "default-tag",
        timerId: String? = null,
    ) = LiveTvProgram(
        id = id,
        name = name,
        channelId = "chan-1",
        startDate = start.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        endDate = end.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        imageTag = imageTag,
        timerId = timerId,
    )
}
