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
    private val lock = Any()
    @GuardedBy("lock")
    private var _currentSession: MediaSession? = null
    val currentSession: MediaSession? get() = synchronized(lock) { _currentSession }

    /**
     * Sets the active session. Releases the previous session if it's still active
     * and the caller hasn't already released it.
     */
    fun setActiveSession(session: MediaSession) {
        val oldSession: MediaSession?
        synchronized(lock) {
            oldSession = _currentSession
            _currentSession = session
        }
        // Release the old session only if it's not the same as the new one.
        // Use try-catch to guard against double-release (isReleased is package-private).
        if (oldSession != null && oldSession !== session) {
            try { oldSession.release() } catch (_: Exception) { }
        }
        // Ensure the playback service is started and will pick up this session
        startPlaybackService()
    }

    fun clearSession(session: MediaSession) {
        synchronized(lock) {
            if (_currentSession === session) {
                _currentSession = null
            }
        }
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
