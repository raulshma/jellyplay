package com.raulshma.jellyplay.core.data.playback.focus

/**
 * The shared play-edge claim choreography (ADR-0004) — the ONE body every
 * player's is-playing observer folds to (the Android music manager's
 * `onIsPlayingChanged`, its desktop twin's engine observer, the video
 * wiring's `onVideoPlayEdge`, the live TV `PlayingChanged` arm). Focus
 * claims ride this ONE edge per player — every play path (queue tap,
 * resume, notification, tile, widget, cast fling) crosses it, so no
 * per-entry-point claim sites can drift.
 *
 * TRUE edge: [PlaybackFocus.acquire] the seat for [surfaceId] (newest user
 * action wins). On [FocusOutcome.Granted] nothing else happens — the
 * caller produces audio.
 *
 * On [FocusOutcome.Denied] the caller MUST NOT produce audio (the
 * [FocusOutcome.Denied] contract: another holder is Suspended under an OS
 * loss — e.g. read-aloud during a phone call — and the newest user action
 * does not outrank an OS suspension), so [onDenied] runs. Hosts pause,
 * mirroring the user's own pause: playWhenReady drops, so neither the OS
 * focus stack nor a later release can auto-resume the denial, and the
 * resulting isPlaying=false edge releases the claim attempt on the
 * observer's next pass.
 *
 * FALSE edge: [PlaybackFocus.release] the seat (a no-op when this surface
 * does not hold it) — resume stays manual for whatever the claim displaced.
 *
 * The duck path never crosses here: a ducked claim stays Held and its
 * surface keeps playing, so no is-playing edge is produced by ducking.
 */
fun PlaybackFocus.claimOnPlayEdge(
    surfaceId: PlaybackSurfaceId,
    isPlaying: Boolean,
    onDenied: () -> Unit,
) {
    if (isPlaying) {
        if (acquire(surfaceId) is FocusOutcome.Denied) {
            onDenied()
        }
    } else {
        release(surfaceId)
    }
}
