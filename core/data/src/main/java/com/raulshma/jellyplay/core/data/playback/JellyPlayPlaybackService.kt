package com.raulshma.jellyplay.core.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
        createNotificationChannel()
        setMediaNotificationProvider(createMediaNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return sessionManager.currentSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
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
            session.release()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Media playback controls"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "jellyplay_media_playback"
    }
}
