package com.raulshma.jellyplay.feature.admin.dashboard

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.AdminDashboardSummary
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class AdminDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var adminRepository: AdminRepository

    /** No replay: like the production channel, nothing emits before the first push. */
    private val scanTaskFlow = kotlinx.coroutines.flow.MutableSharedFlow<ScheduledTaskInfo?>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    private val scanTask = ScheduledTaskInfo(
        id = "task-1",
        key = "RefreshLibrary",
        name = "Scan Media Library",
        state = TaskState.IDLE,
    )

    @Before
    fun setUp() {
        adminRepository = mockk()
        every { adminRepository.libraryScanTask } returns scanTaskFlow
        coEvery { adminRepository.getScheduledTasks(any()) } returns Result.success(emptyList())
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
    fun `load populates all sections from the summary`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Jelly", state.systemInfo?.serverName)
        assertTrue(!state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `summary-level null telemetry degrades without error`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `running tasks are derived from the summary task snapshot`() = runTest(mainDispatcherRule.testDispatcher) {
        val running = scanTask.copy(state = TaskState.RUNNING)
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary(tasks = listOf(running)))

        val viewModel = AdminDashboardViewModel(adminRepository)
        // Read the post-load state directly: the 15s auto-refresh cycle would
        // otherwise overwrite runningTasks with the (empty) getScheduledTasks stub.
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(running), state.runningTasks)
        advanceUntilIdle() // settle the auto-refresh before teardown
    }

    @Test
    fun `scan state seeds Idle from an IDLE snapshot task`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        assertEquals(LibraryScanState.Idle, viewModel.uiState.value.libraryScanState)
    }

    @Test
    fun `scan state seeds Running with progress from a RUNNING snapshot task`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(
            summary(tasks = listOf(scanTask.copy(state = TaskState.RUNNING, currentProgressPercentage = 42.0))),
        )

        val viewModel = AdminDashboardViewModel(adminRepository)
        val state = viewModel.uiState.first { !it.isLoading }
        advanceUntilIdle()

        assertEquals(LibraryScanState.Running(progress = 42.0), state.libraryScanState)
    }

    @Test
    fun `WS IDLE push during optimistic window preserves Running`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `WS IDLE push with no optimistic window settles Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()

        scanTaskFlow.tryEmit(scanTask)
        advanceUntilIdle()

        assertEquals(LibraryScanState.Idle, viewModel.uiState.value.libraryScanState)
    }

    @Test
    fun `scanLibrary delegates to the repository scan operation`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `restart failure surfaces error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { adminRepository.getDashboardSummary() } returns Result.success(summary())
        coEvery { adminRepository.restartServer() } returns Result.failure(RuntimeException("nope"))

        val viewModel = AdminDashboardViewModel(adminRepository)
        advanceUntilIdle()
        viewModel.restartServer()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(!viewModel.uiState.value.isRestarting)
    }
}
