package com.raulshma.jellyplay.feature.player.video

import kotlinx.coroutines.flow.StateFlow

/**
 * "Play On" remote-session strategy seam for the video player (wave 8C): the
 * member set the commonMain [VideoPlayerViewModel] calls on the legacy
 * `core:data` strategy singleton. The Home "Play On" feature keeps using the
 * legacy instance directly; the androidMain adapter
 * ([AndroidJellyfinRemotePlayCastStrategy], module androidMain) wraps it. The
 * jvmMain actual reports `isConnected = false` so routing never triggers.
 */
interface JellyfinRemotePlayCastStrategy {

    /** Whether a Jellyfin remote session is currently connected. */
    val isConnected: StateFlow<Boolean>

    /**
     * Sends the item to the connected remote session ("PlayNow") with the
     * caller's media-source and track selection carried over. No-op without a
     * connected session.
     */
    fun loadMedia(
        itemId: String,
        startPositionMs: Long = 0,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    )
}
