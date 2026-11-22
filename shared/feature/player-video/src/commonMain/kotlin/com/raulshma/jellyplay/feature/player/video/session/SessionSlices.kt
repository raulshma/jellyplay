package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ExoHdrToneMappingMode
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.MpvDebandMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvToneMappingCurve
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.AbRepeatController
import com.raulshma.jellyplay.feature.player.video.AbRepeatEvent
import com.raulshma.jellyplay.feature.player.video.AbRepeatState
import com.raulshma.jellyplay.feature.player.video.LoadPhase
import com.raulshma.jellyplay.feature.player.video.PlaybackSession
import com.raulshma.jellyplay.feature.player.video.PlayerCastController
import com.raulshma.jellyplay.feature.player.video.PlayerSessionManager
import com.raulshma.jellyplay.feature.player.video.PlayerUiCommands
import com.raulshma.jellyplay.feature.player.video.PostPlaybackActionsController
import com.raulshma.jellyplay.feature.player.video.RenderControls
import com.raulshma.jellyplay.feature.player.video.SessionRenderState
import com.raulshma.jellyplay.feature.player.video.StillWatchingController
import com.raulshma.jellyplay.feature.player.video.StillWatchingPromptState
import com.raulshma.jellyplay.feature.player.video.SubtitleManager
import com.raulshma.jellyplay.feature.player.video.SubtitleStyleController
import com.raulshma.jellyplay.feature.player.video.SyncPlayBridge
import com.raulshma.jellyplay.feature.player.video.SleepTimerController
import com.raulshma.jellyplay.feature.player.video.TrackSelectionHelper
import com.raulshma.jellyplay.feature.player.video.UpNextCard
import com.raulshma.jellyplay.feature.player.video.UpNextController
import com.raulshma.jellyplay.feature.player.video.VideoEffectsController
import com.raulshma.jellyplay.feature.player.video.engine.EngineBufferHealth
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.NetworkThroughputSnapshot
import com.raulshma.jellyplay.feature.player.video.engine.StallCause
import com.raulshma.jellyplay.feature.player.video.engine.StallClassifier
import com.raulshma.jellyplay.feature.player.video.state.ActionableRecovery
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import com.raulshma.jellyplay.feature.player.video.state.PlaybackDiagnosticAdvisor
import com.raulshma.jellyplay.feature.player.video.state.RecoveryAction
import com.raulshma.jellyplay.feature.player.video.state.StallTracker
import com.raulshma.jellyplay.feature.player.video.state.SleepTimerState
import com.raulshma.jellyplay.feature.player.video.state.SubtitleState
import com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState
import com.raulshma.jellyplay.feature.player.video.state.TrackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── Session slices (the GLOSSARY "Session slice" seam) ───────────────────
//
//    THE one read seam between the player UI and the playback session:
//    per-region projections whose interfaces the screens program against.
//    Controllers sit behind the slices — a screen reading a session
//    controller directly, or the ViewModel re-exposing one as a
//    pass-through, is a defect. Collection placement follows the slice: hot
//    flows are collected in the leaf that reads them, never in the screen
//    root.
//
//    Every slice is a narrow projection over the collaborators it hides:
//    StateFlows/SharedFlows to collect, derived vals to read, and the
//    command funs its region invokes — never the controllers themselves.
//    The slices are built in ONE place ([createSessionSlices], the
//    PlaybackSession extension) so construction has locality, and the VM
//    exposes the single holder (`sessionSlices`); the screens read
//    `viewModel.sessionSlices.<region>` and nothing else.

/**
 * The whole slice graph, exposed to the screens as ONE VM member
 * (`VideoPlayerViewModel.sessionSlices`). Per-region slices are named
 * after the UI regions that consume them (cast, center overlays, status
 * tier, control chrome, modal sheets).
 */
internal class PlayerSessionSlices(
    /** Cast companion-dashboard region (root branch + window teardown + android cast chrome). */
    val cast: CastSessionSlice,
    /** Center-overlay region (up-next card family + "Still watching?"). */
    val overlay: OverlaySessionSlice,
    /** Status tier (the transient badge family's session feeds). */
    val status: StatusSessionSlice,
    /** Control chrome + input tier (effects/AB/sleep/sync-play wiring, engine handle, session render facts). */
    val controls: ControlsSessionSlice,
    /** Modal-sheet router region (hub, pickers, sync-play/sleep/render sheets, queue). */
    val sheets: SheetsSessionSlice,
    /** Progressive loading and stall diagnostics telemetry region. */
    val diagnostics: DiagnosticsSessionSlice? = null,
)

/**
 * Diagnostic & recovery telemetry for progressive loading and slow-network stalls.
 * All feeds are StateFlows collected exclusively in leaf composables
 * (the Structural/Live state split law).
 */
internal class DiagnosticsSessionSlice(
    val loadPhase: StateFlow<LoadPhase>,
    val bufferHealth: StateFlow<EngineBufferHealth>,
    val throughput: StateFlow<NetworkThroughputSnapshot>,
    val activeRecovery: StateFlow<ActionableRecovery?>,
    val stallCause: StateFlow<StallCause>,
    val rebufferThresholdMs: StateFlow<Long>,
    private val onApplyAction: (RecoveryAction) -> Unit = {},
    private val onDismissAction: () -> Unit = {},
    private val onCancelAction: () -> Unit = {},
) {
    fun applyRecovery(action: RecoveryAction) = onApplyAction(action)
    fun dismissRecovery() = onDismissAction()
    fun cancelLoad() = onCancelAction()
}

/**
 * Cast region: the receiver-connection facts the cast-dashboard branch and
 * the window teardown read, plus the transport commands the cast chrome
 * drives.
 *
 * Cadence contract (the collection-placement law): [isConnectedFlow] /
 * [isConnectingFlow] flip only on cast connect/disconnect — low frequency,
 * safe for the screen root's branch decision. [castIsPlaying] flips only on
 * the receiver's play/pause boundaries — the same cadence class as the
 * local play state. [castDurationMs] (and [castPositionMs]) re-emit at
 * cast-STATUS cadence — they are LEAF-collected only (the companion
 * dashboard collects them branch-locally); a root collection would
 * re-execute the whole screen body every tick.
 */
