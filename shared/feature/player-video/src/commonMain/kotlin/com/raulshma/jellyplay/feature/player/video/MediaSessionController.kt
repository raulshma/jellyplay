package com.raulshma.jellyplay.feature.player.video

/**
 * System media-session seam for the video player (wave 8C): the member set
 * the commonMain [VideoPlayerViewModel] and [PlaybackSession] call. The
 * androidMain class was renamed [AndroidMediaSessionController][com.raulshma.jellyplay.feature.player.video.AndroidMediaSessionController]
 * (module androidMain) and implements this interface; it keeps the media3
 * MediaLibrarySession construction, the pinned-metadata ForwardingPlayer and
 * the session-ID uniqueness choreography unchanged.
 *
 * [createForPlayer]'s [player] is the opaque platform player handle (media3
 * `Player` on Android) — the Android impl narrows it and no-ops when it is
 * not a Player, matching the ViewModel's former `as? Player ?: return` guard.
 * The jvmMain actual is a no-op (no desktop media-session integration).
 */
interface MediaSessionController {

    /**
     * Builds + activates a session for the current item, pinning the
     * title/subtitle + artwork. No-op without a bound platform player.
     */
    fun createForItem(itemId: String, title: String, subtitle: String)

    /**
     * Builds + activates a bare session around [player] (background-cast
     * detach/reattach path); [videoItemId], when supplied, pins the session
     * activity to the fullscreen video item.
     */
    fun createForPlayer(player: Any?, sessionId: String, videoItemId: String? = null)

    /** Tears down the active session. Idempotent. */
    fun release()
}

/**
 * Factory seam replacing the ViewModel's former direct construction of the
 * androidMain controller with a legacy
 * [PlaybackSessionManager][com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager]
 * + `Context`: the androidMain actual captures both; [getPlayer] returns the
 * opaque platform player (`MediaEngine.underlyingPlayer`). The jvmMain actual
 * produces a no-op controller.
 */
fun interface VideoMediaSessionFactory {

    fun create(
        getPlayer: () -> Any?,
        getImageUrl: (itemId: String, maxWidth: Int) -> String,
    ): MediaSessionController
}
