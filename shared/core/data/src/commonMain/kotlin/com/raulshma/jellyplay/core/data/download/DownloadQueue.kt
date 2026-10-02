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
 * The members carry the repository's primary vocabulary verbatim
 * (`pauseDownload`, `getAllDownloads`, … — the short queue-local names the
 * interface originally shipped with were renamed onto it), so the engine's
 * repository overrides ARE the implementation, with no one-line forwarding
 * aliases between the two vocabularies.
 */
interface DownloadQueue {

    /** Whether this platform has a download pipeline; gates the screen's CTAs. */
    val isSupported: Boolean

    /** The full download list, re-emitting on status transitions. */
    fun getAllDownloads(): Flow<List<DownloadItem>>

    /**
     * Live byte/speed progress for in-flight downloads, keyed by download id
     * — the narrow [DownloadProgress] projection (rows drop out as soon as
     * their status leaves the in-flight set).
     */
    fun getActiveDownloadProgress(): Flow<Map<String, DownloadProgress>>

    /** One-shot read of the current download list (selection restore). */
    suspend fun getAllDownloadsSnapshot(): List<DownloadItem>

    /** Pauses an active transfer. */
    suspend fun pauseDownload(id: String): Result<Unit>

    /** Resumes a paused transfer. */
    suspend fun resumeDownload(id: String): Result<Unit>

    /** Re-queues a paused/failed row (non-suspend, mirrors the repository). */
    fun enqueueDownload(id: String)

    /** Cancels a pending/queued/active transfer. */
    suspend fun cancelDownload(id: String): Result<Unit>

    /** Retries a failed transfer. */
    suspend fun retryDownload(id: String): Result<Unit>

    /** Deletes a download (artifacts + offline rows). */
    suspend fun deleteDownload(id: String): Result<Unit>

    /** Moves a row's queue priority (bulk move-up/move-down ordering). */
    suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit>
}
