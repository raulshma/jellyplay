package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.content.Intent
import androidx.annotation.GuardedBy
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    interface Listener {
        fun onSessionChanged(newSession: MediaSession?, oldSession: MediaSession?)
    }

    private val lock = Any()

    @GuardedBy("lock")
    private var _currentSession: MediaSession? = null
    val currentSession: MediaSession? get() = synchronized(lock) { _currentSession }

    @GuardedBy("lock")
    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        val session: MediaSession?
        synchronized(lock) {
            listeners.add(listener)
            session = _currentSession
        }
        if (session != null) {
            listener.onSessionChanged(session, null)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    /**
     * Sets the active session. Releases the previous session if it's still active
     * and the caller hasn't already released it.
     */
    fun setActiveSession(session: MediaSession) {
        val oldSession: MediaSession?
        val currentListeners: List<Listener>
        synchronized(lock) {
            oldSession = _currentSession
            _currentSession = session
            currentListeners = listeners.toList()
        }

        currentListeners.forEach { it.onSessionChanged(session, oldSession) }

        // Release the old session only if it's not the same as the new one.
        // Use try-catch to guard against double-release (isReleased is package-private).
        if (oldSession != null && oldSession !== session) {
            try { oldSession.release() } catch (_: Exception) { }
        }
        // Ensure the playback service is started and will pick up this session
        startPlaybackService()
    }

    fun clearSession(session: MediaSession) {
        val oldSession: MediaSession?
        val currentListeners: List<Listener>
        synchronized(lock) {
            if (_currentSession === session) {
                oldSession = _currentSession
                _currentSession = null
                currentListeners = listeners.toList()
            } else {
                return
            }
        }
        currentListeners.forEach { it.onSessionChanged(null, oldSession) }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, JellyPlayPlaybackService::class.java)
            context.startService(intent)
        } catch (_: Exception) {
            // Service may not be registered yet or app is in background restriction
        }
    }
}
