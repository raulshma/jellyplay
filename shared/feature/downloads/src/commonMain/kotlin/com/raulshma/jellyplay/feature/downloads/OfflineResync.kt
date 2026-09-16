package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.ResyncOptions
import kotlinx.coroutines.flow.StateFlow

/**
 * Web seam over core:data's jvmShared `OfflineSyncManager` —
 * the update-check / metadata-resync surface the downloads screen drives
 * (batch progress sheet, check-for-updates, resync). The manager is the JVM
 * offline-sync engine (IO-dispatched Room reads + network fan-out), so
 * commonMain cannot name the class. QuickDownloadActions template: the
 * interface carries exactly the host-facing surface, the jvmShared actual
 * delegates to the process-wide `OfflineSyncManager` single (android/desktop
 * behavior unchanged), and the wasmJs actual is an honest no-op.
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
