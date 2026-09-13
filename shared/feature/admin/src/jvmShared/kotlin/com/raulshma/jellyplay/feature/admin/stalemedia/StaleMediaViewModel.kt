package com.raulshma.jellyplay.feature.admin.stalemedia

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
 * Adapter over the shared [MediaCleanupScanStateHolder] chassis: the stale
 * flavor supplies its detect call (`detectStaleMedia`), its dry-run config
 * default (90 days, never-played included, Movie/Series/Episode) and bakes
 * [CleanupActionType.STALE_REMOVAL] into the delete/audit seams. Every
 * command is a one-line delegate; the scan lifecycle, selection machine,
 * confirm/delete choreography and audit fold live in the chassis.
 *
 * Declared delta vs the former ~200-line body: the dead `selectedTabIndex`
 * state + `selectTab` command were dropped (the screen drove its tab row from
 * local `remember` state — see the holder KDoc).
 */
class StaleMediaViewModel(
    repository: AdminStatisticsRepository,
    authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val holder = MediaCleanupScanStateHolder(
        scope = scope,
        initialConfig = MediaCleanupConfig(
            daysThreshold = 90,
            includeNeverPlayed = true,
            includeItemTypes = setOf("Movie", "Series", "Episode"),
            dryRun = true,
        ),
        detectMedia = repository::detectStaleMedia,
        scanProgress = repository::getScanProgress,
        scanResultJson = repository::getScanResultJson,
        removeMediaItems = { itemIds, itemNameMap, config ->
            repository.removeMediaItems(
                itemIds = itemIds,
                itemNameMap = itemNameMap,
                actionType = CleanupActionType.STALE_REMOVAL,
                config = config,
            )
        },
        currentUser = authRepository.currentUser,
        auditHistory = repository.getAuditHistory(CleanupActionType.STALE_REMOVAL),
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
