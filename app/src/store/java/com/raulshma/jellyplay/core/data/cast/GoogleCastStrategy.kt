package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The GMS discovery/session strategy (MediaRouter routes + CastContext
 * session/state listeners). Store-source-set resident: the file moved out of
 * core:data so the fdroid variant never sees Play services on its classpath.
 * Same package as the core:data cast types it implements ([CastStrategy]) and
 * consumes ([CastDevice], [withCastDiskReadsPermitted] — both stay in
 * core:data, which is an app dependency, so same-package resolution works
 * unchanged).
 */
class GoogleCastStrategy(
    private val appContext: Context,
) : CastStrategy {

    companion object {
        private const val TAG = "GoogleCastStrategy"
    }

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /**
     * The connection phase machine (commonMain [CastSessionPhase]) — the ONE
     * source of truth the former `_isConnected` / `_isConnecting` boolean
     * pair folded into two hand-synced flags. The public
     * [isConnected] / [isConnecting] surface below are read-through
     * projections of it. The listener-side transitions are the
     * `session*` handlers under [sessionListener]; they are `internal` so
     * the store-lane suite (GoogleCastStrategyTest) can drive the ladder
     * without a live CastContext — same thin-host split as the DLNA /
     * Jellyfin wirings.
     */
    private val _phase = MutableStateFlow<CastSessionPhase>(CastSessionPhase.Idle)
    val phase: StateFlow<CastSessionPhase> = _phase.asStateFlow()

    override val isConnected: StateFlow<Boolean> = _phase.projected { it.isConnected }

    override val isConnecting: StateFlow<Boolean> = _phase.projected { it.isConnecting }

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    @Volatile
    private var sessionListenerRegistered = false

    @Volatile
    private var castStateListenerRegistered = false

    @Volatile
    private var discoveryActive = false

    private val routeSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    private val castStateListener = CastStateListener { state ->
        _isAvailable.value = state != CastState.NO_DEVICES_AVAILABLE
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (route.isEnabled && route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE) {
                refreshDeviceList(router)
            }
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshDeviceList(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshDeviceList(router)
        }
    }

    /**
     * The GMS listener is a thin adapter: every callback forwards to a
     * `session*` handler that owns the phase transition (and the KDoc on the
     * handler records the former boolean writes). The handlers are
     * `internal` so the store-lane suite can drive the ladder without a
     * live CastContext — the callbacks themselves need a real GMS stack.
     */
    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            sessionStarted()
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            sessionEnded()
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            sessionResumed()
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {
            sessionStarting()
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            sessionResumeFailed(error)
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            sessionStartFailed(error)
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            sessionResuming()
        }
    }

    /** `onSessionStarting`: a start attempt is in flight. Former write: `_isConnecting = true` only — so a mid-session re-route kept the stale `isConnected=true` standing; the machine drops the connected projection for the re-route, and the manager observers see the same Disconnected→Connected pair either way. */
    internal fun sessionStarting() {
        _phase.value = _phase.value.beginConnecting()
    }

    /** `onSessionStarted`: the session is live. Former writes: `_isConnected = true`, `_isConnecting = false`. */
    internal fun sessionStarted() {
        _phase.value = _phase.value.connected()
    }

    /** `onSessionResumed` (app resumed with the session alive): same outcome as a start. Former writes: `_isConnected = true`, `_isConnecting = false`. */
    internal fun sessionResumed() {
        _phase.value = _phase.value.connected()
    }

    /** `onSessionResuming` (auto-resume attempt): a connection attempt. Former write: `_isConnecting = true`. */
    internal fun sessionResuming() {
        _phase.value = _phase.value.beginConnecting()
    }

    /** `onSessionEnded`: the session is gone. Former writes: `_isConnected = false`, `_isConnecting = false`. */
    internal fun sessionEnded() {
        _phase.value = _phase.value.disconnected()
    }

    /** `onSessionResumeFailed`: the auto-resume attempt failed. Former writes: `_isConnected = false`, `_isConnecting = false` — same projections as [CastSessionPhase.Failed], now with the attempt's failure carried on the phase. */
    internal fun sessionResumeFailed(error: Int) {
        _phase.value = _phase.value.failed(reason = error.toString())
    }

    /**
     * `onSessionStartFailed`: DECLARED FIX #1. The former write cleared ONLY
     * `_isConnecting`, so when a PRIOR session's `_isConnected=true` was
     * still standing (session died without an `onSessionEnded` reaching us,
     * then the next start attempt failed), the strategy kept reporting a
     * connected=true dead session — the phantom connection. Landing in
     * [CastSessionPhase.Failed] makes both projections honest: the failed
     * start clears the stale connected flag.
     */
    internal fun sessionStartFailed(error: Int) {
        _phase.value = _phase.value.failed(reason = error.toString())
    }

    private fun ensureListenersRegistered() {
        if (sessionListenerRegistered && castStateListenerRegistered) return
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(appContext) }
            if (!sessionListenerRegistered) {
                castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                // Seed from the LIVE session the framework may still hold (a
                // registration after process-level re-entry): a live session
                // recovers to Connected from any phase; no live session lands
                // on Idle — which also retires any Failed state from a
                // previous registration. The former write seeded
                // `_isConnected` only and left `_isConnecting` untouched.
                _phase.value = if (castContext.sessionManager.currentCastSession?.isConnected == true) {
                    _phase.value.connected()
                } else {
                    _phase.value.disconnected()
                }
                sessionListenerRegistered = true
            }
            if (!castStateListenerRegistered) {
                castContext.addCastStateListener(castStateListener)
                _isAvailable.value = castContext.castState != CastState.NO_DEVICES_AVAILABLE
                castStateListenerRegistered = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cast SDK not available", e)
        }
    }

    private fun refreshDeviceList(router: MediaRouter) {
        val routes = router.routes.filter { route ->
            route.isEnabled && route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE
        }
        Log.d(TAG, "refreshDeviceList: found ${routes.size} remote routes out of ${router.routes.size} total")
        _discoveredDevices.value = routes.mapIndexed { index, route ->
            CastDevice(
                id = route.id ?: "google_route_$index",
                name = route.name?.toString() ?: "Unknown",
                type = "chromecast",
                tag = route,
            )
        }
    }

    override fun startDiscovery(context: Context) {
        ensureListenersRegistered()
        if (discoveryActive) return
        try {
            val router = MediaRouter.getInstance(context)
            router.addCallback(routeSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            discoveryActive = true
            refreshDeviceList(router)
            Log.d(TAG, "startDiscovery: active scan started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start discovery", e)
        }
    }

    override fun stopDiscovery() {
        if (!discoveryActive) return
        try {
            val router = MediaRouter.getInstance(appContext)
            router.removeCallback(mediaRouterCallback)
            discoveryActive = false
            Log.d(TAG, "stopDiscovery: active scan stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop discovery", e)
        }
    }

    override fun connect(context: Context, device: CastDevice) {
        try {
            val route = device.tag as? MediaRouter.RouteInfo ?: return
            val router = MediaRouter.getInstance(context)
            router.selectRoute(route)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select route", e)
        }
    }

    override fun disconnect(context: Context) {
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(context) }
            castContext.sessionManager.endCurrentSession(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to end session", e)
        }
    }

    override fun release() {
        // Remove the listeners we registered in ensureListenersRegistered()
        // so they don't outlive the CastManager (and fire on a stale
        // CastContext after logout). Idempotent: guarded by the *Registered
        // flags so calling twice is a no-op.
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(appContext) }
            if (sessionListenerRegistered) {
                castContext.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
                sessionListenerRegistered = false
            }
            if (castStateListenerRegistered) {
                castContext.removeCastStateListener(castStateListener)
                castStateListenerRegistered = false
            }
        } catch (_: Exception) {
            // Cast SDK may already be torn down during process shutdown.
        }
        if (discoveryActive) {
            // Best-effort: stopDiscovery already removes the MediaRouter
            // callback; call it so we don't leak an active scan.
            stopDiscovery()
        }
    }
}
