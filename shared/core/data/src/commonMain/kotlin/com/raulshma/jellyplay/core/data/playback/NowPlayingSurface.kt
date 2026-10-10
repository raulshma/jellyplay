package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * The ONE now-playing surface the shells' music home cards read at CLICK
 * time (via `rememberShellAudioClicks`): the four StateFlow members
 * [AudioPlaybackManager] (androidMain) and [DesktopAudioQueueManager]
 * (jvmMain) already exposed verbatim through [AudioQueueManager] /
 * [AudioPlayerEngine]. Declared once here — commonMain, so both managers
 * implement it directly — replacing the per-shell private adapters that
 * forwarded the same four flows by hand.
 *
 * IDENTITY-STABILITY CONTRACT: every implementation is the app-scoped
 * Koin SINGLE for its platform — identity-stable for the app's lifetime.
 * Consumers may pass the manager straight into remember-keyed helpers
 * (the shell audio clicks pair); a fresh-per-recomposition source would
 * compare unequal every time and rebuild those helpers' outputs — and
 * through them both shells' section graphs — on every recomposition.
 */
interface NowPlayingSurface {
    /** The playing item's id, or null when nothing is playing. */
    val currentPlayingItemId: StateFlow<String?>

    /** The current track's album-art URL — "" when there is none. */
    val albumArtUrl: StateFlow<String>

    /** The current track's title. */
    val title: StateFlow<String>

    /** The current track's artist. */
    val artist: StateFlow<String>
}
