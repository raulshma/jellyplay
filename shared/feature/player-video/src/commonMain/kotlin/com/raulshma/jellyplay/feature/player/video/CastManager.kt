package com.raulshma.jellyplay.feature.player.video

/**
 * Cast-manager seam for the video player (wave 8C): the member set the
 * commonMain [VideoPlayerViewModel] calls on the legacy `core:data`
 * `CastManager` singleton. The full discovery/connect surface (Context-bound
 * startDiscovery/connect, cast-device types) stays on the legacy class — the
 * screen reaches it through the androidMain `androidCastManager` extension.
 *
 * The androidMain adapter ([AndroidCastManager], module androidMain) wraps
 * the Hilt-owned legacy singleton; `castPlayerForSession` is surfaced as the
 * opaque [Any] because the platform player handle (media3 `Player`) must not
 * leak into common code. The jvmMain actual is a no-op stub.
 */
interface CastManager {

    /** Registers this player as the active cast consumer (ref-counted). */
    fun acquireConsumer()

    /** Releases the consumer registration (full teardown path). */
    fun releaseConsumer()

    /** Marks whether playback is currently handed off to a cast receiver. */
    fun markBackgroundCasting(casting: Boolean)

    /** Whether playback is currently handed off to a cast receiver. */
    val isBackgroundCasting: Boolean

    /**
     * Soft-releases the cast player for a background-cast handoff (keeps the
     * session alive for the receiver).
     */
    fun softRelease()

    /**
     * The cast receiver's player for the active session (opaque platform
     * handle; media3 `Player?` on Android), or null when playing locally.
     */
    val castPlayerForSession: Any?
}
