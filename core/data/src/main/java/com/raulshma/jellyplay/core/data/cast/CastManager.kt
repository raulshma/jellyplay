package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Events emitted by [CastManager] when the cast session state changes. */
sealed class CastSessionEvent {
    data object Connected : CastSessionEvent()
    data object Disconnected : CastSessionEvent()
}

@OptIn(UnstableApi::class)
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var castPlayer: CastPlayer? = null
    private var sessionListener: SessionAvailabilityListener? = null

    private val _sessionEvents = MutableSharedFlow<CastSessionEvent>(extraBufferCapacity = 1)
    /** A hot flow of cast session lifecycle events (connect / disconnect). */
    val sessionEvents: SharedFlow<CastSessionEvent> = _sessionEvents.asSharedFlow()

    /** Tracks whether the global [SessionManagerListener] has been registered. */
    @Volatile
    private var sessionManagerListenerRegistered = false

    val isCastAvailable: Boolean
        get() = try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.currentCastSession?.isConnected == true ||
                    castContext.castState != CastState.NO_DEVICES_AVAILABLE
        } catch (_: Exception) {
            false
        }

    val isConnected: Boolean
        get() = try {
            CastContext.getSharedInstance(context)
                .sessionManager.currentCastSession?.isConnected == true
        } catch (_: Exception) {
            false
        }

    private var currentListener: Player.Listener? = null

    /**
     * Lazily registers a [SessionManagerListener] that forwards session lifecycle
     * events to [sessionEvents]. Safe to call multiple times — registration happens once.
     */
    private fun ensureSessionManagerListener() {
        if (sessionManagerListenerRegistered) return
        try {
            val castContext = CastContext.getSharedInstance(context)
            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    _sessionEvents.tryEmit(CastSessionEvent.Connected)
                }
                override fun onSessionEnded(session: CastSession, error: Int) {
                    _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
                }
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    _sessionEvents.tryEmit(CastSessionEvent.Connected)
                }
                override fun onSessionSuspended(session: CastSession, reason: Int) {
                    // Don't emit disconnect for suspend — the session may resume
                }
                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {
                    _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
                }
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
            }
            castContext.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            sessionManagerListenerRegistered = true
        } catch (_: Exception) { }
    }

    fun getCastPlayer(listener: Player.Listener): CastPlayer? {
        if (!isConnected) return null
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
    }

    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long = 0,
        listener: Player.Listener,
    ) {
        ensureSessionManagerListener()
        val player = getCastPlayer(listener) ?: return
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }
}
