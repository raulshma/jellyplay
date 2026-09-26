package com.raulshma.jellyplay.feature.player.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.dischargePipDismissal
import com.raulshma.jellyplay.core.data.playback.reArmPipTransport
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.TranscodeReasonsRefresher
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
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.feature.player.video.engine.EngineDecision
import com.raulshma.jellyplay.feature.player.video.engine.EngineEventCoordinator
import com.raulshma.jellyplay.feature.player.video.engine.EngineEventSource
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineSessionShell
import com.raulshma.jellyplay.feature.player.video.engine.FallbackPolicy
import com.raulshma.jellyplay.feature.player.video.engine.WatchdogScope
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_buffering_timeout
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_no_channels
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_playback_fallback
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_resolve_failed
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_transcode_fallback
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_canceled
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_success
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineConfig
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LiveMuteMemory
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerAudio
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import com.raulshma.jellyplay.feature.livetv.LiveTvProgramWindow
import com.raulshma.jellyplay.feature.livetv.components.RecordAction
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import com.raulshma.jellyplay.feature.livetv.nowInstant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

private const val TAG = "LiveTvPlayerViewModel"

private const val PROGRAM_LOOKAHEAD_HOURS = 12L
private const val CHANNEL_LIST_LIMIT = 200
/**
 * This host's buffering-watchdog window, fed to the shared
 * [EngineEventCoordinator] at construction (candidate C4: the policy core is
 * the VOD coordinator's, moved to player-contract; the timeout is a policy
 * KNOB here). If a live stream stays in BUFFERING this long without reaching
 * READY (common with flaky tuners that stall without raising a
 * PlaybackException), the coordinator's timeout decision surfaces an
 * actionable error instead of spinning the rebuffer spinner forever.
 */
private const val LIVE_BUFFERING_TIMEOUT_MS = 20_000L

/**
 * Owns Live TV playback end to end: loads the channel list from
 * [LiveTvRepository], resolves the live stream URL via [LiveSessionManager]
 * (the PlaybackRepository choreography), drives a
 * [LivePlayerEngine], and surfaces UI state for the zap list + now/next
 * overlay + rebuffer spinner.
 *
 * Channel switching is in-player: [channelUp] / [channelDown] re-resolve and
 * call [LivePlayerEngine.load] on the same instance. The last-watched channel
 * is persisted via [LastChannelStore].
 *
 * Player-live conveyor: the ViewModel moved to commonMain over
 * four seams — [LiveEngineFactory] (platform engine construction), the
 * engine's commonMain state surface ([LivePlayerEngine]; the media3 player
 * handle stays behind the androidMain `Media3LivePlayerEngine` cast),
 * [LivePlayerAudio] (audio-focus/becoming-noisy + raw player volume; the
 * legacy `PlayerAudioLifecycle` wrapper and its `@ApplicationContext Context`
 * died with it) and [TranscodeReasonsRenderer] (legacy core:ui formatter).
 * The `UserMessageBus`/`UiText` ctor dep died too: record/cancel feedback
 * now flows through [events] as [LivePlayerEvent.Message] values (livetv's
 * LiveTvUserMessage screen-forward seam) and localized error state stays
 * unresolved until render time ([LivePlayerMessage]).
 *
 * Live PiP: the nullable [pip] seam — the shared core:data
 * [PipController] port (its AndroidPipController singleton is the same
 * instance the host Activity reads) — arms auto-enter on each
 * successful tune, mirrors play state, installs the remote-action transport
 * (SKIP = channel zap), discharges the PiP-dismiss latch into teardown +
 * [LivePlayerEvent.ClosePlayer] (the VOD VM's choreography) and tears it all
 * down in [stop].
 *
 * LiveNowWindow: the now/next program scan converged on livetv's
 * vocabulary — the wall-clock read goes through the injected
 * [EpochMillisSource] seam ([nowInstant], never a direct `Clock.System.now()`,
 * fake-able in jvmTest) and the current/next pick is
 * [LiveTvProgramWindow.currentAndNext] (the lenient-parse widening of the
 * C10 timestamp-vocabulary unification; the former strict `Instant.parse`
 * ladder died with it).
 */
