package com.raulshma.jellyplay.core.data.playback.focus

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager

/**
 * The music surface adapter: the matrix's pause command lands on the
 * manager's existing main-confined `pause()` (a documented no-op when idle).
 *
 * THIS is the playWhenReady guard, on both command paths the matrix sends
 * through it: the victim pause (read-aloud took the floor) and — since the
 * music OS-leg migration — the suspended-holder pause (OS loss on the MUSIC
 * seat; without it the holder would keep playing unfocused, having no
 * observer of its own). `ExoPlayer.pause()` clears `playWhenReady`, so
 * neither the claim's release nor the ignored `Regained` event can
 * auto-resume music — resume stays manual. (Since the migration slice music
 * has no media3 focus stack of its own at all — `handleAudioFocus` is off —
 * so there is no focus-stack path left to fight this guard.)
 *
 * The same command also stops the crossfade secondary mid-fade (the
 * manager's pause covers the not-yet-promoted player).
 */
internal class AudioPlaybackManagerSurface(
    /**
     * Deferred: the manager's constructor takes the [PlaybackFocus] single
     * this surface belongs to, so the graph wires the manager LAZILY (the
     * AppShortcutManager precedent) — the first pause command is long after
     * construction, and the cycle dies here.
     */
    private val manager: Lazy<AudioPlaybackManager>,
) : PlaybackSurface {
    override val id = PlaybackSurfaceId.MUSIC
    override fun pause() {
        manager.value.pause()
    }
}
