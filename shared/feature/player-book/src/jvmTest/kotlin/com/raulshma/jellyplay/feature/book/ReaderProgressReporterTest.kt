package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.BookProgressPolicy
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Pins [ReaderProgressReporter]'s choreography: the final-flush idempotence
 * across the onDispose + onCleared double fire, [ReaderProgressReporter.schedule]'s
 * re-arm of that latch once reading resumes, the debounce coalescing, the
 * last-CFI ride-along on flushes and the flush-scope escape past a cancelled
 * session scope. Payloads come from the injected lambdas (the VM's state is
 * simulated by swapping them); timing rides the test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressReporterTest {

    /** Past [ReaderProgressReporter]'s 800 ms debounce window. */
    private val debounceWindowMs = 1_000L

    private data class Recorded(val itemId: String, val ticks: Long, val final: Boolean)

    private val reports = mutableListOf<Recorded>()
    private val cfiWrites = mutableListOf<Pair<String, String>>()

    /** The payload the buildReport lambda currently resolves; null simulates no Ready session. */
    private var liveReport: ReaderProgressReport? = ReaderProgressReport(
        itemId = "item-1",
        ticks = BookProgressPolicy.pageToTicks(2),
        cfi = null,
    )
    private var liveItemId: String? = "item-1"

    @BeforeTest
    fun setUp() {
        reports.clear()
        cfiWrites.clear()
        liveReport = ReaderProgressReport("item-1", BookProgressPolicy.pageToTicks(2), cfi = null)
        liveItemId = "item-1"
    }

    private fun TestScope.reporter(
        sessionScope: CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        flushScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    ): ReaderProgressReporter = ReaderProgressReporter(
        scope = sessionScope,
        flushScope = flushScope,
        itemId = { liveItemId },
        buildReport = { item -> liveReport?.copy(itemId = item) },
        reportProgress = { item, ticks, final -> reports += Recorded(item, ticks, final) },
        setLastCfi = { item, cfi -> cfiWrites += item to cfi },
    )

    @Test
    fun `final flush fires once across the dispose plus cleared double fire`() = runTest {
        val reporter = reporter()
        reporter.reportNow(final = true)
        advanceUntilIdle()
        reporter.reportNow(final = true)
        advanceUntilIdle()

        assertEquals(1, reports.size)
        assertTrue(reports.single().final, "the surviving report is the exit flush")
    }

    @Test
    fun `schedule re-arms the final flush after a final flush`() = runTest {
        val reporter = reporter()
        reporter.reportNow(final = true)
        advanceUntilIdle()
        // Reading activity again (screen re-created after the config-change
        // dispose already flushed): a debounce fires, and the NEXT exit must
        // flush again rather than being swallowed by the spent latch.
        reporter.schedule()
        advanceTimeBy(debounceWindowMs)
        advanceUntilIdle()
        reporter.reportNow(final = true)
        advanceUntilIdle()

        assertEquals(listOf(true, false, true), reports.map { it.final })
    }

    @Test
    fun `rapid schedules coalesce into one debounced mid-reading report`() = runTest {
        val reporter = reporter()
        repeat(3) { reporter.schedule() }
        advanceTimeBy(debounceWindowMs)
        advanceUntilIdle()

        assertEquals(1, reports.size)
        assertFalse(reports.single().final, "the debounce report is never the exit flush")
        assertEquals(BookProgressPolicy.pageToTicks(2), reports.single().ticks)
    }

    @Test
    fun `final flush persists the last cfi ride-along`() = runTest {
        liveReport = ReaderProgressReport(
            itemId = "item-1",
            ticks = BookProgressPolicy.percentToTicks(0.4),
            cfi = "epubcfi(/6/20)",
        )
        val reporter = reporter()
        reporter.reportNow(final = true)
        advanceUntilIdle()

        assertEquals(listOf("item-1" to "epubcfi(/6/20)"), cfiWrites)
        assertEquals(BookProgressPolicy.percentToTicks(0.4), reports.single().ticks)
    }

    @Test
    fun `report without a cfi ride-along skips the persistence`() = runTest {
        val reporter = reporter()
        reporter.reportNow(final = true)
        advanceUntilIdle()

        assertEquals(1, reports.size)
        assertTrue(cfiWrites.isEmpty(), "a paged / never-relocated session persists no CFI")
    }

    @Test
    fun `exit flush escapes a cancelled session scope`() = runTest {
        val sessionScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val reporter = reporter(sessionScope = sessionScope)
        sessionScope.cancel()

        reporter.reportNow(final = true)
        advanceUntilIdle()
        assertEquals(1, reports.size, "the exit flush rides the process-wide scope")

        reporter.reportNow(final = false)
        advanceUntilIdle()
        assertEquals(1, reports.size, "a mid-reading report on the cancelled session scope is dropped")
    }

    @Test
    fun `report without a loaded item cancels the pending debounce and reports nothing`() = runTest {
        val reporter = reporter()
        reporter.schedule()
        liveItemId = null
        // The no-item call still cancels the armed debounce before returning —
        // restore the item and let the window lapse: a surviving debounce
        // would fire here, and the no-item final fired nothing either.
        reporter.reportNow(final = true)
        liveItemId = "item-1"
        advanceTimeBy(debounceWindowMs)
        advanceUntilIdle()

        assertTrue(reports.isEmpty(), "the stale debounce never fired and the no-item final reported nothing")
    }

    @Test
    fun `no ready session reports nothing on dispose`() = runTest {
        liveReport = null
        val reporter = reporter()
        reporter.reportNow(final = true)
        advanceUntilIdle()

        assertTrue(reports.isEmpty(), "a failed load must not report position 0 and wipe the server-side resume")
    }
}
