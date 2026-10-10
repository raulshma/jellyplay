package com.raulshma.jellyplay.core.data.worker

/**
 * Schedules the settings/profile sync engine's background flush (ADR 0011's
 * background-sync trigger face). Defined as an interface (and consumed via
 * that interface) so callers don't reach into the concrete Android worker or
 * the desktop in-process scheduler — the [PlaybackSyncScheduler] DI-clean
 * pattern.
 *
 * Three entry points:
 *   - [enqueueNow]: one-shot dirty flush — enqueued on the app-background
 *     edge, the dirty-write signal, and the network reconnect; KEEP-idempotent,
 *     so repeated calls never stack runs.
 *   - [enqueuePeriodicIfEnabled]: the 12h catch-up backstop, armed only while
 *     the sync engine is enabled (the engine's own gate stays the ONE switch;
 *     this avoids pinning a useless periodic run for a user who never opted
 *     in).
 *   - [cancelPeriodic]: the backstop's de-arm — the disable edge of the same
 *     switch ("armed only while sync is enabled" cuts both ways); a canceled
 *     periodic re-arms from the next start/re-enable's
 *     [enqueuePeriodicIfEnabled].
 *
 * Platform actuals: Android runs [SettingsSyncWorker] through WorkManager
 * (`SettingsSyncSchedulerImpl`); desktop flushes in-process
 * (`DesktopSettingsSyncScheduler`, the [DesktopPlaybackSyncScheduler] twin —
 * where the periodic backstop is a no-op and the window-focus edge replaces
 * the app-background one).
 */
interface SettingsSyncScheduler {
    fun enqueueNow()
    fun enqueuePeriodicIfEnabled()
    fun cancelPeriodic()
}
