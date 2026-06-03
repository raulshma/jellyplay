package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskTriggerInfo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.model.api.DayOfWeek
import org.jellyfin.sdk.model.api.TaskTriggerInfoType
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : AdminApiClient {

    override suspend fun getSystemInfo(): Result<SystemInfo> = engine.apiResult {
        val dto = engine.requireApi().systemApi.getSystemInfo().content
        SystemInfo(
            serverName = dto.serverName ?: "",
            version = dto.version ?: "",
            productName = dto.productName ?: "",
            id = dto.id?.toString() ?: "",
            localAddress = dto.localAddress ?: "",
            wanAddress = "",
            operatingSystem = dto.operatingSystem ?: "",
            operatingSystemDisplayName = dto.operatingSystemDisplayName ?: "",
            hasPendingRestart = dto.hasPendingRestart,
            isShuttingDown = dto.isShuttingDown,
            startupWizardCompleted = dto.startupWizardCompleted ?: true,
            webSocketPortNumber = dto.webSocketPortNumber,
            packageName = dto.packageName ?: "",
            canSelfRestart = dto.canSelfRestart ?: false,
            canLaunchWebBrowser = dto.canLaunchWebBrowser ?: false,
            transcodingTempPath = dto.transcodingTempPath ?: "",
            cachePath = dto.cachePath ?: "",
            logPath = dto.logPath ?: "",
            internalMetadataPath = dto.internalMetadataPath ?: "",
        )
    }

    override suspend fun getItemCounts(): Result<ItemCounts> = engine.apiResult {
        val dto = engine.requireApi().libraryApi.getItemCounts().content
        ItemCounts(
            movieCount = dto.movieCount.toLong(),
            seriesCount = dto.seriesCount.toLong(),
            episodeCount = dto.episodeCount.toLong(),
            albumCount = dto.albumCount.toLong(),
            songCount = dto.songCount.toLong(),
            musicVideoCount = dto.musicVideoCount.toLong(),
            bookCount = dto.bookCount.toLong(),
            totalCount = dto.movieCount.toLong() + dto.seriesCount.toLong() +
                    dto.episodeCount.toLong() + dto.albumCount.toLong() +
                    dto.songCount.toLong() + dto.musicVideoCount.toLong() +
                    dto.bookCount.toLong(),
        )
    }

    override suspend fun restartServer(): Result<Unit> = engine.apiResult {
        engine.requireApi().systemApi.restartApplication()
    }

    override suspend fun shutdownServer(): Result<Unit> = engine.apiResult {
        engine.requireApi().systemApi.shutdownApplication()
    }

    override suspend fun getScheduledTasks(isHidden: Boolean?, isEnabled: Boolean?): Result<List<ScheduledTaskInfo>> = engine.apiResult {
        val response = engine.requireApi().scheduledTasksApi.getTasks(
            isHidden = isHidden,
            isEnabled = isEnabled,
        ).content ?: emptyList()
        response.mapNotNull { dto ->
            try { dto.toTaskModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getScheduledTask(taskId: String): Result<ScheduledTaskInfo> = engine.apiResult {
        engine.requireApi().scheduledTasksApi.getTask(taskId = taskId).content.toTaskModel()
    }

    override suspend fun startTask(taskId: String): Result<Unit> = engine.apiResult {
        engine.requireApi().scheduledTasksApi.startTask(taskId = taskId)
    }

    override suspend fun cancelTask(taskId: String): Result<Unit> = engine.apiResult {
        engine.requireApi().scheduledTasksApi.stopTask(taskId = taskId)
    }

    override suspend fun updateTaskTriggers(taskId: String, triggers: List<TaskTriggerInfo>): Result<Unit> = engine.apiResult {
        val sdkTriggers = triggers.map { trigger ->
            org.jellyfin.sdk.model.api.TaskTriggerInfo(
                type = TaskTriggerInfoType.entries.find { it.serialName.equals(trigger.type, ignoreCase = true) }
                    ?: TaskTriggerInfoType.INTERVAL_TRIGGER,
                timeOfDayTicks = trigger.timeOfDayTicks,
                intervalTicks = trigger.intervalTicks,
                dayOfWeek = trigger.dayOfWeek?.let { dow ->
                    DayOfWeek.entries.find { it.serialName.equals(dow, ignoreCase = true) }
                },
                maxRuntimeTicks = trigger.maxRuntimeTicks,
            )
        }
        engine.requireApi().scheduledTasksApi.updateTask(taskId = taskId, data = sdkTriggers)
    }

    override suspend fun getDevices(userId: String?): Result<List<DeviceInfo>> = engine.apiResult {
        val response = engine.requireApi().devicesApi.getDevices(
            userId = userId?.toUUID(),
        ).content
        response.items.mapNotNull { dto ->
            try { dto.toDeviceModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getDeviceInfo(deviceId: String): Result<DeviceInfo> = engine.apiResult {
        engine.requireApi().devicesApi.getDeviceInfo(id = deviceId).content.toDeviceModel()
    }

    override suspend fun updateDeviceOptions(deviceId: String, customName: String?): Result<Unit> = engine.apiResult {
        engine.requireApi().devicesApi.updateDeviceOptions(
            id = deviceId,
            data = org.jellyfin.sdk.model.api.DeviceOptionsDto(
                id = 0,
                deviceId = deviceId,
                customName = customName,
            ),
        )
    }

    override suspend fun deleteDevice(deviceId: String): Result<Unit> = engine.apiResult {
        engine.requireApi().devicesApi.deleteDevice(id = deviceId)
    }

    override suspend fun getLogFiles(): Result<List<LogFile>> = engine.apiResult {
        val logs = engine.requireApi().systemApi.getServerLogs().content
        logs.map { it.toLogFileModel() }
    }

    override suspend fun getLogFileContent(fileName: String): Result<String> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/System/Logs/Log?name=${java.net.URLEncoder.encode(fileName, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to get log file: ${response.code}")
            response.body?.string() ?: ""
        }
    }

    override suspend fun getActivityLogEntries(startIndex: Int?, limit: Int?, minDate: String?, hasUserId: Boolean?): Result<List<ActivityLogEntry>> = engine.apiResult {
        val result = engine.requireApi().activityLogApi.getLogEntries(
            startIndex = startIndex,
            limit = limit,
            minDate = minDate?.let { java.time.LocalDateTime.parse(it) },
            hasUserId = hasUserId,
        ).content
        result.items.map { it.toActivityModel() }
    }

    override suspend fun getSessions(): Result<List<SessionInfo>> = engine.apiResult {
        val sessions = engine.requireApi().sessionApi.getSessions().content
        sessions.map { it.toSessionModel() }
    }

    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String, timeoutMs: Long): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Sessions/$sessionId/Command"
        val body = buildJsonObject {
            put("Name", JsonPrimitive("DisplayMessage"))
            put("Arguments", buildJsonObject {
                put("Header", JsonPrimitive(header))
                put("Text", JsonPrimitive(text))
                put("TimeoutMs", JsonPrimitive(timeoutMs.toString()))
            })
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to send message: ${response.code}")
            }
        }
    }
}
