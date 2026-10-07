package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository

/**
 * The WorkManager-backed [SettingsSyncScheduler] (the
 * [PlaybackSyncSchedulerImpl] mirror): a CONNECTED-constrained KEEP one-shot
 * per flush ([enqueueNow], never expedited — house convention) plus the 12h
 * catch-up periodic ([enqueuePeriodicIfEnabled]), armed only while the sync
 * engine is enabled — the engine's own toggle stays the ONE switch; a user
 * who never opted in pins no periodic run. Re-enabling sync re-arms here (the
 * app-start/reconnect trigger's next pass re-checks), and the live SSE
 * connector covers the enabled foreground regardless.
 */
class SettingsSyncSchedulerImpl(
    private val context: Context,
    private val syncRepository: ProfileSyncRepository,
) : SettingsSyncScheduler {

    override fun enqueueNow() {
        UniqueWorkSchedules.uniqueOnce<SettingsSyncWorker>(
            context = context,
            uniqueName = SettingsSyncWorker.UNIQUE_NOW_NAME,
            tag = SettingsSyncWorker.WORK_TAG,
        )
    }

    override fun enqueuePeriodicIfEnabled() {
        // The engine's gate re-read at arm time (the state's enabled edge is
        // authoritative — reloadPersistedEnabled has settled by the time any
        // background trigger runs, which is deferred 2s past app start).
        if (!syncRepository.state.value.enabled) return
        UniqueWorkSchedules.uniquePeriodic<SettingsSyncWorker>(
            context = context,
            uniqueName = SettingsSyncWorker.UNIQUE_PERIODIC_NAME,
            tag = SettingsSyncWorker.WORK_TAG,
            interval = SettingsSyncWorker.PERIODIC_INTERVAL,
        )
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME)
    }
}
