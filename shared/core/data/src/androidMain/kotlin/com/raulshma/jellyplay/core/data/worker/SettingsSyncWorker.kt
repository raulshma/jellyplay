package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository

/**
 * The settings/profile sync engine's background flush (ADR 0011): one
 * WorkManager one-shot running [ProfileSyncRepository.requestSync] — the
 * engine's own gates (opt-in toggle, availability probe, per-namespace
 * selective sync, cycle mutex) stay authoritative, so a disabled or
 * plugin-absent run is a cheap no-op and a Result.success either way (a
 * failed cycle surfaces in the sync screen's error row, never as a WorkManager
 * retry loop).
 *
 * Triggered (KEEP-idempotent, never expedited — house convention):
 *   - on the app-background edge + the network reconnect edge
 *     ([SettingsSyncBackgroundTrigger]),
 *   - on dirty writes in the syncable stores (the repositories' `onDirty`
 *     seams),
 *   - periodically (12h catch-up) while sync is enabled
 *     ([SettingsSyncSchedulerImpl.enqueuePeriodicIfEnabled]).
 *
 * (The live face — the settings SSE stream folding into requestSync — rides
 * JellyPlayLiveResyncConnector; the push face rides the `sync-nudge` silent
 * push. This worker covers the windows neither reaches: process-alive but
 * screen-off, no SSE, no reachable nudge.)
 */
class SettingsSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncRepository: ProfileSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        syncRepository.requestSync()
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.settings_sync_periodic"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.settings_sync_now"
        const val WORK_TAG = "settings_sync"

        /** The catch-up backstop cadence (ADR 0011: optional 12h periodic). */
        val PERIODIC_INTERVAL: java.time.Duration = java.time.Duration.ofHours(12)
    }
}
