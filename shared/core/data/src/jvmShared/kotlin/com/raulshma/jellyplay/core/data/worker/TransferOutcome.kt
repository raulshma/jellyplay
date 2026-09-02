package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.Outcome

/**
 * Portable transfer result for the download engine (V3 downloads conveyor).
 *
 * The pre-move [DownloadTransferRunner] / [MultiConnectionDownloadStrategy]
 * returned `androidx.work.ListenableWorker.Result` — an Android-only surface
 * that blocked the desktop port. This sealed type models exactly the three
 * values the engine actually produced (`Result.success()` / `Result.retry()` /
 * `Result.failure()`); the staying-legacy Android [DownloadWorker] maps it
 * back onto the WorkManager result, and the desktop manager consumes it
 * directly.
 */
sealed interface TransferOutcome {

    /**
     * The transfer ended without needing WorkManager re-enqueue — completed,
     * cooperatively paused/cancelled, or suppressed by a concurrent user
     * action. (Android mapping: `Result.success()`.)
     */
    data object Success : TransferOutcome

    /**
     * The row was failed-with-retry-budget by the failure policy; the caller
     * should re-run the transfer after the backoff delay. (Android mapping:
     * `Result.retry()`; desktop: the manager's in-process re-kick.)
     */
    data object Retry : TransferOutcome

    /**
     * Terminal failure — no retry (e.g. 401/403 session expiry, exhausted
     * non-retryable outcome). (Android mapping: `Result.failure()`.)
     */
    data object Fail : TransferOutcome
}

/**
 * Maps a failure-policy [Outcome] onto [TransferOutcome] — the portable
 * equivalent of the legacy `Outcome.toWorkResult()` androidx.work mapping
 * (which stays in the legacy module for the Android worker's outer catch).
 * Public since the move so the (now-public) strategy can reach it.
 */
fun Outcome.toTransferOutcome(): TransferOutcome =
    if (shouldRetry) {
        TransferOutcome.Retry
    } else if (this is Outcome.Suppress) {
        TransferOutcome.Success
    } else {
        TransferOutcome.Fail
    }
