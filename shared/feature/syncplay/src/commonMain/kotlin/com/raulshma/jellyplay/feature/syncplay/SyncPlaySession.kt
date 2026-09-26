package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import kotlinx.coroutines.flow.Flow

/**
 * Common seam over core:data's jvmShared `SyncPlayManager` —
 * the group-session handle the screen's ViewModel reads (join/leave, the
 * active group id, the reconnect timestamp, the WebSocket event stream) and
 * the transport commands it fires (pause/unpause/seek/stop/repeat/shuffle/
 * ignore-wait). The manager's constructor closure is the JVM SyncPlay stack
 * (OkHttp `JellyfinApiClient` + `JellyfinWebSocketClient` + `TimeSyncManager`
 * + `java.util.concurrent` mirrors), so commonMain cannot name the class.
 * Promoted-interface precedent (DownloadIntake/DownloadQueue): the interface
 * carries exactly the host-facing surface, the jvmShared actual delegates to
 * the process-wide `SyncPlayManager` single (same DI graph — android/desktop
 * behavior unchanged).
 *
 * The transport members are the second wire census's landing spot: the
 * ignored-Result `SyncPlayRepository.syncPlay*` twins were retired, and these
 * commands now converge on `SyncPlayController.safe()` — the ONE
 * fire-and-forget wrapper home — via the jvmShared adapter's plain delegation
 * (the adapter never wraps; errors are logged inside the controller, so a
 * failed command cannot crash the VM's launch).
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

    // ── fire-and-forget transport commands (delegate to SyncPlayController) ──

    suspend fun pause()

    suspend fun unpause()

    suspend fun seek(positionTicks: Long)

    suspend fun stop()

    suspend fun setRepeatMode(mode: SyncPlayRepeatMode)

    suspend fun setShuffleMode(mode: SyncPlayShuffleMode)

    suspend fun setIgnoreWait(ignore: Boolean)
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
