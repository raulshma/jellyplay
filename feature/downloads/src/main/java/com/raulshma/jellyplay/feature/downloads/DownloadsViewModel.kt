package com.raulshma.jellyplay.feature.downloads

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@Immutable
data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val totalStorageBytes: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Stable ids currently in selection mode. */
    val selectedIds: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadRepository: DownloadRepository,
) : JellyPlayViewModel() {

    private val workManager = androidx.work.WorkManager.getInstance(context)

    private fun workName(id: String) =
        "${com.raulshma.jellyplay.core.data.worker.DownloadWorker.UNIQUE_WORK_PREFIX}$id"

    private val _uiState = stateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.flow

    init {
        launch {
            downloadRepository.getAllDownloads()
                .distinctUntilChanged { old, new ->
                    if (old.size != new.size) return@distinctUntilChanged false
                    old.zip(new).all { (o, n) -> o.downloadedBytes == n.downloadedBytes && o.id == n.id && o.status == n.status }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(error = e.localizedMessage ?: "Failed to load downloads", isLoading = false)
                    }
                }
                .collectLatest { items ->
                    _uiState.update {
                        it.copy(
                            downloads = items,
                            error = null,
                            isLoading = false,
                            totalStorageBytes = items.sumOf { item -> item.downloadedBytes },
                        )
                    }
                }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        launch {
            workManager.cancelUniqueWork(workName(item.id))
            downloadRepository.cancelDownload(item.id)
        }
    }

    fun pauseDownload(item: DownloadItem) {
        launch {
            downloadRepository.pauseDownload(item.id)
            workManager.cancelUniqueWork(workName(item.id))
        }
    }

    fun resumeDownload(item: DownloadItem) {
        launch {
            downloadRepository.resumeDownload(item.id)
            downloadRepository.enqueueDownload(item.id)
        }
    }

    fun deleteDownload(item: DownloadItem) {
        launch {
            workManager.cancelUniqueWork(workName(item.id))
            downloadRepository.deleteDownload(item.id)
        }
    }

    fun retryDownload(item: DownloadItem) {
        launch {
            downloadRepository.retryDownload(item.id)
            downloadRepository.enqueueDownload(item.id)
        }
    }

    fun moveToFront(item: DownloadItem) {
        launch {
            val maxPriority = _uiState.value.downloads.maxOfOrNull { it.priority } ?: 0
            downloadRepository.setDownloadPriority(item.id, maxPriority + 1)
        }
    }

    fun lowerPriority(item: DownloadItem) {
        launch {
            val minPriority = _uiState.value.downloads.minOfOrNull { it.priority } ?: 0
            downloadRepository.setDownloadPriority(item.id, minPriority - 1)
        }
    }

    // ── Selection ────────────────────────────────────────────────────────

    fun toggleSelection(item: DownloadItem) {
        _uiState.update {
            val next = if (item.id in it.selectedIds) it.selectedIds - item.id else it.selectedIds + item.id
            it.copy(selectedIds = next, selectionMode = next.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedIds = it.downloads.map { item -> item.id }.toSet(), selectionMode = true)
        }
    }

    // ── Bulk actions ─────────────────────────────────────────────────────

    /** Bulk-delete every selected download. Frees disk for completed items. */
    fun deleteSelected() {
        val targets = _uiState.value.downloads.filter { it.id in _uiState.value.selectedIds }
        if (targets.isEmpty()) return
        launch {
            targets.forEach { item ->
                workManager.cancelUniqueWork(workName(item.id))
                downloadRepository.deleteDownload(item.id)
            }
            clearSelection()
        }
    }

    /** Pause every selected download that is currently downloading. */
    fun pauseSelected() {
        val targets = _uiState.value.downloads
            .filter { it.id in _uiState.value.selectedIds && it.status == DownloadStatus.DOWNLOADING }
        if (targets.isEmpty()) return
        launch {
            targets.forEach { item ->
                downloadRepository.pauseDownload(item.id)
                workManager.cancelUniqueWork(workName(item.id))
            }
        }
    }

    /** Resume every selected download that is currently paused. */
    fun resumeSelected() {
        val targets = _uiState.value.downloads
            .filter { it.id in _uiState.value.selectedIds && it.status == DownloadStatus.PAUSED }
        if (targets.isEmpty()) return
        launch {
            targets.forEach { item ->
                downloadRepository.resumeDownload(item.id)
                downloadRepository.enqueueDownload(item.id)
            }
        }
    }

    /** Cancel every selected active/queued/paused download. */
    fun cancelSelected() {
        val targets = _uiState.value.downloads.filter { item ->
            item.id in _uiState.value.selectedIds && item.status in setOf(
                DownloadStatus.PENDING,
                DownloadStatus.QUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED,
            )
        }
        if (targets.isEmpty()) return
        launch {
            targets.forEach { item ->
                workManager.cancelUniqueWork(workName(item.id))
                downloadRepository.cancelDownload(item.id)
            }
        }
    }

    fun formatBytes(bytes: Long): String = bytes.formatBytes()

    fun formatSpeed(speedBytesPerSec: Long): String = speedBytesPerSec.formatSpeed()

    fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String =
        com.raulshma.jellyplay.core.model.formatEta(downloadedBytes, totalBytes, speedBytesPerSec)
}
