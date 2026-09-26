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
 * transport members land on `manager.syncPlayController`, whose `safe()` is
 * the ONE fire-and-forget wrapper home — this adapter never wraps); the event
 * stream maps the manager's jvmShared `SyncPlayEvent` sealed class onto the
 * feature-local [SyncPlaySessionEvent] mirror field-for-field (android/desktop
 * behavior unchanged).
 */
internal class JvmSyncPlaySession(
    private val manager: SyncPlayManager,
) : SyncPlaySession {
    override val activeGroupId: String? get() = manager.activeGroupId
    override val lastReconnectMs: Long get() = manager.lastReconnectMs
    override val events: Flow<SyncPlaySessionEvent> =
        manager.events.map(::toSessionEvent)

    private val controller get() = manager.syncPlayController

    override suspend fun joinGroup(groupId: String): Result<Unit> = manager.joinGroup(groupId)
    override suspend fun leaveGroup(): Result<Unit> = manager.leaveGroup()

    override suspend fun pause() = controller.pause()
    override suspend fun unpause() = controller.unpause()
    override suspend fun seek(positionTicks: Long) = controller.seek(positionTicks)
    override suspend fun stop() = controller.stop()
    override suspend fun setRepeatMode(mode: SyncPlayRepeatMode) = controller.setRepeatMode(mode)
    override suspend fun setShuffleMode(mode: SyncPlayShuffleMode) = controller.setShuffleMode(mode)
    override suspend fun setIgnoreWait(ignore: Boolean) = controller.setIgnoreWait(ignore)
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
