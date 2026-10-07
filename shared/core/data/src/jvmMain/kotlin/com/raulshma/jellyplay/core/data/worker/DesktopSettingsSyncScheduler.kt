package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Desktop settings/profile sync flush trigger (the desktop actual of the
 * [SettingsSyncScheduler] seam — the [DesktopPlaybackSyncScheduler] twin,
 * in-process replacement for Android's WorkManager [SettingsSyncWorker]):
 *
 *  - one flush at [start] (the app-start catch-up Android's trigger covers),
 *  - on every going-online edge via the shared [ReconnectTrigger],
 *  - on [enqueueNow] (the dirty-write signal's desktop half),
 *  - on [onWindowFocus] — the desktop's app-background equivalent (ADR 0011):
 *    the window regaining focus is the "the user is back" edge, so changes
 *    made here flush shortly after; a cheap time-debounce keeps rapid
 *    alt-tabbing from stampeding the engine (whose own mutex would serialize
 *    the cycles anyway).
 *
 * [enqueuePeriodicIfEnabled] stays a deliberate no-op: there is no
 * WorkManager-style periodic backstop in process — the same declared delta
 * the playback scheduler's desktop twin documents (the reconnect edge is the
 * trigger the backstop exists to back up, and it is handled in-process here).
 * The engine's own gates (opt-in, probe, mutex) stay authoritative; every
 * flush is fire-and-forget.
 */
class DesktopSettingsSyncScheduler(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    /** The process-wide application scope (DatastoreQualifiers.applicationScope in Koin). */
    private val scope: CoroutineScope,
    private val flush: suspend () -> Unit,
) : SettingsSyncScheduler {

    /**
     * The going-online edge detector — the ONE trigger choreography. [start]'s
     * one-shot startup flush is gated on the trigger's idempotent return.
     */
    private val reconnectTrigger = ReconnectTrigger(
        networkMonitor = networkMonitor,
        offlineModeManager = offlineModeManager,
        scope = scope,
        tag = TAG,
        onReady = { flush("reconnect") },
    )

    /** The last focus-flush's monotonic stamp — the alt-tab debounce's clock. */
    @Volatile
    private var lastFocusFlushNanos: Long = Long.MIN_VALUE

    /**
     * Launches the startup flush and arms the reconnect edge. Idempotent,
     * mirroring [DesktopPlaybackSyncScheduler.start]; the composition root
     * resolves + start()s it after startKoin.
     */
    fun start() {
        if (reconnectTrigger.start()) {
            scope.launch { flush("startup") }
        }
    }

    override fun enqueueNow() {
        scope.launch { flush("dirty-write") }
    }

    override fun enqueuePeriodicIfEnabled() {
        // See the class KDoc: no in-process periodic backstop on desktop.
    }

    override fun cancelPeriodic() {
        // Nothing pinned — the desktop has no periodic backstop to de-arm.
    }

    /**
     * The window-focus flush (Main.kt's AWT `windowGainedFocus` hook):
     * debounced to one flush per [FOCUS_DEBOUNCE_MILLIS] — the first focus in
     * a burst flushes, the rest collapse.
     */
    fun onWindowFocus() {
        val now = System.nanoTime()
        val last = lastFocusFlushNanos
        val elapsedMillis = (now - last) / 1_000_000
        if (last != Long.MIN_VALUE && elapsedMillis < FOCUS_DEBOUNCE_MILLIS) return
        lastFocusFlushNanos = now
        scope.launch { flush("window-focus") }
    }

    private suspend fun flush(trigger: String) {
        try {
            flush.invoke()
            Log.d(TAG, "Settings sync flush ($trigger) requested")
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.w(TAG, "Settings sync flush ($trigger) failed", t)
        }
    }

    private companion object {
        const val TAG = "DesktopSettingsSync"

        /** Rapid alt-tab bursts collapse to one flush per window. */
        const val FOCUS_DEBOUNCE_MILLIS = 30_000L
    }
}
