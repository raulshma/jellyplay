package com.raulshma.jellyplay.feature.player.video

/**
 * Audio-lifecycle seam for the video player (wave 8C): audio-focus
 * (duck/restore, pause on permanent loss) + becoming-noisy auto-pause. The
 * androidMain actual ([AndroidVideoPlayerAudio], module androidMain) wraps
 * the legacy `core:data` [PlayerAudioLifecycle][com.raulshma.jellyplay.core.data.playback.PlayerAudioLifecycle]
 * with the exact PlaybackControl adapter the ViewModel used to build inline
 * (LivePlayerAudio precedent, player-live conveyor). The jvmMain actual is a
 * no-op stub (desktop focus handling is queued work).
 */
interface VideoPlayerAudio {

    /** Whether an audio-focus request is currently active. */
    fun isAudioFocusActive(): Boolean

    /** Requests audio focus (idempotent; re-registers when already active). */
    fun registerAudioFocus()

    /** Abandons the audio-focus request. */
    fun unregisterAudioFocus()

    /** Registers the ACTION_AUDIO_BECOMING_NOISY auto-pause receiver. */
    fun registerBecomingNoisy()

    /** Releases both listeners. Idempotent; safe before any register. */
    fun release()
}
