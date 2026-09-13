package com.raulshma.jellyplay.feature.syncplay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The wasmJs actual of the [SyncPlaySession] seam: an honest unsupported
 * session. The browser has no SyncPlay transport — the manager's WebSocket
 * client is JVM-only — so join/leave fail with an explicit cause (never a
 * fabricated success), [activeGroupId] stays null, [lastReconnectMs] stays 0
 * and [events] never emits. The screen surfaces the join/leave failure
 * through its existing error channel; no group state is ever faked.
 */
internal object WasmSyncPlaySession : SyncPlaySession {
    override val activeGroupId: String? = null
    override val lastReconnectMs: Long = 0L
    override val events: Flow<SyncPlaySessionEvent> = MutableSharedFlow()

    override suspend fun joinGroup(groupId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("SyncPlay requires the Android or desktop app"))

    override suspend fun leaveGroup(): Result<Unit> =
        Result.failure(UnsupportedOperationException("SyncPlay requires the Android or desktop app"))
}
