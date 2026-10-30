package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [EndOfMediaDecision] — the end-of-media dispatch fold the ViewModel's
 * `handlePlaybackEnded` / `onEndedWithNoNext` / the #14 release's
 * `closeAfterKeep` run through. Truth-table style: the precedence ladder
 * (each intercept beats the next; each close-arm ordering), the two-entry
 * keep-gate single-siting, and the facts → effect mapping per arm. Pure —
 * no session graph, no ViewModel; the six verdict modules the facts
 * aggregate are pinned by their own suites
 * ([SleepTimerBoundaryPolicyTest] / [StillWatchingGateTest] /
 * [AutoPlayControllerTest] / [UpNextControllerTest] /
 * [PostPlaybackActionsControllerTest]).
 */
class EndOfMediaDecisionTest {

    // ── Fact builders (each test names only the arms it pins) ───────────────

    private fun advance(
        armed: Boolean = true,
        upNextAllows: Boolean = true,
        sleepTimerIntercepts: Boolean = false,
        promptStillWatching: Boolean = false,
    ): EndOfMediaDecision.AdvanceFacts = EndOfMediaDecision.AdvanceFacts(
        armed = armed,
        upNextAllows = upNextAllows,
        sleepTimerIntercepts = sleepTimerIntercepts,
        promptStillWatching = promptStillWatching,
    )

    private fun close(
        cinemaChainActive: Boolean = false,
        offerResolved: Boolean = false,
        keepCardHolds: Boolean = false,
    ): EndOfMediaDecision.CloseFacts = EndOfMediaDecision.CloseFacts(
        cinemaChainActive = cinemaChainActive,
        offerResolved = offerResolved,
        keepCardHolds = keepCardHolds,
    )

    private fun resolve(
        advance: EndOfMediaDecision.AdvanceFacts?,
        close: EndOfMediaDecision.CloseFacts,
    ): List<EndOfMediaDecision.Effect> = EndOfMediaDecision.resolve(
        EndOfMediaDecision.Facts(advance = advance, close = close),
    )

    // ── The advance ladder: each intercept beats the next ───────────────────

    @Test
    fun advanceLadder_sleepTimerInterceptBeatsPromptAndAdvance() {
        // The end-of-episode timer is the ladder's top rung: even with the
        // prompt armed too, only the intercept fires (the user asked to STOP
        // at this episode's end).
        assertEquals(
            listOf(EndOfMediaDecision.Effect.SleepTimerIntercept),
            resolve(
                advance(sleepTimerIntercepts = true, promptStillWatching = true),
                close(cinemaChainActive = true, keepCardHolds = true),
            ),
            "the sleep-timer intercept must outrank the prompt, the advance and the close rungs",
        )
    }

