package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * The queue reads and transfer controls the downloads screen offers (list,
 * live per-row progress, pause/resume/enqueue/cancel/retry/delete, priority,
 * queue placement).
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

    /**
     * The composite resume verb: marks the row PENDING **and** re-enqueues its
     * worker (honoring schedule/network prefs) — one dispatch for what every
     * control surface (the screen's VM, the notification receiver) always did
     * as a hand-paired resume+enqueue. The enqueue follow-up lives inside the
     * shared implementation rather than as a new interface member, so the two
     * callers dispatch one verb and the surface stays at this size.
     */
    suspend fun resumeDownload(id: String): Result<Unit>

    /** Re-queues a paused/failed row (non-suspend, mirrors the repository). */
    fun enqueueDownload(id: String)

    /** Cancels a pending/queued/active transfer. */
    suspend fun cancelDownload(id: String): Result<Unit>

    /** The composite retry verb — same enqueue-inside shape as [resumeDownload], from byte 0. */
    suspend fun retryDownload(id: String): Result<Unit>

    /**
     * Deletes a download (artifacts + offline rows).
     *
     * ADR-0015's un-arm verb split rides [keepSeriesArm]: this screen's
     * single/bulk delete is the LIST-MANAGEMENT half — a series deleted to
     * zero rows loses its arm (the policy row that keeps it in the
     * auto-download check's universe) — so callers pass `keepSeriesArm =
     * false` explicitly. The classification is explicit HERE rather than
     * defaulted because [com.raulshma.jellyplay.core.data.repository.DownloadRepository]
     * is the only supertype allowed to carry the default (Kotlin forbids one
     * override inheriting defaults from several supertypes); only
     * watch-boundary callers route around this seam via the repository verb.
     */
    suspend fun deleteDownload(id: String, keepSeriesArm: Boolean): Result<Unit>

    /** Moves a row's queue priority (bulk move-up/move-down ordering). */
    suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit>

    /**
     * The queue-placement composite verbs (the downloads screen's move-to-front /
     * lower-priority row actions): the caller hands only the row id — the
     * placement number is computed inside the implementation over the
     * authoritative queue state (the same DAO snapshot
     * [getAllDownloadsSnapshot] reads; a caller's rendered list can be
     * stale), then persisted through the same write [setDownloadPriority]
     * runs. Higher priority = closer to the front of the pending queue (the
     * DAO orders it `priority DESC`); an empty queue bases placement at 0
     * (front → 1, back → −1), the baselines the ViewModel arithmetic used
     * before this verb existed.
     */
    suspend fun moveToFront(id: String): Result<Unit>

    /** The toward-the-back twin of [moveToFront]. */
    suspend fun moveToBack(id: String): Result<Unit>
}
