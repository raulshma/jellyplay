package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Desktop auto-download scheduler (V3 downloads conveyor): the in-process
 * replacement for Android's periodic [AutoDownloadWorker] +
 * AutoDownloadScheduler WorkManager pair. One check runs at [start], then the
 * loop repeats every [CHECK_INTERVAL_MS] (6 h, matching the Android periodic
 * interval) with a small jitter so a fleet of desktop clients doesn't stampede
 * a server in lockstep.
 *
 * The check itself is the shared [AutoDownloadCheck] (same
 * `autoDownloadNewEpisodes` prefs gate, same single-query
 * getDownloadedEpisodeIdsBySeries index, same per-season
 * DownloadIntake.startSeries call, same transient-failure escalation);
 * `AutoDownloadScheduler.runCheck` used to port that body verbatim and is
 * gone.
 *
 * One deliberate difference from the Android worker: the in-process retry
 * ladder spaces its
 * passes by [DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS] directly —
 * the private `RETRY_DELAY_MS = 30_000L` re-declaration (comment-mirroring
 * the repository constant) is gone — and the exhausted-retries warning logs
 * under the shared core's tag instead of a desktop one. Otherwise only the
 * WorkManager result mapping became in-process retry passes: attempt counts
 * from 0 like the Android `runAttemptCount`, same [AutoDownloadCheck.MAX_RETRIES]
 * budget, give-up (not crash) at the cap, the ladder then waiting for the
 * next periodic tick.
 */
class DesktopAutoDownloadScheduler(
    downloadsStore: DownloadsStore,
    downloadRepository: DownloadRepository,
    downloadIntake: DownloadIntake,
    episodeCatalogue: EpisodeCatalogue,
    /** The process-wide application scope (DatastoreQualifiers.applicationScope in Koin). */
    private val scope: CoroutineScope,
) {
    private val check = AutoDownloadCheck(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        // The worker's `isStopped` twin: stop() cancels (and nulls) the loop
        // job, so the pass stops between series/seasons, not just between
        // passes. The check only ever runs inside that job, so reading the
        // field is the loop coroutine's own isActive.
        isStopped = { job?.isActive != true },
    )

    private var job: Job? = null

    /** Starts the periodic loop with one immediate check. Idempotent. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            runCheckWithRetries()
            while (currentCoroutineContext().isActive) {
                delay(CHECK_INTERVAL_MS + Random.nextLong(JITTER_MS))
                runCheckWithRetries()
            }
        }
    }

    /** Cancels the periodic loop (process shutdown / tests). */
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * One auto-download check with the WorkManager `Result.retry()` /
     * runAttemptCount semantics of the Android worker folded in-process: a
     * transient failure (a series' catalogue load failing) escalates up to
     * [AutoDownloadCheck.MAX_RETRIES] retry passes spaced by the shared
     * download backoff, then gives up until the next periodic tick.
     */
    private suspend fun runCheckWithRetries() {
        var attempt = 0
        while (true) {
            val outcome = check.checkOnce(attempt)
            if (outcome !is AutoDownloadCheck.Outcome.RetriesPending) return
            attempt++
            delay(DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS)
        }
    }

    private companion object {
        /** Android's AutoDownloadScheduler CHECK_INTERVAL — 6 h. */
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** De-sync multiple clients' check times (Android's flex window analogue). */
        const val JITTER_MS = 5L * 60 * 1000
    }
}
