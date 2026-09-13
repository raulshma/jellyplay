package com.raulshma.jellyplay.feature.admin.watchedremoval

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.mediacleanup.MediaCleanupScanState
import com.raulshma.jellyplay.feature.admin.mediacleanup.MediaCleanupScanStateHolder
import com.raulshma.jellyplay.feature.admin.mediacleanup.MediaSortOption
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter over the shared [MediaCleanupScanStateHolder] chassis: the watched
 * flavor supplies its detect call (`detectWatchedMedia`), its dry-run config
 * default (Movie/Episode, keep-favorites, 0-day threshold, no partial
 * watches) and bakes [CleanupActionType.WATCHED_REMOVAL] into the delete/audit
 * seams. Every command is a one-line delegate; the scan lifecycle, selection
 * machine, confirm/delete choreography and audit fold live in the chassis.
 */
class WatchedMediaCleanupViewModel(
    repository: AdminStatisticsRepository,
    authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val holder = MediaCleanupScanStateHolder(
        scope = scope,
        initialConfig = MediaCleanupConfig(
            includeItemTypes = setOf("Movie", "Episode"),
            keepFavorites = true,
            minDaysSinceWatched = 0,
            includePartiallyWatched = false,
            dryRun = true,
        ),
        detectMedia = repository::detectWatchedMedia,
        scanProgress = repository::getScanProgress,
        scanResultJson = repository::getScanResultJson,
        removeMediaItems = { itemIds, itemNameMap, config ->
            repository.removeMediaItems(
                itemIds = itemIds,
                itemNameMap = itemNameMap,
                actionType = CleanupActionType.WATCHED_REMOVAL,
                config = config,
            )
        },
        currentUser = authRepository.currentUser,
        auditHistory = repository.getAuditHistory(CleanupActionType.WATCHED_REMOVAL),
    )

    val state: StateFlow<MediaCleanupScanState> = holder.state

    fun updateConfig(config: MediaCleanupConfig) = holder.updateConfig(config)

    fun updateSort(option: MediaSortOption) = holder.updateSort(option)

    fun startScan() = holder.startScan()

    fun toggleItemSelection(itemId: String) = holder.toggleItemSelection(itemId)

    fun selectAll() = holder.selectAll()

    fun showDeleteConfirmation() = holder.showDeleteConfirmation()

    fun dismissDeleteConfirmation() = holder.dismissDeleteConfirmation()

    fun deleteSelected() = holder.deleteSelected()
}
