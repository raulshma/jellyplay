package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore

/**
 * Thin Android adapter between WorkManager and the shared
 * [AutoDownloadCheck] — the check choreography itself (prefs gate,
 * retention-policy bounds, single-query series index, per-season intake,
 * transient-failure retry budget) moved verbatim to :shared:core:data so
 * Android and desktop check through one code path (desktop previously ported
 * this body verbatim).
 *
 * Triggered periodically and on demand by [AutoDownloadScheduler]; the worker
 * respects the WiFi-only and storage-limit constraints enforced inside
 * [DownloadRepository].
 *
 * This class owns only what is WorkManager-shaped: the attempt counter
 * ([runAttemptCount], the check's retry-budget input) and the retry()/
 * success()/failure() mapping of the check's [AutoDownloadCheck.Outcome].
 */
class AutoDownloadWorker(
    context: Context,
    params: WorkerParameters,
    episodeCatalogue: EpisodeCatalogue,
    downloadRepository: DownloadRepository,
    policyStore: SeriesDownloadPolicyStore,
    downloadIntake: DownloadIntake,
    downloadsStore: DownloadsStore,
    serverIdentityStore: ServerIdentityStore,
    /** gh-181: the Ask-mode suggestion shade summary (Android posts, desktop no-op). */
    private val suggestionNotifier: AutoDownloadSuggestionNotifier,
) : CoroutineWorker(context, params) {

    private val check = AutoDownloadCheck(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        policyStore = policyStore,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        serverIdentityStore = serverIdentityStore,
        isStopped = { isStopped },
    )

    override suspend fun doWork(): Result =
        when (val outcome = check.checkOnce(attempt = runAttemptCount)) {
            is AutoDownloadCheck.Outcome.Complete -> {
                // Best-effort: a failed post never fails the pass (the
                // suggestion card on the Downloads screen is the source of
                // truth either way).
                runCatchingRethrowingCancellation {
                    if (outcome.newSuggestions > 0) suggestionNotifier.notifyNewSuggestions(outcome.newSuggestions)
                }
                Result.success()
            }
            AutoDownloadCheck.Outcome.RetriesPending -> Result.retry()
            AutoDownloadCheck.Outcome.Exhausted -> Result.failure()
        }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.auto_download"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.auto_download_now"
        const val WORK_TAG = "auto_download"
    }
}
