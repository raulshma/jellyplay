package com.raulshma.jellyplay.core.data.playback

/**
 * One-shot per-invocation Cinema Mode override ("Play (with pre-rolls)").
 *
 * The detail screen's ⋮ action plays the current item with server intros
 * FORCED for that single play — bypassing the global `cinemaModeEnabled`
 * preference — while every other load keeps the pref's verdict. The flag
 * cannot ride the play dispatch (`Route.VideoPlayer`'s arguments are the
 * stable cross-app nav contract), so it crosses the details → player boundary
 * through this process-wide holder instead — the same one-shot arm/consume
 * shape the PiP controller's event flags use:
 *
 *  1. the details side calls [arm] immediately before the play dispatch;
 *  2. `PlaybackSession.initialize`'s user-open funnel [consume]s it into the
 *     session request (`LoadRequest.forceCinemaMode`), which the load
 *     pipeline's cinema gate ORs over the global pref.
 *
 * The armed state lingers if the navigation never happens (the same accepted
 * residue the PiP flags carry) — the worst case is the NEXT player open
 * playing intros once. Registered as a Koin single beside the player graph;
 * test graphs that don't wire it keep compiling (the session's seam is
 * nullable).
 */
class CinemaModeOverride {

    // The VideoQueueController idiom: a plain main-thread state holder — arm
    // runs on the details click, consume on the session's user-open funnel;
    // both on Main, so there is no cross-thread race to guard.
    private val armed = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Forces Cinema Mode for the next user-open session load (one play). */
    fun arm() {
        armed.value = true
    }

    /**
     * Reads and clears the override. True exactly once per [arm]: the load
     * that consumes it forces intros; later loads see the cleared flag and
     * follow the global pref again.
     */
    fun consume(): Boolean {
        val wasArmed = armed.value
        armed.value = false
        return wasArmed
    }
}
