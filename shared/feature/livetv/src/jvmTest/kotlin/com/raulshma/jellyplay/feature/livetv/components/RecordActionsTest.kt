package com.raulshma.jellyplay.feature.livetv.components

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.LiveTvProgram
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the ONE recording choreography [RecordActions] owns for every Live TV
 * tab: the synchronous Requesting flip (the in-flight latch the dialog state
 * rides), the single repository call, and the Success/Error outcome carrying
 * its [RecordRequest] identity. Uses the module's inlined-MainDispatcherRule
 * pattern (jvmTest has no :core:testing access); the queueing dispatcher keeps
 * the launched repository call inert until the scheduler is advanced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordActionsTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var scope: CoroutineScope
    private lateinit var repository: LiveTvRepository
    private lateinit var observed: MutableList<RecordOutcome>
    private lateinit var actions: RecordActions

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        scope = CoroutineScope(Job() + mainDispatcher)
        repository = mockk(relaxed = true)
        observed = mutableListOf()
        actions = RecordActions(repository, scope) { observed += it }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun program(id: String, timerId: String? = null, seriesTimerId: String? = null) = LiveTvProgram(
        id = id,
        name = "Program $id",
        channelId = "chan-1",
        startDate = "2026-07-01T19:00:00Z",
        endDate = "2026-07-01T19:30:00Z",
        timerId = timerId,
        seriesTimerId = seriesTimerId,
    )

    @Test
    fun in_flight_Requesting_is_published_synchronously_before_the_repository_answers() = runTest(mainDispatcher) {
        // Park the repository so the request stays in flight.
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.createTimer("prog-1") } coAnswers { gate.await(); Result.success(Unit) }

        actions.recordOnce(program(id = "prog-1"))

        // The synchronous flip: dialog state can move in the same frame as the tap.
        val request = RecordRequest(RecordAction.RECORD_ONCE, program = program(id = "prog-1"))
        assertEquals(listOf<RecordOutcome>(RecordOutcome.Requesting(request)), observed)
        assertEquals(RecordOutcome.Requesting(request), actions.outcomes.value)

        gate.complete(Unit)
        advanceUntilIdle()

        // Success keeps the request identity and mirrors onto the outcome flow.
        assertEquals(
            listOf(RecordOutcome.Requesting(request), RecordOutcome.Success(request)),
            observed,
        )
        assertEquals(RecordOutcome.Success(request), actions.outcomes.value)
    }

    @Test
    fun failure_publishes_Error_with_the_request_identity_and_the_raw_message() = runTest(mainDispatcher) {
        coEvery { repository.createSeriesTimer("prog-1") } returns Result.failure(RuntimeException("conflict"))

        actions.recordSeries(program(id = "prog-1"))
        advanceUntilIdle()

        val request = RecordRequest(RecordAction.RECORD_SERIES, program = program(id = "prog-1"))
        assertEquals(
            listOf(RecordOutcome.Requesting(request), RecordOutcome.Error(request, "conflict")),
            observed,
        )
    }

    @Test
    fun a_null_exception_message_stays_null_so_each_tab_applies_its_own_fallback() = runTest(mainDispatcher) {
        coEvery { repository.createTimer("prog-1") } returns Result.failure(RuntimeException(null as String?))

        actions.recordOnce(program(id = "prog-1"))
        advanceUntilIdle()

        val last = observed.last()
        assertTrue(last is RecordOutcome.Error && last.message == null, "Expected Error(null), got $last")
    }

    @Test
    fun cancelTimer_needs_a_timer_id_and_emits_nothing_without_one() {
        assertFalse(actions.cancelTimer(program(id = "prog-1", timerId = null)))

        assertTrue(observed.isEmpty())
        assertEquals(RecordOutcome.Idle, actions.outcomes.value)
        coVerify(exactly = 0) { repository.cancelTimer(any()) }
    }

    @Test
    fun cancelSeries_needs_a_series_timer_id_and_emits_nothing_without_one() {
        assertFalse(actions.cancelSeries(program(id = "prog-1", seriesTimerId = null)))

        assertTrue(observed.isEmpty())
        coVerify(exactly = 0) { repository.cancelSeriesTimer(any()) }
    }

    @Test
    fun cancelTimer_carries_the_program_and_timer_identity() = runTest(mainDispatcher) {
        coEvery { repository.cancelTimer("timer-9") } returns Result.success(Unit)

        assertTrue(actions.cancelTimer(program(id = "prog-1", timerId = "timer-9")))
        advanceUntilIdle()

        val request = RecordRequest(
            RecordAction.CANCEL_TIMER,
            program = program(id = "prog-1", timerId = "timer-9"),
            timerId = "timer-9",
        )
        assertEquals(
            listOf(RecordOutcome.Requesting(request), RecordOutcome.Success(request)),
            observed,
        )
        coVerify(exactly = 1) { repository.cancelTimer("timer-9") }
    }

    @Test
    fun id_based_timer_cancel_carries_the_timer_identity_without_a_program() = runTest(mainDispatcher) {
        coEvery { repository.cancelTimer("t1") } returns Result.success(Unit)

        actions.cancelTimer("t1")
        advanceUntilIdle()

        val request = RecordRequest(RecordAction.CANCEL_TIMER, timerId = "t1")
        assertEquals(
            listOf(RecordOutcome.Requesting(request), RecordOutcome.Success(request)),
            observed,
        )
    }

    @Test
    fun id_based_series_cancel_carries_the_series_timer_identity_without_a_program() = runTest(mainDispatcher) {
        coEvery { repository.cancelSeriesTimer("st1") } returns Result.success(Unit)

        actions.cancelSeries("st1")
        advanceUntilIdle()

        val request = RecordRequest(RecordAction.CANCEL_SERIES, seriesTimerId = "st1")
        assertEquals(
            listOf(RecordOutcome.Requesting(request), RecordOutcome.Success(request)),
            observed,
        )
    }
}
