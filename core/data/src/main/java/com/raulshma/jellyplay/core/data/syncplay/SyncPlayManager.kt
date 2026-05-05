package com.raulshma.jellyplay.core.data.syncplay

import android.util.Log
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncPlayCommand {
    data class PrepareSession(
        val positionTicks: Long,
        val isPlaying: Boolean,
        val itemIds: List<String>,
    ) : SyncPlayCommand()

    data class Play(val positionTicks: Long) : SyncPlayCommand()
    data class Pause(val positionTicks: Long) : SyncPlayCommand()
    data class Seek(val positionTicks: Long) : SyncPlayCommand()
    data class GroupUpdate(val groupName: String, val participantCount: Int) : SyncPlayCommand()
    data object WaitForGroup : SyncPlayCommand()
}

@Singleton
class SyncPlayManager @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val webSocketClient: JellyfinWebSocketClient,
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
                    val command = event.data.optString("Name", "")
                    when (command) {
                        "PrepareSession" -> {
                            val positionTicks = parseTicks(event.data, "PositionTicks")
                            val isPlaying = event.data.optBoolean("IsPlaying", false)
                            _commands.tryEmit(SyncPlayCommand.PrepareSession(
                                positionTicks = positionTicks,
                                isPlaying = isPlaying,
                                itemIds = emptyList(),
                            ))
                        }
                        "Play" -> {
                            val positionTicks = parseTicks(event.data, "PositionTicks")
                            _commands.tryEmit(SyncPlayCommand.Play(positionTicks))
                        }
                        "Pause" -> {
                            val positionTicks = parseTicks(event.data, "PositionTicks")
                            _commands.tryEmit(SyncPlayCommand.Pause(positionTicks))
                        }
                        "Seek" -> {
                            val positionTicks = parseTicks(event.data, "PositionTicks")
                            _commands.tryEmit(SyncPlayCommand.Seek(positionTicks))
                        }
                        "WaitForGroup" -> {
                            _commands.tryEmit(SyncPlayCommand.WaitForGroup)
                        }
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
                        "UserJoined", "UserLeft" -> {
                            scope.launch { refreshGroupInfo() }
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
            refreshGroupInfo()
            startListening()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun reportReady(): Result<Unit> {
        return try {
            apiClient.syncPlayReady()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportBuffering(): Result<Unit> {
        return try {
            apiClient.syncPlayBuffering()
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
