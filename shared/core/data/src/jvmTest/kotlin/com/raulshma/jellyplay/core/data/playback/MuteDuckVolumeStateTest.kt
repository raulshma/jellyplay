package com.raulshma.jellyplay.core.data.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure decision tests for [MuteDuckVolumeState] — the explicit mute/duck
 * machine extracted from [AudioPlaybackManager] (the recorded Tier-1 finding:
 * muted-ness used to be INFERRED from `volume > 0f` at the toggle/duck
 * decision sites). What is pinned is exactly each transition's byte formula,
 * so the manager's executor cannot drift from the behavior the Duck/Crossfade/
 * QueueSemantics suites pin end-to-end:
 *
 *  - the mute capture: an audible level is captured verbatim, a silent player
 *    captures `1f` (the legacy re-mute quirk — preserved, not fixed);
 *  - the duck guard's two arms: the explicit mute refuses, and so does an
 *    unmuted-but-silent player (the byte behavior the volume inference gave);
 *  - the duck capture: only a level strictly louder than the target, so a
 *    re-duck at the target never stores the duck level itself;
 *  - the engine-volume reconcile: an audible write ends a mute (the former
 *    inference verdict — the manager's write edges and the crossfade restore
 *    ports apply it), a silent write mints nothing, duck state is untouched.
 */
class MuteDuckVolumeStateTest {

    // ── the mute capture ─────────────────────────────────────────────────

    @Test
    fun `mute captures an audible level verbatim`() {
        val state = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0.6f)

        assertTrue(state.muted)
        assertEquals(0.6f, state.preMuteVolume)
        assertEquals(0.6f, state.unmuteVolume)
    }

    @Test
    fun `muting a silent player captures 1f (the legacy formula)`() {
        val state = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0f)

        assertTrue(state.muted)
        assertEquals(1f, state.preMuteVolume)
        assertEquals(1f, state.unmuteVolume)
    }

    @Test
    fun `re-muting while muted re-captures the silent 1f quirk`() {
        val once = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0.6f)
        val twice = once.mute(currentVolume = 0f) // the player is silent now

        assertTrue(twice.muted)
        assertEquals(1f, twice.preMuteVolume, "the second mute overwrote the capture — legacy, preserved")
    }

    @Test
    fun `unmute keeps the capture slot and clears only the flag`() {
        val state = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0.6f).unmute()

        assertFalse(state.muted)
        assertEquals(0.6f, state.preMuteVolume, "the slot is kept (the legacy behavior)")
        assertEquals(0.6f, state.unmuteVolume)
    }

    @Test
    fun `the restore levels coerce into the unit interval`() {
        val state = MuteDuckVolumeState(
            preMuteVolume = 1.7f,
            preDuckVolume = -0.2f,
            ducked = true,
        )

        assertEquals(1f, state.unmuteVolume)
        assertEquals(0f, state.duckRestoreVolume)
    }

    // ── the duck guard's two arms ────────────────────────────────────────

    @Test
    fun `the duck refuses the explicit mute`() {
        val state = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0.8f)

        assertTrue(state.duckRefuses(currentVolume = 0f), "the muted arm — the Duck suite's pinned row")
    }

    @Test
    fun `the duck refuses an unmuted silent player (the volume arm preserved)`() {
        val state = MuteDuckVolumeState.INITIAL

        assertFalse(state.muted)
        assertTrue(state.duckRefuses(currentVolume = 0f), "the volume arm keeps the unmuted-at-zero byte behavior")
        assertTrue(state.duckRefuses(currentVolume = -0.3f), "a coerced-negative level is still silent")
    }

    @Test
    fun `the duck admits an audible unmuted player`() {
        assertFalse(MuteDuckVolumeState.INITIAL.duckRefuses(currentVolume = 0.2f))
    }

    // ── the duck capture rule ────────────────────────────────────────────

    @Test
    fun `only a level louder than the target is captured`() {
        val captured = MuteDuckVolumeState.INITIAL.captureDuckIfLouder(currentVolume = 0.9f, target = 0.2f)
        assertTrue(captured.ducked)
        assertEquals(0.9f, captured.preDuckVolume)

        val atTarget = MuteDuckVolumeState.INITIAL.captureDuckIfLouder(currentVolume = 0.2f, target = 0.2f)
        assertFalse(atTarget.ducked, "a re-duck at the target never captures the duck level itself")

        val quieter = MuteDuckVolumeState.INITIAL.captureDuckIfLouder(currentVolume = 0.1f, target = 0.2f)
        assertFalse(quieter.ducked, "a quieter-than-target duck captures nothing — the regain stays a no-op")
    }

    @Test
    fun `a re-duck keeps the ORIGINAL capture`() {
        val first = MuteDuckVolumeState.INITIAL.captureDuckIfLouder(currentVolume = 0.9f, target = 0.2f)
        val second = first.captureDuckIfLouder(currentVolume = 0.2f, target = 0.2f)

        assertTrue(second.ducked)
        assertEquals(0.9f, second.preDuckVolume, "the marker still holds the original louder level")
    }

    @Test
    fun `releaseDuck clears the marker and resets the slot`() {
        val state = MuteDuckVolumeState.INITIAL
            .captureDuckIfLouder(currentVolume = 0.9f, target = 0.2f)
            .releaseDuck()

        assertFalse(state.ducked)
        assertEquals(1f, state.preDuckVolume, "the slot resets (only ever read while ducked)")
    }

    // ── the engine-volume reconcile ──────────────────────────────────────

    @Test
    fun `an audible write ends a mute (the former inference verdict)`() {
        val state = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0.6f)

        val reconciled = state.onEngineVolume(effectiveVolume = 0.5f)
        assertFalse(reconciled.muted, "the write left the engine audible — the inference judged unmuted")
        assertEquals(0.6f, reconciled.preMuteVolume, "the capture slot is kept untouched")
    }

    @Test
    fun `a silent write does not end a mute and mints nothing`() {
        val muted = MuteDuckVolumeState.INITIAL.mute(currentVolume = 0.6f)
        assertTrue(muted.onEngineVolume(effectiveVolume = 0f).muted)

        val unmuted = MuteDuckVolumeState.INITIAL
        assertFalse(unmuted.onEngineVolume(effectiveVolume = 0f).muted, "silence never mints a mute")
    }

    @Test
    fun `the reconcile never touches the duck state`() {
        val ducked = MuteDuckVolumeState.INITIAL.captureDuckIfLouder(currentVolume = 0.9f, target = 0.2f)

        val reconciled = ducked.onEngineVolume(effectiveVolume = 0.4f)
        assertTrue(reconciled.ducked)
        assertEquals(0.9f, reconciled.preDuckVolume, "only restoreFromDuck releases a duck")
    }

    @Test
    fun `the reconcile is a no-op on an already-unmuted state`() {
        val state = MuteDuckVolumeState.INITIAL
        assertEquals(state, state.onEngineVolume(effectiveVolume = 0.5f))
    }
}
