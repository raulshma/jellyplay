package com.raulshma.jellyplay.feature.player.live

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineConfig
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

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
 * because Hilt only binds the former (the latter is a super-interface of
 * `MediaRepository`); the VM only uses the [LiveTvRepository] surface.
 */
@HiltViewModel
class LiveTvPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val userPreferencesStore: UserPreferencesStore,
    private val lastChannelStore: LastChannelStore,
    private val engineFactory: LiveEngineFactory,
    private val imageUrlProvider: ImageUrlProvider,
) : ViewModel() {

    private val liveTvRepository: LiveTvRepository = mediaRepository

    private val _state = MutableStateFlow(LiveTvPlayerUiState())
    val state: StateFlow<LiveTvPlayerUiState> = _state.asStateFlow()

    private var engine: LivePlayerEngine? = null
    private var initialized = false
    private var preMuteVolume: Float? = null
    // Becoming-noisy (headphone unplug) + audio-focus (call/duck) lifecycle.
    // Ported from the VOD VideoPlayerViewModel — live TV arguably needs these
    // more (always-on stream, often background). Fields mirror the VOD names.
    private var becomingNoisyReceiver: android.content.BroadcastReceiver? = null
    private var transientAudioFocusRequest: android.media.AudioFocusRequest? = null
    private var preDuckVolume: Float? = null
    private var wasPlayingBeforeTransientLoss = false
    // Buffering watchdog (see LIVE_BUFFERING_TIMEOUT_MS). A live tuner can stall
    // in BUFFERING without ever raising a PlaybackException; this surfaces an
    // error after the timeout so the rebuffer spinner doesn't spin forever.
    private var bufferingWatchdogJob: Job? = null

    init {
        userPreferencesStore.preferences
            .onEach { prefs ->
                _state.value = _state.value.copy(favorites = prefs.favoriteChannels)
            }
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
            _state.value = _state.value.copy(
                isLoadingChannels = false,
                isBuffering = false,
                errorMessage = "No channels available",
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
        playChannel(channels[index], audioStreamIndex, subtitleStreamIndex)
    }

    fun channelUp(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
        val channels = _state.value.channels
        if (channels.isEmpty()) return
        val next = (_state.value.currentIndex + 1) % channels.size
        switchTo(next, audioStreamIndex, subtitleStreamIndex)
    }

    fun channelDown(audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
        val channels = _state.value.channels
        if (channels.isEmpty()) return
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
            val current = userPreferencesStore.preferences.first().favoriteChannels
            val updated = if (channelId in current) current - channelId else current + channelId
            userPreferencesStore.setFavoriteChannels(updated)
        }
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
     *   1. Always resolve under [PlaybackMode.AUTO] regardless of the user's
     *      playback pref — live tuners do not support static direct play
     *      (FORCE_DIRECT_PLAY disables direct stream + transcode, leaving
     *      the server no playable method for a live source) and forcing
     *      transcode up-front breaks tuners that only offer direct stream.
     *   2. Call `fetchPlaybackInfo` with `autoOpenLiveStream = true` and a
     *      **blank** `mediaSourceId` (live sources have a server-generated
     *      source id distinct from the channel id; passing the channel id as
     *      the source id causes the server to return an empty source list).
     *   3. Pick the first source from the response.
     *   4. Try `resolvePlayback` first — it walks the full Direct Play /
     *      Direct Stream / Transcode decision tree and returns null only if
     *      the server offers no playable method.
     *   5. If `resolvePlayback` returns null, fall back to building a direct
     *      stream URL directly via `getStreamUrl(itemId, sourceId,
     *      liveStreamId)` (the VOD path's `PlayerSessionManager.loadOnline`
     *      does the same fallback). The server has already opened the tuner
     *      session via `autoOpenLiveStream=true`, so the URL works even when
     *      the source flags are all false.
     *   6. If we still have no URL, surface the error with the actual cause.
     */
    private suspend fun playChannel(
        channel: LiveTvChannel,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {
        val prefs = userPreferencesStore.preferences.first()
        val token = playbackRepository.getAccessToken()
        val resolved = resolveLiveStream(
            channel = channel,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mode = PlaybackMode.AUTO,
            playerType = prefs.preferredPlayer,
        ) ?: run {
            _state.value = _state.value.copy(
                isBuffering = false,
                isSwitchingChannel = false,
                errorMessage = "Unable to resolve live stream for ${channel.name}",
            )
            return
        }

        val headers = mutableMapOf<String, String>()
        token?.let { headers["X-Emby-Token"] = it }

        Log.i(TAG, "Playing ${channel.name}: method=${resolved.playMethod}, url=${resolved.streamUrl}")

        ensureEngine(prefs.preferredPlayer, playbackRepository.getServerUrl())
            .load(
                LivePlaybackRequest(
                    url = resolved.streamUrl,
                    title = channel.name,
                    headers = headers,
                    playMethod = resolved.playMethod.toLivePlayMethod(),
                )
            )

        _state.value = _state.value.copy(isSwitchingChannel = false)
        loadPrograms(channel.id)
    }

    /**
     * Resolves a live stream for [channel] under [mode]. Tries the full
     * decision tree (`resolvePlayback`), then falls back to a direct stream
     * URL built from the first server-returned source. Returns null only
     * when both paths fail.
     */
    private suspend fun resolveLiveStream(
        channel: LiveTvChannel,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mode: PlaybackMode,
        playerType: com.raulshma.jellyplay.core.model.PlayerType,
    ): ResolvedPlayback? {
        // Pass mediaSourceId = "" so the server does not filter on a
        // channel-id-as-source-id.
        val resolved = playbackRepository.resolvePlayback(
            itemId = channel.id,
            mediaSourceId = "",
            startTimeTicks = 0L,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxStreamingBitrateBits = null,
            mode = mode,
            playerType = playerType,
        )
        if (resolved != null) return resolved

        Log.w(TAG, "resolvePlayback returned null for ${channel.name} (mode=$mode); falling back to fetchPlaybackInfo")

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
                mode = mode,
                playerType = playerType,
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

        val playMethod = when {
            source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY
            source.supportsDirectStream -> PlayMethod.DIRECT_STREAM
            source.supportsTranscoding && !source.transcodeUrl.isNullOrBlank() -> PlayMethod.TRANSCODE
            // liveStreamId-only fallback above is effectively a direct stream
            // against the opened tuner session.
            else -> PlayMethod.DIRECT_STREAM
        }
        return ResolvedPlayback(
            mediaSourceId = source.id,
            streamUrl = url,
            playMethod = playMethod,
            playSessionId = info.playSessionId,
            maxStreamingBitrate = null,
        )
    }

    private fun ensureEngine(
        preferred: com.raulshma.jellyplay.core.model.PlayerType,
        serverUrl: String?,
    ): LivePlayerEngine {
        val existing = engine
        if (existing != null) return existing
        val config = LiveEngineConfig(
            serverUrl = serverUrl,
            authToken = playbackRepository.getAccessToken(),
        )
        val newEngine = engineFactory.create(preferred, config)
        newEngine.onTranscodeFallbackNeeded = ::onTranscodeFallback
        observeEngine(newEngine)
        engine = newEngine
        // Install becoming-noisy + audio-focus only once for the (reused)
        // engine instance, mirroring the VOD player. They persist across
        // channel switches and are torn down in [stop].
        registerBecomingNoisyReceiver()
        registerTransientFocusLossListener()
        return newEngine
    }

    /**
     * Pause live TV when audio becomes noisy (e.g. headphones unplugged),
     * mirroring the VOD `VideoPlayerViewModel`. Without this, unplugging
     * headphones keeps the live stream blasting through the speaker.
     */
    private fun registerBecomingNoisyReceiver() {
        if (becomingNoisyReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    engine?.pause()
                }
            }
        }
        becomingNoisyReceiver = receiver
        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            context.registerReceiver(
                receiver,
                filter,
                // Private receiver for a system broadcast — explicit flag required on API 34+.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_NOT_EXPORTED
                } else 0,
            )
        } catch (_: Exception) {}
    }

    /**
     * Request audio focus and duck/pause on transient loss, then restore on
     * regain — mirrors the VOD `registerTransientFocusLossListener`. Live TV
     * has no resume-skip concept, so the GAIN path simply restores volume and
     * resumes if it was playing. Volume bookkeeping uses the Media3 player's
     * `volume` directly (same surface [toggleMute] uses).
     */
    private fun registerTransientFocusLossListener() {
        if (transientAudioFocusRequest != null) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return
        val listener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
            val player = engine?.media3Player ?: return@OnAudioFocusChangeListener
            when (focusChange) {
                android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                    engine?.pause()
                    preDuckVolume = null
                    wasPlayingBeforeTransientLoss = false
                    unregisterTransientFocusLossListener()
                }
                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    wasPlayingBeforeTransientLoss = engine?.isPlaying?.value == true
                    if (!_state.value.isMuted) {
                        // Store the raw player volume so GAIN restores it exactly.
                        // Skip capture while muted (player.volume is 0f and would
                        // clobber the real level on restore — mute is re-asserted
                        // on GAIN instead).
                        if (preDuckVolume == null) preDuckVolume = player.volume
                        player.volume = 0.2f
                    }
                    // When muted, volume stays at 0f — ducking must not make muted
                    // audio audible (e.g. during a phone call with the UI muted).
                }
                android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                    // Restore pre-duck volume, or re-assert mute (0f) so a
                    // duck-while-muted cycle never leaks audio at the duck level
                    // past the focus regain.
                    player.volume = if (_state.value.isMuted) 0f else (preDuckVolume ?: player.volume)
                    if (wasPlayingBeforeTransientLoss) engine?.play()
                    preDuckVolume = null
                    wasPlayingBeforeTransientLoss = false
                }
            }
        }
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(listener)
            .build()
        transientAudioFocusRequest = request
        try {
            audioManager.requestAudioFocus(request)
        } catch (_: Exception) {
            transientAudioFocusRequest = null
        }
    }

    private fun unregisterTransientFocusLossListener() {
        val request = transientAudioFocusRequest ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        try {
            audioManager?.abandonAudioFocusRequest(request)
        } catch (_: Exception) {}
        transientAudioFocusRequest = null
        preDuckVolume = null
        wasPlayingBeforeTransientLoss = false
    }

    private fun observeEngine(eng: LivePlayerEngine) {
        eng.state.onEach { s ->
            _state.value = _state.value.copy(
                engineState = s,
                isBuffering = s == LiveEngineState.BUFFERING || s == LiveEngineState.IDLE,
                errorMessage = if (s == LiveEngineState.ERROR) eng.errorMessage.value else null,
            )
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
                                    errorMessage = "Live stream failed to load. Check your connection and retry.",
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
        eng.isPlaying.onEach { _state.value = _state.value.copy(isPlaying = it) }
            .launchIn(viewModelScope)
        eng.isAtLiveEdge.onEach { _state.value = _state.value.copy(isAtLiveEdge = it) }
            .launchIn(viewModelScope)
        eng.positionMs.onEach { _state.value = _state.value.copy(positionMs = it) }
            .launchIn(viewModelScope)
        eng.durationMs.onEach { _state.value = _state.value.copy(durationMs = it) }
            .launchIn(viewModelScope)
    }

    /**
     * Invoked by the engine on a direct/direct stream failure. Re-resolves
     * via `resolveLiveStream` with `FORCE_TRANSCODE` so the server hands
     * back a transcoding URL `onPlayerError` path.
     */
    private fun onTranscodeFallback() {
        val channel = _state.value.currentChannel ?: return
        viewModelScope.launch {
            val prefs = userPreferencesStore.preferences.first()
            val resolved = resolveLiveStream(
                channel = channel,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                mode = PlaybackMode.FORCE_TRANSCODE,
                playerType = prefs.preferredPlayer,
            ) ?: run {
                _state.value = _state.value.copy(
                    isBuffering = false,
                    errorMessage = "Transcode fallback failed for ${channel.name}",
                )
                return@launch
            }
            val headers = mutableMapOf<String, String>()
            playbackRepository.getAccessToken()?.let { headers["X-Emby-Token"] = it }
            engine?.load(
                LivePlaybackRequest(
                    url = resolved.streamUrl,
                    title = channel.name,
                    headers = headers,
                    playMethod = LivePlayMethod.TRANSCODE,
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
        val current = programs.firstOrNull { p ->
            val start = p.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val finish = p.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
            start != null && finish != null && !now.isBefore(start) && now.isBefore(finish)
        }
        val next = programs.firstOrNull { p ->
            val start = p.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
            start != null && start.isAfter(now) && p.id != current?.id
        }
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

    /** Polled by the screen every 500ms while playing to refresh seek-bar state. */
    fun refreshPosition() {
        engine?.refreshLiveWindow()
    }

    /**
     * Toggles mute on the underlying Media3 player. Preserves the pre-mute
     * volume so unmute restores it (per project convention). No-op if the
     * engine has no Media3 player (e.g. a future non-Exo engine).
     */
    fun toggleMute() {
        val player = engine?.media3Player ?: return
        if (_state.value.isMuted) {
            preMuteVolume?.let { player.volume = it }
            _state.value = _state.value.copy(isMuted = false)
        } else {
            preMuteVolume = player.volume
            player.volume = 0f
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
        // Unregister becoming-noisy + audio-focus before releasing the engine
        // so the listeners never dereference a torn-down player.
        becomingNoisyReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        becomingNoisyReceiver = null
        unregisterTransientFocusLossListener()
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        engine?.release()
        engine = null
        initialized = false
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
