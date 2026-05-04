package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayManager @Inject constructor(
    private val apiClient: JellyfinApiClient,
) {
    private var activeGroupId: String? = null
    private var isGroupActive = false

    val currentGroup: SyncPlayGroup? get() = null

    val isInSyncPlaySession: Boolean get() = isGroupActive && activeGroupId != null

    suspend fun getAvailableGroups(): List<SyncPlayGroup> {
        return try {
            apiClient.getSyncPlayGroups().getOrElse { emptyList() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun joinGroup(groupId: String): Result<Unit> {
        return try {
            apiClient.joinSyncPlayGroup(groupId)
            activeGroupId = groupId
            isGroupActive = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(): Result<Unit> {
        return try {
            apiClient.leaveSyncPlayGroup()
            activeGroupId = null
            isGroupActive = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroup(groupName: String): Result<Unit> {
        return try {
            apiClient.createSyncPlayGroup(groupName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportReady(): Result<Unit> {
        return try {
            apiClient.syncPlayReady()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportBuffering(): Result<Unit> {
        return try {
            apiClient.syncPlayBuffering()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPause(): Result<Unit> {
        return try {
            apiClient.syncPlayPause()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendUnpause(): Result<Unit> {
        return try {
            apiClient.syncPlayUnpause()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSeek(positionTicks: Long): Result<Unit> {
        return try {
            apiClient.syncPlaySeek(positionTicks)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun reset() {
        activeGroupId = null
        isGroupActive = false
    }
}
