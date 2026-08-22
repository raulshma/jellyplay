package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.model.DownloadStatus
import java.io.File

/**
 * User-facing message written to `DownloadEntity.errorMessage` when the server
 * returns 401/403 mid-download, signalling the access token was revoked or
 * expired. The Downloads UI renders `errorMessage` under the FAILED state so the
 * user knows to sign in again. Lives here because it is a policy *output*, not
 * a worker concern.
 */
const val SESSION_EXPIRED_ERROR = "Session expired — please sign in again"

/**
 * User-facing message written when the final byte count does not match the
 * Content-Length (or the body was empty with no Content-Length), indicating a
 * truncated/empty download — possible network truncation.
 */
const val SIZE_MISMATCH_ERROR = "File size mismatch — possible network truncation"

/**
 * Deep module: the download failure-classification rule, plus its DAO
 * applicator. Decides — given a thrown [Throwable] caught mid-download — what
 * should happen to the `downloads` row, and exposes a sealed [Outcome] the
 * caller applies to the DAO and maps to a WorkManager `Result`.
 *
 * **Why this lives here.** Before extraction, the same exception-classification
 * rule was inlined in four catch blocks across [DownloadWorker] (three outer
 * catches around `doWork`) and [MultiConnectionDownloadStrategy], with three
 * divergent guards and two missing `pausedReason` writes. The outer catches in
 * `DownloadWorker` silently dropped the pause reason for `IOException`, so a
 * mid-stream `SocketTimeout` caught there marked the row FAILED (not
 * PAUSED+NETWORK) and the reconnect auto-resume never picked it up — the row
 * sat FAILED until the user manually retried. Multi-connection had its own
 * copy of the same rule with the byte-reset + partial-delete fork inlined.
 *
 * Centralising the rule here means:
 *   - one home for "what does this failure do to the row?"
 *   - the outer-catch bug is structurally impossible (every site routes here)
 *   - the rule has a direct pure-JVM test instead of being asserted only
 *     transitively through a `CoroutineWorker` that needs Robolectric.
 *
 * **Scope.** Two classification entry points, both pure (no DAO, no androidx.work):
 *   - [decide] classifies a *thrown* [Throwable] caught mid-transfer.
 *   - [decideForStatus] classifies a non-2xx HTTP response code (401/403
 *     "session expired", transient 5xx/429). The 416 "stale range" code is
 *     *not* a failure — it is a recovery action (re-issue without `Range:`)
 *     and stays in the transfer runner. Integrity failures (size mismatch,
 *     0-byte result) are fixed outcomes built from [SIZE_MISMATCH_ERROR].
 *
 * Both entry points return the same sealed [Outcome] and honour the same
 * concurrent-pause guard (`PAUSED → [Outcome.Suppress]`), so every failure
 * path — thrown or status — converges on one applicator + [toWorkResult].
 *
 * **CancellationException is not a download failure.** It is a structured-
 * concurrency control signal and must be rethrown by the caller ahead of the
 * `catch (Throwable)` that invokes this policy. This module never sees it.
 *
 * **Inputs.** The four call sites disagree on which bytes to compare ("did we
 * make progress?"): the inner catch uses `downloadedBytes > existingBytes`, the
 * multi-connection catch uses `totalDownloaded > 0`, the outer catches have no
 * body bytes at all (pre-stream failures). [madeProgress] collapses those to
 * the one fact the policy cares about — "did this run write anything we'd want
 * to keep?" — and each caller computes it from its own truth. [isResumablePartial]
 * is a *strategy* constant (single-connection = true, multi-connection = false),
 * not a runtime measurement: single-connection partials are a contiguous prefix
 * safe to resume with `Range: bytes=N-`; multi-connection partials are scattered
 * `RandomAccessFile.seek()` offsets that can never be appended to.
 */
object DownloadFailurePolicy {

    /**
     * True when the row's status column was flipped to PAUSED by the user
     * while this run was failing. Both entry points honour it so a failure
     * never clobbers the user's pause with a FAILED/PAUSED write.
     */
    private fun pausedConcurrently(currentStatus: String): Boolean =
        currentStatus == DownloadStatus.PAUSED.name

