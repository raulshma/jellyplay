package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.ResyncOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * The JVM adapters over core:data's `DownloadRepository` and
 * `OfflineSyncManager` singles — the downloads screen's queue reads, transfer
 * controls and resync calls delegate verbatim; the only added motion is the
 * field-identical mapping of the repository's jvmShared `DownloadProgress`
 * projection onto the feature-local [DownloadRowProgress] mirror
 * (android/desktop behavior unchanged).
 */
internal class JvmDownloadQueue(
    private val repository: DownloadRepository,
) : DownloadQueue {
    override val isSupported: Boolean = true
    override fun allDownloads(): Flow<List<DownloadItem>> = repository.getAllDownloads()
    override fun activeDownloadProgress(): Flow<Map<String, DownloadRowProgress>> =
        repository.getActiveDownloadProgress().map { live -> live.mapValues { (_, p) -> p.toMirror() } }
    override suspend fun allDownloadsSnapshot(): List<DownloadItem> = repository.getAllDownloadsSnapshot()
    override suspend fun pause(id: String): Result<Unit> = repository.pauseDownload(id)
    override suspend fun resume(id: String): Result<Unit> = repository.resumeDownload(id)
    override fun enqueue(id: String) = repository.enqueueDownload(id)
    override suspend fun cancel(id: String): Result<Unit> = repository.cancelDownload(id)
    override suspend fun retry(id: String): Result<Unit> = repository.retryDownload(id)
    override suspend fun delete(id: String): Result<Unit> = repository.deleteDownload(id)
    override suspend fun setPriority(id: String, priority: Int): Result<Unit> =
        repository.setDownloadPriority(id, priority)
}

internal class JvmOfflineResync(
    private val manager: OfflineSyncManager,
) : OfflineResync {
    override val batchProgress: StateFlow<ResyncBatchProgress> = manager.batchProgress
    override suspend fun checkForUpdatesBatch(itemIds: List<String>, force: Boolean): List<ResyncCheckResult> =
        manager.checkForUpdatesBatch(itemIds, force)
    override fun resyncBatch(itemIds: List<String>, options: ResyncOptions) = manager.resyncBatch(itemIds, options)
    override fun clearBatchProgress() = manager.clearBatchProgress()
}

/** Field-identical 1:1 map — see the [DownloadRowProgress] mirror KDoc. */
private fun DownloadProgress.toMirror(): DownloadRowProgress =
    DownloadRowProgress(id = id, downloadedBytes = downloadedBytes, speedBytesPerSec = speedBytesPerSec)
