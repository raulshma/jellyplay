package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Debounce window for engine config syncs driven by slider drags. Moved
 * verbatim beside the debounce itself (the SUBTITLE_DELAY_APPLY_DEBOUNCE_MS
 * precedent).
 */
internal const val CONFIG_SYNC_DEBOUNCE_MS = 150L

/**
 * The state-bag slice set [EngineConfigBuilder.buildFromSlices] reads, as one
 * snapshot value. Replaces the former nine-mirror constructor lambdas: the
 * wiring supplies ONE `() -> EngineConfigSlices` provider (one of its
 * sanctioned uiState sites) and the mirror cannot drift from the builder's
 * parameter list — a builder slice added without a provider field is a
 * compile error here, not a silent default.
 */
internal data class EngineConfigSlices(
    /** The in-memory subtitle style mirror (style AND the per-item delay). */
    val subtitleStyle: SubtitleStyle,
    /** The video-effects slice mirror (per-item persisted filters included). */
    val videoEffects: VideoEffectsConfig,
    /** The dialogue-boost enabled mirror. */
    val dialogueBoostEnabled: Boolean,
    /** The dialogue-boost strength mirror. */
    val dialogueBoostStrength: EffectStrength,
    /** The current item's media streams (drives the HDR gate). */
    val mediaStreams: List<MediaStream>,
    /** The audio-effects slice owned by [VideoEffectsController]. */
    val effects: AudioEffectsState,
    /** The cached aggregate-preferences snapshot. */
    val agg: VideoPlayerAggregate,
    /** The session's effective per-engine config (global slice + overrides). */
    val engineSpecific: EngineSpecificConfig?,
    /** The session-scoped deinterlace cycle value. */
    val deinterlace: DeinterlaceMode,
)

/**
 * Owns the runtime engine-config sync extracted from [VideoPlayerViewModel]
 * (the [SubtitleStyleController] shape): the [EngineConfigBuilder]
 * invocation over the current state slices and the dispatch onto the live
 * engine, plus the two trigger paths the ViewModel used to own —
 *
 *  - [markDirty]: the immediate rebuild+dispatch (the former
 *    `updateConfigWithUiState`) — user commits and pref-rebuild triggers;
 *  - [markDirtyDebounced]: the drag-settling coalesced path (the former
 *    `updateConfigWithUiStateDebounced`). Backing flow for the debounced
 *    config-sync: slider drags fire 60–120 value-changed callbacks/sec;
 *    previously each launched a new coroutine and cancelled the previous
 *    (allocating a DispatchedContinuation per call and walking the job tree
 *    on each cancel). A SharedFlow + debounce emits one coroutine that only
 *    fires after the drag settles. Moved VERBATIM — window
 *    ([CONFIG_SYNC_DEBOUNCE_MS]), buffer policy and job wiring included.
 *
 * Every fact the builder reads arrives in the [EngineConfigSlices] snapshot
 * the [slices] provider returns — read at BUILD time, so the coalesced
 * debounce lands on the last values (the uiState bag itself never crosses
 * this boundary; the god-count ratchet stays at its baseline; this class
 * references no [VideoPlayerUiState] in code). `getEngine` is read at
 * DISPATCH time, so a debounce that settles after an engine swap (mode/
 * quality reload, engine switch, retry) lands on the NEW engine — the
 * pre-extraction `configSyncJob` collector dispatched through
 * `playerSessionManager.engine` at fire time and was itself never cancelled
 * outside the ViewModel scope; that semantics is preserved unchanged.
 *
 * Load-bearing invariants (pinned by [EngineConfigSyncTest]):
 *  - a burst of rapid [markDirtyDebounced] calls coalesces into exactly ONE
 *    build+dispatch after [CONFIG_SYNC_DEBOUNCE_MS];
 *  - the coalesced build reads the state slices at FIRE time (the last
 *    values win), and dispatches to the engine current at that moment;
 *  - [markDirty] is synchronous — the build+dispatch runs on the caller
 *    without any virtual time.
 */
internal class EngineConfigSync(
    private val scope: CoroutineScope,
    /** The slice snapshot the builder reads — evaluated per build. */
    private val slices: () -> EngineConfigSlices,
    /** The live engine handle — read at dispatch time, never cached. */
    private val getEngine: () -> MediaEngine?,
) {

    private val configChangeIntent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val configSyncJob: Job = scope.launch {
        configChangeIntent.debounce(CONFIG_SYNC_DEBOUNCE_MS).collect {
            markDirty()
        }
    }

    /**
     * Immediate engine-config rebuild + dispatch (the former
     * `updateConfigWithUiState`). `equalizerEnabled` is hard `false`: the
     * former VM field died with the X1a dead cut of
     * toggleEqualizer/setEqualizerSettings (no player-screen callers — the
     * settings screens own the audio-effects store): the flag was only ever
     * false at every build.
     */
    fun markDirty() {
        val s = slices()
        val config = EngineConfigBuilder.buildFromSlices(
            subtitleStyle = s.subtitleStyle,
            videoEffects = s.videoEffects,
            dialogueBoostEnabled = s.dialogueBoostEnabled,
            dialogueBoostStrength = s.dialogueBoostStrength,
            mediaStreams = s.mediaStreams,
            effects = s.effects,
            equalizerEnabled = false,
            agg = s.agg,
            engineSpecific = s.engineSpecific,
            deinterlace = s.deinterlace,
        )
        getEngine()?.updateConfig(config)
    }

    /** The drag-settling trigger (the former `updateConfigWithUiStateDebounced`). */
    fun markDirtyDebounced() {
        configChangeIntent.tryEmit(Unit)
    }
}
