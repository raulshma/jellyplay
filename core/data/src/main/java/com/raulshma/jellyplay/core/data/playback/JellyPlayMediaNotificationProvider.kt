package com.raulshma.jellyplay.core.data.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

@OptIn(UnstableApi::class)
fun createMediaNotificationProvider(context: Context): DefaultMediaNotificationProvider {
    ensureNotificationChannel(context)
    val builder = DefaultMediaNotificationProvider.Builder(context)
        .setChannelId(CHANNEL_ID)
    return builder.build().apply {
        setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
    }
}

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}

const val CHANNEL_ID = "jellyplay_media_playback"