class LiveTvPlayerViewModel(
    private val liveTvRepository: LiveTvRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackIdentity: PlaybackIdentity,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val playbackStore: PlaybackStore,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val lastChannelStore: LastChannelStore,
    private val epochMillisSource: EpochMillisSource,
    private val engineFactory: LiveEngineFactory,
    private val imageUrlProvider: ImageUrlProvider,
    private val audio: LivePlayerAudio? = null,
    private val transcodeReasonsRenderer: TranscodeReasonsRenderer =
        TranscodeReasonsRenderer { emptyList() },
    private val pip: PipController? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvPlayerUiState())
    val state: StateFlow<LiveTvPlayerUiState> = _state.asStateFlow()

    /**
     * One-shot screen events (record/cancel feedback, PiP-dismiss screen
     * close) on the [LivePlayerEvent] vocabulary — ONE intake replacing the
     * former messages/closePlayer member pair (the VOD `SessionEvent`
     * pattern; the VM's public-member ratchet stays at its ceiling).
     * Emitted via the engine-session shell's tryEmit-only pipe (a
     * mid-teardown emission never suspends).
     */
    val events: Flow<LivePlayerEvent> get() = engineEventShell.events

    // High-frequency DVR-window streams kept OUT of [LiveTvPlayerUiState] so
    // the 500 ms position tick invalidates only the leaf that renders it (the
    // bottom-bar seek bar), not the whole screen — mirrors the VOD player's
    // dedicated position/duration flows.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(-1L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /**
     * The live source-resolution choreography — `resolvePlayback` → the
     * DIRECT_STREAM probe-override → the fetchPlaybackInfo/getStreamUrl
     * fallback ladder (the "Mirrors the VOD PlayerSessionManager.loadOnline
     * fallback" duplicate that used to live inline in [playChannel] and
     * [onTranscodeFallback]). Owned here over this VM's [playbackRepository]
     * the way the VOD VM owns its `playerSessionManager`; this VM keeps the
     * uiState writes, the event emission and the engine load.
     */
    private val sessionManager = LiveSessionManager(playbackRepository)

    private var engine: LivePlayerEngine? = null
    private var initialized = false

    /**
     * The mute toggle's pre-mute memory (the [LiveMuteMemory] policy chip —
     * remember/restore/stale-clear semantics live there, this VM applies).
     */
    private var muteMemory = LiveMuteMemory()

    /**
     * A zap that arrived while the channel list was still loading, deferred
     * instead of dropped (gap: a zap during load silently no-oped;
     * the callers that actually hit the window are D-pad/screen zaps — the
     * PiP transport is armed only after an engine exists, and no engine
     * exists during a load window). Exactly ONE zap is retained and a
     * newer zap replaces it — user intent is the LAST direction requested.
     * Applied via [switchTo] once [loadChannelsAndPlay] commits a non-empty
     * list (identical to a zap landing after the commit, including
     * last-channel persistence); dropped when the load fails or on [stop] —
     * never retried from the zap path itself.
     */
    private var pendingZap: PendingZap? = null

    /** Direction (+1 = up / -1 = down) plus the deferred zap's stream overrides. */
    private data class PendingZap(
        val direction: Int,
        val audioStreamIndex: Int?,
        val subtitleStreamIndex: Int?,
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
     * Audio-focus (duck/restore) + becoming-noisy auto-pause seam. Shared
     * legacy `PlayerAudioLifecycle` under the androidMain actual; its
     * [LivePlayerAudio.playerVolume]-backed control re-asserts mute as
     * `volume = 0f` (the same surface [toggleMute] uses — live has no
     * `setMuted`). Live has no resume-skip, so no regain hook.
     */
    private val playerAudioLifecycle: LivePlayerAudio? = audio

    // ── Engine-event policy core (shared with the VOD player) ────────────────
    // The player-contract EngineEventCoordinator owns the engine-event
    // POLICIES (the buffering watchdog; the coordinator's decision vocabulary)
    // — raw engine flows in, EngineDecisions out. This VM keeps the
    // EXECUTION: the shell's fan-out routes each decision to
    // [executeEngineDecision], which writes uiState / re-resolves. The
    // player-contract EngineSessionShell owns the session-structural plumbing
    // both players used to hand-roll (coordinator build/re-arm/dispose, the
    // intake wiring, the decision fan-out, the one-shot event pipe); this VM
    // pins its behavior via the shell's Config knobs — previously a
    // hand-rolled copy of the VOD coordinator's policies:

    /**
     * Raw-event slice of the current engine ([EngineEventSource]) — null
     * while no engine exists. Drives the coordinator's policies; never
     * commanded through. Published in [ensureEngine], nulled in [stop].
     */
    private val engineEventSource = MutableStateFlow<EngineEventSource?>(null)

    /**
     * The mapped-state collector behind the current [engineEventSource]'s
     * `playbackState`; cancelled when the slice is re-created or the session
     * is torn down so a RELEASED engine is never retained by an orphaned
     * `stateIn` job across screen re-entries.
     */
    private var enginePlaybackMapJob: Job? = null

    /**
     * The engine-session shell: the live coordinator inside, disposed in
     * [stop] and re-armed in [ensureEngine] (only after a dispose does
     * [EngineSessionShell.reArm] actually build a fresh one). This host's
     * Config pins:
     *  - EVERY_BUFFERING_EPISODE watchdog at LIVE_BUFFERING_TIMEOUT_MS — a
     *    stalled tuner can sit in BUFFERING mid-playback without ever
     *    raising a PlaybackException, so every episode (re-)arms a fresh
     *    window (the VOD player pins initial-buffer-only instead).
     *  - EXTERNAL_REQUEST_ONLY fallback — the engine's own per-load phase
     *    machine decides WHEN a direct/direct-stream failure falls back;
     *    the coordinator converts its callback into a decision (unlatched;
     *    the engine owns the one-shot counting).
     */
    private val engineEventShell = EngineSessionShell<LivePlayerEvent>(
        scope = viewModelScope,
        engineSources = engineEventSource,
        onDecision = ::executeEngineDecision,
        config = EngineSessionShell.Config(
            coordinator = EngineEventCoordinator.Config(
                bufferingTimeoutMs = LIVE_BUFFERING_TIMEOUT_MS,
                watchdogScope = WatchdogScope.EVERY_BUFFERING_EPISODE,
                fallbackPolicy = FallbackPolicy.EXTERNAL_REQUEST_ONLY,
            ),
        ),
    )

    init {
        // Bind the audio seam before anything can create an engine (the
        // platform impl reads the engine + mute state lazily through this
        // owner, mirroring the legacy inline adapter's re-read contract).
        playerAudioLifecycle?.bind(this)

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
        // (fired below on engine END/ERROR in PiP) into notifyPipDismissed;
        // landing here means the window is showing a dead stream and the
        // screen must close. The ordering (pause → teardown → close → the
        // defensive latch clear, issue #145) lives on the shared helper —
        // this host supplies only its teardown list and its close pipe.
        viewModelScope.dischargePipDismissal(
            pip = pip,
            teardown = {
                engine?.pause()
                stop()
            },
            close = { engineEventShell.emitEvent(LivePlayerEvent.ClosePlayer) },
        )

        // (The coordinator's decision fan-out lives in [engineEventShell]: it
        // routes every EngineDecision to [executeEngineDecision] — the
        // executor the shell was constructed with — and follows the current
        // coordinator instance across stop()/re-entry re-arms.)
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
            is LiveTvPlayerUiEvent.ChannelUp -> channelUp(event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.ChannelDown -> channelDown(event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.SelectChannelById -> selectChannelById(event.channelId)
            is LiveTvPlayerUiEvent.ToggleFavorite -> toggleFavorite(event.channelId)
            is LiveTvPlayerUiEvent.RecordCurrentProgramOnce -> recordCurrentProgramOnce()
            is LiveTvPlayerUiEvent.RecordCurrentProgramSeries -> recordCurrentProgramSeries()
            is LiveTvPlayerUiEvent.CancelCurrentProgramTimer -> cancelCurrentProgramTimer()
            is LiveTvPlayerUiEvent.CancelCurrentProgramSeries -> cancelCurrentProgramSeries()
            is LiveTvPlayerUiEvent.TogglePlayPause -> togglePlayPause()
            is LiveTvPlayerUiEvent.SeekToLiveEdge -> seekToLiveEdge()
            is LiveTvPlayerUiEvent.SeekWithinDvr -> seekWithinDvr(event.positionMs)
            is LiveTvPlayerUiEvent.PlayFromStart -> playFromStart()
            is LiveTvPlayerUiEvent.RefreshPosition -> refreshPosition()
            is LiveTvPlayerUiEvent.ToggleMute -> toggleMute()
            is LiveTvPlayerUiEvent.Retry -> retry(event.audioStreamIndex, event.subtitleStreamIndex)
            is LiveTvPlayerUiEvent.SetLiveStreamOption -> setLiveStreamOption(event.option)
        }
    }

    /**
     * Entry point invoked from the screen's `LaunchedEffect(channelId)`.
     * Loads the channel list, selects the start channel (preferring the
     * route's id — i.e. the channel the user actually tapped — then the
     * last-watched id, then the first channel), and starts playback.
     * Idempotent — subsequent calls with the same id are no-ops so
     * recomposition doesn't restart playback.
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
        if (initialized) return
        initialized = true
        viewModelScope.launch { loadChannelsAndPlay(channelId, audioStreamIndex, subtitleStreamIndex) }
    }

    private suspend fun loadChannelsAndPlay(
        routeChannelId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        _state.value = _state.value.copy(isLoadingChannels = true)
        val channels = liveTvRepository.getLiveTvChannels(limit = CHANNEL_LIST_LIMIT)
            .getOrNull().orEmpty()
        if (channels.isEmpty()) {
            // Load failed (or returned nothing): a zap queued during the
            // load is dropped — applying it against a missing list is
            // meaningless, and the zap path never retries the load.
            pendingZap = null
            _state.value = _state.value.copy(
                isLoadingChannels = false,
                isBuffering = false,
                errorMessage = LivePlayerMessage.Resource(Res.string.live_error_no_channels),
            )
            return
        }

        // Selection priority: the channel the user tapped (route id) wins.
        // The last-watched id is only a fallback when no explicit channel was
        // requested, and the first channel is the last resort so we never
        // silently play the wrong channel when the tapped one is missing.
        val storedId = lastChannelStore.observeLastChannelId().first()
        val targetId = channels.firstOrNull { it.id == routeChannelId }?.id
            ?: channels.firstOrNull { it.id == storedId }?.id
            ?: channels.first().id
        val index = channels.indexOfFirst { it.id == targetId }.coerceAtLeast(0)

        _state.value = _state.value.copy(
            isLoadingChannels = false,
            channels = channels,
            currentIndex = index,
            currentChannel = channels[index],
        )
        // A zap that arrived while this list was loading applies now —
        // through the same switchTo a post-load zap takes (so last-channel
        // persistence and switching chrome behave identically). Consumed
        // exactly once; a later zap while the list is committed goes the
        // direct channelUp/channelDown route.
        val deferredZap = pendingZap
        pendingZap = null
        if (deferredZap != null) {
            val zapped = (index + deferredZap.direction + channels.size) % channels.size
            switchTo(zapped, deferredZap.audioStreamIndex, deferredZap.subtitleStreamIndex)
        } else {
            playChannel(channels[index], audioStreamIndex, subtitleStreamIndex)
        }
    }

    private fun channelUp(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
        val channels = _state.value.channels
        if (channels.isEmpty()) {
            // List still loading → defer the zap; it applies once the list
            // commits. Otherwise (load failed / never initialized) the silent
            // no-op stands — a zap must not retry a failed load.
            if (_state.value.isLoadingChannels) {
                pendingZap = PendingZap(+1, audioStreamIndex, subtitleStreamIndex)
            }
            return
        }
        val next = (_state.value.currentIndex + 1) % channels.size
        switchTo(next, audioStreamIndex, subtitleStreamIndex)
    }

    private fun channelDown(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
        val channels = _state.value.channels
        if (channels.isEmpty()) {
            // See channelUp: defer while loading, no-op otherwise.
            if (_state.value.isLoadingChannels) {
                pendingZap = PendingZap(-1, audioStreamIndex, subtitleStreamIndex)
            }
            return
        }
        val prev = (_state.value.currentIndex - 1 + channels.size) % channels.size
        switchTo(prev, audioStreamIndex, subtitleStreamIndex)
    }

    /**
     * Tunes the channel whose id matches [channelId]. No-op if the id is not
     * in the current channel list. Used by the in-player channel list sheet.
     */
    private fun selectChannelById(channelId: String) {
        val channels = _state.value.channels
        val index = channels.indexOfFirst { it.id == channelId }
        if (index !in channels.indices) return
        switchTo(index, audioStreamIndex = null, subtitleStreamIndex = null)
    }

    /**
     * Adds/removes [channelId] from the user's favorite channels. Persists
     * via [UserPreferencesStore.setFavoriteChannels]; the `init` observer
     * propagates the change into [_state].
     */
    private fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            val current = appRuntimeStateStore.state.first().favoriteChannels
            val updated = if (channelId in current) current - channelId else current + channelId
            appRuntimeStateStore.setFavoriteChannels(updated)
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
                engineEventShell.emitEvent(LivePlayerEvent.Message(outcome.request.action.successMessage()))
                viewModelScope.launch { refreshProgramsForCurrentChannel() }
            }
            is RecordOutcome.Error ->
                engineEventShell.emitEvent(
                    LivePlayerEvent.Message(
                        LivePlayerMessage.Raw(outcome.message ?: outcome.request.action.failureFallback())
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

    private fun switchTo(
        index: Int,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        val channels = _state.value.channels
        if (index !in channels.indices) return
        _state.value = _state.value.copy(
            currentIndex = index,
            currentChannel = channels[index],
            currentProgram = null,
            nextProgram = null,
            isSwitchingChannel = true,
        )
        viewModelScope.launch {
            playChannel(channels[index], audioStreamIndex, subtitleStreamIndex)
            lastChannelStore.setLastChannelId(channels[index].id)
        }
    }

    /**
     * Resolves a playable live URL for [channel] and starts playback.
     *
     * The resolution choreography (the full decision tree, the DIRECT_STREAM
     * probe-override and the fetchPlaybackInfo/getStreamUrl fallback ladder —
     * end-to-end semantics including the mandatory AUTO mode and the blank
     * mediaSourceId live on [LiveSessionManager.resolve]). This body keeps
     * the tune choreography: surface the resolution failure, log the resolved
     * pick, mirror the play method into the chrome, load the engine, arm PiP
     * and refresh the program window.
     */
    private suspend fun playChannel(
        channel: LiveTvChannel,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        val playback = playbackStore.playback.first()
        val resolved = sessionManager.resolve(
            channel = channel,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            option = playback.liveStreamOption,
            playerType = playback.preferredPlayer,
        ) ?: run {
            _state.value = _state.value.copy(
                isBuffering = false,
                isSwitchingChannel = false,
                errorMessage = LivePlayerMessage.Resource(
                    Res.string.live_error_resolve_failed, listOf(channel.name)
                ),
            )
            return
        }

        Log.i(
            TAG,
            "Playing ${channel.name}: option=${playback.liveStreamOption}, " +
                "player=${playback.preferredPlayer}, method=${resolved.playMethod}, " +
                "url=${resolved.streamUrl}"
        )

        // Auth token flows via LiveEngineConfig.authToken (read by ExoLiveEngine's
        // HTTP data-source factory), not per-request — see ensureEngine.
        val livePlayMethod = resolved.playMethod.toLivePlayMethod()
        _state.value = _state.value.copy(playMethod = livePlayMethod)
        refreshTranscodeReasons(channel.id, livePlayMethod)
        ensureEngine()
            .load(
                LivePlaybackRequest(
                    url = resolved.streamUrl,
                    title = channel.name,
                    playMethod = livePlayMethod,
                    container = resolved.container,
                )
            )
        // PiP auto-entry arms on every successful tune (init, zap, retry,
        // stream-option reload all funnel through here) — mirrors the VOD
        // session's per-load arm. The Activity additionally gates entry on
        // isPlaying + unlocked controls, so an armed-but-paused live stream
        // never yanks the user into PiP.
        pip?.requestAutoEnterPip(true)

        _state.value = _state.value.copy(isSwitchingChannel = false)
        loadPrograms(channel.id)
    }

    private fun ensureEngine(): LivePlayerEngine {
        val existing = engine
        if (existing != null) return existing
        val config = LiveEngineConfig(
            authToken = playbackIdentity.accessToken(),
        )
        val newEngine = engineFactory.create(config, ::onEngineFallbackRequested)
        observeEngine(newEngine)
        engine = newEngine
        // Arm the engine-event policy core (shared with the VOD player's
        // PlaybackSession) BEFORE publishing the engine's raw-event slice, so
        // no policy window is missed between engine creation and collection.
        // The shell builds its coordinator eagerly over [engineEventSource];
        // this re-arm is a no-op until [stop] disposed it — the same
        // re-arm-on-entry shape the VOD session applies per initialize.
        // This host's pins (EVERY_BUFFERING_EPISODE + EXTERNAL_REQUEST_ONLY)
        // live on the shell's Config at the declaration site.
        engineEventShell.reArm()
        engineEventSource.value = newEngine.toEngineEventSource()
        // Install becoming-noisy + audio-focus only once for the (reused)
        // engine instance, mirroring the VOD player. They persist across
        // channel switches and are torn down in [stop].
        playerAudioLifecycle?.onEngineCreated()
        // Re-arm the PiP transport with every engine creation: [stop] nulls
        // it via PipController.reset() — the re-arm lifecycle rationale lives
        // on [reArmPipTransport].
        registerPipTransport()
        return newEngine
    }

    private fun observeEngine(eng: LivePlayerEngine) {
        eng.state.onEach { s ->
            // On engine errors during a transcoded stream, append the
            // plain-language transcode reasons to the expandable detail so
            // the error overlay answers "why was this transcoding at all".
            val engineDetail = if (s == LiveEngineState.ERROR) eng.errorDetail.value else null
            val reasonsBlock = if (
                s == LiveEngineState.ERROR &&
                _state.value.playMethod == LivePlayMethod.TRANSCODE &&
                _state.value.transcodeReasons.isNotEmpty()
            ) {
                transcodeReasonsRenderer.render(_state.value.transcodeReasons)
                    .joinToString("\n")
            } else {
                null
            }
            val combinedDetail = listOfNotNull(engineDetail, reasonsBlock)
                .joinToString("\n\n")
                .ifBlank { null }
            _state.value = _state.value.copy(
                engineState = s,
                isBuffering = s == LiveEngineState.BUFFERING || s == LiveEngineState.IDLE,
                // The engine reports raw error strings; a null message (no
                // localizedMessage on the PlaybackException) falls back to
                // the generic playback-error string, resolved at render time.
                errorMessage = if (s == LiveEngineState.ERROR) {
                    eng.errorMessage.value?.let(LivePlayerMessage::Raw)
                        ?: LivePlayerMessage.Resource(Res.string.live_error_playback_fallback)
                } else {
                    null
                },
                errorDetail = combinedDetail,
            )
            // Auto-exit PiP on engine END/ERROR so the floating window does not
            // linger on a dead stream; the Activity's collector translates this
            // into the existing dismiss path (pause + finish). Mirrors the VOD
            // coordinator's playbackState policy.
            if (
                (s == LiveEngineState.ERROR || s == LiveEngineState.ENDED) &&
                pip?.isInPipMode?.value == true
            ) {
                pip?.requestAutoExitPip()
            }
            // (The buffering watchdog arm/cancel that used to live here moved
            // to the shared EngineEventCoordinator — its every-episode scope
            // re-arms on BUFFERING and its timeout decision lands in
            // [executeEngineDecision].)
        }.launchIn(viewModelScope)
        eng.isPlaying.onEach {
            _state.value = _state.value.copy(isPlaying = it)
            // Mirror play state so the host Activity renders the correct
            // play/pause icon on the PiP window.
            pip?.setPlaying(it)
        }.launchIn(viewModelScope)
        eng.isAtLiveEdge.onEach { _state.value = _state.value.copy(isAtLiveEdge = it) }
            .launchIn(viewModelScope)
        eng.positionMs.onEach { _positionMs.value = it }
            .launchIn(viewModelScope)
        eng.durationMs.onEach { _durationMs.value = it }
            .launchIn(viewModelScope)
    }

    /** Owns the in-flight transcode-reason lookup; cancelled/replaced per tune. */
    private val transcodeReasonsRefresher =
        TranscodeReasonsRefresher(viewModelScope, playbackRepository::fetchActiveTranscodeReasons)

    /**
     * Populates [LiveTvPlayerUiState.transcodeReasons] from the server's
     * live session (`TranscodingInfo`) when tuning landed on a transcode,
     * and clears it otherwise. Mirrors PlayerSessionManager's VOD refresh
     * via the shared [TranscodeReasonsRefresher]: wait for the session
     * to register, retry once, drop silently on a miss.
     */
    private fun refreshTranscodeReasons(channelId: String, method: LivePlayMethod) {
        transcodeReasonsRefresher.refresh(
            channelId,
            isTranscode = method == LivePlayMethod.TRANSCODE,
            isCurrent = { _state.value.currentChannel?.id == channelId },
            clear = { _state.value = _state.value.copy(transcodeReasons = emptyList()) },
            onReasons = { reasons ->
                _state.value = _state.value.copy(transcodeReasons = reasons)
            },
        )
    }

    /**
     * Executes one [EngineDecision] from the shared coordinator — what a
     * decision *does* (uiState writes, the transcode re-resolve/reload). The
     * live engine carries no `EngineError` flow and no subtitle events, so
     * the watchdog timeout is the only decision this host receives today.
     */
    private fun executeEngineDecision(decision: EngineDecision) {
        when (decision) {
            is EngineDecision.ShowError -> {
                // The ONLY ShowError the coordinator can emit here is the
                // buffering-watchdog timeout (clearBuffering = true — the VOD
                // error-dialog path rides the engine error flow live doesn't
                // have): lift the stuck rebuffer spinner and surface the
                // retryable timeout error.
                if (decision.clearBuffering) {
                    _state.value = _state.value.copy(
                        isBuffering = false,
                        errorMessage = LivePlayerMessage.Resource(
                            Res.string.live_error_buffering_timeout
                        ),
                    )
                }
            }
            is EngineDecision.FallbackToTranscode ->
                // The engine's phase machine latched the direct/direct-stream
                // failure and requested the re-resolve; execution unchanged.
                onTranscodeFallback()
            // ENDED handling (the engineState/isBuffering writes and the PiP
            // auto-exit shared with ERROR) stays in observeEngine's state
            // collector — the coordinator's ENDED decision is redundant here.
            EngineDecision.PlaybackEnded -> Unit
            // Pass-out protection is a VOD preference; live arms no hours
            // budget, so the poller is disabled and this never fires.
            EngineDecision.PassOutPause -> Unit
            // The live engine produces no subtitle events.
            is EngineDecision.InformUser -> Unit
        }
    }

    /**
     * The engine's raw-event slice feeding the coordinator (candidate C4): a
     * per-tune fresh [EngineEventSource] over this engine's flows. The tuner
     * engine has no `EngineError` channel (errors surface as an ERROR state +
     * message/detail flows, handled in [observeEngine]) and no subtitle
     * events — the watchdog is the only policy those defaults leave armed.
     */
    private fun LivePlayerEngine.toEngineEventSource(): EngineEventSource {
        // Cancel the previous engine's mapped-state collector first: the
        // released engine must not be retained by an orphaned collector
        // across screen re-entries (the activity-scoped VM outlives engines).
        enginePlaybackMapJob?.cancel()
        val mapped = MutableStateFlow(EnginePlaybackState.IDLE)
        enginePlaybackMapJob = viewModelScope.launch {
            state.map { it.toEnginePlaybackState() }.collect { mapped.value = it }
        }
        return EngineEventSource(isPlaying = isPlaying, playbackState = mapped)
    }

    /**
     * Positional one-to-one map onto the shared engine-state vocabulary — an
     * exhaustive `when` so a state added to either enum breaks this site at
     * compile time instead of silently mis-mapping.
     */
    private fun LiveEngineState.toEnginePlaybackState(): EnginePlaybackState = when (this) {
        LiveEngineState.IDLE -> EnginePlaybackState.IDLE
        LiveEngineState.BUFFERING -> EnginePlaybackState.BUFFERING
        LiveEngineState.READY -> EnginePlaybackState.READY
        LiveEngineState.ENDED -> EnginePlaybackState.ENDED
        LiveEngineState.ERROR -> EnginePlaybackState.ERROR
    }

    /**
     * Engine-side fallback trigger (installed via [LiveEngineFactory]): the
     * engine's per-load phase machine latched a direct/direct-stream failure
     * and requests the transcode re-resolve. Routed through the shared
     * coordinator's intake so the request becomes a normal
     * [EngineDecision.FallbackToTranscode] — one decision intake, the same
     * shape the VOD player's errors take — executed by
     * [executeEngineDecision] → [onTranscodeFallback].
     */
    private fun onEngineFallbackRequested() {
        engineEventShell.onTranscodeFallbackRequested()
    }

    /**
     * Executes the transcode fallback: re-resolves via
     * [LiveSessionManager.resolve] with [LiveStreamOption.TRANSCODE] so the
     * server hands back a transcoding URL, and reloads the engine
     * (`onPlayerError` path).
     */
    private fun onTranscodeFallback() {
        val channel = _state.value.currentChannel ?: return
        viewModelScope.launch {
            val playback = playbackStore.playback.first()
            val resolved = sessionManager.resolve(
                channel = channel,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                option = LiveStreamOption.TRANSCODE,
                playerType = playback.preferredPlayer,
            ) ?: run {
                // The failed tune was direct/direct-stream, so there are no
                // server transcode reasons yet. The engine stayed in BUFFERING
                // to avoid flashing the error overlay mid-fallback, which also
                // kept observeEngine from mirroring its captured error —
                // surface that originating error here so the banner answers
                // "why did this tune fail" (the client forced the fallback).
                _state.value = _state.value.copy(
                    isBuffering = false,
                    errorMessage = LivePlayerMessage.Resource(
                        Res.string.live_error_transcode_fallback, listOf(channel.name)
                    ),
                    errorDetail = engine?.errorDetail?.value,
                )
                return@launch
            }
            // Reflect the method change in the chrome badge before reloading.
            _state.value = _state.value.copy(playMethod = LivePlayMethod.TRANSCODE)
            refreshTranscodeReasons(channel.id, LivePlayMethod.TRANSCODE)
            engine?.load(
                LivePlaybackRequest(
                    url = resolved.streamUrl,
                    title = channel.name,
                    playMethod = LivePlayMethod.TRANSCODE,
                    container = resolved.container,
                )
            )
        }
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

    private fun togglePlayPause() {
        engine?.let { if (it.isPlaying.value) it.pause() else it.play() }
    }

    private fun seekToLiveEdge() {
        engine?.seekToLiveEdge()
    }

    private fun seekWithinDvr(positionMs: Long) {
        engine?.seekTo(positionMs)
    }

    /**
     * Restarts the current program from its beginning.
     * Seeks to the start of the DVR window (position 0); only meaningful when
     * the server exposes a timeshift buffer (`durationMs > 0`). On pure-live
     * streams with no DVR window there is no "start" to return to, so this is
     * a no-op — the UI gates the action on `canSeek`.
     */
    private fun playFromStart() {
        // Guard: only restart when a DVR window exists. Mirrors the seek-bar
        // gate (LiveSeekBar returns early when durationMs <= 0).
        if (_durationMs.value <= 0L) return
        engine?.seekTo(0L)
    }

    /** Polled by the screen every 500ms while playing to refresh seek-bar state. */
    private fun refreshPosition() {
        engine?.refreshLiveWindow()
    }

    /**
     * Toggles mute on the underlying platform player. Preserves the pre-mute
     * volume so unmute restores it (per project convention). No-op if there
     * is no audio seam / attached platform player (e.g. a future non-Exo
     * engine, or a platform without one).
     */
    private fun toggleMute() {
        val audio = playerAudioLifecycle ?: return
        val currentVolume = audio.playerVolume() ?: return
        if (_state.value.isMuted) {
            // Restore the pre-mute level captured when muting; never slam to a
            // fixed default. Null (e.g. mute set externally, or player swapped)
            // means leave the current volume untouched.
            muteMemory.restorationVolume()?.let(audio::setPlayerVolume)
            muteMemory = muteMemory.onUnmute()
            _state.value = _state.value.copy(isMuted = false)
        } else {
            // Capture the raw player volume so unmute restores it exactly.
            muteMemory = muteMemory.onMute(currentVolume)
            audio.setPlayerVolume(0f)
            _state.value = _state.value.copy(isMuted = true)
        }
    }

    private fun retry(
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        val channel = _state.value.currentChannel ?: return
        viewModelScope.launch { playChannel(channel, audioStreamIndex, subtitleStreamIndex) }
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
            playChannel(
                channel = channel,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
    }

    /**
     * Arms the PiP transport bridge so the host Activity can dispatch PiP
     * remote-action intents to the live engine. The null-controller guard and
     * assignment mechanics (plus the Activity-scoped-VM re-arm rationale)
     * live in [reArmPipTransport]; this body owns only the live mapping:
     * PLAY/PAUSE hit the engine directly (no SyncPlay/cast routing exists on
     * live); the window's rewind/forward SKIP actions zap channel-down/up
     * (the live-TV PiP convention — a DVR micro-seek is meaningless on
     * pure-live streams, and [seekWithinDvr] is already a no-op there),
     * re-resolving with the route's preferred stream overrides; NEXT stays
     * unmapped (live has no "next episode", so pipHasNext is never set and
     * the Activity never renders that action).
     */
    private fun registerPipTransport() {
        reArmPipTransport(pip) { action ->
            when (action) {
                PipAction.PLAY -> engine?.play()
                PipAction.PAUSE -> engine?.pause()
                PipAction.SKIP_FORWARD ->
                    channelUp(routeAudioStreamIndex, routeSubtitleStreamIndex)
                PipAction.SKIP_BACKWARD ->
                    channelDown(routeAudioStreamIndex, routeSubtitleStreamIndex)
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
    fun engineForRendering(): LivePlayerEngine? = engine

    /** Channel logo URL for the chrome/zap toast; null when no image tag. */
    fun logoUrlFor(channel: LiveTvChannel): String? =
        if (!channel.imageTag.isNullOrBlank()) imageUrlProvider.getImageUrl(channel.id) else null

    /**
     * Releases the live engine and resets playback state. Called from
     * [LivePlayerScreen]'s `onDispose` so that leaving the screen — including
     * a nav-back — tears down the ExoPlayer immediately instead of letting
     * audio keep playing in the background until activity destroy.
     *
     * The live VM is activity-scoped (nav3 entries don't install a per-entry
     * ViewModelStore owner here), so [onCleared] alone only fires on process
     * / activity teardown — far too late for a back press. Resetting
     * `initialized` lets the screen re-init playback cleanly if the user
     * returns to the same channel.
     */
    fun stop() {
        // Tear down audio-focus + becoming-noisy before releasing the engine so
        // the listeners never dereference a torn-down player (idempotent).
        playerAudioLifecycle?.onReleased()
        // Tear down the engine-event shell BEFORE releasing the engine so no
        // policy collector (the buffering watchdog included) observes a
        // released engine — the same order the VOD session's release applies.
        engineEventShell.dispose()
        enginePlaybackMapJob?.cancel()
        enginePlaybackMapJob = null
        engineEventSource.value = null
        engine?.release()
        engine = null
        initialized = false
        // Drop any zap deferred during an in-flight load — it belongs to the
        // session being torn down and must not fire on the next entry's load.
        pendingZap = null
        // Clear the captured pre-mute volume so a stale value from the previous
        // player is never restored on a later unmute (e.g. mute → leave screen →
        // return to a fresh engine). isMuted is reset via the fresh uiState below.
        muteMemory = muteMemory.onStopped()
        // Full PiP teardown: nulls the transport, disarms auto-enter and drops
        // the aspect/playing mirrors so a stale armed flag can't float the next
        // screen's window into PiP. The transport re-arms in [ensureEngine] on
        // the next entry.
        pip?.reset()
        _positionMs.value = 0L
        _durationMs.value = -1L
        _state.value = LiveTvPlayerUiState()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun PlayMethod.toLivePlayMethod(): LivePlayMethod = when (this) {
        PlayMethod.DIRECT_PLAY -> LivePlayMethod.DIRECT_PLAY
        PlayMethod.DIRECT_STREAM -> LivePlayMethod.DIRECT_STREAM
        PlayMethod.TRANSCODE -> LivePlayMethod.TRANSCODE
    }
}

/** Timer creations announce success; cancels announce cancellation. */
private fun RecordAction.startsTimer(): Boolean =
    this == RecordAction.RECORD_ONCE || this == RecordAction.RECORD_SERIES

private fun RecordAction.successMessage(): LivePlayerMessage =
    if (startsTimer()) {
        LivePlayerMessage.Resource(Res.string.live_record_success)
    } else {
        LivePlayerMessage.Resource(Res.string.live_record_canceled)
    }

/** The failure fallback literals, kept byte-identical from the legacy inline arms. */
private fun RecordAction.failureFallback(): String =
    if (startsTimer()) "Failed to set recording" else "Failed to cancel recording"
