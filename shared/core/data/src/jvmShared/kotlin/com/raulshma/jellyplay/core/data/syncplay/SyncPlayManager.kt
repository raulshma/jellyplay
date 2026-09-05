package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ConnectionCredentials
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class SyncPlayManager(
    private val apiClient: JellyfinApiClient,
    private val webSocketClient: JellyfinWebSocketClient,
    private val authRepository: AuthRepository,
    private val timeSyncManager: TimeSyncManager,
    private val serverIdentityStore: ServerIdentityStore,
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
    private var pingReportJob: Job? = null
    private var reconnectWatchJob: Job? = null

    /**
     * Wall-clock ms of the most-recent WebSocket reconnect that happened while a
     * SyncPlay session was active. Consumers (e.g. [SyncPlayViewModel]) can read
     * this to ignore transient empty [SyncPlayEvent.GroupUpdate] messages that the
     * server emits right after a drop, instead of treating them as an ejection.
     */
    private val lastReconnectAtMs = AtomicLong(0L)

    private val queuedEvent = AtomicReference<SyncPlayEvent?>(null)

    private val _events = MutableSharedFlow<SyncPlayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncPlayEvent> = _events.asSharedFlow()

    private val _currentGroup = MutableStateFlow<SyncPlayGroup?>(null)
    val currentGroupFlow: StateFlow<SyncPlayGroup?> = _currentGroup.asStateFlow()

    val currentGroup: SyncPlayGroup? get() = cachedGroup.get()
    val activeGroupId: String? get() = activeGroupIdRef.get()
    val isInSyncPlaySession: Boolean get() = isGroupActive.get() && activeGroupIdRef.get() != null
    /** See [lastReconnectAtMs]. 0 when no reconnect has occurred mid-session. */
    val lastReconnectMs: Long get() = lastReconnectAtMs.get()

    fun startListening() {
        if (eventJob?.isActive == true) return
        eventJob = scope.launch {
            webSocketClient.events.collect { wsEvent ->
                val typedEvent = eventHandler.parse(wsEvent.type, wsEvent.data) ?: return@collect
                handleEvent(typedEvent)
            }
        }
        // KeepAlive is now owned by JellyfinWebSocketClient itself (it self-pings
        // while connected and reacts to the server's ForceKeepAlive). SyncPlay no
        // longer needs its own loop, which previously left the app-lifetime socket
        // un-kept during non-SyncPlay sessions (e.g. admin dashboards).
    }

    private fun handleEvent(event: SyncPlayEvent) {
        try {
            when (event) {
                is SyncPlayEvent.PlaybackCommand -> {
                    if (!syncPlayReady.get()) {
                        Log.d(TAG, "SyncPlay not ready, queuing command: ${event.cmd.command}")
                        queuedEvent.set(event)
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
                        val updated = g.copy(isPlaying = event.isPlaying)
                        cachedGroup.set(updated)
                        _currentGroup.value = updated
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
                    teardownTo(TeardownLevel.GROUP_LEFT_KEEP_LISTENING)
                    _events.tryEmit(event)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
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
            startReconnectWatcher()
            timeSyncManager.start()

            scope.launch {
                timeSyncManager.pingUpdated.first()
                syncPlayReady.set(true)
                Log.d(TAG, "SyncPlay ready (time sync first ping received)")
                queuedEvent.getAndSet(null)?.let { evt ->
                    Log.d(TAG, "Processing queued event")
                    handleEvent(evt)
                }
            }

            startPingReporting()
            refreshGroupInfo()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join SyncPlay group", e)
            Result.failure(e)
        }
    }

    private suspend fun connectWebSocket() {
        val server = authRepository.currentServer.first() ?: return
        val user = authRepository.currentUser.first() ?: return
        if (server.address.isNotBlank() && user.accessToken.isNotBlank()) {
            val deviceId = serverIdentityStore.ensureDeviceId()
            webSocketClient.connect(
                ConnectionCredentials(
                    serverAddress = server.address,
                    accessToken = user.accessToken,
                    deviceId = deviceId,
                    deviceName = ConnectionCredentials.deviceNameFor(user.name),
                    clientName = "JellyPlay",
                )
            )
        }
    }

    /**
     * Watches the shared [JellyfinWebSocketClient] for transient disconnects and, on
     * every false → true transition of [JellyfinWebSocketClient.isConnected], re-asserts
     * the user's current group membership. Without this the WS auto-reconnects but the
     * server-side group listener never gets re-established, so a momentary network blip
     * silently orphans the watch party.
     */
    private fun startReconnectWatcher() {
        reconnectWatchJob?.cancel()
        reconnectWatchJob = scope.launch {
            // StateFlow already de-duplicates consecutive equal emissions, so we only
            // need drop(1) (skip the initial value, which on join is already `true`)
            // and filter { it } (only react to reconnects, not disconnects). Each
            // collected `true` therefore represents a reconnect.
            webSocketClient.isConnected
                .drop(1)
                .filter { it }
                .collect {
                    val groupId = activeGroupIdRef.get()
                    if (!isGroupActive.get() || groupId == null) return@collect
                    lastReconnectAtMs.set(System.currentTimeMillis())
                    Log.d(TAG, "WebSocket reconnected mid-session, re-asserting group membership: $groupId")
                    try {
                        apiClient.postCapabilities()
                        apiClient.joinSyncPlayGroup(groupId)
                        refreshGroupInfo()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to re-assert SyncPlay group membership after reconnect", e)
                    }
                }
        }
    }

    suspend fun leaveGroup(): Result<Unit> {
        Log.d(TAG, "Leaving SyncPlay group")
        val apiResult = try {
            apiClient.leaveSyncPlayGroup()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "leaveSyncPlayGroup API failed", e)
            Result.failure(e)
        }
        teardownTo(TeardownLevel.FULL)
        return apiResult
    }

    suspend fun createGroup(groupName: String): Result<Unit> {
        return try {
            apiClient.postCapabilities()
            apiClient.createSyncPlayGroup(groupName)
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startPingReporting() {
        pingReportJob?.cancel()
        pingReportJob = scope.launch {
            timeSyncManager.pingUpdated
                .sample(PING_REPORT_INTERVAL_MS)
                .collect {
                    if (isInSyncPlaySession) {
                        try {
                            val ping = timeSyncManager.getPingMs()
                            apiClient.syncPlayPing(ping)
                        } catch (ce: CancellationException) {
                            throw ce
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
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {}
    }

    fun remoteNow(): Long = timeSyncManager.remoteNow()

    fun estimateCurrentTicks(positionTicks: Long, whenMs: Long): Long {
        val elapsedMs = timeSyncManager.remoteNow() - whenMs
        return positionTicks + elapsedMs * 10_000
    }

    /**
     * How much teardown a departure from a SyncPlay group performs. The three
     * former teardown copies ([leaveGroup], [reset], the GroupLeft handler)
     * differed only in these terms, so they share [teardownTo].
     */
    private enum class TeardownLevel {
        /**
         * Full teardown: session state cleared, listener / ping / reconnect
         * jobs cancelled, [TimeSyncManager] stopped, shared WebSocket
         * disconnected. Used by [leaveGroup] and [reset], where the user (or
         * the app lifecycle) has decided SyncPlay is over.
         */
        FULL,

        /**
         * Server-initiated GroupLeft: clears the session state but
         * deliberately keeps the listener / ping / reconnect jobs running and
         * the WebSocket connected. The socket is app-lifetime shared
         * infrastructure, and the user may immediately rejoin (or the server
         * re-add us) — cancelling [eventJob] would drop the GroupUpdate /
         * GroupJoined messages that make that rejoin observable, and
         * disconnecting would kill unrelated WebSocket consumers (admin
         * dashboards, remote control).
         */
        GROUP_LEFT_KEEP_LISTENING,
    }

    private fun teardownTo(level: TeardownLevel) {
        cachedGroup.set(null)
        _currentGroup.value = null
        isGroupActive.set(false)
        activeGroupIdRef.set(null)
        syncPlayReady.set(false)
        queuedEvent.set(null)
        syncPlayEnabledAtMs.set(0L)
        if (level == TeardownLevel.FULL) {
            lastReconnectAtMs.set(0L)
            eventJob?.cancel()
            pingReportJob?.cancel()
            reconnectWatchJob?.cancel()
        }
        queueCore.clear()
        playbackCore.onGroupLeft()
        if (level == TeardownLevel.FULL) {
            timeSyncManager.stop()
            webSocketClient.disconnect()
        }
    }

    fun reset() {
        teardownTo(TeardownLevel.FULL)
    }

    companion object {
        private const val TAG = "SyncPlayManager"
        private const val STALE_SKEW_ALLOWANCE_MS = 1500L
        private const val PING_REPORT_INTERVAL_MS = 10_000L
    }
}
