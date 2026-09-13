package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.ResyncOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The wasmJs actuals of the downloads web seams: an honest empty
 * platform. The browser has no local download pipeline and no offline-sync
 * engine, so:
 *  - [WasmDownloadQueue] reports [DownloadQueue.isSupported] = false (the
 *    screen hides its transfer controls), the list/progress flows stay
 *    empty — a genuinely empty queue, since nothing was ever downloaded in
 *    this browser — and the controls are inert;
 *  - [WasmOfflineResync] keeps the batch sheet idle, returns no check
 *    results and no-ops the resync — never a fabricated "up to date".
 */
internal object WasmDownloadQueue : DownloadQueue {
    override val isSupported: Boolean = false
    override fun allDownloads(): Flow<List<DownloadItem>> = flowOf(emptyList())
    override fun activeDownloadProgress(): Flow<Map<String, DownloadRowProgress>> = flowOf(emptyMap())
    override suspend fun allDownloadsSnapshot(): List<DownloadItem> = emptyList()
    override suspend fun pause(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun resume(id: String): Result<Unit> = Result.success(Unit)
    override fun enqueue(id: String) = Unit
    override suspend fun cancel(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun retry(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun setPriority(id: String, priority: Int): Result<Unit> = Result.success(Unit)
}

internal object WasmOfflineResync : OfflineResync {
    override val batchProgress: StateFlow<ResyncBatchProgress> = MutableStateFlow(ResyncBatchProgress())
    override suspend fun checkForUpdatesBatch(itemIds: List<String>, force: Boolean): List<ResyncCheckResult> =
        emptyList()
    override fun resyncBatch(itemIds: List<String>, options: ResyncOptions) = Unit
    override fun clearBatchProgress() = Unit
}
