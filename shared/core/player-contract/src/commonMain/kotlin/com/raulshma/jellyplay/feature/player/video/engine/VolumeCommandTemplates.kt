package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The four [MediaEngine] volume/mute commands as FINAL templates over
 * [PlaybackVolumePolicy] — the shared COMMON half of the adapters that
 * run this choreography: Android's `ReloadablePlayerEngine` finals, the
 * desktop `MpvDesktopEngine`, and (volume-policy conveyor) the live TV
 * engine's mute controller — all of which delegate here so the
 * plan → remember → capture → native write ordering cannot drift again (the
 * desktop formerly hand-rolled this ordering in parallel with the Android
 * templates).
 *
 * Home (volume-policy conveyor): moved wholesale from player-video to
 * player-contract — the recorded home for engine-shared machinery — so the
 * live player (player-live, which may not depend on player-video) can host
 * the same mute template. The `feature.player.video.engine` package is kept
 * by recorded decision, so every existing consumer and test compiles with no
 * import changes.
 *
 * The decisions (clamp bound, remember target, snapshot ordering, native
 * restore vocabulary) live in [PlaybackVolumePolicy]; the per-adapter
 * divergences stay seams on [NativeVolumeSurface]: the native write
 * mechanism, the delta-base read, the boost ceiling, the mute flag and
 * mute-gate, the mute-restore vocabulary, the unmute restore floor, the
 * system music-stream sync (Android-only — desktop and live have no system
 * stream) and the user-change capture (desktop per-content-type volume
 * memory; Android video deliberately stays on the system-stream model and
 * never captures).
 */
object VolumeCommandTemplates {

    /**
     * What a host adapter can actually provide under the templates. The
     * defaults cover the mute-flag-less / no-system-stream / no-capture hosts,
     * so each adapter declares only its own divergences.
     */
    interface NativeVolumeSurface {
        /** Normalized boost ceiling for [PlaybackVolumePolicy.planLevel]. */
        val volumeBoostCeiling: Float get() = PlaybackVolumePolicy.MAX_BOOST_NOMINAL

        /** The remembered unmute level — [PlaybackVolumePolicy.planUnmute]'s restore target. */
        val rememberedUnmuteLevel: Float

        /**
         * The unmute restore floor — the host's declared divergence data fed
         * to [PlaybackVolumePolicy.planUnmute]. VOD floors a silenced memory
         * at the policy's audible floor; live's controller declares `0f` so
         * the EXACT pre-mute level is restored.
         */
        val unmuteRestoreFloor: Float get() = PlaybackVolumePolicy.UNMUTE_FLOOR

        /**
         * Whether the mute/unmute template may run at all (libVLC aborts
         * without a handle — no snapshot, no system-stream write). The native
         * mute flag is written BEFORE this gate either way, matching the
         * former bodies' ordering.
         */
        val muteTemplateEnabled: Boolean get() = true

        /**
         * The native handle's current normalized level — the delta base for
         * [increaseVolume] / [decreaseVolume]. Null when the handle is absent:
         * the delta templates abort exactly where the former bodies'
         * `?: return` early-exits did.
         */
        fun readNativeVolume(): Float?

        /** Writes [normalized] (0..[volumeBoostCeiling]) to the native volume handle. */
        fun applyNativeVolume(normalized: Float)

        /**
         * The native mute FLAG (mpv's real silencing mechanism); the default
         * no-op covers engines whose silencing is the volume plan itself.
         */
        fun applyNativeMuteFlag(muted: Boolean) {}

        /** Remembers the unmute restore target (adapters keep the audible-only rule). */
        fun rememberUnmuteVolume(level: Float)

        /**
         * The pre-mute snapshot: Android reads the system music stream, the
         * desktop re-remembers its own native level. Invoked BEFORE any plan
         * write, so the mute itself is never remembered as the restore target.
         */
        fun snapshotVolumeForMute() {}

        /**
         * Post-plan system music-stream sync (Android); no-op where no system
         * stream exists (desktop and live — the native handle is the only
         * volume surface).
         */
        fun syncSystemStream(normalized: Float) {}

        /**
         * User-initiated capture (the desktop per-content-type volume memory);
         * fired only for [PlaybackVolumePolicy.LevelPlan.isUserChange] plans,
         * so a programmatic fade never overwrites what the user chose.
         */
        fun onUserVolumeChanged(level: Float) {}

        /**
         * Which level the native handle should carry across a mute/unmute
         * transition ([muted] = the requested state) — the policy's
         * [PlaybackVolumePolicy.NativeVolumeRestore] vocabulary.
         */
        fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore
    }

    /** The `setVolume` command: plan → remember → capture → write → sync. */
    fun setVolume(surface: NativeVolumeSurface, raw: Float, isUserChange: Boolean) {
        applyLevel(surface, PlaybackVolumePolicy.planLevel(raw, surface.volumeBoostCeiling, isUserChange))
    }

    /** The `increaseVolume` command; a null handle aborts before any state moves. */
    fun increaseVolume(surface: NativeVolumeSurface, delta: Float) {
        val current = surface.readNativeVolume() ?: return
        applyLevel(surface, PlaybackVolumePolicy.planLevel(current + delta, surface.volumeBoostCeiling))
    }

    /** The `decreaseVolume` command; a null handle aborts before any state moves. */
    fun decreaseVolume(surface: NativeVolumeSurface, delta: Float) {
        val current = surface.readNativeVolume() ?: return
        applyLevel(surface, PlaybackVolumePolicy.planLevel(current - delta, surface.volumeBoostCeiling))
    }

    /**
     * The mute command: flag → gate → snapshot → (optional) native restore
     * write → sync. Unmute restores [NativeVolumeSurface.rememberedUnmuteLevel]
     * whenever the adapter's vocabulary says REMEMBERED_LEVEL, floored at the
     * adapter's declared [NativeVolumeSurface.unmuteRestoreFloor].
     */
    fun setMuted(surface: NativeVolumeSurface, muted: Boolean) {
        surface.applyNativeMuteFlag(muted)
        if (!surface.muteTemplateEnabled) return
        if (muted) {
            val plan = PlaybackVolumePolicy.planMute(surface.nativeVolumeRestore(muted = true))
            if (plan.snapshotSystemVolume) surface.snapshotVolumeForMute()
            plan.nativeVolume?.let { surface.applyNativeVolume(it) }
            surface.syncSystemStream(plan.systemStream)
        } else {
            val plan = PlaybackVolumePolicy.planUnmute(
                surface.rememberedUnmuteLevel,
                surface.nativeVolumeRestore(muted = false),
                surface.unmuteRestoreFloor,
            )
            plan.nativeVolume?.let { surface.applyNativeVolume(it) }
            surface.syncSystemStream(plan.systemStream)
        }
    }

    /** Remember-before-write — the unified order (the former Exo/VLC order that fixed mpv's drift). */
    private fun applyLevel(surface: NativeVolumeSurface, plan: PlaybackVolumePolicy.LevelPlan) {
        surface.rememberUnmuteVolume(plan.normalized)
        if (plan.isUserChange) surface.onUserVolumeChanged(plan.normalized)
        surface.applyNativeVolume(plan.normalized)
        surface.syncSystemStream(plan.systemStream)
    }
}
