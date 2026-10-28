package com.raulshma.jellyplay.feature.player.video

/**
 * The end-of-media dispatch fold — the ORDERED composition of the six
 * individually-tested end-of-playback verdicts ([SleepTimerBoundaryPolicy],
 * [AutoPlayController], [StillWatchingGate], [UpNextController]'s advance
 * cap + #17 keep gate, [PostPlaybackActionsController]'s #14 offer, the
 * session's cinema-intro chain) that the ViewModel's `handlePlaybackEnded` /
 * `onEndedWithNoNext` / the #14 release's `closeAfterKeep` used to
 * hand-sequence as if-ladders. Pure — one facts value in, the ladder's
 * decided effect list out; NO Compose types, NO ViewModel types: every
 * verdict arrives ALREADY COMPUTED as data (the VM consults the six modules
 * while building [Facts]; the fold decides ORDER + composition only), and
 * the ViewModel executes the returned effects in order, one arm per effect.
 * Pinned by [EndOfMediaDecisionTest].
 *
 * The two ladders (the exact precedence the former if-ordering applied):
 *  - the advance rungs — consulted only when the dispatch carries an
 *    [AdvanceFacts] and it is armed AND the Up Next cap allows: the
 *    end-of-episode sleep timer intercepts (the user asked to STOP at this
 *    episode's end — a timer armed in timed mode does not intercept, and
 *    neither rung is consulted when the advance routes away) > the
 *    "Still watching?" prompt (feature 1.3 — the confirm overlay replaces
 *    the advance) > the unattended advance (the still-watching streak
 *    records BEFORE it, one dispatch);
 *  - the close rungs — a cinema chain continuing is a CONTINUATION, not an
 *    end (it routes away before the offer's ONE entry point) > the #14
 *    delete-offer dispatch (its verdict resolves IN the executor arm: an
 *    armed offer holds the close, a suppressed precheck releases the tail —
 *    the original ladder's fall-through) > the #17 after-finish keep card >
 *    the close.
 *
 * The #17 keep gate is SINGLE-SITED here: both close paths — the natural end
 * and the offer's own release ([CloseFacts.offerResolved]) — resolve
 * HoldForKeepCard-vs-Close through the same rung, so the gate cannot drift
 * between them.
 *
 * Recorded constraint: autoplay/cinema/close policy stays VM-side — the
 * module is VM-constructed, nothing moved behind a new public seam.
 * [Effect.OfferDelete] is the one effect whose arm may end the dispatch
 * early (the controller owns that close); every other rung is
 * terminal-LAST — a resolve list never carries effects past its terminal
 * rung, so the executor is a plain in-order walk.
 */
internal object EndOfMediaDecision {

    /** One end-of-media dispatch's effects, in execution order. */
    sealed interface Effect {
        /** The end-of-episode sleep timer wins — trigger its pause. */
        data object SleepTimerIntercept : Effect

        /** Raise the "Still watching?" confirm overlay (the episode arm). */
        data object PromptStillWatching : Effect

        /** Grow [AutoPlayController]'s unattended still-watching streak — rides AHEAD of the advance. */
        data object RecordStreak : Effect

        /** The unattended advance: the binge-cap streak grows, then the queue-first play-next. */
        data object AutoAdvance : Effect

        /** The cinema-intro chain advances to its next intro (or resumes the feature). */
        data object ChainCinema : Effect

        /**
         * Consult the #14 delete offer (the controller's ONE entry point):
         * an armed offer holds the close; a suppressed precheck releases the
         * ladder's tail.
         */
        data object OfferDelete : Effect

        /** The #17 after-finish keep card holds — the player stays open, unclosed. */
        data object HoldForKeepCard : Effect

        /** Close the player. */
        data object Close : Effect
    }

    /**
     * The advance rungs' verdicts, computed by the VM from
     * [AutoPlayController] / [SleepTimerBoundaryPolicy] / [UpNextController]
     * before the fold. Every field is computed eagerly on each dispatch —
     * all four are pure synchronous reads, so eager reads are
     * observationally identical to the pre-fold ladder's lazy arms — but the
     * fold consults [sleepTimerIntercepts] and [promptStillWatching] only
     * after [armed] and [upNextAllows] hold.
     */
    data class AdvanceFacts(
        /** [AutoPlayController.shouldAutoPlayNext]'s verdict (a next episode or queued head, enabled, not cancelled). */
        val armed: Boolean,
        /** [UpNextController.canAutoAdvance]'s verdict (the #17 binge cap not reached). */
        val upNextAllows: Boolean,
        /** [SleepTimerBoundaryPolicy]'s verdict — an end-of-episode timer is active. */
        val sleepTimerIntercepts: Boolean,
        /** [StillWatchingGate]'s episode-arm verdict via [AutoPlayController.shouldPromptStillWatching]. */
        val promptStillWatching: Boolean,
    )

    /**
     * The close rungs' verdicts. [offerResolved] names the ENTRY: `false` is
     * the natural-end dispatch (the offer's ONE entry point — the fold emits
     * [Effect.OfferDelete] and the executor's arm consults the controller);
     * `true` is the offer's own release (the deferred close wired behind
     * "Keep" / a failing permission read — the offer is never re-entered,
     * and the cinema arm routed away before the offer ever armed, so
     * [cinemaChainActive] is `false` there and only the keep-gate rung is
     * live).
     */
    data class CloseFacts(
        /** The session's cinema-intro context is live — the chain advances instead of ending. */
        val cinemaChainActive: Boolean,
        /** See the class KDoc: the natural-end dispatch vs the offer's own release. */
        val offerResolved: Boolean,
        /** [UpNextController.shouldKeepUpNextCardAfterFinish]'s verdict — the #17 keep gate. */
        val keepCardHolds: Boolean,
    )

    /**
     * Everything the ladders read, as one value: the advance rungs' verdicts
     * (`null` — the dispatch enters at the close rung: the offer's release,
     * or a no-next dispatch the reporter's safety net already established
     * cannot advance) and the close rungs'.
     */
    data class Facts(
        val advance: AdvanceFacts?,
        val close: CloseFacts,
    )

    /**
     * Folds [facts] into the ordered effects — at most one ladder runs, and
     * the returned list is that ladder's DECIDED prefix (see the class KDoc
     * for the terminal-rung invariant).
     */
    fun resolve(facts: Facts): List<Effect> {
        val advance = facts.advance
        if (advance != null && advance.armed && advance.upNextAllows) {
            return when {
                advance.sleepTimerIntercepts -> listOf(Effect.SleepTimerIntercept)
                advance.promptStillWatching -> listOf(Effect.PromptStillWatching)
                else -> listOf(Effect.RecordStreak, Effect.AutoAdvance)
            }
        }
        val close = facts.close
        if (close.cinemaChainActive) return listOf(Effect.ChainCinema)
        if (!close.offerResolved) {
            // The offer's verdict resolves in the executor arm; the tail
            // runs only if the controller's precheck suppresses it.
            return listOf(Effect.OfferDelete) + closeTail(close)
        }
        return closeTail(close)
    }

    /** The keep-gate rung — the #17 gate's ONE decision site (both close entries end here). */
    private fun closeTail(close: CloseFacts): List<Effect> =
        if (close.keepCardHolds) listOf(Effect.HoldForKeepCard) else listOf(Effect.Close)
}
