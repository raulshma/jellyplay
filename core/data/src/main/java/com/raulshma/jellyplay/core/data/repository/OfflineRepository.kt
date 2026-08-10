package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import kotlinx.coroutines.flow.Flow

interface OfflineRepository {
    fun getOfflineLibrary(): Flow<List<OfflineMediaItem>>
    fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaItem>>
    fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaItem>>

    /**
     * Returns the single offline item for [id] as a reactive flow, with the
     * matching download row joined (status/bytes/size/path). Used by the
     * offline detail screen so it reflects download progress live.
     */
    fun getOfflineDetail(id: String): Flow<OfflineMediaItem?>

    /**
     * Returns the direct children of [parentId] (e.g. album tracks), with the
     * matching download rows joined. Ordered by `indexNumber` ascending.
     */
    fun getChildren(parentId: String): Flow<List<OfflineMediaItem>>

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
     * Applies a played/unplayed flip to [itemId] and every offline row in its
     * hierarchy (its children, seasons, and episodes). Mirrors the Jellyfin
     * `markPlayedItem` / `markUnplayedItem` endpoint cascade into the local
     * offline store so the offline screens stay consistent with server state
     * when the user marks a season or series played/unplayed online.
     *
     * No-op if the item (and its hierarchy) isn't in the offline store — the
     * underlying UPDATE matches zero rows.
     *
     * @param itemId the item id (episode, season, or series).
     * @param isPlayed when true, marks the hierarchy as fully watched; when
     *   false, resets position/percentage and clears lastPlayedDate.
     */
    suspend fun applyPlayedState(itemId: String, isPlayed: Boolean)

    /**
     * Applies a favorite / unfavorite flip to [itemId] in the local offline
     * store. Favorite is per-item (no hierarchy cascade), mirroring the
     * Jellyfin favorite endpoints' single-item scope. No-op if the item isn't
     * in the offline store — the underlying UPDATE matches zero rows.
     *
     * @param itemId the item id.
     * @param isFavorite when true, marks the item as a favorite; when false,
     *   clears the favorite flag.
     */
    suspend fun applyFavoriteState(itemId: String, isFavorite: Boolean)

    /**
     * Search the on-device downloaded library by free-text query. Matches
     * against [OfflineMediaItem.name], [OfflineMediaItem.seriesName] and
     * [OfflineMediaItem.seasonName] (case-insensitive substring). Returns
     * up to [limit] results ordered with prefix matches first then
     * alphabetically. Returns an empty list for blank or too-short queries.
     */
    suspend fun searchOffline(query: String, limit: Int = 20): List<OfflineMediaItem>

    /**
     * Reactive freshness state for an offline item, projected from the persisted
     * sync-baseline columns. Drives the offline-detail "update available" badge
     * with zero network — the flags are written by [OfflineSyncManager]'s check
     * / resync paths and re-emit here as they flip.
     */
    fun getOfflineSyncState(id: String): Flow<OfflineSyncState?>

    /** Reactive count of items flagged for a metadata/image resync. */
    fun getUpdatesCount(): Flow<Int>

    /** Reactive list of items flagged for resync, for the downloads resync sheet. */
    fun getItemsWithUpdates(): Flow<List<OfflineSyncUpdate>>

    /** Snapshot ids of all offline items (top-level + episodes), for batch checks. */
    suspend fun getDownloadedItemIds(): List<String>
}
