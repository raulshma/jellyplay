package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.AdminDashboardSummary
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.UserEditorContext
import com.raulshma.jellyplay.core.model.UsersOverview
import com.raulshma.jellyplay.core.model.buildUserImageUrl
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * D5 ctor narrowed to the family singles the members actually call — the
 * injected [JellyfinApiClient] union was a pure delegation over those same
 * singles, so every forward here paid two extra hops
 * (feature → AdminRepository → union → family single) for nothing:
 *  - [AdminApiClient]: system/tasks/devices/sessions/logs/dashboard telemetry;
 *  - [UserApiClient]: managed users, current user, parental ratings, and the
 *    user-editor's library folders;
 *  - [LiveTvApiClient]: the users-detail auxiliary channels tab;
 *  - [LibraryApiClient]: tags (the editor's tag picker).
 * The union's other injectors are unchanged — [JellyfinApiClient] itself keeps
 * its composition and its remaining consumers.
 */
class AdminRepositoryImpl constructor(
    private val adminApiClient: AdminApiClient,
    private val userApiClient: UserApiClient,
    private val liveTvApiClient: LiveTvApiClient,
    private val libraryApiClient: LibraryApiClient,
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

    override suspend fun getSystemInfo(): Result<SystemInfo> = adminApiClient.getSystemInfo()

    override fun getUserImageUrl(userId: String, tag: String?, maxWidth: Int): String =
        buildUserImageUrl(
            // activeServerAddress, NOT currentServer.value?.address: the router's
            // active endpoint is failover-correct — after a failover the server's
            // primary address may be the dead one, and an image URL pinned to it
            // becomes a straggler request against a server nobody can reach.
            baseUrl = engine.activeServerAddress,
            userId = userId,
            imageType = "Primary",
            maxWidth = maxWidth,
            tag = tag,
        )

    override suspend fun getUsersOverview(): Result<UsersOverview> = coroutineScope {
        // Independent round-trips — run concurrently (sum → max latency).
        val usersDeferred = async { userApiClient.getManagedUsers() }
        val meDeferred = async { userApiClient.getCurrentUserId() }
        usersDeferred.await().map { users ->
            UsersOverview(
                users = users,
                currentUserId = meDeferred.await().getOrNull(),
                adminCount = users.activeAdminCount(),
            )
        }
    }

    override suspend fun createUser(name: String, password: String?): Result<ManagedUser> =
        userApiClient.createUser(name, password)

    override suspend fun deleteUser(userId: String): Result<Unit> =
        userApiClient.deleteUser(userId)

    override suspend fun getUserEditorContext(userId: String): Result<UserEditorContext> = coroutineScope {
        // All four round-trips are independent — run concurrently.
        val userDeferred = async { userApiClient.getManagedUser(userId) }
        val libsDeferred = async { userApiClient.getLibraryFoldersForEditor() }
        val meDeferred = async { userApiClient.getCurrentUserId() }
        val allUsersDeferred = async { userApiClient.getManagedUsers() }
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
        userApiClient.getManagedUser(userId)

    override suspend fun renameUser(userId: String, newName: String): Result<ManagedUser> =
        userApiClient.renameUser(userId, newName)

    override suspend fun updateUserPolicy(userId: String, policy: ManagedUserPolicy): Result<Unit> =
        userApiClient.updateUserPolicy(userId, policy)

    override suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit> =
        userApiClient.updateUserPassword(userId, newPassword)

    override suspend fun getDevices(): Result<List<DeviceInfo>> =
        adminApiClient.getDevices()

    override suspend fun getLiveTvChannels(limit: Int): Result<List<LiveTvChannel>> =
        liveTvApiClient.getLiveTvChannels(limit = limit)

    override suspend fun getParentalRatings(): Result<List<ParentalRatingOption>> =
        userApiClient.getParentalRatings()

    override suspend fun getTags(limit: Int): Result<List<String>> =
        libraryApiClient.getTags(limit = limit)

    override suspend fun renameDevice(deviceId: String, customName: String?): Result<Unit> =
        adminApiClient.updateDeviceOptions(deviceId, customName)

    override suspend fun deleteDevice(deviceId: String): Result<Unit> =
        adminApiClient.deleteDevice(deviceId)

    override suspend fun getScheduledTasks(isHidden: Boolean?): Result<List<ScheduledTaskInfo>> =
        adminApiClient.getScheduledTasks(isHidden = isHidden)

    override suspend fun startTask(taskId: String): Result<Unit> =
        adminApiClient.startTask(taskId)

    override suspend fun cancelTask(taskId: String): Result<Unit> =
        adminApiClient.cancelTask(taskId)

    override suspend fun getDashboardSummary(): Result<AdminDashboardSummary> = coroutineScope {
        // Each endpoint degrades independently: telemetry fields to null,
        // list fields to empty — a single failing card never blanks the screen.
        val sysInfoDeferred = async { adminApiClient.getSystemInfo().getOrNull() }
        val countsDeferred = async { adminApiClient.getItemCounts().getOrNull() }
        val sessionsDeferred = async { adminApiClient.getSessions().getOrNull() }
        val activityDeferred = async { adminApiClient.getActivityLogEntries(limit = 10).getOrNull() }
        val tasksDeferred = async { adminApiClient.getScheduledTasks().getOrNull() }

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

    override suspend fun restartServer(): Result<Unit> = adminApiClient.restartServer()

    override suspend fun shutdownServer(): Result<Unit> = adminApiClient.shutdownServer()

    override suspend fun stopSession(sessionId: String): Result<Unit> =
        adminApiClient.stopSession(sessionId)

    override suspend fun startLibraryScan(): Result<Unit> {
        val tasks = adminApiClient.getScheduledTasks().getOrNull().orEmpty()
        val taskId = tasks.firstOrNull { it.key == KEY_SCAN_LIBRARY }?.id
            ?: tasks.firstOrNull { it.name.equals(NAME_SCAN_LIBRARY, ignoreCase = true) }?.id
        return if (taskId != null) {
            adminApiClient.startTask(taskId)
        } else {
            // No exposed task — fall back to the library refresh endpoint (no progress).
            adminApiClient.scanLibrary()
        }
    }

    override suspend fun getSessions(): Result<List<SessionInfo>> = adminApiClient.getSessions()

    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String): Result<Unit> =
        adminApiClient.sendMessageToSession(sessionId, header, text)

    override suspend fun getLogFiles(): Result<List<LogFile>> = adminApiClient.getLogFiles()

    override suspend fun getLogFileContent(fileName: String): Result<String> =
        adminApiClient.getLogFileContent(fileName)

    override suspend fun getActivityLogEntries(startIndex: Int?, limit: Int?): Result<List<ActivityLogEntry>> =
        adminApiClient.getActivityLogEntries(startIndex = startIndex, limit = limit)

    override fun liveActivityEntries(knownIds: Set<Long>): Flow<ActivityLogEntry> =
        activityLogRealtimeChannel.entries(knownIds)

    private fun List<ManagedUser>.activeAdminCount(): Int =
        count { it.policy.isAdministrator && !it.policy.isDisabled }

    private companion object {
        // Jellyfin scheduled-task key / display name for "Scan media library".
        const val KEY_SCAN_LIBRARY = "RefreshLibrary"
        const val NAME_SCAN_LIBRARY = "Scan Media Library"
    }
}
