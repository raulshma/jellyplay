package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode

/**
 * SyncPlay group session state surfaced to the player UI.
 *
 * The group-display transitions live HERE, not at the former six call-site
 * copies in `SyncPlayBridge` (start / joinGroup / reattachSession populate;
 * leaveGroup / empty-GroupUpdate / GroupLeft clear): adding a group-display
 * field means editing [from] and [cleared] only.
 */
@Immutable
data class SyncPlayUiState(
    val isInSyncPlaySession: Boolean = false,
    val syncPlayGroupName: String? = null,
    val syncPlayParticipantCount: Int = 0,
    val isSyncPlaySynced: Boolean = false,
    val isSyncPlaySyncing: Boolean = false,
    val syncPlayRepeatMode: SyncPlayRepeatMode = SyncPlayRepeatMode.REPEAT_NONE,
    val syncPlayShuffleMode: SyncPlayShuffleMode = SyncPlayShuffleMode.SORTED,
) {
    /**
     * Folds the group-display fields from the manager's current [SyncPlayGroup]
     * into this state — the single populate the bridge's start / joinGroup /
     * reattachSession paths all run. INSTANCE method (not a companion
     * factory) so non-group fields (`isSyncPlaySynced`/`isSyncPlaySyncing`)
     * carry over exactly as the former per-site `it.copy(...)` blocks did.
     *
     * [groupNameFallback] covers joinGroup only: before the server confirms
     * the group, the UI shows the requested group id as the name.
     */
    fun from(group: SyncPlayGroup?, groupNameFallback: String? = null): SyncPlayUiState = copy(
        isInSyncPlaySession = true,
        syncPlayGroupName = group?.groupName ?: groupNameFallback,
        syncPlayParticipantCount = group?.participantCount ?: 0,
        syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
        syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
    )

    /**
     * The single "session display state is gone" fold — the former
     * leaveGroup / empty-GroupUpdate / GroupLeft copies. Repeat/shuffle are
     * deliberately NOT reset here (they were not in those copies either);
     * the per-item reset restores the full default state.
     */
    fun cleared(): SyncPlayUiState = copy(
        isInSyncPlaySession = false,
        syncPlayGroupName = null,
        syncPlayParticipantCount = 0,
        isSyncPlaySynced = false,
        isSyncPlaySyncing = false,
    )
}
