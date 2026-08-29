package com.raulshma.jellyplay.core.data.worker

/**
 * Schedules a one-shot refresh of the Android TV "Watch Next" OS row.
 *
 * Defined as an interface (and consumed by feature modules via that
 * interface) so feature code doesn't reach into the `core.data.worker`
 * package's concrete `TvWatchNextWorker` class. The worker remains an
 * internal scheduling detail of the legacy `:core:data`.
 *
 * TV-only behaviour lives inside the worker itself: it's a no-op on
 * phones and respects the `androidTvWatchNextEnabled` preference.
 *
 * One-shot by design: there is no periodic schedule; see `TvWatchNextWorker`
 * for the rationale.
 *
 * Home conveyor (plan §Phase X cutover): the interface was split out of the
 * legacy `worker/TvWatchNextScheduler.kt` (same package) so the shared home
 * feature's commonMain can reference it — the PlaybackSyncScheduler
 * precedent. The WorkManager-backed `TvWatchNextSchedulerImpl` stays in the
 * legacy `:core:data` as a Koin single in androidCoreDataModule.
 */
interface TvWatchNextScheduler {
    fun scheduleRefresh()
}
