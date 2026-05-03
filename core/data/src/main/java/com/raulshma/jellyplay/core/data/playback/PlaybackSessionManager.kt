package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var _currentSession: MediaSession? = null
    val currentSession: MediaSession? get() = _currentSession

    fun setActiveSession(session: MediaSession) {
        _currentSession?.release()
        _currentSession = session
        val intent = Intent(context, JellyPlayPlaybackService::class.java)
        context.startForegroundService(intent)
    }

    fun clearSession(session: MediaSession) {
        if (_currentSession === session) {
            _currentSession = null
        }
    }
}
