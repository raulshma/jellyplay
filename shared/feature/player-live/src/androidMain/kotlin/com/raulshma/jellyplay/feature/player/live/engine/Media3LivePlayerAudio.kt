package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.PlayerAudioLifecycle
import com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel

/**
 * Android actual of the [LivePlayerAudio] seam (player-live conveyor): the
 * legacy `PlayerAudioLifecycle` audio-focus/becoming-noisy wrapper the
 * ViewModel used to construct inline (plus the raw-player volume access
 * behind `toggleMute`), extracted verbatim. The control adapter reaches the
 * current engine's Media3 player through the owner and re-asserts mute as
 * `volume = 0f` — live has no `setMuted`, and no resume-skip hook (the
 * legacy live wiring passed `onRegain = null`).
 *
 * Constructed per-ViewModel by `androidPlayerLiveModule`; `bind` is invoked
 * from the ViewModel's `init`.
 */
internal class Media3LivePlayerAudio(
    context: Context,
) : LivePlayerAudio {

    private var owner: LiveTvPlayerViewModel? = null

    private val delegate = PlayerAudioLifecycle(
        context = context,
        control = {
            player()?.let { player ->
                PlayerAudioLifecycle.PlaybackControl(
                    isPlaying = { player.isPlaying },
                    volume = { player.volume },
                    pause = { player.pause() },
                    play = { player.play() },
                    setVolume = { player.volume = it },
                    setMuted = { if (it) player.volume = 0f },
                )
            }
        },
        isMuted = { owner?.state?.value?.isMuted ?: false },
    )

    override fun bind(owner: LiveTvPlayerViewModel) {
        this.owner = owner
    }

    override fun playerVolume(): Float? = player()?.volume

    override fun setPlayerVolume(volume: Float) {
        player()?.volume = volume
    }

    override fun onEngineCreated() {
        delegate.registerBecomingNoisy()
        delegate.registerAudioFocus()
    }

    override fun onReleased() = delegate.release()

    private fun player(): androidx.media3.common.Player? =
        (owner?.engineForRendering() as? Media3LivePlayerEngine)?.media3Player
}
