package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.FreshnessCeilings
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

class AdminApiClientImpl(
    private val engine: JellyfinApiEngine,
) : AdminApiClient {

    // loadDashboard() runs on every admin screen entry and on periodic refresh,
    // re-issuing getSystemInfo + getItemCounts in parallel each time. Both are
    // near-static (server version / library counts) but the repository layer
    // does not memoise them here (unlike MediaRepositoryImpl). A short TTL
    // removes the redundant calls without observable staleness.
    //
    // Declared exception to the identity-keyed house idiom: the keys are bare
    // server-scoped strings (no CacheIdentity composite), because this client
    // is a process-lifetime single over the shared engine and core:network has
    // no identity source to key with — see CONTEXT.md "Core data repositories".
    private val systemInfoCache = TtlCache<SystemInfo>(maxSize = 4, ttlMs = FreshnessCeilings.ADMIN_SYSTEM_INFO_TTL_MS)
    private val itemCountsCache = TtlCache<ItemCounts>(maxSize = 4, ttlMs = FreshnessCeilings.ADMIN_ITEM_COUNTS_TTL_MS)

    override suspend fun getSystemInfo(): Result<SystemInfo> = engine.withApi { api ->
        systemInfoCache.getOrPut(KEY_SYSTEM_INFO) {
            val dto = api.systemApi.getSystemInfo().content
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
    }

    override suspend fun getItemCounts(): Result<ItemCounts> = engine.withApi { api ->
        itemCountsCache.getOrPut(KEY_ITEM_COUNTS) {
            api.libraryApi.getItemCounts().content.toItemCounts()
        }
    }

    override suspend fun restartServer(): Result<Unit> = engine.withApi { api ->
        api.systemApi.restartApplication()
    }

    override suspend fun shutdownServer(): Result<Unit> = engine.withApi { api ->
        api.systemApi.shutdownApplication()
    }

    override suspend fun scanLibrary(): Result<Unit> = engine.withApi { api ->
        // POST /Library/Refresh — fires a server-side library scan. No progress
        // payload; the scheduled-task path (startTask with "RefreshLibrary") is
        // preferred where available so the UI can poll currentProgressPercentage.
        api.libraryApi.refreshLibrary()
    }

    override suspend fun getScheduledTasks(isHidden: Boolean?, isEnabled: Boolean?): Result<List<ScheduledTaskInfo>> = engine.withApi { api ->
        val response = api.scheduledTasksApi.getTasks(
            isHidden = isHidden,
            isEnabled = isEnabled,
        ).content ?: emptyList()
        response.mapNotNull { dto ->
            try { dto.toTaskModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getScheduledTask(taskId: String): Result<ScheduledTaskInfo> = engine.withApi { api ->
        api.scheduledTasksApi.getTask(taskId = taskId).content.toTaskModel()
    }

    override suspend fun startTask(taskId: String): Result<Unit> = engine.withApi { api ->
        api.scheduledTasksApi.startTask(taskId = taskId)
    }

    override suspend fun cancelTask(taskId: String): Result<Unit> = engine.withApi { api ->
        api.scheduledTasksApi.stopTask(taskId = taskId)
    }

    override suspend fun updateTaskTriggers(taskId: String, triggers: List<TaskTriggerInfo>): Result<Unit> = engine.withApi { api ->
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
        api.scheduledTasksApi.updateTask(taskId = taskId, data = sdkTriggers)
    }

    override suspend fun getDevices(userId: String?): Result<List<DeviceInfo>> = engine.withApi { api ->
        val response = api.devicesApi.getDevices(
            userId = userId?.toUUID(),
        ).content
        response.items.mapNotNull { dto ->
            try { dto.toDeviceModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getDeviceInfo(deviceId: String): Result<DeviceInfo> = engine.withApi { api ->
        api.devicesApi.getDeviceInfo(id = deviceId).content.toDeviceModel()
    }

    override suspend fun updateDeviceOptions(deviceId: String, customName: String?): Result<Unit> = engine.withApi { api ->
        api.devicesApi.updateDeviceOptions(
            id = deviceId,
            data = org.jellyfin.sdk.model.api.DeviceOptionsDto(
                id = 0,
                deviceId = deviceId,
                customName = customName,
            ),
        )
    }

    override suspend fun deleteDevice(deviceId: String): Result<Unit> = engine.withApi { api ->
        api.devicesApi.deleteDevice(id = deviceId)
    }

    override suspend fun getLogFiles(): Result<List<LogFile>> = engine.withApi { api ->
        val logs = api.systemApi.getServerLogs().content
        logs.map { it.toLogFileModel() }
    }

    override suspend fun getLogFileContent(fileName: String): Result<String> = engine.withApi { api ->
        api
            .request(pathTemplate = "/System/Logs/Log", queryParameters = mapOf("name" to fileName))
            .body
            .decodeToString()
    }

    override suspend fun getActivityLogEntries(startIndex: Int?, limit: Int?, minDate: String?, hasUserId: Boolean?): Result<List<ActivityLogEntry>> = engine.withApi { api ->
        val result = api.activityLogApi.getLogEntries(
            startIndex = startIndex,
            limit = limit,
            minDate = minDate?.let { java.time.LocalDateTime.parse(it) },
            hasUserId = hasUserId,
        ).content
        result.items.map { it.toActivityModel() }
    }

    override suspend fun getSessions(): Result<List<SessionInfo>> = engine.withApi { api ->
        val sessions = api.sessionApi.getSessions().content
        sessions.map { it.toSessionModel() }
    }

    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String, timeoutMs: Long): Result<Unit> = engine.withApi { api ->
        api.sessionApi.sendMessageCommand(
            sessionId = sessionId,
            data = org.jellyfin.sdk.model.api.MessageCommand(
                header = header.takeIf { it.isNotBlank() },
                text = text,
                timeoutMs = timeoutMs,
            ),
        )
    }

    override suspend fun stopSession(sessionId: String): Result<Unit> = engine.withApi { api ->
        // Issue the play-state STOP command (canonical Jellyfin transport stop).
        api.sessionApi.sendPlaystateCommand(
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
    ): Result<Unit> = engine.withApi { api ->
        api.sessionApi.play(
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
    ): Result<Unit> = engine.withApi { api ->
        api.sessionApi.sendPlaystateCommand(
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
    ): Result<Unit> = engine.withApi { api ->
        api.sessionApi.sendFullGeneralCommand(
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
        // The two TTLs used to be private consts here (2 minutes each); both
        // now cite FreshnessCeilings.ADMIN_SYSTEM_INFO_TTL_MS /
        // ADMIN_ITEM_COUNTS_TTL_MS at the construction sites above.
    }
}
