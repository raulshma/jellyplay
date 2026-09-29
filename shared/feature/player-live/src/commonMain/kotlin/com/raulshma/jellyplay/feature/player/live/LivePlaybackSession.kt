package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.TranscodeReasonsRefresher
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineConfig
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_playback_fallback
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_resolve_failed
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_transcode_fallback
import com.raulshma.jellyplay.feature.player.video.engine.EngineDecision
import com.raulshma.jellyplay.feature.player.video.engine.EngineEventCoordinator
import com.raulshma.jellyplay.feature.player.video.engine.EngineEventSource
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineSessionShell
import com.raulshma.jellyplay.feature.player.video.engine.FallbackPolicy
import com.raulshma.jellyplay.feature.player.video.engine.WatchdogScope
import com.raulshma.jellyplay.feature.player.video.engine.mirrorPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.ui.message.UiMessage

private const val TAG = "LivePlaybackSession"

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
 * The live playback session — the EXECUTOR half of the former
 * [LiveTvPlayerViewModel] god-VM, the VOD `PlaybackSession` shape at live
 * scale: it owns the tune / zap / retry / transcode-fallback choreography,
 * the engine and its engine-event policy shell, and the deferred-zap
 * machine, and reports every UI-relevant outcome upward as a
 * [LivePlaybackEvent] on [events] that the ViewModel folds into its UI
 * state. The ViewModel keeps the folding itself plus the PiP wiring, the
 * platform seams (audio-focus/mute, rendering, EPG program scan, record
 * actions) — exactly the split the VOD pair (`PlaybackSession` /
 * `VideoPlayerViewModel`) already has: source resolution lives one level
 * deeper in [LiveSessionManager], so the session is the middle layer between
 * resolution and UI folding.
 *
 * Invariants this class owns:
 *  - ONE [LivePlayerEngine] per screen entry, created lazily in
 *    [ensureEngine], reused across channel switches, released in [release];
 *  - the engine-event shell's lifecycle: re-armed only after [release]
 *    disposed it (no policy collector ever observes a released engine);
 *  - the channel-list commit + deferred-zap single-flight (see [pendingZap])
 *    and last-watched persistence on every successful zap;
 *  - the transcode-reasons refresh cadence per tune ([transcodeReasonsRefresher]);
 *  - [events] is the ONLY channel upward — the session never touches UI
 *    state directly (it holds private mirrors of `channels` / index /
 *    current channel / play method / transcode reasons purely to make its
 *    own decisions and to build the merged error detail it emits).
 *
 * Internal (the LiveSessionManager / LiveMuteMemory seam shape): consumed
 * only by the ViewModel and this module's jvmTest.
 */
