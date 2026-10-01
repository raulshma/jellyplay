package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.BecomingNoisyPauseReceiver
import com.raulshma.jellyplay.core.data.playback.focus.FocusCommandTarget
import com.raulshma.jellyplay.core.data.playback.focus.VideoPlaybackSurface
import com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel

/**
 * Android actual of the [LivePlayerAudio] seam (player-live conveyor): the
 * becoming-noisy receiver half of the deleted `PlayerAudioLifecycle` wrapper
 * (plus the raw-player volume access behind `toggleMute`), with the focus
 * surface binding the video slice added — the current engine's Media3 player
 * is bound into the focus module's [VideoPlaybackSurface] singleton as a
 * [FocusCommandTarget], so the module's OS-loss commands (pause / duck /
 * restore) reach the live stream exactly where the legacy duck/restore
 * listener used to. Mute is re-asserted as `volume = 0f` — live has no
 * `setMuted`, and no resume-skip hook (the legacy live wiring passed
 * `onRegain = null`). The broadcast chassis lives in core:data's
 * [BecomingNoisyPauseReceiver] (one home, shared with the VOD player); this
 * class supplies the raw-player pause target.
 *
 * Constructed per-ViewModel by `androidPlayerLiveModule`; `bind` is invoked
 * from the ViewModel's `init`.
 */
internal class Media3LivePlayerAudio(
    context: Context,
    /** Null where no video surface is bound (desktop never resolves this actual). */
    private val videoFocusSurface: VideoPlaybackSurface?,
) : LivePlayerAudio {

    private var owner: LiveTvPlayerViewModel? = null

    private val becomingNoisyReceiver = BecomingNoisyPauseReceiver(context) { player()?.pause() }

    override fun bind(owner: LiveTvPlayerViewModel) {
        this.owner = owner
    }

    override fun playerVolume(): Float? = player()?.volume

    override fun setPlayerVolume(volume: Float) {
        player()?.volume = volume
    }

    override fun onEngineCreated() {
        becomingNoisyReceiver.register()
        videoFocusSurface?.bind(
            target = {
                player()?.let { player ->
                    Media3LiveFocusTarget(player) { owner?.state?.value?.isMuted ?: false }
                }
            },
        )
    }

    override fun onReleased() {
        becomingNoisyReceiver.release()
        videoFocusSurface?.unbind()
    }

    private fun player(): androidx.media3.common.Player? =
        (owner?.engineForRendering() as? Media3LivePlayerEngine)?.media3Player
}

/**
 * The live focus-surface command target over the raw Media3 player (the
 * legacy `PlaybackControl` adapter's shape): volume/mute read the player
 * directly, [isMuted] stays the VM's uiState mirror (the same surface
 * [LiveTvPlayerViewModel.toggleMute] writes), and mute is re-asserted as
 * `volume = 0f` — live has no separate mute lever.
 */
private class Media3LiveFocusTarget(
    private val player: androidx.media3.common.Player,
    private val isMutedState: () -> Boolean,
) : FocusCommandTarget {
    override val isPlaying: Boolean get() = player.isPlaying
    override val volume: Float get() = player.volume
    override val isMuted: Boolean get() = isMutedState()
    override fun pause() = player.pause()
    override fun setMuted(muted: Boolean) {
        if (muted) player.volume = 0f
    }
    override fun setVolume(volume: Float, isUserChange: Boolean) {
        player.volume = volume
    }
}
