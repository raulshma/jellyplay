package com.raulshma.jellyplay.core.data.playback

/**
 * The media-service face over the Android audio family — the two lifecycle
 * verbs the media3 host service ([com.raulshma.jellyplay.core.data.playback.JellyPlayPlaybackService])
 * drives: the Android Auto / Automotive cold-connect ensure (an idle player +
 * MediaLibrarySession so a car client binding before any play gets a session,
 * not null) and the teardown (release the player + effects + session). One
 * role per face (the [ShellAudioSurface] role-split idiom): exactly what the
 * service reads, nothing else.
 *
 * Satisfied directly by [AudioPlaybackManager]; the Koin alias rides
 * androidPlaybackStackModule beside the other interface aliases — ONLY the DI
 * module names the concrete class, so the service resolves the interface and
 * test suites can bind a fake.
 */
interface AudioServiceSurface {
    /**
     * Guarantees the audio session exists without starting playback.
     * Idempotent; main thread only.
     */
    fun ensureAudioSession()

    /** Stop and tear the engine + session down (the service's release paths). */
    fun stopAndRelease()
}
