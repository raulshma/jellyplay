package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * System media-session seam for the video player: the member set
 * the commonMain [VideoPlayerViewModel] and [PlaybackSession] call. The
 * androidMain class was renamed [AndroidMediaSessionController][com.raulshma.jellyplay.feature.player.video.AndroidMediaSessionController]
 * (module androidMain) and implements this interface; it keeps the media3
 * MediaLibrarySession construction, the pinned-metadata ForwardingPlayer and
 * the session-ID uniqueness choreography unchanged.
 *
 * No member carries an opaque player handle: [createForPlayer] takes the
 * typed [MediaEngine] (the androidMain impl narrows it to media3 `Player`
 * via `MediaEngine.asMedia3Player()` and no-ops when the engine hosts none),
 * and [createForBackgroundCast] resolves the cast receiver's player behind a
 * provider wired in androidMain. The jvmMain actual is a no-op (no desktop
 * media-session integration).
 */
interface MediaSessionController {

    /**
     * Builds + activates a session for the current item, pinning the
     * title/subtitle + artwork. No-op without a bound platform player.
     */
    fun createForItem(itemId: String, title: String, subtitle: String)

    /**
     * Builds + activates a bare session around [engine]'s platform player
     * (local-engine reattach path); [videoItemId], when supplied, pins the
     * session activity to the fullscreen video item. No-op when [engine] is
     * null or hosts no media3 player.
     */
    fun createForPlayer(engine: MediaEngine?, sessionId: String, videoItemId: String? = null)

    /**
     * Builds + activates a bare session around the active cast receiver's
     * player (background-cast detach path). No-op when no cast player is
     * active.
     */
    fun createForBackgroundCast(sessionId: String)

    /** Tears down the active session. Idempotent. */
    fun release()
}

/**
 * Factory seam replacing the ViewModel's former direct construction of the
 * androidMain controller with a legacy
 * [PlaybackSessionManager][com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager]
 * + `Context`: the androidMain actual captures both, narrows [getEngine]'s
 * engine to its media3 player, and owns the cast-player provider behind
 * [MediaSessionController.createForBackgroundCast]. The jvmMain actual
 * produces a no-op controller.
 */
fun interface VideoMediaSessionFactory {

    fun create(
        getEngine: () -> MediaEngine?,
        getImageUrl: (itemId: String, maxWidth: Int) -> String,
    ): MediaSessionController
}
