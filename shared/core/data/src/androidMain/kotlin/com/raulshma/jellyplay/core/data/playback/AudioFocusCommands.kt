package com.raulshma.jellyplay.core.data.playback

/**
 * The cross-player-exclusivity face over the Android audio family — the three
 * commands the focus matrix sends the MUSIC seat ([com.raulshma.jellyplay.core.data.playback.focus.AudioPlaybackManagerSurface]
 * delegates to it): the loss pause (victim or suspended-holder), the
 * "Duck on Phone Call" software-gain duck, and the regain restore. One role
 * per face (the [ShellAudioSurface] role-split idiom): exactly what the focus
 * executor reads, nothing else.
 *
 * Satisfied directly by [AudioPlaybackManager] (pause is the existing
 * [AudioPlayerEngine] override; duck/restore are the manager's existing
 * concrete verbs); the Koin alias rides androidPlaybackStackModule beside the
 * other interface aliases — ONLY the DI module names the concrete class.
 */
interface AudioFocusCommands {
    /**
     * Pause the audio engine (and any in-flight crossfade secondary). The
     * manager's main-confined pause — a documented no-op when idle.
     */
    fun pause()

    /** The transient-loss duck (mute-guarded, never user-volume memory). */
    fun duck(volume: Float)

    /** Restore the level captured at [duck] time (no-op when never ducked). */
    fun restoreFromDuck()
}
