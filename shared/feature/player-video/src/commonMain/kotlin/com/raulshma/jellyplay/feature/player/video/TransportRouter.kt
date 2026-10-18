package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.chrome.stepSeekTargetMs
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * The session-owned transport routing module (constructed in
 * [PlaybackSession] beside [PipTransportController], its first consumer):
 * the SyncPlay → cast → local precedence ladders every absolute seek and
 * play/pause press funnels through — the former [VideoPlayerViewModel]
 * `routedSeek` / `routedPlay` funnels, which lived on the VM only because
 * their first rung read `_uiState`'s SyncPlay mirror. The mirror read arrives
 * as the [isInSyncPlaySession] lambda (the session's uiState handle — the
 * same mirror the VM read), so the ladders are session-owned without the
 * session ever seeing the state bag.
 *
 * The seek routing funnel: SyncPlay group first, cast receiver second, local
 * engine last — every user-initiated absolute seek lands in [seek] (the
 * `SeekTo` event arm and [step]'s step target alike), so the transports can
 * never diverge. Routing reads are LIVE (`isInSyncPlaySession`'s mirror +
 * [isCastConnected]'s connection flow value) — the same seam [play] reads —
 * so a cast connect/disconnect racing a recomposition can no longer route a
 * press to a stale target. The SyncPlay/cast arms deliberately bypass the
 * local seek: the group owns the position (the engine seek + group broadcast
 * happen inside SyncPlayBridge.seekTo) and the receiver owns the cast
 * timeline, so the `skipSegmentsOnSeek` clamp and the local position-flow
 * write stay local-arm only.
 *
 * The play routing funnel: the same order [seek] routes seeks — the PiP
 * window's PLAY/PAUSE remote actions and the screen's play/pause controls
 * both land in [play], so they can never diverge again (PiP previously called
 * `engine.play()`/`engine.pause()` raw, which no-oped a SyncPlay group's or
 * cast receiver's pause state). While in a SyncPlay session BOTH arms toggle
 * the group transport (the group, not this device, owns the play state). The
 * local play arm deliberately routes through [resume] rather than a raw
 * `engine.play()`: that imports the configured `videoSkipBackOnResumeMs`
 * resume-skip for PiP too, aligning PiP's play button with the on-screen one
 * (declared divergence from the old raw PiP call, not an accident).
 *
 * [seekLocal] is the local-engine arm (the moved [VideoPlayerViewModel.seekTo]
 * body — its every read is session-reachable, arriving as the ctor lambdas the
 * [transportRouter] wiring supplies). Only the human-driven paths
 * ([userInitiated]: the seek bar / gesture commit, the step buttons /
 * keyboard / D-pad, restart) pay attention to the `skipSegmentsOnSeek` clamp
 * (a target landing strictly inside an AUTO_SKIP segment is pulled to the
 * segment's end, with the "Skipped …" notice) and reset the interaction
 * clock. Internal, app-driven seeks pass `false` and are never touched: the
 * auto-skip execution, the SyncPlay position sync and the resume-skip. The
 * A-B repeat loop seeks the engine directly (AbRepeatController holds the
 * engine getter), so nothing there can be clamped by accident either. The
 * gesture path only commits on release — scrub preview writes the display
 * flows, never this arm — so the clamp sees exactly one final target per
 * gesture (the SegmentSeekClamp risk note).
 *
 * The per-arm effects arrive as constructor lambdas (the
 * [PipTransportController] seam shape) so this class never touches the
 * session's collaborators directly.
 */
internal class TransportRouter(
    /** Routing reads — LIVE at dispatch time, never captured. */
    private val isInSyncPlaySession: () -> Boolean,
    private val isCastConnected: () -> Boolean,

    // ── Seek arms ────────────────────────────────────────────────────────────
    /** The SyncPlay group arm: the group owns the position (SyncPlayBridge.seekTo broadcasts). */
    private val groupSeek: (positionMs: Long) -> Unit,
    /** The cast receiver arm: the receiver owns the cast timeline. */
    private val castSeek: (positionMs: Long) -> Unit,

    // ── Play/pause arms ──────────────────────────────────────────────────────
    /** The group arm toggles the group transport (no play/pause split). */
    private val groupPlayPause: () -> Unit,
    private val castPlay: () -> Unit,
    private val castPause: () -> Unit,
    /** The local pause arm (the local play arm is [resume], below). */
    private val localPause: () -> Unit,

    // ── Shared inputs ────────────────────────────────────────────────────────
    /** Re-read per use, so engine swaps between calls are observed. */
    private val getEngine: () -> MediaEngine?,
    /** The gesture step size (the uiState gestures mirror). */
    private val stepMs: () -> Long,
    /** The `videoSkipBackOnResumeMs` pref (ms; <= 0 disables the resume-skip). */
    private val resumeSkipMs: () -> Long,
    /** The pass-out interaction clock reset + still-watching counter feed. */
    private val onUserInteraction: () -> Unit,

    // ── The local seek arm's reads (the moved ViewModel seekTo body) ─────────
    private val currentPositionMs: () -> Long,
    private val durationMs: () -> Long,
    private val segments: () -> List<MediaSegment>,
    private val segmentBehaviors: () -> Map<MediaSegmentType, SegmentBehavior>,
    /** The `skip_segments_on_seek` gate (the cached aggregate). */
    private val seekClampEnabled: () -> Boolean,
    /** The clamp's "Skipped …" notice (VM-owned; see [PlaybackSession]'s ctor). */
    private val onSegmentSkippedNotice: (MediaSegmentType) -> Unit,
    /** The seek latches + process-death position snapshot + coalesced offline-mirror write. */
    private val persistSeek: (positionMs: Long) -> Unit,
    /** The dedicated position display flow write (the seek bar's immediate reflection). */
    private val writeDisplayedPosition: (positionMs: Long) -> Unit,
) {

    /**
     * The seek routing funnel (the [play] companion): SyncPlay group first,
     * cast receiver second, local engine last. [userInitiated] marks the
     * paths a human drove and only reaches the local arm, where it gates the
     * `skipSegmentsOnSeek` clamp and the interaction clock.
     */
    fun seek(positionMs: Long, userInitiated: Boolean) {
        when {
            isInSyncPlaySession() -> groupSeek(positionMs)
            isCastConnected() -> castSeek(positionMs)
            else -> seekLocal(positionMs, userInitiated)
        }
    }

    /**
     * The local-engine arm, direct — no ladder. The callers that must never
     * route to the group/cast transports: the SyncPlay group's position sync
     * (the group already owns the transport), the restart action and the
     * segment-skip dispatch. See the class KDoc for the clamp/interaction
     * gating [userInitiated] controls.
     */
    fun seekLocal(positionMs: Long, userInitiated: Boolean) {
        if (userInitiated) {
            // A user-initiated seek resets the pass-out interaction clock and
            // feeds the still-watching counter through the same signal.
            onUserInteraction()
        }
        val effectiveTargetMs = if (userInitiated) {
            resolveForwardSeekSegmentClamp(
                targetMs = positionMs,
                currentPositionMs = currentPositionMs(),
                segments = segments(),
                segmentBehaviors = segmentBehaviors(),
                durationMs = durationMs(),
                enabled = seekClampEnabled(),
            )?.also { onSegmentSkippedNotice(it.segmentType) }?.adjustedTargetMs ?: positionMs
        } else {
            positionMs
        }
        persistSeek(effectiveTargetMs)
        // Update the dedicated position flow so the seek bar reflects the
        // new position immediately; uiState is no longer the source of truth.
        writeDisplayedPosition(effectiveTargetMs)
        getEngine()?.seekTo(effectiveTargetMs)
    }

    /**
     * The play routing funnel: SyncPlay group first, cast receiver second,
     * local engine last. A user-initiated play/pause: resets the pass-out
     * interaction clock — the episode counter rides the same signal.
     */
    fun play(play: Boolean) {
        onUserInteraction()
        when {
            isInSyncPlaySession() -> groupPlayPause()
            isCastConnected() -> if (play) castPlay() else castPause()
            play -> resume()
            else -> localPause()
        }
    }

    /**
     * The single discrete skip-step owner: the screen's skip buttons /
     * keyboard / D-pad commits and the PiP transport's SKIP actions all
     * reduce to this funnel. The clamp math stays in
     * [stepSeekTargetMs] — floor at 0 on the back path, cap at the engine's
     * duration on the forward path (skipped for live streams with no
     * resolved duration). The seek itself routes through [seek] — the
     * ONE SyncPlay → cast → local ladder — so PiP steps and on-screen steps
     * can never diverge. Gesture / hold-speed paths do NOT
     * go through here. [direction] < 0 steps back, anything else forward.
     */
    fun step(direction: Int) {
        // User-initiated step: resets the interaction clock + the
        // still-watching counter (the SyncPlay/cast arms bypass the local seek).
        onUserInteraction()
        val engine = getEngine()
        seek(
            stepSeekTargetMs(
                direction = direction,
                currentPositionMs = engine?.currentPositionMs ?: 0L,
                stepMs = stepMs(),
                durationMs = engine?.durationMs ?: 0L,
            ),
            userInitiated = true,
        )
    }

    /**
     * Restarts the current item from the beginning. Backs the
     * "Restart" action on the resume-reminder chip shown when playback resumes
     * from a saved position. A user-initiated LOCAL seek — the ladder is not
     * involved, exactly like the former `restartPlayback` body.
     */
    fun restart() {
        seekLocal(0L, true)
    }

    /**
     * The play/pause toggle's play arm (and the local arm of [play]).
     *
     * The is-playing guard here is DELIBERATE and its absence from the
     * audio-focus regain path ([applyResumeSkip]'s focus-restore caller,
     * which applies the same skip unguarded) is equally deliberate: resume is
     * the play/pause toggle's play arm, so it can fire while already
     * playing — re-seeking then would scrub an active stream — whereas a
     * focus regain only follows a transient loss, where the skip is
     * always wanted. The clamp math itself is shared via
     * [applyResumeSkip] / [resumeSkipTargetMs] (PlaybackVolumePolicy
     * style: math shared, per-site divergence declared).
     */
    fun resume() {
        val engine = getEngine() ?: return
        if (!engine.isPlaying.value) {
            applyResumeSkip(engine)
        }
        engine.play()
    }

    /**
     * The `videoSkipBackOnResumeMs` resume-skip funnel: rewinds the engine's
     * current position by the configured skip through
     * [resumeSkipTargetMs] (floor at zero; a non-positive skip disables the
     * feature and seeks nothing). Shared by the audio-focus regain path
     * (the session's focus-restore hook) and [resume] — the two former
     * hand-copied bodies. Each call site keeps its OWN guard; the divergence
     * is declared at both (see [resume]).
     */
    fun applyResumeSkip(engine: MediaEngine) {
        val skipMs = resumeSkipMs()
        if (skipMs <= 0L) return
        // Not user-initiated: an app-driven rewind (focus regain / resume),
        // so the skip-on-seek clamp must not look at it.
        seekLocal(
            resumeSkipTargetMs(currentPositionMs = engine.currentPositionMs, skipMs = skipMs),
            false,
        )
    }
}

/**
 * The composition wiring for [PlaybackSession.transport] — moved out of the
 * session body (the composition root's line ratchet) into the module being
 * wired, the same ownership move the constructors' lambda bundles made. Reads
 * the session's internal collaborators through the receiver (lazily — the
 * lambdas defer every read of the later-declared `syncPlay` / aggregate
 * cache, the usual construction-order rule) and the owner-private
 * handles/stores through the parameters; the uiState never crosses by name
 * (the narrow-lambda discipline — the bag stays inference-only here too).
 */
internal fun PlaybackSession.transportRouter(
    handles: PlayerStateHandles,
    stores: PlayerStores,
    onSegmentSkippedNotice: (MediaSegmentType) -> Unit,
): TransportRouter {
    val uiState = handles.uiState
    return TransportRouter(
        isInSyncPlaySession = { uiState.value.isInSyncPlaySession },
        isCastConnected = { cast.isConnectedFlow.value },
        groupSeek = { positionMs -> syncPlay.seekTo(positionMs) },
        castSeek = { positionMs -> cast.castSeekTo(positionMs) },
        groupPlayPause = { syncPlay.togglePlayPause() },
        castPlay = { cast.castPlay() },
        castPause = { cast.castPause() },
        localPause = { playerSessionManager.engine?.pause() },
        getEngine = { playerSessionManager.engine },
        stepMs = { uiState.value.gestures.seekDurationMs },
        resumeSkipMs = { stores.aggregateStore.aggregate.value.videoPlayer.videoSkipBackOnResumeMs },
        onUserInteraction = { engineEventCoordinator.onUserInteraction() },
        currentPositionMs = { handles.positionMs.value },
        durationMs = { handles.durationMs.value },
        segments = { uiState.value.segmentState.segments },
        segmentBehaviors = { uiState.value.segmentState.segmentBehaviors },
        seekClampEnabled = { cachedAggregate.videoPlayer.skipSegmentsOnSeek },
        onSegmentSkippedNotice = onSegmentSkippedNotice,
        persistSeek = { positionMs -> seekPersisted(positionMs) },
        writeDisplayedPosition = { positionMs -> handles.positionMs.value = positionMs },
    )
}
