package com.raulshma.jellyplay.feature.player.video

/**
 * Audio-lifecycle seam for the video player: the ACTION_AUDIO_BECOMING_NOISY
 * auto-pause (headphone unplug) receiver — and, on the Android actual, the
 * opt-in resume-on-headset-insert twin — only. The
 * audio-FOCUS half of this seam died with the video focus slice — the OS seat
 * moved into core:data's PlaybackFocus module (the deleted
 * `PlayerAudioLifecycle`'s focus request and the ExoPlayer-builtin
 * `handleAudioFocus` both collapsed onto the one module-owned seat), so the
 * androidMain actual no longer wraps it.
 *
 * The receiver stays a seam because it is Android-only machinery with an
 * engine dependence (the pause must reach the CURRENT engine — mpv/libVLC
 * have no media3-native becoming-noisy path; ExoPlayer ships
 * `setHandleAudioBecomingNoisy(true)` itself and the receiver is its
 * belt-and-suspenders twin, exactly the posture the shared lifecycle had).
 * The jvmMain actual is a no-op stub (desktop has no such broadcast).
 *
 * Constructed per-ViewModel by the platform seam (`createBecomingNoisy`);
 * `register` is invoked from the wiring's arm phase, `release` from the
 * full teardown.
 */
interface VideoPlayerAudio {

    /** Registers the ACTION_AUDIO_BECOMING_NOISY auto-pause receiver (and the Android resume-on-plug twin). */
    fun register()

    /**
     * Unregisters the receiver. Idempotent; safe before any register.
     */
    fun release()
}
