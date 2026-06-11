package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode

interface SyncPlayRepository {

    suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>>

    suspend fun joinSyncPlayGroup(groupId: String): Result<Unit>

    suspend fun leaveSyncPlayGroup(): Result<Unit>

    suspend fun createSyncPlayGroup(groupName: String): Result<Unit>

    suspend fun getSyncPlayInfo(groupId: String? = null): Result<SyncPlayGroupInfo>

    suspend fun syncPlayReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
    ): Result<Unit>

    suspend fun syncPlayPause(): Result<Unit>

    suspend fun syncPlayUnpause(): Result<Unit>

    suspend fun syncPlaySeek(positionTicks: Long): Result<Unit>

    suspend fun syncPlayStop(): Result<Unit>

    suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit>

    suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit>

    suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ): Result<Unit>

    suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit>

    suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit>

    suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit>
}
