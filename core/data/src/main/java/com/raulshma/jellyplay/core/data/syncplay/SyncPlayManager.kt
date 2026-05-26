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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayManager @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val webSocketClient: JellyfinWebSocketClient,
    private val authRepository: AuthRepository,
    private val timeSyncManager: TimeSyncManager,
    val eventHandler: SyncPlayEventHandler,
    val syncPlayController: SyncPlayController,
    val playbackCore: SyncPlayPlaybackCore,
    val queueCore: SyncPlayQueueCore,
) {
    private val activeGroupIdRef = AtomicReference<String?>(null)
    private val isGroupActive = AtomicBoolean(false)
    private val syncPlayEnabledAtMs = AtomicLong(0L)
    private val syncPlayReady = AtomicBoolean(false)
    private val cachedGroup = AtomicReference<SyncPlayGroup?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var eventJob: Job? = null
    private var keepAliveJob: Job? = null
    private var pingReportJob: Job? = null

    @Volatile
    private var queuedEvent: SyncPlayEvent? = null

    private val _events = MutableSharedFlow<SyncPlayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncPlayEvent> = _events.asSharedFlow()

    private val _currentGroup = MutableStateFlow<SyncPlayGroup?>(null)
    val currentGroupFlow: StateFlow<SyncPlayGroup?> = _currentGroup.asStateFlow()

    val currentGroup: SyncPlayGroup? get() = cachedGroup.get()
    val activeGroupId: String? get() = activeGroupIdRef.get()
    val isInSyncPlaySession: Boolean get() = isGroupActive.get() && activeGroupIdRef.get() != null

    fun startListening() {
        if (eventJob?.isActive == true) return
        eventJob = scope.launch {
            webSocketClient.events.collect { wsEvent ->
                val typedEvent = eventHandler.parse(wsEvent.type, wsEvent.data) ?: return@collect
                handleEvent(typedEvent)
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
            when (event) {
                is SyncPlayEvent.PlaybackCommand -> {
                    if (!syncPlayReady.get()) {
                        Log.d(TAG, "SyncPlay not ready, queuing command: ${event.cmd.command}")
                        queuedEvent = event
                        return
                    }
                    if (event.cmd.emittedAtMs > 0 && syncPlayEnabledAtMs.get() > 0) {
                        if (event.cmd.emittedAtMs < syncPlayEnabledAtMs.get() - STALE_SKEW_ALLOWANCE_MS) {
                            Log.d(TAG, "Ignoring stale command: emittedAt=${event.cmd.emittedAtMs}, enabledAt=${syncPlayEnabledAtMs.get()}")
                            return
                        }
                    }
                    _events.tryEmit(event)
                    playbackCore.applyCommand(event.cmd)
                }
                is SyncPlayEvent.PlayQueueUpdate -> {
                    if (queueCore.updatePlayQueue(event.data)) {
                        updateCachedGroupFromQueue(event.data)
                    }
                    _events.tryEmit(event)
                }
                is SyncPlayEvent.GroupUpdate -> {
                    updateCachedGroup(event.groupName, event.participantCount)
                    _events.tryEmit(event)
                }
                is SyncPlayEvent.StateUpdate -> {
                    cachedGroup.get()?.let { g ->
                        cachedGroup.set(g.copy(isPlaying = event.isPlaying))
                        _currentGroup.value = g.copy(isPlaying = event.isPlaying)
                    }
                    _events.tryEmit(event)
                }
                is SyncPlayEvent.WaitForGroup -> {
                    _events.tryEmit(event)
                }
                is SyncPlayEvent.Notification -> {
                    _events.tryEmit(event)
                }
                is SyncPlayEvent.GroupLeft -> {
                    cachedGroup.set(null)
                    _currentGroup.value = null
                    isGroupActive.set(false)
                    activeGroupIdRef.set(null)
                    syncPlayReady.set(false)
                    queuedEvent = null
                    syncPlayEnabledAtMs.set(0L)
                    queueCore.clear()
                    playbackCore.onGroupLeft()
                    _events.tryEmit(event)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle SyncPlay event", e)
        }
    }

    private fun updateCachedGroup(groupName: String, participantCount: Int) {
        val current = cachedGroup.get()
        val newGroup = (current ?: SyncPlayGroup(
            groupId = activeGroupIdRef.get() ?: "",
            groupName = groupName,
            participantCount = participantCount,
        )).copy(
            groupName = groupName.ifBlank { current?.groupName ?: "" },
            participantCount = participantCount,
        )
        cachedGroup.set(newGroup)
        _currentGroup.value = newGroup
    }

    private fun updateCachedGroupFromQueue(data: com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData) {
        val current = cachedGroup.get()
        val newGroup = (current ?: SyncPlayGroup(
            groupId = activeGroupIdRef.get() ?: "",
            groupName = "",
            participantCount = 0,
        )).copy(
            playingItemId = data.playingItemId.ifBlank { current?.playingItemId },
            playingPlaylistItemId = data.playingPlaylistItemId.ifBlank { current?.playingPlaylistItemId },
            isPlaying = data.isPlaying,
            positionTicks = data.startPositionTicks,
            playlistItemIds = data.playlistItemIds,
            repeatMode = data.repeatMode,
            shuffleMode = data.shuffleMode,
        )
        cachedGroup.set(newGroup)
        _currentGroup.value = newGroup
    }

    suspend fun joinGroup(groupId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Joining SyncPlay group: $groupId")
            apiClient.postCapabilities()
            apiClient.joinSyncPlayGroup(groupId)
            activeGroupIdRef.set(groupId)
            isGroupActive.set(true)
            syncPlayReady.set(false)
            syncPlayEnabledAtMs.set(timeSyncManager.remoteNow())

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
                queuedEvent?.let { evt ->
                    Log.d(TAG, "Processing queued event")
                    handleEvent(evt)
                    queuedEvent = null
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
        cachedGroup.set(null)
        _currentGroup.value = null
        isGroupActive.set(false)
        activeGroupIdRef.set(null)
        syncPlayReady.set(false)
        queuedEvent = null
        syncPlayEnabledAtMs.set(0L)
        eventJob?.cancel()
        keepAliveJob?.cancel()
        pingReportJob?.cancel()
        queueCore.clear()
        playbackCore.onGroupLeft()
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
            val groupId = activeGroupIdRef.get() ?: return
            val info = apiClient.getSyncPlayInfo(groupId).getOrNull() ?: return
            val current = cachedGroup.get()
            val newGroup = SyncPlayGroup(
                groupId = info.groupId,
                groupName = info.groupName,
                participantCount = info.participants.size,
                participants = info.participants.map { it.userName },
                isPlaying = info.isPlaying,
                playingItemId = info.playingItemId ?: current?.playingItemId,
                playingItemName = info.playingItemName ?: current?.playingItemName,
                playingPlaylistItemId = current?.playingPlaylistItemId,
                positionTicks = info.positionTicks ?: current?.positionTicks,
                playlistItemIds = current?.playlistItemIds ?: emptyList(),
                playlistItemMap = current?.playlistItemMap ?: emptyMap(),
                repeatMode = current?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                shuffleMode = current?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
            )
            cachedGroup.set(newGroup)
            _currentGroup.value = newGroup
        } catch (_: Exception) {}
    }

    fun remoteNow(): Long = timeSyncManager.remoteNow()

    fun estimateCurrentTicks(positionTicks: Long, whenMs: Long): Long {
        val elapsedMs = timeSyncManager.remoteNow() - whenMs
        return positionTicks + elapsedMs * 10_000
    }

    fun reset() {
        cachedGroup.set(null)
        _currentGroup.value = null
        isGroupActive.set(false)
        activeGroupIdRef.set(null)
        syncPlayReady.set(false)
        syncPlayEnabledAtMs.set(0L)
        eventJob?.cancel()
        keepAliveJob?.cancel()
        pingReportJob?.cancel()
        queueCore.clear()
        playbackCore.onGroupLeft()
        timeSyncManager.stop()
        webSocketClient.disconnect()
    }

    companion object {
        private const val TAG = "SyncPlayManager"
        private const val STALE_SKEW_ALLOWANCE_MS = 1500L
    }
}
