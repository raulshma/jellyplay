package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.PluginConfigPage
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [AdminRepositoryImpl]'s composition logic over the API client + realtime
 * channels (pure pass-throughs are not retested):
 *  1. `startLibraryScan` resolves the scan task by the `RefreshLibrary` key
 *     first, then by display name (case-insensitive), and only then falls back
 *     to the bare library-refresh endpoint (which has no progress to poll);
 *  2. `getDashboardSummary` degrades independently — a failing card blanks
 *     its own field (null / empty) while the summary still succeeds;
 *  3. `getUsersOverview` / `getUserEditorContext` count only ACTIVE admins
 *     (administrator AND not disabled) and degrade auxiliary fetches;
 *  4. `getPluginConfigPage` maps found/missing/error cases precisely;
 *  5. the realtime task streams + `pluginWebViewSession` + user image URL
 *     delegate to the engine/channel state.
 */
class AdminRepositoryImplTest {

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var engine: JellyfinApiEngine
    private lateinit var realtimeTasks: ScheduledTasksRealtimeChannel
    private lateinit var activityLogChannel: ActivityLogRealtimeChannel
    private lateinit var repository: AdminRepositoryImpl

    private val currentServer = MutableStateFlow<com.raulshma.jellyplay.core.model.ServerInfo?>(
        com.raulshma.jellyplay.core.model.ServerInfo(
            id = "server-1",
            name = "Test",
            address = "https://server.example.com",
        ),
    )
    private val currentUser = MutableStateFlow<com.raulshma.jellyplay.core.model.UserInfo?>(
        com.raulshma.jellyplay.core.model.UserInfo(
            id = "11111111-1111-4111-8111-111111111111",
            name = "admin",
            serverAddress = "https://server.example.com",
            accessToken = "token-1",
            serverId = "server-1",
        ),
    )

    @BeforeTest
    fun setup() {
        apiClient = mockk()
        engine = mockk()
        realtimeTasks = mockk()
        activityLogChannel = mockk()
        every { engine.currentServer } returns currentServer
        every { engine.currentUser } returns currentUser
        every { engine.okHttpClient } returns OkHttpClient()
        every { realtimeTasks.tasks } returns flowOf(emptyList())
        every { realtimeTasks.scanLibraryTask } returns flowOf(null)
        every { realtimeTasks.lastPushAtMs } returns 0L
        every { activityLogChannel.entries(any()) } returns flowOf()
        repository = AdminRepositoryImpl(apiClient, engine, realtimeTasks, activityLogChannel)
    }

    private fun task(id: String, key: String, name: String) = ScheduledTaskInfo(
        id = id,
        key = key,
        name = name,
        state = TaskState.IDLE,
    )

    @Test
    fun `startLibraryScan prefers the RefreshLibrary task key`() = runTest {
        coEvery { apiClient.getScheduledTasks() } returns Result.success(
            listOf(task("t-key", key = "RefreshLibrary", name = "Other name")),
        )
        coEvery { apiClient.startTask("t-key") } returns Result.success(Unit)

        repository.startLibraryScan()

        coVerifyScanStarted("t-key")
    }

    @Test
    fun `startLibraryScan falls back to a case-insensitive name match`() = runTest {
        coEvery { apiClient.getScheduledTasks() } returns Result.success(
            listOf(task("t-name", key = "different", name = "scan MEDIA library")),
        )
        coEvery { apiClient.startTask("t-name") } returns Result.success(Unit)

        repository.startLibraryScan()

        coVerifyScanStarted("t-name")
    }

    @Test
    fun `startLibraryScan falls back to the bare refresh endpoint without a task`() = runTest {
        coEvery { apiClient.getScheduledTasks() } returns Result.success(emptyList())
        coEvery { apiClient.scanLibrary() } returns Result.success(Unit)

        repository.startLibraryScan()

        coVerify(exactly = 0) { apiClient.startTask(any()) }
        coVerify(exactly = 1) { apiClient.scanLibrary() }
    }

