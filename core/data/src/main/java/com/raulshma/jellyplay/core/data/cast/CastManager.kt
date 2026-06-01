package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class CastSessionEvent {
    data object Connected : CastSessionEvent()
    data object Disconnected : CastSessionEvent()
}

@OptIn(UnstableApi::class)
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleCastStrategy: GoogleCastStrategy,
) {
    private val strategies = mutableMapOf<String, CastStrategy>()
    private var activeStrategyName: String = STRATEGY_GOOGLE

    private var castPlayer: CastPlayer? = null
    private var sessionListener: SessionAvailabilityListener? = null
    private var currentListener: Player.Listener? = null

    private val _sessionEvents = MutableSharedFlow<CastSessionEvent>(extraBufferCapacity = 1)
    val sessionEvents: SharedFlow<CastSessionEvent> = _sessionEvents.asSharedFlow()

    private val googleSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    @Volatile
    private var googleSessionListenerRegistered = false

    init {
        strategies[STRATEGY_GOOGLE] = googleCastStrategy
        ensureGoogleSessionListener()
    }

    fun registerStrategy(name: String, strategy: CastStrategy) {
        strategies[name] = strategy
    }

    fun unregisterStrategy(name: String) {
        strategies.remove(name)
        if (activeStrategyName == name) {
            activeStrategyName = STRATEGY_GOOGLE
        }
    }

    fun setActiveStrategy(name: String) {
        val previous = activeStrategyName
        activeStrategyName = name
        val prevStrategy = strategies[previous]
        if (prevStrategy != null) {
            prevStrategy.stopDiscovery()
        }
    }

    private val activeStrategy: CastStrategy?
        get() = strategies[activeStrategyName]

    val isCastAvailable: Boolean
        get() = activeStrategy?.isAvailable?.value == true

    val isConnected: Boolean
        get() = activeStrategy?.isConnected?.value == true

    val isAvailableFlow: StateFlow<Boolean>
        get() = activeStrategy?.isAvailable ?: googleCastStrategy.isAvailable

    val isConnectedFlow: StateFlow<Boolean>
        get() = activeStrategy?.isConnected ?: googleCastStrategy.isConnected

    val discoveredDevices: StateFlow<List<CastDevice>>
        get() = activeStrategy?.discoveredDevices ?: googleCastStrategy.discoveredDevices

    fun startDiscovery(context: android.content.Context) {
        activeStrategy?.startDiscovery(context)
    }

    fun stopDiscovery() {
        activeStrategy?.stopDiscovery()
    }

    fun connect(context: android.content.Context, device: CastDevice) {
        activeStrategy?.connect(context, device)
    }

    fun disconnect(context: android.content.Context) {
        activeStrategy?.disconnect(context)
    }

    private fun ensureGoogleSessionListener() {
        if (googleSessionListenerRegistered) return
        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.addSessionManagerListener(googleSessionListener, CastSession::class.java)
            googleSessionListenerRegistered = true
        } catch (_: Exception) {}
    }

    fun getCastPlayer(listener: Player.Listener): CastPlayer? {
        if (!googleCastStrategy.isConnected.value) return null
        if (castPlayer == null) {
            try {
                val castContext = CastContext.getSharedInstance(context)
                sessionListener = object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() {}
                    override fun onCastSessionUnavailable() {
                        currentListener?.onPlaybackStateChanged(Player.STATE_ENDED)
                    }
                }
                castPlayer = CastPlayer(castContext).apply {
                    addListener(listener)
                    setSessionAvailabilityListener(sessionListener!!)
                }
                currentListener = listener
            } catch (_: Exception) {
                return null
            }
        } else if (currentListener !== listener) {
            currentListener?.let { castPlayer?.removeListener(it) }
            castPlayer?.addListener(listener)
            currentListener = listener
        }
        return castPlayer
    }

    fun release() {
        castPlayer?.release()
        castPlayer = null
        sessionListener = null
        currentListener = null
        strategies.values.forEach { it.stopDiscovery() }
    }

    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long = 0,
        listener: Player.Listener,
    ) {
        ensureGoogleSessionListener()
        val player = getCastPlayer(listener) ?: return
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    companion object {
        const val STRATEGY_GOOGLE = "google"
        const val STRATEGY_LIBVLC = "libvlc"
    }
}