internal class LivePlaybackSession(
    /** The owning ViewModel's scope — every launch here dies with the VM. */
    private val scope: CoroutineScope,
    private val liveTvRepository: LiveTvRepository,
    private val sessionManager: LiveSessionManager,
    private val playbackStore: PlaybackStore,
    private val playbackIdentity: PlaybackIdentity,
    private val engineFactory: LiveEngineFactory,
    private val lastChannelStore: LastChannelStore,
    private val transcodeReasonsRenderer: TranscodeReasonsRenderer,
    playbackRepository: PlaybackRepository,
) {

    // ── Upward event pipe ─────────────────────────────────────────────────────

    /**
     * Raw-event slice of the current engine ([EngineEventSource]) — null
     * while no engine exists. Drives the coordinator's policies; never
     * commanded through. Published in [ensureEngine], nulled in [release].
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
     * [release] and re-armed in [ensureEngine] (only after a dispose does
     * [EngineSessionShell.reArm] actually build a fresh one). This host's
     * Config pins:
     *  - EVERY_BUFFERING_EPISODE watchdog at LIVE_BUFFERING_TIMEOUT_MS — a
     *    stalled tuner can sit in BUFFERING mid-playback without ever
     *    raising a PlaybackException, so every episode (re-)arms a fresh
     *    window (the VOD player pins initial-buffer-only instead);
     *  - EXTERNAL_REQUEST_ONLY fallback — the engine's own per-load phase
     *    machine decides WHEN a direct/direct-stream failure falls back;
     *    the coordinator converts its callback into a decision (unlatched;
     *    the engine owns the one-shot counting).
     */
    private val engineEventShell = EngineSessionShell<LivePlaybackEvent>(
        scope = scope,
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

    /**
     * The session's outcomes, in order — the fold the ViewModel applies to
     * its UI state (plus the PiP wiring it triggers). Rides the shell's
     * one-shot pipe: `tryEmit`-only, so a mid-teardown emission never
     * suspends.
     */
    val events: SharedFlow<LivePlaybackEvent> get() = engineEventShell.events

    // High-frequency DVR-window streams kept OUT of any event vocabulary —
    // the 500 ms position tick must not fan through the event pipe; the
    // ViewModel re-exposes them as its dedicated leaf flows (the same
    // out-of-UiState rule the VOD player applies to position/duration).
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(-1L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // ── Private session mirrors (decision inputs; never UI state) ─────────────

    private var engine: LivePlayerEngine? = null
    private var initialized = false

    /** The committed channel list + cursor the zap machine steps over. */
    private var channels: List<LiveTvChannel> = emptyList()
    private var channelIndex = 0
    private var loadingChannels = false

    /** Delivery method of the current tune — feeds the error-detail merge. */
    private var playMethod: LivePlayMethod? = null

    /** Server-reported transcode reasons of the current tune (same consumer). */
    private var transcodeReasons: List<String> = emptyList()

    /**
     * A zap that arrived while the channel list was still loading, deferred
     * instead of dropped (gap: a zap during load silently no-oped;
     * the callers that actually hit the window are D-pad/screen zaps — the
     * PiP transport is armed only after an engine exists, and no engine
     * exists during a load window). Exactly ONE zap is retained and a
     * newer zap replaces it — user intent is the LAST direction requested.
     * Applied via [switchTo] once [loadChannelsAndPlay] commits a non-empty
     * list (identical to a zap landing after the commit, including
     * last-channel persistence); dropped when the load fails or on [release]
     * — never retried from the zap path itself.
     */
    private var pendingZap: PendingZap? = null

    /** Direction (+1 = up / -1 = down) plus the deferred zap's stream overrides. */
    private data class PendingZap(
        val direction: Int,
        val audioStreamIndex: Int?,
        val subtitleStreamIndex: Int?,
    )

    /** Owns the in-flight transcode-reason lookup; cancelled/replaced per tune. */
    private val transcodeReasonsRefresher =
        TranscodeReasonsRefresher(scope, playbackRepository::fetchActiveTranscodeReasons)

    // ── Entry + channel list ──────────────────────────────────────────────────

    /**
     * The load entry (the ViewModel's initialize funnel after its PiP-latch
     * clears and route-override capture): idempotent — the `initialized`
     * latch means recompositions re-firing the route cannot restart
     * playback.
     */
    fun initialize(
        routeChannelId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        if (initialized) return
        initialized = true
        scope.launch { loadChannelsAndPlay(routeChannelId, audioStreamIndex, subtitleStreamIndex) }
    }

    private suspend fun loadChannelsAndPlay(
        routeChannelId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        loadingChannels = true
        emit(LivePlaybackEvent.ChannelLoadStarted)
        val loaded = liveTvRepository.getLiveTvChannels(limit = CHANNEL_LIST_LIMIT)
            .getOrNull().orEmpty()
        if (loaded.isEmpty()) {
            // Load failed (or returned nothing): a zap queued during the
            // load is dropped — applying it against a missing list is
            // meaningless, and the zap path never retries the load.
            pendingZap = null
            loadingChannels = false
            emit(LivePlaybackEvent.ChannelLoadFailed)
            return
        }

        // Selection priority: the channel the user tapped (route id) wins.
        // The last-watched id is only a fallback when no explicit channel was
        // requested, and the first channel is the last resort so we never
        // silently play the wrong channel when the tapped one is missing.
        val storedId = lastChannelStore.observeLastChannelId().first()
        val targetId = loaded.firstOrNull { it.id == routeChannelId }?.id
            ?: loaded.firstOrNull { it.id == storedId }?.id
            ?: loaded.first().id
        val index = loaded.indexOfFirst { it.id == targetId }.coerceAtLeast(0)

        channels = loaded
        channelIndex = index
        loadingChannels = false
        emit(LivePlaybackEvent.ChannelsCommitted(loaded, index, loaded[index]))
        // A zap that arrived while this list was loading applies now —
        // through the same switchTo a post-load zap takes (so last-channel
        // persistence and switching chrome behave identically). Consumed
        // exactly once; a later zap while the list is committed goes the
        // direct channelUp/channelDown route.
        val deferredZap = pendingZap
        pendingZap = null
        if (deferredZap != null) {
            val zapped = (index + deferredZap.direction + loaded.size) % loaded.size
            switchTo(zapped, deferredZap.audioStreamIndex, deferredZap.subtitleStreamIndex)
        } else {
            playChannel(loaded[index], audioStreamIndex, subtitleStreamIndex)
        }
    }

    fun channelUp(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
        if (channels.isEmpty()) {
            // List still loading → defer the zap; it applies once the list
            // commits. Otherwise (load failed / never initialized) the silent
            // no-op stands — a zap must not retry a failed load.
            if (loadingChannels) {
                pendingZap = PendingZap(+1, audioStreamIndex, subtitleStreamIndex)
            }
            return
        }
        val next = (channelIndex + 1) % channels.size
        switchTo(next, audioStreamIndex, subtitleStreamIndex)
    }

    fun channelDown(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
        if (channels.isEmpty()) {
            // See channelUp: defer while loading, no-op otherwise.
            if (loadingChannels) {
                pendingZap = PendingZap(-1, audioStreamIndex, subtitleStreamIndex)
            }
            return
        }
        val prev = (channelIndex - 1 + channels.size) % channels.size
        switchTo(prev, audioStreamIndex, subtitleStreamIndex)
    }

    /**
     * Tunes the channel whose id matches [channelId]. No-op if the id is not
     * in the current channel list. Used by the in-player channel list sheet.
     */
    fun selectChannelById(channelId: String) {
        val index = channels.indexOfFirst { it.id == channelId }
        if (index !in channels.indices) return
        switchTo(index, audioStreamIndex = null, subtitleStreamIndex = null)
    }

    private fun switchTo(
        index: Int,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        if (index !in channels.indices) return
        channelIndex = index
        emit(LivePlaybackEvent.ChannelSelected(index, channels[index]))
        scope.launch {
            playChannel(channels[index], audioStreamIndex, subtitleStreamIndex)
            lastChannelStore.setLastChannelId(channels[index].id)
        }
    }

    // ── Tune / retry ──────────────────────────────────────────────────────────

    /**
     * Resolves a playable live URL for [channel] and starts playback.
     *
     * The resolution choreography (the full decision tree, the DIRECT_STREAM
     * probe-override and the fetchPlaybackInfo/getStreamUrl fallback ladder —
     * end-to-end semantics including the mandatory AUTO mode and the blank
     * mediaSourceId live on [LiveSessionManager.resolve]). This body keeps
     * the tune choreography: surface the resolution failure, log the resolved
     * pick, mirror the play method into the chrome, load the engine, arm PiP
     * and refresh the program window (the last two as [LivePlaybackEvent.TuneStarted]
     * folds — ViewModel side).
     */
    suspend fun playChannel(
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
            emit(
                LivePlaybackEvent.TuneFailed(
                    UiMessage.Resource(Res.string.live_error_resolve_failed, listOf(channel.name))
                )
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
        playMethod = livePlayMethod
        emit(LivePlaybackEvent.PlayMethodChanged(livePlayMethod))
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
        emit(LivePlaybackEvent.TuneStarted(channel))
    }

    fun retry(
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        val channel = channels.getOrNull(channelIndex) ?: return
        scope.launch { playChannel(channel, audioStreamIndex, subtitleStreamIndex) }
    }

    // ── Engine + policy shell ─────────────────────────────────────────────────

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
        // this re-arm is a no-op until [release] disposed it — the same
        // re-arm-on-entry shape the VOD session applies per initialize.
        // This host's pins (EVERY_BUFFERING_EPISODE + EXTERNAL_REQUEST_ONLY)
        // live on the shell's Config at the declaration site.
        engineEventShell.reArm()
        engineEventSource.value = newEngine.toEngineEventSource()
        // EngineCreated is the ViewModel's cue to install the platform seams
        // that must ride engine creation: the audio-focus/becoming-noisy
        // listeners (once per engine instance, torn down in its stop) and
        // the PiP transport re-arm ([release] nulls it via PipController.reset()).
        emit(LivePlaybackEvent.EngineCreated)
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
                playMethod == LivePlayMethod.TRANSCODE &&
                transcodeReasons.isNotEmpty()
            ) {
                transcodeReasonsRenderer.render(transcodeReasons)
                    .joinToString("\n")
            } else {
                null
            }
            val combinedDetail = listOfNotNull(engineDetail, reasonsBlock)
                .joinToString("\n\n")
                .ifBlank { null }
            // The engine reports raw error strings; a null message (no
            // localizedMessage on the PlaybackException) falls back to
            // the generic playback-error string, resolved at render time.
            val errorMessage = if (s == LiveEngineState.ERROR) {
                UiMessage.of(eng.errorMessage.value, Res.string.live_error_playback_fallback)
            } else {
                null
            }
            // (The buffering watchdog arm/cancel that used to live here moved
            // to the shared EngineEventCoordinator — its every-episode scope
            // re-arms on BUFFERING and its timeout decision lands in
            // [executeEngineDecision]. The PiP auto-exit on ERROR/ENDED while
            // in PiP rides the ViewModel's fold of this event.)
            emit(LivePlaybackEvent.EngineStateChanged(s, errorMessage, combinedDetail))
        }.launchIn(scope)
        // The play-state mirror is the shared [mirrorPlaying] collector — the
        // VOD host feeds it one more sink (SyncPlay); here the single
        // PlayingChanged event fans out to BOTH ViewModel sinks (the uiState
        // write and the PiP icon). The same-value guard is adopted from the
        // shared policy: a repeated emission never re-folds.
        scope.mirrorPlaying(
            eng.isPlaying,
            { isPlaying -> emit(LivePlaybackEvent.PlayingChanged(isPlaying)) },
        )
        eng.isAtLiveEdge.onEach { emit(LivePlaybackEvent.AtLiveEdgeChanged(it)) }
            .launchIn(scope)
        eng.positionMs.onEach { _positionMs.value = it }
            .launchIn(scope)
        eng.durationMs.onEach { _durationMs.value = it }
            .launchIn(scope)
    }

    /**
     * Populates the session's transcode-reason mirror from the server's live
     * session (`TranscodingInfo`) when tuning landed on a transcode, and
     * clears it otherwise. Mirrors PlayerSessionManager's VOD refresh via
     * the shared [TranscodeReasonsRefresher]: wait for the session to
     * register, retry once, drop silently on a miss.
     */
    private fun refreshTranscodeReasons(channelId: String, method: LivePlayMethod) {
        transcodeReasonsRefresher.refresh(
            channelId,
            isTranscode = method == LivePlayMethod.TRANSCODE,
            isCurrent = { channels.getOrNull(channelIndex)?.id == channelId },
            clear = {
                transcodeReasons = emptyList()
                emit(LivePlaybackEvent.TranscodeReasonsChanged(emptyList()))
            },
            onReasons = { reasons ->
                transcodeReasons = reasons
                emit(LivePlaybackEvent.TranscodeReasonsChanged(reasons))
            },
        )
    }

    /**
     * Executes one [EngineDecision] from the shared coordinator — what a
     * decision *does* (event emission, the transcode re-resolve/reload). The
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
                    emit(LivePlaybackEvent.BufferingWatchdogTimedOut)
                }
            }
            is EngineDecision.FallbackToTranscode ->
                // The engine's phase machine latched the direct/direct-stream
                // failure and requested the re-resolve; execution unchanged.
                onTranscodeFallback()
            // ENDED handling (the EngineStateChanged fold and the PiP
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
        // across screen re-entries (the activity-scoped session outlives
        // engines).
        enginePlaybackMapJob?.cancel()
        val mapped = MutableStateFlow(EnginePlaybackState.IDLE)
        enginePlaybackMapJob = scope.launch {
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
     * [LiveSessionManager.resolve] with [LiveStreamOption.TRANSCODE]
     * so the server hands back a transcoding URL, and reloads the engine
     * (`onPlayerError` path).
     */
    private fun onTranscodeFallback() {
        val channel = channels.getOrNull(channelIndex) ?: return
        scope.launch {
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
                emit(
                    LivePlaybackEvent.TranscodeFallbackFailed(
                        message = UiMessage.Resource(
                            Res.string.live_error_transcode_fallback, listOf(channel.name)
                        ),
                        engineErrorDetail = engine?.errorDetail?.value,
                    )
                )
                return@launch
            }
            // Reflect the method change in the chrome badge before reloading.
            playMethod = LivePlayMethod.TRANSCODE
            emit(LivePlaybackEvent.PlayMethodChanged(LivePlayMethod.TRANSCODE))
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

    // ── Engine transport (thin forwards the ViewModel funnels into) ──────────

    /** Plays or pauses per the engine's current play state. */
    fun togglePlayPause() {
        engine?.let { if (it.isPlaying.value) it.pause() else it.play() }
    }

    /** Raw play — the PiP transport's PLAY action. */
    fun play() {
        engine?.play()
    }

    /** Raw pause — the PiP-dismiss teardown's pre-teardown quieting. */
    fun pause() {
        engine?.pause()
    }

    fun seekToLiveEdge() {
        engine?.seekToLiveEdge()
    }

    fun seekWithinDvr(positionMs: Long) {
        engine?.seekTo(positionMs)
    }

    /**
     * Restarts the current program from its beginning. Seeks to the start of
     * the DVR window (position 0); only meaningful when the server exposes a
     * timeshift buffer (`durationMs > 0`). On pure-live streams with no DVR
     * window there is no "start" to return to, so this is a no-op — the UI
     * gates the action on `canSeek`.
     */
    fun playFromStart() {
        // Guard: only restart when a DVR window exists. Mirrors the seek-bar
        // gate (LiveSeekBar returns early when durationMs <= 0).
        if (_durationMs.value <= 0L) return
        engine?.seekTo(0L)
    }

    /** Polled by the screen every 500ms while playing to refresh seek-bar state. */
    fun refreshLiveWindow() {
        engine?.refreshLiveWindow()
    }

    /** Exposes the live engine for PlayerView attachment (null before first load). */
    fun engineForRendering(): LivePlayerEngine? = engine

    // ── Teardown ──────────────────────────────────────────────────────────────

    /**
     * Releases the live engine and resets the session's playback state (the
     * ViewModel's [LiveTvPlayerViewModel.stop] funnel, after it has torn the
     * audio seam down and before it resets the UI state). Resetting
     * `initialized` lets the screen re-init playback cleanly if the user
     * returns to the same channel.
     */
    fun release() {
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
        _positionMs.value = 0L
        _durationMs.value = -1L
    }

    private fun emit(event: LivePlaybackEvent) {
        engineEventShell.emitEvent(event)
    }

    private fun PlayMethod.toLivePlayMethod(): LivePlayMethod = when (this) {
        PlayMethod.DIRECT_PLAY -> LivePlayMethod.DIRECT_PLAY
        PlayMethod.DIRECT_STREAM -> LivePlayMethod.DIRECT_STREAM
        PlayMethod.TRANSCODE -> LivePlayMethod.TRANSCODE
    }
}

/**
 * The live playback session's outcome vocabulary — every event names one
 * fold the [LiveTvPlayerViewModel] applies to its UI state (plus the PiP
 * wiring it triggers there). Shaped by exactly what the ViewModel used to
 * do inline in its tune/zap/retry/fallback/engine-observation bodies before
 * the extraction; nothing here is UI state itself — high-frequency position
 * and duration ride their dedicated [StateFlow]s on the session instead.
 */
internal sealed interface LivePlaybackEvent {

    /** The channel-list load started → fold `isLoadingChannels = true`. */
    data object ChannelLoadStarted : LivePlaybackEvent

    /**
     * The channel-list load failed or returned nothing → fold
     * `isLoadingChannels = false`, `isBuffering = false` and the
     * no-channels resource error (the list-empty error is list-UI state, so
     * the ViewModel builds the message).
     */
    data object ChannelLoadFailed : LivePlaybackEvent

    /**
     * A non-empty channel list committed with the selected start channel
     * (route id → last-watched → first) → fold `channels` / `currentIndex` /
     * `currentChannel` and clear `isLoadingChannels`.
     */
    data class ChannelsCommitted(
        val channels: List<LiveTvChannel>,
        val index: Int,
        val channel: LiveTvChannel,
    ) : LivePlaybackEvent

    /**
     * A zap committed to [channel] at [index] → fold the index/channel,
     * clear the now/next programs (they belong to the previous channel) and
     * raise the switching chrome (`isSwitchingChannel = true`).
     */
    data class ChannelSelected(val index: Int, val channel: LiveTvChannel) : LivePlaybackEvent

    /**
     * Source resolution failed for a tune → fold `isBuffering = false`,
     * `isSwitchingChannel = false` and surface [message].
     */
    data class TuneFailed(val message: LivePlayerMessage) : LivePlaybackEvent

    /**
     * The engine loaded a resolved URL for [channel] → fold: arm PiP
     * auto-enter (every successful tune), clear the switching chrome and
     * re-scan the program window for the new channel.
     */
    data class TuneStarted(val channel: LiveTvChannel) : LivePlaybackEvent

    /** The delivery method of the current tune changed → fold `playMethod` (chrome badge). */
    data class PlayMethodChanged(val method: LivePlayMethod) : LivePlaybackEvent

    /** Server transcode reasons for the current tune → fold `transcodeReasons`. */
    data class TranscodeReasonsChanged(val reasons: List<String>) : LivePlaybackEvent

    /**
     * A fresh engine instance came up → fold: install the audio seam
     * (audio-focus + becoming-noisy, once per engine) and re-arm the PiP
     * transport.
     */
    data object EngineCreated : LivePlaybackEvent

    /**
     * An engine state flip, with the message/detail already resolved (the
     * raw engine error or the generic fallback; the detail merged with the
     * rendered transcode reasons when the tune was a transcode) → fold
     * `engineState`, the buffering spinner (`BUFFERING || IDLE`),
     * `errorMessage` and `errorDetail`, plus the PiP auto-exit request when
     * ERROR/ENDED lands while in PiP.
     */
    data class EngineStateChanged(
        val state: LiveEngineState,
        val errorMessage: LivePlayerMessage?,
        val errorDetail: String?,
    ) : LivePlaybackEvent

    /** The engine's play state flipped (same-value guarded) → fold `isPlaying` + mirror into PiP. */
    data class PlayingChanged(val isPlaying: Boolean) : LivePlaybackEvent

    /** Live-edge reachability flipped → fold `isAtLiveEdge`. */
    data class AtLiveEdgeChanged(val atLiveEdge: Boolean) : LivePlaybackEvent

    /**
     * The buffering watchdog timed out (the coordinator's only ShowError
     * here) → fold `isBuffering = false` and surface the retryable
     * buffering-timeout resource error.
     */
    data object BufferingWatchdogTimedOut : LivePlaybackEvent

    /**
     * The engine-requested transcode fallback could not re-resolve → fold
     * `isBuffering = false`, surface [message] and restore the originating
     * [engineErrorDetail] the engine held through the failed fallback.
     */
    data class TranscodeFallbackFailed(
        val message: LivePlayerMessage,
        val engineErrorDetail: String?,
    ) : LivePlaybackEvent
}
