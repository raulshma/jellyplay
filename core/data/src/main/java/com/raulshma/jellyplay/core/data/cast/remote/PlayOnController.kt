package com.raulshma.jellyplay.core.data.cast.remote

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
import kotlinx.coroutines.flow.StateFlow

/**
 * Central "Play On" routing chokepoint, modelled on Jellyfin Web's
 * `playbackManager` + `SessionPlayer` design.
 *
 * Web wraps a connected remote Jellyfin session in a player object
 * (`SessionPlayer`, `isLocalPlayer = false`) that becomes the app's
 * `_currentPlayer`. Every subsequent `play()` then routes to it via a single
 * check: `if (!_currentPlayer.isLocalPlayer) return _currentPlayer.play(...)`.
 *
 * This controller is the native equivalent of that check. It is a thin facade
 * over [JellyfinRemotePlayCastStrategy] that the local players consult at their
 * play entry points:
 *
 *  - `VideoPlayerViewModel.initializeInternal`
 *  - `AudioPlaybackManager.play`
 *
 * When [isConnected] is true, callers should [fling] the item to the connected
 * session and skip local playback entirely — mirroring how Web's
 * `playbackManager.play()` delegates to `SessionPlayer.play()`. Transport
 * controls (play/pause/seek/volume) for the remote session are also proxied
 * here.
 *
 * It deliberately does NOT touch the shared [com.raulshma.jellyplay.core.data.cast.CastManager]:
 * that singleton's `activeStrategy` / connection flag is what the video
 * player's own cast UI (`isCastConnected`) reads, so routing Play On through it
 * would make the video player think it is casting and hijack it into companion
 * mode. Keeping a separate, independent connection here avoids that.
 */
class PlayOnController(
    private val strategy: JellyfinRemotePlayCastStrategy,
) {

    /** True while a Jellyfin remote session is the active "Play On" target. */
    val isConnected: StateFlow<Boolean> = strategy.isConnected

    /** Display name of the connected session, if any. */
    val targetName: StateFlow<String?> get() = strategy.targetName

    /** Connect to [device] (and start state polling). Does not fling media. */
    fun connect(context: Context, device: CastDevice) {
        strategy.connect(context, device)
    }

    /**
     * Fling an item to the connected session (PlayTo: `POST /Sessions/{id}/Playing`
     * with `PlayCommand=PlayNow`). No-op if nothing is connected. Callers should
     * gate local playback on [isConnected] before calling this.
     */
    fun fling(
        itemId: String,
        startPositionMs: Long = 0L,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        strategy.loadMedia(
            itemId = itemId,
            startPositionMs = startPositionMs,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
    }

    fun play() = strategy.play()
    fun pause() = strategy.pause()
    fun seekTo(positionMs: Long) = strategy.seekTo(positionMs)
    fun setVolume(volume: Float) = strategy.setRendererVolume(volume)
    fun disconnect(context: Context) = strategy.disconnect(context)
}
