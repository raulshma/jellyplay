package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore

/**
 * Thin Android adapter between WorkManager and the shared
 * [AutoDownloadCheck] — the check choreography itself (prefs gate,
 * single-query series index, per-season intake, transient-failure retry
 * budget) moved verbatim to :shared:core:data so Android and desktop check
 * through one code path (desktop previously ported this body verbatim).
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
    downloadIntake: DownloadIntake,
    downloadsStore: DownloadsStore,
) : CoroutineWorker(context, params) {

    private val check = AutoDownloadCheck(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        isStopped = { isStopped },
    )

    override suspend fun doWork(): Result =
        when (check.checkOnce(attempt = runAttemptCount)) {
            AutoDownloadCheck.Outcome.Complete -> Result.success()
            AutoDownloadCheck.Outcome.RetriesPending -> Result.retry()
            AutoDownloadCheck.Outcome.Exhausted -> Result.failure()
        }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.auto_download"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.auto_download_now"
        const val WORK_TAG = "auto_download"
    }
}
