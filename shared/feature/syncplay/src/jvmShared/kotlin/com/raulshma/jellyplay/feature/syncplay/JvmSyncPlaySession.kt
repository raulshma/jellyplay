package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import kotlinx.coroutines.flow.Flow

/**
 * The JVM adapter over core:data's `SyncPlayManager` single — every member
 * delegates verbatim (the transport members ride the manager's
 * `SyncPlayController` forwarders, whose `safe()` is the ONE fire-and-forget
 * wrapper home — this adapter never wraps; errors are logged inside the
 * controller, so a failed command cannot crash the VM's launch). The event
 * stream forwards the manager's promoted-commonMain `SyncPlayEvent` flow
 * as-is (android/desktop behavior unchanged).
 */
internal class JvmSyncPlaySession(
    private val manager: SyncPlayManager,
) : SyncPlaySession {
    override val activeGroupId: String? get() = manager.activeGroupId
    override val lastReconnectMs: Long get() = manager.lastReconnectMs
    override val events: Flow<SyncPlayEvent> = manager.events

    override suspend fun joinGroup(groupId: String): Result<Unit> = manager.joinGroup(groupId)
    override suspend fun leaveGroup(): Result<Unit> = manager.leaveGroup()

    override suspend fun pause() = manager.pauseGroup()
    override suspend fun unpause() = manager.unpauseGroup()
    override suspend fun seek(positionTicks: Long) = manager.seekGroup(positionTicks)
    override suspend fun stop() = manager.stopGroup()
    override suspend fun setRepeatMode(mode: SyncPlayRepeatMode) = manager.setGroupRepeatMode(mode)
    override suspend fun setShuffleMode(mode: SyncPlayShuffleMode) = manager.setGroupShuffleMode(mode)
    override suspend fun setIgnoreWait(ignore: Boolean) = manager.setGroupIgnoreWait(ignore)
}
