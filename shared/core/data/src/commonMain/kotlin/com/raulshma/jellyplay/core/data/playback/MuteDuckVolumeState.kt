package com.raulshma.jellyplay.core.data.playback

/**
 * The audio mute/duck machine's state — the explicit truth the former
 * volume-inference encoded (muted-ness read as `volume > 0f` at the
 * [AudioPlaybackManager.toggleMute]/[AudioPlaybackManager.duck] decision
 * sites, ducked-ness as `preDuckVolume != null`). One value owns both
 * orthogonal concerns (mute and duck genuinely compose — a duck can be
 * interrupted by a mute and the mute outlives the duck's restore), so this is
 * one data class, not a sealed ladder: the pair is not a sequential
 * lifecycle.
 *
 * PURE (the StateManagementConventions §3 decision object): every transition
 * is a total function from (state, inputs) to the next state, byte-equal to
 * the former inline formulas, so the capture/restore/reconcile rules are
 * unit-testable without an engine, a scope, or Robolectric
 * (`MuteDuckVolumeStateTest`, jvmTest). The manager ([AudioPlaybackManager])
 * is the executor: it reads the guards, applies the transitions, and performs
 * the engine volume writes the state describes.
 *
 * The one NON-obvious rule is [onEngineVolume]: the explicit mute is never
 * READ out of the player volume any more, but the manager's volume write
 * edges still RECONCILE the state with the engine's effective level — an
 * external write that leaves the engine audible ends a mute exactly as the
 * former inference verdict did (byte-preserving: today's toggle-after such a
 * write re-captures the new level, and so does the migrated one). The crossfade
 * restore edges ([AudioCrossfader.cancel]'s `1f` unwind, the swap's
 * full-volume secondary) run the same reconcile, and so do the effects
 * processor's night-mode volume writes (the enable arm's night level and the
 * disable arm's `1f` unwind — REVIEW FIX: the edge the first cut of the
 * migration missed). Between the enumerated edges
 * the state — not the volume — is the truth.
 */
internal data class MuteDuckVolumeState(
    /** The explicit mute — owned by [AudioPlaybackManager.setMuted], never inferred from volume. */
    val muted: Boolean = false,
    /** The level captured at mute time for the unmute arm's restore. */
    val preMuteVolume: Float = 1f,
    /** The explicit duck-active marker — set only when a pre-duck level was captured. */
    val ducked: Boolean = false,
    /** The level captured at duck time for the regain's restore (valid while [ducked]). */
    val preDuckVolume: Float = 1f,
) {

    /** The level the unmute arm writes (the former `preMuteVolume.coerceIn(0f, 1f)`). */
    val unmuteVolume: Float get() = preMuteVolume.coerceIn(0f, 1f)

    /** The level the duck regain writes (the former `preDuckVolume.coerceIn(0f, 1f)`). */
    val duckRestoreVolume: Float get() = preDuckVolume.coerceIn(0f, 1f)

    /**
     * The mute arm. Byte-formula preserved verbatim: re-muting while muted
     * re-captures the CURRENT level (a silent player captures `1f`, so a
     * mute-mute-unmute lands at full volume — the legacy capture quirk,
     * preserved and pinned, not fixed).
     */
    fun mute(currentVolume: Float): MuteDuckVolumeState =
        copy(muted = true, preMuteVolume = if (currentVolume > 0f) currentVolume else 1f)

    /** The unmute arm — the restore level rides [unmuteVolume]; the capture slot is kept (legacy). */
    fun unmute(): MuteDuckVolumeState = copy(muted = false)

    /**
     * The duck guard: a muted player is never spoken for by a duck (the
     * explicit rule — the Duck suite's pinned row), and neither is an
     * UN-muted-but-silent player (reachable through external writes — the
     * user slider at zero; the volume arm keeps that byte behavior).
     */
    fun duckRefuses(currentVolume: Float): Boolean = muted || currentVolume <= 0f

    /**
     * The duck capture rule: only a level LOUDER than the duck target is
     * captured, so a re-duck at the target never stores the duck level itself
     * (the Duck suite's pinned row). A quieter-than-target duck captures
     * nothing and the regain stays a no-op.
     */
    fun captureDuckIfLouder(currentVolume: Float, target: Float): MuteDuckVolumeState =
        if (currentVolume > target) copy(ducked = true, preDuckVolume = currentVolume) else this

    /**
     * The duck regain: the marker comes down and the capture slot resets (it
     * is only ever read while [ducked], so the reset is hygiene, not
     * behavior). The manager reads [duckRestoreVolume] BEFORE releasing.
     */
    fun releaseDuck(): MuteDuckVolumeState = copy(ducked = false, preDuckVolume = 1f)

    /**
     * The engine-volume reconcile an enumerated write edge applies: a write
     * that leaves the engine audible ends a mute (the former inference's
     * verdict — the player became audible, so the next toggle had to
     * re-capture the new level), and a write that leaves it silent does not
     * mint one. Duck state is untouched: only [AudioPlaybackManager.restoreFromDuck]
     * releases a duck.
     */
    fun onEngineVolume(effectiveVolume: Float): MuteDuckVolumeState =
        if (muted && effectiveVolume > 0f) copy(muted = false) else this

    companion object {
        val INITIAL = MuteDuckVolumeState()
    }
}