    @Test
    fun `the dashboard summary degrades each failing endpoint independently`() = runTest {
        coEvery { apiClient.getSystemInfo() } returns Result.success(SystemInfo(serverName = "Jelly"))
        coEvery { apiClient.getItemCounts() } returns Result.failure(IllegalStateException("x"))
        coEvery { apiClient.getSessions() } returns Result.failure(IllegalStateException("x"))
        coEvery { apiClient.getActivityLogEntries(startIndex = null, limit = 10) } returns
            Result.failure(IllegalStateException("x"))
        coEvery { apiClient.getScheduledTasks() } returns Result.success(emptyList())

        val summary = repository.getDashboardSummary().getOrThrow()

        assertEquals("Jelly", summary.systemInfo?.serverName)
        assertNull(summary.itemCounts, "a failing card must blank only itself")
        assertTrue(summary.sessions.isEmpty())
        assertTrue(summary.recentActivity.isEmpty())
    }

    @Test
    fun `the dashboard summary still succeeds when everything fails`() = runTest {
        coEvery { apiClient.getSystemInfo() } returns Result.failure(IllegalStateException("down"))
        coEvery { apiClient.getItemCounts() } returns Result.failure(IllegalStateException("down"))
        coEvery { apiClient.getSessions() } returns Result.failure(IllegalStateException("down"))
        coEvery { apiClient.getActivityLogEntries(any(), any()) } returns Result.failure(IllegalStateException("down"))
        coEvery { apiClient.getScheduledTasks(any()) } returns Result.failure(IllegalStateException("down"))

        val summary = repository.getDashboardSummary().getOrThrow()

        assertNull(summary.systemInfo)
        assertTrue(summary.tasks.isEmpty())
    }

    @Test
    fun `the sessions dashboard passes populated lists through`() = runTest {
        val sessions = listOf(SessionInfo(id = "s1"))
        coEvery { apiClient.getSystemInfo() } returns Result.success(SystemInfo())
        coEvery { apiClient.getItemCounts() } returns Result.failure(IllegalStateException("x"))
        coEvery { apiClient.getSessions() } returns Result.success(sessions)
        coEvery { apiClient.getActivityLogEntries(any(), any()) } returns Result.failure(IllegalStateException("x"))
        coEvery { apiClient.getScheduledTasks(any()) } returns Result.failure(IllegalStateException("x"))

        assertEquals(sessions, repository.getDashboardSummary().getOrThrow().sessions)
    }

    @Test
    fun `getUsersOverview counts only active administrators`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(
            listOf(
                user("u1", isAdministrator = true, isDisabled = false),   // counts
                user("u2", isAdministrator = true, isDisabled = true),    // disabled: no
                user("u3", isAdministrator = false, isDisabled = false),  // non-admin: no
            ),
        )
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u1")

        val overview = repository.getUsersOverview().getOrThrow()

