package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * The app shell's audio playback surface — the narrow contract the Android
 * shell (MainContent → TvContent / PhoneContent / AppMiniPlayerHost)
 * consumes off the audio core: the [NowPlayingSurface] metadata flows the
 * mini-player renders and the audio-clicks helper reads at click time, plus
 * the one read ([isPlaying]) and the three transport commands
 * ([togglePlayPause] / [skipToNext] / [stopAndRelease]) the mini-player's
 * wiring calls. Declared so shell code is constructible against a fake on
 * the JVM — the former concrete [AudioPlaybackManager] parameter dragged the
 * 21-param media3 construction into every shell signature while using eight
 * members of it (and none of its Android types).
 *
 * Deliberately extends [NowPlayingSurface] rather than re-declaring its
 * flows: the manager IS the shared now-playing surface (the identity-stability
 * contract on that interface carries over — implementations are the
 * app-scoped Koin single), and [rememberShellAudioClicks]' parameter accepts
 * this surface unchanged. [AudioNowPlayingFace] stays the remote
 * reporter/tile face and keeps its pull member — the shell needs none of it.
 *
 * Satisfied directly by [AudioPlaybackManager] (androidMain — every member
 * is an existing override for [AudioQueueManager] / [AudioPlayerEngine] /
 * [AudioNowPlayingFace], so the supertype list is the only delta); the Koin
 * binding rides androidPlaybackStackModule beside the other interface
 * aliases. The desktop shell has no Android manager to bind — its player
 * family is desktopPlayerModule's, and none of its files consumed the
 * concrete Android class.
 */
interface ShellAudioSurface : NowPlayingSurface {
    /** The audio engine's playing state — the mini-player's transport glyph. */
    val isPlaying: StateFlow<Boolean>

    /** Play/pause toggle (the mini-player's center transport button). */
    fun togglePlayPause()

    /** Skip to the next queue entry. */
    fun skipToNext()

    /** Stop and tear the engine down (the mini-player's dismiss action). */
    fun stopAndRelease()
}
