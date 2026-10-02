package com.raulshma.jellyplay.feature.player.live.engine

import com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel

/**
 * Platform seam for the audio concerns the live player ViewModel needs
 * (player-live conveyor): the Android becoming-noisy receiver (headphone
 * unplug auto-pause) and the raw Media3 player volume access behind the mute
 * toggle. The audio-FOCUS half of this seam died with the video focus slice
 * — the legacy `PlayerAudioLifecycle` duck/restore request collapsed onto
 * the core:data PlaybackFocus module's one seat (the commonMain ViewModel
 * claims [com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId.VIDEO]
 * on the play edge itself; the androidMain actual binds the current player as
 * that module's commandable VIDEO surface target, so OS losses come back as
 * pause/duck commands).
 *
 * Desktop has no live engine, so no actual is registered there — the module
 * wiring passes `audio = get()` explicitly, so a desktop VM resolution fails
 * fast with NoDefinitionFound (Route.LiveTvChannelPlayer is
 * dead-end-guarded, nothing reaches it); the null ctor default exists for
 * jvmTest only.
 *
 * [bind] is invoked from the ViewModel's `init` (the platform impl reads the
 * engine + mute state lazily through the owner, so callbacks always observe
 * the *current* engine — the same re-read-on-every-callback contract the
 * legacy inline adapter had).
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
     * Register the becoming-noisy receiver and bind the focus surface
     * target; called once when the (reused) engine instance is created,
     * before the first load.
     */
    fun onEngineCreated()

    /**
     * Unregister the receiver and unbind the focus surface target; called
     * from [LiveTvPlayerViewModel.stop] before the engine is released so
     * nothing ever dereferences a torn-down player.
     */
    fun onReleased()
}
