package com.raulshma.jellyplay.feature.admin.tasks

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskExecutionInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.model.TaskTriggerInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the scheduled-tasks screen flow (`ScheduledTasksViewModel`) — the
 * grouping pure logic is covered by ScheduledTaskGroupingTest and is not
 * duplicated here:
 *
 *  - the REST snapshot loads with `isHidden = false` and drives loading/error
 *    state; start/cancel route the task id and re-fetch to paint the new
 *    state before the next WS push;
 *  - each WS push is MERGED onto the REST snapshot by task key: WS wins for
 *    the live fields (state, progress) while the richer REST metadata
 *    (name, description, category, triggers, last-run result) survives a
 *    partial push that omits them — this is what keeps the last-run row and
 *    trigger chips from flickering off;
 *  - hidden tasks are filtered from WS pushes to match the REST fetch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledTasksViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    /** replay = 1 mirrors the production channel's shareIn(replay = 1). */
    private val wsTasksFlow = MutableSharedFlow<List<ScheduledTaskInfo>>(
        replay = 1,
        extraBufferCapacity = 8,
    )

    private val scanTask = ScheduledTaskInfo(
        id = "task-1",
        key = "RefreshLibrary",
        name = "Scan Media Library",
        state = TaskState.IDLE,
        triggers = listOf(TaskTriggerInfo(type = "Interval", intervalTicks = 1)),
        lastExecutionResult = TaskExecutionInfo(key = "RefreshLibrary", status = "Completed"),
        category = "Library",
        description = "Scans media folders",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk()
        every { adminRepository.scheduledTasks } returns wsTasksFlow
        coEvery { adminRepository.getScheduledTasks(any()) } returns Result.success(listOf(scanTask))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── REST load ──

    @Test
    fun `load populates the task list`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()

        assertFalse(viewModel.state.isLoading)
        assertEquals(listOf(scanTask), viewModel.state.tasks)
        assertNull(viewModel.state.error)
    }

    @Test
    fun `load failure surfaces the error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getScheduledTasks(any()) } returns
            Result.failure(RuntimeException("offline"))

        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()

        assertEquals("offline", viewModel.state.error)
        assertTrue(viewModel.state.tasks.isEmpty())
    }

    @Test
    fun `refresh toggles isRefreshing`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.isRefreshing)
        assertEquals(listOf(scanTask), viewModel.state.tasks)
    }

    // ── start / stop routing ──

    @Test
    fun `startTask routes the id and re-fetches to paint the new state`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()
        coEvery { adminRepository.startTask("task-1") } returns Result.success(Unit)

        viewModel.startTask("task-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.startTask("task-1") }
        // init load + the immediate refresh after start.
        coVerify(exactly = 2) { adminRepository.getScheduledTasks(any()) }
    }

    @Test
    fun `cancelTask routes the id and re-fetches`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()
        coEvery { adminRepository.cancelTask("task-1") } returns Result.success(Unit)

        viewModel.cancelTask("task-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.cancelTask("task-1") }
        coVerify(exactly = 2) { adminRepository.getScheduledTasks(any()) }
    }

    // ── WS merge ──

    @Test
    fun `WS push overlays live fields but keeps richer REST metadata`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()

        // A partial push: live fields present, descriptive fields blank/null.
        val partial = scanTask.copy(
            name = "",
            state = TaskState.RUNNING,
            currentProgressPercentage = 42.0,
            triggers = emptyList(),
            lastExecutionResult = null,
            description = null,
            category = null,
        )
        wsTasksFlow.tryEmit(listOf(partial))
        advanceUntilIdle()

        val merged = viewModel.state.tasks.single()
        assertEquals(TaskState.RUNNING, merged.state)
        assertEquals(42.0, merged.currentProgressPercentage)
        assertEquals("Scan Media Library", merged.name)
        assertEquals(scanTask.triggers, merged.triggers)
        assertNotNull(merged.lastExecutionResult)
        assertEquals("Completed", merged.lastExecutionResult?.status)
        assertEquals("Scans media folders", merged.description)
        assertEquals("Library", merged.category)
    }

    @Test
    fun `WS push values win over the REST snapshot when both are present`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()

        val pushed = scanTask.copy(
            name = "Scan Media Library (renamed)",
            description = "New description",
            currentProgressPercentage = 10.0,
        )
        wsTasksFlow.tryEmit(listOf(pushed))
        advanceUntilIdle()

        val merged = viewModel.state.tasks.single()
        assertEquals("Scan Media Library (renamed)", merged.name)
        assertEquals("New description", merged.description)
    }

    @Test
    fun `WS push filters hidden tasks and admits unknown ones`() = runTest(mainDispatcher) {
        val viewModel = ScheduledTasksViewModel(adminRepository)
        advanceUntilIdle()

        val hidden = ScheduledTaskInfo(
            id = "task-2",
            key = "SecretCleanup",
            name = "Secret Cleanup",
            state = TaskState.RUNNING,
            isHidden = true,
        )
        val fresh = ScheduledTaskInfo(id = "task-3", key = "OptimizeDatabase", name = "Optimize Database")
        wsTasksFlow.tryEmit(listOf(hidden, fresh))
        advanceUntilIdle()

        // Hidden excluded to match the REST isHidden=false fetch; the unknown
        // task passes through as-is.
        assertEquals(listOf("OptimizeDatabase"), viewModel.state.tasks.map { it.key })
    }
}