internal class CastSessionSlice(
    private val cast: PlayerCastController,
    /**
     * The cast route-button projection (availability + click) — supplied by
     * the construction site (the VM's class body, the one place the platform
     * cast surface is reachable). Null on a platform with no cast stack
     * (desktop): the chrome hides the button.
     */
    val routeButton: CastRouteButton? = null,
) {
    /** Whether a cast receiver is connected — root-collected for the dashboard branch. */
    val isConnectedFlow: StateFlow<Boolean> = cast.isConnectedFlow

    /** Connection-in-progress flip (route selected, session establishing). */
    val isConnectingFlow: StateFlow<Boolean> = cast.isConnectingFlow

    /** Receiver play state — flips on the receiver's play/pause boundaries only. */
    val castIsPlaying: StateFlow<Boolean> = cast.castIsPlaying

    /**
     * Receiver item duration — RE-EMITS AT CAST-STATUS CADENCE. Leaf-collect
     * only (see the class KDoc); never collect at a screen root.
     */
    val castDurationMs: StateFlow<Long> = cast.castDurationMs

    /** Receiver playback position — leaf-collected (the dashboard's seek bar). */
    val castPositionMs: StateFlow<Long> = cast.castPositionMs

    /** Receiver volume (0..1) — leaf-collected, or read `.value` from gesture lambdas. */
    val castVolume: StateFlow<Float> = cast.castVolumeFlow

    /** Whether playback is currently handed off to a background cast receiver. */
    val isBackgroundCasting: Boolean get() = cast.isBackgroundCasting

    /** Whether background casting is enabled in preferences (window teardown gate). */
    val backgroundCastingEnabled: Boolean get() = cast.backgroundCastingEnabled

    /** Transport: set the receiver volume (gesture tier + dashboard slider). */
    fun setCastVolume(volume: Float) = cast.setCastVolume(volume)

    /** Hands the current item off to the active receiver (android cast-chrome Connected event). */
    fun castToDevice() = cast.castToDevice()

    /** Resumes local playback after a receiver disconnect (dashboard disconnect + android event). */
    fun onCastDisconnected() = cast.onCastDisconnected()
}

/**
 * The cast route button's projection (availability + click): the ONE typed
 * member the controls' cast entry consumes — the former opaque platform
 * handle threaded through the screen and [PlayerControls][com.raulshma.jellyplay.feature.player.video.components.PlayerControls]
 * is retired. Null (the whole projection) on a platform with no cast stack
 * (desktop) — the chrome hides the button. The platform picker flow
 * (discovery lifecycle, device dialog, connect/disconnect) lives behind the
 * platform implementation; the chrome never sees the legacy `core:data`
 * manager (which stays untouched).
 */
interface CastRouteButton {
    /** Whether the platform offers cast route selection (the button's render gate). */
    val isAvailable: Boolean

    /**
     * The click arm: open the platform route picker. The connected arm (a
     * tap while a receiver is attached) stays the disconnect, which the
     * platform implementation resolves.
     */
    val onClick: () -> Unit
}

/**
 * Center-overlay region: the up-next card family and the "Still watching?"
 * confirm — the projections [PlayerCenterOverlayTier] renders — plus the
 * binge-controls surface (the autoplay-cap verdict and the countdown's expiry
 * arm) and the #14 offer-delete-after-playback dialog state + commands. All
 * low-frequency (card resolves / load settles / prompt raises / cap flips /
 * offer arms).
 */
internal class OverlaySessionSlice(
    private val upNext: UpNextController,
    stillWatching: StillWatchingController,
    /**
     * The #14 post-playback controller — constructed session-side (every
     * fact it reads is session-owned; see [PlaybackSession.postPlaybackActions])
     * and projected here by [createSessionSlices]. Null in graphs without
     * the delete seams: the commands no-op and the offer flow stays
     * permanently null.
     */
    private val postPlayback: PostPlaybackActionsController? = null,
) {
    /**
     * The Up Next card — the controller's projection, already resolved with
     * the ADVANCE's precedence (queue head beats the episode sibling, null
     * under SyncPlay). The overlay never re-decides precedence from raw state.
     */
    val upNextCard: StateFlow<UpNextCard?> = upNext.upNextCard

    /** True while a next-episode advance is in flight and unsettled. */
    val isNextEpisodeLoading: StateFlow<Boolean> = upNext.isNextEpisodeLoading

    /** The "Still watching?" confirm overlay's state surface; null = hidden (feature 1.3). */
    val stillWatchingPrompt: StateFlow<StillWatchingPromptState?> = stillWatching.prompt

    /**
     * The autoplay episode-cap verdict (#17): true once the unattended
     * auto-advance streak reached the configured cap — the Up Next overlay
     * renders MANUAL (no countdown, no autoplay toggle) while it holds.
     */
    val isAutoplayLimitReached: StateFlow<Boolean> = upNext.isAutoplayLimitReached

    /**
     * The Up Next countdown's EXPIRY arm (#17): a countdown that ran out is
     * an AUTO advance — the binge streak grows before the advance fires (the
     * manual "Watch now" taps keep riding the manual event, which resets the
     * streak instead).
     */
    fun onUpNextCountdownFinished() = upNext.advanceUnattended()

    /**
     * The #14 offer-delete-after-playback dialog's state; `null` = hidden.
     * Null controller (no delete seams) → a permanently-null flow.
     */
    val deleteOffer: StateFlow<PostPlaybackActionsController.DeleteOffer?> =
        postPlayback?.deleteOffer ?: kotlinx.coroutines.flow.MutableStateFlow(null)

    /**
     * #14 "Keep" — dismiss the offer for good (the per-item latch holds) and
     * release the end-of-media close hold (the #17 card gate decides whether
     * the player closes).
     */
    fun keepPlayedItem() = postPlayback?.keepItem()

    /** #14 "Delete" — server delete + cache invalidation, then close. */
    fun deletePlayedItem() = postPlayback?.deleteOfferedItem()
}

/**
 * Status tier: the session feeds the transient badge family consumes.
 * The A/B repeat events are one-shot confirmations (point captured, loop
 * active, cleared) — collected inside the badge leaf.
 */
