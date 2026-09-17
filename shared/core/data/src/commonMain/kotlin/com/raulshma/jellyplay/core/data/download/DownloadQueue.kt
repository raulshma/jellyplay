package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * The queue reads and transfer controls the downloads screen offers (list,
 * live per-row progress, pause/resume/enqueue/cancel/retry/delete, priority).
 * Moved from feature:downloads (which declared it over an unnameable
 * jvmShared `DownloadRepository`) with the promoted-interface pass that
 * retired the per-read QuickDownloadActions template adapters: the surface is
 * core:model + [DownloadProgress] only, so — per the DownloadIntake
 * precedent — it crosses verbatim and its natural JVM source,
 * [com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl],
 * implements it directly (bound in dataJvmModule over the repository single).
 * The wasmJs actual — [WasmDownloadQueue] in wasmJsMain, bound in
 * dataWasmModule — is an honest no-op.
 *
 * Web behavior: the browser has no local download pipeline, so the wasm
 * actual reports [isSupported] = false — the screen hides its transfer
 * controls — while the list/progress flows stay empty (a genuinely empty
 * queue: nothing was ever downloaded in this browser) and the controls are
 * inert. No offline artifacts are ever fabricated.
 */
interface DownloadQueue {

    /** Whether this platform has a download pipeline; gates the screen's CTAs. */
    val isSupported: Boolean

    /** The full download list, re-emitting on status transitions. */
    fun allDownloads(): Flow<List<DownloadItem>>

    /**
     * Live byte/speed progress for in-flight downloads, keyed by download id
     * — the narrow [DownloadProgress] projection (rows drop out as soon as
     * their status leaves the in-flight set).
     */
    fun activeDownloadProgress(): Flow<Map<String, DownloadProgress>>

    /** One-shot read of the current download list (selection restore). */
    suspend fun allDownloadsSnapshot(): List<DownloadItem>

    /** Pauses an active transfer. */
    suspend fun pause(id: String): Result<Unit>

    /** Resumes a paused transfer. */
    suspend fun resume(id: String): Result<Unit>

    /** Re-queues a paused/failed row (non-suspend, mirrors the repository). */
    fun enqueue(id: String)

    /** Cancels a pending/queued/active transfer. */
    suspend fun cancel(id: String): Result<Unit>

    /** Retries a failed transfer. */
    suspend fun retry(id: String): Result<Unit>

    /** Deletes a download (artifacts + offline rows). */
    suspend fun delete(id: String): Result<Unit>

    /** Moves a row's queue priority (bulk move-up/move-down ordering). */
    suspend fun setPriority(id: String, priority: Int): Result<Unit>
}
