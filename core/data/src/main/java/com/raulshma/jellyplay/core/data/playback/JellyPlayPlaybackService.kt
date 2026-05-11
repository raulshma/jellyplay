package com.raulshma.jellyplay.core.data.playback

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class JellyPlayPlaybackService : MediaSessionService() {

    @Inject lateinit var sessionManager: PlaybackSessionManager
    @Inject lateinit var audioPlaybackManager: AudioPlaybackManager

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(JellyPlayNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return sessionManager.currentSession
    }

    /**
     * When the app starts the service (via `startService`), we need to ensure the current
     * session is added to this service so the MediaNotificationManager tracks it and posts
     * the notification. This also handles media button events when a controller connects.
     */
    @UnstableApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = sessionManager.currentSession
        if (session != null && !isSessionAdded(session)) {
            addSession(session)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = sessionManager.currentSession
        val player = session?.player ?: run {
            stopSelf()
            return
        }
        if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            return
        }
        audioPlaybackManager.stopAndRelease()
        stopSelf()
    }

    override fun onDestroy() {
        val session = sessionManager.currentSession
        if (session != null) {
            sessionManager.clearSession(session)
            try { session.release() } catch (_: Exception) { }
        }
        super.onDestroy()
    }
}
