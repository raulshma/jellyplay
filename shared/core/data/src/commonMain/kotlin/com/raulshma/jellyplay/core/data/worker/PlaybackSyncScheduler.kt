package com.raulshma.jellyplay.core.data.worker

/**
 * Schedules the playback-progress offline outbox drain. Defined as an
 * interface (and consumed via that interface) so callers don't reach into
 * the concrete WorkManager worker, mirroring the [UserDataSyncScheduler]
 * DI-clean pattern.
 *
 * Two entry points:
 *   - [enqueuePeriodic]: long-interval backstop so queued progress eventually
 *     flushes even if the reconnect signal is missed.
 *   - [enqueueNow]: immediate one-shot drain, called on the Offline→Online
 *     transition and at app start.
 *
 * C4 part 2: the interface was split out of the legacy
 * `worker/PlaybackSyncScheduler.kt` so `SyncStatusStateHolder` (jvmShared)
 * can reference it. Platform actuals: the WorkManager-backed
 * `PlaybackSyncSchedulerImpl` stays in the legacy `:core:data` (workers are
 * Android-side per plan ) as a Koin single in
 * androidCoreDataModule; desktop runs the in-process
 * [DesktopPlaybackSyncScheduler] (shared jvmMain) once the playback-outbox
 * drainer moved into jvmShared — its outbox drains no longer sit.
 */
interface PlaybackSyncScheduler {
    fun enqueuePeriodic()
    fun enqueueNow()
}
