package com.raulshma.jellyplay.feature.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.model.DownloadItem
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

    private val workManager = WorkManager.getInstance(context)

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
            workManager.cancelUniqueWork("${DownloadWorker.UNIQUE_WORK_PREFIX}${item.id}")
            downloadRepository.cancelDownload(item.id)
        }
    }

    fun pauseDownload(item: DownloadItem) {
        launch {
            downloadRepository.pauseDownload(item.id)
            workManager.cancelUniqueWork("${DownloadWorker.UNIQUE_WORK_PREFIX}${item.id}")
        }
    }

    fun resumeDownload(item: DownloadItem) {
        launch {
            downloadRepository.resumeDownload(item.id)
            enqueueDownloadWorker(item.id)
        }
    }

    fun deleteDownload(item: DownloadItem) {
        launch {
            workManager.cancelUniqueWork("${DownloadWorker.UNIQUE_WORK_PREFIX}${item.id}")
            downloadRepository.deleteDownload(item.id)
        }
    }

    fun retryDownload(item: DownloadItem) {
        launch {
            downloadRepository.retryDownload(item.id)
            enqueueDownloadWorker(item.id)
        }
    }

    private fun enqueueDownloadWorker(downloadId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            "${DownloadWorker.UNIQUE_WORK_PREFIX}$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun formatSpeed(speedBytesPerSec: Long): String = when {
        speedBytesPerSec <= 0 -> ""
        speedBytesPerSec < 1024 -> "$speedBytesPerSec B/s"
        speedBytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(speedBytesPerSec / 1024.0)
        speedBytesPerSec < 1024 * 1024 * 1024 -> "%.1f MB/s".format(speedBytesPerSec / (1024.0 * 1024))
        else -> "%.1f GB/s".format(speedBytesPerSec / (1024.0 * 1024 * 1024))
    }

    fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String {
        if (totalBytes <= 0 || speedBytesPerSec <= 0) return ""
        val remainingBytes = totalBytes - downloadedBytes
        if (remainingBytes <= 0) return ""
        val secondsRemaining = remainingBytes / speedBytesPerSec
        return when {
            secondsRemaining < 60 -> "${secondsRemaining}s left"
            secondsRemaining < 3600 -> "${secondsRemaining / 60}m ${secondsRemaining % 60}s left"
            else -> {
                val hours = secondsRemaining / 3600
                val minutes = (secondsRemaining % 3600) / 60
                "${hours}h ${minutes}m left"
            }
        }
    }
}
