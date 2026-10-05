package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
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
     * the reconnect-grace input behind the empty-[SyncPlayEvent.GroupUpdate]
     * branch (see the ViewModel's [com.raulshma.jellyplay.feature.syncplay.SyncPlayViewModel]).
     */
    val lastReconnectMs: Long

    /**
     * Server session events, in order — core:data's commonMain
     * [SyncPlayEvent] vocabulary, forwarded verbatim by the JVM adapter.
     */
    val events: Flow<SyncPlayEvent>

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
