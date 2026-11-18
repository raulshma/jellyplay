package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * ViewModel-bound slices of [PlaybackSession.initialize], in the exact order
 * the session calls them (see [PlaybackSession.initialize] for the numbered
 * sequence). The session owns the sequence; the hooks own the per-item
 * teardown slices and the gates whose knowledge lives outside the session
 * (the remote-play strategy probe, the mini-player reclaim, the SyncPlay
 * re-attach flag). The session provides the implementation ITSELF — its
 * private `sessionHooks` object delegates to session-internal hook bodies
 * (the production `hooks` argument is null); the behavioral suites
 * substitute recording doubles to pin the call ORDER without weakening the
 * pins.
 */
internal interface SessionLifecycleHooks {
    /**
     * Re-arms the PiP transport bridge for a new load: release()
     * nulled it and `init` does not re-run on this Activity-scoped, reused
     * VM. The engine-event coordinator re-arm is session-owned and
     * [PlaybackSession.initialize] performs it directly after this hook.
     */
    fun rearmTransports()

    /**
     * Synchronous-prefix resets for the new item — all direct writes, none
     * pipelined: the autoplay controller reset, the autoplay-cancelled clear,
     * the coordinator's new-item fallback-latch reset, and the pending
     * audio/subtitle stream selection for the track selection helper.
     */
    fun resetForNewItem(selection: MediaStreamSelection)

    /**
     * "Play On" routing early-return: when a Jellyfin remote session is
     * connected, sends the video there instead of playing locally. Returns
     * true when the load was routed away and initialization is complete.
     */
    fun routeToRemotePlaySession(request: LoadRequest): Boolean

    /**
     * Reclaims the mini-player's live engine when it is playing exactly
     * [itemId], so the fullscreen load can bind it instead of reloading.
     * Null when there is nothing to reclaim. The GATE only — the
     * reclaim BODY (detail fetch, engine bind, media session, tracking
     * restart) is session-side ([PlaybackSession.loadReclaimedEngine]); the
     * gate stays a hook because the mini-player state it reads is
     * owner-lifecycle knowledge.
     */
    fun tryReclaimMiniPlayer(itemId: String): MediaEngine?

    /**
     * Lowers the loading veil when the reclaim routing takes over — playback
     * is continuous, no load screen. The uiState write stays in the hook
     * implementation; the session's [PlaybackSession.loadReclaimedEngine]
     * invokes this synchronously BEFORE launching the body (the veil write's
     * original position).
     */
    fun onMiniPlayerReclaimed()

    /**
     * Post-bind hydration for a reclaimed mini-player engine: the segments,
     * adjacent-episodes and series-episodes fetches whose uiState writes and
     * collaborators keep them in the hook implementation. Called by the
     * session's reclaim body (after the engine bind, media session and
     * tracking restart).
     */
    fun hydrateReclaimedItem(itemId: String, detail: MediaDetail)

    /** Releases the mini-player state when its engine was not reclaimed. */
    fun releaseMiniPlayerState()

    /**
     * The controller/ui-state part of the per-item teardown, prefixed by the
     * loading-veil raise. Called back-to-back AFTER the session-owned teardown
     * half — the session cancels the in-flight load, reporter jobs, media
     * session and PSM, and clears the seek latches directly before this hook —
     * from the same synchronous call chain (no dispatch hop). Also invoked by
     * [PlaybackSession.release] on full teardown, where the veil raise is a
     * same-value write (the ui-state rebuild constructs a fresh state whose
     * `isInitializing` default is already true). The play-session id restore
     * is session-side.
     */
    fun releaseInternalsVmPart()

    /** Clears the trickplay cache for the outgoing item. */
    fun clearTrickplay()

    /** Re-attaches the SyncPlay bridge to the (surviving) group session. */
    fun reattachSyncPlay()

    /**
     * Reads whether playback was in a SyncPlay session before this load
     * (feeding the conditional [reattachSyncPlay] call). A pure flag read:
     * the outgoing session's stop-report is session-owned
     * ([PlaybackSession.initialize] fires
     * [PlaybackSession.reportCurrentPlaybackStopped] directly after this
     * read).
     */
    fun wasInSyncPlay(): Boolean
}
