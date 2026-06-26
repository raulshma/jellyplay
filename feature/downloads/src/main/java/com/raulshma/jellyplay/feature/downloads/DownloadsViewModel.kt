package com.raulshma.jellyplay.feature.downloads

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
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
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadRepository: DownloadRepository,
) : JellyPlayViewModel() {

    private val workManager = androidx.work.WorkManager.getInstance(context)

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
            workManager.cancelUniqueWork("${com.raulshma.jellyplay.core.data.worker.DownloadWorker.UNIQUE_WORK_PREFIX}${item.id}")
            downloadRepository.cancelDownload(item.id)
        }
    }

    fun pauseDownload(item: DownloadItem) {
        launch {
            downloadRepository.pauseDownload(item.id)
            workManager.cancelUniqueWork("${com.raulshma.jellyplay.core.data.worker.DownloadWorker.UNIQUE_WORK_PREFIX}${item.id}")
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
            workManager.cancelUniqueWork("${com.raulshma.jellyplay.core.data.worker.DownloadWorker.UNIQUE_WORK_PREFIX}${item.id}")
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

    fun formatBytes(bytes: Long): String = bytes.formatBytes()

    fun formatSpeed(speedBytesPerSec: Long): String = speedBytesPerSec.formatSpeed()

    fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String =
        com.raulshma.jellyplay.core.model.formatEta(downloadedBytes, totalBytes, speedBytesPerSec)
}
