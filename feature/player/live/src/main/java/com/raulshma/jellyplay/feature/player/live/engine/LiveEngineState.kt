package com.raulshma.jellyplay.feature.player.live.engine

/**
 * Coarse player state surfaced to the live TV UI. Mirrors the subset of
 * [androidx.media3.common.Player] playback states that drive the rebuffer
 * spinner, the error banner, and the now/next overlay visibility.
 */
enum class LiveEngineState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR,
}