internal class StatusSessionSlice(
    abRepeat: AbRepeatController,
) {
    /** The A/B repeat badge's one-shot event feed. */
    val abRepeatEvents: SharedFlow<AbRepeatEvent> = abRepeat.events
}

/**
 * Control-chrome + input-tier region: the state slices the root screen wires
 * into the control chrome (effects bundle, A/B repeat, sleep-timer control,
 * SyncPlay indicator) and the one-shot engine reads the input executor's
 * lambdas make.
 */
internal class ControlsSessionSlice(
    private val effects: VideoEffectsController,
    private val abRepeat: AbRepeatController,
    sleepTimer: SleepTimerController,
    syncPlay: SyncPlayBridge,
    trackSelectionHelper: TrackSelectionHelper,
    private val playerSessionManager: PlayerSessionManager,
    sessionRender: SessionRenderState,
) {
    /**
     * Reactive engine handle for composition (deterministic `key(engine)`
     * re-keying on a swap) — the root's one engine subscription.
     */
    val engineFlow: StateFlow<MediaEngine?> = playerSessionManager.engineFlow

    /**
     * One-shot engine read for invocation-time lambdas (the input executor's
     * position/duration getters) — no subscription, the live value.
     */
    val engine: MediaEngine? get() = playerSessionManager.engine

    /** The audio-effects slice the effects bundle + PlaybackInfo render from. */
    val effectsState: StateFlow<AudioEffectsState> = effects.state

    /** Uniform engine-effect setters (the effects bundle's command family). */
    fun setAudioDelay(ms: Long) = effects.setAudioDelay(ms)

    fun toggleNightMode() = effects.toggleNightMode()

    fun setNightModeStrength(strength: EffectStrength) = effects.setNightModeStrength(strength)

    fun setAudioPassthrough(enabled: Boolean) = effects.setAudioPassthrough(enabled)

    /** The ADVISORY spatial-audio toggle (Android/ExoPlayer only). */
    fun setSpatialAudioEnabled(enabled: Boolean) = effects.setSpatialAudioEnabled(enabled)

    fun toggleAudioNormalization() = effects.toggleAudioNormalization()

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) = effects.setAudioNormalizationMode(mode)

    fun toggleChannelMix() = effects.toggleChannelMix()

    fun setChannelMixMode(mode: ChannelMixMode) = effects.setChannelMixMode(mode)

    /** The A/B repeat slice (the control-chrome badge + overflow toggle family). */
    val abRepeatState: StateFlow<AbRepeatState> = abRepeat.state

    fun setAbRepeatEnabled(enabled: Boolean) = abRepeat.setEnabled(enabled)

    fun setAbRepeatPointA() = abRepeat.setPointA()

    fun setAbRepeatPointB() = abRepeat.setPointB()

    fun clearAbRepeat() = abRepeat.clear()

    /** The sleep-timer slice (the control-chrome countdown readout). */
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    /** The sleep timer's remaining-time feed (collected leaf-side in the readout). */
    val sleepTimerRemainingMs: StateFlow<Long> = sleepTimer.remainingMs

    /** The SyncPlay group-display slice (the control-chrome indicator + notifications toasts). */
    val syncPlayState: StateFlow<SyncPlayUiState> = syncPlay.state

    /** The sheet's "ignore wait" toggle mirror (read at the root, rendered by the sheet). */
    val syncPlayIgnoreWait: StateFlow<Boolean> = syncPlay.ignoreWait

    /** SyncPlay notices surfaced as snackbars (fire-and-forget feed). */
    val syncPlayNotifications: SharedFlow<String> = syncPlay.notifications

    /** The audio/subtitle track slice (the control chrome's audio-track readout). */
    val trackState: StateFlow<TrackState> = trackSelectionHelper.state

    /**
     * The session-scoped render facts (deinterlace cycle for the gear menu;
     * override + effective-config lens for the sheet — shared with
     * [SheetsSessionSlice.render]). Pure state — the write choreography
     * stays behind the VM's render events.
     */
    val render: SessionRenderState = sessionRender

    /**
     * The bound engine's live subtitle cue — the zoom-safe
     * COMPOSE_CUE overlay's feed. Takes the engine explicitly so the
     * per-engine collection site (inside `key(engine)`) keeps its exact
     * re-keying on an engine swap.
     */
    fun liveSubtitleCue(engine: MediaEngine): StateFlow<CharSequence?> = engine.liveSubtitleCue
}

/**
 * Modal-sheet region: every session read the sheet router's branches make —
 * the subtitle hub (state + remote-subtitle workflow + language-pref filter),
 * the named style presets, the audio/subtitle/version pickers, the queue
 * sheet, the AV-sync and decoder sheets, the SyncPlay and sleep-timer sheets,
 * the Rendering sheet's session + global render facts and its persisted
 * render prefs, and the PlaybackMode sheet's engine-swap + local-control
 * verdicts.
 */
