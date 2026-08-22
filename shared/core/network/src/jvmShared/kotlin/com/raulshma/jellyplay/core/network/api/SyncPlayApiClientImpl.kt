package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayParticipant
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import org.jellyfin.sdk.model.api.BufferRequestDto
import org.jellyfin.sdk.model.api.GroupQueueMode
import org.jellyfin.sdk.model.api.GroupRepeatMode
import org.jellyfin.sdk.model.api.GroupShuffleMode
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.IgnoreWaitRequestDto
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.MovePlaylistItemRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.NextItemRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.PreviousItemRequestDto
import org.jellyfin.sdk.model.api.QueueRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.RemoveFromPlaylistRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import org.jellyfin.sdk.model.api.SetPlaylistItemRequestDto
import org.jellyfin.sdk.model.api.SetRepeatModeRequestDto
import org.jellyfin.sdk.model.api.SetShuffleModeRequestDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : SyncPlayApiClient {

    override suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>> = engine.apiResultWithRetry {
        val response = engine.requireApi().syncPlayApi.syncPlayGetGroups().content
        response.map { groupInfo ->
            SyncPlayGroup(
                groupId = groupInfo.groupId.toString(),
                groupName = groupInfo.groupName ?: "",
                participantCount = groupInfo.participants?.size ?: 0,
                participants = groupInfo.participants ?: emptyList(),
                isPlaying = groupInfo.state == GroupStateType.PLAYING,
            )
        }
    }

    override suspend fun joinSyncPlayGroup(groupId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayJoinGroup(
            JoinGroupRequestDto(
                groupId = groupId.toUUID(),
            )
        )
    }

    override suspend fun leaveSyncPlayGroup(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayLeaveGroup()
    }

    override suspend fun createSyncPlayGroup(groupName: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayCreateGroup(
            NewGroupRequestDto(
                groupName = groupName,
            )
        )
    }

    override suspend fun syncPlayReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
        whenMs: Long?,
    ): Result<Unit> = engine.apiResultWithRetry {
        val whenDate = whenMs?.let {
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneOffset.UTC)
        } ?: java.time.LocalDateTime.now(java.time.Clock.systemUTC())

        engine.requireApi().syncPlayApi.syncPlayReady(
            ReadyRequestDto(
                `when` = whenDate,
                positionTicks = positionTicks,
                isPlaying = isPlaying,
                playlistItemId = (playlistItemId ?: "00000000-0000-0000-0000-000000000000").toUUID(),
            )
        )
    }

    override suspend fun syncPlayBuffering(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
        whenMs: Long?,
    ): Result<Unit> = engine.apiResultWithRetry {
        val whenDate = whenMs?.let {
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneOffset.UTC)
        } ?: java.time.LocalDateTime.now(java.time.Clock.systemUTC())

        engine.requireApi().syncPlayApi.syncPlayBuffering(
            BufferRequestDto(
                `when` = whenDate,
                positionTicks = positionTicks,
                isPlaying = isPlaying,
                playlistItemId = (playlistItemId ?: "00000000-0000-0000-0000-000000000000").toUUID(),
            )
        )
    }

    override suspend fun syncPlayPause(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayPause()
    }

    override suspend fun syncPlayUnpause(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayUnpause()
    }

    override suspend fun syncPlaySeek(positionTicks: Long): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlaySeek(
            SeekRequestDto(
                positionTicks = positionTicks,
            )
        )
    }

    override suspend fun getSyncPlayInfo(groupId: String?): Result<SyncPlayGroupInfo> = engine.apiResultWithRetry {
        val activeId = groupId ?: throw IllegalArgumentException("groupId is required for getSyncPlayInfo")
        val groups = engine.requireApi().syncPlayApi.syncPlayGetGroups().content
        val groupInfo = groups.find { it.groupId.toString() == activeId }
            ?: throw IllegalStateException("SyncPlay group $activeId not found")
        SyncPlayGroupInfo(
            groupId = groupInfo.groupId.toString(),
            groupName = groupInfo.groupName ?: "",
            participants = (groupInfo.participants ?: emptyList()).map { name ->
                SyncPlayParticipant(
                    userId = name,
                    userName = name,
                    isConnected = true,
                )
            },
            isPlaying = groupInfo.state == GroupStateType.PLAYING,
        )
    }

    override suspend fun syncPlayStop(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayStop()
    }

    override suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayNextItem(
            NextItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
            )
        )
    }

    override suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayPreviousItem(
            PreviousItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
            )
        )
    }

    override suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit> = engine.apiResultWithRetry {
        val sdkMode = when (mode) {
            SyncPlayRepeatMode.REPEAT_NONE -> GroupRepeatMode.REPEAT_NONE
            SyncPlayRepeatMode.REPEAT_ALL -> GroupRepeatMode.REPEAT_ALL
            SyncPlayRepeatMode.REPEAT_ONE -> GroupRepeatMode.REPEAT_ONE
        }
        engine.requireApi().syncPlayApi.syncPlaySetRepeatMode(
            SetRepeatModeRequestDto(mode = sdkMode)
        )
    }

    override suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit> = engine.apiResultWithRetry {
        val sdkMode = when (mode) {
            SyncPlayShuffleMode.SORTED -> GroupShuffleMode.SORTED
            SyncPlayShuffleMode.SHUFFLE -> GroupShuffleMode.SHUFFLE
        }
        engine.requireApi().syncPlayApi.syncPlaySetShuffleMode(
            SetShuffleModeRequestDto(mode = sdkMode)
        )
    }

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlaySetNewQueue(
            PlayRequestDto(
                playingQueue = itemIds.map { it.toUUID() },
                playingItemPosition = itemIds.indexOf(playingItemId).takeIf { it >= 0 } ?: 0,
                startPositionTicks = startPositionTicks,
            )
        )
    }

    override suspend fun syncPlayQueue(
        itemIds: List<String>,
        mode: String,
    ): Result<Unit> = engine.apiResultWithRetry {
        val sdkMode = GroupQueueMode.fromNameOrNull(mode)
            ?: GroupQueueMode.QUEUE
        engine.requireApi().syncPlayApi.syncPlayQueue(
            QueueRequestDto(
                itemIds = itemIds.map { it.toUUID() },
                mode = sdkMode,
            )
        )
    }

    override suspend fun syncPlaySetPlaylistItem(playlistItemId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlaySetPlaylistItem(
            SetPlaylistItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
            )
        )
    }

    override suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlaySetIgnoreWait(
            IgnoreWaitRequestDto(ignoreWait = ignore)
        )
    }

    override suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayRemoveFromPlaylist(
            RemoveFromPlaylistRequestDto(
                playlistItemIds = listOf(playlistItemId.toUUID()),
                clearPlayingItem = true,
                clearPlaylist = false,
            )
        )
    }

    override suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayMovePlaylistItem(
            MovePlaylistItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
                newIndex = newIndex,
            )
        )
    }

    override suspend fun syncPlayPing(pingMs: Long): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().syncPlayApi.syncPlayPing(
            PingRequestDto(ping = pingMs)
        )
    }
}
