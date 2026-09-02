package com.raulshma.jellyplay.feature.player.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.TranscodeReasonsRefresher
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.ResolvedPlayback
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
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerAudio
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val TAG = "LiveTvPlayerViewModel"

private const val PROGRAM_LOOKAHEAD_HOURS = 12L
private const val CHANNEL_LIST_LIMIT = 200
/**
 * Watchdog mirroring the VOD player's BUFFERING_TIMEOUT_MS: if a live stream
 * stays in BUFFERING this long without reaching READY (common with flaky
 * tuners that stall without raising a PlaybackException), surface an
 * actionable error instead of spinning the rebuffer spinner forever.
 */
private const val LIVE_BUFFERING_TIMEOUT_MS = 20_000L

/**
 * Owns Live TV playback end to end: loads the channel list from
 * [MediaRepository] (which implements [LiveTvRepository]), resolves the
 * live stream URL via [PlaybackRepository.resolvePlayback], drives a
 * [LivePlayerEngine], and surfaces UI state for the zap list + now/next
 * overlay + rebuffer spinner.
 *
 * Channel switching is in-player: [channelUp] / [channelDown] re-resolve and
 * call [LivePlayerEngine.load] on the same instance. The last-watched channel
 * is persisted via [LastChannelStore].
 *
 * Note: we inject [MediaRepository] rather than the narrower [LiveTvRepository]
 * because the legacy DI graph only bound the former (the latter is a
 * super-interface of `MediaRepository`); the VM only uses the
 * [LiveTvRepository] surface.
 *
 * Player-live conveyor (wave 7B): the ViewModel moved to commonMain over
 * four seams — [LiveEngineFactory] (platform engine construction), the
 * engine's commonMain state surface ([LivePlayerEngine]; the media3 player
 * handle stays behind the androidMain `Media3LivePlayerEngine` cast),
 * [LivePlayerAudio] (audio-focus/becoming-noisy + raw player volume; the
 * legacy `PlayerAudioLifecycle` wrapper and its `@ApplicationContext Context`
 * died with it) and [TranscodeReasonsRenderer] (legacy core:ui formatter).
 * The `UserMessageBus`/`UiText` ctor dep died too: record/cancel feedback
 * now flows through [messages] (livetv's LiveTvUserMessage screen-forward
 * seam) and localized error state stays unresolved until render time
 * ([LivePlayerMessage]).
 *
 * Wave 19C (live PiP): the nullable [pip] seam (androidMain adapter over the
 * legacy core:data singleton the host Activity reads) arms auto-enter on each
 * successful tune, mirrors play state, installs the remote-action transport
 * (SKIP = channel zap) and tears it all down in [stop] — see [PipController].
 */
