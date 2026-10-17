package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.AbRepeatController
import com.raulshma.jellyplay.feature.player.video.AbRepeatEvent
import com.raulshma.jellyplay.feature.player.video.AbRepeatState
import com.raulshma.jellyplay.feature.player.video.PlaybackSession
import com.raulshma.jellyplay.feature.player.video.PlayerCastController
import com.raulshma.jellyplay.feature.player.video.PlayerSessionManager
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
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import com.raulshma.jellyplay.feature.player.video.state.SleepTimerState
import com.raulshma.jellyplay.feature.player.video.state.SubtitleState
import com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState
import com.raulshma.jellyplay.feature.player.video.state.TrackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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
)

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
 * confirm — the projections [PlayerCenterOverlayTier] renders. All
 * low-frequency (card resolves / load settles / prompt raises).
 */
internal class OverlaySessionSlice(
    upNext: UpNextController,
    stillWatching: StillWatchingController,
) {
    /**
     * The Up Next card — the controller's projection, already resolved with
     * the ADVANCE's precedence (queue head beats the episode sibling, null
     * under SyncPlay). The overlay never re-decides precedence from raw state.
     */
    val upNextCard: StateFlow<UpNextCard?> = upNext.upNextCard

    /** True while a next-episode advance is in flight and unsettled (#146). */
    val isNextEpisodeLoading: StateFlow<Boolean> = upNext.isNextEpisodeLoading

    /** The "Still watching?" confirm overlay's state surface; null = hidden (feature 1.3). */
    val stillWatchingPrompt: StateFlow<StillWatchingPromptState?> = stillWatching.prompt
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
 * the subtitle hub (state + remote-subtitle workflow), the named style
 * presets, the audio/subtitle/version pickers, the queue sheet, the AV-sync
 * and decoder sheets, the SyncPlay and sleep-timer sheets, and the Rendering
 * sheet's session + global render facts.
 */
internal class SheetsSessionSlice(
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
) {
    /** Reactive engine handle — the hub's custom-mpv-config ownership snapshot. */
    val engineFlow: StateFlow<MediaEngine?> = playerSessionManager.engineFlow

    // ── Subtitle hub ──────────────────────────────────────────────────────

    /** The subtitle workflow's state slice (search/download/upload + hub flags). */
    val subtitleState: StateFlow<SubtitleState> = subtitles.state

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
): PlayerSessionSlices = PlayerSessionSlices(
    cast = CastSessionSlice(cast, routeButton),
    overlay = OverlaySessionSlice(
        upNext = upNext,
        stillWatching = stillWatching,
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
    sheets = SheetsSessionSlice(
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
    ),
)
