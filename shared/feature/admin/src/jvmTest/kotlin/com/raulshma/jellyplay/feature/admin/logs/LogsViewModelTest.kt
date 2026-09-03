package com.raulshma.jellyplay.feature.admin.logs

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.LogFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
 *    `isNew`, and clearSelectedLogFile cancels the poll and resets state.
 *
 * The 5s file poll runs on the test scheduler, so only explicit
 * advanceTimeBy/runCurrent steps drive it — a bare advanceUntilIdle while a
 * file is selected would spin the poll forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    /** No replay: like the production live-log socket, nothing emits before a push. */
    private val liveFlow = MutableSharedFlow<ActivityLogEntry>(replay = 0, extraBufferCapacity = 512)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk(relaxed = true)
        every { adminRepository.liveActivityEntries(any()) } returns liveFlow
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(id: Long) = ActivityLogEntry(id = id, name = "entry-$id")

    private fun TestScope.loadedViewModel(initial: List<ActivityLogEntry> = emptyList()): LogsViewModel {
        coEvery { adminRepository.getLogFiles() } returns
            Result.success(listOf(LogFile(name = "server.log", size = 10L, dateModified = "t1")))
        coEvery { adminRepository.getActivityLogEntries(any(), any()) } returns Result.success(initial)
        coEvery { adminRepository.getLogFileContent(any()) } returns Result.success("")
        return LogsViewModel(adminRepository).also { advanceUntilIdle() }
    }

    // ── initial load ──

    @Test
    fun `initial load populates log files and activity entries`() = runTest(mainDispatcher) {
        val vm = loadedViewModel(initial = listOf(entry(1), entry(2)))

        assertFalse(vm.state.isLoading)
        assertEquals(listOf("server.log"), vm.state.logFiles.map { it.name })
        assertEquals(listOf(1L, 2L), vm.state.activityEntries.map { it.id })
        assertNull(vm.state.error)
    }

    // ── live tail ──

    @Test
    fun `live tail caps at 200 entries newest first`() = runTest(mainDispatcher) {
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
    fun `duplicate live ids are deduped`() = runTest(mainDispatcher) {
        val vm = loadedViewModel()
        vm.startLiveStream()
        advanceUntilIdle()

        repeat(3) { liveFlow.tryEmit(entry(7L)) }
        advanceUntilIdle()

        assertEquals(listOf(7L), vm.state.liveEntries.map { it.id })
        assertEquals(1, vm.state.liveEntryIds.size)
    }

    @Test
    fun `dedup id set caps at 400 and evicts the oldest ids`() = runTest(mainDispatcher) {
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
    fun `start resets the tail and stop freezes it`() = runTest(mainDispatcher) {
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
    fun `activity ids seed the live dedup request`() = runTest(mainDispatcher) {
        val vm = loadedViewModel(initial = listOf(entry(1), entry(2)))
        vm.startLiveStream()
        advanceUntilIdle()

        // The repository owns dedup against already-known REST entries.
        verify { adminRepository.liveActivityEntries(setOf(1L, 2L)) }
    }

    // ── selected file: load + metadata-driven poll ──

    @Test
    fun `loadLogFile maps content to indexed lines`() = runTest(mainDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.success("line1\nline2")

        vm.loadLogFile("server.log")
        assertTrue(vm.state.isLoadingLogContent)
        assertEquals("server.log", vm.state.selectedLogFileName)

        testScheduler.runCurrent()

        assertFalse(vm.state.isLoadingLogContent)
        assertEquals("line1\nline2", vm.state.selectedLogFileContent)
        assertEquals(
            listOf(LogLine(index = 0, text = "line1"), LogLine(index = 1, text = "line2")),
            vm.state.selectedLogFileLines,
        )

        // Stop the 5s poll before the test scope closes — runTest's quiescence
        // advance would otherwise spin the rescheduling loop to its timeout.
        vm.clearSelectedLogFile()
        advanceUntilIdle()
    }

    @Test
    fun `unchanged file metadata skips the content re-download`() = runTest(mainDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns Result.success("line1")

        vm.loadLogFile("server.log")
        testScheduler.runCurrent() // initial fetch
        coVerify(exactly = 1) { adminRepository.getLogFileContent("server.log") }

        // One poll tick with unchanged size/dateModified: metadata fast path.
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        coVerify(exactly = 1) { adminRepository.getLogFileContent("server.log") }

        // Stop the 5s poll before the test scope closes — runTest's quiescence
        // advance would otherwise spin the rescheduling loop forever.
        vm.clearSelectedLogFile()
        advanceUntilIdle()
    }

    @Test
    fun `appended lines are marked new and previous lines are reused`() = runTest(mainDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.success("line1\nline2")
        vm.loadLogFile("server.log")
        testScheduler.runCurrent()

        // The file grew: metadata changes, so the poll re-downloads.
        coEvery { adminRepository.getLogFiles() } returns
            Result.success(listOf(LogFile(name = "server.log", size = 20L, dateModified = "t2")))
        coEvery { adminRepository.getLogFileContent("server.log") } returns
            Result.success("line1\nline2\nline3")

        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()

        val lines = vm.state.selectedLogFileLines
        assertEquals(listOf("line1", "line2", "line3"), lines.map { it.text })
        assertEquals(listOf(false, false, true), lines.map { it.isNew })
        assertEquals(listOf(0, 1, 2), lines.map { it.index })

        vm.clearSelectedLogFile()
        advanceUntilIdle()
        coVerify(exactly = 2) { adminRepository.getLogFileContent("server.log") }
    }

    // ── polling flag + clear ──

    @Test
    fun `toggleLogPolling pauses and resumes the poll`() = runTest(mainDispatcher) {
        val vm = loadedViewModel()
        assertTrue(vm.state.isLogPollingActive)
        vm.toggleLogPolling()
        assertFalse(vm.state.isLogPollingActive)
        vm.toggleLogPolling()
        assertTrue(vm.state.isLogPollingActive)
    }

    @Test
    fun `clear resets selection and stops the poll job`() = runTest(mainDispatcher) {
        val vm = loadedViewModel()
        coEvery { adminRepository.getLogFileContent("server.log") } returns Result.success("line1")
        vm.loadLogFile("server.log")
        testScheduler.runCurrent()
        vm.toggleLogPolling()
        assertFalse(vm.state.isLogPollingActive)

        vm.clearSelectedLogFile()

        assertNull(vm.state.selectedLogFileName)
        assertNull(vm.state.selectedLogFileContent)
        assertEquals(emptyList(), vm.state.selectedLogFileLines)
        // The polling flag resets so the next selection starts live.
        assertTrue(vm.state.isLogPollingActive)
        // Completes only because the poll job was cancelled — otherwise the
        // 5s loop would keep the scheduler busy forever.
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.getLogFileContent("server.log") }
    }
}
