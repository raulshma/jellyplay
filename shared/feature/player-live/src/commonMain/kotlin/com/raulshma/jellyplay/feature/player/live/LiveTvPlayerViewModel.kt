package com.raulshma.jellyplay.feature.player.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.dischargePipDismissal
import com.raulshma.jellyplay.core.data.playback.reArmPipTransport
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.data.playback.focus.claimOnPlayEdge
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_buffering_timeout
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_cancel_recording
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_no_channels
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_canceled
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_failed
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_success
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerAudio
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import com.raulshma.jellyplay.feature.livetv.LiveTvProgramWindow
import com.raulshma.jellyplay.feature.livetv.components.RecordAction
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import com.raulshma.jellyplay.feature.livetv.nowInstant
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import com.raulshma.jellyplay.core.ui.message.UiMessage
import org.jetbrains.compose.resources.StringResource

private const val PROGRAM_LOOKAHEAD_HOURS = 12L

/**
 * Owns the Live TV player's UI state end to end and forwards everything
 * else: the tune/zap/retry/fallback choreography, the engine and its
 * event-policy shell live in [LivePlaybackSession] (the VOD `PlaybackSession`
 * shape at live scale) whose [LivePlaybackEvent]s this VM folds into
 * [_state]; source resolution lives one layer deeper in `LiveSessionManager`
 * (the PlaybackRepository choreography). This VM keeps the UI-state folding
 * ([foldSessionEvent]), the PiP wiring, the platform seams (audio-focus/mute
 * via [LivePlayerAudio], the rendering handle, the EPG program scan) and the
 * in-player record actions — and surfaces the channel list, zap list +
 * now/next overlay + rebuffer spinner as [state].
 *
 * Channel switching is in-player: channel up/down re-resolve and reload the
 * same engine instance (via the session). The last-watched channel is
 * persisted through [LastChannelStore] by the session's zap choreography.
 *
 * Player-live conveyor: the ViewModel moved to commonMain over four seams —
 * [LiveEngineFactory] (platform engine construction), the engine's
 * commonMain state surface ([LivePlayerEngine]; the media3 player handle
 * stays behind the androidMain `Media3LivePlayerEngine` cast),
 * [LivePlayerAudio] (audio-focus/becoming-noisy + raw player volume; the
 * legacy `PlayerAudioLifecycle` wrapper and its `@ApplicationContext Context`
 * died with it) and [TranscodeReasonsRenderer] (legacy core:ui formatter).
 * The `UserMessageBus`/`UiText` ctor dep died too: record/cancel feedback
 * now flows through [events] as [LivePlayerEvent.Message] values (livetv's
 * LiveTvUserMessage screen-forward seam) and localized error state stays
 * unresolved until render time ([LivePlayerMessage]).
 *
 * Live PiP: the nullable [pip] seam — the shared core:data [PipController]
 * port (its AndroidPipController singleton is the same instance the host
 * Activity reads) — arms auto-enter on each successful tune (the
 * TuneStarted fold), mirrors play state, installs the remote-action transport
 * (SKIP = channel zap; the engineCreated fold re-arms it), discharges the
 * PiP-dismiss latch into teardown + [LivePlayerEvent.ClosePlayer] (the VOD
 * VM's choreography) and tears it all down in [stop].
 *
 * LiveNowWindow: the now/next program scan converged on livetv's vocabulary
 * — the wall-clock read goes through the injected [EpochMillisSource] seam
 * ([nowInstant], never a direct `Clock.System.now()`, fake-able in jvmTest)
 * and the current/next pick is [LiveTvProgramWindow.currentAndNext] (the
 * lenient-parse widening of the C10 timestamp-vocabulary unification; the
 * former strict `Instant.parse` ladder died with it).
 */
