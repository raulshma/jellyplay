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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCastStrategy @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : CastStrategy {

    companion object {
        private const val TAG = "GoogleCastStrategy"
    }

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    override val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

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

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _isConnected.value = true
            _isConnecting.value = false
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            _isConnected.value = false
            _isConnecting.value = false
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _isConnected.value = true
            _isConnecting.value = false
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {
            _isConnecting.value = true
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _isConnected.value = false
            _isConnecting.value = false
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _isConnecting.value = false
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _isConnecting.value = true
        }
    }

    private fun ensureListenersRegistered() {
        if (sessionListenerRegistered && castStateListenerRegistered) return
        try {
            val castContext = withCastDiskReadsPermitted { CastContext.getSharedInstance(appContext) }
            if (!sessionListenerRegistered) {
                castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                _isConnected.value = castContext.sessionManager.currentCastSession?.isConnected == true
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
