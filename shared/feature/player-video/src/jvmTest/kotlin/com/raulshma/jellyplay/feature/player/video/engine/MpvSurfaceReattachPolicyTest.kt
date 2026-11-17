package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSurfaceReattachDecision
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSurfaceReattachPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pins for the mpv surface-reattach policy ([MpvSurfaceReattachPolicy]) —
 * the issue-#220 guard. The `is.xyz.mpv.BaseMPVView` binding's
 * `surfaceCreated` re-issues `loadfile` for its recorded path whenever the
 * one-shot is still armed (a `playFile` no surface event has consumed — the
 * common engine-reuse path), so a screen lock/unlock RESTARTED the session;
 * and any re-attach that skips the runtime `vo` restore leaves the video
 * output down (`VO: [null]`) — black video with a live playhead
 * (device-proven on the POCO F1).
 *
 * The load-bearing rules this suite pins:
 *
 * 1. The initial-load window ALWAYS delegates to the library — its one-shot
 *    surfaceCreated-loadfile is the sole initial-load issuer (the v0.11.8
 *    attach-race semantics the load-verification bridge repairs ride it).
 * 2. A mid-session re-attach NEVER reloads: the decision type has no reload
 *    arm by construction, and the mid-session rows below assert the
 *    Reattach verdict directly.
 * 3. A paused re-attach carries the re-present kick (a paused session pushes
 *    no frame on its own after the lib's `vo=null` teardown); a playing
 *    re-attach does not (mpv re-presents on its own once the VO is back).
 */
class MpvSurfaceReattachPolicyTest {

    private val policy = MpvSurfaceReattachPolicy()

    // ── the issue-#220 repro: paused → lock → unlock ────────────────────────

    @Test
    fun pausedReAttachAfterStartFile_neverReloads_andCarriesTheRePresentKick() {
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = false,
            hasEverStartedFile = true,
            paused = true,
        )
        val reattach = assertIs<MpvSurfaceReattachDecision.Reattach>(decision)
        assertEquals(
            true,
            reattach.rePresentFrame,
            "a paused re-attach must carry the frame-exact re-present kick — the torn-down vo presents nothing on its own",
        )
    }

    @Test
    fun playingReAttach_neverReloads_andDoesNotKick() {
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = false,
            hasEverStartedFile = true,
            paused = false,
        )
        val reattach = assertIs<MpvSurfaceReattachDecision.Reattach>(decision)
        assertEquals(
            false,
            reattach.rePresentFrame,
            "a playing re-attach must not seek — mpv re-presents on its own once the new surface attaches",
        )
    }

    // ── the initial-load window always delegates to the library ─────────────

    @Test
    fun initialLoadWindow_armedPlayFile_delegatesToTheLibrary() {
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = true,
            hasEverStartedFile = false,
            paused = false,
        )
        assertEquals(
            MpvSurfaceReattachDecision.LibraryInitialLoad,
            decision,
            "a pending playFile's surfaceCreated is the sole initial-load issuer — it must reach the lib untouched",
        )
    }

    @Test
    fun initialLoadWindow_armedPlayFileOnAUsedHandle_delegatesToTheLibrary() {
        // Item 2 on the same handle: the window re-arms per playFile, so a
        // re-attach before the new load's start-file still delegates (the
        // lib loads the CURRENT path — the correct initial load for item 2).
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = true,
            hasEverStartedFile = true,
            paused = false,
        )
        assertEquals(MpvSurfaceReattachDecision.LibraryInitialLoad, decision)
    }

    @Test
    fun surfaceAttachBeforePlayFile_theV0118RaceShape_delegatesToTheLibrary() {
        // The v0.11.8 attach-race shape: the surface attaches before any
        // playFile armed the window. The lib's path is still null, so the
        // delegation lands its attach-only arm and the load-verification
        // bridge repairs the lost one-shot — exactly the shipped semantics.
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = false,
            hasEverStartedFile = false,
            paused = false,
        )
        assertEquals(MpvSurfaceReattachDecision.LibraryInitialLoad, decision)
    }

    @Test
    fun surfaceAttachBeforePlayFile_whilePaused_stillDelegates() {
        // The pre-playFile attach is a virgin handle: "paused" is the default
        // playing-flow seed and must not turn the event into a re-attach.
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = false,
            hasEverStartedFile = false,
            paused = true,
        )
        assertEquals(MpvSurfaceReattachDecision.LibraryInitialLoad, decision)
    }

    @Test
    fun initialLoadWindow_armedPlayFile_whilePaused_stillDelegates() {
        // The 8-combo truth table, (T,F,T): a destroy→create INSIDE the armed
        // window of a READY-paused session still delegates — the lib's
        // pending one-shot must not be bypassed by the paused state. The
        // engine compensates the delegate arm's missing vo restore (its
        // loadfile arm has no `vo = voInUse`) via the voAtSurfaceLoss stash.
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = true,
            hasEverStartedFile = false,
            paused = true,
        )
        assertEquals(MpvSurfaceReattachDecision.LibraryInitialLoad, decision)
    }

    @Test
    fun initialLoadWindow_armedPlayFileOnAUsedHandle_whilePaused_stillDelegates() {
        // The 8-combo truth table, (T,T,T): item 2 on the same handle, armed
        // window, paused. Delegation wins over the paused kick — same
        // reasoning as the unpaused sibling above.
        val decision = policy.onSurfaceCreated(
            awaitingInitialStartFile = true,
            hasEverStartedFile = true,
            paused = true,
        )
        assertEquals(MpvSurfaceReattachDecision.LibraryInitialLoad, decision)
    }
}