internal class SheetsSessionSlice(
    scope: CoroutineScope,
    private val subtitles: SubtitleManager,
    private val subtitleStyleController: SubtitleStyleController,
    trackSelectionHelper: TrackSelectionHelper,
    videoQueue: VideoQueueController,
    private val effects: VideoEffectsController,
    private val syncPlay: SyncPlayBridge,
    private val sleepTimer: SleepTimerController,
    private val renderControls: RenderControls,
    sessionRender: SessionRenderState,
    private val playerSessionManager: PlayerSessionManager,
    /**
     * The cast controller — the local-control verdict's connectivity input
     * only; the cast region's own reads and commands stay on
     * [CastSessionSlice].
     */
    cast: PlayerCastController,
    /** The playback session's UI-commands module (the render-prefs commands project below). */
    private val uiCommands: PlayerUiCommands,
    /** The subtitle-language preference store slice (the hub's display filter). */
    subtitlePreferences: StateFlow<SubtitleSlice>,
    /** The engines this platform can construct (the engine-swap verdict's universe). */
    private val availablePlayerTypes: List<PlayerType>,
) {
    /** Reactive engine handle — the hub's custom-mpv-config ownership snapshot. */
    val engineFlow: StateFlow<MediaEngine?> = playerSessionManager.engineFlow

    // ── Playback-mode verdicts (engine swap + local control) ─────────────

    /**
     * True while the session is NOT under local control: a cast receiver is
     * connected, or a SyncPlay session is active. The PlaybackMode sheet's
     * quality section disables under it (the delivery-side half of that
     * gate — the VM-state terms, forced direct play and the downloaded
     * badge, stay VM-side, folded at the sheet), and it is one arm of the
     * engine-swap verdict below.
     */
    val localControlBlocked: StateFlow<Boolean> = combine(
        cast.isConnectedFlow,
        syncPlay.state.map { it.isInSyncPlaySession }.distinctUntilChanged(),
    ) { castConnected, syncPlayActive -> castConnected || syncPlayActive }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * The engine-swap verdict — the ONE fold of the PlaybackMode sheet's
     * engine section: the platform's available engine types minus the
     * currently-bound engine, EMPTY while [localControlBlocked] holds or no
     * engine is bound. The sheet renders it and the ViewModel's
     * SwitchPlayerEngine arm consumes it as a one-line membership check — a
     * second site re-folding the policy (cast connectivity, SyncPlay,
     * engine identity, availability) is the defect shape the slice exists
     * to retire. The SyncPlay term reads the manager flag, not the uiState
     * mirror: the Up Next module's display-on-mirror split is per-verdict,
     * and this verdict is deliberately ONE source for both the sheet's
     * display and the VM's advance guard.
     */
    val switchableEngines: StateFlow<List<PlayerType>> = combine(
        playerSessionManager.engineFlow,
        localControlBlocked,
    ) { engine, blocked ->
        when {
            blocked || engine == null -> emptyList()
            else -> availablePlayerTypes.filter { it.displayName != engine.displayName }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ── Subtitle hub ──────────────────────────────────────────────────────

    /** The subtitle workflow's state slice (search/download/upload + hub flags). */
    val subtitleState: StateFlow<SubtitleState> = subtitles.state

    /**
     * The subtitle-language preference slice the hub's track list filters
     * against (picker presentation only — automatic selection never
     * consults it).
     */
    val subtitlePreferences: StateFlow<SubtitleSlice> = subtitlePreferences

    /** The hub's single open-load trigger (reset-first intent rides in from the entry click). */
    fun openSubtitleHub(resetFirst: Boolean) = subtitles.openSubtitleHub(resetFirst)

    /** Side-loads a local subtitle document (the SAF picker's callback). */
    fun addLocalSubtitle(uri: String, fileName: String) = subtitles.addLocalSubtitle(uri, fileName)

    /** Downloads a server-listed remote subtitle (open + search result rows). */
    fun downloadSubtitle(subtitleInfo: RemoteSubtitleInfo) = subtitles.downloadSubtitle(subtitleInfo)

    /** Searches the server's open-subtitle source for one language. */
    fun searchRemoteSubtitles(language: String) = subtitles.searchRemoteSubtitles(language)

    /** Searches every configured external provider for one language. */
    fun searchAllProviders(language: String) = subtitles.searchAllProviders(language)

    /** Downloads an external-provider search result. */
    fun downloadProviderSubtitle(result: SubtitleSearchResult) = subtitles.downloadProviderSubtitle(result)

    /** Re-runs a failed/sync-less upload for a downloaded-subtitle row. */
    fun retryUploadSubtitle(rowKey: String) = subtitles.retryUploadSubtitle(rowKey)

    /** Uploads a picked local subtitle file to the server item. */
    fun uploadSubtitle(
        uri: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ) = subtitles.uploadSubtitle(uri, fileName, language, isForced, isHearingImpaired)

    // ── Named style presets (the hub's Style tab) ─────────────────────────

    /** The named style presets (pure data over the style controller). */
    val userStylePresets: Flow<List<SubtitleStylePreset>> = subtitleStyleController.userStylePresets

    /** Saves [style] under [name] (the hub's save-preset flow). */
    fun saveStylePreset(name: String, style: SubtitleStyle) = subtitleStyleController.savePreset(name, style)

    /** Deletes a named preset. */
    fun deleteStylePreset(name: String) = subtitleStyleController.deletePreset(name)

    // ── Track / queue pickers ─────────────────────────────────────────────

    /** The audio/subtitle track slice (audio + subtitle + version pickers). */
    val trackState: StateFlow<TrackState> = trackSelectionHelper.state

    /** The manual up-next queue rows (the queue sheet; the controller is the single truth). */
    val queueItems: StateFlow<List<VideoQueueItem>> = videoQueue.queue

    // ── AV-sync / decoder / playback-info sheets ──────────────────────────

    /** The audio-effects slice (AV-sync, decoder, playback-info sheets). */
    val effectsState: StateFlow<AudioEffectsState> = effects.state

    fun setAudioDelay(ms: Long) = effects.setAudioDelay(ms)

    /** The AV-sync slider's live drag leg — debounced engine sync, no persist. */
    fun setAudioDelayLive(ms: Long) = effects.setAudioDelayLive(ms)

    fun setDecoderMode(mode: DecoderMode) = effects.setDecoderMode(mode)

    // ── SyncPlay sheet ────────────────────────────────────────────────────

    /** The SyncPlay group-display slice. */
    val syncPlayState: StateFlow<SyncPlayUiState> = syncPlay.state

    /** The "ignore wait" toggle mirror. */
    val syncPlayIgnoreWait: StateFlow<Boolean> = syncPlay.ignoreWait

    fun toggleSyncPlayTransport() = syncPlay.togglePlayPause()

    fun sendSyncPlayStop() = syncPlay.sendStop()

    fun leaveSyncPlayGroup() = syncPlay.leaveGroup()

    fun setSyncPlayIgnoreWait(ignore: Boolean) = syncPlay.setIgnoreWait(ignore)

    // ── Sleep-timer sheet ─────────────────────────────────────────────────

    /** The sleep-timer slice (the sheet's duration/end-of-episode pickers). */
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    /** The remaining-time feed (sheet-local binder subscription). */
    val sleepTimerRemainingMs: StateFlow<Long> = sleepTimer.remainingMs

    fun startSleepTimer(durationMs: Long) = sleepTimer.startSleepTimer(durationMs)

    fun startSleepTimerEndOfEpisode() = sleepTimer.startSleepTimerEndOfEpisode()

    fun cancelSleepTimer() = sleepTimer.cancelSleepTimer()

    // ── Rendering sheet ───────────────────────────────────────────────────

    /**
     * The session-scoped render state (override lens + deinterlace) — shared
     * with [ControlsSessionSlice.render]; pure state, no commands.
     */
    val render: SessionRenderState = sessionRender

    /** The global mpv slice the sheet's pickers start from (before the session lens). */
    val globalMpvConfig: MpvEngineConfig get() = renderControls.globalMpvConfig

    // ── HDR output sheet ─────────────────────────────────────────────────

    /** The global ExoPlayer slice the HDR sheet's tone-mapping picker starts from. */
    val globalExoConfig: ExoPlayerEngineConfig get() = renderControls.globalExoConfig

    /** The HDR sheet's mpv passthrough toggle (global pick, session-lens bridge — see [RenderControls]). */
    fun setMpvHdrPassthrough(enabled: Boolean) = renderControls.setMpvHdrPassthrough(enabled)

    /** The HDR sheet's ExoPlayer tone-mapping pick (global pick — applies on next playback). */
    fun setExoHdrToneMapping(mode: ExoHdrToneMappingMode) = renderControls.setExoHdrToneMapping(mode)

    /** The playback slice's HDR delivery posture — the HDR sheet's format picker starts from it (#5). */
    val hdrFormatPreference: HdrFormatPreference get() = renderControls.hdrFormatPreference

    /** The HDR sheet's HDR-format pick (persisted playback pref — the fanout's diff trigger re-applies it). */
    fun setHdrFormatPreference(preference: HdrFormatPreference) = renderControls.setHdrFormatPreference(preference)

    // ── Persisted render prefs (the Render sheet's store-writing picks) ──

    /** The tone-mapping-curve pick — the prefs-write choreography stays in [PlayerUiCommands]. */
    fun setMpvToneMappingCurve(curve: MpvToneMappingCurve) = uiCommands.setMpvToneMappingCurve(curve)

    /** The deband-strength pick — same choreography as [setMpvToneMappingCurve]. */
    fun setMpvDebandMode(mode: MpvDebandMode) = uiCommands.setMpvDebandMode(mode)

    /** The motion-interpolation toggle — same choreography as [setMpvToneMappingCurve]. */
    fun setMpvMotionInterpolation(enabled: Boolean) = uiCommands.setMpvMotionInterpolation(enabled)
}

/**
 * Builds the whole slice graph in ONE place (construction locality): every
 * session collaborator a screen region consumes is projected here, once, and
 * the VM stores the result as its single `sessionSlices` member. The
 * route-button projection arrives from the construction site (the platform
 * cast surface is the VM's to reach; the slices stay VM-independent).
 */
internal fun PlaybackSession.createSessionSlices(
    routeButton: CastRouteButton? = null,
): PlayerSessionSlices {
    val loadPhaseFlow = playerSessionManager.sessionState
        .map { it.loadPhase }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = playerSessionManager.sessionState.value.loadPhase,
        )
    // The PlaybackMode sheet's slice is built BEFORE the diagnostics
    // telemetry: its [SheetsSessionSlice.switchableEngines] flow is the ONE
    // engine-change verdict (cast connectivity + SyncPlay + engine identity +
    // platform availability), and the recovery banner's SwitchEngine offer and
    // apply arms below gate on it — never a teardown the sheet's verdict
    // forbids.
    val sheetsSlice = SheetsSessionSlice(
        scope = scope,
        subtitles = subtitles,
        subtitleStyleController = subtitleStyleController,
        trackSelectionHelper = trackSelectionHelper,
        videoQueue = videoQueue,
        effects = effects,
        syncPlay = syncPlay,
        sleepTimer = sleepTimer,
        renderControls = render,
        sessionRender = sessionRender,
        playerSessionManager = playerSessionManager,
        cast = cast,
        uiCommands = uiCommands,
        subtitlePreferences = subtitlePreferences,
        availablePlayerTypes = availablePlayerTypes,
    )
    // The diagnostics telemetry pump: the ACTIVE engine's live telemetry →
    // slice flows, subscriptions replaced on every engine swap (only the
    // current engine and its advisor verdicts write them).
    val diagnosticsTelemetry = DiagnosticsTelemetry(
        scope = scope,
        sessionManager = playerSessionManager,
        switchableEngines = sheetsSlice.switchableEngines,
        localControlBlocked = sheetsSlice.localControlBlocked,
    )
    val diagnosticsSlice = DiagnosticsSessionSlice(
        loadPhase = loadPhaseFlow,
        bufferHealth = diagnosticsTelemetry.bufferHealth,
        throughput = diagnosticsTelemetry.throughput,
        activeRecovery = diagnosticsTelemetry.activeRecovery,
        stallCause = diagnosticsTelemetry.stallCause,
        rebufferThresholdMs = diagnosticsTelemetry.rebufferThresholdMs,
        onApplyAction = { action ->
            when (action) {
                is RecoveryAction.SwitchQuality -> {
                    // Quality reload re-issues the stream — under group
                    // (SyncPlay/cast) control it must not run (the same
                    // local-control verdict that gates the engine swap), and
                    // the Playback Mode sheet's delivery-side eligibility
                    // holds here too: a forced-direct-play session and a
                    // downloaded item have no quality tier to switch to, so
                    // neither a preference write nor a recovery reload may
                    // fire.
                    val eligibleMode =
                        cachedAggregate.playback.playbackMode != PlaybackMode.FORCE_DIRECT_PLAY
                    val eligibleItem = !playerSessionManager.sessionState.value.isOffline
                    if (!sheetsSlice.localControlBlocked.value && eligibleMode && eligibleItem) {
                        uiCommands.setStreamingQuality(action.targetQuality)
                    }
                }
                is RecoveryAction.SwitchEngine -> {
                    // The shared engine-change verdict gates the apply side:
                    // under cast/SyncPlay (or for an unavailable target) the
                    // retry is a no-op, never a teardown.
                    if (action.targetEngine in sheetsSlice.switchableEngines.value) {
                        retryWithEngine(
                            playerType = action.targetEngine,
                            playbackSpeed = engine?.playbackSpeed ?: 1.0f,
                            streamingQuality = cachedAggregate.playback.streamingQuality,
                            sessionOnly = true,
                        )
                    }
                }
                RecoveryAction.BoostBufferSize -> diagnosticsTelemetry.boostBufferSize()
                RecoveryAction.Dismiss -> diagnosticsTelemetry.dismissRecovery()
            }
        },
        onDismissAction = {
            diagnosticsTelemetry.dismissRecovery()
        },
        onCancelAction = {
            // The session-owned cancel op: its ledger bracket takes the final
            // Stop snapshot BEFORE the manager state clears, then the paired
            // VM-internals half tears the session down — the bare
            // playerSessionManager.release() this replaces skipped the owning
            // LOAD job's cancel, leaving the load spine running (a suspended
            // getMediaDetail resumption or a priming settlement wake-up would
            // resurrect the released session).
            cancelActiveLoad()
        },
    )
    diagnosticsTelemetry.start()

    return PlayerSessionSlices(
        cast = CastSessionSlice(cast, routeButton),
        overlay = OverlaySessionSlice(
            upNext = upNext,
            stillWatching = stillWatching,
            postPlayback = postPlaybackActions,
        ),
        status = StatusSessionSlice(
            abRepeat = abRepeat,
        ),
        controls = ControlsSessionSlice(
            effects = effects,
            abRepeat = abRepeat,
            sleepTimer = sleepTimer,
            syncPlay = syncPlay,
            trackSelectionHelper = trackSelectionHelper,
            playerSessionManager = playerSessionManager,
            sessionRender = sessionRender,
        ),
        sheets = sheetsSlice,
        diagnostics = diagnosticsSlice,
    )
}

/**
 * Pumps the ACTIVE engine's live telemetry into the diagnostics slice's
 * flows — buffer health from the engine's buffered-ahead position, throughput
 * and stream bitrate from its video stats, and while the engine is BUFFERING
 * the stall cause from [StallClassifier] plus the recovery banner from
 * [PlaybackDiagnosticAdvisor]. The per-engine subscriptions are replaced
 * whenever [PlayerSessionManager.engineFlow] changes, so only the CURRENT
 * engine's telemetry (and its advisor verdicts) ever write these flows.
 */
private class DiagnosticsTelemetry(
    private val scope: CoroutineScope,
    private val sessionManager: PlayerSessionManager,
    /**
     * The shared engine-change verdict ([SheetsSessionSlice.switchableEngines]
     * — cast connectivity + SyncPlay + engine identity + platform
     * availability): a SwitchEngine recovery is offered only for a target the
     * verdict admits.
     */
    private val switchableEngines: StateFlow<List<PlayerType>>,
    /**
     * The shared local-control verdict ([SheetsSessionSlice.localControlBlocked]
     * — cast connectivity + SyncPlay): a SwitchQuality recovery is offered
     * only while local control holds — the SAME verdict the apply arm gates
     * on, which stays for a group that becomes active after publication.
     */
    private val localControlBlocked: StateFlow<Boolean>,
) {
    val bufferHealth = MutableStateFlow(
        EngineBufferHealth(
            bufferedDurationMs = 0L,
            rebufferThresholdMs = StallTracker.DEFAULT_REBUFFER_THRESHOLD_MS,
            targetBufferDurationMs = TARGET_BUFFER_DURATION_MS,
        ),
    )
    val throughput = MutableStateFlow(
        NetworkThroughputSnapshot(currentThroughputBps = 0L, estimatedBandwidthBps = 0L, activeBitrateBps = 0),
    )
    val stallCause = MutableStateFlow(StallCause.UNKNOWN)
    val activeRecovery = MutableStateFlow<ActionableRecovery?>(null)
    val rebufferThresholdMs = MutableStateFlow(StallTracker.DEFAULT_REBUFFER_THRESHOLD_MS)

    private val stallTracker = StallTracker()

    /** Whether the BoostBufferSize arm is holding the 30 s floor (cleared on reset). */
    private var boostActive = false

    /**
     * Recovery ids the user dismissed during the CURRENT stall: the 500 ms
     * re-evaluation loop would otherwise republish the dismissed banner every
     * tick. Cleared when the stall ends so a later stall can offer it again.
     * Confined to the session scope + UI thread writes via the flow's
     * transactional update.
     */
    private val dismissedRecoveryIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The frame-counter deltas backing the decoder arms: the engine counters
     * are lifetime-cumulative, so classification/advisor see the clamped
     * per-emission steps (a reset counter deltas as zero-forward, never a
     * negative).
     */
    private var prevDroppedFrames = 0L
    private var prevTotalFrames = 0L
    private var lastDroppedDelta = 0L
    private var lastTotalDelta = 0L

    /**
     * When [lastDroppedDelta]/[lastTotalDelta] were computed (the stats
     * emission that produced them). A stalled decode stops emitting stats, so
     * without this stamp the last deltas would be fed to classification and
     * the advisor forever — stale decoder evidence overriding a live
     * NETWORK_STARVATION verdict.
     */
    private var frameEvidenceAtMs = 0L

    /**
     * The frame-loss deltas, EXPIRED: evidence older than
     * [FRAME_EVIDENCE_FRESHNESS_MS] reads as zero so a quiet stats flow can
     * neither classify a stall as decoder overload nor keep offering the
     * engine switch — while fresh evidence still flows through unchanged (the
     * immediate decoder offer survives).
     */
    private fun freshFrameDeltas(): Pair<Long, Long> =
        if (monotonicNowMillis() - frameEvidenceAtMs <= FRAME_EVIDENCE_FRESHNESS_MS) {
            lastDroppedDelta to lastTotalDelta
        } else {
            0L to 0L
        }

    /** The BoostBufferSize recovery arm: raise the rebuffer threshold to the large window. */
    fun boostBufferSize() {
        rebufferThresholdMs.value = BOOSTED_REBUFFER_THRESHOLD_MS
        boostActive = true
        // The 30 s affordance must reach the engine's buffer too — Exo applies it at next load, mpv live.
        sessionManager.engine?.setBufferConfiguration(
            minBufferMs = BOOSTED_REBUFFER_THRESHOLD_MS.toInt(),
            maxBufferMs = BOOSTED_REBUFFER_THRESHOLD_MS.toInt(),
        )
    }

    fun dismissRecovery() {
        activeRecovery.value?.let { current ->
            dismissedRecoveryIds.update { it + current.id }
        }
        activeRecovery.value = null
    }

    /** Arms the engine watcher; called once by the slice build site. */
    fun start() {
        var engineWatch: Job? = null
        scope.launch {
            sessionManager.engineFlow.collect { engine ->
                // Replace the per-engine subscriptions on every swap so a
                // released engine's emissions can never outlive its binding.
                engineWatch?.cancel()
                engineWatch = engine?.let { eng -> scope.launch { watch(eng) } }
                // A new engine's counters start from its own baseline — the
                // deltas must not fold the previous engine's lifetime in.
                prevDroppedFrames = 0L
                prevTotalFrames = 0L
                lastDroppedDelta = 0L
                lastTotalDelta = 0L
                frameEvidenceAtMs = 0L
                if (engine == null) {
                    resetToDefaults()
                }
            }
        }
    }

    private fun resetToDefaults() {
        // The 30 s boost must not outlive the engine that applied it: drop the
        // threshold back to its default BEFORE the reset snapshot copies it,
        // so the next engine starts unboosted (the same value the published
        // buffer-health snapshot carries).
        rebufferThresholdMs.value = StallTracker.DEFAULT_REBUFFER_THRESHOLD_MS
        bufferHealth.value = EngineBufferHealth(
            bufferedDurationMs = 0L,
            rebufferThresholdMs = rebufferThresholdMs.value,
            targetBufferDurationMs = TARGET_BUFFER_DURATION_MS,
        )
        throughput.value =
            NetworkThroughputSnapshot(currentThroughputBps = 0L, estimatedBandwidthBps = 0L, activeBitrateBps = 0)
        stallCause.value = StallCause.UNKNOWN
        activeRecovery.value = null
        dismissedRecoveryIds.value = emptySet()
        stallTracker.reset()
        boostActive = false
        prevDroppedFrames = 0L
        prevTotalFrames = 0L
        lastDroppedDelta = 0L
        lastTotalDelta = 0L
        frameEvidenceAtMs = 0L
    }

    private suspend fun watch(engine: MediaEngine) = coroutineScope {
        // Buffer health: buffered-ahead duration over the current threshold.
        launch {
            combine(
                engine.bufferedPositionMs,
                engine.positionFlow,
                rebufferThresholdMs,
            ) { bufferedMs, _, thresholdMs ->
                val bufferedAheadMs = (bufferedMs - engine.currentPositionMs).coerceAtLeast(0L)
                bufferHealth.value = EngineBufferHealth(
                    bufferedDurationMs = bufferedAheadMs,
                    rebufferThresholdMs = thresholdMs,
                    targetBufferDurationMs = TARGET_BUFFER_DURATION_MS,
                )
            }.collect()
        }
        // Throughput + active stream bitrate from the engine's video stats
        // (the estimated bandwidth is the only network surface engines expose).
        launch {
            engine.videoStats.collect { stats ->
                // The counters are lifetime-cumulative: fold the clamped
                // per-emission deltas (a reset/unreadable counter must never
                // read as a negative step) for the decoder arms.
                val recentDropped = (stats.droppedFrames - prevDroppedFrames).coerceAtLeast(0L)
                val recentTotal = (stats.totalVideoFrames - prevTotalFrames).coerceAtLeast(0L)
                lastDroppedDelta = recentDropped
                lastTotalDelta = recentTotal
                prevDroppedFrames = stats.droppedFrames
                prevTotalFrames = stats.totalVideoFrames
                frameEvidenceAtMs = monotonicNowMillis()
                // The estimate rides its OWN field: the engines expose no
                // measured instantaneous throughput, so the measured rate
                // stays unset (0) and the pill never shows the EWMA estimate
                // as a download rate.
                throughput.value = NetworkThroughputSnapshot(
                    currentThroughputBps = 0L,
                    estimatedBandwidthBps = stats.estimatedBandwidthBps,
                    activeBitrateBps = stats.videoBitrate ?: 0,
                )
            }
        }
        // Stall lifecycle: classify the cause while BUFFERING and evaluate the
        // recovery advisor on a cadence; clear both when playback recovers or
        // the engine reaches a terminal/idle state (an ERROR or IDLE landing
        // mid-stall must cancel the loop and clear the banner too, or the
        // released stream's stale verdict would survive its own stall).
        var stallStartedAtMs = 0L
        var recoveryLoop: Job? = null
        launch {
            engine.playbackState.collect { state ->
                when (state) {
                    EnginePlaybackState.BUFFERING -> {
                        if (stallStartedAtMs == 0L) {
                            stallStartedAtMs = monotonicNowMillis()
                            stallTracker.recordStall()
                        }
                        // Publish the tracker's escalated threshold for the
                        // CURRENT stall history (3s → 6s → 12s across the
                        // sliding window) BEFORE classification and the
                        // buffer-health diagnostic read it — the window
                        // de-escalates as old stalls age out, and the
                        // stall-start edge alone would freeze a stale value.
                        // An explicit 30 s boost stays the floor only while
                        // boostActive holds.
                        republishRebufferThreshold()
                        stallCause.value = classifyStall(engine)
                        if (recoveryLoop == null) {
                            recoveryLoop = launch {
                                while (isActive) {
                                    // Re-classify per evaluation: the cause is a
                                    // live telemetry fold, not the snapshot taken
                                    // at the BUFFERING edge (a decode stall can
                                    // deepen into starvation as the buffer drains).
                                    republishRebufferThreshold()
                                    stallCause.value = classifyStall(engine)
                                    offerRecovery(engine, stallStartedAtMs)
                                    delay(RECOVERY_EVALUATION_INTERVAL_MS)
                                }
                            }
                        }
                    }
                    EnginePlaybackState.READY,
                    EnginePlaybackState.ENDED,
                    EnginePlaybackState.ERROR,
                    EnginePlaybackState.IDLE,
                    -> {
                        stallStartedAtMs = 0L
                        recoveryLoop?.cancel()
                        recoveryLoop = null
                        stallCause.value = StallCause.UNKNOWN
                        activeRecovery.value = null
                        // The stall ended — a later stall may offer the same
                        // recovery again.
                        dismissedRecoveryIds.value = emptySet()
                    }
                }
            }
        }
    }

    /**
     * The live threshold fold, published before classification/diagnostics
     * read it: the tracker's escalated value for the CURRENT stall history
     * (its sliding window de-escalates as stalls age out) with the 30 s boost
     * floor applied only while [boostActive] holds — otherwise the current
     * unboosted threshold publishes.
     */
    private fun republishRebufferThreshold() {
        rebufferThresholdMs.value = if (boostActive) {
            maxOf(BOOSTED_REBUFFER_THRESHOLD_MS, stallTracker.currentRebufferThresholdMs())
        } else {
            stallTracker.currentRebufferThresholdMs()
        }
    }

    /**
     * The stall cause for the engine's CURRENT telemetry snapshot. The
     * bandwidth estimate is the engines' only network surface: with one, the
     * full priority fold runs; without it, only the network-starvation checks
     * are skipped — decoder frame-counter evidence is still evaluated first,
     * with the short-buffer underrun fallback preserved. The evidence is the
     * EXPIRED deltas ([freshFrameDeltas]): stale frame counters cannot keep a
     * stall classified as decoder overload (nor override a starvation
     * verdict) once the stats flow has gone quiet.
     */
    private fun classifyStall(engine: MediaEngine): StallCause {
        val bufferedAheadMs = (engine.bufferedPositionMs.value - engine.currentPositionMs).coerceAtLeast(0L)
        val stats = engine.videoStats.value
        val activeBitrateBps = stats.videoBitrate ?: 0
        val hasBandwidthEstimate = stats.estimatedBandwidthBps > 0L && activeBitrateBps > 0
        val (recentDropped, recentTotal) = freshFrameDeltas()
        val droppedFrameRatio = if (recentTotal > 0L) recentDropped.toFloat() / recentTotal else 0f
        return StallClassifier.classify(
            bufferedDurationMs = bufferedAheadMs,
            rebufferThresholdMs = rebufferThresholdMs.value,
            throughputRatio = if (hasBandwidthEstimate) {
                stats.estimatedBandwidthBps.toFloat() / activeBitrateBps
            } else {
                Float.MAX_VALUE
            },
            isTranscoding = hasBandwidthEstimate &&
                sessionManager.sessionState.value.playMethod == PlayMethod.TRANSCODE,
            // No engine exposes transcode FPS today — never mis-classify.
            transcodeSpeedRatio = null,
            droppedFrameRatio = droppedFrameRatio,
            isDecoderStuck = false,
        )
    }

    private fun offerRecovery(engine: MediaEngine, stallStartedAtMs: Long) {
        val playerType = PlayerType.entries.firstOrNull { it.displayName == engine.displayName } ?: return
        val stats = engine.videoStats.value
        val (recentDropped, recentTotal) = freshFrameDeltas()
        val verdict = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = stallCause.value,
            stallDurationMs = monotonicNowMillis() - stallStartedAtMs,
            stallsInWindow = stallTracker.stallCount(),
            isDirectPlay = sessionManager.sessionState.value.playMethod == PlayMethod.DIRECT_PLAY,
            currentEngine = playerType,
            streamBitrateBps = stats.videoBitrate ?: 0,
            estimatedBandwidthBps = stats.estimatedBandwidthBps,
            // The LIVE counters: the advisor's decoder-overload arm folds the
            // drop rate — zero defaults would mute it for every stream whose
            // stall duration hadn't yet crossed the actionable threshold.
            droppedFrames = stats.droppedFrames,
            totalVideoFrames = stats.totalVideoFrames,
            recentDroppedFrames = recentDropped,
            recentTotalFrames = recentTotal,
        )
        if (verdict == null) {
            activeRecovery.value = null
            return
        }
        // A dismissal suppresses THIS id for the remainder of the stall (the
        // re-evaluation loop would otherwise republish it every 500 ms); the
        // suppression clears when the stall ends. A still-shown banner of the
        // suppressed verdict (a dismissal racing the publish loop) is
        // retracted; a DIFFERENT, not-dismissed verdict keeps its publication.
        if (verdict.id in dismissedRecoveryIds.value) {
            if (activeRecovery.value?.id == verdict.id) {
                activeRecovery.value = null
            }
            return
        }
        // The engine-switch offer rides the shared verdict: under cast/SyncPlay
        // or an unavailable target the fold admits nothing — never offer a
        // teardown it forbids.
        val switch = verdict.primaryAction as? RecoveryAction.SwitchEngine
        if (switch != null && switch.targetEngine !in switchableEngines.value) {
            activeRecovery.value = null
            return
        }
        // The quality-switch offer rides the SAME local-control verdict the
        // apply arm gates on: under cast/SyncPlay the switch would be a no-op —
        // never publish it (the apply-time check stays for a group that
        // becomes active after publication).
        if (verdict.primaryAction is RecoveryAction.SwitchQuality && localControlBlocked.value) {
            activeRecovery.value = null
            return
        }
        activeRecovery.value = verdict
    }

    private companion object {
        const val TARGET_BUFFER_DURATION_MS = 30_000L
        const val BOOSTED_REBUFFER_THRESHOLD_MS = 30_000L
        const val RECOVERY_EVALUATION_INTERVAL_MS = 500L

        /**
         * How long the frame-counter deltas stay classifiable. Engines emit
         * stats on roughly a 1 s cadence; past this window a quiet stats flow
         * (a stalled decode) means the deltas no longer describe the CURRENT
         * stall — they expire (read as zero) rather than overriding a live
         * starvation verdict.
         */
        const val FRAME_EVIDENCE_FRESHNESS_MS = 2_000L
    }
}
