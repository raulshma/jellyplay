package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurface
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId

/**
 * The desktop music surface adapter (the playback-focus contract) — the
 * counterpart of Android's `AudioPlaybackManagerSurface`, and the second
 * production [PlaybackSurface] that makes the command seam real. The matrix's
 * pause command lands on the manager's existing EDT-confined `pause()` (a
 * no-op when idle — `engine?.takeIf { it.isPlaying.value }`), exactly where
 * every other desktop pause path lands.
 *
 * THIS is the desktop manual-resume guard: `pause()` stops the mpv engine
 * with its `pause` property SET — the desktop playWhenReady equivalent,
 * which persists across loads — and there is no OS focus stack on desktop
 * to resurrect a paused victim when the claim releases (the arbiter never
 * fires Regained). Resume stays a user action, enforced in-process.
 *
 * The claim edge deliberately does NOT live here: the playback-focus contract keeps
 * [PlaybackSurface] the COMMAND seam only ("commandable surfaces"); the
 * claimant side stays per-surface (Android's manager claims in its Player
 * Listener, the desktop manager claims in its engine is-playing observer) —
 * so this adapter, like Android's, is a one-line pause forwarder.
 */
internal class DesktopAudioQueueManagerSurface(
    /**
     * Deferred: the manager's constructor takes the PlaybackFocus single
     * this surface belongs to, so the graph wires the manager LAZILY (the
     * Android surface + AppShortcutManager precedent) — the first pause
     * command is long after construction, and the cycle dies here.
     */
    private val manager: Lazy<DesktopAudioQueueManager>,
) : PlaybackSurface {
    override val id = PlaybackSurfaceId.MUSIC
    override fun pause() {
        manager.value.pause()
    }
}
