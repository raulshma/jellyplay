package com.raulshma.jellyplay.feature.player.video

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager

/**
 * Owns the system [MediaSession] (now-playing / lock-screen / Bluetooth metadata)
 * for the VOD player. Extracted from [VideoPlayerViewModel], continuing the
 * collaborator pattern established by [SubtitleManager] / [PlayerCastController].
 *
 * (Wave 8C: renamed from `MediaSessionController` — the commonMain
 * [MediaSessionController] seam interface took the old name; the ViewModel
 * constructs this class through [AndroidMediaSessionFactory].)
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
 * Both builders construct a [MediaLibrarySession] (not a plain [MediaSession]).
 * The host service [com.raulshma.jellyplay.core.data.playback.JellyPlayPlaybackService]
 * is a `MediaLibraryService` whose `onGetSession` casts the current session to
 * `MediaLibrarySession?`; a plain `MediaSession` would cast to null and the
 * service would reject new controller connections. Media-button / headset-hook
 * events arrive as *new* controller connections, so returning null broke
 * background pause until the app was restarted. Video never browses the library,
 * so [NO_OP_LIBRARY_CALLBACK] rejects browse calls by default.
 *
 * The controller is the sole owner of the [MediaSession] reference; the VM no
 * longer holds a `videoMediaSession` field.
 */
