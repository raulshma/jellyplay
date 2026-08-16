package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
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

    /** The "Scan media library" task when present/running, else null. */
    val libraryScanTask: Flow<ScheduledTaskInfo?>
}
