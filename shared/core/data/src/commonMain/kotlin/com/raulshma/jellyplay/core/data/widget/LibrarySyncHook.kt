package com.raulshma.jellyplay.core.data.widget

/**
 * Fired after a successful foreground library scan / home refresh so downstream
 * one-shot triggers can piggy-back on the same signal:
 *   - the auto-download foreground drain ([com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler.enqueueNow])
 *   - the widget recommendations refresh ([com.raulshma.jellyplay.widget.WidgetWorkScheduler])
 *
 * Mirrors the `ContinueWatchingBroadcaster` DI-clean pattern: the interface
 * lives in `core/data` (reachable from `feature/home`), the wiring lives in
 * `app` where both schedulers are available.
 */
interface LibrarySyncHook {
    /** Best-effort; implementations must swallow their own failures. */
    suspend fun onLibraryScanComplete()
}
