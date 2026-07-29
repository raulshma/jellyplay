package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadStatus

/**
 * Deep module: the status predicates + the resume-byte-offset rule for the
 * download lifecycle.
 *
 * The `downloads.status` column stores [DownloadStatus.name] (a String, for
 * Room migration safety). Status *writes* are entangled with byte/speed
 * bookkeeping and stay at their DAO call sites, but the *predicates* over a
 * status ("is this download in flight?", "can it be resumed?", "did the user
 * pause it?") and the *resume-offset rule* (how many bytes survive a pause vs
 * a failure) were previously re-derived at every site — the predicates as
 * long `status == X.name || status == Y.name` chains, the offset rule as
 * inline `if (status == PAUSED) bytes else 0` copies, duplicated across
 * [DownloadRepositoryImpl], [DownloadWorker], [DownloadRecoveryInitializer]
 * and [MultiConnectionDownloadStrategy].
 *
 * This object is the single home for both. Centralizing them means a future
 * status addition (or a rename, or a fix to the resume rule) touches one
 * file, and each predicate/rule has a direct test instead of being asserted
 * only transitively through the worker/repo.
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

    /**
     * Byte offset a worker should resume from, given the row's last status and
     * accumulated bytes.
     *
     * **Why this lives here.** The resume rule was previously copy-pasted in
     * three places — `DownloadRepositoryImpl`, `DownloadRecoveryInitializer`,
     * and `MultiConnectionDownloadStrategy` — each re-deriving it from
     * comments. The rule:
     *
     *   - `PAUSED` rows keep their contiguous bytes (Range: bytes=N-).
     *   - anything else (`FAILED`, fresh `PENDING`, …) restarts from 0 —
     *     a partial multi-connection body cannot be safely resumed because
     *     its chunks were scattered across `RandomAccessFile` offsets.
     *
     * Centralising it means the next `Range:`-corruption class of bug (a site
     * that resumes a FAILED body from its mid-point) has one place to fix.
     */
    fun resumeByteOffset(status: String, downloadedBytes: Long): Long =
        if (status == DownloadStatus.PAUSED.name) downloadedBytes else 0L

    /** Parses a stored status column back to the typed enum, or null if unknown. */
    fun parse(status: String): DownloadStatus? =
        runCatching { DownloadStatus.valueOf(status) }.getOrNull()
}
