package com.raulshma.jellyplay.core.data.repository

import android.util.Log
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.model.DownloadStatus
import java.io.File

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
 * **Scope.** Classifies *thrown exceptions* only. HTTP response-code branches
 * (416, 401/403, transient non-2xx) and integrity checks (size mismatch,
 * 0-byte result) stay inlined at their call sites — they have a `Response` or
 * a byte count in hand, not a `Throwable`, so a different input shape. Those
 * could be folded in later via a sum-type `FailureEvent`; YAGNI for now.
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
        // User paused while we were failing. Do not overwrite their PAUSED row.
        if (currentStatus == DownloadStatus.PAUSED.name) return Outcome.Suppress

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
 * Single-connection applicator: preserves the partial and the bytes written
 * this run (the partial is a contiguous prefix safe to resume from).
 *
 * @param preservedBytes the byte count to write back to the row. Inner catch
 *   passes `downloadedBytes`; outer (pre-body) catches pass `existingBytes`.
 */
suspend fun Outcome.applyTo(
    dao: DownloadDao,
    downloadId: String,
    preservedBytes: Long,
) = applyDaoWrites(dao, downloadId, preservedBytes)

/**
 * Multi-connection applicator: deletes the partial file and resets bytes to 0
 * when the outcome calls for it (the scattered RandomAccessFile offsets are
 * never a valid resume prefix). [partialFile] is the on-disk download target.
 */
suspend fun Outcome.applyTo(
    dao: DownloadDao,
    downloadId: String,
    partialFile: File,
) {
    // Multi-connection partial is never resumable — delete + reset even on a
    // network pause, matching the pre-extraction behaviour. MarkFailed honours
    // its own deletePartial flag (false only if a future caller sets it).
    when (this) {
        Outcome.Suppress -> Unit
        is Outcome.RecordPause -> deletePartialFile(partialFile)
        is Outcome.MarkFailed -> if (deletePartial) deletePartialFile(partialFile)
    }
    applyDaoWrites(dao, downloadId, 0L)
}

/**
 * The shared status/reason/error/retry write sequence. Both applicators differ
 * only in the byte count they persist (single: preserved; multi: 0 after
 * delete) and whether they touch the partial file — the DAO writes are
 * identical, so live once here. [RecordPause] gates its retry increment on
 * [Outcome.shouldRetry] to stay symmetric with [Outcome.MarkFailed].
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
            // Single-connection partial is resumable even on FAILED (the
            // retry decision re-evaluates via DownloadStates.resumeByteOffset).
            dao.updateProgressWithSpeed(downloadId, bytes, DownloadStatus.FAILED.name, 0L)
            dao.updatePausedReason(downloadId, null)
            if (errorMessage != null) dao.updateErrorMessage(downloadId, errorMessage)
            if (shouldRetry) dao.incrementRetryCount(downloadId)
        }
    }
}

/** Best-effort partial-file delete; logs on failure (was inlined at every catch site). */
private fun deletePartialFile(file: File) {
    runCatching { if (file.exists()) file.delete() }
        .onFailure { Log.w("DownloadFailurePolicy", "Failed to delete partial", it) }
}

/**
 * Maps an [Outcome] to a WorkManager [androidx.work.ListenableWorker.Result].
 * Kept as an extension on `Outcome` (not a member) so the pure policy module
 * doesn't import `androidx.work`; callers in the worker module use it.
 *
 * - [Outcome.RecordPause] / [Outcome.MarkFailed] with `shouldRetry = true` → `Result.retry()`
 * - otherwise → `Result.success()` for [Outcome.Suppress] (clean exit, no
 *   wasteful retry) or `Result.failure()` for terminal [Outcome.MarkFailed].
 */
fun Outcome.toWorkResult(): androidx.work.ListenableWorker.Result =
    if (shouldRetry) androidx.work.ListenableWorker.Result.retry()
    else if (this is Outcome.Suppress) androidx.work.ListenableWorker.Result.success()
    else androidx.work.ListenableWorker.Result.failure()
