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

    /**
     * Search the on-device downloaded library by free-text query. Matches
     * against [OfflineMediaItem.name], [OfflineMediaItem.seriesName] and
     * [OfflineMediaItem.seasonName] (case-insensitive substring). Returns
     * up to [limit] results ordered with prefix matches first then
     * alphabetically. Returns an empty list for blank or too-short queries.
     */
    suspend fun searchOffline(query: String, limit: Int = 20): List<OfflineMediaItem>
}
