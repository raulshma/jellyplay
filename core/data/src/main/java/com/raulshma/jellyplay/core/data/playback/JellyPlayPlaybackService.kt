package com.raulshma.jellyplay.core.data.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class JellyPlayPlaybackService : MediaSessionService() {

    @Inject
    lateinit var sessionManager: PlaybackSessionManager

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return sessionManager.currentSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val session = sessionManager.currentSession
        if (session?.player?.isPlaying == true) return
        stopSelf()
    }
}