        assertEquals(1, overview.adminCount)
        assertEquals("u1", overview.currentUserId)
        assertEquals(3, overview.users.size)
    }

    @Test
    fun `getUserEditorContext degrades failed auxiliary fetches to empty`() = runTest {
        coEvery { apiClient.getManagedUser("u1") } returns Result.success(user("u1", isAdministrator = true))
        coEvery { apiClient.getLibraryFoldersForEditor() } returns
            Result.failure(IllegalStateException("no folders"))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u1")
        coEvery { apiClient.getManagedUsers() } returns
            Result.success(listOf(user("u1", isAdministrator = true)))

        val context = repository.getUserEditorContext("u1").getOrThrow()

        assertEquals("u1", context.user.id)
        assertTrue(context.libraries.isEmpty())
        assertEquals(1, context.adminCount)
    }

    @Test
    fun `getUserEditorContext passes the folder list through when available`() = runTest {
        val folders = listOf(LibraryFolder(id = "f1", name = "Movies", collectionType = "movies"))
        coEvery { apiClient.getManagedUser("u1") } returns Result.success(user("u1", isAdministrator = false))
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.success(folders)
        coEvery { apiClient.getCurrentUserId() } returns Result.failure(IllegalStateException("no session"))
        coEvery { apiClient.getManagedUsers() } returns Result.success(emptyList())

        val context = repository.getUserEditorContext("u1").getOrThrow()

        assertEquals(folders, context.libraries)
        assertNull(context.currentUserId)
        assertEquals(0, context.adminCount)
    }

    @Test
    fun `getPluginConfigPage fetches the html for a matching page`() = runTest {
        coEvery { apiClient.getConfigurationPages() } returns Result.success(
            listOf(
                PluginConfigPage(name = "Other", pluginId = "other-plugin"),
                PluginConfigPage(name = "Config", pluginId = "target-plugin"),
            ),
        )
        coEvery { apiClient.getDashboardConfigurationPage("Config") } returns
            Result.success("<html>body</html>")

        val page = repository.getPluginConfigPage("target-plugin").getOrThrow()

        assertEquals("Config", page!!.name)
        assertEquals("<html>body</html>", page.html)
    }

    @Test
    fun `getPluginConfigPage succeeds with null when no page matches`() = runTest {
        coEvery { apiClient.getConfigurationPages() } returns Result.success(emptyList())

        assertNull(repository.getPluginConfigPage("target-plugin").getOrThrow())
    }

    @Test
    fun `getPluginConfigPage propagates the configuration-pages failure`() = runTest {
        coEvery { apiClient.getConfigurationPages() } returns Result.failure(IllegalStateException("down"))

        assertTrue(repository.getPluginConfigPage("x").isFailure)
    }

    @Test
    fun `scheduled task streams and freshness delegate to the realtime channel`() = runTest {
        val tasks = listOf(task("t1", key = "k", name = "n"))
        every { realtimeTasks.tasks } returns flowOf(tasks)
        every { realtimeTasks.scanLibraryTask } returns flowOf(tasks.first())
        every { realtimeTasks.lastPushAtMs } returns 42L

        assertEquals(tasks, repository.scheduledTasks.firstOrNull())
        assertEquals(tasks.first(), repository.libraryScanTask.firstOrNull())
        assertEquals(42L, repository.scheduledTasksLastPushAtMs)
    }

    @Test
    fun `pluginWebViewSession composes the engine session state`() {
        val session = repository.pluginWebViewSession

        assertEquals("https://server.example.com", session.serverAddress)
        assertEquals("11111111-1111-4111-8111-111111111111", session.userId)
        assertEquals("token-1", session.accessToken)
    }

    @Test
    fun `pluginWebViewSession falls back to empty strings without a session`() {
        currentServer.value = null
        currentUser.value = null

        val session = repository.pluginWebViewSession

        assertEquals("", session.serverAddress)
        assertEquals("", session.userId)
        assertEquals("", session.accessToken)
    }

    @Test
    fun `getUserImageUrl builds the server-scoped primary portrait URL`() {
        val url = repository.getUserImageUrl("11111111-1111-4111-8111-111111111111", tag = "abc", maxWidth = 200)

        assertTrue(url.startsWith("https://server.example.com/Users/"), url)
        assertTrue(url.contains("/Images/Primary"), url)
        assertTrue(url.contains("maxWidth=200"), url)
        assertTrue(url.contains("tag=abc"), url)
    }

    @Test
    fun `getUserImageUrl is empty without a server`() {
        currentServer.value = null

        assertEquals("", repository.getUserImageUrl("11111111-1111-4111-8111-111111111111", tag = "Primary", maxWidth = 400))
    }

    private fun user(id: String, isAdministrator: Boolean, isDisabled: Boolean = false) = ManagedUser(
        id = id,
        name = "User $id",
        policy = ManagedUserPolicy(isAdministrator = isAdministrator, isDisabled = isDisabled),
    )

    private fun coVerifyScanStarted(taskId: String) {
        coVerify(exactly = 1) { apiClient.startTask(taskId) }
        coVerify(exactly = 0) { apiClient.scanLibrary() }
    }
}
