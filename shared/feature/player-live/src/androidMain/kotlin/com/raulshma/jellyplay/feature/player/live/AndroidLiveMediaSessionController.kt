package com.raulshma.jellyplay.feature.player.live

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.Media3LivePlayerEngine

/**
 * The androidMain [LiveMediaSessionController] (gh-178): builds a
 * [MediaLibrarySession] around the active live engine's media3 player
 * (`ExoLiveEngine` always hosts one, exposed through the androidMain
 * [Media3LivePlayerEngine] seam) so headset / Bluetooth / lock-screen media
 * buttons work during live playback. Registered through the shared
 * [PlaybackSessionManager] singleton — the same slot the VOD and audio
 * sessions occupy, with the manager's priority guard arbitrating swaps.
 *
 * Session ID `jellyplay_live_<channelId>`; a same-channel re-tune (retry)
 * rebuilds the same id, so the outgoing session is released ahead of the
 * build (the Media3 construction-time ID-uniqueness rule — see the VOD
 * controller's `releaseSupersededSession` KDoc for the full rationale).
 *
 * Metadata (channel name + artwork) is pinned at the session layer through a
 * `ForwardingPlayer` override — the same non-destructive pin the VOD
 * controller applies.
 *
 * Session activity: reopens the fullscreen live player — [PLAYER_ACTIVITY_CLASS_NAME]
 * with the sealed live-variant extras (mirrored by value; this feature module
 * cannot see the app module, the same mirror the VOD controller keeps for
 * `player_item_id`).
 */
@OptIn(UnstableApi::class)
internal class AndroidLiveMediaSessionController(
    private val context: Context,
    private val sessionManager: PlaybackSessionManager,
    private val getEngine: () -> LivePlayerEngine?,
    private val getImageUrl: (channelId: String, maxWidth: Int, tag: String?) -> String,
) : LiveMediaSessionController {

    private var session: MediaSession? = null

    override fun createForChannel(channelId: String, title: String, imageTag: String?) {
        val engine = getEngine() ?: return
        val player = (engine as? Media3LivePlayerEngine)?.media3Player ?: return

        releaseSupersededSession()

        val artworkUri = getImageUrl(channelId, ARTWORK_MAX_WIDTH, imageTag)
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val pinnedMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_TV_CHANNEL)
            .apply { artworkUri?.let { setArtworkUri(it) } }
            .build()
        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun getMediaMetadata(): MediaMetadata = pinnedMetadata
        }

        session = MediaLibrarySession.Builder(context, sessionPlayer, NO_OP_LIBRARY_CALLBACK)
            .setId("${SESSION_ID_PREFIX}$channelId")
            .setSessionActivity(buildChannelSessionActivity(channelId, title))
            .build()
            .also(sessionManager::setActiveSession)
    }

    override fun release() {
        val current = session ?: return
        if (sessionManager.currentSession === current) {
            sessionManager.clearSession(current)
        }
        try { current.release() } catch (_: Exception) { }
        session = null
    }

    /**
     * Releases the held session ahead of a same-id rebuild without vacating
     * the manager slot — the slot swap stays atomic in
     * [createForChannel]'s `setActiveSession` (the VOD controller's
     * `releaseSupersededSession` rationale applies verbatim).
     */
    private fun releaseSupersededSession() {
        val current = session ?: return
        try { current.release() } catch (_: Exception) { }
        session = null
    }

    private fun buildChannelSessionActivity(channelId: String, channelName: String): PendingIntent {
        val intent = Intent().apply {
            setClassName(context, PLAYER_ACTIVITY_CLASS_NAME)
            putExtra(EXTRA_VARIANT_KEY, VARIANT_LIVE)
            putExtra(EXTRA_CHANNEL_ID_KEY, channelId)
            putExtra(EXTRA_CHANNEL_NAME_KEY, channelName)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            channelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SESSION_ID_PREFIX = "jellyplay_live_"
        const val ARTWORK_MAX_WIDTH = 300

        // PlayerActivity lives in the `app` module; referenced by class name,
        // with the sealed live-variant extras mirrored by value — must match
        // PlayerActivityArgs (EXTRA_VARIANT / VARIANT_LIVE / EXTRA_CHANNEL_ID /
        // EXTRA_CHANNEL_NAME); a value change on either side breaks the
        // notification → fullscreen-player round trip silently.
        private const val PLAYER_ACTIVITY_CLASS_NAME = "com.raulshma.jellyplay.PlayerActivity"
        private const val EXTRA_VARIANT_KEY = "player_args_variant"
        private const val VARIANT_LIVE = "live"
        private const val EXTRA_CHANNEL_ID_KEY = "player_channel_id"
        private const val EXTRA_CHANNEL_NAME_KEY = "player_channel_name"

        /**
         * Live playback never exposes a browsable library; the callback exists
         * only so the host `MediaLibraryService` serves the session (and thus
         * accepts media-button / headset-hook controller connections). The
         * base callback rejects every library method — correct for a
         * non-browsable live session.
         */
        val NO_OP_LIBRARY_CALLBACK = object : MediaLibrarySession.Callback {}
    }
}
