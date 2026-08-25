package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.AdminDashboardSummary
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.UserEditorContext
import com.raulshma.jellyplay.core.model.UsersOverview
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class AdminRepositoryImpl constructor(
    private val apiClient: JellyfinApiClient,
    private val engine: JellyfinApiEngine,
    private val realtimeTasks: ScheduledTasksRealtimeChannel,
    private val activityLogRealtimeChannel: ActivityLogRealtimeChannel,
) : AdminRepository {

    override val scheduledTasks: Flow<List<ScheduledTaskInfo>>
        get() = realtimeTasks.tasks

    override val scheduledTasksLastPushAtMs: Long
        get() = realtimeTasks.lastPushAtMs

    override val libraryScanTask: Flow<ScheduledTaskInfo?>
        get() = realtimeTasks.scanLibraryTask

    override val pluginWebViewSession: PluginWebViewSession
        get() = PluginWebViewSession(
            serverAddress = engine.currentServer.value?.address.orEmpty(),
            userId = engine.currentUser.value?.id.orEmpty(),
            accessToken = engine.currentUser.value?.accessToken.orEmpty(),
            okHttpClient = engine.okHttpClient,
        )

    override suspend fun getSystemInfo(): Result<SystemInfo> = apiClient.getSystemInfo()

    override suspend fun getUsersOverview(): Result<UsersOverview> = coroutineScope {
        // Independent round-trips — run concurrently (sum → max latency).
        val usersDeferred = async { apiClient.getManagedUsers() }
        val meDeferred = async { apiClient.getCurrentUserId() }
        usersDeferred.await().map { users ->
            UsersOverview(
                users = users,
                currentUserId = meDeferred.await().getOrNull(),
                adminCount = users.activeAdminCount(),
            )
        }
    }

    override suspend fun createUser(name: String, password: String?): Result<ManagedUser> =
        apiClient.createUser(name, password)

    override suspend fun deleteUser(userId: String): Result<Unit> =
        apiClient.deleteUser(userId)

    override suspend fun getUserEditorContext(userId: String): Result<UserEditorContext> = coroutineScope {
        // All four round-trips are independent — run concurrently.
        val userDeferred = async { apiClient.getManagedUser(userId) }
        val libsDeferred = async { apiClient.getLibraryFoldersForEditor() }
        val meDeferred = async { apiClient.getCurrentUserId() }
        val allUsersDeferred = async { apiClient.getManagedUsers() }
        userDeferred.await().map { user ->
            UserEditorContext(
                user = user,
                libraries = libsDeferred.await().getOrNull().orEmpty(),
                currentUserId = meDeferred.await().getOrNull(),
                adminCount = allUsersDeferred.await().getOrNull().orEmpty().activeAdminCount(),
            )
        }
    }

    override suspend fun getManagedUser(userId: String): Result<ManagedUser> =
        apiClient.getManagedUser(userId)

    override suspend fun renameUser(userId: String, newName: String): Result<ManagedUser> =
        apiClient.renameUser(userId, newName)

    override suspend fun updateUserPolicy(userId: String, policy: ManagedUserPolicy): Result<Unit> =
        apiClient.updateUserPolicy(userId, policy)

    override suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit> =
        apiClient.updateUserPassword(userId, newPassword)

    override suspend fun getDevices(): Result<List<DeviceInfo>> =
        apiClient.getDevices()

    override suspend fun getLiveTvChannels(limit: Int): Result<List<LiveTvChannel>> =
        apiClient.getLiveTvChannels(limit = limit)

    override suspend fun getParentalRatings(): Result<List<ParentalRatingOption>> =
        apiClient.getParentalRatings()

    override suspend fun getTags(limit: Int): Result<List<String>> =
        apiClient.getTags(limit = limit)

    override suspend fun renameDevice(deviceId: String, customName: String?): Result<Unit> =
        apiClient.updateDeviceOptions(deviceId, customName)

    override suspend fun deleteDevice(deviceId: String): Result<Unit> =
        apiClient.deleteDevice(deviceId)

    override suspend fun getScheduledTasks(isHidden: Boolean?): Result<List<ScheduledTaskInfo>> =
        apiClient.getScheduledTasks(isHidden = isHidden)

    override suspend fun startTask(taskId: String): Result<Unit> =
        apiClient.startTask(taskId)

    override suspend fun cancelTask(taskId: String): Result<Unit> =
        apiClient.cancelTask(taskId)

    override suspend fun getDashboardSummary(): Result<AdminDashboardSummary> = coroutineScope {
        // Each endpoint degrades independently: telemetry fields to null,
        // list fields to empty — a single failing card never blanks the screen.
        val sysInfoDeferred = async { apiClient.getSystemInfo().getOrNull() }
        val countsDeferred = async { apiClient.getItemCounts().getOrNull() }
        val sessionsDeferred = async { apiClient.getSessions().getOrNull() }
        val activityDeferred = async { apiClient.getActivityLogEntries(limit = 10).getOrNull() }
        val tasksDeferred = async { apiClient.getScheduledTasks().getOrNull() }

        Result.success(
            AdminDashboardSummary(
                systemInfo = sysInfoDeferred.await(),
                itemCounts = countsDeferred.await(),
                sessions = sessionsDeferred.await() ?: emptyList(),
                recentActivity = activityDeferred.await() ?: emptyList(),
                tasks = tasksDeferred.await() ?: emptyList(),
            ),
        )
    }

    override suspend fun restartServer(): Result<Unit> = apiClient.restartServer()

    override suspend fun shutdownServer(): Result<Unit> = apiClient.shutdownServer()

    override suspend fun stopSession(sessionId: String): Result<Unit> =
        apiClient.stopSession(sessionId)

    override suspend fun startLibraryScan(): Result<Unit> {
        val tasks = apiClient.getScheduledTasks().getOrNull().orEmpty()
        val taskId = tasks.firstOrNull { it.key == KEY_SCAN_LIBRARY }?.id
            ?: tasks.firstOrNull { it.name.equals(NAME_SCAN_LIBRARY, ignoreCase = true) }?.id
        return if (taskId != null) {
            apiClient.startTask(taskId)
        } else {
            // No exposed task — fall back to the library refresh endpoint (no progress).
            apiClient.scanLibrary()
        }
    }

    override suspend fun getSessions(): Result<List<SessionInfo>> = apiClient.getSessions()

    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String): Result<Unit> =
        apiClient.sendMessageToSession(sessionId, header, text)

    override suspend fun getLogFiles(): Result<List<LogFile>> = apiClient.getLogFiles()

    override suspend fun getLogFileContent(fileName: String): Result<String> =
        apiClient.getLogFileContent(fileName)

    override suspend fun getActivityLogEntries(startIndex: Int?, limit: Int?): Result<List<ActivityLogEntry>> =
        apiClient.getActivityLogEntries(startIndex = startIndex, limit = limit)

    override fun liveActivityEntries(knownIds: Set<Long>): Flow<ActivityLogEntry> =
        activityLogRealtimeChannel.entries(knownIds)

    override suspend fun getInstalledPlugins(): Result<List<PluginInfo>> =
        apiClient.getInstalledPlugins()

    override suspend fun getAvailablePackages(): Result<List<PluginPackage>> =
        apiClient.getAvailablePackages()

    override suspend fun getPackageInfo(name: String, assemblyGuid: String?): Result<PluginPackage> =
        apiClient.getPackageInfo(name, assemblyGuid)

    override suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>> =
        apiClient.getPackageInstallations()

    override suspend fun installPackage(
        name: String,
        assemblyGuid: String?,
        version: String?,
        repositoryUrl: String?,
    ): Result<Unit> = apiClient.installPackage(
        name = name,
        assemblyGuid = assemblyGuid,
        version = version,
        repositoryUrl = repositoryUrl,
    )

    override suspend fun cancelPackageInstallation(packageId: String): Result<Unit> =
        apiClient.cancelPackageInstallation(packageId)

    override suspend fun setPluginEnabled(pluginId: String, version: String, enabled: Boolean): Result<Unit> =
        if (enabled) apiClient.enablePlugin(pluginId, version) else apiClient.disablePlugin(pluginId, version)

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> =
        apiClient.uninstallPlugin(pluginId)

    override suspend fun getRepositories(): Result<List<PluginRepository>> =
        apiClient.getRepositories()

    override suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit> =
        apiClient.setRepositories(repositories)

    override suspend fun getPluginConfigPage(pluginId: String): Result<PluginConfigPageContent?> {
        val pages = apiClient.getConfigurationPages().getOrElse { return Result.failure(it) }
        val page = pages.find { it.pluginId == pluginId } ?: return Result.success(null)
        return apiClient.getDashboardConfigurationPage(page.name).map { html ->
            PluginConfigPageContent(name = page.name, html = html)
        }
    }

    private fun List<ManagedUser>.activeAdminCount(): Int =
        count { it.policy.isAdministrator && !it.policy.isDisabled }

    private companion object {
        // Jellyfin scheduled-task key / display name for "Scan media library".
        const val KEY_SCAN_LIBRARY = "RefreshLibrary"
        const val NAME_SCAN_LIBRARY = "Scan Media Library"
    }
}
