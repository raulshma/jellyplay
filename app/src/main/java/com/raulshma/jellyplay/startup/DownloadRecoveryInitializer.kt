package com.raulshma.jellyplay.startup

import com.raulshma.jellyplay.core.data.repository.DownloadRecoveryPort

/**
 * Cold-start download recovery invocation shell. Extracted out of
 * `JellyPlayApplication` so the Application class *composes* startup steps
 * rather than *containing* them; the recovery POLICY itself (reconcile
 * completed rows against the filesystem, reset stuck PENDING/DOWNLOADING/
 * QUEUED transitions, clean up orphaned partial bytes) moved down into
 * :shared:core:data as [DownloadRecoveryPort] — the row-state machine stays
 * with the download engine that owns it, and this module no longer touches
 * the DAO.
 *
 * This class keeps only the startup contract (the
 * CacheMaintenanceInitializer idiom): Koin injects the port, the startup
 * choreography (AppStartupPrewarms' best-effort recovery-and-sweep group)
 * runs [recover] on the application scope/IO dispatcher, and the port's own
 * per-pass exception swallowing preserves the best-effort startup-appropriate
 * behavior — a recovery failure can never crash or delay first frame.
 */
class DownloadRecoveryInitializer (
    private val recovery: DownloadRecoveryPort,
) {
    suspend fun recover() = recovery.recover()
}
