package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
 * The check itself ports AutoDownloadWorker.doWork verbatim (same
 * `autoDownloadNewEpisodes` prefs gate, same single-query
 * getDownloadedEpisodeIdsBySeries index, same per-season
 * DownloadIntake.startSeries call, same transient-failure → retry semantics
 * with MAX_RETRIES = 3); only the WorkManager result mapping became in-process
 * retry passes.
 */
class DesktopAutoDownloadScheduler(
    private val downloadsStore: DownloadsStore,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    private val episodeCatalogue: EpisodeCatalogue,
    /** The process-wide application scope (DatastoreQualifiers.applicationScope in Koin). */
    private val scope: CoroutineScope,
) {
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
     * One auto-download check. A transient failure (a series' catalogue load
     * failing) escalates up to [MAX_RETRIES] in-process retry passes spaced by
     * the shared download backoff — the WorkManager `Result.retry()` /
     * runAttemptCount semantics of the Android worker.
     */
    private suspend fun runCheckWithRetries() {
        var attempt = 0
        while (true) {
            val hadTransientFailure = runCheck()
            if (!hadTransientFailure) return
            if (attempt >= MAX_RETRIES) {
                Log.w(TAG, "AutoDownload exhausted $MAX_RETRIES retries")
                return
            }
            attempt++
            delay(RETRY_DELAY_MS)
        }
    }

    /** Returns true when any series' catalogue load failed (transient). */
    private suspend fun runCheck(): Boolean {
        val prefs = downloadsStore.downloads.first()
        if (!prefs.autoDownloadNewEpisodes) return false

        val seriesIds = downloadRepository.getDownloadedSeriesIds()
        if (seriesIds.isEmpty()) return false

        // Fetch every series' downloaded episode ids in a single 2-column query
        // (grouped by seriesId) instead of issuing one full-row query per series
        // inside the loop below (same rationale as the Android worker).
        val downloadedEpisodeIdsBySeries = downloadRepository.getDownloadedEpisodeIdsBySeries()

        var hadTransientFailure = false
        for (seriesId in seriesIds) {
            if (!currentCoroutineContext().isActive) break
            val alreadyDownloaded = downloadedEpisodeIdsBySeries[seriesId].orEmpty()
            // One consolidated load per series: seasons + every season's episodes
            // in a single snapshot. A catalogue failure is a transient error — map
            // it to an empty snapshot so this series is skipped but the run
            // continues and escalates retry → give-up below.
            val snapshot = episodeCatalogue.loadSeriesEpisodes(seriesId).getOrElse {
                hadTransientFailure = true
                continue
            }
            for (season in snapshot.seasons) {
                if (!currentCoroutineContext().isActive) break
                val newEpisodeIds = snapshot.seasonEpisodes(season.id)
                    .filter { it.id !in alreadyDownloaded }
                    .map { it.id }
                if (newEpisodeIds.isNotEmpty()) {
                    downloadIntake.startSeries(
                        seriesId = seriesId,
                        episodeIds = mapOf(season.id to newEpisodeIds),
                    )
                }
            }
        }
        return hadTransientFailure
    }

    private companion object {
        const val TAG = "DesktopAutoDownload"

        /** Android's AutoDownloadScheduler CHECK_INTERVAL — 6 h. */
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** De-sync multiple clients' check times (Android's flex window analogue). */
        const val JITTER_MS = 5L * 60 * 1000

        /** AutoDownloadWorker.MAX_RETRIES. */
        const val MAX_RETRIES = 3

        /** Shared download backoff base (DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS). */
        const val RETRY_DELAY_MS = 30_000L
    }
}
