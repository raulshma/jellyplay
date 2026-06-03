package com.raulshma.jellyplay.feature.admin.watchedremoval

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.feature.admin.stalemedia.MediaSortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

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
    val auditEntries: List<AuditLogEntry> = emptyList(),
    val sortOption: MediaSortOption = MediaSortOption.DEFAULT,
) {
    val scanResults: List<MediaItemStub>
        get() = when (sortOption) {
            MediaSortOption.DEFAULT -> rawScanResults
            MediaSortOption.NAME_ASC -> rawScanResults.sortedBy { it.name.lowercase() }
            MediaSortOption.NAME_DESC -> rawScanResults.sortedByDescending { it.name.lowercase() }
            MediaSortOption.SIZE_DESC -> rawScanResults.sortedByDescending { it.sortSizeBytes }
            MediaSortOption.SIZE_ASC -> rawScanResults.sortedBy { it.sortSizeBytes }
            MediaSortOption.TYPE -> rawScanResults.sortedBy { it.type }
            MediaSortOption.DATE -> rawScanResults.sortedBy { it.dateText }
        }
}

private val MediaItemStub.sortSizeBytes: Long
    get() {
        val match = Regex("(\\d+\\.?\\d*)\\s*(B|KB|MB|GB|TB)").find(sizeText)
        if (match != null) {
            val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
            return when (match.groupValues[2]) {
                "TB" -> (num * 1024 * 1024 * 1024 * 1024).toLong()
                "GB" -> (num * 1024 * 1024 * 1024).toLong()
                "MB" -> (num * 1024 * 1024).toLong()
                "KB" -> (num * 1024).toLong()
                else -> num.toLong()
            }
        }
        return 0L
    }

@HiltViewModel
class WatchedMediaCleanupViewModel @Inject constructor(
    private val repository: AdminStatisticsRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(WatchedMediaState())
    val state = _state.asStateFlow()

    init {
        observeAuditHistory()
    }

    private fun observeAuditHistory() {
        viewModelScope.launch {
            repository.getAuditHistory(CleanupActionType.WATCHED_REMOVAL).collect { entries ->
                _state.value = _state.value.copy(auditEntries = entries)
            }
        }
    }

    fun updateConfig(config: MediaCleanupConfig) {
        _state.value = _state.value.copy(config = config)
    }

    fun updateSort(option: MediaSortOption) {
        _state.value = _state.value.copy(sortOption = option)
    }

    fun startScan() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, rawScanResults = emptyList(), selectedItems = emptySet())
            repository.detectWatchedMedia(_state.value.config)
                .onSuccess { scanId ->
                    _state.value = _state.value.copy(scanId = scanId, isLoading = false)
                    observeScanProgress(scanId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    private fun observeScanProgress(scanId: String) {
        viewModelScope.launch {
            repository.getScanProgress(scanId).collect { progress ->
                _state.value = _state.value.copy(scanProgress = progress)
                if (progress.phase == ScanPhase.COMPLETED) {
                    _state.value = _state.value.copy(rawScanResults = loadResults(scanId))
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
        _state.value = _state.value.copy(
            selectedItems = if (current.contains(itemId)) current - itemId else current + itemId,
        )
    }

    fun selectAll() {
        val allIds = _state.value.scanResults.map { it.itemId }.toSet()
        _state.value = _state.value.copy(
            selectedItems = if (_state.value.selectedItems == allIds) emptySet() else allIds,
        )
    }

    fun showDeleteConfirmation() {
        _state.value = _state.value.copy(showDeleteConfirmation = true)
    }

    fun dismissDeleteConfirmation() {
        _state.value = _state.value.copy(showDeleteConfirmation = false)
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true)
            val selectedItems = _state.value.selectedItems.toList()
            val nameMap = _state.value.scanResults.associate { it.itemId to it.name }

            repository.removeMediaItems(
                itemIds = selectedItems,
                itemNameMap = nameMap,
                actionType = CleanupActionType.WATCHED_REMOVAL,
                config = _state.value.config,
            ).onSuccess {
                _state.value = _state.value.copy(
                    isDeleting = false,
                    showDeleteConfirmation = false,
                    selectedItems = emptySet(),
                    rawScanResults = _state.value.rawScanResults.filterNot { selectedItems.contains(it.itemId) },
                )
            }.onFailure {
                _state.value = _state.value.copy(isDeleting = false, showDeleteConfirmation = false)
            }
        }
    }
}
