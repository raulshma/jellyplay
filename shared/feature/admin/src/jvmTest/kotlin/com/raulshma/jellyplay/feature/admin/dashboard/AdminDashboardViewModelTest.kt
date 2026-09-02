package com.raulshma.jellyplay.feature.admin.dashboard

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.AdminDashboardSummary
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminDashboardViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    /** No replay: like the production channel, nothing emits before the first push. */
    private val scanTaskFlow = kotlinx.coroutines.flow.MutableSharedFlow<ScheduledTaskInfo?>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    /** replay = 1 mirrors the production channel's shareIn(replay = 1). */
    private val tasksFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<ScheduledTaskInfo>>(
        replay = 1,
        extraBufferCapacity = 8,
    )

    private val scanTask = ScheduledTaskInfo(
        id = "task-1",
        key = "RefreshLibrary",
        name = "Scan Media Library",
        state = TaskState.IDLE,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk()
        every { adminRepository.libraryScanTask } returns scanTaskFlow
        every { adminRepository.scheduledTasks } returns tasksFlow
        // Default: no successful push ever (dead socket) — REST snapshots seed.
        every { adminRepository.scheduledTasksLastPushAtMs } returns 0L
    }

    private fun summary(
        systemInfo: SystemInfo? = SystemInfo(serverName = "Jelly"),
        itemCounts: ItemCounts? = ItemCounts(),
        sessions: List<SessionInfo> = emptyList(),
        tasks: List<ScheduledTaskInfo> = listOf(scanTask),
    ) = AdminDashboardSummary(
        systemInfo = systemInfo,
        itemCounts = itemCounts,
        sessions = sessions,
        recentActivity = emptyList(),
        tasks = tasks,
    )

    @Test
    fun `load populates all sections from the summary`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Jelly", state.systemInfo?.serverName)
        assertTrue(!state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `summary-level null telemetry degrades without error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns
            Result.success(summary(systemInfo = null, itemCounts = null))

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.systemInfo)
        assertNull(state.error)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `running tasks are derived from the summary task snapshot`() = runTest(mainDispatcher) {
        val running = scanTask.copy(state = TaskState.RUNNING)
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary(tasks = listOf(running)))

        val viewModel = AdminDashboardViewModel(adminRepository)
        // The REST snapshot seeds runningTasks until the first WS push lands
        // (none is emitted here), so the post-load state carries it as-is.
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(running), state.runningTasks)
        advanceUntilIdle()
    }

    @Test
    fun `WS pushes update running tasks and drop idle ones`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary(tasks = emptyList()))
        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        val running = scanTask.copy(state = TaskState.RUNNING, currentProgressPercentage = 25.0)
        tasksFlow.tryEmit(listOf(scanTask, running))
        advanceUntilIdle()
        assertEquals(listOf(running), viewModel.uiState.value.runningTasks)

        // Task finished: the next push clears the card.
        tasksFlow.tryEmit(listOf(scanTask))
        advanceUntilIdle()
        assertEquals(emptyList<ScheduledTaskInfo>(), viewModel.uiState.value.runningTasks)
    }

    @Test
    fun `WS pushes exclude hidden tasks from the running list`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary(tasks = emptyList()))
        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        val hiddenRunning = ScheduledTaskInfo(
            id = "task-2",
            key = "SecretCleanup",
            name = "Secret Cleanup",
            state = TaskState.RUNNING,
            isHidden = true,
        )
        tasksFlow.tryEmit(listOf(hiddenRunning))
        advanceUntilIdle()

        // Hidden tasks are excluded on both the REST seed and the WS push
        // (see AdminDashboardViewModel.running()).
        assertEquals(emptyList<ScheduledTaskInfo>(), viewModel.uiState.value.runningTasks)
    }

    @Test
    fun `REST snapshot does not overwrite running tasks after a WS push`() = runTest(mainDispatcher) {
        val snapshotRunning = scanTask.copy(state = TaskState.RUNNING)
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary(tasks = listOf(snapshotRunning)))

        val viewModel = AdminDashboardViewModel(adminRepository)
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(snapshotRunning), state.runningTasks) // seeded first frame

        // WS pushes a newer list — it takes over immediately.
        val wsRunning = ScheduledTaskInfo(
            id = "task-2",
            key = "OptimizeDatabase",
            name = "Optimize Database",
            state = TaskState.RUNNING,
        )
        tasksFlow.tryEmit(listOf(scanTask, wsRunning))
        advanceUntilIdle()
        assertEquals(listOf(wsRunning), viewModel.uiState.value.runningTasks)

        // A later loadDashboard() carries the (now stale) snapshot: the socket
        // owns the field, so the UI must not flicker back to the old list.
        every { adminRepository.scheduledTasksLastPushAtMs } returns System.currentTimeMillis()
        viewModel.loadDashboard()
        advanceUntilIdle()
        assertEquals(listOf(wsRunning), viewModel.uiState.value.runningTasks)
    }

    @Test
    fun `stale WS replay does not suppress the REST refresh`() = runTest(mainDispatcher) {
        // Dead socket: the channel's last push is old (timestamp 0), but its
        // shared flow still replays the last list to a new collector.
        val oldRunning = scanTask.copy(state = TaskState.RUNNING, currentProgressPercentage = 10.0)
        tasksFlow.tryEmit(listOf(oldRunning))
        val snapshotRunning = ScheduledTaskInfo(
            id = "task-3",
            key = "OptimizeDatabase",
            name = "Optimize Database",
            state = TaskState.RUNNING,
        )
        // The mocked fetch must land AFTER the replayed emission, as a real
        // REST snapshot always does (network latency vs instant replay).
        coEvery { adminRepository.getDashboardSummary() } coAnswers {
            delay(1)
            Result.success(summary(tasks = listOf(snapshotRunning)))
        }

        val viewModel = AdminDashboardViewModel(adminRepository)
        val state = viewModel.uiState.first { !it.isLoading }

        // The replay paints first, but with no fresh push the REST snapshot
        // must win — a frozen running-tasks card is the bug this guards.
        assertEquals(listOf(snapshotRunning), state.runningTasks)
    }

    @Test
    fun `scan state seeds Idle from an IDLE snapshot task`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        assertEquals(LibraryScanState.Idle, viewModel.uiState.value.libraryScanState)
    }

    @Test
    fun `scan state seeds Running with progress from a RUNNING snapshot task`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(
            summary(tasks = listOf(scanTask.copy(state = TaskState.RUNNING, currentProgressPercentage = 42.0))),
        )

        val viewModel = AdminDashboardViewModel(adminRepository)
        val state = viewModel.uiState.first { !it.isLoading }
        advanceUntilIdle()

        assertEquals(LibraryScanState.Running(progress = 42.0), state.libraryScanState)
    }

    @Test
    fun `WS IDLE push during optimistic window preserves Running`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary(tasks = emptyList()))
        coEvery { adminRepository.startLibraryScan() } returns Result.success(Unit)
        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        viewModel.scanLibrary()
        advanceUntilIdle()
        // Optimistic: Running(null) even though no RUNNING push has arrived.
        assertEquals(LibraryScanState.Running(progress = null), viewModel.uiState.value.libraryScanState)

        // WS pushes IDLE (server hasn't flipped yet) — must stay Running.
        scanTaskFlow.tryEmit(scanTask)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.libraryScanState is LibraryScanState.Running)

        // WS confirms RUNNING with progress — window cleared, real progress shown.
        scanTaskFlow.tryEmit(scanTask.copy(state = TaskState.RUNNING, currentProgressPercentage = 7.5))
        advanceUntilIdle()
        assertEquals(LibraryScanState.Running(progress = 7.5), viewModel.uiState.value.libraryScanState)

        // WS pushes IDLE after RUNNING — scan finished.
        scanTaskFlow.tryEmit(scanTask)
        advanceUntilIdle()
        assertEquals(LibraryScanState.Idle, viewModel.uiState.value.libraryScanState)
    }

    @Test
    fun `WS IDLE push with no optimistic window settles Idle`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        scanTaskFlow.tryEmit(scanTask)
        advanceUntilIdle()

        assertEquals(LibraryScanState.Idle, viewModel.uiState.value.libraryScanState)
    }

    @Test
    fun `scanLibrary delegates to the repository scan operation`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())
        coEvery { adminRepository.startLibraryScan() } returns Result.success(Unit)

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()
        viewModel.scanLibrary()
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.startLibraryScan() }
        assertEquals(LibraryScanState.Running(progress = null), viewModel.uiState.value.libraryScanState)
    }

    @Test
    fun `restart failure surfaces error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())
        coEvery { adminRepository.restartServer() } returns Result.failure(RuntimeException("nope"))

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()
        viewModel.restartServer()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(!viewModel.uiState.value.isRestarting)
    }
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

}