    /**
     * Classifies [error] into an [Outcome].
     *
     * @param error the thrown exception (never a `CancellationException` —
     *   callers rethrow that ahead of this call).
     * @param madeProgress true iff this run wrote bytes worth preserving. Callers
     *   compute this from their own truth (single-conn: `downloaded > existing`;
     *   multi-conn: `total > 0`; pre-body failures: `false`).
     * @param currentStatus the row's current `status` column, read by the caller.
     *   If it is `PAUSED`, the user paused concurrently and the policy returns
     *   [Outcome.Suppress] so the failure handler does not clobber the user's
     *   pause with a FAILED/PAUSED write.
     * @param isResumablePartial strategy constant: single-connection = true
     *   (keep the partial, preserve bytes); multi-connection = false (delete the
     *   partial, reset bytes to 0 — the scattered offsets are not a valid prefix).
     */
    fun decide(
        error: Throwable,
        madeProgress: Boolean,
        currentStatus: String,
        isResumablePartial: Boolean,
    ): Outcome {
        if (pausedConcurrently(currentStatus)) return Outcome.Suppress

        val isIo = error is java.io.IOException
        return when {
            // IOException (incl. SocketTimeoutException): network class.
            //   - made progress → PAUSED + NETWORK (reconnect auto-resume picks it up)
            //   - no progress   → FAILED (fixes the stuck-row bug: pre-body IO
            //     failures used to leave the row DOWNLOADING with no mutation)
            isIo && madeProgress -> Outcome.RecordPause(
                reason = DownloadPauseReason.NETWORK,
                isResumablePartial = isResumablePartial,
                shouldRetry = true,
            )
            isIo -> Outcome.MarkFailed(
                errorMessage = null,
                deletePartial = !isResumablePartial,
                shouldRetry = true,
            )
            // Generic Throwable: programmer error or unrecoverable. Fail fast —
            // don't burn the auto-retry budget on something that won't fix itself.
            else -> Outcome.MarkFailed(
                errorMessage = error.message ?: error::class.simpleName,
                deletePartial = !isResumablePartial,
                shouldRetry = false,
            )
        }
    }

    /**
     * Classifies a non-2xx HTTP [responseCode] (other than 416, which the
     * transfer runner handles as a recovery action) into an [Outcome].
     *
     * Parity with the pre-extraction inline branches in
     * `DownloadWorker.performSingleConnectionDownload`:
     *
     * | code | inline behaviour | outcome |
     * | ---- | ---------------- | ------- |
     * | 401/403 | FAILED + `SESSION_EXPIRED_ERROR`, no retry (every attempt gets the same 401 and burns the budget) | [Outcome.MarkFailed] w/ message, `shouldRetry=false` |
     * | other non-2xx (503, 429, …) | reset bytes to 0, delete partial, retry (previously re-sent a stale `Range:` and looped) | [Outcome.MarkFailed] w/ null message + `deletePartial`, `shouldRetry=true` |
     *
     * @param responseCode the HTTP status code from the GET response. Callers
     *   must handle 416 (recovery) and 200/206 (success) themselves before
     *   calling this — those are not failure codes.
     * @param currentStatus the row's current `status` column. If `PAUSED`, the
     *   user paused concurrently → [Outcome.Suppress] (same guard as [decide]).
     * @param isResumablePartial strategy constant: single-connection = true
     *   (the applicator preserves the partial on FAILED); multi-connection =
     *   false (deletes it). Note the transient branch forces `deletePartial`
     *   true regardless, mirroring the inline wipe-and-retry.
     */
    fun decideForStatus(
        responseCode: Int,
        currentStatus: String,
        isResumablePartial: Boolean,
    ): Outcome {
        if (pausedConcurrently(currentStatus)) return Outcome.Suppress

        return when (responseCode) {
            // Auth failure: retrying is pointless (same 401 every time). Fail
            // the row with a user-facing "session expired" message.
            401, 403 -> Outcome.MarkFailed(
                errorMessage = SESSION_EXPIRED_ERROR,
                deletePartial = !isResumablePartial,
                shouldRetry = false,
            )
            // Transient non-2xx (503, 429, …): wipe the partial and retry from
            // byte 0 so the next attempt doesn't loop on a stale Range header.
            // deletePartial is forced true even for single-connection (where the
            // partial would otherwise be a valid prefix) — the wipe is the fix
            // for the old "stale Range burns retries" loop.
            else -> Outcome.MarkFailed(
                errorMessage = null,
                deletePartial = true,
                shouldRetry = true,
            )
        }
    }
}

/**
 * What the failure handler should do to the `downloads` row, and whether
 * WorkManager should retry. Sealed so invalid combinations (PAUSED + delete,
 * Suppress + mutate) are unrepresentable.
 *
 * Apply via the [applyTo] extension; map to `ListenableWorker.Result` via
 * [toWorkResult]. Both keep `androidx.work` and `DownloadDao` out of the pure
 * [DownloadFailurePolicy] module so the rule is unit-testable in isolation.
 */
sealed interface Outcome {

    /** WorkManager should re-enqueue this worker. */
    val shouldRetry: Boolean

    /**
     * The user paused concurrently. Do not touch the row — its PAUSED status
     * and USER reason are the source of truth. The worker exits cleanly
     * (`Result.success()`) rather than wastefully retrying once.
     */
    data object Suppress : Outcome {
        override val shouldRetry: Boolean = false
    }

    /**
     * Record a network pause: status → PAUSED, pausedReason → [reason],
     * increment the auto-retry budget. Byte/file policy depends on
     * [isResumablePartial] (see [DownloadFailurePolicy]).
     */
    data class RecordPause(
        val reason: DownloadPauseReason,
        val isResumablePartial: Boolean,
        override val shouldRetry: Boolean,
    ) : Outcome

    /**
     * Mark the row FAILED with an optional [errorMessage]. When [deletePartial]
     * is true the applicator also resets bytes to 0 and deletes the partial file
     * (multi-connection path, where the partial can never be resumed).
     */
    data class MarkFailed(
        val errorMessage: String?,
        val deletePartial: Boolean,
        override val shouldRetry: Boolean,
    ) : Outcome
}

