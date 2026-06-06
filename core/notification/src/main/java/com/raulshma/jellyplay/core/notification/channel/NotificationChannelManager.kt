package com.raulshma.jellyplay.core.notification.channel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.raulshma.jellyplay.core.model.NotificationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel(libraryId: String, libraryName: String, prefs: NotificationPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = channelIdFor(libraryId)
        if (nm.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(
            channelId,
            "New in $libraryName",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications for new media in $libraryName"
            enableVibration(prefs.vibrateEnabled)
            enableLights(prefs.lightsEnabled)
            setShowBadge(true)
            if (prefs.soundEnabled) {
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    null,
                )
            } else {
                setSound(null, null)
            }
        }
        nm.createNotificationChannel(channel)
    }

    fun ensureSummaryChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_SUMMARY) != null) return
        val channel = NotificationChannel(
            CHANNEL_SUMMARY,
            "New Media",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Summary of new media notifications"
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun deleteStaleChannels(validLibraryIds: Set<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id !in validLibraryIds.map { id -> channelIdFor(id) } }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    companion object {
        const val CHANNEL_PREFIX = "new_media_"
        const val CHANNEL_SUMMARY = "new_media_summary"

        fun channelIdFor(libraryId: String): String = "${CHANNEL_PREFIX}${libraryId.take(20)}"
    }
}
