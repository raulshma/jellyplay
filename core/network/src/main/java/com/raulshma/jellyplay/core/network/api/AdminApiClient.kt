package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskTriggerInfo

interface AdminApiClient {
    suspend fun getSystemInfo(): Result<SystemInfo>
    suspend fun getItemCounts(): Result<ItemCounts>
    suspend fun restartServer(): Result<Unit>
    suspend fun shutdownServer(): Result<Unit>
    suspend fun scanLibrary(): Result<Unit>
    suspend fun getScheduledTasks(isHidden: Boolean? = null, isEnabled: Boolean? = null): Result<List<ScheduledTaskInfo>>
    suspend fun getScheduledTask(taskId: String): Result<ScheduledTaskInfo>
    suspend fun startTask(taskId: String): Result<Unit>
    suspend fun cancelTask(taskId: String): Result<Unit>
    suspend fun updateTaskTriggers(taskId: String, triggers: List<TaskTriggerInfo>): Result<Unit>
    suspend fun getDevices(userId: String? = null): Result<List<DeviceInfo>>
    suspend fun getDeviceInfo(deviceId: String): Result<DeviceInfo>
    suspend fun updateDeviceOptions(deviceId: String, customName: String?): Result<Unit>
    suspend fun deleteDevice(deviceId: String): Result<Unit>
    suspend fun getLogFiles(): Result<List<LogFile>>
    suspend fun getLogFileContent(fileName: String): Result<String>
    suspend fun getActivityLogEntries(startIndex: Int? = null, limit: Int? = null, minDate: String? = null, hasUserId: Boolean? = null): Result<List<ActivityLogEntry>>
    suspend fun getSessions(): Result<List<SessionInfo>>
    suspend fun sendMessageToSession(sessionId: String, header: String, text: String, timeoutMs: Long = 5000): Result<Unit>
    /** Stops active playback on the session (Jellyfin play-state STOP command). */
    suspend fun stopSession(sessionId: String): Result<Unit>
    suspend fun play(
        sessionId: String,
        playCommand: String,
        itemIds: List<String>,
        startPositionTicks: Long? = null,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startIndex: Int? = null,
    ): Result<Unit>
    suspend fun sendPlaystateCommand(
        sessionId: String,
        command: String,
        seekPositionTicks: Long? = null,
        controllingUserId: String? = null,
    ): Result<Unit>
    suspend fun sendGeneralCommand(
        sessionId: String,
        commandName: String,
        controllingUserId: String? = null,
        arguments: Map<String, String>? = null,
    ): Result<Unit>
}
