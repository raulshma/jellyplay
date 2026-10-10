package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager

/**
 * The SyncPlay family's impl: stateless SyncPlay reads/writes over the
 * [SyncPlayApiClient] wire — the group-list/detail reads and the awaited
 * set-new-queue push — plus the deepened [SyncPlayRepository.createSyncPlayGroup],
 * which delegates to [SyncPlayManager.createGroup] (the one owner of the
 * "create then join MY group" choreography — bounded discovery/settle
 * windows, duplicate-name disambiguation) and maps the joined group to the
 * [SyncPlayGroupInfo] identifying slice (id + name) callers page/queue by.
 *
 * No cache and no epoch involvement: every member surfaces the wire or
 * manager [Result] directly — nothing here reads or evicts the media
 * repository's cache clusters, and a failed call carries no stale-read
 * fallback.
 */
class SyncPlayRepositoryImpl(
    private val syncPlayApiClient: SyncPlayApiClient,
    private val syncPlayManager: SyncPlayManager,
) : SyncPlayRepository {

    override suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>> =
        syncPlayApiClient.getSyncPlayGroups()

    override suspend fun createSyncPlayGroup(groupName: String): Result<SyncPlayGroupInfo> =
        syncPlayManager.createGroup(groupName).map { group ->
            SyncPlayGroupInfo(groupId = group.groupId, groupName = group.groupName)
        }

    override suspend fun getSyncPlayInfo(groupId: String?): Result<SyncPlayGroupInfo> =
        syncPlayApiClient.getSyncPlayInfo(groupId)

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> =
        syncPlayApiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)
}
