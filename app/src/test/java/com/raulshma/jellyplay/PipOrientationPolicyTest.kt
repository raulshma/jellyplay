package com.raulshma.jellyplay

import com.raulshma.jellyplay.feature.player.video.PlayerOrientationLock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM test table for [PipOrientationPolicy] — the PiP dock/restore machine
 * that keeps the player's semantic orientation intent across a system-PiP
 * round trip (issue #186). Every row is a callback sequence transcribed from
 * the plan's edge-case table: the idempotent dock (the RC-1 repeat-callback
 * overwrite), the null/UNSPECIFIED intent folds (RC-2 — the window request is
 * never the intent's source), the expand-only restore vs the dismiss clear
 * (RC-5), the pre-onResume OEM ordering, and the save/restore round trip an
 * activity recreation rides (RC-3).
 */
class PipOrientationPolicyTest {

    private val none = PipOrientationPolicy.Decision.None
    private val keep = PipOrientationPolicy.Decision.Keep
    private val clear = PipOrientationPolicy.Decision.Clear

    private val fresh = PipOrientationPolicy.State()
    private val landscape = PlayerOrientationLock.SENSOR_LANDSCAPE
    private val portrait = PlayerOrientationLock.LOCKED_PORTRAIT

    // ── Dock (onPipModeChanged(true)) ───────────────────────────────────────

    @Test
    fun `first entry stashes the desired intent and docks — repeated entry returns None`() {
        val atFirst = PipOrientationPolicy.onEntered(
            state = fresh,
            desiredIntent = landscape,
            currentRequested = PlayerOrientationLock.UNSPECIFIED,
        )
        assertEquals(PipOrientationPolicy.Decision.Dock(landscape), atFirst)
        val docked = PipOrientationPolicy.State(stashedIntent = landscape, docked = true)

        // The OEM repeat callback (re-entry, PiP resize, configuration ripple)
        // reads the already-reset window request — it must not overwrite the
        // first stash.
        val atRepeat = PipOrientationPolicy.onEntered(
            state = docked,
            desiredIntent = landscape,
            currentRequested = PlayerOrientationLock.UNSPECIFIED,
        )
        assertEquals(none, atRepeat)
    }

    @Test
    fun `desired intent null falls back to the current concrete request`() {
        // A PiP entry landing inside the screen's 400 ms settle: the screen
        // already knows it wants landscape even though nothing recorded yet.
        val decision = PipOrientationPolicy.onEntered(
            state = fresh,
            desiredIntent = null,
            currentRequested = landscape,
        )
        assertEquals(PipOrientationPolicy.Decision.Dock(landscape), decision)
    }

    @Test
    fun `desired UNSPECIFIED never becomes a stash`() {
        // UNSPECIFIED means "no concrete intent" — docking with it as the
        // stash would restore UNSPECIFIED on expand and rot the stash check
        // (stashedIntent == null) for the create-in-PiP path.
        val fromDesired = PipOrientationPolicy.onEntered(
            state = fresh,
            desiredIntent = PlayerOrientationLock.UNSPECIFIED,
            currentRequested = landscape,
        )
        assertEquals(PipOrientationPolicy.Decision.Dock(landscape), fromDesired)

        // Both sources unspecified/absent: nothing to keep — the stash stays
        // null (expand degrades to a clear, not a UNSPECIFIED write).
        val nothingConcrete = PipOrientationPolicy.onEntered(
            state = fresh,
            desiredIntent = PlayerOrientationLock.UNSPECIFIED,
            currentRequested = PlayerOrientationLock.UNSPECIFIED,
        )
        assertEquals(PipOrientationPolicy.Decision.Dock(null), nothingConcrete)
        assertEquals(
            PipOrientationPolicy.Decision.Dock(null),
            PipOrientationPolicy.onEntered(
                state = fresh,
                desiredIntent = null,
                currentRequested = null,
            ),
        )
    }

    // ── Expand vs dismiss (onPipModeChanged(false)) ─────────────────────────

    @Test
    fun `RESUMED restores the stash and the caller clears`() {
        val docked = PipOrientationPolicy.State(stashedIntent = landscape, docked = true)
        val decision = PipOrientationPolicy.onExited(docked, PipOrientationPolicy.Phase.RESUMED)
        assertEquals(PipOrientationPolicy.Decision.Restore(landscape), decision)

        // The expand transition ends with a fresh state: a following
        // onResumed/onExited finds nothing.
        val afterExpand = PipOrientationPolicy.State()
        assertEquals(
            clear,
            PipOrientationPolicy.onExited(afterExpand, PipOrientationPolicy.Phase.RESUMED),
        )
    }

    @Test
    fun `RESUMED with no stash clears without restoring`() {
        val decision = PipOrientationPolicy.onExited(fresh, PipOrientationPolicy.Phase.RESUMED)
        assertEquals(clear, decision)
    }

    @Test
    fun `STARTED_NOT_RESUMED keeps — a following onResumed restores`() {
        val docked = PipOrientationPolicy.State(stashedIntent = landscape, docked = true)
        // The OEM ordering where the expand callback fires before the activity
        // reports RESUMED: the stash must survive this callback...
        assertEquals(keep, PipOrientationPolicy.onExited(docked, PipOrientationPolicy.Phase.STARTED_NOT_RESUMED))

        // ...and the resume is the re-assert point.
        val atResume = PipOrientationPolicy.onResumed(docked, inPip = false)
        assertEquals(PipOrientationPolicy.Decision.Restore(landscape), atResume)
    }

    @Test
    fun `BELOW_STARTED clears without restoring — the dismiss path`() {
        val docked = PipOrientationPolicy.State(stashedIntent = landscape, docked = true)
        val decision = PipOrientationPolicy.onExited(docked, PipOrientationPolicy.Phase.BELOW_STARTED)
        assertEquals(clear, decision)
    }

    // ── Resume paths ────────────────────────────────────────────────────────

    @Test
    fun `onResumed with no stash is a no-op`() {
        assertEquals(none, PipOrientationPolicy.onResumed(fresh, inPip = false))
    }

    @Test
    fun `onResumed while still in PiP never restores`() {
        val docked = PipOrientationPolicy.State(stashedIntent = landscape, docked = true)
        assertEquals(none, PipOrientationPolicy.onResumed(docked, inPip = true))
    }

    // ── Recreation round trip (onSaveInstanceState / onCreate) ──────────────

    @Test
    fun `stash survives a simulated save-restore round trip`() {
        // Dock on the original instance...
        val atEntry = PipOrientationPolicy.onEntered(
            state = fresh,
            desiredIntent = landscape,
            currentRequested = null,
        )
        assertEquals(PipOrientationPolicy.Decision.Dock(landscape), atEntry)
        val docked = PipOrientationPolicy.State(stashedIntent = landscape, docked = true)

        // ...the Activity saves stashedIntent/docked and a NEW instance
        // rebuilds its State from those fields (State is immutable data, so
        // the round trip is exactly this reconstruction)...
        val restored = PipOrientationPolicy.State(
            stashedIntent = docked.stashedIntent,
            docked = docked.docked,
        )

        // ...and the expand on the new instance still restores the pre-PiP
        // intent.
        assertEquals(
            PipOrientationPolicy.Decision.Restore(landscape),
            PipOrientationPolicy.onExited(restored, PipOrientationPolicy.Phase.RESUMED),
        )
    }

    // ── Full session sequence ───────────────────────────────────────────────

    @Test
    fun `dock - expand - dock again uses the NEW pre-PiP intent`() {
        var state = fresh

        // First session: landscape pre-PiP.
        state = when (val d = PipOrientationPolicy.onEntered(state, landscape, null)) {
            is PipOrientationPolicy.Decision.Dock ->
                state.copy(stashedIntent = d.stash, docked = true)
            else -> state
        }
        assertEquals(
            PipOrientationPolicy.Decision.Restore(landscape),
            PipOrientationPolicy.onExited(state, PipOrientationPolicy.Phase.RESUMED),
        )
        state = PipOrientationPolicy.State() // the caller clears on Restore

        // Second session: the user rotated to portrait before re-docking —
        // the new intent must win, no stale landscape survives.
        val atSecondEntry = PipOrientationPolicy.onEntered(state, portrait, landscape)
        assertEquals(PipOrientationPolicy.Decision.Dock(portrait), atSecondEntry)
    }
}
