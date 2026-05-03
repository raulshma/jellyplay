package com.raulshma.jellyplay.core.data.playback

import androidx.annotation.GuardedBy
import androidx.media3.session.MediaSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionManager @Inject constructor() {
    private val lock = Any()
    @GuardedBy("lock")
    private var _currentSession: MediaSession? = null
    val currentSession: MediaSession? get() = synchronized(lock) { _currentSession }

    fun setActiveSession(session: MediaSession) {
        synchronized(lock) {
            _currentSession?.release()
            _currentSession = session
        }
    }

    fun clearSession(session: MediaSession) {
        synchronized(lock) {
            if (_currentSession === session) {
                _currentSession = null
            }
        }
    }
}
