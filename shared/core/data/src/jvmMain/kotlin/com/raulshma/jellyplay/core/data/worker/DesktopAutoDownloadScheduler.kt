package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Desktop auto-download scheduler (V3 downloads conveyor): the in-process
 * replacement for Android's periodic `AutoDownloadWorker` +
 * `AutoDownloadSchedulerImpl` WorkManager pair, and the desktop actual of the
 * [AutoDownloadScheduler] interface. One check runs at [start], then the loop
 * repeats every [CHECK_INTERVAL_MS] (6 h, matching the Android periodic
 * interval) with a small jitter so a fleet of desktop clients doesn't stampede
 * a server in lockstep.
 *
 * The check itself is the shared [AutoDownloadCheck] (same
 * `autoDownloadMode` prefs gate, same single-query
 * getDownloadedEpisodeIdsBySeries index, same per-season
 * DownloadIntake.startSeries call, same transient-failure escalation); the
 * legacy Android scheduler's `runCheck` used to port that body verbatim and
 * is gone.
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
    policyStore: SeriesDownloadPolicyStore,
    downloadIntake: DownloadIntake,
    episodeCatalogue: EpisodeCatalogue,
    /** Active-server identity for the check's per-user allow-list gate. */
    serverIdentityStore: ServerIdentityStore,
    /** The process-wide application scope (DatastoreQualifiers.applicationScope in Koin). */
    private val scope: CoroutineScope,
) : AutoDownloadScheduler {
    private val check = AutoDownloadCheck(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        policyStore = policyStore,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        serverIdentityStore = serverIdentityStore,
        // The worker's `isStopped` twin: the pass reads the liveness of the
        // job that spawned it ([activePass] — the loop job for periodic
        // passes, so stop() stops the pass between series/seasons, not just
        // between passes; the manual check's own job for a checkNow pass,
        // which nothing cancels short of process exit).
        isStopped = { activePass?.isActive != true },
    )

    private var job: Job? = null

    /**
     * Serializes passes — the WorkManager `ExistingWorkPolicy.KEEP` equivalent
     * ([DesktopPlaybackSyncScheduler]'s drain-mutex idiom), so a manual check
     * racing the loop's tick waits for it instead of stampeding a second
     * concurrent pass.
     */
    private val checkMutex = Mutex()

    /** The pass currently holding [checkMutex] — the check's isStopped seam. */
    private var activePass: Job? = null

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

    override fun sync() {
        // The keep-alive poke IS the loop (re)arm: start() is idempotent and
        // its immediate first check honours a freshly toggled auto-download
        // preference right away.
        start()
    }

    /**
     * One off-cycle check ([AutoDownloadScheduler.checkNow] — the storage
     * screen's "Check for new episodes now"): a fire-and-forget pass on the
     * application scope, the manual-drain idiom of the desktop sync
     * schedulers. It never touches the loop job's cadence, and [checkMutex]
     * serializes it against the loop's pass — a manual check landing
     * mid-tick waits for the tick's pass, then runs its own.
     */
    override fun checkNow() {
        scope.launch { runCheckWithRetries() }
    }

    /**
     * One auto-download check with the WorkManager `Result.retry()` /
     * runAttemptCount semantics of the Android worker folded in-process: a
     * transient failure (a series' catalogue load failing) escalates up to
     * [AutoDownloadCheck.MAX_RETRIES] retry passes spaced by the shared
     * download backoff, then gives up until the next periodic tick. The pass
     * publishes itself as [activePass] under [checkMutex] before the first
     * check so the isStopped seam reads the right spawning job.
     */
    private suspend fun runCheckWithRetries() {
        checkMutex.withLock {
            activePass = currentCoroutineContext().job
            var attempt = 0
            while (true) {
                val outcome = check.checkOnce(attempt)
                if (outcome !is AutoDownloadCheck.Outcome.RetriesPending) return
                attempt++
                delay(DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS)
            }
        }
    }

    private companion object {
        /** Android's AutoDownloadScheduler CHECK_INTERVAL — 6 h. */
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** De-sync multiple clients' check times (Android's flex window analogue). */
        const val JITTER_MS = 5L * 60 * 1000
    }
}
