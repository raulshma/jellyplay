package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.focus.FocusCommandTarget
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * The VOD focus-surface command target: adapts the current [MediaEngine]
 * onto the core:data [FocusCommandTarget] shape the duck/restore round-trip
 * drives (the legacy inline `PlaybackControl` over the engine, carried
 * verbatim at the video focus slice). [isMuted] stays the VM's uiState
 * mirror — the engine surface has no muted getter, and the legacy
 * duck-while-muted guard read exactly that mirror.
 *
 * Fields are lambda-backed through the live engine reference the wiring
 * re-resolves per command (`VideoPlaybackSurface.bind`'s provider), so an
 * engine swap mid-duck is observed by the next callback.
 */
internal class MediaEngineFocusTarget(
    private val engine: MediaEngine,
    private val isMutedState: () -> Boolean,
) : FocusCommandTarget {

    override val isPlaying: Boolean get() = engine.isPlaying.value

    override val volume: Float get() = engine.volume

    override val isMuted: Boolean get() = isMutedState()

    override fun pause() = engine.pause()

    override fun setMuted(muted: Boolean) = engine.setMuted(muted)

    override fun setVolume(volume: Float, isUserChange: Boolean) =
        engine.setVolume(volume, isUserChange)
}
