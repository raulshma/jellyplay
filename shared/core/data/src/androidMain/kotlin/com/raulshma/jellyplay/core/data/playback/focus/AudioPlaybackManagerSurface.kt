package com.raulshma.jellyplay.core.data.playback.focus

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager

/**
 * The music surface adapter: the matrix's pause command lands on the
 * manager's existing main-confined `pause()` (a documented no-op when idle).
 *
 * THIS is the playWhenReady guard: `ExoPlayer.pause()` clears
 * `playWhenReady`, so when the read-aloud claim later releases its OS focus
 * and the media3 focus stack re-grants music, media3's focus handling sees
 * `playWhenReady = false` and does NOT auto-resume — the module's
 * manual-resume decision holds at the OS level, not just in-process.
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
