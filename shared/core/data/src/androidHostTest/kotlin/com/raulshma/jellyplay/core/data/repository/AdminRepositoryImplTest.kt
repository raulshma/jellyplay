package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRepositoryImplTest {

    // D5: the ctor takes the family singles directly — this suite only
    // exercises the admin + user families (relaxed, so unstubbed
    // pass-throughs like the live-tv/library tags members stay harmless).
    private val adminApiClient: AdminApiClient = mockk(relaxed = true)
    private val userApiClient: UserApiClient = mockk(relaxed = true)
    private val realtimeTasks: ScheduledTasksRealtimeChannel = mockk(relaxed = true)
    private val engine: JellyfinApiEngine = mockk(relaxed = true)
    private val activityLogChannel: ActivityLogRealtimeChannel = mockk(relaxed = true)
    private val repository = AdminRepositoryImpl(
        adminApiClient = adminApiClient,
        userApiClient = userApiClient,
        liveTvApiClient = mockk<LiveTvApiClient>(relaxed = true),
        libraryApiClient = mockk<LibraryApiClient>(relaxed = true),
        engine = engine,
        realtimeTasks = realtimeTasks,
        activityLogRealtimeChannel = activityLogChannel,
    )

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val disabledAdmin = ManagedUser(id = "u-dis", name = "Bob", policy = ManagedUserPolicy(isAdministrator = true, isDisabled = true))
    private val regular = ManagedUser(id = "u-reg", name = "Cara", policy = ManagedUserPolicy(isAdministrator = false))

    @Test
    fun `getSystemInfo passes success through`() = runTest {
        val info = SystemInfo(serverName = "Jelly", version = "10.9.11")
        coEvery { adminApiClient.getSystemInfo() } returns Result.success(info)

        val result = repository.getSystemInfo()

        assertTrue(result.isSuccess)
        assertEquals(info, result.getOrNull())
        coVerify(exactly = 1) { adminApiClient.getSystemInfo() }
    }

    @Test
    fun `getSystemInfo passes failure through`() = runTest {
        val error = Exception("server unreachable")
        coEvery { adminApiClient.getSystemInfo() } returns Result.failure(error)

        val result = repository.getSystemInfo()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `getUsersOverview joins users with current user id`() = runTest {
        coEvery { userApiClient.getManagedUsers() } returns Result.success(listOf(admin, regular))
        coEvery { userApiClient.getCurrentUserId() } returns Result.success("u-admin")

        val result = repository.getUsersOverview()

        assertTrue(result.isSuccess)
        val overview = result.getOrNull()!!
        assertEquals(listOf(admin, regular), overview.users)
        assertEquals("u-admin", overview.currentUserId)
        assertEquals(1, overview.adminCount)
    }

    @Test
    fun `getUsersOverview adminCount excludes disabled admins`() = runTest {
        coEvery { userApiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin, regular))
        coEvery { userApiClient.getCurrentUserId() } returns Result.success("u-admin")

        val overview = repository.getUsersOverview().getOrNull()!!

        assertEquals(1, overview.adminCount)
    }

    @Test
    fun `getUsersOverview tolerates current-user-id failure`() = runTest {
        coEvery { userApiClient.getManagedUsers() } returns Result.success(listOf(admin))
        coEvery { userApiClient.getCurrentUserId() } returns Result.failure(Exception("no session"))

        val overview = repository.getUsersOverview().getOrNull()!!

        assertEquals(null, overview.currentUserId)
        assertEquals(listOf(admin), overview.users)
    }

    @Test
    fun `getUsersOverview fails when the user list fails`() = runTest {
        coEvery { userApiClient.getManagedUsers() } returns Result.failure(Exception("403"))
        coEvery { userApiClient.getCurrentUserId() } returns Result.success("u-admin")

        assertTrue(repository.getUsersOverview().isFailure)
    }

    @Test
    fun `getUserEditorContext succeeds with full join`() = runTest {
        val libs = listOf(LibraryFolder(id = "lib-1", name = "Movies"))
        coEvery { userApiClient.getManagedUser("u-reg") } returns Result.success(regular)
        coEvery { userApiClient.getLibraryFoldersForEditor() } returns Result.success(libs)
        coEvery { userApiClient.getCurrentUserId() } returns Result.success("u-admin")
        coEvery { userApiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin, regular))

        val context = repository.getUserEditorContext("u-reg").getOrNull()!!

        assertEquals(regular, context.user)
        assertEquals(libs, context.libraries)
        assertEquals("u-admin", context.currentUserId)
        assertEquals(1, context.adminCount)
    }

    @Test
    fun `getUserEditorContext degrades libraries and me on partial failure`() = runTest {
        coEvery { userApiClient.getManagedUser("u-reg") } returns Result.success(regular)
        coEvery { userApiClient.getLibraryFoldersForEditor() } returns Result.failure(Exception("libs boom"))
        coEvery { userApiClient.getCurrentUserId() } returns Result.failure(Exception("no session"))
        coEvery { userApiClient.getManagedUsers() } returns Result.failure(Exception("users boom"))

        val context = repository.getUserEditorContext("u-reg").getOrNull()!!

        assertEquals(regular, context.user)
        assertEquals(emptyList<LibraryFolder>(), context.libraries)
        assertEquals(null, context.currentUserId)
        assertEquals(0, context.adminCount)
    }

    @Test
    fun `getUserEditorContext fails when the target user fails`() = runTest {
        coEvery { userApiClient.getManagedUser("missing") } returns Result.failure(Exception("404"))
        coEvery { userApiClient.getLibraryFoldersForEditor() } returns Result.success(emptyList())
        coEvery { userApiClient.getCurrentUserId() } returns Result.success("u-admin")
        coEvery { userApiClient.getManagedUsers() } returns Result.success(emptyList())

        assertTrue(repository.getUserEditorContext("missing").isFailure)
    }

    @Test
    fun `user mutations delegate to the client`() = runTest {
        coEvery { userApiClient.createUser("Dave", null) } returns Result.success(regular)
        coEvery { userApiClient.renameUser("u-reg", "Dave2") } returns Result.success(regular.copy(name = "Dave2"))
        coEvery { userApiClient.updateUserPolicy("u-reg", any()) } returns Result.success(Unit)
        coEvery { userApiClient.updateUserPassword("u-reg", "pw") } returns Result.success(Unit)
        coEvery { userApiClient.deleteUser("u-reg") } returns Result.success(Unit)

        repository.createUser("Dave", null)
        repository.renameUser("u-reg", "Dave2")
        repository.updateUserPolicy("u-reg", ManagedUserPolicy())
        repository.updateUserPassword("u-reg", "pw")
        repository.deleteUser("u-reg")

        coVerify(exactly = 1) { userApiClient.createUser("Dave", null) }
        coVerify(exactly = 1) { userApiClient.renameUser("u-reg", "Dave2") }
        coVerify(exactly = 1) { userApiClient.updateUserPolicy("u-reg", any()) }
        coVerify(exactly = 1) { userApiClient.updateUserPassword("u-reg", "pw") }
        coVerify(exactly = 1) { userApiClient.deleteUser("u-reg") }
    }

    @Test
    fun `device operations delegate to the client`() = runTest {
        val devices = listOf(DeviceInfo(id = "d1", name = "Phone"))
        coEvery { adminApiClient.getDevices() } returns Result.success(devices)
        coEvery { adminApiClient.updateDeviceOptions("d1", "Living Room") } returns Result.success(Unit)
        coEvery { adminApiClient.deleteDevice("d1") } returns Result.success(Unit)

        repository.getDevices()
        repository.renameDevice("d1", "Living Room")
        repository.deleteDevice("d1")

        coVerify(exactly = 1) { adminApiClient.getDevices() }
        coVerify(exactly = 1) { adminApiClient.updateDeviceOptions("d1", "Living Room") }
        coVerify(exactly = 1) { adminApiClient.deleteDevice("d1") }
    }

    @Test
    fun `scheduled task operations delegate with the hidden filter`() = runTest {
        val tasks = listOf(ScheduledTaskInfo(name = "Scan", key = "RefreshLibrary"))
        coEvery { adminApiClient.getScheduledTasks(isHidden = false) } returns Result.success(tasks)
        coEvery { adminApiClient.startTask("RefreshLibrary") } returns Result.success(Unit)
        coEvery { adminApiClient.cancelTask("RefreshLibrary") } returns Result.success(Unit)

        repository.getScheduledTasks(isHidden = false)
        repository.startTask("RefreshLibrary")
        repository.cancelTask("RefreshLibrary")

        coVerify(exactly = 1) { adminApiClient.getScheduledTasks(isHidden = false) }
        coVerify(exactly = 1) { adminApiClient.startTask("RefreshLibrary") }
        coVerify(exactly = 1) { adminApiClient.cancelTask("RefreshLibrary") }
    }

    @Test
    fun `realtime task flows surface the channel's pushes`() = runTest {
        val scanTask = ScheduledTaskInfo(name = "Scan media library", key = "RefreshLibrary")
        val otherTask = ScheduledTaskInfo(name = "Optimize", key = "OptimizeDatabase")
        every { realtimeTasks.tasks } returns flowOf(listOf(scanTask, otherTask))
        every { realtimeTasks.scanLibraryTask } returns flowOf(scanTask)

        assertEquals(listOf(scanTask, otherTask), repository.scheduledTasks.first())
        assertEquals(scanTask, repository.libraryScanTask.first())
    }

    @Test
    fun `getDashboardSummary degrades individual endpoint failures`() = runTest {
        coEvery { adminApiClient.getSystemInfo() } returns Result.failure(Exception("sys boom"))
        coEvery { adminApiClient.getItemCounts() } returns Result.success(ItemCounts(movieCount = 3))
        coEvery { adminApiClient.getSessions() } returns Result.failure(Exception("sessions boom"))
        coEvery { adminApiClient.getActivityLogEntries(limit = 10) } returns Result.success(emptyList())
        coEvery { adminApiClient.getScheduledTasks() } returns Result.success(emptyList())

        val summary = repository.getDashboardSummary().getOrNull()!!

        assertEquals(null, summary.systemInfo)
        assertEquals(3L, summary.itemCounts?.movieCount)
        assertEquals(emptyList<com.raulshma.jellyplay.core.model.SessionInfo>(), summary.sessions)
        assertEquals(emptyList<com.raulshma.jellyplay.core.model.ActivityLogEntry>(), summary.recentActivity)
    }

    @Test
    fun `getDashboardSummary fires all five endpoints`() = runTest {
        coEvery { adminApiClient.getSystemInfo() } returns Result.success(SystemInfo(serverName = "Jelly"))
        coEvery { adminApiClient.getItemCounts() } returns Result.success(ItemCounts())
        coEvery { adminApiClient.getSessions() } returns Result.success(emptyList())
        coEvery { adminApiClient.getActivityLogEntries(limit = 10) } returns Result.success(emptyList())
        coEvery { adminApiClient.getScheduledTasks() } returns Result.success(emptyList())

        val summary = repository.getDashboardSummary().getOrNull()!!

        assertEquals("Jelly", summary.systemInfo?.serverName)
        coVerify(exactly = 1) { adminApiClient.getSystemInfo() }
        coVerify(exactly = 1) { adminApiClient.getItemCounts() }
        coVerify(exactly = 1) { adminApiClient.getSessions() }
        coVerify(exactly = 1) { adminApiClient.getActivityLogEntries(limit = 10) }
        coVerify(exactly = 1) { adminApiClient.getScheduledTasks() }
    }

    @Test
    fun `startLibraryScan starts the task found by key`() = runTest {
        val scanTask = ScheduledTaskInfo(id = "task-1", key = "RefreshLibrary", name = "Scan Media Library")
        coEvery { adminApiClient.getScheduledTasks() } returns Result.success(listOf(scanTask))
        coEvery { adminApiClient.startTask("task-1") } returns Result.success(Unit)

        val result = repository.startLibraryScan()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { adminApiClient.startTask("task-1") }
        coVerify(exactly = 0) { adminApiClient.scanLibrary() }
    }

    @Test
    fun `startLibraryScan falls back to name match when key is absent`() = runTest {
        val scanTask = ScheduledTaskInfo(id = "task-2", key = "SomethingElse", name = "Scan Media Library")
        coEvery { adminApiClient.getScheduledTasks() } returns Result.success(listOf(scanTask))
        coEvery { adminApiClient.startTask("task-2") } returns Result.success(Unit)

        repository.startLibraryScan()

        coVerify(exactly = 1) { adminApiClient.startTask("task-2") }
    }

    @Test
    fun `startLibraryScan falls back to the refresh endpoint when no task matches`() = runTest {
        coEvery { adminApiClient.getScheduledTasks() } returns Result.success(emptyList())
        coEvery { adminApiClient.scanLibrary() } returns Result.success(Unit)

        val result = repository.startLibraryScan()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { adminApiClient.startTask(any()) }
        coVerify(exactly = 1) { adminApiClient.scanLibrary() }
    }

    @Test
    fun `server power and session operations delegate`() = runTest {
        coEvery { adminApiClient.restartServer() } returns Result.success(Unit)
        coEvery { adminApiClient.shutdownServer() } returns Result.success(Unit)
        coEvery { adminApiClient.stopSession("s1") } returns Result.success(Unit)
        coEvery { adminApiClient.sendMessageToSession("s1", "Header", "Body") } returns Result.success(Unit)

        repository.restartServer()
        repository.shutdownServer()
        repository.stopSession("s1")
        repository.sendMessageToSession("s1", "Header", "Body")

        coVerify(exactly = 1) { adminApiClient.restartServer() }
        coVerify(exactly = 1) { adminApiClient.shutdownServer() }
        coVerify(exactly = 1) { adminApiClient.stopSession("s1") }
        coVerify(exactly = 1) { adminApiClient.sendMessageToSession("s1", "Header", "Body") }
    }

    @Test
    fun `log REST operations delegate to the client`() = runTest {
        coEvery { adminApiClient.getLogFiles() } returns Result.success(emptyList())
        coEvery { adminApiClient.getLogFileContent("log.txt") } returns Result.success("line")
        coEvery { adminApiClient.getActivityLogEntries(startIndex = 10, limit = 50) } returns Result.success(emptyList())

        repository.getLogFiles()
        repository.getLogFileContent("log.txt")
        repository.getActivityLogEntries(startIndex = 10, limit = 50)

        coVerify(exactly = 1) { adminApiClient.getLogFiles() }
        coVerify(exactly = 1) { adminApiClient.getLogFileContent("log.txt") }
        coVerify(exactly = 1) { adminApiClient.getActivityLogEntries(startIndex = 10, limit = 50) }
    }

    @Test
    fun `liveActivityEntries delegates to the realtime channel with the seed ids`() = runTest {
        val entry = com.raulshma.jellyplay.core.model.ActivityLogEntry(id = 7L, name = "Login")
        every { activityLogChannel.entries(setOf(7L)) } returns flowOf(entry)

        val first = repository.liveActivityEntries(setOf(7L)).first()

        assertEquals(entry, first)
    }
}
