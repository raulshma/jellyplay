package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Pure, testable volume/mute choreography shared by the three reloadable
 * engines (ExoPlayer / MPV / libVLC). Extracted from the three hand-mirrored
 * setVolume/increaseVolume/decreaseVolume/setMuted bodies, which had drifted:
 * libVLC's unmute discarded the remembered level and snapped its native volume
 * to full loudness, and the boost ceilings differed silently per adapter. The
 * engines only APPLY the returned plans to their native handles and to the
 * system music stream; every decision — clamp bound, remember target, snapshot
 * ordering, native-restore vocabulary — lives here so the adapters cannot
 * drift apart.
 *
 * All magnitudes are normalized (1.0 == nominal loudness). Converting to a
 * native scale (Media3 0..1 float, mpv 0..100 double percent, libVLC 0..200
 * int percent) is the adapter's apply step.
 *
 * Public (not internal) because [ReloadablePlayerEngine]'s protected seam
 * `nativeVolumeRestore` returns [NativeVolumeRestore] — a protected member
 * cannot expose an internal type. Consumers stay the engine adapters and
 * the policy test; this is not a stable API surface.
 */
object PlaybackVolumePolicy {

    /**
     * Loudness ceiling in normalized units. libVLC's software amplification
     * legitimately boosts above nominal (MediaPlayer.volume accepts 0..200), so
     * its ceiling is 2.0; Media3's float volume and the mpv adapter's UI
     * contract cap at 1.0. Declared as data here instead of hiding as copy
     * variance in per-adapter coerce calls.
     */
    const val MAX_BOOST_NOMINAL = 1.0f
    const val MAX_BOOST_VLC = 2.0f

    /**
     * What the adapter should write to its native volume handle on a mute /
     * unmute transition. mpv owns a real mute flag, so its native *volume*
     * stays untouched on both transitions; Media3 has none, so its unmute
     * restores the handle to full and lets the system stream carry the
     * remembered level; libVLC mutes by zeroing its volume, so unmute must
     * write the remembered level back — not a hardcoded full loudness.
     */
    enum class NativeVolumeRestore { ZERO, FULL, REMEMBERED_LEVEL, LEAVE_UNCHANGED }

    /** Decision for one volume application (set / increase / decrease). */
    data class LevelPlan(
        /** Normalized target for the native handle (0..maxBoost). */
        val normalized: Float,
        /** System music-stream sync value (0..1, no amplification). */
        val systemStream: Float,
    )

    /**
     * Clamps [raw] to the engine's declared boost ceiling and derives the
     * system-stream sync value. The caller routes [LevelPlan.normalized]
     * through the base `rememberUnmuteVolumeIfAudible` so the remembered
     * unmute level updates exactly when the applied level is audible.
     */
    fun planLevel(raw: Float, maxBoost: Float): LevelPlan {
        val clamped = raw.coerceIn(0f, maxBoost)
        return LevelPlan(normalized = clamped, systemStream = clamped.coerceIn(0f, 1f))
    }

    /** Decision for muting. */
    data class MutePlan(
        /** Native volume write; null = leave the handle alone (mpv mute flag). */
        val nativeVolume: Float?,
        val systemStream: Float,
        /**
         * The caller must snapshot the system stream (base
         * `snapshotSystemVolumeForMute`) BEFORE applying this plan: snapshotting
         * after zeroing would remember the mute itself as the unmute level.
         */
        val snapshotSystemVolume: Boolean,
    )

    fun planMute(nativeRestore: NativeVolumeRestore): MutePlan = MutePlan(
        nativeVolume = resolveNativeVolume(nativeRestore, rememberedLevel = 0f),
        systemStream = 0f,
        snapshotSystemVolume = true,
    )

    /** Decision for unmuting. */
    data class UnmutePlan(
        val nativeVolume: Float?,
        /** Remembered level coerced into the audible-floor..1 window. */
        val systemStream: Float,
    )

    fun planUnmute(
        rememberedUnmuteVolume: Float,
        nativeRestore: NativeVolumeRestore,
    ): UnmutePlan {
        val target = rememberedUnmuteVolume.coerceIn(UNMUTE_FLOOR, 1f)
        return UnmutePlan(
            nativeVolume = resolveNativeVolume(nativeRestore, rememberedLevel = target),
            systemStream = target,
        )
    }

    private fun resolveNativeVolume(restore: NativeVolumeRestore, rememberedLevel: Float): Float? =
        when (restore) {
            NativeVolumeRestore.ZERO -> 0f
            NativeVolumeRestore.FULL -> 1f
            NativeVolumeRestore.REMEMBERED_LEVEL -> rememberedLevel
            NativeVolumeRestore.LEAVE_UNCHANGED -> null
        }

    private const val UNMUTE_FLOOR = 0.05f
}
