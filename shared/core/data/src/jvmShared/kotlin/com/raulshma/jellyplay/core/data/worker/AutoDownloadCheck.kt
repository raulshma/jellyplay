package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import kotlinx.coroutines.flow.firstOrNull

/**
 * The auto-download check choreography: one pass that finds new episodes of
 * series the user has already downloaded from and hands them to
 * [DownloadIntake.startSeries] per season, gated on the
 * `autoDownloadNewEpisodes` preference and respecting the WiFi-only /
 * storage-limit constraints enforced inside [DownloadRepository].
 *
 * Previously this lived twice: verbatim in the legacy Android
 * `core.data.worker.AutoDownloadWorker.doWork`, and ported verbatim in
 * `DesktopAutoDownloadScheduler.runCheck` (whose own KDoc admitted the
 * copy). The move is a body move (same gate, same single-query series index,
 * same per-season intake, same transient-failure escalation) so both
 * platforms check through ONE code path; the adapters own only the
 * scheduling shells — Android's WorkManager periodic/enqueueNow pair and
 * the desktop's in-process 6 h loop (see each adapter's KDoc).
 *
 * Failures are observable: a per-series catalogue load failure does not
 * abort the pass (partial success is preserved; that series is skipped), but
 * it escalates the outcome to [Outcome.RetriesPending] so the caller comes
 * back, and to [Outcome.Exhausted] once the [MAX_RETRIES] budget is spent.
 * Previously the Android worker returned `Result.success()` unconditionally,
 * leaving a persistently broken server path invisible to operators.
 *
 * Seasons + episodes come from [EpisodeCatalogue.loadSeriesEpisodes] — a
 * single consolidated snapshot per series instead of a separate `getSeasons`
 * + per-season `getEpisodes` fan-out. This path runs online-only, so
 * `offline` defaults to `false`.
 */
class AutoDownloadCheck(
    private val downloadsStore: DownloadsStore,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    private val episodeCatalogue: EpisodeCatalogue,
    /**
     * Cancellation seam (the ScanWorkerHelper pattern): folds the Android
     * worker's `isStopped` and the desktop loop's `isActive` into one check,
     * polled before each series and each season so a stopped caller stops
     * mid-pass instead of queueing downloads for a dead shell.
     */
    private val isStopped: () -> Boolean = { false },
) {

    /**
     * Runs one check pass. [attempt] is the retry-budget input (the Android
     * adapter passes WorkManager's `runAttemptCount`; the desktop ladder
     * counts its own in-process passes from 0).
     */
    suspend fun checkOnce(attempt: Int): Outcome {
        val prefs = downloadsStore.downloads.firstOrNull()
        if (prefs == null || !prefs.autoDownloadNewEpisodes) return Outcome.Complete

        val seriesIds = downloadRepository.getDownloadedSeriesIds()
        if (seriesIds.isEmpty()) return Outcome.Complete

        // Fetch every series' downloaded episode ids in a single 2-column query
        // (grouped by seriesId) instead of issuing one full-row query per series
        // inside the loop below. A 100-episode series previously decoded ~100
        // rows × 23 columns per iteration; this reads 2 columns, once.
        val downloadedEpisodeIdsBySeries = downloadRepository.getDownloadedEpisodeIdsBySeries()

        var hadTransientFailure = false
        for (seriesId in seriesIds) {
            if (isStopped()) break
            val alreadyDownloaded = downloadedEpisodeIdsBySeries[seriesId].orEmpty()
            // One consolidated load per series: seasons + every season's episodes
            // in a single snapshot, replacing the prior getSeasons + per-season
            // getEpisodes fan-out. A catalogue failure is a transient error —
            // this series is skipped but the run continues and the outcome
            // escalates to RetriesPending below.
            val snapshot = episodeCatalogue.loadSeriesEpisodes(seriesId).getOrElse {
                hadTransientFailure = true
                continue
            }
            for (season in snapshot.seasons) {
                if (isStopped()) break
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

        if (!hadTransientFailure) return Outcome.Complete
        return if (attempt < MAX_RETRIES) {
            Outcome.RetriesPending
        } else {
            Log.w(TAG, "AutoDownload exhausted $MAX_RETRIES retries")
            Outcome.Exhausted
        }
    }

    /**
     * Outcome of one [checkOnce] pass — what the platform adapter decides on.
     * The retry-budget comparison is applied HERE (one place owns
     * [MAX_RETRIES]); the adapters only map the arms.
     */
    sealed interface Outcome {

        /**
         * The gate was off, there was nothing downloaded to check, or every
         * catalogue load landed. (Android mapping: `Result.success()`;
         * desktop: the ladder returns until the next periodic pass.)
         */
        data object Complete : Outcome

        /**
         * At least one series' catalogue load failed while still under
         * [MAX_RETRIES] — the caller should come back for another pass.
         * (Android mapping: `Result.retry()`; desktop: the in-process
         * backoff-spaced retry pass.)
         */
        data object RetriesPending : Outcome

        /**
         * At least one series' catalogue load failed and the [MAX_RETRIES]
         * budget is spent — no further passes. (Android mapping:
         * `Result.failure()`; desktop: the ladder gives up until the next
         * periodic pass.)
         */
        data object Exhausted : Outcome
    }

    companion object {
        private const val TAG = "AutoDownloadCheck"

        /**
         * Retry budget for transient catalogue-load failures before the check
         * escalates to [Outcome.Exhausted]. The ONE declaration behind the
         * Android worker's retry()/failure() escalation (was
         * `AutoDownloadWorker.MAX_RETRIES`) and the desktop ladder's give-up
         * (was `DesktopAutoDownloadScheduler.MAX_RETRIES`).
         */
        const val MAX_RETRIES = 3
    }
}
