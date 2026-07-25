package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadStatus

/**
 * Deep module: the status predicates for the download lifecycle.
 *
 * The `downloads.status` column stores [DownloadStatus.name] (a String, for
 * Room migration safety). Transition *writes* stay at their call sites (they
 * are entangled with byte/speed bookkeeping), but the boolean *predicates*
 * over a status — "is this download in flight?", "can it be resumed?", "did
 * the user pause it?" — were previously re-derived at every guard site as
 * long `status == X.name || status == Y.name` chains, duplicated across
 * [DownloadRepositoryImpl], [DownloadWorker] and [MultiConnectionDownloadStrategy].
 *
 * This object is the single home for those predicates. Centralizing them means
 * a future status addition (or a rename) touches one file, and each predicate
 * has a direct test instead of being asserted only transitively through the
 * worker/repo.
 *
 * Predicates take the raw column String so callers need no parse step; use
 * [parse] when the typed enum is needed.
 */
object DownloadStates {

    /** Download is queued or actively transferring (not yet started, queued, or downloading). */
    fun isActive(status: String): Boolean =
        status == DownloadStatus.DOWNLOADING.name ||
            status == DownloadStatus.QUEUED.name ||
            status == DownloadStatus.PENDING.name

    /** Download was interrupted and may be resumable (paused by user/network, or failed). */
    fun isPausedOrFailed(status: String): Boolean =
        status == DownloadStatus.PAUSED.name || status == DownloadStatus.FAILED.name

    /** Download is not in flight — paused or cancelled. Worker checks this before touching a row. */
    fun isInactive(status: String?): Boolean =
        status != null && (status == DownloadStatus.PAUSED.name || status == DownloadStatus.CANCELLED.name)

    /** The user paused this row (USER reason), so auto-resume must leave it alone. */
    fun isUserPaused(status: String, pausedReason: String?): Boolean =
        status == DownloadStatus.PAUSED.name &&
            pausedReason == DownloadPauseReason.USER.persistedValue

    /** True when the row has exhausted its reconnect auto-retry budget. */
    fun isExhausted(retryCount: Int): Boolean = retryCount >= DOWNLOAD_MAX_AUTO_RETRY

    /** Parses a stored status column back to the typed enum, or null if unknown. */
    fun parse(status: String): DownloadStatus? =
        runCatching { DownloadStatus.valueOf(status) }.getOrNull()
}
