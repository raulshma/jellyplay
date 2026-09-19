package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.ResyncOptions
import kotlinx.coroutines.flow.StateFlow

/**
 * The update-check / metadata-resync surface the downloads screen drives
 * (batch progress sheet, check-for-updates, resync). Moved from
 * feature:downloads (which declared it over an unnameable jvmShared sync
 * manager) with the promoted-interface pass that retired the per-read
 * QuickDownloadActions template adapters: the surface is core:model only, so
 * — per the DownloadIntake precedent — it crosses verbatim and its natural
 * JVM source, `OfflineSyncManager` (jvmShared `sync/`), implements it
 * directly (bound in dataJvmModule over the manager single). The wasmJs
 * actual — [WasmOfflineResync] in wasmJsMain, bound in dataWasmModule — is an
 * honest no-op.
 *
 * Web behavior: the browser has no offline downloads to check or resync, so
 * the wasm actual keeps [batchProgress] idle-empty, [checkForUpdatesBatch]
 * returns no results and [resyncBatch] is inert — never a fabricated
 * "everything up to date" result.
 */
interface OfflineResync {

    /** Live per-item progress for the running resync batch (idle = empty). */
    val batchProgress: StateFlow<ResyncBatchProgress>

    /**
     * Checks [itemIds] against their persisted sync baselines; returns the
     * per-item verdicts (empty on web — no baselines exist there).
     */
    suspend fun checkForUpdatesBatch(itemIds: List<String>, force: Boolean = false): List<ResyncCheckResult>

    /** Resyncs [itemIds]' metadata/images per [options]; reports [batchProgress]. */
    fun resyncBatch(itemIds: List<String>, options: ResyncOptions = ResyncOptions.ALL)

    /** Clears the batch progress sheet. */
    fun clearBatchProgress()
}
