package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * Cast-to-device controller seam for the video player (wave 8C): the
 * member set the commonMain [VideoPlayerViewModel] and the screen use on the
 * androidMain class formerly named `PlayerCastController` (renamed
 * [AndroidPlayerCastController][com.raulshma.jellyplay.feature.player.video.AndroidPlayerCastController],
 * which implements this interface). Members whose signatures carry
 * Android/legacy types (Context-bound `disconnect`, the legacy
 * `CastSessionEvent` flow) stay on the Android class — the screen reaches
 * them through the androidMain `androidCast` extension. The jvmMain actual is
 * a no-op stub (no desktop cast stack).
 */
interface PlayerCastController {

    /** Whether a cast route is available at all (device discovery state). */
    val isCastAvailable: Boolean

    /** Whether a cast receiver is currently connected. */
    val isCastConnected: Boolean

    /** Cast receiver playback position. */
    val castPositionMs: StateFlow<Long>

    /** Cast receiver item duration. */
    val castDurationMs: StateFlow<Long>

    /** Whether the cast receiver is playing. */
    val castIsPlaying: StateFlow<Boolean>

    /** Cast receiver volume (0..1). */
    val castVolumeFlow: StateFlow<Float>

    /** Connection state (route selected, session establishing). */
    val isConnectedFlow: StateFlow<Boolean>

    val isConnectingFlow: StateFlow<Boolean>

    /** Whether playback is currently handed off to a cast receiver. */
    val isBackgroundCasting: Boolean

    /** Whether background casting is enabled in preferences. */
    val backgroundCastingEnabled: Boolean

    /** Transport: play on the cast receiver. */
    fun castPlay()

    /** Transport: pause the cast receiver. */
    fun castPause()

    /** Transport: seek the cast receiver. */
    fun castSeekTo(positionMs: Long)

    /** Transport: set the cast receiver volume. */
    fun setCastVolume(volume: Float)

    /**
     * Resumes local playback after a cast disconnect (only when the local
     * engine is paused by the earlier handoff).
     */
    fun onCastDisconnected()

    /**
     * Hands the current item off to the active cast receiver (resolves a
     * stream URL at the engine position, carries track/quality selection).
     */
    fun castToDevice()

    /**
     * Re-picks the cast strategy when an engine binds (DLNA is sticky,
     * otherwise Google Cast).
     */
    fun updateCastStrategyForEngine(engine: MediaEngine)
}
