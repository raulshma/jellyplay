package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager

/**
 * Owns the system [MediaSession] (now-playing / lock-screen / Bluetooth metadata)
 * for the VOD player. Extracted from [VideoPlayerViewModel], continuing the
 * collaborator pattern established by [SubtitleManager] / [PlayerCastController].
 *
 * Three entry points cover every prior call site:
 *  - [createForItem]: builds a session around a [ForwardingPlayer] that pins the
 *    caller-supplied title/artwork at the MediaSession layer. ExoPlayer's HLS
 *    playlist parser resolves an empty MediaMetadata for Jellyfin transcode
 *    manifests (which carry no metadata), which would blank the system
 *    now-playing notification. We previously re-applied the title by mutating
 *    the playing MediaItem mid-playback — that disrupted HLS timeline/seek state
 *    and restarted seeks from 0 on transcoded media. Overriding
 *    `getMediaMetadata()` here is non-destructive: the underlying timeline and
 *    position are untouched.
 *  - [createForPlayer]: builds a bare session around an arbitrary [Player]
 *    (used by the background-cast detach/reattach path, which swaps the local
 *    engine player for the cast receiver's player).
 *  - [release]: tears down the active session (idempotent).
 *
 * The controller is the sole owner of the [MediaSession] reference; the VM no
 * longer holds a `videoMediaSession` field.
 */
@OptIn(UnstableApi::class)
internal class MediaSessionController(
    private val context: Context,
    private val sessionManager: PlaybackSessionManager,
    private val getPlayer: () -> Player?,
    private val getImageUrl: (itemId: String, maxWidth: Int) -> String,
) {

    private var session: MediaSession? = null

    /**
     * Build + activate a session for the current item, pinning [title] /
     * [subtitle] + the item's artwork. No-op if no underlying player is bound.
     * Releases any prior session first.
     */
    fun createForItem(itemId: String, title: String, subtitle: String) {
        release()
        val player = getPlayer() ?: return

        val artworkUri = getImageUrl(itemId, ARTWORK_MAX_WIDTH)
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val pinnedMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply { artworkUri?.let { setArtworkUri(it) } }
            .build()
        val sessionPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun getMediaMetadata(): MediaMetadata = pinnedMetadata
        }

        activate(MediaSession.Builder(context, sessionPlayer)
            .setId("${SESSION_ID_PREFIX}$itemId")
            .build())
    }

    /**
     * Build + activate a bare session around [player] with the given [sessionId]
     * (used by background-cast detach/reattach, which swap the player surface).
     * Releases any prior session first.
     */
    fun createForPlayer(player: Player, sessionId: String) {
        release()
        activate(MediaSession.Builder(context, player)
            .setId(sessionId)
            .build())
    }

    /** Tear down the active session. Idempotent. */
    fun release() {
        val current = session ?: return
        if (sessionManager.currentSession === current) {
            sessionManager.clearSession(current)
        }
        try { current.release() } catch (_: Exception) { }
        session = null
    }

    private fun activate(newSession: MediaSession) {
        session = newSession
        sessionManager.setActiveSession(newSession)
    }

    private companion object {
        const val SESSION_ID_PREFIX = "jellyplay_video_"
        const val ARTWORK_MAX_WIDTH = 300
    }
}
