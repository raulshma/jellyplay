package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import kotlinx.coroutines.flow.Flow

/**
 * Common seam over core:data's jvmShared `SyncPlayManager` —
 * the group-session handle the screen's ViewModel reads (join/leave, the
 * active group id, the reconnect timestamp, and the WebSocket event stream).
 * The manager's constructor closure is the JVM SyncPlay stack (OkHttp
 * `JellyfinApiClient` + `JellyfinWebSocketClient` + `TimeSyncManager` +
 * `java.util.concurrent` mirrors), so commonMain cannot name the class.
 * Promoted-interface precedent (DownloadIntake/DownloadQueue): the interface
 * carries exactly the host-facing surface, the jvmShared actual delegates to the process-wide
 * `SyncPlayManager` single (same DI graph — android/desktop behavior
 * unchanged).
 */
interface SyncPlaySession {

    /** The currently-joined group id, or null when not in a group. */
    val activeGroupId: String?

    /**
     * Wall-clock millis of the last WebSocket reconnect, 0 before the first —
     * the reconnect-grace input behind the empty-[SyncPlaySessionEvent.GroupUpdate]
     * branch (see the ViewModel's [com.raulshma.jellyplay.feature.syncplay.SyncPlayViewModel]).
     */
    val lastReconnectMs: Long

    /**
     * Server session events, in order. The feature-local mirror of
     * core:data's jvmShared `SyncPlayEvent` — same variants, same payloads
     * (the classifiers carry core:model types, which are common), so the
     * JVM adapter maps 1:1.
     */
    val events: Flow<SyncPlaySessionEvent>

    /** Joins [groupId] (server-side join + WebSocket session attach). */
    suspend fun joinGroup(groupId: String): Result<Unit>

    /** Leaves the current group (no-op failure when not in one — see the manager). */
    suspend fun leaveGroup(): Result<Unit>
}

/**
 * The feature-local mirror of core:data's jvmShared `SyncPlayEvent` sealed
 * class — variants and payloads kept field-identical so [JvmSyncPlaySession]'s
 * mapping is mechanical. [state] on [StateUpdate] is the raw server
 * GroupStateType ("Playing"/"Waiting"/"Paused"/"Idle"); Waiting is the
 * transient everyone-parked-while-a-client-catches-up state — the only one
 * that should surface as "syncing"; a Paused group is still in sync.
 */
sealed interface SyncPlaySessionEvent {
    data class PlaybackCommand(val cmd: SyncPlayPlaybackCommand) : SyncPlaySessionEvent
    data class PlayQueueUpdate(val data: SyncPlayQueueUpdateData) : SyncPlaySessionEvent
    data class GroupUpdate(val groupName: String, val participantCount: Int) : SyncPlaySessionEvent
    data class StateUpdate(val isPlaying: Boolean, val state: String, val reason: String) : SyncPlaySessionEvent
    data class WaitForGroup(val userName: String?) : SyncPlaySessionEvent
    data class Notification(val message: String) : SyncPlaySessionEvent
    data object GroupLeft : SyncPlaySessionEvent
}
