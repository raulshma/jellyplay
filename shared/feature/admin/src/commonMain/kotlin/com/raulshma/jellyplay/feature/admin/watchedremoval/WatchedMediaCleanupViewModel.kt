package com.raulshma.jellyplay.feature.admin.watchedremoval

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.sortSizeBytes
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.stalemedia.MediaSortOption
import kotlinx.serialization.json.Json

@Immutable
data class WatchedMediaState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val config: MediaCleanupConfig = MediaCleanupConfig(
        includeItemTypes = setOf("Movie", "Episode"),
        keepFavorites = true,
        minDaysSinceWatched = 0,
        includePartiallyWatched = false,
        dryRun = true,
    ),
    val scanId: String? = null,
    val scanProgress: ScanProgress = ScanProgress(),
    val rawScanResults: List<MediaItemStub> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val canDeleteContent: Boolean = true,
    val auditEntries: List<AuditLogEntry> = emptyList(),
    val sortOption: MediaSortOption = MediaSortOption.DEFAULT,
) {
    val scanResults: List<MediaItemStub>
        get() = when (sortOption) {
            MediaSortOption.DEFAULT -> rawScanResults
            MediaSortOption.NAME_ASC -> rawScanResults.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            MediaSortOption.NAME_DESC -> rawScanResults.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            MediaSortOption.SIZE_DESC -> rawScanResults.sortedByDescending { it.sortSizeBytes }
            MediaSortOption.SIZE_ASC -> rawScanResults.sortedBy { it.sortSizeBytes }
            MediaSortOption.TYPE -> rawScanResults.sortedBy { it.type }
            MediaSortOption.DATE -> rawScanResults.sortedBy { it.dateText }
        }
}

class WatchedMediaCleanupViewModel(
    private val repository: AdminStatisticsRepository,
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = stateFlow(WatchedMediaState())
    val state = _state.flow

    init {
        observeAuditHistory()
        observePermissions()
    }

    private fun observePermissions() {
        launch {
            authRepository.currentUser.collect { user ->
                _state.update { it.copy(canDeleteContent = user?.canDeleteContent ?: false) }
            }
        }
    }

    private fun observeAuditHistory() {
        launch {
            repository.getAuditHistory(CleanupActionType.WATCHED_REMOVAL).collect { entries ->
                _state.update { it.copy(auditEntries = entries) }
            }
        }
    }

    fun updateConfig(config: MediaCleanupConfig) {
        _state.update { it.copy(config = config) }
    }

    fun updateSort(option: MediaSortOption) {
        _state.update { it.copy(sortOption = option) }
    }

    fun startScan() {
        launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    rawScanResults = emptyList(),
                    selectedItems = emptySet(),
                )
            }
            repository.detectWatchedMedia(_state.value.config)
                .onSuccess { scanId ->
                    _state.update { it.copy(scanId = scanId, isLoading = false) }
                    observeScanProgress(scanId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun observeScanProgress(scanId: String) {
        launch {
            repository.getScanProgress(scanId).collect { progress ->
                _state.update { it.copy(scanProgress = progress) }
                if (progress.phase == ScanPhase.COMPLETED) {
                    val results = loadResults(scanId)
                    _state.update { it.copy(rawScanResults = results) }
                }
            }
        }
    }

    private suspend fun loadResults(scanId: String): List<MediaItemStub> {
        return try {
            val resultJson = repository.getScanResultJson(scanId)
            if (resultJson.isNullOrBlank()) emptyList()
            else json.decodeFromString<List<MediaItemStub>>(resultJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toggleItemSelection(itemId: String) {
        val current = _state.value.selectedItems
        _state.update {
            it.copy(
                selectedItems = if (current.contains(itemId)) current - itemId else current + itemId,
            )
        }
    }

    fun selectAll() {
        val allIds = _state.value.scanResults.map { it.itemId }.toSet()
        _state.update {
            it.copy(
                selectedItems = if (it.selectedItems == allIds) emptySet() else allIds,
            )
        }
    }

    fun showDeleteConfirmation() {
        _state.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _state.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteSelected() {
        launch {
            _state.update { it.copy(isDeleting = true) }
            val selectedItems = _state.value.selectedItems.toList()
            val nameMap = _state.value.scanResults.associate { it.itemId to it.name }

            repository.removeMediaItems(
                itemIds = selectedItems,
                itemNameMap = nameMap,
                actionType = CleanupActionType.WATCHED_REMOVAL,
                config = _state.value.config,
            ).onSuccess {
                _state.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirmation = false,
                        selectedItems = emptySet(),
                        rawScanResults = it.rawScanResults.filterNot { selectedItems.contains(it.itemId) },
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirmation = false,
                        error = e.message,
                    )
                }
            }
        }
    }
}
