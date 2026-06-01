package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    companion object {
        private const val TAG = "CastManager"
        const val STRATEGY_GOOGLE = "google"
        const val STRATEGY_LIBVLC = "libvlc"
    }

    private val strategies = mutableMapOf<String, CastStrategy>()
    private var activeStrategyName: String = STRATEGY_GOOGLE

    private var castPlayer: CastPlayer? = null
    private var sessionAvailabilityListener: SessionAvailabilityListener? = null
    private var externalListener: Player.Listener? = null

    private val _sessionEvents = MutableSharedFlow<CastSessionEvent>(extraBufferCapacity = 1)
    val sessionEvents: SharedFlow<CastSessionEvent> = _sessionEvents.asSharedFlow()

    private val _castPositionMs = MutableStateFlow(0L)
    val castPositionMs: StateFlow<Long> = _castPositionMs.asStateFlow()

    private val _castDurationMs = MutableStateFlow(0L)
    val castDurationMs: StateFlow<Long> = _castDurationMs.asStateFlow()

    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()

    private val _castBufferedPositionMs = MutableStateFlow(0L)
    val castBufferedPositionMs: StateFlow<Long> = _castBufferedPositionMs.asStateFlow()

    private val castPlayerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateCastState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _castIsPlaying.value = isPlaying
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            updateCastState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateCastState()
        }
    }

    private fun updateCastState() {
        val player = castPlayer ?: return
        _castPositionMs.value = player.currentPosition.coerceAtLeast(0)
        _castDurationMs.value = player.duration.coerceAtLeast(0)
        _castBufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(0)
        _castIsPlaying.value = player.isPlaying
    }

    private val googleSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            resetCastState()
            _sessionEvents.tryEmit(CastSessionEvent.Disconnected)
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _sessionEvents.tryEmit(CastSessionEvent.Connected)
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            resetCastState()
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

    fun play() {
        castPlayer?.play()
    }

    fun pause() {
        castPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        castPlayer?.seekTo(positionMs)
    }

    private fun ensureGoogleSessionListener() {
        if (googleSessionListenerRegistered) return
        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.addSessionManagerListener(googleSessionListener, CastSession::class.java)
            googleSessionListenerRegistered = true
        } catch (_: Exception) {}
    }

    private fun ensureCastPlayer(): CastPlayer? {
        if (!googleCastStrategy.isConnected.value) return null
        if (castPlayer != null) return castPlayer
        try {
            val castContext = CastContext.getSharedInstance(context)
            sessionAvailabilityListener = object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {}
                override fun onCastSessionUnavailable() {
                    externalListener?.onPlaybackStateChanged(Player.STATE_ENDED)
                }
            }
            castPlayer = CastPlayer(castContext).apply {
                addListener(castPlayerListener)
                setSessionAvailabilityListener(sessionAvailabilityListener!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create CastPlayer", e)
        }
        return castPlayer
    }

    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long = 0,
        listener: Player.Listener,
    ) {
        ensureGoogleSessionListener()
        externalListener?.let { castPlayer?.removeListener(it) }
        externalListener = listener
        val player = ensureCastPlayer() ?: return
        player.addListener(listener)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    fun release() {
        castPlayer?.removeListener(castPlayerListener)
        externalListener?.let { castPlayer?.removeListener(it) }
        castPlayer?.release()
        castPlayer = null
        sessionAvailabilityListener = null
        externalListener = null
        resetCastState()
        strategies.values.forEach { it.stopDiscovery() }
    }

    private fun resetCastState() {
        _castPositionMs.value = 0L
        _castDurationMs.value = 0L
        _castIsPlaying.value = false
        _castBufferedPositionMs.value = 0L
    }
}
