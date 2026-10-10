package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData

sealed class SyncPlayEvent {
    data class PlaybackCommand(val cmd: SyncPlayPlaybackCommand) : SyncPlayEvent()
    data class PlayQueueUpdate(val data: SyncPlayQueueUpdateData) : SyncPlayEvent()
    data class GroupUpdate(val groupName: String, val participantCount: Int) : SyncPlayEvent()

    /**
     * @param state raw server GroupStateType: "Playing", "Waiting", "Paused",
     *   "Idle". Waiting is the transient everyone-parked-while-a-client-catches-up
     *   state — the only one that should surface as "syncing"; a Paused group
     *   is still in sync.
     */
    data class StateUpdate(val isPlaying: Boolean, val state: String, val reason: String) : SyncPlayEvent()
    data class WaitForGroup(val userName: String?) : SyncPlayEvent()
    data class Notification(val message: String) : SyncPlayEvent()
    data object GroupLeft : SyncPlayEvent()
}
