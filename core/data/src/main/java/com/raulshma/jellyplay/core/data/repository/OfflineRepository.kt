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
     * Records playback progress for a downloaded item so it can be shown on the
     * downloads UI and resumed while offline. Independent of the
     * server sync path (which continues when online). No-op if the item isn't
     * in the offline store.
     *
     * @param itemId the downloaded media item id.
     * @param positionTicks the current playback position in Jellyfin ticks, or
     *   null to leave the existing position untouched.
     * @param percentage 0–100 played fraction. When `>=` the watched threshold
     *   the item is marked played.
     * @param isPlayed when true, marks the item as fully watched.
     */
    suspend fun updatePlaybackProgress(
        itemId: String,
        positionTicks: Long?,
        percentage: Double,
        isPlayed: Boolean,
    )

    /**
     * Search the on-device downloaded library by free-text query. Matches
     * against [OfflineMediaItem.name], [OfflineMediaItem.seriesName] and
     * [OfflineMediaItem.seasonName] (case-insensitive substring). Returns
     * up to [limit] results ordered with prefix matches first then
     * alphabetically. Returns an empty list for blank or too-short queries.
     */
    suspend fun searchOffline(query: String, limit: Int = 20): List<OfflineMediaItem>
}
