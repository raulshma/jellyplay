package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode

/**
 * SyncPlay group session state surfaced to the player UI.
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
)
