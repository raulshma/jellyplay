package com.raulshma.jellyplay.core.data.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class JellyPlayPlaybackService : MediaSessionService() {

    @Inject
    lateinit var sessionManager: PlaybackSessionManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .build()
                .apply {
                    setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
                }
        )
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
            // Keep the service running if playback is active
            return
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notification for media playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "jellyplay_media_playback"
    }
}
