package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * Web seam over core:data's jvmShared `DownloadRepository` —
 * the queue reads and transfer controls the downloads screen offers (list,
 * live per-row progress, pause/resume/enqueue/cancel/retry/delete,
 * priority). The repository's constructor closure is the JVM download
 * engine (OkHttp streaming, WorkManager coupling, Room writes), so
 * commonMain cannot name the class — nor the jvmShared `DownloadProgress`
 * projection it returns, mirrored here field-for-field as
 * [DownloadRowProgress]. QuickDownloadActions template: the interface carries
 * exactly the host-facing surface, the jvmShared actual delegates to the
 * process-wide `DownloadRepository` single (android/desktop behavior
 * unchanged), and the wasmJs actual is an honest no-op.
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
     * — the feature-local mirror of the repository's `DownloadProgress`
     * projection (rows drop out as soon as their status leaves the in-flight
     * set).
     */
    fun activeDownloadProgress(): Flow<Map<String, DownloadRowProgress>>

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

/**
 * The feature-local mirror of core:data's jvmShared `DownloadProgress` —
 * the live per-row transfer projection (id, downloaded bytes, bytes/sec).
 * Rows present here are by definition in flight.
 */
data class DownloadRowProgress(
    val id: String,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
)