@OptIn(UnstableApi::class)
internal class AndroidMediaSessionController(
    private val context: Context,
    private val sessionManager: PlaybackSessionManager,
    private val getPlayer: () -> Player?,
    private val getImageUrl: (itemId: String, maxWidth: Int) -> String,
) : MediaSessionController {

    private var session: MediaSession? = null

    /**
     * Build + activate a session for the current item, pinning [title] /
     * [subtitle] + the item's artwork. No-op if no underlying player is bound.
     *
     * Same-item rebuilds (force-transcode / quality / engine-fallback reloads)
     * reuse the outgoing session's ID (`jellyplay_video_<itemId>`), and Media3
     * registers session IDs in a process-wide map *at construction time*,
     * throwing `IllegalStateException: Session ID must be unique` if the old
     * session is still registered when the replacement is built. So the held
     * session is released by [releaseSupersededSession] before building; the
     * manager slot is still vacated only atomically by
     * [PlaybackSessionManager.setActiveSession] when the new session takes it —
     * releasing via [release]'s clearSession would fire `onSessionChanged(null)`
     * and `stopSelf` the service mid-reload, and under background-start
     * restrictions the service then cannot be restarted (see
     * [PlaybackSessionManager.startPlaybackService]).
     */
    override fun createForItem(itemId: String, title: String, subtitle: String) {
        val player = getPlayer() ?: return

        releaseSupersededSession()

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

        activate(MediaLibrarySession.Builder(context, sessionPlayer, NO_OP_LIBRARY_CALLBACK)
            .setId("${SESSION_ID_PREFIX}$itemId")
            .setSessionActivity(buildPlayerSessionActivity(itemId))
            .build())
    }

    /**
     * Build + activate a bare session around [player] with the given [sessionId]
     * (used by background-cast detach/reattach, which swap the player surface).
     * [releaseSupersededSession] runs first — the caller's [sessionId] is stable
     * across reattach, so a rebuild hits the same Media3 ID-uniqueness check as
     * [createForItem]. Slot vacating stays atomic — see [createForItem].
     *
     * [videoItemId], when supplied (the local-engine reattach path), pins the
     * session activity to [buildPlayerSessionActivity] so the notification still
     * reopens the fullscreen video after a session rebuild. The background-cast
     * detach path omits it — that session's notification falls back to the app
     * launcher intent (browse UI), preserving prior behaviour.
     */
    override fun createForPlayer(player: Any?, sessionId: String, videoItemId: String?) {
        val platformPlayer = player as? Player ?: return
        releaseSupersededSession()

        val builder = MediaLibrarySession.Builder(context, platformPlayer, NO_OP_LIBRARY_CALLBACK)
            .setId(sessionId)
        videoItemId?.let { builder.setSessionActivity(buildPlayerSessionActivity(it)) }
        activate(builder.build())
    }

    /** Tear down the active session. Idempotent. */
    override fun release() {
        val current = session ?: return
        if (sessionManager.currentSession === current) {
            sessionManager.clearSession(current)
        }
        try { current.release() } catch (_: Exception) { }
        session = null
    }

    /**
     * Releases the held session ahead of a rebuild without vacating the
     * [PlaybackSessionManager] slot (the slot swap stays atomic in [activate]).
     * Needed because Media3 registers session IDs at construction time — the
     * outgoing session must be deregistered before a same-ID replacement is
     * built or the constructor throws "Session ID must be unique". Three
     * properties make this mid-rebuild release safe:
     *  - the slot keeps pointing at the retired session, so listeners never
     *    observe `onSessionChanged(null)` (which stopSelfs the service
     *    mid-reload); [activate]'s swap fires `onSessionChanged(new, old)` and
     *    the service removes the retired session from its registry then;
     *  - the retired session's player reads `isPlaying == false` (ExoPlayer
     *    masks `STATE_IDLE` on release), so [PlaybackSessionManager]'s
     *    priority guard cannot reject the incoming rebuild;
     *  - [MediaSession.release] is idempotent, so the slot swap's second
     *    release of the retired session is a no-op.
     */
    private fun releaseSupersededSession() {
        val current = session ?: return
        try { current.release() } catch (_: Exception) { }
        session = null
    }

    private fun activate(newSession: MediaSession) {
        session = newSession
        sessionManager.setActiveSession(newSession)
    }

    /**
     * PendingIntent that the system fires when the user taps the media
     * notification / lock-screen artwork — reopens the fullscreen
     * [PLAYER_ACTIVITY_CLASS_NAME] (PlayerActivity).
     *
     * PlayerActivity lives in the `app` module, which this feature module
     * cannot compile against, so it is referenced by class name. [EXTRA_ITEM_ID_KEY]
     * mirrors `PlayerActivityArgs.EXTRA_ITEM_ID` — the app module's single
     * build/parse adapter for the PlayerActivity launch contract (the same
     * contract the `PlaybackHostRouter` DedicatedActivity branch builds).
     *
     * Because PlayerActivity is `singleTask` and shares the default
     * taskAffinity, firing this while a PlayerActivity instance is alive — the
     * PiP case, where it is the floating window — brings that task forward and
     * expands it out of PiP instead of recreating the activity. Without a
     * session activity the notification content intent fell back to the app
     * launcher (MainActivity), so tapping it from PiP dropped the user on the
     * browse UI rather than the fullscreen video.
     */
    private fun buildPlayerSessionActivity(itemId: String): PendingIntent {
        val intent = Intent().apply {
            setClassName(context, PLAYER_ACTIVITY_CLASS_NAME)
            putExtra(EXTRA_ITEM_ID_KEY, itemId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            // Per-item request code so rebuilt/overlapping sessions don't alias.
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SESSION_ID_PREFIX = "jellyplay_video_"
        const val ARTWORK_MAX_WIDTH = 300

        // PlayerActivity lives in the `app` module; reference by class name.
        private const val PLAYER_ACTIVITY_CLASS_NAME = "com.raulshma.jellyplay.PlayerActivity"
        // Must match PlayerActivityArgs.EXTRA_ITEM_ID ("player_item_id") —
        // the app module owns the launch contract; a value change here (or
        // there) breaks notification→PlayerActivity silently. The app-side
        // PlayerActivityArgs round-trip test pins the literal both must agree on.
        private const val EXTRA_ITEM_ID_KEY = "player_item_id"

        /**
         * Video playback never exposes a browsable library; the callback exists
         * only to satisfy [MediaLibrarySession]'s constructor so the host
         * `MediaLibraryService` will serve the session from `onGetSession`
         * (and thus accept media-button / headset-hook controller connections).
         * The base [MediaLibrarySession.Callback] returns
         * `RESULT_ERROR_NOT_SUPPORTED` for every library method by default, which
         * is the correct behaviour for a non-browsable video session.
         */
        val NO_OP_LIBRARY_CALLBACK = object : MediaLibrarySession.Callback {}
    }
}
