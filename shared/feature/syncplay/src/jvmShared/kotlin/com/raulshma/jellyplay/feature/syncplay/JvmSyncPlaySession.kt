package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The JVM adapter over core:data's `SyncPlayManager` single — join/leave, the
 * id/reconnect reads and the transport commands delegate verbatim (the
 * transport members ride the manager's `SyncPlayController` forwarders, whose
 * `safe()` is the ONE fire-and-forget wrapper home — this adapter never
 * wraps); the event stream maps the manager's jvmShared `SyncPlayEvent`
 * sealed class onto the feature-local [SyncPlaySessionEvent] mirror
 * field-for-field (android/desktop behavior unchanged).
 */
internal class JvmSyncPlaySession(
    private val manager: SyncPlayManager,
) : SyncPlaySession {
    override val activeGroupId: String? get() = manager.activeGroupId
    override val lastReconnectMs: Long get() = manager.lastReconnectMs
    override val events: Flow<SyncPlaySessionEvent> =
        manager.events.map(::toSessionEvent)

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

/** Field-identical 1:1 map — see the [SyncPlaySessionEvent] mirror KDoc. */
private fun toSessionEvent(event: SyncPlayEvent): SyncPlaySessionEvent = when (event) {
    is SyncPlayEvent.PlaybackCommand -> SyncPlaySessionEvent.PlaybackCommand(event.cmd)
    is SyncPlayEvent.PlayQueueUpdate -> SyncPlaySessionEvent.PlayQueueUpdate(event.data)
    is SyncPlayEvent.GroupUpdate -> SyncPlaySessionEvent.GroupUpdate(event.groupName, event.participantCount)
    is SyncPlayEvent.StateUpdate -> SyncPlaySessionEvent.StateUpdate(event.isPlaying, event.state, event.reason)
    is SyncPlayEvent.WaitForGroup -> SyncPlaySessionEvent.WaitForGroup(event.userName)
    is SyncPlayEvent.Notification -> SyncPlaySessionEvent.Notification(event.message)
    is SyncPlayEvent.GroupLeft -> SyncPlaySessionEvent.GroupLeft
}
