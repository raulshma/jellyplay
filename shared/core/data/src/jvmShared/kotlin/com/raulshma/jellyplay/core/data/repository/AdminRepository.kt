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
import kotlinx.coroutines.flow.Flow

/**
 * Server-administration seam for feature screens. Features consume screen
 * operations here instead of the wide [com.raulshma.jellyplay.core.network.JellyfinApiClient]
 * transport interface; the implementation owns fan-out, lookups, fallbacks,
 * and realtime channels so callers cannot hand-roll them per screen.
 */
interface AdminRepository {

    /** Server telemetry for the About screen and the admin dashboard. */
    suspend fun getSystemInfo(): Result<SystemInfo>

    // ── Users screen ──

    /** Users + current user id + active-admin count in one joined read. */
    suspend fun getUsersOverview(): Result<UsersOverview>

    /**
     * Bearer-less avatar URL for a managed user: `/Users/{userId}/Images/Primary`
     * under the active server, with [tag] ([ManagedUser.primaryImageTag]) as
     * cache-buster and [maxWidth] as decode cap. Jellyfin serves user images
     * anonymously, so no token travels in the URL. "" when no server is active
     * or [userId] is not a GUID in either serialization — callers fall back to
     * the initials avatar.
     */
    fun getUserImageUrl(userId: String, tag: String?, maxWidth: Int): String

    suspend fun createUser(name: String, password: String?): Result<ManagedUser>

    suspend fun deleteUser(userId: String): Result<Unit>

    // ── User detail screen ──

    /**
     * The user-editor's opening context. Partial-failure semantics: fails only
     * when the target user cannot be fetched; libraries degrade to empty and a
     * missing current-user id to null (mirroring the screen's previous
     * getOrNull joins).
     */
    suspend fun getUserEditorContext(userId: String): Result<UserEditorContext>

    suspend fun getManagedUser(userId: String): Result<ManagedUser>

    suspend fun renameUser(userId: String, newName: String): Result<ManagedUser>

    suspend fun updateUserPolicy(userId: String, policy: ManagedUserPolicy): Result<Unit>

    suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit>

    // ── User-detail auxiliary tabs (also the devices screen) ──

    suspend fun getDevices(): Result<List<DeviceInfo>>

    suspend fun getLiveTvChannels(limit: Int = 500): Result<List<LiveTvChannel>>

    suspend fun getParentalRatings(): Result<List<ParentalRatingOption>>

    suspend fun getTags(limit: Int = 500): Result<List<String>>

    // ── Devices screen ──

    suspend fun renameDevice(deviceId: String, customName: String?): Result<Unit>

    suspend fun deleteDevice(deviceId: String): Result<Unit>

    // ── Scheduled tasks screen ──

    suspend fun getScheduledTasks(isHidden: Boolean? = false): Result<List<ScheduledTaskInfo>>

    suspend fun startTask(taskId: String): Result<Unit>

    suspend fun cancelTask(taskId: String): Result<Unit>

    /**
     * Live scheduled-task pushes (full list, server cadence). App-lifetime hot
     * flow over the shared socket: collecting subscribes, cancelling the
     * collector unsubscribes (after a grace window), the socket survives.
     */
    val scheduledTasks: Flow<List<ScheduledTaskInfo>>

    /**
     * Wall time of the last successful ScheduledTasksInfo push (0 = none
     * this process). Freshness signal for [scheduledTasks] consumers: the
     * shared flow replays its last emission to every new collector, and that
     * replay can be arbitrarily old, so recency must be judged from the push
     * time, not the collection time.
     */
    val scheduledTasksLastPushAtMs: Long

    /** The "Scan media library" task when present/running, else null. */
    val libraryScanTask: Flow<ScheduledTaskInfo?>

    // ── Dashboard screen ──

    /** The dashboard's five-endpoint opening fan-out with per-field degradation. */
    suspend fun getDashboardSummary(): Result<AdminDashboardSummary>

    suspend fun restartServer(): Result<Unit>

    suspend fun shutdownServer(): Result<Unit>

    suspend fun stopSession(sessionId: String): Result<Unit>

    /**
     * Starts the media-library scan. Absorbs the task lookup (key, then name)
     * and falls back to the plain library-refresh endpoint when the server
     * exposes no such task.
     */
    suspend fun startLibraryScan(): Result<Unit>

    // ── Shared (dashboard, settings) ──

    suspend fun getSessions(): Result<List<SessionInfo>>

    suspend fun sendMessageToSession(sessionId: String, header: String, text: String): Result<Unit>

    // ── Logs screen ──

    suspend fun getLogFiles(): Result<List<LogFile>>

    suspend fun getLogFileContent(fileName: String): Result<String>

    suspend fun getActivityLogEntries(startIndex: Int? = null, limit: Int? = null): Result<List<ActivityLogEntry>>

    /**
     * Cold flow of live activity-log entries: collecting opens the live-log
     * WebSocket (with reconnect backoff and REST-polling fallback owned by the
     * data layer); cancelling the collector tears it down. [knownIds] seeds
     * dedupe so the polling fallback does not replay already-known entries.
     */
    fun liveActivityEntries(knownIds: Set<Long> = emptySet()): Flow<ActivityLogEntry>

    // ── Plugins screens (list, detail, config) ──

    suspend fun getInstalledPlugins(): Result<List<PluginInfo>>

    suspend fun getAvailablePackages(): Result<List<PluginPackage>>

    suspend fun getPackageInfo(name: String, assemblyGuid: String? = null): Result<PluginPackage>

    suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>>

    suspend fun installPackage(
        name: String,
        assemblyGuid: String? = null,
        version: String? = null,
        repositoryUrl: String? = null,
    ): Result<Unit>

    suspend fun cancelPackageInstallation(packageId: String): Result<Unit>

    suspend fun setPluginEnabled(pluginId: String, version: String, enabled: Boolean): Result<Unit>

    suspend fun uninstallPlugin(pluginId: String): Result<Unit>

    suspend fun getRepositories(): Result<List<PluginRepository>>

    suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit>

    /**
     * Resolves a plugin's configuration page in one call: page lookup by
     * plugin id, then the page's HTML. Null when the plugin exposes no
     * configuration page.
     */
    suspend fun getPluginConfigPage(pluginId: String): Result<PluginConfigPageContent?>

    /**
     * WebView bridge session: the server address, user id, and access token
     * the plugin bridge script is parameterized with, plus the engine's
     * OkHttpClient for same-origin request interception.
     */
    val pluginWebViewSession: PluginWebViewSession
}
