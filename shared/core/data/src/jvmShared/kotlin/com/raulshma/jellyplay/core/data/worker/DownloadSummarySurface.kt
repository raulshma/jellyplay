package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The download notification-group summary's in-flight count as ONE observed
 * flow with a single collector through the [DownloadProgressNotifier] port
 * (the former seven restatements — a COUNT query + refreshSummary call —
 * across DownloadWorker's queued/promotion/dismiss lambdas, the repository's
 * in-app state changes, and the transfer gate/strategy's lifecycle points,
 * per-tick on the single-connection path). The DAO writes those call sites
 * already perform are what drive the summary now: Room invalidation re-runs
 * the count query, [distinctUntilChanged] collapses same-count re-emissions,
 * and only a genuine transition reaches the port.
 *
 * The port is the SAME seam the repository used — Android's adapter lands on
 * `DownloadNotificationHelper.refreshSummary` (whose same-count memo + cancel
 * at zero semantics are unchanged, still behind the port), desktop's binding
 * is the no-op. Best-effort by the port's contract: a failed post must not
 * kill the collector, so the post is guarded per emission.
 *
 * Process-scoped by construction: the Koin single starts this collector at
 * app start on the application scope — download lifecycles span screens and
 * workers, so no screen/worker can own it.
 */
class DownloadSummarySurface(
    inFlightCount: Flow<Int>,
    notifier: DownloadProgressNotifier,
    scope: CoroutineScope,
) {

    /** The one collector; lives as long as the application scope. */
    val job: Job = scope.launch {
        inFlightCount
            .distinctUntilChanged()
            .collect { count ->
                runCatchingRethrowingCancellation { notifier.refreshSummary(count) }
            }
    }
}
