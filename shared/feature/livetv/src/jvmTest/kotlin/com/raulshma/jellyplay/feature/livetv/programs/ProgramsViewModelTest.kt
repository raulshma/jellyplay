package com.raulshma.jellyplay.feature.livetv.programs

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.feature.livetv.components.RecordDialogState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: LiveTvRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: ProgramsViewModel

    /** The fake clock behind the full-render throttle; tests move [FakeTimeSource.nowMs] across its boundary. */
    private val fakeTimeSource = FakeTimeSource()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecommendedPrograms(any(), any()) } returns Result.success(emptyList())
        viewModel = ProgramsViewModel(mediaRepository, imageUrlProvider, fakeTimeSource)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_fires_On_Now_airing_query_and_5_category_queries() = runTest(mainDispatcher) {
        advanceUntilIdle()
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isAiring == true }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isSeries == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isMovie == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isSports == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isKids == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isNews == true && it.hasAired == false }, any()) }
    }

    @Test
    fun rows_are_built_only_for_non_empty_categories() = runTest(mainDispatcher) {
        val onNow = listOf(sampleProgram("On Air Show"))
        coEvery {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isAiring == true }, any())
        } returns Result.success(onNow)
        // every other category empty (default relaxed mock)

        viewModel.load()
        advanceUntilIdle()

        val rows = viewModel.uiState.value.rows
        assertEquals(1, rows.size)
        assertEquals("On Now", rows.first().title)
    }

    @Test
    fun requestRecord_opens_AwaitingChoice_dialog_for_the_program() {
        val program = sampleProgram("Late Show")
        viewModel.requestRecord(program)

        val state = viewModel.uiState.value.recordDialog
        assertTrue(state is RecordDialogState.AwaitingChoice, "Expected AwaitingChoice, got $state")
        assertEquals("Late Show", (state as RecordDialogState.AwaitingChoice).program.name)
    }

    @Test
    fun recordOnce_calls_createTimer_with_the_program_id() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.success(Unit)
        val program = sampleProgram(id = "prog-1")
        viewModel.recordOnce(program)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createTimer("prog-1") }
    }

    @Test
    fun recordSeries_calls_createSeriesTimer_with_the_program_id() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createSeriesTimer("prog-1") } returns Result.success(Unit)
        viewModel.recordSeries(sampleProgram(id = "prog-1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createSeriesTimer("prog-1") }
    }

    @Test
    fun cancelTimer_forwards_the_program_timerId() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelTimer("timer-9") } returns Result.success(Unit)
        viewModel.cancelTimer(sampleProgram(id = "prog-1", timerId = "timer-9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.cancelTimer("timer-9") }
    }

    // ── Row building ─────────────────────────────────────────────────────────

    @Test
    fun rows_are_empty_when_every_category_query_comes_back_empty() = runTest(mainDispatcher) {
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── The 5-minute full-render throttle ────────────────────────────────────

    @Test
    fun reload_within_5_minutes_only_refreshes_the_on_now_row() = runTest(mainDispatcher) {
        val onNow1 = listOf(sampleProgram(name = "Now A", id = "now-1"))
        val shows = listOf(sampleProgram(name = "Show", id = "show-1"))
        coEvery {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isAiring == true }, any())
        } returns Result.success(onNow1)
        coEvery {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isSeries == true && it.hasAired == false }, any())
        } returns Result.success(shows)
        viewModel = ProgramsViewModel(mediaRepository, imageUrlProvider, fakeTimeSource)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.rows.size)

        // Forget the initial render's calls (a second VM was just built, so
        // the shared mock already logged one full render) — what follows
        // must prove the throttled reload adds no category queries.
        clearMocks(mediaRepository, answers = false)

        // Re-entry within the throttle window: only On Now is refetched.
        val onNow2 = listOf(sampleProgram(name = "Now B", id = "now-2"))
        coEvery {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isAiring == true }, any())
        } returns Result.success(onNow2)
        viewModel.load()
        advanceUntilIdle()

        val rows = viewModel.uiState.value.rows
        assertEquals(2, rows.size)
        assertEquals(listOf("now-2"), rows[0].programs.map { it.id })
        assertEquals("On Now", rows[0].title)
        assertEquals("Shows", rows[1].title)
        assertEquals(listOf("show-1"), rows[1].programs.map { it.id })
        assertFalse(viewModel.uiState.value.refreshing)
        assertFalse(viewModel.uiState.value.isLoading)
        // The throttled reload added no category queries (initial render only).
        coVerify(exactly = 0) {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isSeries == true }, any())
        }
    }

    @Test
    fun full_render_throttle_boundary_is_strict_at_the_5_minute_mark() = runTest(mainDispatcher) {
        viewModel = ProgramsViewModel(mediaRepository, imageUrlProvider, fakeTimeSource)
        advanceUntilIdle()
        clearMocks(mediaRepository, answers = false)

        // Exactly 5:00.000 after the init full render (which stamped the fake
        // epoch) → the strict `>` keeps the reload THROTTLED (no category
        // queries).
        fakeTimeSource.nowMs = BOOT_MS + 5 * 60 * 1000L
        viewModel.load()
        advanceUntilIdle()
        coVerify(exactly = 0) {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isSeries == true }, any())
        }

        // The throttled reload re-stamped the clock, so the full render lands
        // one virtual ms past the NEXT 5-minute boundary.
        fakeTimeSource.nowMs = BOOT_MS + 2 * 5 * 60 * 1000L + 1
        viewModel.load()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isSeries == true }, any())
        }
    }

    // ── Record-flow dialog state machine ─────────────────────────────────────

    @Test
    fun recordOnce_success_shows_Success_resets_dialog_and_reloads() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.success(Unit)
        viewModel.recordOnce(sampleProgram(id = "prog-1"))
        assertEquals(RecordDialogState.Requesting, viewModel.uiState.value.recordDialog)

        advanceUntilIdle()

        assertEquals(RecordDialogState.Success(), viewModel.uiState.value.recordDialog)
        // load() re-ran after the success (2× on-now query: init + reload).
        coVerify(exactly = 2) {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isAiring == true }, any())
        }
    }

    @Test
    fun recordOnce_failure_shows_Error_with_the_exception_message() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.failure(RuntimeException("conflict"))
        viewModel.recordOnce(sampleProgram(id = "prog-1"))
        advanceUntilIdle()

        assertEquals(RecordDialogState.Error("conflict"), viewModel.uiState.value.recordDialog)
    }

    @Test
    fun recordOnce_failure_with_null_message_falls_back_to_Failed() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createTimer("prog-1") } returns
            Result.failure(RuntimeException(null as String?))
        viewModel.recordOnce(sampleProgram(id = "prog-1"))
        advanceUntilIdle()

        assertEquals(RecordDialogState.Error("Failed"), viewModel.uiState.value.recordDialog)
    }

    @Test
    fun recordSeries_failure_shows_Error() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createSeriesTimer("prog-1") } returns Result.failure(RuntimeException("no"))
        viewModel.recordSeries(sampleProgram(id = "prog-1"))
        advanceUntilIdle()

        assertEquals(RecordDialogState.Error("no"), viewModel.uiState.value.recordDialog)
    }

    @Test
    fun cancelTimer_without_a_timerId_dismisses_without_calling() {
        viewModel.requestRecord(sampleProgram(id = "prog-1"))

        viewModel.cancelTimer(sampleProgram(id = "prog-1", timerId = null))

        assertEquals(RecordDialogState.Idle, viewModel.uiState.value.recordDialog)
    }

    @Test
    fun cancelSeries_forwards_the_seriesTimerId_and_shows_Success() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelSeriesTimer("st-9") } returns Result.success(Unit)

        viewModel.cancelSeries(sampleProgram(id = "prog-1", seriesTimerId = "st-9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.cancelSeriesTimer("st-9") }
        assertEquals(RecordDialogState.Success(), viewModel.uiState.value.recordDialog)
    }

    @Test
    fun cancelSeries_without_a_seriesTimerId_dismisses_without_calling() {
        viewModel.requestRecord(sampleProgram(id = "prog-1"))

        viewModel.cancelSeries(sampleProgram(id = "prog-1", seriesTimerId = null))

        assertEquals(RecordDialogState.Idle, viewModel.uiState.value.recordDialog)
    }

    @Test
    fun dismissRecordDialog_returns_to_Idle() {
        viewModel.requestRecord(sampleProgram())

        viewModel.dismissRecordDialog()

        assertEquals(RecordDialogState.Idle, viewModel.uiState.value.recordDialog)
    }

    // ── Image url tag quirk (same as channels/recordings) ────────────────────
    // The null-tag policy itself lives on the interface default now (pinned
    // in ImageUrlProviderImplTest); these pin the VM forwarding BOTH args
    // through it instead of re-deciding the fold.

    @Test
    fun getImageUrl_forwards_the_null_tag_to_the_interface_fold() {
        every { imageUrlProvider.getImageUrlOrNull("p1", null) } returns ""

        assertEquals("", viewModel.getImageUrl("p1", null))

        verify(exactly = 1) { imageUrlProvider.getImageUrlOrNull("p1", null) }
    }

    @Test
    fun getImageUrl_forwards_a_present_tag_to_the_interface_fold() {
        every { imageUrlProvider.getImageUrlOrNull("p1", "tag") } returns "http://img/p1"

        assertEquals("http://img/p1", viewModel.getImageUrl("p1", "tag"))

        verify(exactly = 1) { imageUrlProvider.getImageUrlOrNull("p1", "tag") }
    }

    /**
     * Controllable [TimeSource] on a fixed epoch well past the zero start of
     * the throttle's lastFullRender stamp (the HomeRefresher fake idiom) —
     * the init load is a full render, and tests move [nowMs] across the
     * 5-minute boundary relative to [BOOT_MS].
     */
    private class FakeTimeSource(var nowMs: Long = BOOT_MS) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }

    private companion object {
        const val BOOT_MS: Long = 1_000_000L
    }

    private fun sampleProgram(
        name: String = "Evening News",
        id: String = "prog-1",
        timerId: String? = null,
        seriesTimerId: String? = null,
    ) = LiveTvProgram(
        id = id,
        name = name,
        channelId = "chan-1",
        startDate = "2026-07-01T19:00:00Z",
        endDate = "2026-07-01T19:30:00Z",
        timerId = timerId,
        seriesTimerId = seriesTimerId,
    )
}
