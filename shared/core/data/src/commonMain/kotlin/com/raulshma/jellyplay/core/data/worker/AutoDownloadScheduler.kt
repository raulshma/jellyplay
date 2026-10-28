package com.raulshma.jellyplay.core.data.worker

/**
 * Schedules the auto-download background check that fetches new episodes of
 * series the user has already downloaded from. Defined as an interface (and
 * consumed via that interface) so callers don't reach into the concrete
 * Android worker or the desktop in-process scheduler — the
 * [PlaybackSyncScheduler] DI-clean pattern.
 *
 * Two entry points:
 *   - [sync]: the keep-alive reconcile — the schedule stays active while the
 *     `autoDownloadMode` preference is enabled **or** any series carries a
 *     per-series override (gh-181); only when both are dead is the periodic
 *     schedule cancelled. Safe to call repeatedly.
 *   - [checkNow]: the one-shot off-cycle check — the storage screen's
 *     "Check for new episodes now", the app-foreground edge, and the
 *     post-library-scan hook. Each platform states its own gate and
 *     serialization (Android's KEEP one-shot; the desktop pass runs beside
 *     the 6 h loop).
 *
 * Platform actuals: Android runs `AutoDownloadWorker` through WorkManager
 * (`AutoDownloadSchedulerImpl`, androidMain); desktop checks in-process
 * (`DesktopAutoDownloadScheduler` — its 6 h loop replacing the WorkManager
 * periodic arm, the `DesktopPlaybackSyncScheduler` twin).
 */
interface AutoDownloadScheduler {
    fun sync()
    fun checkNow()
}
