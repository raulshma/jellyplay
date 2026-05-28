package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlinx.coroutines.flow.Flow

interface OfflineRepository {
    fun getOfflineLibrary(): Flow<List<OfflineMediaItem>>
    fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaItem>>
    fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaItem>>
    suspend fun getOfflineItem(id: String): OfflineMediaItem?
    fun getOfflineItemCount(): Flow<Int>
    suspend fun deleteOfflineItem(id: String)
    suspend fun deleteOfflineSeries(seriesId: String)
    suspend fun deleteOfflineSeason(seasonId: String)
    suspend fun cleanupOrphans()
}
