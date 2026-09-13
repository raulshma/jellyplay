package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The JVM adapter over core:data's `SyncPlayManager` single — join/leave and
 * the id/reconnect reads delegate verbatim; the event stream maps the
 * manager's jvmShared `SyncPlayEvent` sealed class onto the feature-local
 * [SyncPlaySessionEvent] mirror field-for-field (android/desktop behavior
 * unchanged).
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
