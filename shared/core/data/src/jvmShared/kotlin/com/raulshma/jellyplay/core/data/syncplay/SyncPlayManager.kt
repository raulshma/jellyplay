package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ConnectionCredentials
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The process-wide SyncPlay facade and the ONLY public reach into the four
 * cores ([SyncPlayEventHandler], [SyncPlayController], [SyncPlayPlaybackCore],
 * [SyncPlayQueueCore] — injected here, private from here on). Consumers
 * (the player bridge, the syncplay feature session, the video ViewModel)
 * name intents on this manager; they never dereference a core. The cores'
 * own members are module-internal for exactly that reason.
 *
 * The manager's surface is grouped by intent: group lifecycle
 * ([joinGroup]/[leaveGroup]/[createGroup]/[reset]), observation
 * ([events]/[currentGroupFlow]/[currentGroup]/[activeGroupId]/
 * [isInSyncPlaySession]/[lastReconnectMs]/[ignoreWait]), the session attach
 * seam ([attachSession]/[detachSession]), local playback reporting and queue
 * sync (the playback-core intents), and the group transport forwarders —
 * thin [SyncPlayController] delegation where the caller keeps owning the
 * launch, preserving the controller's fire-and-forget routing semantics.
 */
class SyncPlayManager(
    /**
     * Group lifecycle + info + ping (the PlaybackRepositoryImpl ctor
     * precedent: family seams, not the JellyfinApiClient union — the family
     * singles compose the same impls the union delegates to).
     */
    private val syncPlayApiClient: SyncPlayApiClient,
    /** The pre-join capability broadcast (postCapabilities). */
    private val authApiClient: AuthApiClient,
    private val webSocketClient: JellyfinWebSocketClient,
    private val authRepository: AuthRepository,
    private val timeSyncManager: TimeSyncManager,
    private val serverIdentityStore: ServerIdentityStore,
    private val eventHandler: SyncPlayEventHandler,
    private val syncPlayController: SyncPlayController,
    private val playbackCore: SyncPlayPlaybackCore,
    private val queueCore: SyncPlayQueueCore,
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
            authApiClient.postCapabilities()
            syncPlayApiClient.joinSyncPlayGroup(groupId)
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
     * Watches the shared [JellyfinWebSocketClient] for reconnects and, on every
     * [JellyfinWebSocketClient.reconnects] emission while a SyncPlay session is
     * active, re-asserts the user's current group membership. Without this the
     * WS auto-reconnects but the server-side group listener never gets
     * re-established, so a momentary network blip silently orphans the watch
     * party.
     *
     * Declared behavior change vs the former
     * `isConnected.drop(1).filter { it }` edge derivation: that shape also
     * fired on the FIRST join's open whenever the subscription won the race
     * against the handshake — a duplicate of [joinGroup]'s own re-assert
     * sequence, and it stamped [lastReconnectAtMs] on a non-reconnect. This
     * watcher now reacts to true reconnects only (see the flow's KDoc for the
     * vocabulary).
     */
    private fun startReconnectWatcher() {
        reconnectWatchJob?.cancel()
        reconnectWatchJob = scope.launch {
            webSocketClient.reconnects.collect {
                val groupId = activeGroupIdRef.get()
                if (!isGroupActive.get() || groupId == null) return@collect
                lastReconnectAtMs.set(System.currentTimeMillis())
                Log.d(TAG, "WebSocket reconnected mid-session, re-asserting group membership: $groupId")
                try {
                    authApiClient.postCapabilities()
                    syncPlayApiClient.joinSyncPlayGroup(groupId)
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
            syncPlayApiClient.leaveSyncPlayGroup()
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
            authApiClient.postCapabilities()
            syncPlayApiClient.createSyncPlayGroup(groupName)
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
                            syncPlayApiClient.syncPlayPing(ping)
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
            val info = syncPlayApiClient.getSyncPlayInfo(groupId).getOrNull() ?: return
            val current = cachedGroup.get()
            // playingItem* / positionTicks are carried over from the cached
            // group ONLY: this manager's producer (SyncPlayApiClientImpl's
            // getSyncPlayInfo, backed by syncPlayGetGroups) never fills the
            // SyncPlayGroupInfo counterparts — the former `info.x ?:
            // current?.x` merge arms always took the fallback. The model
            // fields themselves are alive: the syncplay feature module
            // builds SyncPlayGroupInfo locally from PlayQueueUpdate events
            // and renders playingItemName.
            val newGroup = SyncPlayGroup(
                groupId = info.groupId,
                groupName = info.groupName,
                participantCount = info.participants.size,
                participants = info.participants.map { it.userName },
                isPlaying = info.isPlaying,
                playingItemId = current?.playingItemId,
                playingItemName = current?.playingItemName,
                playingPlaylistItemId = current?.playingPlaylistItemId,
                positionTicks = current?.positionTicks,
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

    /**
     * Forward to the [TimeSyncManager] clock projection (its pure core plus a
     * `remoteNow` read on the injected instance) — kept public because the
     * player bridge consumes it through the manager.
     */
    fun estimateCurrentTicks(positionTicks: Long, whenMs: Long): Long =
        TimeSyncManager.projectCurrentTicks(positionTicks, timeSyncManager.remoteNow() - whenMs)

    // ── Session attach seam (the folded callbacks registration) ──────────────

    /**
     * Registers [session] as the one live engine-callbacks holder of the
     * process-wide playback core, REPLACING any previous registration.
     * Replace-by-construction is the whole point of the fold: the former
     * global `setCallbacks`/`clearCallbacks` pair let a stale bridge linger
     * behind a new one unless the caller remembered to clear first — the
     * player bridge used to defuse that hazard defensively at every attach
     * site. Idempotent-safe: attaching twice keeps only the newest session.
     */
    fun attachSession(session: PlaybackCoreCallbacks) {
        playbackCore.attachCallbacks(session)
    }

    /**
     * Drops the callbacks registered by [attachSession] so the process-wide
     * core stops retaining the departed player session (and through it the
     * destroyed ViewModel). The bridge's teardown path.
     */
    fun detachSession() {
        playbackCore.detachCallbacks()
    }

    // ── Local playback reporting + queue sync (playback-core intents) ────────

    /**
     * Reports a local engine playback-state transition to the group (the
     * debounced Buffering / item-load-handshake Ready machinery). [state] is
     * the core's private `STATE_*` Int space — the player bridge owns the
     * EnginePlaybackState → Int encoding and remains the only caller.
     */
    fun onPlaybackStateChanged(state: Int) {
        playbackCore.onPlaybackStateChanged(state)
    }

    /**
     * The core-owned position reconcile: estimate → clamp → lane tolerance →
     * seek + play/pause mirror. The lane vocabulary stays core-owned
     * ([SyncPlayPlaybackCore.ReconcileLane] — declared per-lane tolerances,
     * deliberately not unified; pinned by `SyncPlayPlaybackCoreReconcileTest`).
     */
    fun reconcileToServerPosition(
        serverTicks: Long,
        whenMs: Long,
        lane: SyncPlayPlaybackCore.ReconcileLane,
        groupIsPlaying: Boolean,
    ) {
        playbackCore.reconcileToServerPosition(serverTicks, whenMs, lane, groupIsPlaying)
    }

    /**
     * True when the last applied group command was an Unpause — i.e. local
     * playback that just started is group-driven, not a user resume, and must
     * not echo another unpause request back to the server.
     */
    val lastGroupCommandWasUnpause: Boolean
        get() = playbackCore.lastCommand?.command == "Unpause"

    /**
     * The position ticks a queue-driven item load should start at: the queue
     * update's start position, superseded by the last playback command when
     * that command is newer (both advanced by the clock projection). The one
     * intent that reads across BOTH the queue core and the playback core —
     * the reason it exists as a single member.
     */
    fun queuedItemStartPositionTicks(): Long =
        queueCore.getStartPositionTicks(playbackCore.lastCommand)

    /**
     * Syncs the core's notion of which playlist item local Ready/Buffering
     * reports refer to (the group's playing playlist item changed — by queue
     * update or by reattach repopulation).
     */
    fun onQueueItemChanged(playlistItemId: String?) {
        playbackCore.setCurrentPlaylistItemId(playlistItemId)
    }

    /**
     * Arms the item-load handshake: group playback commands drop until the
     * engine finishes loading the new item (the core's READY arm clears it).
     */
    fun beginPendingItemLoad() {
        playbackCore.beginPendingItemLoad()
    }

    /**
     * Drops the playback core's scheduled-command / correction state WITHOUT
     * leaving the group — the per-item teardown of the player bridge (item
     * switch, screen leave). Distinct from [reset], the full session
     * teardown.
     */
    fun resetPlaybackSync() {
        playbackCore.reset()
    }

    /** The local ignore-wait mirror (drives the player's toggle UI). */
    val ignoreWait: StateFlow<Boolean>
        get() = playbackCore.ignoreWait

    /**
     * The player path for ignore-wait: flips the local mirror synchronously
     * and fires the server command fire-and-forget. The syncplay feature's
     * awaited, mirror-free variant is [setGroupIgnoreWait].
     */
    fun setIgnoreWait(ignore: Boolean) {
        playbackCore.setIgnoreWait(ignore)
    }

    // ── Group transport commands (SyncPlayController forwarders) ─────────────
    //
    // Thin delegation over the ONE fire-and-forget wrapper home: the callers
    // keep owning the launch (the bridge wraps in its scope exactly as before
    // the fold), so the controller's routing semantics are untouched.

    /** Pauses the whole group. */
    suspend fun pauseGroup() = syncPlayController.pause()

    /** Unpauses the whole group. */
    suspend fun unpauseGroup() = syncPlayController.unpause()

    /** Seeks the whole group to [positionTicks]. */
    suspend fun seekGroup(positionTicks: Long) = syncPlayController.seek(positionTicks)

    /** Stops group playback. */
    suspend fun stopGroup() = syncPlayController.stop()

    /** Advances the group's queue to the item after [playlistItemId]. */
    suspend fun nextQueueItem(playlistItemId: String) = syncPlayController.nextItem(playlistItemId)

    /** Moves the group's queue back to the item before [playlistItemId]. */
    suspend fun previousQueueItem(playlistItemId: String) = syncPlayController.previousItem(playlistItemId)

    /** Points the group's existing queue at [playlistItemId] (no queue replace). */
    suspend fun setQueueItem(playlistItemId: String) = syncPlayController.setPlaylistItem(playlistItemId)

    /** Replaces the group's queue with [itemIds], playing [playingItemId]. */
    suspend fun setNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ) = syncPlayController.setNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)

    /** Sets the group's repeat mode. */
    suspend fun setGroupRepeatMode(mode: SyncPlayRepeatMode) = syncPlayController.setRepeatMode(mode)

    /** Sets the group's shuffle mode. */
    suspend fun setGroupShuffleMode(mode: SyncPlayShuffleMode) = syncPlayController.setShuffleMode(mode)

    /**
     * The AWAITED ignore-wait transport command (the syncplay feature
     * session's variant): sends the server command WITHOUT flipping the
     * playback core's local mirror. [setIgnoreWait] is the player path that
     * does both; the divergence is declared behavior preserved from the
     * pre-fold call graph.
     */
    suspend fun setGroupIgnoreWait(ignore: Boolean) = syncPlayController.setIgnoreWait(ignore)

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
