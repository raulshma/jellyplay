package com.raulshma.jellyplay.core.data.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.toBitmap
import com.google.common.collect.ImmutableList
import com.raulshma.jellyplay.core.data.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom media notification provider for JellyPlay.
 *
 * Builds a branded MediaStyle notification with:
 * - JellyPlay small icon and branded playback action icons
 * - Large artwork from MediaMetadata.artworkUri (loaded async via Coil)
 * - Colorized background extracted from artwork palette
 * - Content intent that reopens the session's session activity when set
 *   (video → PlayerActivity fullscreen, e.g. expanding out of PiP) and falls
 *   back to the app launcher intent (MainActivity) for sessions without one
 *
 * Works for both audio and video playback via MediaSession.
 */
@OptIn(UnstableApi::class)
class JellyPlayNotificationProvider(
    context: Context,
) : MediaNotification.Provider {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoader by lazy { coil3.SingletonImageLoader.get(appContext) }
    private val bitmapLock = Any()
    @Volatile private var cachedBitmap: Bitmap? = null
    private var cachedArtworkUri: String? = null
    @Volatile private var cachedAccentColor: Int = FALLBACK_COLOR
    @Volatile private var cachedContentIntent: PendingIntent? = null

    fun release() {
        scope.cancel()
        synchronized(bitmapLock) {
            // Recycle the artwork Bitmap. Safe because loadArtworkAsync
            // decodes with memoryCachePolicy(DISABLED), so this is a private
            // instance never handed to a Compose AsyncImage (which would crash
            // on its next onDraw). Mirrors NowPlayingWidgetUpdater.stop().
            cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
            cachedBitmap = null
            cachedArtworkUri = null
        }
        cachedContentIntent = null
    }

    init {
        ensureNotificationChannel()
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata

        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString()
            ?: metadata.albumTitle?.toString()
            ?: ""
        val artworkUri = metadata.artworkUri?.toString()

        // Load artwork asynchronously if URI changed
        val artworkUriStr = metadata.artworkUri?.toString()
        synchronized(bitmapLock) {
            if (artworkUriStr != null && artworkUriStr != cachedArtworkUri) {
                cachedArtworkUri = artworkUriStr
                loadArtworkAsync(artworkUriStr, mediaSession, actionFactory, onNotificationChangedCallback)
            }
        }

        val isPlaying = player.isPlaying

        // Build actions using ActionFactory.createMediaAction with Player.Command constants.
        // The action factory routes them through the MediaSession's internal notification controller.
        // Rewind/Fast-Forward (COMMAND_SEEK_BACK/FORWARD) were previously used, but with no
        // setSeekBack/ForwardIncrementMs on the player they resolved to the system's 10s/10s
        // jumps and gave no way to skip a track from the notification, lock screen, Bluetooth
        // headset, Wear or Android Auto. Skip-to-next/previous maps to the queue-backed
        // seekToNext/seekToPrevious the MediaSession already exposes.
        val previousAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(appContext, R.drawable.ic_notification_skip_prev),
            "Previous",
            Player.COMMAND_SEEK_TO_PREVIOUS,
        )
        val playPauseAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(
                appContext,
                if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            ),
            if (isPlaying) "Pause" else "Play",
            Player.COMMAND_PLAY_PAUSE,
        )
        val nextAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(appContext, R.drawable.ic_notification_skip_next),
            "Next",
            Player.COMMAND_SEEK_TO_NEXT,
        )
        val stopAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(appContext, R.drawable.ic_notification_stop),
            "Stop",
            Player.COMMAND_STOP,
        )

        // Compact view: previous, play/pause, next
        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(artist)
            .setStyle(mediaStyle)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
            .setContentIntent(buildContentIntent(mediaSession))
            .setOngoing(isPlaying)

        synchronized(bitmapLock) {
            builder.setColor(cachedAccentColor)
            cachedBitmap?.let { builder.setLargeIcon(it) }
        }

        // Actions: previous, play/pause, next, stop
        builder.addAction(previousAction)
        builder.addAction(playPauseAction)
        builder.addAction(nextAction)
        builder.addAction(stopAction)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle,
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return MediaNotification.Provider.NotificationChannelInfo(
            CHANNEL_ID,
            "Now Playing",
        )
    }

    // ── Artwork loading ──────────────────────────────────────────────

    private fun loadArtworkAsync(
        artworkUri: String,
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ) {
        scope.launch {
            try {
                val request = ImageRequest.Builder(appContext)
                    .data(artworkUri)
                    .size(ARTWORK_SIZE)
                    // Disable the memory cache so Coil decodes a fresh,
                    // privately-owned Bitmap. The default path returns the
                    // *shared* cache Bitmap also handed to any Compose
                    // AsyncImage showing the same artwork URL (MiniPlayer,
                    // AudioPlayerArtwork). Recycling that shared Bitmap in
                    // release() then crashed the Compose painter with
                    // "Canvas: trying to use a recycled bitmap". Mirrors the
                    // fix proven in WidgetImageLoader.loadPoster.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
                val result = imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()

                if (bitmap != null) {
                    val accentColor = extractAccentColor(bitmap)
                    synchronized(bitmapLock) {
                        cachedBitmap = bitmap
                        cachedAccentColor = accentColor
                    }
                    withContext(Dispatchers.Main) {
                        callback.onNotificationChanged(
                            createNotification(session, ImmutableList.of(), actionFactory, callback)
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun extractAccentColor(bitmap: Bitmap): Int {
        return try {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap)
                    .maximumColorCount(8)
                    .generate()
            }
            palette.vibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: FALLBACK_COLOR
        } catch (_: Exception) {
            FALLBACK_COLOR
        }
    }

    // ── Intents ──────────────────────────────────────────────────────

    private fun buildContentIntent(session: MediaSession): PendingIntent {
        // Prefer the active session's own session activity. Video sessions pin
        // this to PlayerActivity, so tapping the notification expands the
        // fullscreen video (e.g. out of PiP) instead of landing on the browse
        // UI. Audio sessions leave it unset, so we fall through to the app
        // launcher intent (MainActivity) — preserving prior audio behaviour.
        session.sessionActivity?.let { return it }
        cachedContentIntent?.let { return it }
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = if (intent != null) {
            PendingIntent.getActivity(
                appContext,
                CONTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                appContext,
                CONTENT_REQUEST_CODE,
                Intent().apply { setPackage(appContext.packageName) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        cachedContentIntent = pending
        return pending
    }

    // ── Channel ──────────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Now Playing",
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Media playback controls"
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "jellyplay_media_playback"
        const val NOTIFICATION_ID = 1001
        private const val CONTENT_REQUEST_CODE = 2001
        private const val ARTWORK_SIZE = 512
        private const val FALLBACK_COLOR = 0xFF006878.toInt() // md_theme_light_primary
    }
}