class LiveTvPlayerViewModel(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val playbackStore: PlaybackStore,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val lastChannelStore: LastChannelStore,
    private val engineFactory: LiveEngineFactory,
    private val imageUrlProvider: ImageUrlProvider,
    private val audio: LivePlayerAudio? = null,
    private val transcodeReasonsRenderer: TranscodeReasonsRenderer =
        TranscodeReasonsRenderer { emptyList() },
    private val pip: PipController? = null,
) : ViewModel() {

    private val liveTvRepository: LiveTvRepository = mediaRepository

    private val _state = MutableStateFlow(LiveTvPlayerUiState())
    val state: StateFlow<LiveTvPlayerUiState> = _state.asStateFlow()

    /**
     * One-shot record/cancel feedback (livetv conveyor's LiveTvUserMessage
     * pattern): the Android screen collects this flow and forwards through
     * the app-wide user-message bus, resolving [LivePlayerMessage.Resource]
     * values with the current locale. Buffered channel + trySend preserves
     * the legacy UserMessageBus emit ordering.
     */
    private val messageChannel = Channel<LivePlayerMessage>(Channel.BUFFERED)
    val messages: Flow<LivePlayerMessage> = messageChannel.receiveAsFlow()

    // High-frequency DVR-window streams kept OUT of [LiveTvPlayerUiState] so
    // the 500 ms position tick invalidates only the leaf that renders it (the
    // bottom-bar seek bar), not the whole screen — mirrors the VOD player's
    // dedicated position/duration flows.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(-1L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var engine: LivePlayerEngine? = null
    private var initialized = false
    private var preMuteVolume: Float? = null

    /**
     * A zap that arrived while the channel list was still loading, deferred
     * instead of dropped (wave 19C gap: a zap during load silently no-oped;
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

    // Buffering watchdog (see LIVE_BUFFERING_TIMEOUT_MS). A live tuner can stall
    // in BUFFERING without ever raising a PlaybackException; this surfaces an
    // error after the timeout so the rebuffer spinner doesn't spin forever.
    private var bufferingWatchdogJob: Job? = null

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
    }

    /**
     * Entry point invoked from the screen's `LaunchedEffect(channelId)`.
     * Loads the channel list, selects the start channel (preferring the
     * route's id — i.e. the channel the user actually tapped — then the
     * last-watched id, then the first channel), and starts playback.
     * Idempotent — subsequent calls with the same id are no-ops so
     * recomposition doesn't restart playback.
     */
    fun initialize(
        channelId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
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

    fun channelUp(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
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

    fun channelDown(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
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
    fun selectChannelById(channelId: String) {
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
    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            val current = appRuntimeStateStore.state.first().favoriteChannels
            val updated = if (channelId in current) current - channelId else current + channelId
            appRuntimeStateStore.setFavoriteChannels(updated)
        }
    }

    // ── In-player recording ──
    // Mirrors ChannelDetailViewModel: each action schedules/cancels a timer on
    // the currently-airing program then re-fetches the program window so the
    // Record ↔ Cancel sheet state follows the server, emitting a one-shot
    // message on [messages] (the legacy UserMessageBus posts). No-op without
    // a current program.

    /** Schedules a single-episode timer for the current program. */
    fun recordCurrentProgramOnce() {
        val program = _state.value.currentProgram ?: return
        viewModelScope.launch {
            mediaRepository.createTimer(program.id)
                .onSuccess {
                    messageChannel.trySend(LivePlayerMessage.Resource(Res.string.live_record_success))
                    refreshProgramsForCurrentChannel()
                }
                .onFailure { e ->
                    messageChannel.trySend(LivePlayerMessage.Raw(e.message ?: "Failed to set recording"))
                }
        }
    }

    /** Schedules a series timer rooted at the current program. */
    fun recordCurrentProgramSeries() {
        val program = _state.value.currentProgram ?: return
        viewModelScope.launch {
            mediaRepository.createSeriesTimer(program.id)
                .onSuccess {
                    messageChannel.trySend(LivePlayerMessage.Resource(Res.string.live_record_success))
                    refreshProgramsForCurrentChannel()
                }
                .onFailure { e ->
                    messageChannel.trySend(LivePlayerMessage.Raw(e.message ?: "Failed to set recording"))
                }
        }
    }

    /** Cancels the single timer on the current program (if one is set). */
    fun cancelCurrentProgramTimer() {
        val program = _state.value.currentProgram ?: return
        val timerId = program.timerId ?: return
        viewModelScope.launch {
            mediaRepository.cancelTimer(timerId)
                .onSuccess {
                    messageChannel.trySend(LivePlayerMessage.Resource(Res.string.live_record_canceled))
                    refreshProgramsForCurrentChannel()
                }
                .onFailure { e ->
                    messageChannel.trySend(LivePlayerMessage.Raw(e.message ?: "Failed to cancel recording"))
                }
        }
    }

    /** Cancels the series timer on the current program (if one is set). */
    fun cancelCurrentProgramSeries() {
        val program = _state.value.currentProgram ?: return
        val seriesTimerId = program.seriesTimerId ?: return
        viewModelScope.launch {
            mediaRepository.cancelSeriesTimer(seriesTimerId)
                .onSuccess {
                    messageChannel.trySend(LivePlayerMessage.Resource(Res.string.live_record_canceled))
                    refreshProgramsForCurrentChannel()
                }
                .onFailure { e ->
                    messageChannel.trySend(LivePlayerMessage.Raw(e.message ?: "Failed to cancel recording"))
                }
        }
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
     * end-to-end flow:
     * 1. Always resolve under [PlaybackMode.AUTO] regardless of the user's
     * playback pref — live tuners do not support static direct play
     * (FORCE_DIRECT_PLAY disables direct stream + transcode, leaving
     * the server no playable method for a live source) and forcing
     * transcode up-front breaks tuners that only offer direct stream.
     * 2. Call `fetchPlaybackInfo` with `autoOpenLiveStream = true` and a
     * **blank** `mediaSourceId` (live sources have a server-generated
     * source id distinct from the channel id; passing the channel id as
     * the source id causes the server to return an empty source list).
     * 3. Pick the first source from the response.
     * 4. Try `resolvePlayback` first — it walks the full Direct Play /
     * Direct Stream / Transcode decision tree and returns null only if
     * the server offers no playable method.
     * 5. If `resolvePlayback` returns null, fall back to building a direct
     * stream URL directly via `getStreamUrl(itemId, sourceId,
     * liveStreamId)` (the VOD path's `PlayerSessionManager.loadOnline`
     * does the same fallback). The server has already opened the tuner
     * session via `autoOpenLiveStream=true`, so the URL works even when
     * the source flags are all false.
     * 6. If we still have no URL, surface the error with the actual cause.
     */
    private suspend fun playChannel(
        channel: LiveTvChannel,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        val playback = playbackStore.playback.first()
        val resolved = resolveLiveStream(
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

    /**
     * Resolves a live stream for [channel] under [option]. Tries the full
     * decision tree (`resolvePlayback`), then falls back to a direct stream
     * URL built from the first server-returned source. Returns null only
     * when both paths fail.
     */
    private suspend fun resolveLiveStream(
        channel: LiveTvChannel,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        option: LiveStreamOption,
        playerType: com.raulshma.jellyplay.core.model.PlayerType,
    ): ResolvedPlayback? {
        // Pass mediaSourceId = "" so the server does not filter on a
        // channel-id-as-source-id. mode = AUTO is inert here because the
        // live flag table is driven by `liveStreamOption`.
        val resolved = playbackRepository.resolvePlayback(
            itemId = channel.id,
            mediaSourceId = "",
            startTimeTicks = 0L,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxStreamingBitrateBits = null,
            mode = PlaybackMode.AUTO,
            playerType = playerType,
            liveStreamOption = option,
        )
        if (resolved != null) {
            // When the user asked for Direct Stream but the server still
            // resolved a transcode, it's because the server's live-source
            // probe failed (TranscodeReasons=DirectPlayError) even though
            // the tuner is opened and readable. The tuner session is live,
            // so ignore the server's verdict and build a direct-stream URL
            // ourselves from the liveStreamId; if the player genuinely can't
            // decode it, the existing onPlayerError -> transcode fallback
            // catches that. AUTO/TRANSCODE accept whatever the server picks.
            if (option == LiveStreamOption.DIRECT_STREAM &&
                resolved.playMethod == PlayMethod.TRANSCODE
            ) {
                Log.w(
                    TAG,
                    "Server resolved transcode for ${channel.name} despite " +
                        "DIRECT_STREAM request (probe failed); forcing direct stream"
                )
            } else {
                return resolved
            }
        } else {
            Log.w(TAG, "resolvePlayback returned null for ${channel.name} (option=$option); falling back to fetchPlaybackInfo")
        }

        // Fallback: fetch PlaybackInfo directly and build a direct stream URL
        // from the first source's liveStreamId. Mirrors the VOD
        // PlayerSessionManager.loadOnline fallback.
        val info: PlaybackInfoResult = playbackRepository
            .fetchPlaybackInfo(
                itemId = channel.id,
                mediaSourceId = "",
                startTimeTicks = 0L,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                maxStreamingBitrateBits = null,
                mode = PlaybackMode.AUTO,
                playerType = playerType,
                liveStreamOption = option,
            )
            .getOrNull() ?: run {
            Log.e(TAG, "fetchPlaybackInfo failed for ${channel.name}")
            return null
        }

        val source = info.mediaSources.firstOrNull() ?: run {
            Log.e(TAG, "fetchPlaybackInfo returned no media sources for ${channel.name}")
            return null
        }
        Log.i(
            TAG,
            "Source for ${channel.name}: id=${source.id}, " +
                "directPlay=${source.supportsDirectPlay}, " +
                "directStream=${source.supportsDirectStream}, " +
                "transcode=${source.supportsTranscoding}, " +
                "transcodeUrl=${source.transcodeUrl != null}, " +
                "liveStreamId=${source.liveStreamId != null}, " +
                "requiresOpening=${source.requiresOpening}"
        )

        val liveId = source.liveStreamId
        val url = when {
            source.supportsDirectStream || source.supportsDirectPlay ->
                playbackRepository.getStreamUrl(
                    itemId = channel.id,
                    mediaSourceId = source.id,
                    startTimeTicks = 0L,
                    liveStreamId = liveId,
                )
            source.supportsTranscoding && !source.transcodeUrl.isNullOrBlank() ->
                playbackRepository.getStreamUrl(
                    itemId = channel.id,
                    mediaSourceId = source.id,
                    startTimeTicks = 0L,
                    liveStreamId = liveId,
                )
            // Live tuner sessions opened via autoOpenLiveStream=true can be
            // read by hitting /Videos/{id}/stream?LiveStreamId=… even when
            // the server's playability decision returned all-false flags
            // (observed with some M3U/HLS-only tuners under FORCE_DIRECT_PLAY
            // or when the device profile doesn't claim HLS support). The
            // tuner is already open server-side, so the URL is valid.
            !liveId.isNullOrBlank() -> {
                Log.w(TAG, "All playability flags false for ${channel.name}; attempting direct stream via liveStreamId")
                playbackRepository.getStreamUrl(
                    itemId = channel.id,
                    mediaSourceId = source.id,
                    startTimeTicks = 0L,
                    liveStreamId = liveId,
                )
            }
            else -> {
                Log.e(TAG, "No playable method offered for ${channel.name}")
                return null
            }
        }
        if (url.isBlank()) {
            Log.e(TAG, "Resolved URL is blank for ${channel.name}")
            return null
        }

        // The URL built above is always a direct `/Videos/{id}/stream` URL
        // (via getStreamUrl), never a transcoding master.m3u8 — even when the
        // server's flags say transcoding is the only option. So the play
        // method reflects the URL we built, not the server's verdict; this
        // also keeps the onPlayerError -> transcode fallback eligible.
        val playMethod = when {
            source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY
            else -> PlayMethod.DIRECT_STREAM
        }
        return ResolvedPlayback(
            mediaSourceId = source.id,
            streamUrl = url,
            playMethod = playMethod,
            playSessionId = info.playSessionId,
            maxStreamingBitrate = null,
            container = source.container,
        )
    }

    private fun ensureEngine(): LivePlayerEngine {
        val existing = engine
        if (existing != null) return existing
        val config = LiveEngineConfig(
            authToken = playbackRepository.getAccessToken(),
        )
        val newEngine = engineFactory.create(config, ::onTranscodeFallback)
        observeEngine(newEngine)
        engine = newEngine
        // Install becoming-noisy + audio-focus only once for the (reused)
        // engine instance, mirroring the VOD player. They persist across
        // channel switches and are torn down in [stop].
        playerAudioLifecycle?.onEngineCreated()
        // Re-arm the PiP transport alongside every engine creation: [stop]
        // runs PipController.reset() which nulls it, and this (reused,
        // activity-scoped) VM's init never re-runs on a screen re-entry — so
        // the bridge must ride the engine lifecycle or PiP controls go dead.
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
            // Buffering watchdog: arm a timeout on entering BUFFERING, cancel it
            // on any other state. If the tuner stalls without a PlaybackException,
            // the timeout surfaces a retryable error so the user isn't stuck on
            // a spinning rebuffer. Mirrors the VOD player's initial-buffer guard.
            when (s) {
                LiveEngineState.BUFFERING -> {
                    if (bufferingWatchdogJob == null) {
                        bufferingWatchdogJob = viewModelScope.launch {
                            delay(LIVE_BUFFERING_TIMEOUT_MS)
                            if (eng.state.value == LiveEngineState.BUFFERING) {
                                _state.value = _state.value.copy(
                                    isBuffering = false,
                                    errorMessage = LivePlayerMessage.Resource(
                                        Res.string.live_error_buffering_timeout
                                    ),
                                )
                            }
                        }
                    }
                }
                else -> {
                    bufferingWatchdogJob?.cancel()
                    bufferingWatchdogJob = null
                }
            }
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
     * Invoked by the engine on a direct/direct stream failure. Re-resolves
     * via `resolveLiveStream` with [LiveStreamOption.TRANSCODE] so the server
     * hands back a transcoding URL (`onPlayerError` path).
     */
    private fun onTranscodeFallback() {
        val channel = _state.value.currentChannel ?: return
        viewModelScope.launch {
            val playback = playbackStore.playback.first()
            val resolved = resolveLiveStream(
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
        val now = Instant.now()
        val end = now.plusSeconds(PROGRAM_LOOKAHEAD_HOURS * 3600)
        val fmt = DateTimeFormatter.ISO_INSTANT
        val programs = liveTvRepository.getLiveTvPrograms(
            channelId = channelId,
            startDateUtc = fmt.format(now),
            endDateUtc = fmt.format(end),
        ).getOrNull().orEmpty()
        val parsed = programs.map { p ->
            Triple(
                p,
                p.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() },
                p.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() },
            )
        }
        val current = parsed.firstOrNull { (_, start, finish) ->
            start != null && finish != null && !now.isBefore(start) && now.isBefore(finish)
        }?.first
        val next = parsed.firstOrNull { (p, start, _) ->
            start != null && start.isAfter(now) && p.id != current?.id
        }?.first
        _state.value = _state.value.copy(currentProgram = current, nextProgram = next)
    }

    fun togglePlayPause() {
        engine?.let { if (it.isPlaying.value) it.pause() else it.play() }
    }

    fun seekToLiveEdge() {
        engine?.seekToLiveEdge()
    }

    fun seekWithinDvr(positionMs: Long) {
        engine?.seekTo(positionMs)
    }

    /**
     * Restarts the current program from its beginning.
     * Seeks to the start of the DVR window (position 0); only meaningful when
     * the server exposes a timeshift buffer (`durationMs > 0`). On pure-live
     * streams with no DVR window there is no "start" to return to, so this is
     * a no-op — the UI gates the action on `canSeek`.
     */
    fun playFromStart() {
        // Guard: only restart when a DVR window exists. Mirrors the seek-bar
        // gate (LiveSeekBar returns early when durationMs <= 0).
        if (_durationMs.value <= 0L) return
        engine?.seekTo(0L)
    }

    /** Polled by the screen every 500ms while playing to refresh seek-bar state. */
    fun refreshPosition() {
        engine?.refreshLiveWindow()
    }

    /**
     * Toggles mute on the underlying platform player. Preserves the pre-mute
     * volume so unmute restores it (per project convention). No-op if there
     * is no audio seam / attached platform player (e.g. a future non-Exo
     * engine, or a platform without one).
     */
    fun toggleMute() {
        val audio = playerAudioLifecycle ?: return
        val currentVolume = audio.playerVolume() ?: return
        if (_state.value.isMuted) {
            // Restore the pre-mute level captured when muting; never slam to a
            // fixed default. Null (e.g. mute set externally, or player swapped)
            // means leave the current volume untouched.
            preMuteVolume?.let { audio.setPlayerVolume(it) }
            preMuteVolume = null
            _state.value = _state.value.copy(isMuted = false)
        } else {
            // Capture the raw player volume so unmute restores it exactly.
            preMuteVolume = currentVolume
            audio.setPlayerVolume(0f)
            _state.value = _state.value.copy(isMuted = true)
        }
    }

    fun retry(
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
    fun setLiveStreamOption(option: LiveStreamOption) {
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
     * remote-action intents to the live engine. Live mapping: PLAY/PAUSE hit
     * the engine directly; the window's rewind/forward SKIP actions zap
     * channel-down/up (the live-TV PiP convention — a DVR micro-seek is
     * meaningless on pure-live streams, and [seekWithinDvr] is already a
     * no-op there), re-resolving with the route's preferred stream overrides;
     * NEXT stays unmapped (live has no "next episode", so pipHasNext is never
     * set and the Activity never renders that action).
     */
    private fun registerPipTransport() {
        val pip = pip ?: return
        pip.pipTransport = PipTransport { action ->
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
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        engine?.release()
        engine = null
        initialized = false
        // Drop any zap deferred during an in-flight load — it belongs to the
        // session being torn down and must not fire on the next entry's load.
        pendingZap = null
        // Clear the captured pre-mute volume so a stale value from the previous
        // player is never restored on a later unmute (e.g. mute → leave screen →
        // return to a fresh engine). isMuted is reset via the fresh uiState below.
        preMuteVolume = null
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
