package com.raulshma.jellyplay.core.notification.channel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.raulshma.jellyplay.shared.core.data.R
import com.raulshma.jellyplay.core.data.playback.JellyPlayNotificationProvider
import com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncNotificationHelper
import com.raulshma.jellyplay.core.model.NotificationPreferences

/**
 * The one home for notification-channel creation policy (I4 fold). Besides
 * the per-library new-media channels this manager has always owned, the
 * three fixed subsystem channels — download progress, playback-sync drain,
 * now-playing media — each used to be created by an inline
 * `getNotificationChannel == null → create` twin inside its own helper
 * ([DownloadNotificationHelper], [PlaybackSyncNotificationHelper],
 * [JellyPlayNotificationProvider]), restating the dedup + construction with
 * slightly different importance/badge choices. Those declarations now live
 * here ([ensureDownloadsChannel] / [ensurePlaybackSyncChannel] /
 * [ensureNowPlayingChannel]) with each channel's EXACT former importance,
 * badge, name and description values — this is a fold, not a redesign; the
 * notification BUILDERS stay in their subsystems, where their locality is
 * good. Only channel creation moved. The opt-in new-episodes channel
 * ([ensureNewEpisodesChannel]) follows the same fixed-declaration pattern.
 */
class NotificationChannelManager(
    private val context: Context,
) {

    /**
     * Declaration of a fixed (non-per-library) channel: every knob the three
     * former inline twins set — id, resolved name, importance, description,
     * badge, optional lockscreen visibility. [name]/[description] are already
     * resolved (resource-or-literal per subsystem, exactly as before).
     */
    data class ChannelDeclaration(
        val id: String,
        val name: CharSequence,
        val importance: Int,
        val description: String? = null,
        val showBadge: Boolean = false,
        val lockscreenVisibility: Int? = null,
    )

    /** The downloads-progress channel (Downloads twin's exact former values). */
    fun ensureDownloadsChannel() = ensureDeclaredChannel(
        ChannelDeclaration(
            id = DownloadNotificationHelper.CHANNEL_ID,
            name = "Downloads",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "Download progress notifications",
            showBadge = false,
        ),
    )

    /** The playback-sync drain channel (resource-named, as the twin was). */
    fun ensurePlaybackSyncChannel() = ensureDeclaredChannel(
        ChannelDeclaration(
            id = PlaybackSyncNotificationHelper.CHANNEL_ID,
            name = context.getString(R.string.data_sync_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
            description = context.getString(R.string.data_sync_channel_desc),
            showBadge = false,
        ),
    )

    /** The now-playing media channel (public lockscreen visibility, as before). */
    fun ensureNowPlayingChannel() = ensureDeclaredChannel(
        ChannelDeclaration(
            id = JellyPlayNotificationProvider.CHANNEL_ID,
            name = "Now Playing",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "Media playback controls",
            showBadge = false,
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC,
        ),
    )

    /**
     * The opt-in new-episodes channel — the fixed routing target the
     * dispatcher sends episode items to when the new-episodes preference is
     * on. Same importance/badge family as the other new-media channels
     * ([ensureSummaryChannel], the per-library channels), not the progress
     * channels' LOW: this is notification content, not progress.
     */
    fun ensureNewEpisodesChannel() = ensureDeclaredChannel(
        ChannelDeclaration(
            id = CHANNEL_NEW_EPISODES,
            name = context.getString(R.string.notification_channel_new_episodes),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            description = context.getString(R.string.notification_channel_new_episodes_desc),
            showBadge = true,
        ),
    )

    private fun ensureDeclaredChannel(channel: ChannelDeclaration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channel.id) != null) return
        val created = NotificationChannel(channel.id, channel.name, channel.importance).apply {
            channel.description?.let { description = it }
            setShowBadge(channel.showBadge)
            channel.lockscreenVisibility?.let { lockscreenVisibility = it }
        }
        nm.createNotificationChannel(created)
    }

    fun ensureChannel(libraryId: String, libraryName: String, prefs: NotificationPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = channelIdFor(libraryId)
        if (nm.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_new_in_library, libraryName),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_new_in_library_desc, libraryName)
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
            context.getString(R.string.notification_channel_summary),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_summary_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun deleteStaleChannels(validLibraryIds: Set<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val validChannelIds = validLibraryIds.map { channelIdFor(it) }.toSet()
        nm.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id !in validChannelIds }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    companion object {
        const val CHANNEL_PREFIX = "new_media_"
        const val CHANNEL_SUMMARY = "new_media_summary"

        /**
         * The fixed new-episodes channel. Deliberately NOT [CHANNEL_PREFIX]-
         * namespaced so [deleteStaleChannels] (which sweeps the per-library
         * prefix) can never remove it — same protection the other fixed
         * subsystem channels have.
         */
        const val CHANNEL_NEW_EPISODES = "new_episodes"

        fun channelIdFor(libraryId: String): String = "${CHANNEL_PREFIX}${libraryId.take(20)}"
    }
}
