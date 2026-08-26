package com.raulshma.jellyplay.feature.player.live.engine

/**
 * The Android seam over [LivePlayerEngine] (player-live conveyor): the raw
 * Media3 `Player` surface behind the commonMain engine contract. Only
 * androidMain code touches it — the screen attaches it to a media3
 * `PlayerView`, and the audio seam pokes its volume for mute/duck. The
 * legacy interface exposed this directly; splitting it out is what lets the
 * shared ViewModel hold a `LivePlayerEngine` in commonMain.
 */
interface Media3LivePlayerEngine : LivePlayerEngine {

    /** Underlying Media3 Player for PlayerView attachment. */
    val media3Player: androidx.media3.common.Player?
}
