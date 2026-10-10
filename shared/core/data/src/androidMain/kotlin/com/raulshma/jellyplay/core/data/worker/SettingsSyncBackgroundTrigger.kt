package com.raulshma.jellyplay.core.data.worker

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import kotlinx.coroutines.CoroutineScope

/**
 * The settings/profile sync engine's background trigger face (ADR 0011): arms
 * the two edges the live faces (SSE stream, sync-nudge push) cannot cover and
 * the one app-start catch-up arm:
 *
 *  - the APP-BACKGROUND edge ([ProcessLifecycleOwner]'s ON_STOP — the process
 *    stays alive with the screen gone, exactly when neither the SSE stream
 *    nor a visible sync screen can be trusted to run a cycle): one
 *    [SettingsSyncScheduler.enqueueNow] flush, so changes accumulated during
 *    the session leave the device promptly;
 *  - the NETWORK RECONNECT edge (the shared [ReconnectTrigger] — the same
 *    Offline/Local → Online detector the other listeners run): one flush so
 *    changes staged offline leave on the first online moment;
 *  - the app-start arm ([start]): re-arms the 12h catch-up periodic if sync
 *    is enabled (KEEP — re-arming never resets the schedule).
 *
 * Every enqueue collapses through the scheduler's KEEP policy; the engine's
 * own gates make a disabled run a no-op. The observer is registered on the
 * main thread per ProcessLifecycleOwner's contract — [start] must be called
 * from one (the app's startup block is).
 */
class SettingsSyncBackgroundTrigger(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val scheduler: SettingsSyncScheduler,
    private val scope: CoroutineScope,
) {
    private val reconnectTrigger = ReconnectTrigger(
        networkMonitor = networkMonitor,
        offlineModeManager = offlineModeManager,
        scope = scope,
        tag = TAG,
        onReady = { scheduler.enqueueNow() },
    )

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            scheduler.enqueueNow()
        }
    }

    /**
     * ProcessLifecycleOwner requires main-thread registration, but this
     * trigger starts from the deferred background-scheduler group
     * (Dispatchers.IO) — the registration posts to main, and a failed post
     * (test environments) costs only the background edge, never the process.
     */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Arms both edges + the periodic catch-up. Idempotent (the listener-start idiom). */
    fun start() {
        val started = reconnectTrigger.start()
        if (!started) return
        mainHandler.post {
            runCatching { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver) }
        }
        // The catch-up backstop's arm point: app start (deferred by the
        // caller's scheduler group), re-checked every start and every process
        // lifetime — uniquePeriodic's KEEP keeps repeated arms free.
        scheduler.enqueuePeriodicIfEnabled()
    }

    fun stop() {
        reconnectTrigger.stop()
        mainHandler.post {
            runCatching { ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver) }
        }
    }

    private companion object {
        const val TAG = "SettingsSyncTrigger"
    }
}
