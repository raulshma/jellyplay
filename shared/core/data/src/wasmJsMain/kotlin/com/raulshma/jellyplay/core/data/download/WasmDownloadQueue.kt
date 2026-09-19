package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The wasmJs actual of the downloads-screen queue seam: an honest no-op. The
 * browser has no local download pipeline, so [DownloadQueue.isSupported] is
 * false (the screen hides its transfer controls), the list/progress flows
 * stay empty — a genuinely empty queue, since nothing was ever downloaded in
 * this browser — and the controls are inert. Moved from feature:downloads'
 * WasmDownloadQueue with the promoted-interface pass; bound in dataWasmModule.
 */
internal object WasmDownloadQueue : DownloadQueue {
    override val isSupported: Boolean = false
    override fun allDownloads(): Flow<List<DownloadItem>> = flowOf(emptyList())
    override fun activeDownloadProgress(): Flow<Map<String, DownloadProgress>> = flowOf(emptyMap())
    override suspend fun allDownloadsSnapshot(): List<DownloadItem> = emptyList()
    override suspend fun pause(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun resume(id: String): Result<Unit> = Result.success(Unit)
    override fun enqueue(id: String) = Unit
    override suspend fun cancel(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun retry(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun setPriority(id: String, priority: Int): Result<Unit> = Result.success(Unit)
}
