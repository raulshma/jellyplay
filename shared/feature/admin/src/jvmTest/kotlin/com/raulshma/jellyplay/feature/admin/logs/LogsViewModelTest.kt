package com.raulshma.jellyplay.feature.admin.logs

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * Pins the logs screen's live-tail invariants (`LogsViewModel`):
 *
 *  - the live ring buffer and the activity buffer both cap at
 *    MAX_LIVE_ENTRIES = 200, newest first (addFirst / removeLast);
 *  - the dedup id set caps at MAX_LIVE_ENTRY_IDS = 400 (2× the display
 *    buffer) and evicts oldest-inserted ids, so a long session cannot grow
 *    it without bound — at the cost of re-admitting long-evicted ids;
 *  - stopLiveStream cancels the collector (later pushes are ignored) and
 *    startLiveStream resets the tail;
 *  - the selected-file poll skips the multi-MB content re-download when the
 *    file's size/dateModified metadata is unchanged, marks appended lines
 *    `isNew`, and runs only between startLogPolling/stopLogPolling (the
 *    screen's LifecycleStartEffect arms) with the pause toggle + "a file is
 *    selected" as the round gate — clearSelectedLogFile resets state and
 *    closes the gate;
 *  - loadMoreActivity is re-entry safe via the PageAppender guard: a second
 *    call while an older-page fetch is still in flight is dropped, so a fast
 *    fling cannot re-fetch and double-append the same server page
 *    (startIndex = currentSize); the skip pager never goes terminal — an
 *    empty server page leaves the gate open.
 *
 * The 5s file poll runs on the test scheduler, so only explicit
 * advanceTimeBy/runCurrent steps drive it — a bare advanceUntilIdle while a
 * file is selected would spin the poll forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var adminRepository: AdminRepository

    /** No replay: like the production live-log socket, nothing emits before a push. */
    private val liveFlow = MutableSharedFlow<ActivityLogEntry>(replay = 0, extraBufferCapacity = 512)

    @BeforeTest
    fun setUp() {
        adminRepository = mockk(relaxed = true)
        every { adminRepository.liveActivityEntries(any()) } returns liveFlow
    }

    private fun entry(id: Long) = ActivityLogEntry(id = id, name = "entry-$id")

    private fun TestScope.loadedViewModel(initial: List<ActivityLogEntry> = emptyList()): LogsViewModel {
        coEvery { adminRepository.getLogFiles() } returns
            Result.success(listOf(LogFile(name = "server.log", size = 10L, dateModified = "t1")))
        coEvery { adminRepository.getActivityLogEntries(any(), any()) } returns Result.success(initial)
        coEvery { adminRepository.getLogFileContent(any()) } returns Result.success("")
        return LogsViewModel(adminRepository, parseDispatcher = mainDispatcherRule.testDispatcher).also { advanceUntilIdle() }
    }

    // ── initial load ──

    @Test
    fun `initial load populates log files and activity entries`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel(initial = listOf(entry(1), entry(2)))

        assertFalse(vm.state.isLoading)
        assertEquals(listOf("server.log"), vm.state.logFiles.map { it.name })
        assertEquals(listOf(1L, 2L), vm.state.activityEntries.map { it.id })
        assertNull(vm.state.error)
    }

    // ── live tail ──

    @Test
    fun `live tail caps at 200 entries newest first`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        vm.startLiveStream()
        advanceUntilIdle()

        repeat(220) { liveFlow.tryEmit(entry(it.toLong())) }
        advanceUntilIdle()

        val live = vm.state.liveEntries
        assertEquals(200, live.size)
        assertEquals(219L, live.first().id) // newest first
        assertEquals(20L, live.last().id) // ids 0..19 evicted from the display buffer
        assertEquals(200, vm.state.activityEntries.size)
        // Under the dedup-set cap every seen id is still remembered.
        assertEquals(220, vm.state.liveEntryIds.size)
    }

    @Test
    fun `duplicate live ids are deduped`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        vm.startLiveStream()
        advanceUntilIdle()

        repeat(3) { liveFlow.tryEmit(entry(7L)) }
        advanceUntilIdle()

        assertEquals(listOf(7L), vm.state.liveEntries.map { it.id })
        assertEquals(1, vm.state.liveEntryIds.size)
    }

    @Test
    fun `dedup id set caps at 400 and evicts the oldest ids`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        vm.startLiveStream()
        advanceUntilIdle()

        repeat(450) { liveFlow.tryEmit(entry(it.toLong())) }
        advanceUntilIdle()

        // MAX_LIVE_ENTRY_IDS = 2 × MAX_LIVE_ENTRIES: the set holds ids 50..449.
        assertEquals(400, vm.state.liveEntryIds.size)
        assertEquals(200, vm.state.liveEntries.size)

        // An id still inside the trimmed set but no longer displayed stays deduped…
        liveFlow.tryEmit(entry(100))
        advanceUntilIdle()
        assertEquals(449L, vm.state.liveEntries.first().id)
        assertEquals(200, vm.state.liveEntries.size)

        // …while an evicted id (0) is re-admitted — the set guards the display
        // buffer's memory, it is not a seen-forever history.
        liveFlow.tryEmit(entry(0))
        advanceUntilIdle()
        assertEquals(0L, vm.state.liveEntries.first().id)
        assertEquals(200, vm.state.liveEntries.size)
        assertEquals(400, vm.state.liveEntryIds.size)
    }

    @Test
    fun `start resets the tail and stop freezes it`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        vm.startLiveStream()
        advanceUntilIdle()
        liveFlow.tryEmit(entry(1))
        advanceUntilIdle()
        assertTrue(vm.state.isLiveStreamActive)
        assertEquals(listOf(1L), vm.state.liveEntries.map { it.id })

        vm.stopLiveStream()
        assertFalse(vm.state.isLiveStreamActive)
        liveFlow.tryEmit(entry(2)) // collector cancelled — must be ignored
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.state.liveEntries.map { it.id })

        // A restart clears the tail and re-subscribes.
        vm.startLiveStream()
        assertTrue(vm.state.isLiveStreamActive)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.liveEntries)
        liveFlow.tryEmit(entry(3))
        advanceUntilIdle()
        assertEquals(listOf(3L), vm.state.liveEntries.map { it.id })
    }

    @Test
    fun `activity ids seed the live dedup request`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel(initial = listOf(entry(1), entry(2)))
        vm.startLiveStream()
        advanceUntilIdle()

        // The repository owns dedup against already-known REST entries.
        verify { adminRepository.liveActivityEntries(setOf(1L, 2L)) }
    }

    // ── selected file: load + metadata-driven poll ──

    @Test
    fun `loadLogFile maps content to indexed lines`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.success("line1\nline2")

        vm.loadLogFile("server.log")
        // Adapted from the former isLoadingLogContent + selectedLogFileName pair:
        // the Loading arm names the file it is loading.
        assertEquals(LogPanelPhase.Loading("server.log"), vm.state.panelPhase)

        testScheduler.runCurrent()

        // Adapted from the former content + lines pair: ONE immutable
        // SelectedLogFile, name and content from the same write.
        val phase = vm.state.panelPhase as LogPanelPhase.Loaded
        assertEquals("server.log", phase.file.name)
        assertEquals("line1\nline2", phase.file.content)
        assertEquals(
            listOf(LogLine(index = 0, text = "line1"), LogLine(index = 1, text = "line2")),
            phase.file.lines,
        )
    }

    @Test
    fun `unchanged file metadata skips the content re-download`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns Result.success("line1")

        vm.loadLogFile("server.log")
        vm.startLogPolling() // the screen's LifecycleStartEffect arm
        testScheduler.runCurrent() // initial fetch
        coVerify(exactly = 1) { adminRepository.getLogFileContent("server.log") }

        // One poll tick with unchanged size/dateModified: metadata fast path.
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        coVerify(exactly = 1) { adminRepository.getLogFileContent("server.log") }

        // Stop the 5s poll before the test scope closes — runTest's quiescence
        // advance would otherwise spin the rescheduling loop forever.
        vm.stopLogPolling()
        vm.clearSelectedLogFile()
        advanceUntilIdle()
    }

    @Test
    fun `appended lines are marked new and previous lines are reused`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.success("line1\nline2")
        vm.loadLogFile("server.log")
        vm.startLogPolling()
        testScheduler.runCurrent()

        // The file grew: metadata changes, so the poll re-downloads.
        coEvery { adminRepository.getLogFiles() } returns
            Result.success(listOf(LogFile(name = "server.log", size = 20L, dateModified = "t2")))
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.success("line1\nline2\nline3")

        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()

        // Adapted: the diffed lines publish inside the same SelectedLogFile.
        val lines = (vm.state.panelPhase as LogPanelPhase.Loaded).file.lines
        assertEquals(listOf("line1", "line2", "line3"), lines.map { it.text })
        assertEquals(listOf(false, false, true), lines.map { it.isNew })
        assertEquals(listOf(0, 1, 2), lines.map { it.index })

        vm.stopLogPolling()
        vm.clearSelectedLogFile()
        advanceUntilIdle()
        coVerify(exactly = 2) { adminRepository.getLogFileContent("server.log") }
    }

    // ── polling flag + clear ──

    @Test
    fun `toggleLogPolling pauses and resumes the poll`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        assertTrue(vm.state.isLogPollingActive)
        vm.toggleLogPolling()
        assertFalse(vm.state.isLogPollingActive)
        vm.toggleLogPolling()
        assertTrue(vm.state.isLogPollingActive)
    }

    @Test
    fun `clear resets selection and the poll gate stays closed`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns Result.success("line1")
        vm.loadLogFile("server.log")
        vm.startLogPolling()
        testScheduler.runCurrent()
        vm.toggleLogPolling()
        assertFalse(vm.state.isLogPollingActive)

        vm.clearSelectedLogFile()

        // Adapted from the former name/content/lines triple: ONE phase back to Idle.
        assertEquals(LogPanelPhase.Idle, vm.state.panelPhase)
        // The polling flag resets so the next selection starts live.
        assertTrue(vm.state.isLogPollingActive)
        // Completes only because the cycle is stopped — otherwise the 5s
        // loop would keep the scheduler busy forever (the screen's
        // LifecycleStartEffect STOP arm is this call in production).
        vm.stopLogPolling()
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.getLogFileContent("server.log") }
    }

    // ── paginated more-load re-entry guard ──

    @Test
    fun `loadMoreActivity while a page is in flight fetches and appends exactly one page`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val initial = (0L until 50L).map { entry(it) }
            val vm = loadedViewModel(initial = initial)

            // The older-page fetch suspends until released, so the second
            // call lands while the first is genuinely in flight — the old
            // unguarded code re-read the unchanged currentSize (50), fetched
            // the same page twice, and double-appended it.
            val gate = CompletableDeferred<Unit>()
            var moreFetches = 0
            coEvery { adminRepository.getActivityLogEntries(startIndex = 50, limit = 50) } coAnswers {
                moreFetches += 1
                gate.await()
                Result.success((50L until 100L).map { entry(it) })
            }

            vm.loadMoreActivity()
            vm.loadMoreActivity() // rapid re-entry before the first completes
            advanceUntilIdle()

            assertEquals(1, moreFetches)
            assertTrue(vm.state.isLoadingMoreActivity)
            assertEquals(50, vm.state.activityEntries.size)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, moreFetches)
            assertFalse(vm.state.isLoadingMoreActivity)
            assertEquals((0L until 100L).toList(), vm.state.activityEntries.map { it.id })
            coVerify(exactly = 1) { adminRepository.getActivityLogEntries(startIndex = 50, limit = 50) }
        }

    @Test
    fun `loadMoreActivity releases the in-flight flag so the next page can load`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = loadedViewModel(initial = (0L until 50L).map { entry(it) })
            coEvery { adminRepository.getActivityLogEntries(startIndex = 50, limit = 50) } returns
                Result.success((50L until 100L).map { entry(it) })
            coEvery { adminRepository.getActivityLogEntries(startIndex = 100, limit = 50) } returns
                Result.success(emptyList())

            vm.loadMoreActivity()
            advanceUntilIdle()
            assertFalse(vm.state.isLoadingMoreActivity)

            vm.loadMoreActivity()
            advanceUntilIdle()

            coVerify(exactly = 1) { adminRepository.getActivityLogEntries(startIndex = 100, limit = 50) }
        }

    @Test
    fun `loadMoreActivity has no terminal gate so an exhausted server is re-polled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = loadedViewModel(initial = (0L until 50L).map { entry(it) })
            // The skip pager tracks no hasMore: every more-fetch of an
            // exhausted server answers empty, and the gate stays open so the
            // next tap re-polls the same startIndex.
            coEvery { adminRepository.getActivityLogEntries(startIndex = 50, limit = 50) } returns
                Result.success(emptyList())

            vm.loadMoreActivity()
            advanceUntilIdle()
            assertFalse(vm.state.isLoadingMoreActivity)

            vm.loadMoreActivity()
            advanceUntilIdle()

            coVerify(exactly = 2) { adminRepository.getActivityLogEntries(startIndex = 50, limit = 50) }
            assertEquals(50, vm.state.activityEntries.size)
        }

    // ── selected-log panel phase (the atomic SelectedLogFile) ──

    @Test
    fun `the panel phases run idle - loading - loaded and clear returns to idle`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns Result.success("line1")

        assertEquals(LogPanelPhase.Idle, vm.state.panelPhase)

        vm.loadLogFile("server.log")
        assertEquals(LogPanelPhase.Loading("server.log"), vm.state.panelPhase)

        testScheduler.runCurrent()
        val phase = vm.state.panelPhase
        assertTrue(phase is LogPanelPhase.Loaded)
        assertEquals("server.log", (phase as LogPanelPhase.Loaded).file.name)

        vm.clearSelectedLogFile()
        assertEquals(LogPanelPhase.Idle, vm.state.panelPhase)
    }

    @Test
    fun `a failed file load lands the panel phase in Failed with the message`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.failure(RuntimeException("boom"))

        vm.loadLogFile("server.log")
        assertEquals(LogPanelPhase.Loading("server.log"), vm.state.panelPhase)
        advanceUntilIdle()

        // The former global `error` write moved onto the Failed arm.
        assertEquals(LogPanelPhase.Failed("server.log", "boom"), vm.state.panelPhase)
    }

    @Test
    fun `a stale fetch landing after a file switch never attaches its content to the newer selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = loadedViewModel()

            // Park server.log's content fetch mid-flight.
            val parked = CompletableDeferred<Unit>()
            coEvery { adminRepository.getLogFileContent("server.log") } coAnswers {
                parked.await()
                Result.success("line1\nline2")
            }
            vm.loadLogFile("server.log")
            testScheduler.runCurrent()
            assertEquals(LogPanelPhase.Loading("server.log"), vm.state.panelPhase)

            // A switch lands while the first fetch is still in flight: the
            // panel phase moves atomically to Loading(other.log) — the torn
            // window the former three-field state allowed (new name + old
            // content) is unrepresentable.
            coEvery { adminRepository.getLogFileContent("other.log") } returns Result.success("other")
            vm.loadLogFile("other.log")

            parked.complete(Unit)
            advanceUntilIdle()

            val phase = vm.state.panelPhase
            assertTrue(phase is LogPanelPhase.Loaded)
            // Name + content + lines are ONE instance from ONE write — the
            // stale server.log content never attached to other.log.
            assertEquals("other.log", (phase as LogPanelPhase.Loaded).file.name)
            assertEquals("other", phase.file.content)
            assertEquals(listOf(LogLine(index = 0, text = "other")), phase.file.lines)
        }

    @Test
    fun `selecting a file un-pauses a paused poll - the declared legacy behavior`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedViewModel()
        vm.toggleLogPolling()
        assertFalse(vm.state.isLogPollingActive)

        vm.loadLogFile("server.log")

        assertTrue(vm.state.isLogPollingActive)
        vm.stopLogPolling()
        vm.clearSelectedLogFile()
        advanceUntilIdle()
    }
}
