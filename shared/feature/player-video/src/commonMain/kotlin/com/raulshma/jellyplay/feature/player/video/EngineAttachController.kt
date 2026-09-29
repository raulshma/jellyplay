package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The ordered engine-attach choreography (extracted from
 * [VideoPlayerViewModel]'s `init` engineFlow collector): everything the VM
 * ran on EVERY engine emission — fresh bind on load, mode/quality/stream
 * reload, engine switch, error retry, and the null detach. The VM's
 * collector is now the single forwarding point
 * (`engineAttachController.attach(engine)`); the choreography itself lives
 * here so the ORDER is jvmTest-pinned (EngineAttachControllerTest).
 *
 * The order is load-bearing end to end:
 *
 *  1. The previous emission's per-engine collectors are cancelled FIRST, so
 *     a rapid engine swap can never let the outgoing engine's tracks/cues/
 *     playback-state fans feed the new attach.
 *  2. `activePlayerController.bindEngine` registers the engine for the
 *     remote-control paths (the registry is null-cleared on the detach arm).
 *  3. The subtitle style seed
 *     ([SubtitleStyleController.onEngineBound]) + dialogue-boost reset MUST
 *     land BEFORE anything below can rebuild an engine config — the
 *     constraint is preserved because there is no suspension point between
 *     the seed and the rest of the attach (the whole body runs synchronously
 *     on the collector's context).
 *  4. `trackSelectionHelper.onEngineRecreated` drops the engine-positional
 *     selection state (a fresh engine renumbers tracks and re-side-loads
 *     subtitles) and runs BEFORE the tracks collector below so the immediate
 *     initial (empty) availableTracks emission cannot act on a stale held
 *     selection.
 *
 * The ui state bag never crosses this boundary (the controllers' ratchet
 * rule): the ONE state write the choreography performs — the engine
 * capability mirror + the keep-screen-on seed — goes through the narrow
 * [onEngineCapabilities] lambda (the SettingsProjector god-pair shape: named
 * per concern, never a generic state transformer). The VM-owned slices the
 * steps read arrive as constructor lambdas — [getAggregate] (the VM's
 * `@Volatile` prefs cache, snapshotted ONCE per attach into `agg` like the
 * former inline body), [getCurrentItemId] / [getIsHdr] (live session-state
 * reads) and [getSeriesIdForPip] (the media-detail holder, deliberately
 * VM-owned — see the PiP step) — so each is read at attach time, exactly
 * where the inline collector read it.
 *
 * Lifecycle: [collectionJob] is cancelled on the next [attach] (arm 1); on
 * full release nothing cancels it explicitly — it dies with the VM scope,
 * matching the former VM-side field, whose release path never touched it
 * either (PlaybackSession.release tears the engine down through the same
 * scope).
 */
internal class EngineAttachController(
    private val scope: CoroutineScope,
    private val activePlayerController: ActivePlayerController,
    private val getAggregate: () -> VideoPlayerAggregate,
    private val getCurrentItemId: () -> String?,
    private val getSeriesIdForPip: () -> String?,
    private val getIsHdr: () -> Boolean,
    private val subtitleStyleController: SubtitleStyleController,
    private val effects: VideoEffectsController,
    private val cast: PlayerCastController,
    private val userMessageBus: PlayerVideoMessageBus,
    private val pipController: PipController,
    private val trackSelectionHelper: TrackSelectionHelper,
    private val subtitlePreview: SubtitlePreviewController,
    private val syncPlay: SyncPlayBridge,
    private val onEngineCapabilities: (capabilities: EngineCapabilities, keepScreenOnDuringVideo: Boolean) -> Unit,
) {

    /** The three per-engine fan-out collectors; cancelled by the next attach. */
    private var collectionJob: Job? = null

    /**
     * Runs the attach choreography for one engine emission. `null` is the
     * detach arm: only the remote-control registry is cleared (the former
     * inline else-branch) — the collectors were already cancelled above.
     */
    fun attach(engine: MediaEngine?) {
        collectionJob?.cancel()
        if (engine != null) {
            activePlayerController.bindEngine(engine)
            val agg = getAggregate()
            // Subtitle style seed + dialogue-boost reset route through
            // the style controller (A7); the writes land synchronously
            // here, BEFORE anything below can rebuild an engine config —
            // the ordering constraint (seed before first
            // updateConfigWithUiState of this engine) is preserved
            // because there is no suspension point between them.
            subtitleStyleController.onEngineBound(
                slice = agg.subtitle,
                itemId = getCurrentItemId(),
                isHdr = getIsHdr(),
            )
            // The capability mirror + keep-screen-on seed: the choreography's
            // single ui-state write, through the narrow lambda (see class
            // KDoc).
            onEngineCapabilities(engine.capabilities, agg.playback.keepScreenOnDuringVideo)
            // Seed the audio-effects slice from the cached preferences —
            // the same fields this collector used to write into UiState.
            // bass/virtualizer/reverb keep their live values (they were
            // never seeded here) and persist across items by design.
            effects.seedFromPreferences(
                audioDelayMs = agg.audio.audioDelayMs,
                decoderMode = agg.playback.decoderMode,
                audioPassthrough = agg.playback.audioPassthrough,
                nightModeEnabled = agg.audioEffects.nightModeEnabled,
                nightModeStrength = agg.audioEffects.nightModeStrength,
                audioNormalizationMode = agg.audio.audioNormalizationMode,
                audioNormalizationEnabled = agg.audio.audioNormalizationEnabled,
                channelMixMode = agg.audio.channelMixMode,
                channelMixEnabled = agg.audio.channelMixEnabled,
            )
            cast.updateCastStrategyForEngine(engine)
            notifyUnsupportedAudioDelayIfNeeded(engine, agg.audio.audioDelayMs)
            // Expose whether a "next" action is available for the PiP
            // window. Reads the media-detail holder — a VM-owned slice — so
            // it deliberately stays out of the coordinator's policies.
            pipController.pipHasNext = getSeriesIdForPip() != null
            // A fresh engine instance (mode/quality/stream-index
            // reload, engine switch, error retry) renumbers tracks and
            // re-side-loads subtitles: drop the engine-positional
            // selection state so the ladder re-applies the stored
            // per-item selection on the new engine's emissions. Runs
            // before the tracks collector below so the immediate
            // initial (empty) availableTracks emission cannot act on
            // a stale held selection.
            trackSelectionHelper.onEngineRecreated()
            collectionJob = scope.launch {
                // The play/buffering mirrors, buffering watchdog,
                // direct-play fallback latch, error surfacing, subtitle
                // toasts, ENDED and pass-out protection live in
                // [EngineEventCoordinator]; the session executes its
                // decisions (see the VM's startEngineEventCoordinatorOutputs
                // for the mirrors the VM keeps). What remains here
                // are the adapter fan-outs the VM owns: the SyncPlay
                // state forward, the PiP auto-exit, track
                // fan-out and the cue-preview gate.
                launch { engine.availableTracks.collect { trackSelectionHelper.updateTracksFromEngine() } }
                // G10: accumulate embedded-subtitle cues from the engine
                // for the sync preview. Only wins when no external text
                // source is active — external gives the full track in
                // both offset directions; engine accumulation covers the
                // played range only. The external-precedence check and
                // the sheet-visibility gate (no state churn while the
                // sheet is closed) live in the controller.
                launch {
                    engine.currentCues.collect { engineCues ->
                        subtitlePreview.onEngineCues(engineCues)
                    }
                }
                // SyncPlay: typed forward — the engine→core Int
                // encoding lives in the bridge ([SyncPlayBridge
                // .toCoreStateInt]), the only module owning both
                // vocabularies. PiP auto-exit, track fan-out and the
                // cue-preview gate are below/beside.
                launch { engine.playbackState.collect { state ->
                    syncPlay.onPlaybackStateChanged(state)
                    // Auto-exit PiP when playback ends or errors so the
                    // window does not linger on a frozen frame. Pause is
                    // intentionally excluded — users pause to read.
                    if (pipController.isInPipMode.value &&
                        (state == EnginePlaybackState.ENDED || state == EnginePlaybackState.ERROR)
                    ) {
                        pipController.requestAutoExitPip()
                    }
                } }
            }
        } else {
            activePlayerController.clearEngine()
        }
    }

    /**
     * Surfaces a one-time heads-up when the user has a non-zero audio-delay
     * preference (set on mpv/LibVLC) but the active engine can't apply it
     * (e.g. ExoPlayer, see `EngineCapabilities.supportsAudioDelay`). Without
     * this the user gets out-of-sync audio with no explanation after switching
     * engines.
     *
     * Only fires when a delay is actually configured, so the common case
     * (delay == 0) stays silent.
     */
    private fun notifyUnsupportedAudioDelayIfNeeded(
        engine: MediaEngine,
        audioDelayMs: Long,
    ) {
        if (audioDelayMs == 0L) return
        if (engine.capabilities.supportsAudioDelay) return
        val engineName = engine.displayName
        userMessageBus.info(
            "Audio delay (${audioDelayMs}ms) isn't supported by $engineName — switching engines re-enables it",
        )
    }
}
