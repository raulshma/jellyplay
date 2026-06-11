package com.raulshma.jellyplay.feature.downloads

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadRepository: DownloadRepository,
) : JellyPlayViewModel() {

    private val workManager = androidx.work.WorkManager.getInstance(context)

    private val _downloads = composeState<List<DownloadItem>>(emptyList())
    val downloads: List<DownloadItem> get() = _downloads.value

    private val _totalStorageBytes = composeLongState(0L)
    val totalStorageBytes: Long get() = _totalStorageBytes.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    init {
        launch {
            downloadRepository.getAllDownloads()
                .distinctUntilChanged { old, new ->
                    if (old.size != new.size) return@distinctUntilChanged false
                    old.zip(new).all { (o, n) -> o.downloadedBytes == n.downloadedBytes && o.id == n.id && o.status == n.status }
                }
                .collectLatest { items ->
                    _downloads.value = items
                    _isLoading.value = false
                    val total = _totalStorageBytes.value
                    val newTotal = items.sumOf { it.downloadedBytes }
                    if (total != newTotal) _totalStorageBytes.value = newTotal
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

    fun formatBytes(bytes: Long): String = bytes.formatBytes()

    fun formatSpeed(speedBytesPerSec: Long): String = speedBytesPerSec.formatSpeed()

    fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String =
        com.raulshma.jellyplay.core.model.formatEta(downloadedBytes, totalBytes, speedBytesPerSec)
}
