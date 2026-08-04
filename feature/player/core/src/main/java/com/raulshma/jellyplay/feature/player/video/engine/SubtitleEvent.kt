package com.raulshma.jellyplay.feature.player.video.engine

/**
 * One-shot subtitle-related events surfaced by a [MediaEngine] for the UI to
 * react to (e.g. show a toast). Distinct from the data-only [MediaEngine.currentCues]
 * flow, which feeds the subtitle-sync preview. Currently only produced by
 * ExoPlayer, which auto-disables a text track that delivers a pathological
 * number of simultaneous cues (the malformed-"subtitle wall" guard).
 */
sealed interface SubtitleEvent {
    /**
     * Emitted after the engine disabled the active subtitle track because a
     * single `onCues` batch carried an implausibly large number of cues — the
     * signature of a malformed text track that would otherwise freeze the UI.
     * The UI should inform the user and let them pick another subtitle track.
     */
    data object MalformedTrackDisabled : SubtitleEvent
}
