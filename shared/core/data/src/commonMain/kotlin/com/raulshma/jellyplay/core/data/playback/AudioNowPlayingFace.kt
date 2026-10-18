package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * The audio family's read face for [NowPlayingVerdict] — the five members
 * the verdict's folds consume off the audio manager, declared here because
 * [AudioPlaybackManager] (androidMain) and `DesktopAudioQueueManager`
 * (jvmMain) implement them directly (the same one-object-many-contracts
 * shape as [AudioQueueManager]/[AudioPlayerEngine]/[NowPlayingSurface]).
 *
 * Deliberately NOT the metadata surface: [NowPlayingSurface] stays the
 * shells' audio metadata surface and keeps its own four members (three of
 * which this face re-declares as the verdict's inputs — the flows are the
 * same by-reference instances). This face exists so the verdict can sit in
 * commonMain beside it while its video input
 * (`ActivePlayerController`) is equally platform-free; nothing here is
 * Android-shaped.
 *
 * Four of the five members are read-only flow re-exposures. The fifth,
 * [hasActiveSession], is the one PULL (non-flow) member and the one the
 * two managers compute rather than re-expose: "a loaded, resumable audio
 * session exists" = a live engine AND a claimed current item — on Android
 * `exoPlayer != null && currentItemId != null`, on desktop the exact twin
 * over its own engine cell. It cannot be derived from the flows: the item
 * CLAIM (the chassis cell) is written synchronously at `play()` entry
 * while the tracker flow that feeds [currentPlayingItemId] publishes only
 * after the detail resolve lands — the two disagree inside that window,
 * which is precisely the difference the reporter's gate and the tile's
 * paused-vs-inactive fold read through this member.
 */
interface AudioNowPlayingFace {
    /** The audio engine's playing state — [NowPlayingSurface]-adjacent, by reference. */
    val isPlaying: StateFlow<Boolean>

    /** The audio engine's last published position, in ms. */
    val currentPosition: StateFlow<Long>

    /** The tracker's published item id — null when nothing is presented. */
    val currentPlayingItemId: StateFlow<String?>

    /** The tracker's title — the tile's label input, by reference. */
    val title: StateFlow<String>

    /** A loaded, resumable audio session exists (live engine AND claimed item). */
    val hasActiveSession: Boolean
}
