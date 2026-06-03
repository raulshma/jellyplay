package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.StaleMediaItem
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import kotlinx.coroutines.flow.Flow

interface AdminStatisticsRepository {

    fun getPlaybackReportingStatus(): Flow<PlaybackReportingStatus>

    suspend fun refreshPlaybackReportingStatus()

    suspend fun getAllUsersWithStatistics(): Result<List<UserStatistics>>

    suspend fun getUserDetailStatistics(
        userId: String,
        page: Int = 0,
        pageSize: Int = 50,
    ): Result<UserDetailPage>

    suspend fun detectStaleMedia(config: MediaCleanupConfig): Result<String>

    suspend fun detectWatchedMedia(config: MediaCleanupConfig): Result<String>

    fun getScanProgress(scanId: String): Flow<ScanProgress>

    suspend fun getScanResultJson(scanId: String): String?

    suspend fun removeMediaItems(
        itemIds: List<String>,
        itemNameMap: Map<String, String>,
        actionType: CleanupActionType,
        config: MediaCleanupConfig,
    ): Result<AuditLogEntry>

    fun getAuditHistory(actionType: CleanupActionType? = null): Flow<List<AuditLogEntry>>
}
