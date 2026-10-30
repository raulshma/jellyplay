package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine

/**
 * The live player's system [androidx.media3.session.MediaSession] seam — the
 * `player-video` [com.raulshma.jellyplay.feature.player.video.MediaSessionController]
 * shape at live scale (gh-178). Live TV previously shipped no session at all,
 * so headset / Bluetooth media buttons and lock-screen controls were dead
 * during live playback; the androidMain actual builds a `MediaLibrarySession`
 * around the active [engine.LivePlayerEngine]'s media3 player and registers it
 * through the shared `core:data` [com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager]
 * (whose priority guard arbitrates against the VOD / audio sessions).
 *
 * Desktop never composes the live screen, so no jvm binding exists — the
 * factory is resolved `getOrNull()` and every call site null-guards (the same
 * documented-latent stance the module takes for the audio / PiP seams).
 */
interface LiveMediaSessionController {

    /**
     * Builds + activates a session for [channelId], pinning [title] (and the
     * channel artwork the factory's image-url lambda resolves) at the session
     * layer. [imageTag] is the channel's `LiveTvChannel.imageTag` — it rides
     * the artwork URL as the `?tag=` etag cache-buster so a re-uploaded logo
     * mints a new cache key on the notification surface too. No-op when no
     * engine is bound (the same guard the VOD controller applies). Re-tunes
     * replace the session; a same-channel re-tune
     * ([LivePlaybackSession.retry]) reuses the outgoing id, so the outgoing
     * session is released first — the Media3 session-ID uniqueness rule the
     * VOD controller documents.
     */
    fun createForChannel(channelId: String, title: String, imageTag: String? = null)

    /** Tears down the active session. Idempotent. */
    fun release()
}

/**
 * Constructs the platform controller per [LiveTvPlayerViewModel] — the
 * [com.raulshma.jellyplay.feature.player.video.VideoMediaSessionFactory] SAM
 * shape. [getEngine] is re-read on every [LiveMediaSessionController.createForChannel]
 * so the controller follows engine creation; [getImageUrl] resolves channel
 * artwork (blank string = no artwork) with the channel's image tag forwarded.
 */
fun interface LiveMediaSessionFactory {
    fun create(
        getEngine: () -> LivePlayerEngine?,
        getImageUrl: (channelId: String, maxWidth: Int, tag: String?) -> String,
    ): LiveMediaSessionController
}
