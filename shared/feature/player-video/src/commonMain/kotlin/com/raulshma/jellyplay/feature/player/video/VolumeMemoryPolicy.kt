package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlatformKind

/**
 * Pure apply/capture ladder for per-content-type volume memory.
 * The design decision: do NOT fight Android's system-volume model — the
 * feature is scoped to platforms where the app owns a volume scalar
 * (desktop mpv video). Every gate lives here so the VM apply path is a dumb
 * dispatcher and the ladder is matrix-testable.
 */
object VolumeMemoryPolicy {

    /**
     * The level to restore at item start, or null when the session must leave
     * the engine volume untouched:
     *  - non-DESKTOP platforms never restore (Android video = system stream);
     *  - the master toggle off never restores;
     *  - a bucket with no remembered level (first ever playback) restores
     *    nothing — the engine default stands;
     *  - no live engine, nothing to restore onto.
     */
    fun restoreLevel(
        platform: PlatformKind,
        rememberEnabled: Boolean,
        storedLevel: Float?,
        enginePresent: Boolean,
    ): Float? {
        if (platform != PlatformKind.DESKTOP) return null
        if (!rememberEnabled || !enginePresent) return null
        return storedLevel
    }

    /**
     * Whether a user-initiated volume change should be WRITTEN to the bucket
     * memory: desktop only, and only while the master toggle is on. The
     * user-vs-programmatic half of the decision is upstream —
     * `PlaybackVolumePolicy.LevelPlan.isUserChange` (programmatic fades
     * `setVolume(level, isUserChange = false)` and engines never fire the
     * capture hook for them).
     */
    fun shouldCapture(platform: PlatformKind, rememberEnabled: Boolean): Boolean =
        platform == PlatformKind.DESKTOP && rememberEnabled
}