    @Test
    fun advanceLadder_promptBeatsTheAdvance() {
        // The "Still watching?" confirm overlay replaces the advance; the
        // close rungs stay out of it.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.PromptStillWatching),
            resolve(
                advance(promptStillWatching = true),
                close(cinemaChainActive = true, keepCardHolds = true),
            ),
            "the still-watching prompt must outrank the advance and the close rungs",
        )
    }

    @Test
    fun advanceLadder_unattendedAdvanceRecordsTheStreakBeforeAdvancing() {
        // The still-watching streak grows BEFORE the advance fires, in one
        // dispatch — the two-effect list's order is the pin.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.RecordStreak, EndOfMediaDecision.Effect.AutoAdvance),
            resolve(advance(), close(cinemaChainActive = true, keepCardHolds = true)),
            "the advance dispatch must record the streak, then advance",
        )
    }

    @Test
    fun advanceLadder_armedAdvanceBeatsTheCloseRungs() {
        // An eligible advance is a continuation — cinema and the keep card
        // are never consulted on it.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.RecordStreak, EndOfMediaDecision.Effect.AutoAdvance),
            resolve(advance(), close(cinemaChainActive = true, keepCardHolds = true)),
            "an armed advance must route away from the close rungs",
        )
    }

    // ── The advance ladder routes away to the close rungs ───────────────────

    @Test
    fun advanceLadder_disarmedAdvanceFallsToTheCloseRungs() {
        // Autoplay disabled / cancelled / no next: the close ladder runs.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.OfferDelete, EndOfMediaDecision.Effect.Close),
            resolve(advance(armed = false), close()),
            "a disarmed advance must fall through to the close rungs",
        )
    }

    @Test
    fun advanceLadder_capBlockedAdvanceFallsToTheCloseRungs() {
        // The #17 binge cap blocks the advance even with autoplay armed — the
        // card renders manual, and the dispatch falls to the close rungs.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.OfferDelete, EndOfMediaDecision.Effect.Close),
            resolve(advance(upNextAllows = false), close()),
            "a cap-blocked advance must fall through to the close rungs",
        )
    }

    @Test
    fun advanceLadder_sleepTimerDoesNotInterceptTheCloseRungs() {
        // An end-of-episode timer armed while the advance is DISARMED must
        // not intercept the close ladder: the intercept rung lives inside the
        // advance dispatch only (the original if-ordering's recorded shape) —
        // the dispatch lands on the close rungs and consults the offer.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.OfferDelete, EndOfMediaDecision.Effect.Close),
            resolve(
                advance(armed = false, sleepTimerIntercepts = true),
                close(),
            ),
            "the sleep timer must not intercept a dispatch that never reaches the advance rungs",
        )
    }

    // ── The close ladder: each arm beats the next ───────────────────────────

    @Test
    fun closeLadder_cinemaChainBeatsOfferKeepAndClose() {
        // A cinema chain continuing is a continuation, not an end — it routes
        // away before the offer's ONE entry point.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.ChainCinema),
            resolve(null, close(cinemaChainActive = true, keepCardHolds = true)),
            "the cinema chain must outrank the offer, the keep card and the close",
        )
    }

    @Test
    fun closeLadder_offerDispatchCarriesTheKeepTail() {
        // The offer's verdict resolves in the executor arm; the fold already
        // decided the tail it releases to on a suppressed precheck: the #17
        // keep card.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.OfferDelete, EndOfMediaDecision.Effect.HoldForKeepCard),
            resolve(null, close(keepCardHolds = true)),
            "a natural end with the keep card armed must dispatch the offer, then hold",
        )
    }

    @Test
    fun closeLadder_offerDispatchCarriesTheCloseTail() {
        assertEquals(
            listOf(EndOfMediaDecision.Effect.OfferDelete, EndOfMediaDecision.Effect.Close),
            resolve(null, close()),
            "a natural end without the keep card must dispatch the offer, then close",
        )
    }

    // ── The keep-gate rung: two entries, ONE decision site ──────────────────

    @Test
    fun keepGate_naturalEndTail_resolvesHoldVsClose() {
        assertEquals(
            EndOfMediaDecision.Effect.HoldForKeepCard,
            resolve(null, close(keepCardHolds = true)).last(),
            "the keep gate holding must end the dispatch with the player open",
        )
        assertEquals(
            EndOfMediaDecision.Effect.Close,
            resolve(null, close(keepCardHolds = false)).last(),
            "the keep gate clear must end the dispatch with the close",
        )
    }

    @Test
    fun keepGate_offerReleaseEntry_sharesTheSameRung() {
        // The #14 release's deferred close (closeAfterKeep) resolves the SAME
        // hold-vs-close verdict as the natural end's tail rung — the gate is
        // single-sited in the fold, so the two entries cannot drift.
        for (keepCardHolds in listOf(true, false)) {
            val naturalEndTail = resolve(null, close(offerResolved = false, keepCardHolds = keepCardHolds)).last()
            val offerRelease = resolve(null, close(offerResolved = true, keepCardHolds = keepCardHolds))
            assertEquals(
                naturalEndTail,
                offerRelease.single(),
                "both close entries must resolve keepCardHolds=$keepCardHolds through the same rung",
            )
            // …and the release entry never re-enters the offer arm.
            assertEquals(
                false,
                offerRelease.contains(EndOfMediaDecision.Effect.OfferDelete),
                "the offer's own release must not re-enter the offer arm",
            )
        }
    }

    @Test
    fun keepGate_offerReleaseEntry_mapsToTheKeepRungOnly() {
        // The release entry's recorded shape (the VM's closeAfterKeep): the
        // cinema arm routed away before the offer ever armed, so the entry
        // passes no live cinema fact and the fold maps it to the keep-gate
        // rung alone.
        assertEquals(
            listOf(EndOfMediaDecision.Effect.Close),
            resolve(null, close(cinemaChainActive = false, offerResolved = true, keepCardHolds = false)),
            "the release entry must not dispatch cinema or offer effects",
        )
        assertEquals(
            listOf(EndOfMediaDecision.Effect.HoldForKeepCard),
            resolve(null, close(cinemaChainActive = false, offerResolved = true, keepCardHolds = true)),
            "the release entry's hold rides the same rung",
        )
    }
}
