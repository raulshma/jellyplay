package com.raulshma.jellyplay.feature.player.live.engine

import com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel

/**
 * Platform seam for the audio concerns the live player ViewModel needs
 * (player-live conveyor): the Android audio-focus / becoming-noisy lifecycle
 * (legacy `PlayerAudioLifecycle` in :core:data) and the raw Media3 player
 * volume access behind the mute toggle. The commonMain ViewModel drives the
 * seam; the androidMain actual (`Media3LivePlayerAudio`) wraps the legacy
 * PlayerAudioLifecycle with the exact PlaybackControl adapter the VM used to
 * build inline. Desktop has no live engine, so no actual is registered there
 * — the ctor default is null and every seam call is a no-op.
 *
 * [bind] is invoked from the ViewModel's `init` (the platform impl reads the
 * engine + mute state lazily through the owner, so audio callbacks always
 * observe the *current* engine — the same re-read-on-every-callback contract
 * the legacy inline adapter had).
 */
interface LivePlayerAudio {

    /** Bind the owning ViewModel; called once from its `init`. */
    fun bind(owner: LiveTvPlayerViewModel)

    /**
     * Current raw player volume, or null while no platform player is
     * attached (the mute toggle no-ops in that case, matching the legacy
     * `engine?.media3Player ?: return` guard).
     */
    fun playerVolume(): Float?

    /** Set the raw player volume (`0f` = mute). No-op without a player. */
    fun setPlayerVolume(volume: Float)

    /**
     * Register audio-focus + becoming-noisy listeners; called once when the
     * (reused) engine instance is created, before the first load.
     */
    fun onEngineCreated()

    /**
     * Release audio-focus + becoming-noisy listeners; called from
     * [LiveTvPlayerViewModel.stop] before the engine is released so the
     * listeners never dereference a torn-down player.
     */
    fun onReleased()
}
