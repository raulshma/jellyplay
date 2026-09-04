@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the EPG guide-window math and the record-confirmation side effects the
 * synchronous [EpgViewModelRecordTest] cannot reach (it deliberately never
 * advances the scheduler, keeping the ctor loops inert):
 *
 *  - the guide fetch window is exactly 24h long, starting 2h in the past
 *    (recently-ended shows stay visible), queried with limit=100;
 *  - loadGuide success/failure populates channels/programs/grid vs error;
 *  - confirmRecord drives Confirm → Requesting → Success/Error and reloads
 *    the guide so the timer badges reflect the new timer;
 *  - the auto-refresh loop refetches at exactly the 5-minute virtual mark.
 *
 * The infinite auto-refresh/now-tick loops park on delays, so tests only ever
 * [runCurrent] / [advanceTimeBy] — never `advanceUntilIdle`, which would spin
 * forever on the rescheduling loops. The now-tick loop itself is not asserted:
 * it stamps the REAL clock (`Instant.now()`), not the virtual scheduler's.
 * Because `Dispatchers.setMain` installs a `TestDispatcher`, `runTest` ADOPTS
 * its scheduler (verified: `testScheduler === mainDispatcher.scheduler`), so
 * the VM's loops ride the same scheduler the tests pump — and every test runs
 * through [vmTest], whose `finally` cancels the loops before runTest's
 * completion drain (a bare trailing `stopLoops()` is skipped on a failed
 * assertion and the drain would spin forever).
 *
 * Stub note: `getLiveTvGuide` has 4 parameters (2 with defaults), and the VM
 * calls it with `limit = 100` named — a DIFFERENT default-arg mask than a
 * 3-matcher recording (`limit` defaulted instead of `startIndex`), which the
 * stub silently never matches. Every stub/verify therefore spells out all
 * four matchers explicitly.
 */
class EpgViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        coEvery { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) } returns Result.success(
            EpgGuide(channels = emptyList(), programs = emptyList())
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Runs the init loadGuide; the refresh/now-tick loops park on their delays. */
    private fun TestScope.createViewModel(): EpgViewModel {
        val vm = EpgViewModel(mediaRepository)
        createdViewModels += vm
        testScheduler.runCurrent()
        return vm
    }

    /** Every VM created by [createViewModel]; all cancelled in [vmTest]'s finally. */
    private val createdViewModels = mutableListOf<EpgViewModel>()

    /**
     * Stops a VM's infinite auto-refresh/now-tick loops. Must run INSIDE the
     * test body: runTest's teardown advances the shared scheduler until idle
     * and would spin forever on the rescheduling loops.
     */
    private fun EpgViewModel.stopLoops() {
        viewModelScope.cancel()
    }

    /**
     * runTest wrapper that cancels every created VM's loops in a `finally`
     * INSIDE the coroutine — before runTest's completion drain. The trailing
     * [stopLoops] calls alone are not enough: on a failed assertion they are
     * skipped, and the drain then spins forever on the never-idling loops
     * (the HomeViewModelTest 600s-hang lesson).
     */
    private fun vmTest(block: suspend TestScope.() -> Unit): Unit = runTest {
        try {
            block()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
            createdViewModels.clear()
        }
    }

    private fun program(
        id: String,
        name: String = "Program $id",
        start: Instant = Instant.now(),
        end: Instant = Instant.now().plusSeconds(1_800),
    ) = LiveTvProgram(
        id = id,
        name = name,
        channelId = "chan-1",
        startDate = start.toString(),
        endDate = end.toString(),
    )

    // ── Guide-window math ────────────────────────────────────────────────────

    @Test
    fun loadGuide_fetches_a_24h_window_looking_back_2h_with_limit_100() = vmTest {
        val starts = mutableListOf<String>()
        val ends = mutableListOf<String>()
        coEvery { mediaRepository.getLiveTvGuide(capture(starts), capture(ends), any(), 100) } returns
            Result.success(EpgGuide(channels = emptyList(), programs = emptyList()))

        createViewModel().let { vm ->
            vm.loadGuide()
            testScheduler.runCurrent()
            vm.stopLoops()
        }

        // init + the explicit reload.
        assertEquals(2, starts.size)
        val start = Instant.parse(starts.last())
        val end = Instant.parse(ends.last())
        // start ≈ now - 2h (a small real-time tolerance around the fetch).
        val sinceStart = Duration.between(start, Instant.now())
        assertTrue(
            sinceStart > Duration.ofMinutes(119) && sinceStart < Duration.ofMinutes(123),
            "expected start ≈ now-2h, was $start",
        )
        // Total window span is the jellyfin-web 24h guide.
        assertEquals(Duration.ofHours(24), Duration.between(start, end))
    }

    @Test
    fun loadGuide_success_populates_channels_programs_and_the_grid_snapshot() = vmTest {
        val now = Instant.now()
        val channel = LiveTvChannel(id = "chan-1", name = "CNN")
        val airing = program(id = "p1", start = now.minusSeconds(600), end = now.plusSeconds(1_200))
        coEvery { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) } returns
            Result.success(EpgGuide(channels = listOf(channel), programs = listOf(airing)))

        val vm = createViewModel()

        assertEquals(listOf(channel), vm.channels)
        assertEquals(listOf(airing), vm.programs)
        assertEquals(listOf("chan-1"), vm.gridData.rows.map { it.channel.id })
        assertEquals(listOf("p1"), vm.gridData.rows.single().programs.map { it.id })
        assertFalse(vm.isLoading)
        assertNull(vm.error)
        vm.stopLoops()
    }

    @Test
    fun loadGuide_failure_surfaces_the_error_and_keeps_the_previous_grid() = vmTest {
        coEvery { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("guide down"))

        val vm = createViewModel()

        assertEquals("guide down", vm.error)
        assertFalse(vm.isLoading)
        assertTrue(vm.channels.isEmpty())
        assertTrue(vm.gridData.rows.isEmpty())
        vm.stopLoops()
    }

    // ── Record confirmation side effects ─────────────────────────────────────

    @Test
    fun confirmRecord_success_flips_to_Success_and_reloads_the_guide() = vmTest {
        val vm = createViewModel()
        val p = program(id = "prog-1", name = "Evening News")
        vm.requestRecord(p)
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.success(Unit)

        vm.confirmRecord()
        // The in-flight state is set synchronously, before the repo answers.
        assertEquals(RecordDialogState.Requesting, vm.recordDialog)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(RecordDialogState.Success("Evening News"), vm.recordDialog)
        coVerify(exactly = 1) { mediaRepository.createTimer("prog-1") }
        // The guide is reloaded so timer badges reflect the new timer
        // (init load + confirm reload + the explicit loadGuide below = 3).
        vm.loadGuide()
        testScheduler.runCurrent()
        coVerify(exactly = 3) { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) }
        vm.stopLoops()
    }

    @Test
    fun confirmRecord_failure_flips_to_Error_with_the_exception_message() = vmTest {
        val vm = createViewModel()
        vm.requestRecord(program(id = "prog-1"))
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.failure(RuntimeException("dvr busy"))

        vm.confirmRecord()
        mainDispatcher.scheduler.runCurrent()

        assertEquals(RecordDialogState.Error("dvr busy"), vm.recordDialog)
        vm.stopLoops()
    }

    @Test
    fun confirmRecord_failure_with_null_message_falls_back_to_the_literal() = vmTest {
        val vm = createViewModel()
        vm.requestRecord(program(id = "prog-1"))
        coEvery { mediaRepository.createTimer("prog-1") } returns
            Result.failure(RuntimeException(null as String?))

        vm.confirmRecord()
        mainDispatcher.scheduler.runCurrent()

        assertEquals(RecordDialogState.Error("Failed to create recording"), vm.recordDialog)
        vm.stopLoops()
    }

    @Test
    fun confirmRecord_without_a_pending_confirmation_is_a_no_op() = vmTest {
        val vm = createViewModel()

        vm.confirmRecord()
        mainDispatcher.scheduler.runCurrent()

        assertNull(vm.recordDialog)
        coVerify(exactly = 0) { mediaRepository.createTimer(any()) }
        vm.stopLoops()
    }

    @Test
    fun confirmRecord_after_dismiss_is_a_no_op() = vmTest {
        val vm = createViewModel()
        vm.requestRecord(program(id = "prog-1"))
        vm.dismissRecordDialog()

        vm.confirmRecord()
        mainDispatcher.scheduler.runCurrent()

        assertNull(vm.recordDialog)
        coVerify(exactly = 0) { mediaRepository.createTimer(any()) }
        vm.stopLoops()
    }

    // ── Auto-refresh loop ────────────────────────────────────────────────────

    @Test
    fun autoRefresh_refetches_the_guide_at_the_5_minute_mark_only() = vmTest {
        val vm = createViewModel()
        coVerify(exactly = 1) { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) }

        // 4:59.999 — not yet.
        mainDispatcher.scheduler.advanceTimeBy(4 * 60 * 1000L + 59_999L)
        mainDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) }

        // The 5-minute boundary fires the next refresh.
        mainDispatcher.scheduler.advanceTimeBy(1L)
        mainDispatcher.scheduler.runCurrent()
        coVerify(exactly = 2) { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) }

        // And it keeps going on schedule.
        mainDispatcher.scheduler.advanceTimeBy(5 * 60 * 1000L)
        mainDispatcher.scheduler.runCurrent()
        coVerify(exactly = 3) { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) }
        vm.stopLoops()
    }

    @Test
    fun autoRefresh_updates_the_window_and_grid_with_the_fresh_guide() = vmTest {
        val vm = createViewModel()
        val channel = LiveTvChannel(id = "chan-9", name = "Fresh")
        coEvery { mediaRepository.getLiveTvGuide(any(), any(), any(), any()) } returns
            Result.success(EpgGuide(channels = listOf(channel), programs = emptyList()))

        mainDispatcher.scheduler.advanceTimeBy(5 * 60 * 1000L)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(listOf(channel), vm.channels)
        assertEquals(listOf("chan-9"), vm.gridData.rows.map { it.channel.id })
        assertNull(vm.error)
        vm.stopLoops()
    }
}
