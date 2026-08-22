package com.raulshma.jellyplay.core.data.repository

// C4p2 note: the pure failure-classification rule + DAO applicators moved to
// :shared:core:data jvmShared; these two extensions stay in the legacy module
// because they map an Outcome onto androidx.work ListenableWorker results,
// which is an Android-only surface owned by the worker paths.

import com.raulshma.jellyplay.core.database.dao.DownloadDao
import java.io.File

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

/**
 * Applies [this] outcome to the single-connection row and maps it to a
 * WorkManager [androidx.work.ListenableWorker.Result] in one call — the
 * two-step every single-connection failure site used to open-code. Collapses
 * the four divergent `applyTo` + `toWorkResult` pairs (runner non-2xx,
 * `routeThrowable`, `sizeMismatch`, worker outer catch) onto one path, so the
 * DAO write and the WorkManager result can't drift apart.
 */
suspend fun Outcome.applyAndRoute(
    dao: DownloadDao,
    downloadId: String,
    partialFile: File,
    preservedBytes: Long,
): androidx.work.ListenableWorker.Result {
    applyTo(dao, downloadId, partialFile, preservedBytes)
    return toWorkResult()
}