class LiveTvPlayerViewModel(
    private val liveTvRepository: LiveTvRepository,
    playbackRepository: PlaybackRepository,
    playbackIdentity: PlaybackIdentity,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val playbackStore: PlaybackStore,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val lastChannelStore: LastChannelStore,
    private val epochMillisSource: EpochMillisSource,
    engineFactory: LiveEngineFactory,
    private val imageUrlProvider: ImageUrlProvider,
    private val audio: LivePlayerAudio? = null,
    transcodeReasonsRenderer: TranscodeReasonsRenderer =
        TranscodeReasonsRenderer { emptyList() },
    private val pip: PipController? = null,
    /**
     * The cross-player exclusivity authority (the video focus slice,
     * ADR-0004): the live stream claims [PlaybackSurfaceId.VIDEO] on the
     * play edge (the music manager's onIsPlayingChanged pattern) and OS
     * losses come back as pause/duck commands through the bound
     * [com.raulshma.jellyplay.core.data.playback.focus.VideoPlaybackSurface].
     * Live's legacy duck-on-transient behavior rides the module's injected
     * video policy, which this VM asserts once at init (see the init block —
     * live had no pref gate; it always ducked). jvmTest takes the vacuous
     * NoopPlaybackFocus default.
     */
    private val playbackFocus: com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus =
        com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvPlayerUiState())
    val state: StateFlow<LiveTvPlayerUiState> = _state.asStateFlow()

    /**
     * One-shot screen events (record/cancel feedback, PiP-dismiss screen
     * close) on the [LivePlayerEvent] vocabulary — ONE intake replacing the
     * former messages/closePlayer member pair (the VOD `SessionEvent`
     * pattern; the VM's public-member ratchet stays at its ceiling). Backed
     * by a `tryEmit`-only, DROP_OLDEST pipe mirroring the engine-session
     * shell's one-shot contract (a mid-teardown emission never suspends).
     */
    private val _screenEvents = MutableSharedFlow<LivePlayerEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LivePlayerEvent> = _screenEvents.asSharedFlow()

    /**
     * High-frequency DVR-window streams kept OUT of [LiveTvPlayerUiState] so
     * the 500 ms position tick invalidates only the leaf that renders it (the
     * bottom-bar seek bar), not the whole screen — mirrors the VOD player's
     * dedicated position/duration flows. The session owns the engine mirrors;
     * these are pass-throughs.
     */
    val positionMs: StateFlow<Long> get() = playbackSession.positionMs
    val durationMs: StateFlow<Long> get() = playbackSession.durationMs

    /**
     * The playback executor — tune/zap/retry/fallback choreography + the
     * engine and its event-policy shell (see [LivePlaybackSession]). Built
     * here over [LiveSessionManager] (source resolution) with this VM's
     * scope, so every launch inside dies with the VM.
     */
    private val playbackSession = LivePlaybackSession(
        scope = viewModelScope,
        liveTvRepository = liveTvRepository,
        sessionManager = LiveSessionManager(playbackRepository),
        playbackStore = playbackStore,
        playbackIdentity = playbackIdentity,
        engineFactory = engineFactory,
        lastChannelStore = lastChannelStore,
        transcodeReasonsRenderer = transcodeReasonsRenderer,
        playbackRepository = playbackRepository,
    )

    /**
     * The route's preferred stream overrides, captured at [initialize] so the
     * PiP transport's channel-zap mapping re-resolves with the same preferred
     * tracks the screen passes on its D-pad/button zaps (the transport has no
     * composition context to read them from).
     */
    private var routeAudioStreamIndex: Int? = null
    private var routeSubtitleStreamIndex: Int? = null

    /**
     * Becoming-noisy auto-pause + raw player volume seam; the androidMain
     * actual also binds the current player as the focus module's VIDEO
     * surface target (the video focus slice — the OS-loss pause/duck
     * commands land there, and its mute command routes to the engine's real
     * [LivePlayerEngine.setMuted]). Live has no resume-skip, so no restore
     * hook.
     */
    private val playerAudioLifecycle: LivePlayerAudio? = audio

    init {
        // Bind the audio seam before anything can create an engine (the
        // platform impl reads the engine + mute state lazily through this
        // owner, mirroring the legacy inline adapter's re-read contract).
        playerAudioLifecycle?.bind(this)

        // Assert live's focus policy once, at start: the legacy
        // `Media3LivePlayerAudio` registered the OS focus seat UNCONDITIONALLY
        // at engine creation and always ducked on a transient loss (restore
        // on regain) — live had no pref gate. The module's
        // [com.raulshma.jellyplay.core.data.playback.focus.VideoFocusPolicyInput]
        // receiver is one process-singleton policy holder, so the VOD
        // wiring's pref pushes override this while VOD plays and this
        // re-asserts on every live start (the legacy seat/duck timing:
        // per-player-session, not per-process). NoopPlaybackFocus (desktop,
        // bare tests) is not a VideoFocusPolicyInput — the cast no-ops.
        (playbackFocus as? com.raulshma.jellyplay.core.data.playback.focus.VideoFocusPolicyInput)
            ?.onVideoFocusPolicy(osLegEnabled = true, duckOnTransientLoss = true)

        // The session's outcomes fold into UI state (plus the PiP wiring each
        // event carries). Subscribed before any funnel can run so no fold is
        // missed — the collector lives for the VM's whole lifetime.
        playbackSession.events.onEach(::foldSessionEvent).launchIn(viewModelScope)

        combine(appRuntimeStateStore.state, playbackStore.playback) { runtime, playback ->
            runtime.favoriteChannels to playback.liveStreamOption
        }.onEach { (favoriteChannels, liveStreamOption) ->
                _state.value = _state.value.copy(
                    favorites = favoriteChannels,
                    liveStreamOption = liveStreamOption,
                )
            }
            .launchIn(viewModelScope)

        lastChannelStore.observeLastChannelId()
            .onEach { id -> _state.value = _state.value.copy(lastChannelId = id) }
            .launchIn(viewModelScope)

        // Controls auto-hide delay comes from the same `videoControlsTimeoutMs`
        // preference the VOD player reads (via VideoPlayerAggregateStore), so a
        // user's choice applies consistently across live and VOD players.
        aggregateStore.aggregate
            .map { it.videoPlayer.videoControlsTimeoutMs }
            .distinctUntilChanged()
            .onEach { ms -> _state.value = _state.value.copy(controlsTimeoutMs = ms) }
            .launchIn(viewModelScope)

        // PiP auto-exit discharge (the VOD VM's pipDismissed collector): the
        // host Activity's autoExitPip collector translates requestAutoExitPip
        // (fired by the EngineStateChanged fold on engine END/ERROR in PiP)
        // into notifyPipDismissed; landing here means the window is showing a
        // dead stream and the screen must close. The ordering (pause →
        // teardown → close → the defensive latch clear, issue #145) lives on
        // the shared helper — this host supplies only its teardown list and
        // its close pipe.
        viewModelScope.dischargePipDismissal(
            pip = pip,
            teardown = {
                playbackSession.pause()
                stop()
            },
            close = { _screenEvents.tryEmit(LivePlayerEvent.ClosePlayer) },
        )
    }

    /**
     * The single command funnel (the VideoPlayerUiEvent / AudioPlayerUiEvent
     * precedent): every user intent the screen expresses arrives as a
     * [LiveTvPlayerUiEvent] and routes once here to a private handler — the
     * former per-action public funs. The public surface beyond the funnel is
     * the state flows, the queries ([engineForRendering], [logoUrlFor]), the
     * media3 relay [onVideoSizeChanged] and the lifecycle [stop] — pinned by
     * LiveTvPlayerViewModelOwnershipTest.
     */
    fun onEvent(event: LiveTvPlayerUiEvent) {
        when (event) {
            is LiveTvPlayerUiEvent.Initialize ->
                initialize(event.channelId, event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.ChannelUp -> playbackSession.channelUp(event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.ChannelDown -> playbackSession.channelDown(event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.SelectChannelById -> playbackSession.selectChannelById(event.channelId)
            is LiveTvPlayerUiEvent.ToggleFavorite -> toggleFavorite(event.channelId)
            is LiveTvPlayerUiEvent.RecordCurrentProgramOnce -> recordCurrentProgramOnce()
            is LiveTvPlayerUiEvent.RecordCurrentProgramSeries -> recordCurrentProgramSeries()
            is LiveTvPlayerUiEvent.CancelCurrentProgramTimer -> cancelCurrentProgramTimer()
            is LiveTvPlayerUiEvent.CancelCurrentProgramSeries -> cancelCurrentProgramSeries()
            is LiveTvPlayerUiEvent.TogglePlayPause -> playbackSession.togglePlayPause()
            is LiveTvPlayerUiEvent.SeekToLiveEdge -> playbackSession.seekToLiveEdge()
            is LiveTvPlayerUiEvent.SeekWithinDvr -> playbackSession.seekWithinDvr(event.positionMs)
            is LiveTvPlayerUiEvent.PlayFromStart -> playbackSession.playFromStart()
            is LiveTvPlayerUiEvent.RefreshPosition -> playbackSession.refreshLiveWindow()
            is LiveTvPlayerUiEvent.ToggleMute -> toggleMute()
            is LiveTvPlayerUiEvent.Retry -> playbackSession.retry(event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.SetLiveStreamOption -> setLiveStreamOption(event.option)
        }
    }

    /**
     * THE fold: every [LivePlaybackEvent] the session emits becomes exactly
     * the UI-state write (plus the PiP wiring) the former inline executor
     * bodies performed — the event vocabulary's KDocs are the fold's spec.
     */
    private fun foldSessionEvent(event: LivePlaybackEvent) {
        when (event) {
            LivePlaybackEvent.ChannelLoadStarted ->
                _state.value = _state.value.copy(isLoadingChannels = true)
            LivePlaybackEvent.ChannelLoadFailed ->
                _state.value = _state.value.copy(
                    isLoadingChannels = false,
                    isBuffering = false,
                    errorMessage = UiMessage.Resource(Res.string.live_error_no_channels),
                )
            is LivePlaybackEvent.ChannelsCommitted ->
                _state.value = _state.value.copy(
                    isLoadingChannels = false,
                    channels = event.channels,
                    currentIndex = event.index,
                    currentChannel = event.channel,
                )
            is LivePlaybackEvent.ChannelSelected ->
                _state.value = _state.value.copy(
                    currentIndex = event.index,
                    currentChannel = event.channel,
                    currentProgram = null,
                    nextProgram = null,
                    isSwitchingChannel = true,
                )
            is LivePlaybackEvent.TuneFailed ->
                _state.value = _state.value.copy(
                    isBuffering = false,
                    isSwitchingChannel = false,
                    errorMessage = event.message,
                )
            is LivePlaybackEvent.TuneStarted -> {
                // PiP auto-entry arms on every successful tune — the Activity
                // additionally gates entry on isPlaying + unlocked controls, so
                // an armed-but-paused live stream never yanks the user into PiP.
                pip?.requestAutoEnterPip(true)
                _state.value = _state.value.copy(isSwitchingChannel = false)
                viewModelScope.launch { loadPrograms(event.channel.id) }
            }
            is LivePlaybackEvent.PlayMethodChanged ->
                _state.value = _state.value.copy(playMethod = event.method)
            is LivePlaybackEvent.TranscodeReasonsChanged ->
                _state.value = _state.value.copy(transcodeReasons = event.reasons)
            LivePlaybackEvent.EngineCreated -> {
                // Install becoming-noisy (+ the focus surface binding) only
                // once for the (reused) engine instance, mirroring the VOD
                // player. They persist across channel switches and are torn
                // down in [stop]. The FOCUS CLAIM itself rides the play edge
                // below — not the engine creation.
                playerAudioLifecycle?.onEngineCreated()
                // Re-arm the PiP transport with every engine creation: [stop]
                // nulls it via PipController.reset() — the re-arm lifecycle
                // rationale lives on [reArmPipTransport].
                registerPipTransport()
            }
            is LivePlaybackEvent.EngineStateChanged -> {
                _state.value = _state.value.copy(
                    engineState = event.state,
                    isBuffering = event.state == LiveEngineState.BUFFERING ||
                        event.state == LiveEngineState.IDLE,
                    errorMessage = event.errorMessage,
                    errorDetail = event.errorDetail,
                )
                // Auto-exit PiP on engine END/ERROR so the floating window
                // does not linger on a dead stream; the Activity's collector
                // translates this into the existing dismiss path (pause +
                // finish). Mirrors the VOD coordinator's playbackState policy.
                if (
                    (event.state == LiveEngineState.ERROR || event.state == LiveEngineState.ENDED) &&
                    pip?.isInPipMode?.value == true
                ) {
                    pip?.requestAutoExitPip()
                }
            }
            is LivePlaybackEvent.PlayingChanged -> {
                _state.value = _state.value.copy(isPlaying = event.isPlaying)
                pip?.setPlaying(event.isPlaying)
                // The focus claim rides this ONE edge (the music manager's
                // onIsPlayingChanged pattern), folded onto the shared
                // [claimOnPlayEdge] body: a granted claim evicts MUSIC
                // synchronously; a DENIED claim pauses (see the helper's
                // KDoc for the contract). The duck path never crosses here:
                // a ducked live claim stays Held and the stream keeps
                // playing.
                playbackFocus.claimOnPlayEdge(
                    surfaceId = PlaybackSurfaceId.VIDEO,
                    isPlaying = event.isPlaying,
                    onDenied = { playbackSession.pause() },
                )
            }
            is LivePlaybackEvent.AtLiveEdgeChanged ->
                _state.value = _state.value.copy(isAtLiveEdge = event.atLiveEdge)
            LivePlaybackEvent.BufferingWatchdogTimedOut ->
                _state.value = _state.value.copy(
                    isBuffering = false,
                    errorMessage = UiMessage.Resource(
                        Res.string.live_error_buffering_timeout
                    ),
                )
            is LivePlaybackEvent.TranscodeFallbackFailed ->
                _state.value = _state.value.copy(
                    isBuffering = false,
                    errorMessage = event.message,
                    errorDetail = event.engineErrorDetail,
                )
        }
    }

    /**
     * Entry point invoked from the screen's `LaunchedEffect(channelId)`.
     * Loads the channel list, selects the start channel (preferring the
     * route's id — i.e. the channel the user actually tapped — then the
     * last-watched id, then the first channel), and starts playback.
     * Idempotent — subsequent calls with the same id are no-ops so
     * recomposition doesn't restart playback (the session's `initialized`
     * latch).
     */
    private fun initialize(
        channelId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        // Defensive (the VOD VM's initialize posture): the PiP seam is a
        // process singleton whose one-shot event flags outlive this Activity.
        // A flag left set by an abnormally torn-down previous session must
        // never greet the next tune — the fresh screen would react to it
        // instantly and close (issue #145). Legitimate in-flight dismiss
        // flows end in stop + close, never a new initialize.
        pip?.clearPipDismissed()
        pip?.consumeAutoExitPip()
        // Captured even on a no-op re-init (initialized already true) so the
        // PiP transport's zap mapping always carries the latest route's
        // overrides — a PlayerActivity onNewIntent args swap re-fires this.
        routeAudioStreamIndex = audioStreamIndex
        routeSubtitleStreamIndex = subtitleStreamIndex
        playbackSession.initialize(channelId, audioStreamIndex, subtitleStreamIndex)
    }

    /**
     * Adds/removes [channelId] from the user's favorite channels. The flip
     * lives on the store ([AppRuntimeStateStore.toggleFavoriteChannel] — the
     * same command the Channels tab's star uses, reading the current set
     * inside the [DataStore.edit] transaction); the `init` observer
     * propagates the change into [_state].
     */
    private fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            appRuntimeStateStore.toggleFavoriteChannel(channelId)
        }
    }

    // ── In-player recording ──
    // The shared [RecordActions] choreography (livetv's ONE record
    // flow), adapted to this screen's feedback surface exactly as
    // ChannelDetailViewModel does it: one-shot messages on [events]
    // (Resource success/canceled, Raw failure with the legacy fallback
    // literals) and a re-fetch of the current channel's program window after
    // every successful action so the Record ↔ Cancel sheet state follows the
    // server. The funnels below stay no-op without a current program (and,
    // for cancels, without the matching timer id — [RecordActions] guards).

    private val recordActions = RecordActions(liveTvRepository, viewModelScope) { outcome ->
        when (outcome) {
            is RecordOutcome.Success -> {
                _screenEvents.tryEmit(LivePlayerEvent.Message(outcome.request.action.successMessage()))
                viewModelScope.launch { refreshProgramsForCurrentChannel() }
            }
            is RecordOutcome.Error ->
                _screenEvents.tryEmit(
                    LivePlayerEvent.Message(
                        UiMessage.of(outcome.message, outcome.request.action.failureFallbackRes())
                    )
                )
            is RecordOutcome.Requesting, RecordOutcome.Idle -> Unit
        }
    }

    /** Schedules a single-episode timer for the current program. */
    private fun recordCurrentProgramOnce() {
        _state.value.currentProgram?.let(recordActions::recordOnce)
    }

    /** Schedules a series timer rooted at the current program. */
    private fun recordCurrentProgramSeries() {
        _state.value.currentProgram?.let(recordActions::recordSeries)
    }

    /** Cancels the single timer on the current program (if one is set). */
    private fun cancelCurrentProgramTimer() {
        _state.value.currentProgram?.let { recordActions.cancelTimer(it) }
    }

    /** Cancels the series timer on the current program (if one is set). */
    private fun cancelCurrentProgramSeries() {
        _state.value.currentProgram?.let { recordActions.cancelSeries(it) }
    }

    /**
     * Re-fetches the program window for the current channel and merges the
     * refreshed [LiveTvProgram] (with its updated timerId / seriesTimerId)
     * into [_state]. Used by the record/cancel actions above so the Record ↔
     * Cancel sheet reflects the latest server state without a full reload.
     */
    private suspend fun refreshProgramsForCurrentChannel() {
        val channelId = _state.value.currentChannel?.id ?: return
        loadPrograms(channelId)
    }

    private suspend fun loadPrograms(channelId: String) {
        val now = epochMillisSource.nowInstant()
        val end = now + PROGRAM_LOOKAHEAD_HOURS.hours
        val programs = liveTvRepository.getLiveTvPrograms(
            channelId = channelId,
            // kotlin.time.Instant.toString() renders the same ISO-8601 UTC
            // instant the former DateTimeFormatter.ISO_INSTANT produced
            // (seconds always, fraction only when non-zero).
            startDateUtc = now.toString(),
            endDateUtc = end.toString(),
        ).getOrNull().orEmpty()
        // LiveNowWindow: the one canonical now/next fold (lenient parse +
        // half-open airing window) — the former strict-parse Triple scan died
        // with the C10 timestamp-vocabulary unification.
        val (current, next) = LiveTvProgramWindow.currentAndNext(programs, now)
        _state.value = _state.value.copy(currentProgram = current, nextProgram = next)
    }

    /**
     * Sets the live stream delivery [option] (Auto / Direct Stream /
     * Transcode) as the global default and re-resolves the current channel
     * under it. Mirrors the VOD `VideoPlayerViewModel.reloadPlaybackForMode`:
     * the old session is stop-reported, the new option is persisted, and the
     * engine reloads the re-resolved URL. No-op if no channel is active.
     */
    private fun setLiveStreamOption(option: LiveStreamOption) {
        val channel = _state.value.currentChannel ?: return
        // Reflect the choice in UI state immediately (ahead of the async
        // DataStore -> preferences collector) so the option sheet keeps the
        // selection visible during the reload instead of briefly reverting.
        _state.value = _state.value.copy(liveStreamOption = option)
        viewModelScope.launch {
            playbackStore.setLiveStreamOption(option)
            _state.value = _state.value.copy(
                isBuffering = true,
                errorMessage = null,
                isSwitchingChannel = true,
            )
            playbackSession.playChannel(
                channel = channel,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
    }

    /**
     * Toggles real mute on the live engine ([LivePlayerEngine.setMuted] —
     * engine state, not a volume write). The engine hosts the shared
     * volume/mute template: mute captures the pre-mute level and silences
     * the native handle; unmute restores that exact level. Live's declared
     * divergences ride the template call as data — no system-stream sync, no
     * persistence (the engine instance's lifetime is the memory's), and —
     * the fix over the former volume-0 hack — mute now survives engine/track
     * volume resets because it is real state. No-op before the first tune /
     * after [stop] releases the engine.
     */
    private fun toggleMute() {
        val engine = playbackSession.engineForRendering() ?: return
        val muted = !_state.value.isMuted
        engine.setMuted(muted)
        _state.value = _state.value.copy(isMuted = muted)
    }

    /**
     * Arms the PiP transport bridge so the host Activity can dispatch PiP
     * remote-action intents to the live engine. The null-controller guard and
     * assignment mechanics (plus the Activity-scoped-VM re-arm rationale)
     * live in [reArmPipTransport]; this body owns only the live mapping:
     * PLAY/PAUSE hit the engine directly (no SyncPlay/cast routing exists on
     * live); the window's rewind/forward SKIP actions zap channel-down/up
     * (the live-TV PiP convention — a DVR micro-seek is meaningless on
     * pure-live streams, and seekWithinDvr is already a no-op there),
     * re-resolving with the route's preferred stream overrides; NEXT stays
     * unmapped (live has no "next episode", so pipHasNext is never set and
     * the Activity never renders that action).
     */
    private fun registerPipTransport() {
        reArmPipTransport(pip) { action ->
            when (action) {
                PipAction.PLAY -> playbackSession.play()
                PipAction.PAUSE -> playbackSession.pause()
                PipAction.SKIP_FORWARD ->
                    playbackSession.channelUp(routeAudioStreamIndex, routeSubtitleStreamIndex)
                PipAction.SKIP_BACKWARD ->
                    playbackSession.channelDown(routeAudioStreamIndex, routeSubtitleStreamIndex)
                PipAction.NEXT -> Unit
            }
        }
    }

    /**
     * PiP aspect-ratio feed from the Android screen's video surface (media3
     * `onVideoSizeChanged`): forwards the decoded video's dimensions so the
     * host Activity shapes the PiP window to the content instead of its 16:9
     * fallback. A non-positive pair clears the override. The commonMain
     * engine surface carries no video-size state, so this rides the screen
     * (the only place media3's [androidx.media3.common.VideoSize] is visible)
     * — same shape as the VOD player's `updatePipSourceRect` screen seam.
     */
    fun onVideoSizeChanged(width: Int, height: Int) {
        pip?.setPipAspectRatio(if (width > 0 && height > 0) width to height else null)
    }

    /** Exposes the live engine for PlayerView attachment (null before first load). */
    fun engineForRendering(): LivePlayerEngine? = playbackSession.engineForRendering()

    /** Channel logo URL for the chrome/zap toast; null when no image tag. */
    fun logoUrlFor(channel: LiveTvChannel): String? =
        if (!channel.imageTag.isNullOrBlank()) imageUrlProvider.getImageUrl(channel.id) else null

    /**
     * Releases the live session and resets playback state. Called from
     * [LivePlayerScreen]'s `onDispose` so that leaving the screen — including
     * a nav-back — tears down the ExoPlayer immediately instead of letting
     * audio keep playing in the background until activity destroy.
     *
     * The live VM is activity-scoped (nav3 entries don't install a per-entry
     * ViewModelStore owner here), so [onCleared] alone only fires on process
     * / activity teardown — far too late for a back press. The session's
     * release resets its `initialized` latch so the screen re-inits playback
     * cleanly if the user returns to the same channel.
     */
    fun stop() {
        // Abandon the focus claim + tear down becoming-noisy (+ the surface
        // binding, via onReleased) before releasing the engine so the
        // commands never dereference a torn-down player (idempotent).
        playbackFocus.release(PlaybackSurfaceId.VIDEO)
        playerAudioLifecycle?.onReleased()
        // Session teardown: engine-event shell disposed before the engine
        // release, the engine released, the deferred-zap and position mirrors
        // reset (see LivePlaybackSession.release). The engine's mute state
        // (real setMuted + its pre-mute memory) dies with the released
        // engine, so a fresh entry never inherits a stale level — and
        // isMuted is reset via the fresh uiState below.
        playbackSession.release()
        // Full PiP teardown: nulls the transport, disarms auto-enter and drops
        // the aspect/playing mirrors so a stale armed flag can't float the next
        // screen's window into PiP. The transport re-arms in the EngineCreated
        // fold on the next entry.
        pip?.reset()
        _state.value = LiveTvPlayerUiState()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

/** Timer creations announce success; cancels announce cancellation. */
private fun RecordAction.startsTimer(): Boolean =
    this == RecordAction.RECORD_ONCE || this == RecordAction.RECORD_SERIES

private fun RecordAction.successMessage(): LivePlayerMessage =
    if (startsTimer()) {
        UiMessage.Resource(Res.string.live_record_success)
    } else {
        UiMessage.Resource(Res.string.live_record_canceled)
    }

/**
 * The failure fallback resource per action ([UiMessage.of]'s Resource arm) —
 * the former baked English literals ("Failed to set recording" / "Failed to
 * cancel recording"), now localized string resources.
 */
private fun RecordAction.failureFallbackRes(): StringResource =
    if (startsTimer()) {
        Res.string.live_record_failed
    } else {
        Res.string.live_error_cancel_recording
    }
