package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.PluginConfigPage
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val realtimeTasks: ScheduledTasksRealtimeChannel = mockk(relaxed = true)
    private val engine: JellyfinApiEngine = mockk(relaxed = true)
    private val repository = AdminRepositoryImpl(apiClient, engine, realtimeTasks)

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val disabledAdmin = ManagedUser(id = "u-dis", name = "Bob", policy = ManagedUserPolicy(isAdministrator = true, isDisabled = true))
    private val regular = ManagedUser(id = "u-reg", name = "Cara", policy = ManagedUserPolicy(isAdministrator = false))

    @Test
    fun `getSystemInfo passes success through`() = runTest {
        val info = SystemInfo(serverName = "Jelly", version = "10.9.11")
        coEvery { apiClient.getSystemInfo() } returns Result.success(info)

        val result = repository.getSystemInfo()

        assertTrue(result.isSuccess)
        assertEquals(info, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSystemInfo() }
    }

    @Test
    fun `getSystemInfo passes failure through`() = runTest {
        val error = Exception("server unreachable")
        coEvery { apiClient.getSystemInfo() } returns Result.failure(error)

        val result = repository.getSystemInfo()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `getUsersOverview joins users with current user id`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, regular))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        val result = repository.getUsersOverview()

        assertTrue(result.isSuccess)
        val overview = result.getOrNull()!!
        assertEquals(listOf(admin, regular), overview.users)
        assertEquals("u-admin", overview.currentUserId)
        assertEquals(1, overview.adminCount)
    }

    @Test
    fun `getUsersOverview adminCount excludes disabled admins`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin, regular))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        val overview = repository.getUsersOverview().getOrNull()!!

        assertEquals(1, overview.adminCount)
    }

    @Test
    fun `getUsersOverview tolerates current-user-id failure`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin))
        coEvery { apiClient.getCurrentUserId() } returns Result.failure(Exception("no session"))

        val overview = repository.getUsersOverview().getOrNull()!!

        assertEquals(null, overview.currentUserId)
        assertEquals(listOf(admin), overview.users)
    }

    @Test
    fun `getUsersOverview fails when the user list fails`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.failure(Exception("403"))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        assertTrue(repository.getUsersOverview().isFailure)
    }

    @Test
    fun `getUserEditorContext succeeds with full join`() = runTest {
        val libs = listOf(LibraryFolder(id = "lib-1", name = "Movies"))
        coEvery { apiClient.getManagedUser("u-reg") } returns Result.success(regular)
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.success(libs)
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin, regular))

        val context = repository.getUserEditorContext("u-reg").getOrNull()!!

        assertEquals(regular, context.user)
        assertEquals(libs, context.libraries)
        assertEquals("u-admin", context.currentUserId)
        assertEquals(1, context.adminCount)
    }

    @Test
    fun `getUserEditorContext degrades libraries and me on partial failure`() = runTest {
        coEvery { apiClient.getManagedUser("u-reg") } returns Result.success(regular)
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.failure(Exception("libs boom"))
        coEvery { apiClient.getCurrentUserId() } returns Result.failure(Exception("no session"))
        coEvery { apiClient.getManagedUsers() } returns Result.failure(Exception("users boom"))

        val context = repository.getUserEditorContext("u-reg").getOrNull()!!

        assertEquals(regular, context.user)
        assertEquals(emptyList<LibraryFolder>(), context.libraries)
        assertEquals(null, context.currentUserId)
        assertEquals(0, context.adminCount)
    }

    @Test
    fun `getUserEditorContext fails when the target user fails`() = runTest {
        coEvery { apiClient.getManagedUser("missing") } returns Result.failure(Exception("404"))
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.success(emptyList())
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")
        coEvery { apiClient.getManagedUsers() } returns Result.success(emptyList())

        assertTrue(repository.getUserEditorContext("missing").isFailure)
    }

    @Test
    fun `user mutations delegate to the client`() = runTest {
        coEvery { apiClient.createUser("Dave", null) } returns Result.success(regular)
        coEvery { apiClient.renameUser("u-reg", "Dave2") } returns Result.success(regular.copy(name = "Dave2"))
        coEvery { apiClient.updateUserPolicy("u-reg", any()) } returns Result.success(Unit)
        coEvery { apiClient.updateUserPassword("u-reg", "pw") } returns Result.success(Unit)
        coEvery { apiClient.deleteUser("u-reg") } returns Result.success(Unit)

        repository.createUser("Dave", null)
        repository.renameUser("u-reg", "Dave2")
        repository.updateUserPolicy("u-reg", ManagedUserPolicy())
        repository.updateUserPassword("u-reg", "pw")
        repository.deleteUser("u-reg")

        coVerify(exactly = 1) { apiClient.createUser("Dave", null) }
        coVerify(exactly = 1) { apiClient.renameUser("u-reg", "Dave2") }
        coVerify(exactly = 1) { apiClient.updateUserPolicy("u-reg", any()) }
        coVerify(exactly = 1) { apiClient.updateUserPassword("u-reg", "pw") }
        coVerify(exactly = 1) { apiClient.deleteUser("u-reg") }
    }

    @Test
    fun `device operations delegate to the client`() = runTest {
        val devices = listOf(DeviceInfo(id = "d1", name = "Phone"))
        coEvery { apiClient.getDevices() } returns Result.success(devices)
        coEvery { apiClient.updateDeviceOptions("d1", "Living Room") } returns Result.success(Unit)
        coEvery { apiClient.deleteDevice("d1") } returns Result.success(Unit)

        repository.getDevices()
        repository.renameDevice("d1", "Living Room")
        repository.deleteDevice("d1")

        coVerify(exactly = 1) { apiClient.getDevices() }
        coVerify(exactly = 1) { apiClient.updateDeviceOptions("d1", "Living Room") }
        coVerify(exactly = 1) { apiClient.deleteDevice("d1") }
    }

    @Test
    fun `scheduled task operations delegate with the hidden filter`() = runTest {
        val tasks = listOf(ScheduledTaskInfo(name = "Scan", key = "RefreshLibrary"))
        coEvery { apiClient.getScheduledTasks(isHidden = false) } returns Result.success(tasks)
        coEvery { apiClient.startTask("RefreshLibrary") } returns Result.success(Unit)
        coEvery { apiClient.cancelTask("RefreshLibrary") } returns Result.success(Unit)

        repository.getScheduledTasks(isHidden = false)
        repository.startTask("RefreshLibrary")
        repository.cancelTask("RefreshLibrary")

        coVerify(exactly = 1) { apiClient.getScheduledTasks(isHidden = false) }
        coVerify(exactly = 1) { apiClient.startTask("RefreshLibrary") }
        coVerify(exactly = 1) { apiClient.cancelTask("RefreshLibrary") }
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
        coEvery { apiClient.getSystemInfo() } returns Result.failure(Exception("sys boom"))
        coEvery { apiClient.getItemCounts() } returns Result.success(ItemCounts(movieCount = 3))
        coEvery { apiClient.getSessions() } returns Result.failure(Exception("sessions boom"))
        coEvery { apiClient.getActivityLogEntries(limit = 10) } returns Result.success(emptyList())
        coEvery { apiClient.getScheduledTasks() } returns Result.success(emptyList())

        val summary = repository.getDashboardSummary().getOrNull()!!

        assertEquals(null, summary.systemInfo)
        assertEquals(3L, summary.itemCounts?.movieCount)
        assertEquals(emptyList<com.raulshma.jellyplay.core.model.SessionInfo>(), summary.sessions)
        assertEquals(emptyList<com.raulshma.jellyplay.core.model.ActivityLogEntry>(), summary.recentActivity)
    }

    @Test
    fun `getDashboardSummary fires all five endpoints`() = runTest {
        coEvery { apiClient.getSystemInfo() } returns Result.success(SystemInfo(serverName = "Jelly"))
        coEvery { apiClient.getItemCounts() } returns Result.success(ItemCounts())
        coEvery { apiClient.getSessions() } returns Result.success(emptyList())
        coEvery { apiClient.getActivityLogEntries(limit = 10) } returns Result.success(emptyList())
        coEvery { apiClient.getScheduledTasks() } returns Result.success(emptyList())

        val summary = repository.getDashboardSummary().getOrNull()!!

        assertEquals("Jelly", summary.systemInfo?.serverName)
        coVerify(exactly = 1) { apiClient.getSystemInfo() }
        coVerify(exactly = 1) { apiClient.getItemCounts() }
        coVerify(exactly = 1) { apiClient.getSessions() }
        coVerify(exactly = 1) { apiClient.getActivityLogEntries(limit = 10) }
        coVerify(exactly = 1) { apiClient.getScheduledTasks() }
    }

    @Test
    fun `startLibraryScan starts the task found by key`() = runTest {
        val scanTask = ScheduledTaskInfo(id = "task-1", key = "RefreshLibrary", name = "Scan Media Library")
        coEvery { apiClient.getScheduledTasks() } returns Result.success(listOf(scanTask))
        coEvery { apiClient.startTask("task-1") } returns Result.success(Unit)

        val result = repository.startLibraryScan()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiClient.startTask("task-1") }
        coVerify(exactly = 0) { apiClient.scanLibrary() }
    }

    @Test
    fun `startLibraryScan falls back to name match when key is absent`() = runTest {
        val scanTask = ScheduledTaskInfo(id = "task-2", key = "SomethingElse", name = "Scan Media Library")
        coEvery { apiClient.getScheduledTasks() } returns Result.success(listOf(scanTask))
        coEvery { apiClient.startTask("task-2") } returns Result.success(Unit)

        repository.startLibraryScan()

        coVerify(exactly = 1) { apiClient.startTask("task-2") }
    }

    @Test
    fun `startLibraryScan falls back to the refresh endpoint when no task matches`() = runTest {
        coEvery { apiClient.getScheduledTasks() } returns Result.success(emptyList())
        coEvery { apiClient.scanLibrary() } returns Result.success(Unit)

        val result = repository.startLibraryScan()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { apiClient.startTask(any()) }
        coVerify(exactly = 1) { apiClient.scanLibrary() }
    }

    @Test
    fun `server power and session operations delegate`() = runTest {
        coEvery { apiClient.restartServer() } returns Result.success(Unit)
        coEvery { apiClient.shutdownServer() } returns Result.success(Unit)
        coEvery { apiClient.stopSession("s1") } returns Result.success(Unit)
        coEvery { apiClient.sendMessageToSession("s1", "Header", "Body") } returns Result.success(Unit)

        repository.restartServer()
        repository.shutdownServer()
        repository.stopSession("s1")
        repository.sendMessageToSession("s1", "Header", "Body")

        coVerify(exactly = 1) { apiClient.restartServer() }
        coVerify(exactly = 1) { apiClient.shutdownServer() }
        coVerify(exactly = 1) { apiClient.stopSession("s1") }
        coVerify(exactly = 1) { apiClient.sendMessageToSession("s1", "Header", "Body") }
    }

    @Test
    fun `plugin operations delegate to the client`() = runTest {
        val plugin = PluginInfo(id = "p1", name = "Webhooks", version = "1.0")
        coEvery { apiClient.getInstalledPlugins() } returns Result.success(listOf(plugin))
        coEvery { apiClient.enablePlugin("p1", "1.0") } returns Result.success(Unit)
        coEvery { apiClient.disablePlugin("p1", "1.0") } returns Result.success(Unit)
        coEvery { apiClient.uninstallPlugin("p1") } returns Result.success(Unit)
        coEvery { apiClient.getPackageInstallations() } returns Result.success(emptyList())
        coEvery { apiClient.getRepositories() } returns Result.success(emptyList())
        val repos = listOf(PluginRepository(name = "Official", url = "https://repo", isEnabled = true))
        coEvery { apiClient.setRepositories(repos) } returns Result.success(Unit)

        repository.getInstalledPlugins()
        repository.setPluginEnabled("p1", "1.0", enabled = true)
        repository.setPluginEnabled("p1", "1.0", enabled = false)
        repository.uninstallPlugin("p1")
        repository.getPackageInstallations()
        repository.getRepositories()
        repository.setRepositories(repos)

        coVerify(exactly = 1) { apiClient.getInstalledPlugins() }
        coVerify(exactly = 1) { apiClient.enablePlugin("p1", "1.0") }
        coVerify(exactly = 1) { apiClient.disablePlugin("p1", "1.0") }
        coVerify(exactly = 1) { apiClient.uninstallPlugin("p1") }
        coVerify(exactly = 1) { apiClient.getPackageInstallations() }
        coVerify(exactly = 1) { apiClient.getRepositories() }
        coVerify(exactly = 1) { apiClient.setRepositories(repos) }
    }

    @Test
    fun `getPluginConfigPage resolves page name to HTML`() = runTest {
        coEvery { apiClient.getConfigurationPages() } returns Result.success(
            listOf(PluginConfigPage(name = "Webhooks", pluginId = "p1")),
        )
        coEvery { apiClient.getDashboardConfigurationPage("Webhooks") } returns Result.success("<html/>")

        val page = repository.getPluginConfigPage("p1").getOrNull()!!

        assertEquals("Webhooks", page.name)
        assertEquals("<html/>", page.html)
    }

    @Test
    fun `getPluginConfigPage returns null when plugin has no page`() = runTest {
        coEvery { apiClient.getConfigurationPages() } returns Result.success(emptyList())

        assertEquals(null, repository.getPluginConfigPage("p1").getOrNull())
    }

    @Test
    fun `getPluginConfigPage fails when the pages lookup fails`() = runTest {
        coEvery { apiClient.getConfigurationPages() } returns Result.failure(Exception("403"))

        assertTrue(repository.getPluginConfigPage("p1").isFailure)
    }

    @Test
    fun `pluginWebViewSession carries engine session state and HTTP client`() {
        val okHttp = OkHttpClient()
        every { engine.currentServer } returns MutableStateFlow(ServerInfo(id = "s1", name = "Srv", address = "https://srv"))
        every { engine.currentUser } returns MutableStateFlow(
            UserInfo(id = "u1", name = "me", serverAddress = "https://srv", accessToken = "tok", serverId = "s1"),
        )
        every { engine.okHttpClient } returns okHttp

        val session = repository.pluginWebViewSession

        assertEquals("https://srv", session.serverAddress)
        assertEquals("u1", session.userId)
        assertEquals("tok", session.accessToken)
        assertSame(okHttp, session.okHttpClient)
    }
}
