package com.raulshma.jellyplay.core.data.syncplay

import android.util.Log
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncPlayCommand {
    data class Play(val positionTicks: Long, val whenMs: Long, val playlistItemId: String) : SyncPlayCommand()
    data class Pause(val positionTicks: Long, val whenMs: Long, val playlistItemId: String) : SyncPlayCommand()
    data class Seek(val positionTicks: Long, val whenMs: Long, val playlistItemId: String) : SyncPlayCommand()
    data object Stop : SyncPlayCommand()
    data class PlayQueueUpdate(
        val itemIds: List<String>,
        val playlistItemIds: List<String>,
        val playingItemId: String,
        val playingPlaylistItemId: String,
        val positionTicks: Long,
        val isPlaying: Boolean,
        val whenMs: Long,
    ) : SyncPlayCommand()
    data class StateUpdate(val isPlaying: Boolean, val reason: String) : SyncPlayCommand()
    data class GroupUpdate(val groupName: String, val participantCount: Int) : SyncPlayCommand()
    data object WaitForGroup : SyncPlayCommand()
    data class Notification(val message: String) : SyncPlayCommand()
    data class ChatMessage(val userId: String, val userName: String, val text: String) : SyncPlayCommand()
}

@Singleton
class SyncPlayManager @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val webSocketClient: JellyfinWebSocketClient,
    private val authRepository: AuthRepository,
    private val timeSyncManager: TimeSyncManager,
) {
    private var activeGroupIdReference = AtomicReference<String?>(null)
    private var isGroupActive = AtomicBoolean(false)
    private val sessionStartedAtRemoteMs = AtomicLong(0L)
    private var cachedGroup = AtomicReference<SyncPlayGroup?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var eventJob: Job? = null
    private var keepAliveJob: Job? = null
    private var pingReportJob: Job? = null

    private var syncPlayReady = AtomicBoolean(false)

    private val _commands = MutableSharedFlow<SyncPlayCommand>(extraBufferCapacity = 64)
    val commands: SharedFlow<SyncPlayCommand> = _commands.asSharedFlow()

    @Volatile
    private var queuedCommand: SyncPlayCommand? = null

    val currentGroup: SyncPlayGroup? get() = cachedGroup.get()
    val activeGroupId: String? get() = activeGroupIdReference.get()
    val isInSyncPlaySession: Boolean get() = isGroupActive.get() && activeGroupIdReference.get() != null

    fun startListening() {
        if (eventJob?.isActive == true) return
        eventJob = scope.launch {
            webSocketClient.events.collect { event ->
                handleEvent(event)
            }
        }
        keepAliveJob = scope.launch {
            while (true) {
                delay(30_000)
                webSocketClient.sendKeepAlive()
            }
        }
    }

    private fun handleEvent(event: SyncPlayEvent) {
        try {
            Log.d(TAG, "WS event: ${event.type}, data=${event.data}")
            if (shouldIgnoreEvent(event.type, event.data)) {
                Log.d(TAG, "Ignoring stale SyncPlay event: ${event.type}")
                return
            }

            when (event.type) {
                "SyncPlayCommand" -> {
                    val command = event.data.optString("Command", "")
                    val positionTicks = parseTicks(event.data, "PositionTicks")
                    val whenMs = parseWhen(event.data)
                    val playlistItemId = event.data.optString("PlaylistItemId", "")
                    if (!syncPlayReady.get()) {
                        Log.d(TAG, "SyncPlay not ready, queuing command: $command")
                        queuedCommand = when (command) {
                            "Unpause" -> SyncPlayCommand.Play(positionTicks, whenMs, playlistItemId)
                            "Pause" -> SyncPlayCommand.Pause(positionTicks, whenMs, playlistItemId)
                            "Seek" -> SyncPlayCommand.Seek(positionTicks, whenMs, playlistItemId)
                            "Stop" -> SyncPlayCommand.Stop
                            else -> null
                        }
                        return
                    }
                    when (command) {
                        "Unpause" -> {
                            _commands.tryEmit(SyncPlayCommand.Play(positionTicks, whenMs, playlistItemId))
                        }
                        "Pause" -> {
                            _commands.tryEmit(SyncPlayCommand.Pause(positionTicks, whenMs, playlistItemId))
                        }
                        "Seek" -> {
                            _commands.tryEmit(SyncPlayCommand.Seek(positionTicks, whenMs, playlistItemId))
                        }
                        "Stop" -> {
                            _commands.tryEmit(SyncPlayCommand.Stop)
                        }
                    }
                }
                "Play" -> {
                    if (isGroupActive.get()) {
                        Log.d(TAG, "Ignoring Play event: active SyncPlay session uses SyncPlayCommand")
                    } else {
                        val itemIds = event.data.optJSONArray("ItemIds")
                        val ids = mutableListOf<String>()
                        if (itemIds != null) {
                            for (i in 0 until itemIds.length()) {
                                ids.add(itemIds.optString(i, ""))
                            }
                        }
                        val startPositionTicks = parseTicks(event.data, "StartPositionTicks")
                        val whenMs = parseWhen(event.data)
                        if (ids.isNotEmpty()) {
                            _commands.tryEmit(SyncPlayCommand.PlayQueueUpdate(
                                itemIds = ids,
                                playlistItemIds = emptyList(),
                                playingItemId = ids.first(),
                                playingPlaylistItemId = "",
                                positionTicks = startPositionTicks,
                                isPlaying = true,
                                whenMs = whenMs,
                            ))
                        }
                    }
                }
                "Playstate" -> {
                    if (isGroupActive.get()) {
                        Log.d(TAG, "Ignoring Playstate event: active SyncPlay session uses SyncPlayCommand")
                    } else {
                        val command = event.data.optString("Command", "")
                        val positionTicks = parseTicks(event.data, "SeekPositionTicks")
                        val whenMs = timeSyncManager.remoteNow()
                        when (command) {
                            "PlayPause" -> {
                                _commands.tryEmit(SyncPlayCommand.Play(0, whenMs, ""))
                            }
                            "Pause" -> {
                                _commands.tryEmit(SyncPlayCommand.Pause(0, whenMs, ""))
                            }
                            "Unpause" -> {
                                _commands.tryEmit(SyncPlayCommand.Play(0, whenMs, ""))
                            }
                            "Stop" -> {
                                _commands.tryEmit(SyncPlayCommand.Stop)
                            }
                            "Seek" -> {
                                _commands.tryEmit(SyncPlayCommand.Seek(positionTicks, whenMs, ""))
                            }
                        }
                    }
                }
                "SyncPlayGroupUpdate" -> {
                    val type = event.data.optString("Type", "")
                    val innerData = event.data.optJSONObject("Data")
                    when (type) {
                        "GroupJoined", "GroupUpdate" -> {
                            val data = innerData ?: event.data
                            val currentGroup = cachedGroup.get()
                            val groupName = data.optString("GroupName", currentGroup?.groupName ?: "")
                            val participants = data.optJSONArray("Participants")
                            val count = participants?.length() ?: currentGroup?.participantCount ?: 0
                            cachedGroup.set(SyncPlayGroup(
                                groupId = data.optString("GroupId", activeGroupIdReference.get() ?: ""),
                                groupName = groupName,
                                participantCount = count,
                                participants = if (participants != null) {
                                    (0 until participants.length())
                                        .mapNotNull { participants.optString(it)?.takeIf { name -> name.isNotBlank() } }
                                } else {
                                    currentGroup?.participants ?: emptyList()
                                },
                                isPlaying = currentGroup?.isPlaying ?: false,
                                playingItemId = currentGroup?.playingItemId,
                                playingItemName = currentGroup?.playingItemName,
                                playingPlaylistItemId = currentGroup?.playingPlaylistItemId,
                                positionTicks = currentGroup?.positionTicks,
                                playlistItemIds = currentGroup?.playlistItemIds ?: emptyList(),
                                playlistItemMap = currentGroup?.playlistItemMap ?: emptyMap(),
                                repeatMode = currentGroup?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                                shuffleMode = currentGroup?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
                            ))
                            _commands.tryEmit(SyncPlayCommand.GroupUpdate(groupName, count))
                        }
                        "PlayQueue" -> {
                            val data = innerData ?: event.data
                            val currentGroup = cachedGroup.get()
                            val playlist = data.optJSONArray("Playlist")
                            val itemIds = mutableListOf<String>()
                            val playlistItemIds = mutableListOf<String>()
                            val playlistItemMap = mutableMapOf<String, String>()
                            if (playlist != null) {
                                for (i in 0 until playlist.length()) {
                                    val item = playlist.optJSONObject(i)
                                    if (item != null) {
                                        val itemId = item.optString("ItemId", "")
                                        val playlistItemId = item.optString("PlaylistItemId", "")
                                        if (itemId.isNotBlank()) {
                                            itemIds.add(itemId)
                                        }
                                        if (playlistItemId.isNotBlank()) {
                                            playlistItemIds.add(playlistItemId)
                                        }
                                        if (itemId.isNotBlank() && playlistItemId.isNotBlank()) {
                                            playlistItemMap[playlistItemId] = itemId
                                        }
                                    }
                                }
                            }
                            val playingItemIndex = data.optInt("PlayingItemIndex", 0)
                            val startPositionTicks = parseTicks(data, "StartPositionTicks")
                            val isPlaying = data.optBoolean("IsPlaying", false)
                            val playingItemId = itemIds.getOrNull(playingItemIndex)
                                ?: currentGroup?.playingItemId
                                ?: ""
                            val playingPlaylistItemId = playlistItemIds.getOrNull(playingItemIndex)
                                ?: currentGroup?.playingPlaylistItemId
                                ?: ""
                            val whenMs = parseWhen(data)
                            val lastUpdate = data.optString("LastUpdate", "")

                            val repeatModeStr = data.optString("RepeatMode", "RepeatNone")
                            val repeatMode = when (repeatModeStr) {
                                "RepeatOne" -> SyncPlayRepeatMode.REPEAT_ONE
                                "RepeatAll" -> SyncPlayRepeatMode.REPEAT_ALL
                                else -> SyncPlayRepeatMode.REPEAT_NONE
                            }
                            val shuffleModeStr = data.optString("ShuffleMode", "Sorted")
                            val shuffleMode = when (shuffleModeStr) {
                                "Shuffle" -> SyncPlayShuffleMode.SHUFFLE
                                else -> SyncPlayShuffleMode.SORTED
                            }

                            if (playingItemId.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.PlayQueueUpdate(
                                    itemIds = itemIds,
                                    playlistItemIds = playlistItemIds,
                                    playingItemId = playingItemId,
                                    playingPlaylistItemId = playingPlaylistItemId,
                                    positionTicks = startPositionTicks,
                                    isPlaying = isPlaying,
                                    whenMs = whenMs,
                                ))
                            }
                            val groupName = data.optString("GroupName", currentGroup?.groupName ?: "")
                            cachedGroup.set(SyncPlayGroup(
                                groupId = activeGroupIdReference.get() ?: "",
                                groupName = groupName,
                                participantCount = currentGroup?.participantCount ?: 0,
                                participants = currentGroup?.participants ?: emptyList(),
                                playingItemId = playingItemId,
                                playingItemName = currentGroup?.playingItemName,
                                playingPlaylistItemId = playingPlaylistItemId,
                                isPlaying = isPlaying,
                                playlistItemIds = playlistItemIds,
                                playlistItemMap = playlistItemMap,
                                positionTicks = startPositionTicks,
                                repeatMode = repeatMode,
                                shuffleMode = shuffleMode,
                            ))
                        }
                        "StateUpdate" -> {
                            val data = innerData ?: event.data
                            val state = data.optString("State", "")
                            val reason = data.optString("Reason", "")
                            val isPlaying = state.equals("Playing", ignoreCase = true)
                            cachedGroup.set(cachedGroup.get()?.copy(isPlaying = isPlaying))
                            _commands.tryEmit(SyncPlayCommand.StateUpdate(isPlaying, reason))
                        }
                        "UserJoined" -> {
                            val userName = (innerData ?: event.data).optString("UserName", "")
                            if (userName.isBlank()) {
                                val raw = event.data.optString("Data", "")
                                if (raw.isNotBlank()) _commands.tryEmit(SyncPlayCommand.Notification("$raw joined the group"))
                            } else {
                                _commands.tryEmit(SyncPlayCommand.Notification("$userName joined the group"))
                            }
                            scope.launch { refreshGroupInfo() }
                        }
                        "UserLeft" -> {
                            val userName = (innerData ?: event.data).optString("UserName", "")
                            if (userName.isBlank()) {
                                val raw = event.data.optString("Data", "")
                                if (raw.isNotBlank()) _commands.tryEmit(SyncPlayCommand.Notification("$raw left the group"))
                            } else {
                                _commands.tryEmit(SyncPlayCommand.Notification("$userName left the group"))
                            }
                            scope.launch { refreshGroupInfo() }
                        }
                        "GroupWait" -> {
                            val data = innerData ?: event.data
                            val userName = data.optString("UserName", "")
                            if (userName.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.Notification("Waiting for $userName to buffer..."))
                            }
                            _commands.tryEmit(SyncPlayCommand.WaitForGroup)
                        }
                        "GroupLeft" -> {
                            cachedGroup.set(null)
                            isGroupActive.set(false)
                            activeGroupIdReference.set(null)
                            _commands.tryEmit(SyncPlayCommand.GroupUpdate("", 0))
                        }
                        "NotInGroup" -> {
                            cachedGroup.set(null)
                            isGroupActive.set(false)
                            activeGroupIdReference.set(null)
                            _commands.tryEmit(SyncPlayCommand.GroupUpdate("", 0))
                        }
                        "SendChatMessage" -> {
                            val data = innerData ?: event.data
                            val userId = data.optString("UserId", "")
                            val userName = data.optString("UserName", "")
                            val text = data.optString("Message", "")
                            if (text.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.ChatMessage(userId, userName, text))
                            }
                        }
                        "SyncPlayIsDisabled",
                        "GroupDoesNotExist",
                        "CreateGroupDenied",
                        "JoinGroupDenied",
                        "LibraryAccessDenied" -> {
                            val data = innerData ?: event.data
                            val message = data.optString("Message", type)
                            _commands.tryEmit(SyncPlayCommand.Notification("SyncPlay: $message"))
                        }
                    }
                }
                "GroupJoined" -> {
                    val currentGroup = cachedGroup.get()
                    val groupName = event.data.optString("GroupName", "")
                    val participants = event.data.optJSONArray("Participants")
                    val participantCount = participants?.length() ?: currentGroup?.participantCount ?: 1
                    cachedGroup.set(SyncPlayGroup(
                        groupId = event.data.optString("GroupId", activeGroupIdReference.get() ?: ""),
                        groupName = groupName.ifBlank { currentGroup?.groupName ?: "" },
                        participantCount = participantCount,
                        participants = if (participants != null) {
                            (0 until participants.length())
                                .mapNotNull { participants.optString(it)?.takeIf { name -> name.isNotBlank() } }
                        } else {
                            currentGroup?.participants ?: emptyList()
                        },
                        isPlaying = currentGroup?.isPlaying ?: false,
                        playingItemId = currentGroup?.playingItemId,
                        playingItemName = currentGroup?.playingItemName,
                        playingPlaylistItemId = currentGroup?.playingPlaylistItemId,
                        positionTicks = currentGroup?.positionTicks,
                        playlistItemIds = currentGroup?.playlistItemIds ?: emptyList(),
                        playlistItemMap = currentGroup?.playlistItemMap ?: emptyMap(),
                        repeatMode = currentGroup?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                        shuffleMode = currentGroup?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
                    ))
                    _commands.tryEmit(SyncPlayCommand.GroupUpdate(groupName, participantCount))
                }
                "GroupLeft" -> {
                    cachedGroup.set(null)
                    isGroupActive.set(false)
                    activeGroupIdReference.set(null)
                    _commands.tryEmit(SyncPlayCommand.GroupUpdate("", 0))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle SyncPlay event", e)
        }
    }

    private fun parseTicks(json: JSONObject, key: String): Long {
        val ticks = json.optJSONObject(key)
        if (ticks == null) return json.optLong(key, 0L)
        return ticks.optLong("Value", 0L)
    }

    private fun parseWhen(json: JSONObject): Long {
        val keys = listOf("When", "EmittedAt", "LastUpdatedAt", "LastUpdate")
        for (key in keys) {
            val iso = json.optString(key, "")
            if (iso.isBlank()) continue
            try {
                return java.time.Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                try {
                    return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                } catch (_: Exception) {
                }
            }
        }
        return timeSyncManager.remoteNow()
    }

    private fun shouldIgnoreEvent(type: String, json: JSONObject): Boolean {
        val sessionStart = sessionStartedAtRemoteMs.get()
        if (sessionStart <= 0L) return false

        val eventTime = parseWhen(json)
        val skewAllowanceMs = 1_500L
        return eventTime + skewAllowanceMs < sessionStart && type != "GroupJoined"
    }

    suspend fun joinGroup(groupId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Joining SyncPlay group: $groupId")
            apiClient.postCapabilities()
            apiClient.joinSyncPlayGroup(groupId)
            activeGroupIdReference.set(groupId)
            isGroupActive.set(true)
            syncPlayReady.set(false)
            sessionStartedAtRemoteMs.set(timeSyncManager.remoteNow())

            connectWebSocket()

            scope.launch {
                webSocketClient.isConnected.first { it }
                Log.d(TAG, "WebSocket connected, starting SyncPlay listeners")
            }

            startListening()
            timeSyncManager.start()

            scope.launch {
                timeSyncManager.pingUpdated.first()
                syncPlayReady.set(true)
                Log.d(TAG, "SyncPlay ready (time sync first ping received)")
                queuedCommand?.let { cmd ->
                    Log.d(TAG, "Processing queued command: $cmd")
                    _commands.tryEmit(cmd)
                    queuedCommand = null
                }
            }

            startPingReporting()

            refreshGroupInfo()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join SyncPlay group", e)
            Result.failure(e)
        }
    }

    private suspend fun connectWebSocket() {
        val server = authRepository.currentServer.first() ?: return
        val user = authRepository.currentUser.first() ?: return
        if (server.address.isNotBlank() && user.accessToken.isNotBlank()) {
            webSocketClient.connect(
                serverAddress = server.address,
                accessToken = user.accessToken,
                device = "JellyPlay-${user.id.take(8)}",
                deviceName = "JellyPlay",
                client = "JellyPlay",
            )
        }
    }

    suspend fun leaveGroup(): Result<Unit> {
        Log.d(TAG, "Leaving SyncPlay group")
        val apiResult = try {
            apiClient.leaveSyncPlayGroup()
        } catch (e: Exception) {
            Log.w(TAG, "leaveSyncPlayGroup API failed", e)
            Result.failure(e)
        }
        activeGroupIdReference.set(null)
        isGroupActive.set(false)
        cachedGroup.set(null)
        syncPlayReady.set(false)
        queuedCommand = null
        sessionStartedAtRemoteMs.set(0L)
        eventJob?.cancel()
        keepAliveJob?.cancel()
        pingReportJob?.cancel()
        timeSyncManager.stop()
        webSocketClient.disconnect()
        return apiResult
    }

    suspend fun createGroup(groupName: String): Result<Unit> {
        return try {
            apiClient.postCapabilities()
            apiClient.createSyncPlayGroup(groupName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startPingReporting() {
        pingReportJob?.cancel()
        pingReportJob = scope.launch {
            timeSyncManager.pingUpdated.collect {
                if (isInSyncPlaySession) {
                    try {
                        val ping = timeSyncManager.getPingMs()
                        apiClient.syncPlayPing(ping)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to report ping", e)
                    }
                }
            }
        }
    }

    private suspend fun refreshGroupInfo() {
        try {
            val groupId = activeGroupIdReference.get() ?: return
            val info = apiClient.getSyncPlayInfo(groupId).getOrNull() ?: return
            val currentGroup = cachedGroup.get()
            val newGroup = SyncPlayGroup(
                groupId = info.groupId,
                groupName = info.groupName,
                participantCount = info.participants.size,
                participants = info.participants.map { it.userName },
                isPlaying = info.isPlaying,
                playingItemId = info.playingItemId ?: currentGroup?.playingItemId,
                playingItemName = info.playingItemName ?: currentGroup?.playingItemName,
                playingPlaylistItemId = currentGroup?.playingPlaylistItemId,
                positionTicks = info.positionTicks ?: currentGroup?.positionTicks,
                playlistItemIds = currentGroup?.playlistItemIds ?: emptyList(),
                playlistItemMap = currentGroup?.playlistItemMap ?: emptyMap(),
                repeatMode = currentGroup?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                shuffleMode = currentGroup?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
            )
            cachedGroup.set(newGroup)
            _commands.tryEmit(SyncPlayCommand.GroupUpdate(newGroup.groupName, newGroup.participantCount))
        } catch (_: Exception) {}
    }

    suspend fun reportReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
    ): Result<Unit> {
        return try {
            val remoteNow = timeSyncManager.remoteNow()
            Log.d(TAG, "reportReady: pos=$positionTicks, isPlaying=$isPlaying, item=$playlistItemId, remoteNow=$remoteNow")
            apiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId, remoteNow)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "reportReady failed", e)
            Result.failure(e)
        }
    }

    suspend fun reportBuffering(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
    ): Result<Unit> {
        return try {
            val remoteNow = timeSyncManager.remoteNow()
            Log.d(TAG, "reportBuffering: pos=$positionTicks, isPlaying=$isPlaying, item=$playlistItemId, remoteNow=$remoteNow")
            apiClient.syncPlayBuffering(positionTicks, isPlaying, playlistItemId, remoteNow)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "reportBuffering failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendPause(): Result<Unit> {
        return try {
            apiClient.syncPlayPause()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendUnpause(): Result<Unit> {
        return try {
            apiClient.syncPlayUnpause()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSeek(positionTicks: Long): Result<Unit> {
        return try {
            apiClient.syncPlaySeek(positionTicks)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendStop(): Result<Unit> {
        return try {
            apiClient.syncPlayStop()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendNextItem(playlistItemId: String): Result<Unit> {
        return try {
            apiClient.syncPlayNextItem(playlistItemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPreviousItem(playlistItemId: String): Result<Unit> {
        return try {
            apiClient.syncPlayPreviousItem(playlistItemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setRepeatMode(mode: SyncPlayRepeatMode): Result<Unit> {
        return try {
            apiClient.syncPlaySetRepeatMode(mode)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setShuffleMode(mode: SyncPlayShuffleMode): Result<Unit> {
        return try {
            apiClient.syncPlaySetShuffleMode(mode)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ): Result<Unit> {
        return try {
            apiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setIgnoreWait(ignore: Boolean): Result<Unit> {
        return try {
            apiClient.syncPlaySetIgnoreWait(ignore)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFromPlaylist(playlistItemId: String): Result<Unit> {
        return try {
            apiClient.syncPlayRemoveFromPlaylist(playlistItemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun movePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit> {
        return try {
            apiClient.syncPlayMovePlaylistItem(playlistItemId, newIndex)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queueItems(itemIds: List<String>, mode: String = "Queue"): Result<Unit> {
        return try {
            apiClient.syncPlayQueue(itemIds, mode)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPlaylistItem(playlistItemId: String): Result<Unit> {
        return try {
            apiClient.syncPlaySetPlaylistItem(playlistItemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        Log.w(TAG, "SyncPlay chat is not supported by standard Jellyfin servers; message will not be delivered")
        val data = JSONObject().apply {
            put("Message", text)
        }
        webSocketClient.sendMessage("SyncPlayCommand", JSONObject().apply {
            put("Name", "SendChatMessage")
            put("Data", data)
        })
    }

    fun calculateLatency(whenMs: Long): Long {
        return (timeSyncManager.remoteNow() - whenMs).coerceAtLeast(0)
    }

    fun remoteNow(): Long = timeSyncManager.remoteNow()

    fun estimateCurrentTicks(positionTicks: Long, whenMs: Long): Long {
        val elapsedMs = timeSyncManager.remoteNow() - whenMs
        return positionTicks + elapsedMs * 10_000
    }

    fun reset() {
        activeGroupIdReference.set(null)
        isGroupActive.set(false)
        cachedGroup.set(null)
        syncPlayReady.set(false)
        sessionStartedAtRemoteMs.set(0L)
        eventJob?.cancel()
        keepAliveJob?.cancel()
        pingReportJob?.cancel()
        timeSyncManager.stop()
        webSocketClient.disconnect()
    }

    companion object {
        private const val TAG = "SyncPlayManager"
    }
}
