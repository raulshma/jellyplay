package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The volume-command chassis of a VOD [MediaEngine]: the four
 * [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine] volume/mute
 * commands, the dispatch containment around every template run, and the
 * unmute memory — the remembered last-audible level, the audible-only
 * remember rule and the pre-mute snapshot wiring.
 *
 * Lifted from androidMain's `ReloadablePlayerEngine`, whose four
 * containment-wrapped finals the desktop `MpvDesktopEngine` used to hand-copy
 * beside a private twin of the memory glue (the same androidMain/apps-desktop
 * duplication [EngineStateChassis] erased for the published-state surface).
 * Both hosts now compose this chassis over their [NativeVolumeSeam] and keep
 * only their genuinely native residue; the arms become constructible in a
 * plain JVM test over a fake seam.
 *
 * The decisions stay in [PlaybackVolumePolicy] and the choreography stays the
 * [VolumeCommandTemplates] finals — the chassis implements
 * [VolumeCommandTemplates.NativeVolumeSurface] over the seam (memory half
 * owned here, native half forwarded) and runs the templates on itself inside
 * the host's containment. Composition over inheritance, per the
 * `MpvCore.Hosts` voice: each host passes a seam object implementing exactly
 * its own residue.
 *
 * The per-host divergences are declared, not copied:
 *  - `dispatch` — the containment shell every command wraps its run in.
 *    Android wires its `dispatchVolumeCommand` open hook (ExoPlayer's
 *    player-thread routing + no-player abort, mpv/libVLC's swallow-all
 *    try/catch); the desktop wires its alive-guard. The default runs inline.
 *  - [NativeVolumeSeam.preMuteSnapshotLevel] — what a mute snapshots: Android
 *    reads the system music stream, the desktop re-reads its own native
 *    level. The snapshot's REMEMBER step is chassis-owned (one audible-only
 *    rule), so the hosts cannot drift on it the way their former `@Volatile`
 *    memory twins could.
 *  - everything else on the seam — the native write/read mechanism, the mute
 *    flag + gate, the boost ceiling, the restore vocabulary, the
 *    system-stream sync, the user-change capture.
 *
 * Public (not internal) because both engine hosts live in another module —
 * player-video's androidMain (via `ReloadablePlayerEngine`) and jvmMain
 * (the `MpvDesktopEngine` split package), with the chassis test beside them
 * (the `MpvConfigApplier` precedent). Not a stable API surface.
 */
class VolumeCommandChassis(
    /** The host's platform-native volume residue. */
    private val native: NativeVolumeSeam,
    /**
     * Dispatch containment around every template run — the shell each of the
     * four commands wraps its template invocation in (thread routing,
     * try/catch, alive-guard, or plain inline). Receives the WHOLE run: an
     * aborting host skips it entirely, so no seam call, no memory write and
     * no snapshot fires on a dead engine.
     */
    private val dispatch: (() -> Unit) -> Unit = { it() },
) : VolumeCommandTemplates.NativeVolumeSurface {

    /** The remembered unmute level — [PlaybackVolumePolicy.planUnmute]'s restore target. */
    @Volatile
    private var lastUnmuteVolume: Float = 1f

    /**
     * Release-time reset of the memory to nominal — ExoPlayer's former
     * `lastUnmuteVolume = 1f` teardown line, so a re-used engine never
     * restores a level an already-released item chose.
     */
    fun resetUnmuteMemory() {
        lastUnmuteVolume = 1f
    }

    /** The audible-only remember rule — both former host copies, verbatim. */
    private fun rememberUnmuteVolumeIfAudible(level: Float) {
        if (level > 0f) lastUnmuteVolume = level
    }

    // ── The memory half of the surface — chassis-owned. ─────────────────────

    override val rememberedUnmuteLevel: Float get() = lastUnmuteVolume

    override fun rememberUnmuteVolume(level: Float) = rememberUnmuteVolumeIfAudible(level)

    /**
     * The pre-mute snapshot: read the seam's declared source (Android's
     * system stream / the desktop's own native level) and remember it if
     * audible — invoked by the mute template BEFORE any plan write, so the
     * mute itself is never remembered as the restore target.
     */
    override fun snapshotVolumeForMute() {
        native.preMuteSnapshotLevel()?.let(::rememberUnmuteVolumeIfAudible)
    }

    // ── The native half — forwarded to the seam. ────────────────────────────

    override val volumeBoostCeiling: Float get() = native.volumeBoostCeiling

    override val unmuteRestoreFloor: Float get() = native.unmuteRestoreFloor

    override val muteTemplateEnabled: Boolean get() = native.muteTemplateEnabled

    override fun readNativeVolume(): Float? = native.readNativeVolume()

    override fun applyNativeVolume(normalized: Float) = native.applyNativeVolume(normalized)

    override fun applyNativeMuteFlag(muted: Boolean) = native.applyNativeMuteFlag(muted)

    override fun syncSystemStream(normalized: Float) = native.syncSystemStream(normalized)

    override fun onUserVolumeChanged(level: Float) = native.onUserVolumeChanged(level)

    override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
        native.nativeVolumeRestore(muted)

    // ── The four commands, each inside the host's containment. ──────────────

    /** The `setVolume` command: containment → plan → remember → capture → write → sync. */
    fun setVolume(value: Float, isUserChange: Boolean) =
        dispatch { VolumeCommandTemplates.setVolume(this, value, isUserChange) }

    /** The `increaseVolume` command; a null handle aborts before any state moves. */
    fun increaseVolume(delta: Float) =
        dispatch { VolumeCommandTemplates.increaseVolume(this, delta) }

    /** The `decreaseVolume` command; a null handle aborts before any state moves. */
    fun decreaseVolume(delta: Float) =
        dispatch { VolumeCommandTemplates.decreaseVolume(this, delta) }

    /** The mute command: flag → gate → snapshot → (optional) native restore write → sync. */
    fun setMuted(muted: Boolean) =
        dispatch { VolumeCommandTemplates.setMuted(this, muted) }
}

