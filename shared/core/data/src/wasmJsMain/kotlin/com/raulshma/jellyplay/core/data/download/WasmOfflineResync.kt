package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.ResyncOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The wasmJs actual of the resync seam: an honest no-op. The browser has no
 * offline-sync engine and no offline downloads to check or resync, so the
 * batch sheet stays idle, [checkForUpdatesBatch] returns no check results and
 * [resyncBatch] is inert — never a fabricated "everything up to date". Moved
 * from feature:downloads' WasmOfflineResync with the promoted-interface pass;
 * bound in dataWasmModule.
 */
internal object WasmOfflineResync : OfflineResync {
    override val batchProgress: StateFlow<ResyncBatchProgress> = MutableStateFlow(ResyncBatchProgress())
    override suspend fun checkForUpdatesBatch(itemIds: List<String>, force: Boolean): List<ResyncCheckResult> =
        emptyList()
    override fun resyncBatch(itemIds: List<String>, options: ResyncOptions) = Unit
    override fun clearBatchProgress() = Unit
}