// ---------------------------------------------------------------------------
// Applicators. The policy above is pure; these extensions translate an Outcome
// into DAO side-effects and a WorkManager Result. Kept as extensions so the
// rule module imports neither DownloadDao nor androidx.work.
// ---------------------------------------------------------------------------

/**
 * Single-connection applicator. The partial is a contiguous prefix, so by
 * default it is kept and [preservedBytes] is written back to the row as a
 * resume point. When the outcome carries [Outcome.MarkFailed.deletePartial]
 * (forced true by [DownloadFailurePolicy.decideForStatus] for a transient
 * 5xx/429, to break a stale-`Range` retry loop) the partial is *not* a safe
 * resume point: it is deleted and the row is zeroed, exactly as the
 * multi-connection applicator does. The two sides must agree — if the file
 * is gone but the row still holds the old byte count, the next run sends
 * `Range: bytes=N-`, appends a tail to a fresh file, and ships a truncated
 * download as COMPLETED.
 *
 * @param preservedBytes the byte count to write back to the row when the
 *   partial is kept. Inner catch passes `downloadedBytes`; outer (pre-body)
 *   catches pass `existingBytes`.
 */
suspend fun Outcome.applyTo(
    dao: DownloadDao,
    downloadId: String,
    partialFile: File,
    preservedBytes: Long,
) = applyOutcome(this, dao, downloadId, partialFile, preservedBytes, deleteOnPause = false)

/**
 * Multi-connection applicator: the scattered `RandomAccessFile` offsets are
 * never a valid resume prefix, so the partial is deleted and bytes reset to 0
 * on every outcome that mutates the row — including a network pause, where the
 * single-connection path keeps its prefix. [partialFile] is the on-disk target.
 */
suspend fun Outcome.applyTo(
    dao: DownloadDao,
    downloadId: String,
    partialFile: File,
) = applyOutcome(this, dao, downloadId, partialFile, preservedBytes = 0L, deleteOnPause = true)

/**
 * The shared applicator core both strategies delegate to. Decides whether the
 * partial file is wiped and what byte count is persisted, then hands the DAO
 * writes to [applyDaoWrites].
 *
 * - Single-connection (`deleteOnPause = false`): the contiguous prefix is a
 *   resume point, so a [Outcome.RecordPause] keeps it; only a
 *   [Outcome.MarkFailed] that asks for `deletePartial` wipes + zeroes.
 * - Multi-connection (`deleteOnPause = true`): the scattered offsets are never
 *   resumable, so even a network pause wipes + zeroes.
 */
private suspend fun applyOutcome(
    outcome: Outcome,
    dao: DownloadDao,
    downloadId: String,
    partialFile: File,
    preservedBytes: Long,
    deleteOnPause: Boolean,
) {
    val delete = when (outcome) {
        Outcome.Suppress -> false
        is Outcome.RecordPause -> deleteOnPause
        is Outcome.MarkFailed -> outcome.deletePartial
    }
    if (delete) deletePartialFile(partialFile)
    val bytes = if (delete) 0L else preservedBytes
    outcome.applyDaoWrites(dao, downloadId, bytes)
}

/**
 * The shared status/reason/error/retry write sequence. Both applicators differ
 * only in the byte count they persist (single: preserved unless deleted; multi:
 * always 0) and whether they touch the partial file — the DAO writes are
 * identical, so live once here. [RecordPause] gates its retry increment on
 * [Outcome.shouldRetry] to stay symmetric with [Outcome.MarkFailed].
 *
 * [errorMessage] is always written, even when null: a transient retry
 * (`MarkFailed(errorMessage = null)`) must clear a stale prior message (e.g. a
 * SESSION_EXPIRED left from an earlier attempt) so the UI doesn't keep showing
 * it after the row is retried. This keeps the applicator as the single owner
 * of the error-message column — callers no longer clear it themselves.
 */
private suspend fun Outcome.applyDaoWrites(
    dao: DownloadDao,
    downloadId: String,
    bytes: Long,
) {
    when (this) {
        Outcome.Suppress -> Unit
        is Outcome.RecordPause -> {
            dao.updateProgressWithSpeed(downloadId, bytes, DownloadStatus.PAUSED.name, 0L)
            dao.updatePausedReason(downloadId, reason.persistedValue)
            if (shouldRetry) dao.incrementRetryCount(downloadId)
        }
        is Outcome.MarkFailed -> {
            dao.updateProgressWithSpeed(downloadId, bytes, DownloadStatus.FAILED.name, 0L)
            dao.updatePausedReason(downloadId, null)
            dao.updateErrorMessage(downloadId, errorMessage)
            if (shouldRetry) dao.incrementRetryCount(downloadId)
        }
    }
}

/** Best-effort partial-file delete; logs on failure (was inlined at every catch site). */
private fun deletePartialFile(file: File) {
    runCatching { if (file.exists()) file.delete() }
        .onFailure { Log.w("DownloadFailurePolicy", "Failed to delete partial", it) }
}
