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
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncPlayCommand {
    data class Play(val positionTicks: Long) : SyncPlayCommand()
    data class Pause(val positionTicks: Long) : SyncPlayCommand()
    data class Seek(val positionTicks: Long) : SyncPlayCommand()
    data object Stop : SyncPlayCommand()
    data class PlayQueueUpdate(
        val itemIds: List<String>,
        val playingItemId: String,
        val positionTicks: Long,
        val isPlaying: Boolean,
    ) : SyncPlayCommand()
    data class StateUpdate(val isPlaying: Boolean) : SyncPlayCommand()
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
) {
    private var activeGroupId = AtomicReference<String?>(null)
    private var isGroupActive = AtomicBoolean(false)
    private var cachedGroup = AtomicReference<SyncPlayGroup?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var eventJob: Job? = null
    private var keepAliveJob: Job? = null

    private val _commands = MutableSharedFlow<SyncPlayCommand>(extraBufferCapacity = 10)
    val commands: SharedFlow<SyncPlayCommand> = _commands.asSharedFlow()

    val currentGroup: SyncPlayGroup? get() = cachedGroup.get()

    val isInSyncPlaySession: Boolean get() = isGroupActive.get() && activeGroupId.get() != null

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
            when (event.type) {
                "SyncPlayCommand" -> {
                    val command = event.data.optString("Command", "")
                    val positionTicks = parseTicks(event.data, "PositionTicks")
                    when (command) {
                        "Unpause" -> {
                            _commands.tryEmit(SyncPlayCommand.Play(positionTicks))
                        }
                        "Pause" -> {
                            _commands.tryEmit(SyncPlayCommand.Pause(positionTicks))
                        }
                        "Seek" -> {
                            _commands.tryEmit(SyncPlayCommand.Seek(positionTicks))
                        }
                        "Stop" -> {
                            _commands.tryEmit(SyncPlayCommand.Stop)
                        }
                    }
                }
                "Play" -> {
                    val itemIds = event.data.optJSONArray("ItemIds")
                    val ids = mutableListOf<String>()
                    if (itemIds != null) {
                        for (i in 0 until itemIds.length()) {
                            ids.add(itemIds.optString(i, ""))
                        }
                    }
                    val startPositionTicks = parseTicks(event.data, "StartPositionTicks")
                    if (ids.isNotEmpty()) {
                        _commands.tryEmit(SyncPlayCommand.PlayQueueUpdate(
                            itemIds = ids,
                            playingItemId = ids.first(),
                            positionTicks = startPositionTicks,
                            isPlaying = true,
                        ))
                    }
                }
                "SyncPlayGroupUpdate" -> {
                    val type = event.data.optString("Type", "")
                    when (type) {
                        "GroupJoined", "GroupUpdate" -> {
                            val groupName = event.data.optString("GroupName", cachedGroup.get()?.groupName ?: "")
                            val participants = event.data.optJSONArray("Participants")
                            val count = participants?.length() ?: cachedGroup.get()?.participantCount ?: 0
                            cachedGroup.set(SyncPlayGroup(
                                groupId = activeGroupId.get() ?: "",
                                groupName = groupName,
                                participantCount = count,
                                participants = (0 until count).mapNotNull { participants?.optString(it) },
                                isPlaying = cachedGroup.get()?.isPlaying ?: false,
                            ))
                            _commands.tryEmit(SyncPlayCommand.GroupUpdate(groupName, count))
                        }
                        "PlayQueue" -> {
                            val playlist = event.data.optJSONArray("Playlist")
                            val itemIds = mutableListOf<String>()
                            if (playlist != null) {
                                for (i in 0 until playlist.length()) {
                                    val item = playlist.optJSONObject(i)
                                    if (item != null) {
                                        itemIds.add(item.optString("ItemId", ""))
                                    }
                                }
                            }
                            val playingItemIndex = event.data.optInt("PlayingItemIndex", 0)
                            val startPositionTicks = parseTicks(event.data, "StartPositionTicks")
                            val isPlaying = event.data.optBoolean("IsPlaying", false)
                            val playingItemId = itemIds.getOrNull(playingItemIndex) ?: ""
                            if (playingItemId.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.PlayQueueUpdate(
                                    itemIds = itemIds,
                                    playingItemId = playingItemId,
                                    positionTicks = startPositionTicks,
                                    isPlaying = isPlaying,
                                ))
                            }
                            val groupName = event.data.optString("GroupName", cachedGroup.get()?.groupName ?: "")
                            cachedGroup.set(SyncPlayGroup(
                                groupId = activeGroupId.get() ?: "",
                                groupName = groupName,
                                participantCount = cachedGroup.get()?.participantCount ?: 0,
                                participants = cachedGroup.get()?.participants ?: emptyList(),
                                playingItemId = playingItemId,
                                isPlaying = isPlaying,
                                positionTicks = startPositionTicks,
                            ))
                        }
                        "StateUpdate" -> {
                            val state = event.data.optString("State", "")
                            val isPlaying = state.equals("Playing", ignoreCase = true)
                            cachedGroup.set(cachedGroup.get()?.copy(isPlaying = isPlaying))
                            _commands.tryEmit(SyncPlayCommand.StateUpdate(isPlaying))
                        }
                        "UserJoined" -> {
                            val userName = event.data.optString("UserName", "")
                            if (userName.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.Notification("$userName joined the group"))
                            }
                            scope.launch { refreshGroupInfo() }
                        }
                        "UserLeft" -> {
                            val userName = event.data.optString("UserName", "")
                            if (userName.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.Notification("$userName left the group"))
                            }
                            scope.launch { refreshGroupInfo() }
                        }
                        "GroupWait" -> {
                            val userName = event.data.optString("UserName", "")
                            if (userName.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.Notification("Waiting for $userName to buffer..."))
                            }
                        }
                        "GroupLeft" -> {
                            cachedGroup.set(null)
                            isGroupActive.set(false)
                            activeGroupId.set(null)
                            _commands.tryEmit(SyncPlayCommand.GroupUpdate("", 0))
                        }
                        "SendChatMessage" -> {
                            val userId = event.data.optString("UserId", "")
                            val userName = event.data.optString("UserName", "")
                            val text = event.data.optString("Message", "")
                            if (text.isNotBlank()) {
                                _commands.tryEmit(SyncPlayCommand.ChatMessage(userId, userName, text))
                            }
                        }
                    }
                }
                "GroupJoined" -> {
                    val groupName = event.data.optString("GroupName", "")
                    _commands.tryEmit(SyncPlayCommand.GroupUpdate(groupName, 1))
                }
                "GroupLeft" -> {
                    cachedGroup.set(null)
                    isGroupActive.set(false)
                    activeGroupId.set(null)
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

    suspend fun getAvailableGroups(): List<SyncPlayGroup> {
        return try {
            apiClient.getSyncPlayGroups().getOrElse { emptyList() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun joinGroup(groupId: String): Result<Unit> {
        return try {
            apiClient.joinSyncPlayGroup(groupId)
            activeGroupId.set(groupId)
            isGroupActive.set(true)
            connectWebSocket()
            refreshGroupInfo()
            startListening()
            Result.success(Unit)
        } catch (e: Exception) {
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
            )
        }
    }

    suspend fun leaveGroup(): Result<Unit> {
        val apiResult = try {
            apiClient.leaveSyncPlayGroup()
        } catch (e: Exception) {
            Log.w(TAG, "leaveSyncPlayGroup API failed", e)
            Result.failure(e)
        }
        activeGroupId.set(null)
        isGroupActive.set(false)
        cachedGroup.set(null)
        eventJob?.cancel()
        keepAliveJob?.cancel()
        webSocketClient.disconnect()
        return apiResult
    }

    suspend fun createGroup(groupName: String): Result<Unit> {
        return try {
            apiClient.createSyncPlayGroup(groupName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun refreshGroupInfo() {
        try {
            val info = apiClient.getSyncPlayInfo().getOrNull() ?: return
            cachedGroup.set(SyncPlayGroup(
                groupId = info.groupId,
                groupName = info.groupName,
                participantCount = info.participants.size,
                participants = info.participants.map { it.userName },
                isPlaying = info.isPlaying,
            ))
        } catch (_: Exception) {}
    }

    suspend fun reportReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
    ): Result<Unit> {
        return try {
            apiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportBuffering(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
    ): Result<Unit> {
        return try {
            apiClient.syncPlayBuffering(positionTicks, isPlaying, playlistItemId)
            Result.success(Unit)
        } catch (e: Exception) {
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
        startPositionTicks: Long = 0L,
    ): Result<Unit> {
        return try {
            apiClient.syncPlaySetNewQueue(itemIds, playingItemId, startPositionTicks)
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

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val data = JSONObject().apply {
            put("Message", text)
        }
        webSocketClient.sendMessage("SyncPlayCommand", JSONObject().apply {
            put("Name", "SendChatMessage")
            put("Data", data)
        })
    }

    fun reset() {
        activeGroupId.set(null)
        isGroupActive.set(false)
        cachedGroup.set(null)
        eventJob?.cancel()
        keepAliveJob?.cancel()
        webSocketClient.disconnect()
    }

    companion object {
        private const val TAG = "SyncPlayManager"
    }
}
