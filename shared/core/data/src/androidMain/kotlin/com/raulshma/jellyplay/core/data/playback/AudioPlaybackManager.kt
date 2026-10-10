package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import androidx.compose.runtime.Stable
import android.net.Uri
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.data.playback.focus.claimOnPlayEdge
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import kotlin.math.pow

// AudioQueueItem moved verbatim to
// :shared:core:data commonMain playback/AudioQueueItem.kt (same package).

/**
 * The Android audio core, and the app's [NowPlayingSurface] — the
 * app-scoped Koin SINGLE, identity-stable for the app's lifetime (the
 * remember-key contract the shells' audio-clicks helper reads; its four
 * now-playing flows are the surface's members, satisfied by the
 * [AudioQueueManager]/[AudioPlayerEngine] overrides below).
 */
@Stable
class AudioPlaybackManager(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    /**
     * The library/browse ladder (detail+local resolve, playable-[MediaItem]
     * building, the [androidx.media3.session.MediaLibrarySession] builder) —
     * DI-constructed since the constructor diet: the browser's five family
     * deps (music catalogue / collection reads / playlists / downloads /
     * adaptive bitrate) ride its own single and never reached this manager's
     * own call sites. The manager keeps the pass-through READS its public
     * surface needs ([buildMediaItemForQueueItem], [createPlayer]'s and the
     * crossfade path's `buildMediaSession`).
     */
    private val libraryBrowser: AudioLibraryBrowser,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val sessionManager: PlaybackSessionManager,
    private val audioStore: com.raulshma.jellyplay.core.datastore.audio.AudioStore,
    private val audioEffectsStore: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore,
    private val playbackStore: com.raulshma.jellyplay.core.datastore.playback.PlaybackStore,
    private val queuePersistenceHelper: QueuePersistenceHelper,
    private val bandwidthMonitor: com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor,
    private val bandwidthInterceptor: com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor,
    private val lyricsManager: AudioLyricsManager,
    private val effectsProcessor: AudioEffectsProcessor,
    private val sleepCountdown: SleepCountdown,
    private val jellyfinRemotePlayCastStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy,
    private val audioStreamCache: AudioStreamCache,
    private val audioPrefetchEngine: AudioPrefetchEngine,
    /**
     * Test seam: scope every manager coroutine is launched into. Production
     * callers omit it and get `SupervisorJob() + Dispatchers.Main.immediate`.
     */
    playbackScope: CoroutineScope? = null,
    /**
     * The cross-player exclusivity owner (PlaybackFocus). Music claims the
     * floor on the is-playing edge (the ONE chokepoint every play path
     * crosses) and pauses when the matrix commands it (read-aloud took the
     * floor; since the OS-leg migration an OS loss on the MUSIC seat lands
     * here too, as the suspended-holder command). Defaulted Noop so plain
     * constructions (tests, previews) keep single-player semantics.
     */
    private val playbackFocus: PlaybackFocus = NoopPlaybackFocus,
    /**
     * Test seam: factory consulted by [getOrCreatePlayer] before the real
     * [createPlayer] path, so tests can supply a fake ExoPlayer. Production
     * callers omit it and the media3 player is built as before.
     */
    playerFactory: (() -> ExoPlayer)? = null,
) : AudioEffectsManager by effectsProcessor, AudioQueueManager, AudioPlayerEngine, NowPlayingSurface {
    private val scope = playbackScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val testPlayerFactory = playerFactory

    companion object {
        // Position-poll interval while playback is actively progressing. Matches
        // the video side's default ticker cadence (≈4 Hz). The paused re-check
        // and the player-less backoff bands live on EnginePositionTicker
        // (POSITION_PAUSED_RECHECK_MS / POSITION_NOT_READY_*), which now owns
        // this loop.
        private const val POSITION_POLL_INTERVAL_MS = 250L
    }

    private var exoPlayer: ExoPlayer? = null
    private var currentAudio = com.raulshma.jellyplay.core.datastore.audio.AudioSlice()
    private var currentEffects = com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice()
    private var currentPlayback = com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()

    /**
     * The windowed queue→playlist mirror ([QueuePlaylistMirror]) — the ONE
     * owner of the prefix invariant, the window math, the build cache +
     * permits, the loading/window job guards, the remove-of-current-row
     * coordination and every non-echo player-playlist write (the play-path
     * pre-warm, the transition window slide, the crossfade re-mirror, the
     * shuffle/undo rebuild). This manager keeps only the pass-through call
     * sites.
     */
    private val queueMirror = QueuePlaylistMirror(
        scope = scope,
        playerProvider = { exoPlayer },
        queueProvider = { state.queue.value },
        cursorProvider = { state.currentIndex.value },
        writeCursor = { state._currentIndex.value = it },
        buildItem = { queueItem -> buildMediaItemForQueueItem(queueItem) },
    )

    // Promoted reporter (commonMain): the former `exoPlayerProvider`
    // seam became the ExoPlayer-backed lambda pair below — behaviour
    // byte-identical (same cadence/dedup/stop ordering).
    private val progressReporter = AudioProgressReporter(
        scope = scope,
        playbackRepository = playbackRepository,
        remoteSessionActive = { remoteSessionActive },
        positionMsProvider = { exoPlayer?.currentPosition },
        isPlayingProvider = { exoPlayer?.isPlaying == true },
        itemIdProvider = { currentItemId },
        playSessionIdProvider = { playSessionId },
        playSessionIdSetter = { playSessionId = it },
    )

    /**
     * The engine-command port — [state]'s ONLY engine touch (the desktop
     * adapter's dispatch twin, shaped over ExoPlayer). The media3 player
     * OWNS the playlist, so [prepare] is a window seek to the chassis cursor
     * when the player playlist already mirrors the queue (skip / play-from-
     * queue transitions), and a whole-playlist rebuild at the transition's
     * start position when it does not (the undo restore — the chassis's undo
     * semantics are `setMediaItems(snapshot, index, positionMs)`).
     */
    private val engineDispatch = object : EngineDispatch {
        override val isLive: Boolean get() = exoPlayer != null

        override fun prepare(item: AudioQueueItem, startPositionMs: Long) {
            val player = exoPlayer ?: return
            if (queueMirror.removingCurrentRow) return
            val index = state.currentIndex.value
            if (queueMirror.mirrorsQueue(player) && index < player.mediaItemCount) {
                player.seekTo(index, startPositionMs)
            } else {
                queueMirror.rebuild(
                    items = state.queue.value,
                    targetIndex = index,
                    positionMs = { startPositionMs },
                )
            }
        }

        override fun play() {
            exoPlayer?.play()
        }

        override fun pause() {
            exoPlayer?.takeIf { it.isPlaying }?.pause()
        }

        override fun stop() {
            exoPlayer?.clearMediaItems()
        }

        override fun seekTo(positionMs: Long) {
            exoPlayer?.seekTo(positionMs)
        }

        override fun setPlaybackSpeed(speed: Float) {
            val pitchMultiplier = if (effectsProcessor.pitchSemitones.value == 0f) 1.0f else {
                2.0f.pow(effectsProcessor.pitchSemitones.value / 12.0f)
            }
            exoPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitchMultiplier)
            crossfader.setPlaybackSpeed(speed)
        }
    }

    /**
     * The queue-state chassis (commonMain [AudioQueueStateCore]) — the ONE
     * owner of the playback state flows (re-exposed below by reference), the
     * undo stack + events, the advance/retreat/wrap/shuffle/repeat/restart
     * selection and the cursor/remap semantics this manager previously
     * inlined. What stays here: the media3 playlist mirror (per-mutation
     * writes above + the [QueuePlaylistMirror.rebuild] shuffle/undo restore), the
     * transition choreography listener (the engine's `onMediaItemTransition`
     * IS the choreographer — the chassis runs with its built-in report block
     * suppressed), the play()/pre-warm path, crossfade, A-B loop, effects,
     * position ticker, persistence and teardown.
     */
    internal val state: AudioQueueStateCore = AudioQueueStateCore(
        scope = scope,
        playbackRepository = playbackRepository,
        lyricsManager = lyricsManager,
        progressReporter = progressReporter,
        dispatch = engineDispatch,
        enginePositionMs = { exoPlayer?.currentPosition },
        // Android has no next-item resolve-cache to invalidate (the pre-warm
        // reads the flows live) — the desktop's prefetch clear stays desktop's.
        onQueueShapeInvalidated = {},
        onQueueExhausted = { sleepCountdown.triggerEndOfEpisode() },
        // ReplayGain context passes isShuffled fresh at every apply site here,
        // so the shuffle-flag hook stays default.
        onShuffleModeChanged = {},
        onPlayRequested = { play(it) },
        reportsRideEngineTransition = true,
    )

    // Session cells live in the chassis core (the reporter's stop paths
    // rotate the id synchronously; the transition listener claims the item)
    // — these delegating properties keep every existing use site's name.
    private var playSessionId: String
        get() = state.playSessionId
        set(value) { state.playSessionId = value }

    private var currentItemId: String?
        get() = state.currentItemId
        set(value) { state.currentItemId = value }



    fun start() {
        lyricsManager.initialize(scope)
        effectsProcessor.initialize(scope)
        effectsProcessor.playerProvider = { exoPlayer }
        // Bind the prefetch engine to this manager's live queue/position.
        audioPrefetchEngine.bindProviders(
            queueProvider = { state.queue.value },
            currentIndexProvider = { state.currentIndex.value },
            positionProvider = { state.currentPosition.value },
            durationProvider = { state.duration.value },
        )
        audioPrefetchEngine.start()
        scope.launch(Dispatchers.IO) {
            restorePersistedQueue()
            observeQueuePersistence()
        }
    }
    private var mediaSession: MediaSession? = null
    private var _isLoadingItemFlag = false
    private var positionJob: Job? = null

    private val _gaplessEnabled = MutableStateFlow(true)
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    // Crossfade duration lives in the chassis core (one of its eleven cells,
    // written through AudioQueueStateCore.setCrossfadeDurationMs — the former
    // manager-local duplicate cell folded so the chassis is the one owner on
    // both adapters; same 0L initial value, so the fold is unobservable).
    override val crossfadeDurationMs: StateFlow<Long> get() = state.crossfadeDurationMs

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    // Load/error/undo surfaces live in the chassis core; re-exposed by
    // reference (same instances the desktop adapter exposes).
    override val playbackError: StateFlow<String?> get() = state.playbackError

    /** One-shot stream of destructive queue ops the UI can offer to undo. */
    override val undoEvents: SharedFlow<QueueUndoEvent> get() = state.undoEvents

    /**
     * A→B loop markers. When both are non-null, playback
     * seeks back to [abLoopStartMs] whenever the position reaches
     * [abLoopEndMs]. Independent of [repeatMode] (off/all/one) so the two can
     * compose. Cleared on track change / fresh queue so a loop never bleeds
     * into the next song.
     */
    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    override val abLoopStartMs: StateFlow<Long?> = _abLoopStartMs.asStateFlow()
    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    override val abLoopEndMs: StateFlow<Long?> = _abLoopEndMs.asStateFlow()

    val estimatedBandwidthKbps: StateFlow<Double> = bandwidthInterceptor.estimatedBandwidthKbps

    private val _currentAudioBitrateTier = MutableStateFlow(com.raulshma.jellyplay.core.model.AudioBitrateTier.DEFAULT)
    val currentAudioBitrateTier: StateFlow<com.raulshma.jellyplay.core.model.AudioBitrateTier> = _currentAudioBitrateTier.asStateFlow()
    override val isLoadingItem: StateFlow<Boolean> get() = state.isLoadingItem

    private val crossfader = AudioCrossfader(
        scope = scope,
        context = context,
        effectsProcessor = effectsProcessor,
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        playbackSourceResolver = playbackSourceResolver,
        repeatModeProvider = { state.repeatMode.value },
        crossfadeDurationMsProvider = { crossfadeDurationMs.value },
        isCrossfadingProvider = { _isCrossfading.value },
        isCrossfadingSetter = { _isCrossfading.value = it },
        exoPlayerProvider = { exoPlayer },
        queueSizeProvider = { state.queue.value.size },
        onGetNextItem = { idx -> state.queue.value.getOrNull(idx) },
        speedProvider = { state.speed.value },
        audioBufferProvider = {
            val buf = currentAudio.audioPreloadBufferSize
            buf.minBufferMs to buf.maxBufferMs
        },
        onCrossfadeTransition = { secondary, nextIndex, nextItem ->
            onCrossfadeTransition(secondary, nextIndex, nextItem)
        },
        detachPrimaryListener = { primary -> primary.removeListener(playerListener) },
        onCrossfadeError = { error -> playerListener.onPlayerError(error) },
        onCrossfadeFailed = { nextIndex -> onCrossfadeFailed(nextIndex) },
        dataSourceFactoryProvider = {
            audioStreamCache.getCacheDataSourceFactory(audioStreamCache.buildUpstreamFactory())
        },
    )

    @Volatile
    var remoteSessionActive: Boolean = false
        internal set

    /**
     * Sole writer of the now-playing metadata below; the manager re-exposes
     * the chassis core's tracker by reference so consumers are unchanged.
     * See [NowPlayingTracker] for the per-publish field coverage contract.
     */
    private val nowPlayingTracker: NowPlayingTracker get() = state.nowPlayingTracker

    override val title: StateFlow<String> get() = nowPlayingTracker.title

    override val artist: StateFlow<String> get() = nowPlayingTracker.artist

    override val artistId: StateFlow<String?> get() = nowPlayingTracker.artistId

    override val album: StateFlow<String> get() = nowPlayingTracker.album

    override val albumArtUrl: StateFlow<String> get() = nowPlayingTracker.albumArtUrl

    // ── Chassis flows (re-exposed by reference; the core owns the writes) ──

    override val isPlaying: StateFlow<Boolean> get() = state.isPlaying

    override val currentPosition: StateFlow<Long> get() = state.currentPosition

    override val duration: StateFlow<Long> get() = state.duration

    override val speed: StateFlow<Float> get() = state.speed

    override val shuffleMode: StateFlow<Boolean> get() = state.shuffleMode

    override val repeatMode: StateFlow<Int> get() = state.repeatMode

    override val queue: StateFlow<List<AudioQueueItem>> get() = state.queue

    override val currentIndex: StateFlow<Int> get() = state.currentIndex

    override val currentPlayingItemId: StateFlow<String?> get() = nowPlayingTracker.currentPlayingItemId

    override val lyrics: StateFlow<List<LyricsLine>> get() = lyricsManager.lyrics
    override val currentLyricIndex: StateFlow<Int> get() = lyricsManager.currentLyricIndex
    override val lyricsSource: StateFlow<LyricsSource> get() = lyricsManager.lyricsSource
    override val isFetchingLyrics: StateFlow<Boolean> get() = lyricsManager.isFetchingLyrics
    override val lyricsOffsetMs: StateFlow<Long> get() = lyricsManager.lyricsOffsetMs

    override fun setLyricsOffset(offsetMs: Long) = lyricsManager.setLyricsOffset(offsetMs)

    // AudioEffectsManager rides class delegation onto effectsProcessor (the
    // ~40 former one-line forwarders are gone): a context-free effect now
    // touches its processor + the interface only. The three queue-context
    // overrides (setReplayGainMode / setReplayGainPreAmpDb /
    // setPitchSemitones) are what the delegation cannot express — that they
    // exist is the visible answer to "which effects read the queue".

    /** Skip-previous restart threshold — the chassis core's cell by delegation. */
    var skipPreviousThresholdMs: Long
        get() = state.skipPreviousThresholdMs
        set(value) { state.skipPreviousThresholdMs = value }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.onEnginePlayingChanged(isPlaying)
            // Focus claims ride this ONE edge — every play path (queue tap,
            // resume, notification, tile, widget, cast fling) crosses it, so
            // no per-entry-point claim sites can drift. Newest user action
            // wins: this publishes Held(MUSIC), and the reader (whose loop is
            // not a commandable surface) pauses its speech on the state.
            playbackFocus.claimOnPlayEdge(
                surfaceId = PlaybackSurfaceId.MUSIC,
                isPlaying = isPlaying,
                onDenied = { pause() },
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            // Surface decode/init failures (e.g. MediaCodecAudioRenderer on an
            // undecodable codec) into the same playbackError flow the UI shows
            // for metadata-load failures. Without this, a renderer error leaves
            // the player silently in STATE_IDLE.
            state.onEngineError(error.message ?: "Playback error")
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            // ExoPlayer allocates its AudioTrack (and the real audio session id)
            // lazily after prepare(). At createPlayer() time the id is still
            // AUDIO_SESSION_ID_UNSET, so effects attached there bind to nothing.
            // Re-attach every effect to the now-valid session id whenever it
            // changes, mirroring the video ExoPlayerEngine pattern.
            effectsProcessor.attachAudioEffects(audioSessionId)
            if (effectsProcessor.nightModeEnabled.value) {
                effectsProcessor.applyNightMode()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // The chassis's ENDED matrix. On media3 this only ever takes
                // its exhaustion branch: under repeat >= 1 the player wraps
                // (ALL) or replays (ONE) itself and never reaches ENDED, so
                // the callback fires only at the end of the playlist under
                // RepeatNone — isPlaying off, cursor parked, and the
                // end-of-episode hook (onQueueExhausted) armed.
                state.onEngineEnded()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onTrackTransitioned()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val appMode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> 2
                Player.REPEAT_MODE_ALL -> 1
                else -> 0
            }
            if (state.repeatMode.value != appMode) {
                state.onEngineRepeatModeChanged(appMode)
            }
        }
    }

    val hasActiveSession: Boolean
        get() = exoPlayer != null && currentItemId != null

    init {
        scope.launch {
            combine(audioStore.audio, audioEffectsStore.audioEffects) { audio, effects ->
                audio to effects
            }.collect { (audio, effects) ->
                // The preference→effect diff lives in AudioPreferencesReducer
                // (pure, JVM-tested). This block was previously a ~77-line
                // hand-rolled field-by-field diff tracking 14 stale `prev*`
                // locals — easy to forget a field when adding an effect, and
                // untestable without 18 mocked collaborators. Now the manager
                // is a thin command-dispatcher: the reducer emits the ordered
                // command list, this `when` maps each to its effect setter.
                val commands = AudioPreferencesReducer.diff(currentEffects, currentAudio, effects, audio)
                currentAudio = audio
                currentEffects = effects
                commands.forEach { command -> applyEffectCommand(command) }
            }
        }
        scope.launch {
            playbackStore.playback.collect { playback -> currentPlayback = playback }
        }
        // Note: there is intentionally no `repeatMode.collect { exoPlayer?.repeatMode = ... }`
        // here. `setRepeatMode()` sets `exoPlayer.repeatMode` inline, the player
        // listener (`onRepeatModeChanged`) is the single source of truth for
        // syncing the chassis repeat flow back from the player, and
        // `createPlayer()` restores `player.repeatMode` from the flow on
        // creation. A collector would just re-apply the same value (redundant
        // JNI call) and live for the singleton's lifetime.
    }

    /**
     * Dispatches one [EffectCommand] to its effect setter. Exhaustive `when`
     * on the sealed hierarchy so adding a new effect forces every dispatcher
     * to handle it.
     */
    private fun applyEffectCommand(command: EffectCommand) {
        when (command) {
            is EffectCommand.SetVisualizerEnabled -> enableVisualizer(command.enabled)
            is EffectCommand.SetEqualizerPreset -> setEqualizerPreset(command.preset)
            is EffectCommand.SetLrBalance -> setLrBalance(command.balance)
            is EffectCommand.SetPitchSemitones -> setPitchSemitones(command.semitones)
            is EffectCommand.SetBassBoostStrength -> effectsProcessor.setBassBoostStrength(command.strength)
            is EffectCommand.SetVirtualizerStrength -> effectsProcessor.setVirtualizerStrength(command.strength)
            is EffectCommand.SetDialogueBoostStrength -> effectsProcessor.setDialogueBoostStrength(command.strength)
            is EffectCommand.SetNightModeStrength -> effectsProcessor.setNightModeStrength(command.strength)
            is EffectCommand.SetEqualizerEnabled -> effectsProcessor.setEqualizerEnabled(command.enabled)
            is EffectCommand.SetBassBoostEnabled -> effectsProcessor.setBassBoostEnabled(command.enabled)
            is EffectCommand.SetVirtualizerEnabled -> effectsProcessor.setVirtualizerEnabled(command.enabled)
            is EffectCommand.SetDialogueBoostEnabled -> effectsProcessor.setDialogueBoostEnabled(command.enabled)
            is EffectCommand.SetNightModeEnabled -> effectsProcessor.setNightModeEnabled(command.enabled)
            is EffectCommand.SetReverbPreset -> effectsProcessor.setReverbPreset(command.preset)
        }
    }

    override fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        if (enabled) {
            state.setCrossfadeDurationMs(0L)
            crossfader.cancel()
        }
    }

    override fun setCrossfadeDurationMs(ms: Long) {
        state.setCrossfadeDurationMs(ms)
        if (ms > 0) {
            _gaplessEnabled.value = false
        } else {
            _gaplessEnabled.value = true
            crossfader.cancel()
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        // The factory path ASSIGNS the field (createPlayer() does the same
        // at its tail): the returned player must be the manager's live
        // engine — EngineDispatch.isLive and every `exoPlayer ?: return`
        // guard read the field, so an unassigned factory result would leave
        // the chassis's engine gate dead (the queue-semantics suite's former
        // reflection write existed precisely for this).
        return exoPlayer ?: testPlayerFactory?.invoke()?.also { player -> exoPlayer = player }
            ?: createPlayer()
    }

    /**
     * Guarantees the audio MediaLibrarySession exists without starting
     * playback — the Android Auto / Automotive cold-connect path. A car
     * client binds [JellyPlayPlaybackService] and asks for its session before
     * any phone-side play has run; the player + session are otherwise built
     * lazily on the first play, so the service would hand the head unit a
     * null session and the app would appear unavailable on the car screen.
     * Idempotent: [getOrCreatePlayer] short-circuits on the live engine, so
     * this is a no-op once playback has built the session (and a full
     * recreate after [stopAndRelease], matching the play path). Main thread
     * only — [createPlayer] builds the ExoPlayer on the calling looper.
     */
    fun ensureAudioSession() {
        getOrCreatePlayer()
    }

    private fun createPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            init {
                // Mirror the video engine: allow the FFmpeg extension renderer
                // (software decode for DTS/TrueHD/etc. that the hardware audio
                // decoder can't handle) and fall back across MediaCodec decoders.
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setEnableDecoderFallback(true)
            }

            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    // ONE in-sink chain order, shared with the video engine's
                    // ExoPlayer sink (core:data `inSinkAudioChain`): channel
                    // mix first — it may change the channel count, so every
                    // downstream processor must see the remixed layout — then
                    // dynamics/ReplayGain, the dialogue-boost high-pass, and
                    // the L/R balance processor (music's in-sink balance is
                    // the declared divergence from video, which has no
                    // balance surface).
                    .setAudioProcessors(
                        inSinkAudioChain(
                            channelMixProcessor = effectsProcessor.channelMixProcessor,
                            dynamicsProcessor = effectsProcessor.dynamicsProcessor,
                            replayGainProcessor = effectsProcessor.replayGainProcessor,
                            highPassProcessor = effectsProcessor.highPassProcessor,
                            balanceProcessor = effectsProcessor.balanceProcessor,
                        ),
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                currentAudio.audioPreloadBufferSize.minBufferMs,
                currentAudio.audioPreloadBufferSize.maxBufferMs,
                1_000,
                3_000
            )
            .setTargetBufferBytes(-1)
            .build()

        // Wrap the default data source in the audio byte cache so every byte
        // ExoPlayer reads is side-cached to disk (transparent cache-on-play).
        val upstreamFactory = audioStreamCache.buildUpstreamFactory()
        val cachedFactory = audioStreamCache.getCacheDataSourceFactory(upstreamFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cachedFactory)

        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            // ADR-0004 music OS-leg migration: the attributes still describe
            // the stream for routing, but the OS focus seat is NOT taken here
            // anymore — PlaybackFocus owns it (FocusArbiter, claimed on the
            // is-playing edge). Built-in handling would fight the module's
            // seat: two requests for one surface.
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setPauseAtEndOfMediaItems(false)
            .build()
        player.addListener(playerListener)
        player.repeatMode = getExoPlayerRepeatMode(state.repeatMode.value)

        exoPlayer = player
        // Same construction path as the crossfade rebuild, via the shared
        // audio-session builder (AudioLibraryBrowser owns the library callback).
        val session = libraryBrowser.buildMediaSession(context, player, playSessionId)
        mediaSession = session
        sessionManager.setActiveSession(session)

        effectsProcessor.attachAudioEffects(player.audioSessionId)

        return player
    }

    private fun restorePersistedQueue() {
        scope.launch {
            // Chassis command: the bulk cold-start restore (empty queue
            // writes nothing; null state leaves the five cells untouched).
            state.restorePersisted(
                queue = queuePersistenceHelper.loadQueue(),
                savedState = queuePersistenceHelper.loadState(),
            )
        }
    }

    private fun observeQueuePersistence() {
        queuePersistenceHelper.observeQueue(
            scope = scope,
            queue = state._queue,
            currentIndex = state._currentIndex,
            currentPositionMs = state._currentPosition,
            isPlaying = state._isPlaying,
            repeatMode = state._repeatMode,
            shuffleEnabled = state._shuffleMode,
            playbackSpeed = state._speed,
            shuffleSeed = state._shuffleSeed,
        )
    }

    /**
     * The item-builder path — one queue row → its playable [MediaItem]
     * through the browser ladder (an unresolvable row builds null). The
     * segment fan-out (permits + cache) lives on [QueuePlaylistMirror]; the
     * direct single-row appends ([addToQueue] / [addToQueueAll]) and the
     * play-path load call this directly, as before.
     */
    private suspend fun buildMediaItemForQueueItem(queueItem: AudioQueueItem, startPositionMs: Long = 0L): MediaItem? {
        return libraryBrowser.buildPlayableMediaItem(queueItem.id, startPositionMs)
    }

    /**
     * The shared (commonMain) play-path skeleton — everything from the stop
     * report through the launched resolve/publish/append/load/report/lyrics/
     * ticker choreography. This manager supplies the Android seams: the
     * detail+local resolve ladder, the media3 loads, the windowed queue
     * pre-warm and the post-lyrics ReplayGain apply (its declared hook
     * positions — see [AudioPlayPath]'s divergence list). [AudioPlayPath]'s
     * failure publish is a no-op here (null): [resolvePlayTrack] already
     * published the specific detail-failure text before its local probe —
     * the historical order — so no captured-copy mutable is needed.
     */
    private val playPath = AudioPlayPath(
        scope = scope,
        playbackRepository = playbackRepository,
        progressReporter = progressReporter,
        state = state,
        resolve = { itemId -> resolvePlayTrack(itemId) },
        loadFailureText = { null },
        clearTrackScopedState = { clearAbLoop() },
        onLoadingItemChanged = { loading -> _isLoadingItemFlag = loading },
        acquireEngine = { getOrCreatePlayer() },
        publishDetail = { track -> publishResolvedTrack(track) },
        appendQueueItem = { track -> buildPlayedQueueRow(track) },
        // ReplayGain rides afterReporting here (post-lyrics, the historical
        // Android position); the desktop twin applies it beforeLoad.
        beforeLoad = { _, _ -> },
        loadIntoEngine = { track, clickedItem, startPositionMs ->
            loadResolvedTrack(track, clickedItem, startPositionMs)
        },
        afterLoad = { _, _ -> preWarmPlaylistAroundCursor() },
        fetchLyrics = { track, _ ->
            fetchLyrics(
                itemId = track.itemId,
                artistName = track.lyricArtistName,
                trackName = track.lyricTrackName,
                durationSec = track.lyricDurationSec,
            )
        },
        afterReporting = { track, _ ->
            effectsProcessor.applyReplayGain(track.normalizationGain, state.shuffleMode.value)
        },
        startPositionTracking = { startPositionTracking() },
    )

    override fun play(itemId: String) {
        assertMainThread("play")

        // "Play On" routing (mirrors jellyfin-web's playbackManager.play(): when a
        // remote Jellyfin session is the active player, every play delegates to it
        // and the local engine never loads). Pause local audio so only the remote
        // session plays. Platform prefix — the shared skeleton has no cast routing.
        if (jellyfinRemotePlayCastStrategy.isConnected.value) {
            jellyfinRemotePlayCastStrategy.loadMedia(
                itemId = itemId,
                startPositionMs = 0L,
            )
            exoPlayer?.pause()
            return
        }

        if (currentItemId == itemId) {
            if (_isLoadingItemFlag) return
            val playbackState = exoPlayer?.playbackState
            if (playbackState != null && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
                return
            }
        }

        // Crossfade teardown precedes the skeleton's stop report — the
        // hand-written order.
        crossfader.cancel()
        playPath.start(itemId)
    }

    /**
     * [AudioPlayPath.resolve] seam: the detail round-trip plus the queue-only
     * local-file fallback ladder (the former ~50-line intra-file duplicate of
     * the append/build/load choreography now folds into this one resolve).
     *
     * Dead-code note: the historical restored-current-item resume override is
     * gone — `play()` claims `currentItemId = itemId` synchronously before
     * this async resolve runs, so its cold-start clause could never hold (the
     * recorded desktop parity note; resume is server-ticks only).
     */
    private suspend fun resolvePlayTrack(itemId: String): AudioPlayTrack? {
        val detailResult = mediaRepository.getMediaDetail(itemId)
        val detail = detailResult.getOrNull()

        if (detail != null) {
            val resumeTicks = detail.item.playbackPositionTicks ?: 0L
            return AudioPlayTrack(
                itemId = itemId,
                startPositionMs = if (resumeTicks > 0) resumeTicks / 10_000 else 0L,
                mediaSourceId = detail.mediaSources.firstOrNull()?.id,
                title = detail.item.name,
                artist = detail.item.albumArtist
                    ?: detail.item.artistItems.firstOrNull()?.name
                    ?: "",
                artistId = detail.item.artistItems.firstOrNull()?.id,
                album = detail.item.album,
                lyricArtistName = detail.item.albumArtist
                    ?: detail.item.artistItems.firstOrNull()?.name,
                lyricTrackName = detail.item.name,
                lyricDurationSec = detail.item.runTimeTicks?.let { it / 10_000_000.0 },
                normalizationGain = detail.item.normalizationGain,
                durationMs = detail.item.runTimeTicks?.let { it / 10_000 } ?: 0L,
            )
        }

        // Queue-only local fallback: when the server detail fetch failed but a
        // completed download exists on disk, play the local file.
        // resolveLocalSource performs no getMediaDetail round-trip, so the
        // COMPLETED classification survives even though the server call
        // failed — preserving the historical queue-only fallback. The load
        // error publishes BEFORE the local probe (the historical arm's
        // order) and stays published on fallback (the arm never cleared it —
        // the preserved quirk [AudioPlayTrack.reportsToServer] documents);
        // the skeleton's failure arm re-publishes the same text, which
        // StateFlow conflation drops).
        val failureText = detailResult.exceptionOrNull()?.message ?: "Failed to load track"
        state.setLoadError(failureText)
        val local = playbackSourceResolver.resolveLocalSource(itemId)
        return if (local != null) {
            AudioPlayTrack(
                itemId = itemId,
                startPositionMs = 0L,
                mediaSourceId = local.download.mediaSourceId,
                title = local.title,
                artist = local.offlineItem?.seriesName ?: "",
                artistId = null,
                album = "",
                lyricArtistName = null,
                lyricTrackName = local.title,
                lyricDurationSec = null,
                normalizationGain = null,
                reportsToServer = false,
                uri = local.uri,
            )
        } else {
            // The specific failure text is already on the chassis error flow
            // (published above, before the probe) — the skeleton's failure
            // arm is a null no-op for this manager.
            null
        }
    }

    /**
     * [AudioPlayPath.publishDetail] seam: the detail arm's all-six-fields
     * publish, or the local fallback's five-field local-file shape.
     */
    private fun publishResolvedTrack(track: AudioPlayTrack) {
        if (track.reportsToServer) {
            nowPlayingTracker.publishDetail(
                itemId = track.itemId,
                title = track.title,
                artist = track.artist,
                artistId = track.artistId,
                album = track.album ?: "",
                albumArtUrl = imageUrlProvider.getImageUrl(track.itemId, maxWidth = 600),
            )
        } else {
            nowPlayingTracker.publishLocalFile(
                itemId = track.itemId,
                title = track.title,
                artist = track.artist,
                album = "",
            )
        }
    }

    /** [AudioPlayPath.appendQueueItem] seam — reads the just-published tracker values. */
    private fun buildPlayedQueueRow(track: AudioPlayTrack): AudioQueueItem = AudioQueueItem(
        id = track.itemId,
        name = title.value,
        artist = artist.value,
        album = if (track.reportsToServer) album.value else "",
        imageUrl = if (track.reportsToServer) albumArtUrl.value else null,
        mediaSourceId = track.mediaSourceId,
        durationMs = if (track.reportsToServer) track.durationMs else 0L,
        normalizationGain = track.normalizationGain,
    )

    /**
     * [AudioPlayPath.loadIntoEngine] seam: the detail arm resolves a media3
     * MediaItem through the browser ladder (an unresolvable item loads
     * nothing — the rest of the arm still runs, its historical shape); the
     * local fallback loads the file uri directly.
     */
    private suspend fun loadResolvedTrack(track: AudioPlayTrack, clickedItem: AudioQueueItem, startPositionMs: Long) {
        val player = getOrCreatePlayer()
        if (track.reportsToServer) {
            val clickedMediaItem = buildMediaItemForQueueItem(clickedItem, startPositionMs)
            if (clickedMediaItem != null) {
                player.setMediaItem(clickedMediaItem, startPositionMs)
                player.prepare()
                player.playWhenReady = true
            }
        } else {
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.itemId)
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title.value)
                        .setArtist(artist.value)
                        .build()
                )
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    /**
     * [AudioPlayPath.afterLoad] seam (Android's windowed queue pre-warm — the
     * declared divergence from the desktop's next-item-only prefetch): the
     * whole choreography (window math, cache, job guards, invariant, the
     * prepend's cursor reconciliation) lives on [QueuePlaylistMirror]; this
     * only supplies the play path's engine acquisition — the player must be
     * created NOW and captured for the mirror's Main-write identity guard.
     */
    private fun preWarmPlaylistAroundCursor() {
        queueMirror.prewarm(getOrCreatePlayer())
    }

    /**
     * Enforces the [AudioQueueManager] main-thread contract. ExoPlayer
     * throws a generic `IllegalStateException` when mutated off the
     * application `Looper`; this helper fails fast with a descriptive
     * message instead so background-thread callers (SyncPlay queue
     * mutations, WorkManager callbacks, etc.) are obvious in dev.
     *
     * Always-on: the cost is a single `ThreadLocal` lookup, negligible
     * compared to the queue mutation that follows.
     */
    private fun assertMainThread(method: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AudioQueueManager.$method must be called on the main thread " +
                "(found: ${Looper.myLooper()?.thread?.name ?: "null"}). " +
                "Wrap the call site in `withContext(Dispatchers.Main) { ... }`."
        }
    }

    override fun playQueue(items: List<AudioQueueItem>, startIndex: Int) {
        assertMainThread("playQueue")
        // Chassis: undo history clear, queue + cursor writes, then the start
        // item rides onPlayRequested → play() below.
        state.playQueue(items, startIndex)
    }

    override fun addToQueue(item: AudioQueueItem) {
        assertMainThread("addToQueue")
        state.addToQueue(item)
        val player = exoPlayer ?: return
        // Windowed mirror: appending onto a player that trails the queue
        // would land the row ahead of the not-yet-mirrored middle rows —
        // only a full mirror takes the direct append; the pre-warm window
        // covers the new row once the cursor reaches it.
        if (player.mediaItemCount + 1 != state.queue.value.size) return
        scope.launch {
            buildMediaItemForQueueItem(item)?.let { mediaItem ->
                player.addMediaItem(mediaItem)
            }
        }
    }

    override fun addToQueueAll(items: List<AudioQueueItem>) {
        assertMainThread("addToQueueAll")
        // Single queue emission → single full-list persistence, and a single
        // ordered player append. Iterating addToQueue would emit + persist the
        // whole list per item (O(N²) row writes in N transactions). The
        // chassis's bulk append skips empties.
        state.addToQueueAll(items)
        val player = exoPlayer ?: return
        // The addToQueue full-mirror guard, bulk-shaped.
        if (player.mediaItemCount + items.size != state.queue.value.size) return
        scope.launch {
            val mediaItems = items.mapNotNull { buildMediaItemForQueueItem(it) }
            if (mediaItems.isNotEmpty()) {
                player.addMediaItems(mediaItems)
            }
        }
    }

    override fun removeFromQueue(index: Int) {
        assertMainThread("removeFromQueue")
        if (queueMirror.isLoading) return
        if (index < 0 || index >= state.queue.value.size) return
        // Remove-of-the-current-row: the chassis transition must not write
        // the player — the removeMediaItem below IS the write (media3 plays
        // the shifted-in row and its transition echo reconciles).
        queueMirror.removingCurrentRow = index == state.currentIndex.value
        state.removeFromQueue(index)
        queueMirror.removingCurrentRow = false
        val player = exoPlayer ?: return
        if (index < player.mediaItemCount) {
            player.removeMediaItem(index)
        }
    }

    override fun clearQueue() {
        assertMainThread("clearQueue")
        // Chassis: empty-guard, undo snapshot, writes, and the empty-park —
        // dispatch.stop → clearMediaItems (metadata kept, player idle).
        state.clearQueue()
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        assertMainThread("moveQueueItem")
        // Pure policy in the chassis ([AudioQueuePolicy.planMove] — bounds/
        // no-op rejection, the reorder and the cursor remap in one decision).
        // A rejected plan leaves the queue list instance untouched, so the
        // reference check below no-ops the player mirror with it (the old
        // body returned before its moveMediaItem).
        val queueBefore = state.queue.value
        state.moveQueueItem(fromIndex, toIndex)
        if (state.queue.value === queueBefore) return
        val player = exoPlayer ?: return
        if (fromIndex < player.mediaItemCount && toIndex < player.mediaItemCount) {
            player.moveMediaItem(fromIndex, toIndex)
        } else if (fromIndex < player.mediaItemCount || toIndex < player.mediaItemCount) {
            // The move straddles the mirrored frontier in either direction:
            // mirrored rows shifted under an unmirrored tail — rebuild the
            // windowed mirror instead of writing a stale move.
            queueMirror.rebuild(state.queue.value, state.currentIndex.value) { player.currentPosition }
        }
        // Fully beyond the frontier: the mirrored prefix is untouched.
    }

    override fun skipToNext() {
        assertMainThread("skipToNext")
        if (queueMirror.isLoading) return
        crossfader.cancel()
        // Chassis: the shared advance/wrap rule (+1 mid-queue, wrap to 0
        // under repeat ≥ ALL, blocked at the RepeatNone tail — no undo
        // snapshot then) + cursor write; the engine write rides
        // engineDispatch.prepare (window seek at the new cursor).
        state.skipToNext()
    }

    override fun skipToPrevious() {
        assertMainThread("skipToPrevious")
        if (queueMirror.isLoading) return
        val player = exoPlayer ?: return
        crossfader.cancel()
        // Chassis: restart-in-place above the threshold (strictly > — seek
        // the CURRENT item to zero, no cursor move, no undo snapshot), else
        // the shared retreat rule (+1 wrap at the head under repeat ≥ ALL).
        state.skipToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        assertMainThread("seekTo")
        // Chassis (same optimistic-publish rationale: the seek-bar indicator
        // snaps immediately; the position-poll loop confirms) + the engine
        // seek via the dispatch port.
        state.seekTo(positionMs)
    }

    /**
     * Marks point A of an A→B loop at the current playback position (engine
     * position when live, last published otherwise). Loop transition rules
     * live in [AudioQueuePolicy] (shared verbatim with the desktop adapter);
     * writing the unchanged markers back is StateFlow-conflated to a no-op.
     */
    fun setAbLoopStart() {
        assertMainThread("setAbLoopStart")
        val next = AudioQueuePolicy.markAbLoopStart(
            positionMs = exoPlayer?.currentPosition ?: state.currentPosition.value,
            markers = AudioQueuePolicy.AbLoopMarkers(_abLoopStartMs.value, _abLoopEndMs.value),
        )
        _abLoopStartMs.value = next.startMs
        _abLoopEndMs.value = next.endMs
    }

    /**
     * Marks point B at the current position. Requires A to be set first and
     * the current position to be strictly after A; otherwise this is a no-op
     * (prevents an empty / inverted loop) — the guard lives in
     * [AudioQueuePolicy.markAbLoopEnd].
     */
    fun setAbLoopEnd() {
        assertMainThread("setAbLoopEnd")
        val next = AudioQueuePolicy.markAbLoopEnd(
            positionMs = exoPlayer?.currentPosition ?: state.currentPosition.value,
            markers = AudioQueuePolicy.AbLoopMarkers(_abLoopStartMs.value, _abLoopEndMs.value),
        )
        _abLoopStartMs.value = next.startMs
        _abLoopEndMs.value = next.endMs
    }

    /** Clears the A→B loop markers. */
    fun clearAbLoop() {
        assertMainThread("clearAbLoop")
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
    }

    /**
     * Cycles the A→B loop UI state: nothing → set A → set B (looping) →
     * clear. The state machine is [AudioQueuePolicy.cycleAbLoop] (shared
     * verbatim with the desktop adapter); the marker writes are the manager's.
     */
    override fun cycleAbLoop() {
        assertMainThread("cycleAbLoop")
        val next = AudioQueuePolicy.cycleAbLoop(
            positionMs = exoPlayer?.currentPosition ?: state.currentPosition.value,
            markers = AudioQueuePolicy.AbLoopMarkers(_abLoopStartMs.value, _abLoopEndMs.value),
        )
        _abLoopStartMs.value = next.startMs
        _abLoopEndMs.value = next.endMs
    }

    /**
     * Restores the queue to its state before the most recent destructive
     * operation, if any. Returns true when an undo was applied. The chassis
     * pops the snapshot and writes queue + cursor; the engine restore rides
     * the dispatch port ([EngineDispatch.prepare] seeks when the player
     * playlist already matches, else rebuilds at the snapshot position — the
     * `setMediaItems(snapshot, index, positionMs)` shape). Guarded while a
     * queue load is in flight to avoid racing with [playQueue].
     */
    override fun undoLastQueueOperation(): Boolean {
        assertMainThread("undoLastQueueOperation")
        if (queueMirror.isLoading) return false
        return state.undoLastQueueOperation()
    }

    /**
     * The ONE queue-rebuild write (the shuffle reorder/restore mirror in
     * [toggleShuffle], the undo restore and the out-of-window fallback via
     * [EngineDispatch.prepare]) is [QueuePlaylistMirror.rebuild] — the
     * mirror's own KDoc carries the write's contract.
     */

    fun seekByDelta(deltaMs: Long) {
        assertMainThread("seekByDelta")
        val player = exoPlayer ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    override fun togglePlayPause() {
        assertMainThread("togglePlayPause")
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun changePlaybackSpeed(value: Float) {
        assertMainThread("changePlaybackSpeed")
        // Chassis: the speed flow write + the engine push via
        // engineDispatch.setPlaybackSpeed (pitch-multiplied playback
        // parameters + the crossfader inform).
        state.changePlaybackSpeed(value)
    }

    override fun toggleShuffle() {
        assertMainThread("toggleShuffle")
        // Chassis: the flag flip, the live-engine gate on the REORDER (no
        // engine → flag only), the current-row-to-head reshuffle and the
        // unshuffle restore (cursor snapped to the playing item's original
        // slot via the tracker). The player-playlist rebuild at the live
        // position is the Android mirror of the reorder; the reference
        // inequality reproduces the old rebuild gates exactly (no rebuild
        // for a <= 1-row queue, and none on restore when nothing was saved).
        val queueBefore = state.queue.value
        state.toggleShuffle()
        val player = exoPlayer ?: return
        if (state.queue.value !== queueBefore) {
            queueMirror.rebuild(
                items = state.queue.value,
                targetIndex = state.currentIndex.value,
                positionMs = { player.currentPosition },
            )
        }
    }

    override fun cycleRepeatMode() {
        assertMainThread("cycleRepeatMode")
        state.cycleRepeatMode()
        syncExoPlayerRepeatMode()
    }

    /**
     * Set the repeat mode explicitly.
     * @param mode 0 = RepeatNone, 1 = RepeatAll, 2 = RepeatOne.
     */
    override fun setRepeatMode(mode: Int) {
        assertMainThread("setRepeatMode")
        state.setRepeatMode(mode)
        syncExoPlayerRepeatMode()
    }

    private fun syncExoPlayerRepeatMode() {
        exoPlayer?.repeatMode = getExoPlayerRepeatMode(state.repeatMode.value)
    }

    private fun getExoPlayerRepeatMode(mode: Int): Int {
        return when (mode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Set the shuffle mode explicitly without rebuilding the queue. Used by
     * remote-control commands (e.g. the "SetShuffleQueue" / "SetPlaybackOrder"
     * general command).
     */
    override fun setShuffleMode(enabled: Boolean) {
        assertMainThread("setShuffleMode")
        if (state.shuffleMode.value == enabled) return
        // The ADAPTER's toggle (the chassis restore/reorder + the player
        // rebuild above) — routing to the chassis directly would skip the
        // playlist mirror.
        toggleShuffle()
    }

    /**
     * Pause the audio player if a session is active.
     *
     * Also pauses the crossfade secondary when one is in flight: the matrix's
     * suspended-holder command (OS loss on the MUSIC holder) and the
     * read-aloud victim pause both land here, and with built-in focus off the
     * unpromoted secondary has nothing else to stop it mid-fade (see
     * [AudioCrossfader.pause]).
     */
    override fun pause() {
        assertMainThread("pause")
        exoPlayer?.takeIf { it.isPlaying }?.pause()
        crossfader.pause()
    }

    /**
     * Resume the audio player if a session is active.
     */
    fun resume() {
        assertMainThread("resume")
        exoPlayer?.takeIf { !it.isPlaying }?.play()
    }

    /**
     * Set the player volume in [0f, 1f]. Also mirrors the value onto the
     * system [android.media.AudioManager.STREAM_MUSIC] stream so remote
     * "SetVolume" is actually audible (the player software gain alone is
     * silent when the system stream is muted or at zero).
     */
    fun setVolume(volume: Float) {
        assertMainThread("setVolume")
        val pct = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = pct
        crossfader.setVolume(pct)
        MediaStreamVolume.setNormalized(context, pct)
    }

    /**
     * [AudioPlayerEngine.volume] — the sleep-timer fade's capture source
     * Software gain only (the primary ExoPlayer); the system stream
     * is deliberately not read.
     */
    override val volume: Float
        get() = exoPlayer?.volume ?: 1f

    /**
     * [AudioPlayerEngine.setVolume] — the sleep-timer fade/restore path.
     * Software-only write on the primary player: unlike the user
     * [setVolume] overload above it never touches the crossfader (the
     * crossfade owns both players' volumes while in flight) nor the system
     * stream, and `isUserChange = false` keeps the ramp out of any
     * user-level memory. The 1-arg overload remains the USER volume path.
     */
    override fun setVolume(volume: Float, isUserChange: Boolean) {
        assertMainThread("setVolume")
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * Convenience: 5% increment.
     */
    fun increaseVolume() {
        assertMainThread("increaseVolume")
        val current = exoPlayer?.volume ?: 1f
        setVolume(current + 0.05f)
    }

    /**
     * Convenience: 5% decrement.
     */
    fun decreaseVolume() {
        assertMainThread("decreaseVolume")
        val current = exoPlayer?.volume ?: 1f
        setVolume(current - 0.05f)
    }

    /**
     * Mute / unmute the audio player. Uses an internal flag so [toggleMute]
     * can restore the prior volume.
     */
    private var preMuteVolume: Float = 1f

    fun setMuted(muted: Boolean) {
        assertMainThread("setMuted")
        val current = exoPlayer?.volume ?: 1f
        if (muted) {
            preMuteVolume = if (current > 0f) current else 1f
            setVolume(0f)
        } else {
            setVolume(preMuteVolume.coerceIn(0f, 1f))
        }
    }

    fun toggleMute() {
        assertMainThread("toggleMute")
        val current = exoPlayer?.volume ?: 1f
        setMuted(current > 0f)
    }

    override fun playFromQueue(index: Int) {
        assertMainThread("playFromQueue")
        if (queueMirror.isLoading) return
        if (index < 0 || index >= state.queue.value.size) return
        crossfader.cancel()
        // Chassis: same-index clicks seek the CURRENT item to zero (no
        // reload); cross-index clicks transition at the new cursor (window
        // seek via the dispatch port). Either way a paused player starts.
        state.playFromQueue(index)
        val player = exoPlayer ?: return
        if (!player.isPlaying) {
            player.play()
        }
    }

    override fun setSkipPreviousThreshold(ms: Long) {
        skipPreviousThresholdMs = ms
    }

    // Queue-aware effects — the AudioEffectsManager members the class
    // delegation cannot express: they read the current queue item's
    // normalization gain (replay gain pair) or the playback speed (pitch).
    override fun setReplayGainMode(mode: AudioNormalizationMode) {
        val currentIdx = state.currentIndex.value
        val q = state.queue.value
        val normalizationGain = if (currentIdx in q.indices) q[currentIdx].normalizationGain else null
        effectsProcessor.setReplayGainMode(mode, normalizationGain, state.shuffleMode.value)
    }

    override fun setReplayGainPreAmpDb(db: Float) {
        val currentIdx = state.currentIndex.value
        val q = state.queue.value
        val normalizationGain = if (currentIdx in q.indices) q[currentIdx].normalizationGain else null
        effectsProcessor.setReplayGainPreAmpDb(db, normalizationGain, state.shuffleMode.value)
    }

    override fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    override fun setPitchSemitones(semitones: Float) {
        effectsProcessor.setPitchSemitones(semitones, state.speed.value)
    }

    // ── Track handoff spine (shared by both transition sites) ──────────────

    /**
     * The ONE track-handoff reconcile block shared by the natural-transition
     * path ([onTrackTransitioned]) and the crossfade swap
     * ([onCrossfadeTransition]): moves the cursor to [nextIndex], rotates the
     * current-item claim, publishes the row to the now-playing tracker and —
     * on request — re-applies ReplayGain for the incoming row.
     *
     * DELIBERATELY NOT owned here: the stop/start report ORDERING. The two
     * sites disagree where no single placement is faithful — the crossfade
     * site reports stop(prev) SYNCHRONOUSLY BEFORE the swap/writes (load-
     * bearing: state keeps showing the old track while the stop report is in
     * flight), while the natural-transition site launches stop+start after
     * the writes on the `Main.immediate` scope (so the launch body would run
     * inline BEFORE the writes if enqueued any earlier). Capture
     * (`prevItemId`/`prevSessionId`/`finalStopPositionTicks`) therefore also
     * stays at the call sites, ordered by each site's own report placement.
     *
     * Micro-reorder at the crossfade site: it now calls this BEFORE
     * `exoPlayer = secondary` (the pre-fold code swapped the player first).
     * Unobservable — the runs are straight-line Main-confined with no
     * suspension between these writes and the swap, so nothing can
     * interleave between them.
     */
    private fun commitTrackTransition(
        nextIndex: Int,
        nextItem: AudioQueueItem,
        reapplyReplayGain: Boolean,
    ) {
        state._currentIndex.value = nextIndex
        currentItemId = nextItem.id
        nowPlayingTracker.publishQueueItem(nextItem)
        if (reapplyReplayGain) {
            effectsProcessor.applyReplayGain(nextItem.normalizationGain, state.shuffleMode.value)
        }
    }

    /**
     * The start-report shape both transition sites send after the handoff
     * (`play()` builds its own with a `startPositionTicks` resume field and a
     * detail-source mediaSourceId, so it stays on its own construction).
     */
    private fun startReportFor(item: AudioQueueItem) = PlaybackStartInfo(
        itemId = item.id,
        sessionId = playSessionId,
        mediaSourceId = item.mediaSourceId,
    )

    private fun onTrackTransitioned() {
        val player = exoPlayer ?: return
        val currentMediaId = player.currentMediaItem?.mediaId
        val queueItems = state.queue.value
        val matchIndex = if (currentMediaId != null) {
            queueItems.indexOfFirst { it.id == currentMediaId }
        } else -1

        val targetIndex = if (matchIndex >= 0) matchIndex else {
            val idx = player.currentMediaItemIndex
            if (idx >= 0 && idx < queueItems.size) idx else -1
        }

        if (targetIndex >= 0) {
            val prevItemId = currentItemId
            val prevSessionId = playSessionId
            val prevPosTicks = AudioQueuePolicy.finalStopPositionTicks(
                positionMs = state.currentPosition.value,
                durationMs = state.duration.value,
            )
            val nextItem = queueItems[targetIndex]

            commitTrackTransition(
                nextIndex = targetIndex,
                nextItem = nextItem,
                reapplyReplayGain = true,
            )
            queueMirror.extend()

            scope.launch {
                progressReporter.reportStopped(
                    itemId = prevItemId,
                    sessionId = prevSessionId,
                    positionTicks = prevPosTicks,
                )

                // Lyrics need only fields the queue item already carries, so the
                // fetch can start without waiting for the detail round-trip.
                fetchLyrics(
                    itemId = nextItem.id,
                    artistName = nextItem.artist,
                    trackName = nextItem.name,
                    durationSec = nextItem.durationMs.takeIf { it > 0 }?.let { it / 1000.0 },
                )

                coroutineScope {
                    val detailJob = async { mediaRepository.getMediaDetail(nextItem.id) }
                    val startJob = async {
                        playbackRepository.reportPlaybackStart(startReportFor(nextItem))
                    }
                    detailJob.await().onSuccess { d ->
                        // Auto-EQ-by-genre: previously the pref toggle only
                        // persisted the flag and applyAutoEqForGenre was never
                        // invoked on transitions, leaving the feature dead beyond
                        // the first manual preset pick. applyAutoEqForGenre no-ops
                        // when autoEqByGenre is disabled or no genre matches.
                        effectsProcessor.applyAutoEqForGenre(d.item.genres)
                    }
                    startJob.await()
                }
            }
        }
    }

    private suspend fun onCrossfadeTransition(secondary: ExoPlayer, nextIndex: Int, nextItem: AudioQueueItem) {
        val prevItemId = currentItemId
        val prevSessionId = playSessionId
        val prevPosTicks = AudioQueuePolicy.finalStopPositionTicks(
            positionMs = state.currentPosition.value,
            durationMs = state.duration.value,
        )

        // The crossfade site's load-bearing ordering: the stop report runs
        // SYNCHRONOUSLY before the swap/writes below (see
        // commitTrackTransition's KDoc for why this is not folded in).
        progressReporter.reportStopped(
            itemId = prevItemId,
            sessionId = prevSessionId,
            positionTicks = prevPosTicks,
        )

        commitTrackTransition(
            nextIndex = nextIndex,
            nextItem = nextItem,
            // The wholesale effects re-attach below stands in for the
            // single-row ReplayGain re-apply the natural-transition site does.
            reapplyReplayGain = false,
        )

        exoPlayer = secondary

        mediaSession?.release()
        // Rebuild via the shared audio-session builder (AudioLibraryBrowser is
        // the single construction path for both the initial session and the
        // crossfade rebuild). JellyPlayPlaybackService.onGetSession casts the
        // active session to MediaLibrarySession; a plain MediaSession rebuild
        // — the pre-fix behaviour — cast to null, so the service rejected
        // controller connections, killing the now-playing notification and
        // headset buttons until app restart. Mirrors the video fix
        // (MediaSessionController).
        val newSession = libraryBrowser.buildMediaSession(context, secondary, playSessionId)
        mediaSession = newSession
        sessionManager.setActiveSession(newSession)

        secondary.addListener(playerListener)
        effectsProcessor.applyNightMode()
        effectsProcessor.applyDialogueBoost()
        effectsProcessor.applyEqualizer()
        effectsProcessor.applyBassBoost()
        effectsProcessor.applyVirtualizer()
        effectsProcessor.reattachForCrossfade(secondary.audioSessionId)

        _isCrossfading.value = false

        // Same windowed mirror as the play-path pre-warm: full prefix
        // below the crossfaded row, bounded lookahead ahead of it.
        queueMirror.prewarmAround(secondary, nextIndex)

        playbackRepository.reportPlaybackStart(startReportFor(nextItem))
    }

    /**
     * Invoked by [AudioCrossfader] when a crossfade setup fails (e.g. a
     * network error fetching the next item's detail). In that case the primary
     * ExoPlayer keeps playing the current track to its end and reaches
     * `STATE_ENDED`; under `REPEAT_MODE_OFF` ExoPlayer neither auto-advances
     * nor fires `onMediaItemTransition`, so `_currentIndex` would otherwise
     * stay stuck on the ended item and desync from the queue/UI highlight.
     *
     * We proactively advance the primary player to [nextIndex], which fires
     * `onMediaItemTransition` → [onTrackTransitioned] for full reconciliation
     * (title/artist/lyrics/replayGain/index). Mirrors the manual
     * [skipToNext] advance path.
     */
    private fun onCrossfadeFailed(nextIndex: Int) {
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer ?: return@launch
            val q = state.queue.value
            if (nextIndex !in q.indices) return@launch
            state._currentIndex.value = nextIndex
            player.seekTo(nextIndex, 0L)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun fetchLyrics(
        itemId: String,
        artistName: String?,
        trackName: String?,
        durationSec: Double?,
    ) {
        lyricsManager.fetchLyrics(itemId, artistName, trackName, durationSec)
    }

    override fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        lyricsManager.searchLyrics(query, callback)
    }

    override fun applyLyrics(lrcLibId: Long) {
        lyricsManager.applyLyrics(lrcLibId, currentItemId)
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        var lastPosition = 0L
        var lastDuration = 0L
        // Per-tracking-start instance (the historical locals' reset shape):
        // the sample counter + buffered-position baseline start from zero
        // with every tracking run.
        val bandwidthSampler = AudioBandwidthSampler(
            bandwidthMonitor = bandwidthMonitor,
            assumedKbpsProvider = { currentAudioBitrateTier.value.targetKbps },
        )
        // The shared polling loop (player-contract) owns the cadence, the
        // bounded reactive paused-wait and the player-less exponential
        // backoff; this is only the tick body. Paused ticks still reach
        // [onActive] on a play→pause edge — the body's own gate keeps them
        // no-ops, exactly as before the unification. The ticker's
        // synchronous first-tick prime (primeFirstTick) is the fix this
        // manager was missing: the first position/duration publish now
        // lands BEFORE startPositionTracking() returns instead of up to one
        // 250 ms interval later — so a skip in that window no longer
        // reports the stop position against duration == 0 (the desktop twin
        // has primed since it adopted the ticker; the regression story is
        // on EnginePositionTicker's primeFirstTick KDoc).
        positionJob = EnginePositionTicker(
            scopeProvider = { scope },
            pollingIntervalMs = MutableStateFlow(POSITION_POLL_INTERVAL_MS),
            isPlayingFlow = state._isPlaying,
            isCurrentlyPlaying = { exoPlayer?.isPlaying == true },
            isReady = { exoPlayer != null },
            primeFirstTick = true,
            onActive = tickBody@{
                val player = exoPlayer ?: return@tickBody
                if (!player.isPlaying) return@tickBody

                // Shared tick decisions (commonMain AudioQueuePolicy — the
                // same plan the desktop ticker executes): A–B enforcement
                // target, position/duration publish dedup, lyric-index gate.
                val plan = AudioQueuePolicy.positionTickPlan(
                    positionMs = player.currentPosition,
                    durationMs = player.duration,
                    lastPublishedPositionMs = lastPosition,
                    lastPublishedDurationMs = lastDuration,
                    hasLyrics = lyricsManager.lyrics.value.isNotEmpty(),
                    abLoopStartMs = _abLoopStartMs.value,
                    abLoopEndMs = _abLoopEndMs.value,
                )
                plan.seekToMs?.let { player.seekTo(it) }
                state.publishTick(plan)
                plan.publishPositionMs?.let { lastPosition = it }
                plan.publishDurationMs?.let { lastDuration = it }
                if (plan.updateLyricIndex) {
                    lyricsManager.updateCurrentLyricIndex(state.currentPosition.value)
                }

                // Android-only tick duties (declared divergences — the
                // desktop ticker stops at the shared plan above).
                if (crossfadeDurationMs.value > 0 && state.repeatMode.value != 2) {
                    crossfader.maybeStart()
                }

                // Bandwidth estimation (the collaborator owns the ~5 s
                // sampling cadence + the bitrate-tier-assumed byte math).
                bandwidthSampler.onTick(player.bufferedPosition)
            },
        ).launch()
    }

    override fun stopAndRelease() {
        audioPrefetchEngine.stop()
        crossfader.cancel()

        positionJob?.cancel()
        // Teardown entry (BEFORE the player release below): the reporter
        // snapshots the final item/session/position through its providers —
        // which read the still-live player — cancels its loop, launches the
        // final stop report and rotates the session id synchronously. This
        // is the former ~35-line hand-rolled tail, now shared with the
        // desktop adapter inside AudioProgressReporter.
        progressReporter.stopAndCancel()
        exoPlayer?.removeListener(playerListener)
        mediaSession?.let { sessionManager.clearSession(it) }
        // JellyPlayPlaybackService.onDestroy() may already have released this
        // session. Guard the release so a double-release cannot skip the
        // exoPlayer/effects cleanup that follows.
        try { mediaSession?.release() } catch (_: Exception) { }
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        effectsProcessor.releaseAll()

        // Display resets + the session's item claim + the tracker clear
        // (artistId deliberately survives — the tracker's recorded
        // divergence), in the chassis core.
        state.onEngineReleased()
        lyricsManager.reset()
    }
}
