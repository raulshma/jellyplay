package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskTriggerInfo
import com.raulshma.jellyplay.core.model.TtlCache
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

    // loadDashboard() runs on every admin screen entry and on periodic refresh,
    // re-issuing getSystemInfo + getItemCounts in parallel each time. Both are
    // near-static (server version / library counts) but the repository layer
    // does not memoise them here (unlike MediaRepositoryImpl). A short TTL
    // removes the redundant calls without observable staleness.
    private val systemInfoCache = TtlCache<SystemInfo>(maxSize = 4, ttlMs = SYSTEM_INFO_TTL_MS)
    private val itemCountsCache = TtlCache<ItemCounts>(maxSize = 4, ttlMs = ITEM_COUNTS_TTL_MS)

    override suspend fun getSystemInfo(): Result<SystemInfo> = engine.apiResultWithRetry {
        systemInfoCache.get(KEY_SYSTEM_INFO)?.let { return@apiResultWithRetry it }
        val dto = engine.requireApi().systemApi.getSystemInfo().content
        val info = SystemInfo(
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
        systemInfoCache.put(KEY_SYSTEM_INFO, info)
        info
    }

    override suspend fun getItemCounts(): Result<ItemCounts> = engine.apiResultWithRetry {
        itemCountsCache.get(KEY_ITEM_COUNTS)?.let { return@apiResultWithRetry it }
        val dto = engine.requireApi().libraryApi.getItemCounts().content
        val counts = ItemCounts(
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
        itemCountsCache.put(KEY_ITEM_COUNTS, counts)
        counts
    }

    override suspend fun restartServer(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().systemApi.restartApplication()
    }

    override suspend fun shutdownServer(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().systemApi.shutdownApplication()
    }

    override suspend fun scanLibrary(): Result<Unit> = engine.apiResultWithRetry {
        // POST /Library/Refresh — fires a server-side library scan. No progress
        // payload; the scheduled-task path (startTask with "RefreshLibrary") is
        // preferred where available so the UI can poll currentProgressPercentage.
        engine.requireApi().libraryApi.refreshLibrary()
    }

    override suspend fun getScheduledTasks(isHidden: Boolean?, isEnabled: Boolean?): Result<List<ScheduledTaskInfo>> = engine.apiResultWithRetry {
        val response = engine.requireApi().scheduledTasksApi.getTasks(
            isHidden = isHidden,
            isEnabled = isEnabled,
        ).content ?: emptyList()
        response.mapNotNull { dto ->
            try { dto.toTaskModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getScheduledTask(taskId: String): Result<ScheduledTaskInfo> = engine.apiResultWithRetry {
        engine.requireApi().scheduledTasksApi.getTask(taskId = taskId).content.toTaskModel()
    }

    override suspend fun startTask(taskId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().scheduledTasksApi.startTask(taskId = taskId)
    }

    override suspend fun cancelTask(taskId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().scheduledTasksApi.stopTask(taskId = taskId)
    }

    override suspend fun updateTaskTriggers(taskId: String, triggers: List<TaskTriggerInfo>): Result<Unit> = engine.apiResultWithRetry {
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

    override suspend fun getDevices(userId: String?): Result<List<DeviceInfo>> = engine.apiResultWithRetry {
        val response = engine.requireApi().devicesApi.getDevices(
            userId = userId?.toUUID(),
        ).content
        response.items.mapNotNull { dto ->
            try { dto.toDeviceModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getDeviceInfo(deviceId: String): Result<DeviceInfo> = engine.apiResultWithRetry {
        engine.requireApi().devicesApi.getDeviceInfo(id = deviceId).content.toDeviceModel()
    }

    override suspend fun updateDeviceOptions(deviceId: String, customName: String?): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().devicesApi.updateDeviceOptions(
            id = deviceId,
            data = org.jellyfin.sdk.model.api.DeviceOptionsDto(
                id = 0,
                deviceId = deviceId,
                customName = customName,
            ),
        )
    }

    override suspend fun deleteDevice(deviceId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().devicesApi.deleteDevice(id = deviceId)
    }

    override suspend fun getLogFiles(): Result<List<LogFile>> = engine.apiResultWithRetry {
        val logs = engine.requireApi().systemApi.getServerLogs().content
        logs.map { it.toLogFileModel() }
    }

    override suspend fun getLogFileContent(fileName: String): Result<String> = engine.apiResultWithRetry {
        engine.requireApi()
            .request(pathTemplate = "/System/Logs/Log", queryParameters = mapOf("name" to fileName))
            .body
            .decodeToString()
    }

    override suspend fun getActivityLogEntries(startIndex: Int?, limit: Int?, minDate: String?, hasUserId: Boolean?): Result<List<ActivityLogEntry>> = engine.apiResultWithRetry {
        val result = engine.requireApi().activityLogApi.getLogEntries(
            startIndex = startIndex,
            limit = limit,
            minDate = minDate?.let { java.time.LocalDateTime.parse(it) },
            hasUserId = hasUserId,
        ).content
        result.items.map { it.toActivityModel() }
    }

    override suspend fun getSessions(): Result<List<SessionInfo>> = engine.apiResultWithRetry {
        val sessions = engine.requireApi().sessionApi.getSessions().content
        sessions.map { it.toSessionModel() }
    }

    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String, timeoutMs: Long): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().sessionApi.sendMessageCommand(
            sessionId = sessionId,
            data = org.jellyfin.sdk.model.api.MessageCommand(
                header = header.takeIf { it.isNotBlank() },
                text = text,
                timeoutMs = timeoutMs,
            ),
        )
    }

    override suspend fun stopSession(sessionId: String): Result<Unit> = engine.apiResultWithRetry {
        // Issue the play-state STOP command (canonical Jellyfin transport stop).
        engine.requireApi().sessionApi.sendPlaystateCommand(
            sessionId = sessionId,
            command = org.jellyfin.sdk.model.api.PlaystateCommand.STOP,
        )
    }

    override suspend fun play(
        sessionId: String,
        playCommand: String,
        itemIds: List<String>,
        startPositionTicks: Long?,
        mediaSourceId: String?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startIndex: Int?,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().sessionApi.play(
            sessionId = sessionId,
            playCommand = org.jellyfin.sdk.model.api.PlayCommand.entries.find { it.serialName.equals(playCommand, ignoreCase = true) }
                ?: org.jellyfin.sdk.model.api.PlayCommand.PLAY_NOW,
            itemIds = itemIds.map { it.toUUID() },
            startPositionTicks = startPositionTicks,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            startIndex = startIndex,
        )
    }

    override suspend fun sendPlaystateCommand(
        sessionId: String,
        command: String,
        seekPositionTicks: Long?,
        controllingUserId: String?,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().sessionApi.sendPlaystateCommand(
            sessionId = sessionId,
            command = org.jellyfin.sdk.model.api.PlaystateCommand.entries.find { it.serialName.equals(command, ignoreCase = true) }
                ?: org.jellyfin.sdk.model.api.PlaystateCommand.PAUSE,
            seekPositionTicks = seekPositionTicks,
            controllingUserId = controllingUserId,
        )
    }

    override suspend fun sendGeneralCommand(
        sessionId: String,
        commandName: String,
        controllingUserId: String?,
        arguments: Map<String, String>?,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().sessionApi.sendFullGeneralCommand(
            sessionId,
            org.jellyfin.sdk.model.api.GeneralCommand(
                name = org.jellyfin.sdk.model.api.GeneralCommandType.entries.find { it.serialName.equals(commandName, ignoreCase = true) }
                    ?: org.jellyfin.sdk.model.api.GeneralCommandType.SET_VOLUME,
                controllingUserId = controllingUserId?.toUUID() ?: java.util.UUID(0L, 0L),
                arguments = arguments ?: emptyMap(),
            )
        )
    }

    private companion object {
        const val KEY_SYSTEM_INFO = "systemInfo"
        const val KEY_ITEM_COUNTS = "itemCounts"
        // 2 minutes — long enough to dedupe the parallel calls fired by
        // loadDashboard() and the independent About-screen getSystemInfo(),
        // short enough to reflect server version/count changes promptly.
        const val SYSTEM_INFO_TTL_MS = 2 * 60 * 1000L
        const val ITEM_COUNTS_TTL_MS = 2 * 60 * 1000L
    }
}
