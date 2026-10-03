package com.raulshma.jellyplay.feature.player.video.engine.mpv

import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The ONE fold-application choreography both mpv hosts run after
 * [MpvEventFold.fold] — the write half of the `MpvEventFold` precedent (pure
 * commonMain decision, thin engine adapters): latch store-back, the four
 * chassis-flow writes ([isPlaying]/[playbackState]/cues/live subtitle), the
 * track-refresh and live-line side effects, in exactly this order.
 *
 * Both engines ride it: Android `MpvPlayerEngine` and desktop
 * `MpvDesktopEngine` each construct one applier over their own chassis flows
 * plus two genuinely engine-specific sinks:
 *
 *  - [refreshTracks] — the host's re-enumeration seam. Android passes its
 *    coalescer/delayed-slot split and consumes [apply]'s `refreshReason`
 *    label in its publish log; desktop routes into its (coalesced) read and
 *    ignores the label (`{ _ -> ... }` hook). The reason is a PARAMETER here,
 *    not a per-engine copy — that is the one declared divergence (Android's
 *    extra `refreshReason` passthrough) kept declared.
 *  - [onLiveSubtitleLine] — the cue-history accumulator. Android folds the
 *    line in from its event-cached `sub-start`; desktop reads `sub-start`
 *    live at line time (its declared, observed-staleness divergence —
 *    see the desktop engine's `accumulateCue` KDoc). The applier fixes the
 *    ORDER: accumulate first, then mirror into the live flow (Android's
 *    order; the desktop's former reverse order had no dependency on it).
 *
 * Threading: NOT internally synchronized, by the same contract the engines
 * already run — every fold application happens on the host's serialized event
 * surface (Android: mpv's single event queue; desktop: the
 * `mpv-desktop-event-loop` thread). [latches] is `@Volatile` so a host reset
 * ([resetLatches] from `load`/`stop`/`release` on another thread) is visible
 * to the event surface, exactly as the engines' former `mpvLatches` fields.
 *
 * The END_FILE error emission keeps its pre-application ordering through
 * [fold]: fold + latch store happen FIRST so the host can inspect
 * [MpvEventFoldResult.emitEndFileError], emit through its own taxonomy edge
 * (desktop int codes / Android error strings), and only then
 * [applyResult] the state — the choreography both engines have always run.
 *
 * Deliberately NOT here: the property-CHANGE intake decisions now ride the
 * shared [MpvPropertyIntake] table (its per-host value readers land results
 * through the same applier); what stays per-platform is the non-property mpv
 * EVENT decode (START_FILE/FILE_LOADED/END_FILE/IDLE — engine choreography)
 * and each reader's native extraction quirks (pointer shapes, released
 * guards).
 */
public class MpvFoldApplier(
    private val isPlaying: MutableStateFlow<Boolean>,
    private val playbackState: MutableStateFlow<EnginePlaybackState>,
    private val currentCues: MutableStateFlow<List<TimedCue>>,
    private val liveSubtitleCue: MutableStateFlow<CharSequence?>,
    private val refreshTracks: (reason: String) -> Unit,
    private val onLiveSubtitleLine: (text: String) -> Unit,
) {

    /**
     * The latched playback state the next [MpvEventFold.fold] folds over.
     * Stored back from every [apply]/[fold]/[applyResult]; reset per item via
     * [resetLatches]. Read by hosts that need a latch outside the fold
     * (the desktop's `fileLoaded` gate in `addExternalSubtitle`).
     */
    @Volatile
    public var latches: MpvPlaybackLatches = MpvPlaybackLatches()
        private set

    /** Per-item reset (`load`/`stop`/`release`) — the fresh-fold-state step. */
    public fun resetLatches() {
        latches = MpvPlaybackLatches()
    }

    /**
     * Folds one event and applies the result in one step — the common
     * observer entry. [refreshReason] labels the track refresh when the fold
     * declares one (Android logs it; desktop ignores it).
     */
    public fun apply(event: MpvPlaybackEvent, refreshReason: String = DEFAULT_REFRESH_REASON) {
        applyResult(MpvEventFold.fold(latches, event), refreshReason)
    }

    /**
     * The fold half alone (latch store included) — for hosts that must
     * inspect the result BEFORE applying it (the END_FILE error emission).
     */
    public fun fold(event: MpvPlaybackEvent): MpvEventFoldResult {
        val result = MpvEventFold.fold(latches, event)
        latches = result.latches
        return result
    }

    /**
     * Applies a (usually [fold]-produced) result: latch store-back, then the
     * declared writes in fold order — isPlaying, playbackState, cue-history
     * clear, live-cue clear, track refresh (with the caller's reason), and
     * the live-subtitle line (accumulate, then mirror).
     */
    public fun applyResult(result: MpvEventFoldResult, refreshReason: String = DEFAULT_REFRESH_REASON) {
        latches = result.latches
        result.isPlaying?.let { isPlaying.value = it }
        result.playbackState?.let { playbackState.value = it }
        if (result.clearCueHistory) currentCues.value = emptyList()
        if (result.clearLiveCue) liveSubtitleCue.value = null
        if (result.refreshTracks) refreshTracks(refreshReason)
        result.liveSubtitleText?.let { text ->
            onLiveSubtitleLine(text)
            liveSubtitleCue.value = text
        }
    }

    public companion object {
        /** The reason label when a caller has no more specific one. */
        public const val DEFAULT_REFRESH_REASON: String = "event"
    }
}