/**
 * The platform-native volume residue a [VolumeCommandChassis] host provides —
 * the members only an engine can answer: its handle's read/write, its mute
 * flag, its restore vocabulary, its snapshot source, its system-stream sync.
 *
 * The memory half of [VolumeCommandTemplates.NativeVolumeSurface] — the
 * remembered level, the remember rule, the snapshot's remember step — is
 * chassis-owned and therefore NOT a seam member: one implementation, so the
 * hosts cannot drift on it.
 *
 * The defaults cover the mute-flag-less / no-system-stream / no-capture
 * hosts, so each adapter declares only its own divergences (the same
 * discipline as [VolumeCommandTemplates.NativeVolumeSurface]).
 */
interface NativeVolumeSeam {
    /** Normalized boost ceiling for [PlaybackVolumePolicy.planLevel]. */
    val volumeBoostCeiling: Float get() = PlaybackVolumePolicy.MAX_BOOST_NOMINAL

    /**
     * The unmute restore floor — the host's declared divergence data fed to
     * [PlaybackVolumePolicy.planUnmute]. VOD floors a silenced memory at the
     * policy's audible floor; a host restoring the EXACT pre-mute level
     * declares `0f`.
     */
    val unmuteRestoreFloor: Float get() = PlaybackVolumePolicy.UNMUTE_FLOOR

    /**
     * Whether the mute/unmute template may run at all (libVLC aborts without
     * a handle — no snapshot, no system-stream write). The native mute flag
     * is written BEFORE this gate either way, matching the former bodies'
     * ordering.
     */
    val muteTemplateEnabled: Boolean get() = true

    /**
     * The native handle's current normalized level — the delta base for
     * `increaseVolume` / `decreaseVolume`. Null when the handle is absent:
     * the delta templates abort exactly where the former bodies' `?: return`
     * early-exits did.
     */
    fun readNativeVolume(): Float?

    /** Writes [normalized] (0..[volumeBoostCeiling]) to the native volume handle. */
    fun applyNativeVolume(normalized: Float)

    /**
     * The native mute FLAG (mpv's real silencing mechanism); the default
     * no-op covers engines whose silencing is the volume plan itself.
     */
    fun applyNativeMuteFlag(muted: Boolean) {}

    /**
     * The pre-mute snapshot SOURCE — what a mute remembers as the unmute
     * target: Android's system music stream, the desktop's own native level.
     * The chassis applies the audible-only rule; the seam only reads.
     */
    fun preMuteSnapshotLevel(): Float?

    /**
     * Post-plan system music-stream sync (Android); no-op where no system
     * stream exists (desktop — the native handle is the only volume surface).
     */
    fun syncSystemStream(normalized: Float) {}

    /**
     * User-initiated capture (the desktop per-content-type volume memory);
     * fired only for [PlaybackVolumePolicy.LevelPlan.isUserChange] plans, so
     * a programmatic fade never overwrites what the user chose.
     */
    fun onUserVolumeChanged(level: Float) {}

    /**
     * Which level the native handle should carry across a mute/unmute
     * transition ([muted] = the requested state) — the policy's
     * [PlaybackVolumePolicy.NativeVolumeRestore] vocabulary.
     */
    fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore
}
