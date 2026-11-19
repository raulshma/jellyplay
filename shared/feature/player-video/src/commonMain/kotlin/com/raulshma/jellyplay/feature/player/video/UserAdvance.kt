package com.raulshma.jellyplay.feature.player.video

/**
 * One manual advance's variant — the six user-driven "move to another item"
 * arms' deltas as data (the [ReloadVariant] precedent), so
 * [PlaybackSession.userAdvance] carries the shared choreography ONCE and each
 * variant's divergence stays constructor arguments, not a bespoke VM body.
 *
 * The shared invariant every variant carries: a user-driven advance resets the
 * still-watching engagement streak ([AutoPlayController.onUserInteraction] —
 * the user is present, the unattended streak restarts from zero) BEFORE the
 * variant's own dispatch; auto-advance loads bypass this verb and keep it.
 * The binge-streak reset (#17) is deliberately NOT duplicated here — it stays
 * inside [UpNextController], whose manual entry points reset it themselves;
 * only [FreshOpen], the full player-open funnel, resets it explicitly.
 *
 * Variant names are the domain vocabulary:
 *
 *  - [FreshOpen]: the player screen's open action — the
 *    [VideoPlayerUiEvent.Initialize] payload verbatim (the full six-argument
 *    load funnel: media-source pin, resume ticks, the stream-index hints and
 *    the "stream instead" force flag);
 *  - [Episode]: the episode picker's pick, resumed from its saved position;
 *  - [Previous]: the previous-episode skip;
 *  - [Next]: the manual play-next (the Up Next card's "Watch now", the PiP
 *    NEXT action) — the binge-streak reset rides UpNext's manual arm;
 *  - [WatchedAndSkip]: the "mark watched & skip" overflow action;
 *  - [QueueItem]: the queue sheet's "play now" for the row at [index].
 */
internal sealed interface UserAdvance {

    /**
     * The player screen's open action: the [VideoPlayerUiEvent.Initialize]
     * payload verbatim. The one variant that additionally resets the
     * autoplay-limit binge streak — a fresh open starts both engagement
     * streaks from zero (#17).
     */
    data class FreshOpen(
        val itemId: String,
        val mediaSourceId: String?,
        val startPositionTicks: Long,
        val subtitleStreamIndex: Int?,
        val audioStreamIndex: Int?,
        /** "Stream instead": resolve Online even when a completed download exists. */
        val forceStream: Boolean,
    ) : UserAdvance

    /** Starts playback of a picked episode at its saved position. */
    data class Episode(
        val episodeId: String,
        val startPositionTicks: Long,
    ) : UserAdvance

    /** Skips to the previous episode. */
    data object Previous : UserAdvance

    /** Skips to the next episode (the manual arm — the binge-streak reset is UpNext-side). */
    data object Next : UserAdvance

    /** "Mark watched & skip": marks the current item played, then advances (or closes). */
    data object WatchedAndSkip : UserAdvance

    /** Plays the up-next queue row at [index] now (removing it from the queue). */
    data class QueueItem(val index: Int) : UserAdvance
}
