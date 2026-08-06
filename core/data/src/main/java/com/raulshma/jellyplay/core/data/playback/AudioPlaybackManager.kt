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
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.ReverbPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Immutable
data class AudioQueueItem(
    val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val imageUrl: String?,
    val mediaSourceId: String?,
    val durationMs: Long = 0L,
    val normalizationGain: Float? = null,
)

@Stable
@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val sessionManager: PlaybackSessionManager,
    private val audioStore: com.raulshma.jellyplay.core.datastore.audio.AudioStore,
    private val audioEffectsStore: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore,
    private val playbackStore: com.raulshma.jellyplay.core.datastore.playback.PlaybackStore,
    private val queuePersistenceHelper: QueuePersistenceHelper,
    private val bandwidthMonitor: com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor,
    private val adaptiveBitrateSelector: com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector,
    private val bandwidthInterceptor: com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor,
    private val lyricsManager: AudioLyricsManager,
    private val effectsProcessor: AudioEffectsProcessor,
    private val sleepTimerManager: SleepTimerManager,
    private val jellyfinRemotePlayCastStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy,
    private val audioStreamCache: AudioStreamCache,
    private val audioPrefetchEngine: AudioPrefetchEngine,
) : AudioEffectsManager, AudioQueueManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val queuePreWarmPermits = Semaphore(8)

    companion object {
        // Position-poll interval while playback is actively progressing. Matches
        // the video side's default ticker cadence (≈4 Hz).
        private const val POSITION_POLL_INTERVAL_MS = 250L
        // While paused, the position/lyrics/crossfade work in startPositionTracking
        // is a no-op; re-check playback state at this interval (mirrors
        // EnginePositionTicker.POSITION_PAUSED_RECHECK_MS) instead of polling
        // at POSITION_POLL_INTERVAL_MS for the whole paused session.
        private const val POSITION_PAUSED_RECHECK_MS = 2_500L
    }

    private var exoPlayer: ExoPlayer? = null
    private var currentAudio = com.raulshma.jellyplay.core.datastore.audio.AudioSlice()
    private var currentEffects = com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice()
    private var currentPlayback = com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()

    private val libraryBrowser = AudioLibraryBrowser(
        scope = scope,
        mediaRepository = mediaRepository,
        downloadRepository = downloadRepository,
        playbackRepository = playbackRepository,
        playbackSourceResolver = playbackSourceResolver,
        streamingQualityProvider = { currentPlayback.streamingQuality },
        adaptiveBitrateSelector = adaptiveBitrateSelector,
    )

    private val progressReporter = AudioProgressReporter(
        scope = scope,
        playbackRepository = playbackRepository,
        remoteSessionActive = { remoteSessionActive },
        exoPlayerProvider = { exoPlayer },
        itemIdProvider = { currentItemId },
        playSessionIdProvider = { playSessionId },
        playSessionIdSetter = { playSessionId = it },
    )



    fun start() {
        lyricsManager.initialize(scope)
        effectsProcessor.initialize(scope)
        effectsProcessor.playerProvider = { exoPlayer }
        // Bind the prefetch engine to this manager's live queue/position.
        audioPrefetchEngine.bindProviders(
            queueProvider = { _queue.value },
            currentIndexProvider = { _currentIndex.value },
            positionProvider = { _currentPosition.value },
            durationProvider = { _duration.value },
        )
        audioPrefetchEngine.start()
        scope.launch(Dispatchers.IO) {
            restorePersistedQueue()
            observeQueuePersistence()
        }
    }
    private var mediaSession: MediaSession? = null
    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private var _isLoadingItemFlag = false
    private var positionJob: Job? = null
    private var queueLoadingJob: Job? = null
    private val mediaItemCache = android.util.LruCache<String, MediaItem>(25)

    private val _gaplessEnabled = MutableStateFlow(true)
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(0L)
    val crossfadeDurationMs: StateFlow<Long> = _crossfadeDurationMs.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    /**
     * Bounded history of pre-mutation queue snapshots enabling undo of
     * destructive operations. Accessed only on the main
     * thread per the [AudioQueueManager] contract.
     */
    private val queueUndoStack = QueueUndoStack()

    private val _undoEvents = MutableSharedFlow<QueueUndoEvent>(extraBufferCapacity = 4)
    /** One-shot stream of destructive queue ops the UI can offer to undo. */
    val undoEvents: SharedFlow<QueueUndoEvent> = _undoEvents.asSharedFlow()

    /**
     * A→B loop markers. When both are non-null, playback
     * seeks back to [abLoopStartMs] whenever the position reaches
     * [abLoopEndMs]. Independent of [repeatMode] (off/all/one) so the two can
     * compose. Cleared on track change / fresh queue so a loop never bleeds
     * into the next song.
     */
    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    val abLoopStartMs: StateFlow<Long?> = _abLoopStartMs.asStateFlow()
    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    val abLoopEndMs: StateFlow<Long?> = _abLoopEndMs.asStateFlow()

    val estimatedBandwidthKbps: StateFlow<Double> = bandwidthInterceptor.estimatedBandwidthKbps

    private val _currentAudioBitrateTier = MutableStateFlow(com.raulshma.jellyplay.core.model.AudioBitrateTier.DEFAULT)
    val currentAudioBitrateTier: StateFlow<com.raulshma.jellyplay.core.model.AudioBitrateTier> = _currentAudioBitrateTier.asStateFlow()
    private val _isLoadingItem = MutableStateFlow(false)
    val isLoadingItem: StateFlow<Boolean> = _isLoadingItem.asStateFlow()

    private val crossfader = AudioCrossfader(
        scope = scope,
        context = context,
        effectsProcessor = effectsProcessor,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        playbackSourceResolver = playbackSourceResolver,
        repeatModeProvider = { _repeatMode.value },
        crossfadeDurationMsProvider = { _crossfadeDurationMs.value },
        isCrossfadingProvider = { _isCrossfading.value },
        isCrossfadingSetter = { _isCrossfading.value = it },
        exoPlayerProvider = { exoPlayer },
        queueSizeProvider = { _queue.value.size },
        onGetNextItem = { idx -> _queue.value.getOrNull(idx) },
        speedProvider = { _speed.value },
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

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist.asStateFlow()

    private val _artistId = MutableStateFlow<String?>(null)
    val artistId: StateFlow<String?> = _artistId.asStateFlow()

    private val _album = MutableStateFlow("")
    val album: StateFlow<String> = _album.asStateFlow()

    private val _albumArtUrl = MutableStateFlow("")
    val albumArtUrl: StateFlow<String> = _albumArtUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    override val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(0)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioQueueItem>>(emptyList())
    override val queue: StateFlow<List<AudioQueueItem>> = _queue.asStateFlow()

    private var unshuffledQueue: List<AudioQueueItem> = emptyList()
    private var unshuffledIndex: Int = -1

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentPlayingItemId = MutableStateFlow<String?>(null)
    override val currentPlayingItemId: StateFlow<String?> = _currentPlayingItemId.asStateFlow()

    val lyrics: StateFlow<List<LyricsLine>> get() = lyricsManager.lyrics
    val currentLyricIndex: StateFlow<Int> get() = lyricsManager.currentLyricIndex
    val lyricsSource: StateFlow<LyricsSource> get() = lyricsManager.lyricsSource
    val isFetchingLyrics: StateFlow<Boolean> get() = lyricsManager.isFetchingLyrics
    val lyricsOffsetMs: StateFlow<Long> get() = lyricsManager.lyricsOffsetMs

    fun setLyricsOffset(offsetMs: Long) = lyricsManager.setLyricsOffset(offsetMs)

    override val nightModeEnabled: StateFlow<Boolean> get() = effectsProcessor.nightModeEnabled
    override val dialogueBoostEnabled: StateFlow<Boolean> get() = effectsProcessor.dialogueBoostEnabled
    override val equalizerEnabled: StateFlow<Boolean> get() = effectsProcessor.equalizerEnabled
    override val equalizerSettings: StateFlow<com.raulshma.jellyplay.core.model.EqualizerSettings> get() = effectsProcessor.equalizerSettings
    override val equalizerPreset: StateFlow<EqualizerPreset> get() = effectsProcessor.equalizerPreset
    override val bassBoostEnabled: StateFlow<Boolean> get() = effectsProcessor.bassBoostEnabled
    override val bassBoostStrengthState: EffectStrength get() = effectsProcessor.bassBoostStrengthState
    override val virtualizerEnabled: StateFlow<Boolean> get() = effectsProcessor.virtualizerEnabled
    override val virtualizerStrength: StateFlow<Int> get() = effectsProcessor.virtualizerStrength
    override val reverbPresetState: StateFlow<ReverbPreset> get() = effectsProcessor.reverbPresetState
    override val lrBalance: StateFlow<Float> get() = effectsProcessor.lrBalance
    override val pitchSemitones: StateFlow<Float> get() = effectsProcessor.pitchSemitones
    override val autoEqByGenre: StateFlow<Boolean> get() = effectsProcessor.autoEqByGenre
    override val fftData: StateFlow<ByteArray> get() = effectsProcessor.fftData
    override val waveformData: StateFlow<ByteArray> get() = effectsProcessor.waveformData
    override val replayGainMode: StateFlow<AudioNormalizationMode> get() = effectsProcessor.replayGainMode
    override val replayGainPreAmpDb: StateFlow<Float> get() = effectsProcessor.replayGainPreAmpDb
    override val channelMixMode: StateFlow<ChannelMixMode> get() = effectsProcessor.channelMixMode
    override val channelMixEnabled: StateFlow<Boolean> get() = effectsProcessor.channelMixEnabled

    var skipPreviousThresholdMs = 3_000L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            // Surface decode/init failures (e.g. MediaCodecAudioRenderer on an
            // undecodable codec) into the same playbackError flow the UI shows
            // for metadata-load failures. Without this, a renderer error leaves
            // the player silently in STATE_IDLE.
            _playbackError.value = error.message ?: "Playback error"
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
                onTrackEnded()
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
            if (_repeatMode.value != appMode) {
                _repeatMode.value = appMode
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
        // Note: there is intentionally no `_repeatMode.collect { exoPlayer?.repeatMode = ... }`
        // here. `setRepeatMode()` sets `exoPlayer.repeatMode` inline, the player
        // listener (`onRepeatModeChanged`) is the single source of truth for
        // syncing `_repeatMode` back from the player, and `ensureExoPlayer()`
        // restores `player.repeatMode` from `_repeatMode.value` on creation.
        // A collector would just re-apply the same value (redundant JNI call)
        // and live for the singleton's lifetime.
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

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        if (enabled) {
            _crossfadeDurationMs.value = 0L
            crossfader.cancel()
        }
    }

    fun setCrossfadeDurationMs(ms: Long) {
        _crossfadeDurationMs.value = ms
        if (ms > 0) {
            _gaplessEnabled.value = false
        } else {
            _gaplessEnabled.value = true
            crossfader.cancel()
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: createPlayer()
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
                    .setAudioProcessors(
                        arrayOf(
                            // Channel mix first: it may change the channel count,
                            // so every downstream processor must see the remixed
                            // layout.
                            effectsProcessor.channelMixProcessor,
                            // Dynamics compression (DYNAMIC normalization) and
                            // ReplayGain (TRACK/ALBUM) are mutually exclusive at
                            // runtime but both live in the chain.
                            effectsProcessor.dynamicsProcessor,
                            effectsProcessor.replayGainProcessor,
                            // High-pass rumble cut for dialogue boost; a no-op
                            // when boost is off.
                            effectsProcessor.highPassProcessor,
                            effectsProcessor.balanceProcessor,
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
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setPauseAtEndOfMediaItems(false)
            .build()
        player.addListener(playerListener)
        player.repeatMode = getExoPlayerRepeatMode(_repeatMode.value)

        exoPlayer = player
        val session = MediaLibrarySession.Builder(context, player, libraryBrowser.callback)
            .setId(playSessionId)
            .build()
        mediaSession = session
        sessionManager.setActiveSession(session)

        effectsProcessor.attachAudioEffects(player.audioSessionId)

        return player
    }

    private fun restorePersistedQueue() {
        scope.launch {
            val items = queuePersistenceHelper.loadQueue()
            if (items.isNotEmpty()) {
                _queue.value = items
            }
            val savedState = queuePersistenceHelper.loadState()
            savedState?.let { state ->
                _currentIndex.value = state.currentIndex
                _currentPosition.value = state.currentPositionMs
                _repeatMode.value = state.repeatMode.coerceIn(0, 2)
                _shuffleMode.value = state.shuffleEnabled
                _speed.value = state.playbackSpeed
            }
        }
    }

    private fun observeQueuePersistence() {
        queuePersistenceHelper.observeQueue(
            scope = scope,
            queue = _queue,
            currentIndex = _currentIndex,
            currentPositionMs = _currentPosition,
            isPlaying = _isPlaying,
            repeatMode = _repeatMode,
            shuffleEnabled = _shuffleMode,
            playbackSpeed = _speed,
        )
    }

    private suspend fun buildMediaItemForQueueItem(queueItem: AudioQueueItem, startPositionMs: Long = 0L): MediaItem? {
        return libraryBrowser.buildPlayableMediaItem(queueItem.id, startPositionMs)
    }

    fun play(itemId: String) {
        assertMainThread("play")

        // "Play On" routing (mirrors jellyfin-web's playbackManager.play(): when a
        // remote Jellyfin session is the active player, every play delegates to it
        // and the local engine never loads). Pause local audio so only the remote
        // session plays.
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
            val state = exoPlayer?.playbackState
            if (state != null && state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
                return
            }
        }

        crossfader.cancel()
        progressReporter.reportStopped()
        // A→B loop is track-specific; clear it when loading a new item so a
        // marker pair never applies to a different song.
        clearAbLoop()
        currentItemId = itemId
        _isLoadingItemFlag = true
        _isLoadingItem.value = true

        val player = getOrCreatePlayer()

        scope.launch {
            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrNull()

            if (detail != null) {
                _playbackError.value = null
                // Capture whether this is the cold-start restored current item
                // BEFORE overwriting _currentPlayingItemId below. On a fresh
                // launch restorePersistedQueue() loads the queue + position but
                // leaves _currentPlayingItemId null and currentItemId null, so
                // the only signal is that the tapped item is the restored
                // queue's current index AND nothing is loaded yet.
                val coldStart = currentItemId == null && _currentPlayingItemId.value == null
                val restoredCurrentId = _queue.value.getOrNull(_currentIndex.value)?.id
                val isRestoredCurrentItem = coldStart && restoredCurrentId == itemId
                val restoredPosMs = _currentPosition.value
                _currentPlayingItemId.value = itemId
                _title.value = detail.item.name
                _artist.value = detail.item.albumArtist
                    ?: detail.item.artistItems.firstOrNull()?.name
                    ?: ""
                _artistId.value = detail.item.artistItems.firstOrNull()?.id
                _album.value = detail.item.album ?: ""
                _albumArtUrl.value = playbackRepository.getImageUrl(itemId, maxWidth = 600)

                val source = detail.mediaSources.firstOrNull()
                val resumeTicks = detail.item.playbackPositionTicks ?: 0L
                // Prefer the locally-persisted resume position over the
                // server-reported ticks when resuming the restored current
                // item: it is always at least as recent as the (10 s-throttled)
                // server progress, and survives process death the server ticks
                // may not.
                val startPositionMs = when {
                    isRestoredCurrentItem && restoredPosMs > 0 -> restoredPosMs
                    resumeTicks > 0 -> resumeTicks / 10_000
                    else -> 0L
                }

                val q = _queue.value
                val currentIdx = _currentIndex.value
                val isInQueue = currentIdx >= 0 && q.getOrNull(currentIdx)?.id == itemId

                if (!isInQueue) {
                    val queueItem = AudioQueueItem(
                        id = itemId,
                        name = _title.value,
                        artist = _artist.value,
                        album = _album.value,
                        imageUrl = _albumArtUrl.value,
                        mediaSourceId = source?.id,
                        durationMs = detail.item.runTimeTicks?.let { it / 10_000 } ?: 0L,
                        normalizationGain = detail.item.normalizationGain,
                    )
                    _queue.value = _queue.value + queueItem
                    _currentIndex.value = _queue.value.lastIndex
                }

                val queueItems = _queue.value
                val playIndex = _currentIndex.value

                val clickedItem = queueItems.getOrNull(playIndex)
                if (clickedItem != null) {
                    val clickedMediaItem = buildMediaItemForQueueItem(clickedItem, startPositionMs)
                    if (clickedMediaItem != null) {
                        player.setMediaItem(clickedMediaItem, startPositionMs)
                        player.prepare()
                        player.playWhenReady = true
                    }

                    queueLoadingJob?.cancel()
                    queueLoadingJob = scope.launch(Dispatchers.IO) {
                        coroutineScope {
                            val afterJobs = (playIndex + 1 until queueItems.size).map { i ->
                                val qi = queueItems[i]
                                val cached = mediaItemCache.get(qi.id)
                                if (cached != null) {
                                    kotlinx.coroutines.CompletableDeferred<MediaItem?>(cached)
                                } else {
                                    async { queuePreWarmPermits.withPermit { buildMediaItemForQueueItem(qi) } }
                                }
                            }
                            val beforeJobs = (0 until playIndex).map { i ->
                                val qi = queueItems[i]
                                val cached = mediaItemCache.get(qi.id)
                                if (cached != null) {
                                    kotlinx.coroutines.CompletableDeferred<MediaItem?>(cached)
                                } else {
                                    async { queuePreWarmPermits.withPermit { buildMediaItemForQueueItem(qi) } }
                                }
                            }

                            val mediaItemsAfter = afterJobs.mapNotNull { it.await()?.also { mi -> mediaItemCache.put(mi.mediaId, mi) } }
                            val mediaItemsBefore = beforeJobs.mapNotNull { it.await()?.also { mi -> mediaItemCache.put(mi.mediaId, mi) } }

                            launch(Dispatchers.Main) {
                                if (exoPlayer == player) {
                                    if (mediaItemsAfter.isNotEmpty()) {
                                        player.addMediaItems(mediaItemsAfter)
                                    }
                                    if (mediaItemsBefore.isNotEmpty()) {
                                        player.addMediaItems(0, mediaItemsBefore)
                                        _currentIndex.value = playIndex
                                    }
                                }
                                queueLoadingJob = null
                            }
                        }
                    }
                }

                playbackRepository.reportPlaybackStart(
                    PlaybackStartInfo(
                        itemId = itemId,
                        sessionId = playSessionId,
                        mediaSourceId = source?.id,
                        startPositionTicks = if (startPositionMs > 0) startPositionMs * 10_000 else null,
                    )
                )

                fetchLyrics(
                    itemId = itemId,
                    artistName = detail.item.albumArtist
                        ?: detail.item.artistItems.firstOrNull()?.name,
                    trackName = detail.item.name,
                    durationSec = detail.item.runTimeTicks?.let { it / 10_000_000.0 },
                )
                effectsProcessor.applyReplayGain(detail.item.normalizationGain, _shuffleMode.value)
                startPositionTracking()
                progressReporter.start()
            } else {
                _playbackError.value = detailResult.exceptionOrNull()?.message ?: "Failed to load track"
                // Queue-only local fallback: when the server detail fetch failed
                // but a completed download exists on disk, play the local file.
                // resolveLocalSource performs no getMediaDetail round-trip, so the
                // COMPLETED classification survives even though the server call
                // failed — preserving the historical queue-only fallback.
                val local = playbackSourceResolver.resolveLocalSource(itemId)
                if (local != null) {
                    _currentPlayingItemId.value = itemId
                    _title.value = local.title
                    _artist.value = local.offlineItem?.seriesName ?: ""
                    _album.value = ""

                    val q = _queue.value
                    val currentIdx = _currentIndex.value
                    val isInQueue = currentIdx >= 0 && q.getOrNull(currentIdx)?.id == itemId

                    if (!isInQueue) {
                        val queueItem = AudioQueueItem(
                            id = itemId,
                            name = _title.value,
                            artist = _artist.value,
                            album = "",
                            imageUrl = null,
                            mediaSourceId = local.download.mediaSourceId,
                        )
                        _queue.value = _queue.value + queueItem
                        _currentIndex.value = _queue.value.lastIndex
                    }

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(itemId)
                        .setUri(local.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(_title.value)
                                .setArtist(_artist.value)
                                .build()
                        )
                        .build()

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                    startPositionTracking()
                }
            }
            _isLoadingItemFlag = false
            _isLoadingItem.value = false
        }
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
        // A fresh queue invalidates any undo history from the previous queue.
        queueUndoStack.clear()
        _queue.value = items
        _currentIndex.value = startIndex
        val item = items.getOrNull(startIndex) ?: return
        play(item.id)
    }

    override fun addToQueue(item: AudioQueueItem) {
        assertMainThread("addToQueue")
        _queue.value = _queue.value + item
        val player = exoPlayer ?: return
        scope.launch {
            buildMediaItemForQueueItem(item)?.let { mediaItem ->
                player.addMediaItem(mediaItem)
            }
        }
    }

    override fun removeFromQueue(index: Int) {
        assertMainThread("removeFromQueue")
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        if (queueLoadingJob != null) return
        val removed = q[index]
        pushUndoSnapshot(QueueUndoEvent.ItemRemoved(removed))
        val wasPlaying = index == _currentIndex.value
        _queue.value = q.toMutableList().apply { removeAt(index) }
        if (wasPlaying) {
            if (_queue.value.isNotEmpty()) {
                _currentIndex.value = _currentIndex.value.coerceAtMost(_queue.value.lastIndex)
            } else {
                _currentIndex.value = -1
            }
        } else if (index < _currentIndex.value) {
            _currentIndex.value -= 1
        }
        exoPlayer?.removeMediaItem(index)
    }

    override fun clearQueue() {
        assertMainThread("clearQueue")
        if (_queue.value.isEmpty()) return
        pushUndoSnapshot(QueueUndoEvent.QueueCleared)
        _queue.value = emptyList()
        _currentIndex.value = -1
        exoPlayer?.clearMediaItems()
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        assertMainThread("moveQueueItem")
        val q = _queue.value
        if (fromIndex < 0 || fromIndex >= q.size) return
        if (toIndex < 0 || toIndex >= q.size) return
        if (fromIndex == toIndex) return
        pushUndoSnapshot(QueueUndoEvent.ItemMoved(q[fromIndex]))
        val mutable = q.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _queue.value = mutable
        val current = _currentIndex.value
        val newIndex = when {
            current == fromIndex -> toIndex
            fromIndex < current && toIndex >= current -> current - 1
            fromIndex > current && toIndex <= current -> current + 1
            else -> current
        }
        _currentIndex.value = newIndex
        exoPlayer?.moveMediaItem(fromIndex, toIndex)
    }

    override fun skipToNext() {
        assertMainThread("skipToNext")
        if (queueLoadingJob != null) return
        val q = _queue.value
        if (q.isEmpty()) return
        crossfader.cancel()
        val next = when {
            _currentIndex.value < q.lastIndex -> _currentIndex.value + 1
            _repeatMode.value >= 1 -> 0
            else -> return
        }
        pushUndoSnapshot(QueueUndoEvent.SkippedToNext)
        _currentIndex.value = next
        exoPlayer?.seekTo(next, 0L)
    }

    override fun skipToPrevious() {
        assertMainThread("skipToPrevious")
        if (queueLoadingJob != null) return
        val q = _queue.value
        if (q.isEmpty()) return
        val player = exoPlayer ?: return
        crossfader.cancel()
        if (player.currentPosition > skipPreviousThresholdMs) {
            player.seekTo(0)
            return
        }
        val prev = when {
            _currentIndex.value > 0 -> _currentIndex.value - 1
            _repeatMode.value >= 1 -> q.lastIndex
            else -> return
        }
        pushUndoSnapshot(QueueUndoEvent.SkippedToPrevious)
        _currentIndex.value = prev
        player.seekTo(prev, 0L)
    }

    fun seekTo(positionMs: Long) {
        assertMainThread("seekTo")
        // Optimistically publish the target position so the seek-bar indicator
        // snaps to the user's touch immediately. Without this the bar only moves
        // when the position-poll loop (up to 250ms when playing, 2.5s when
        // paused) echoes the new position back, making seeking feel laggy.
        // ExoPlayer updates its reported currentPosition synchronously on
        // seekTo(), and the next poll confirms this value — so there is no
        // visible flicker, the write just front-loads the update to the frame
        // the gesture landed in.
        val clamped = positionMs.coerceAtLeast(0L)
        _currentPosition.value = clamped
        exoPlayer?.seekTo(clamped)
    }

    /**
     * Marks point A of an A→B loop at the current playback position. Clears B
     * if it would now be at or before A.
     */
    fun setAbLoopStart() {
        assertMainThread("setAbLoopStart")
        val pos = exoPlayer?.currentPosition ?: _currentPosition.value
        _abLoopStartMs.value = pos
        val end = _abLoopEndMs.value
        if (end != null && end <= pos) _abLoopEndMs.value = null
    }

    /**
     * Marks point B at the current position. Requires A to be set first and
     * the current position to be strictly after A; otherwise this is a no-op
     * (prevents an empty / inverted loop).
     */
    fun setAbLoopEnd() {
        assertMainThread("setAbLoopEnd")
        val start = _abLoopStartMs.value ?: return
        val pos = exoPlayer?.currentPosition ?: _currentPosition.value
        if (pos <= start) return
        _abLoopEndMs.value = pos
    }

    /** Clears the A→B loop markers. */
    fun clearAbLoop() {
        assertMainThread("clearAbLoop")
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
    }

    /**
     * Cycles the A→B loop UI state: nothing → set A → set B (looping) → clear.
     * Encapsulates the state machine so callers don't read-then-act.
     */
    fun cycleAbLoop() {
        assertMainThread("cycleAbLoop")
        when {
            _abLoopStartMs.value == null -> setAbLoopStart()
            _abLoopEndMs.value == null -> setAbLoopEnd()
            else -> clearAbLoop()
        }
    }

    /**
     * Captures the current queue/index/position into the undo stack and emits
     * [event] so the UI can offer an Undo affordance. Must be called on the
     * main thread immediately BEFORE the destructive mutation it guards.
     */
    private fun pushUndoSnapshot(event: QueueUndoEvent) {
        queueUndoStack.push(
            QueueSnapshot(
                queue = _queue.value,
                currentIndex = _currentIndex.value,
                positionMs = exoPlayer?.currentPosition ?: _currentPosition.value,
            ),
        )
        _undoEvents.tryEmit(event)
    }

    /**
     * Restores the queue to its state before the most recent destructive
     * operation, if any. Returns true when an undo was applied. The restore
     * re-syncs the ExoPlayer media items to the snapshot and seeks to the
     * captured position; it is a no-op while a queue load is in flight to
     * avoid racing with [playQueue].
     */
    fun undoLastQueueOperation(): Boolean {
        assertMainThread("undoLastQueueOperation")
        val snapshot = queueUndoStack.pop() ?: return false
        applyQueueSnapshot(snapshot)
        return true
    }

    private fun applyQueueSnapshot(snapshot: QueueSnapshot) {
        if (queueLoadingJob != null) return
        _queue.value = snapshot.queue
        _currentIndex.value = snapshot.currentIndex
        val player = exoPlayer
        if (player == null || snapshot.queue.isEmpty()) return
        scope.launch {
            val mediaItems = snapshot.queue.mapNotNull { qi ->
                mediaItemCache.get(qi.id) ?: buildMediaItemForQueueItem(qi)?.also { mediaItemCache.put(qi.id, it) }
            }
            if (mediaItems.isEmpty()) return@launch
            // Bail if the player was swapped/released during the async build.
            if (exoPlayer != player) return@launch
            val index = snapshot.currentIndex.coerceIn(0, mediaItems.lastIndex)
            player.setMediaItems(mediaItems, index, snapshot.positionMs)
            player.prepare()
        }
    }

    fun seekByDelta(deltaMs: Long) {
        assertMainThread("seekByDelta")
        val player = exoPlayer ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    fun togglePlayPause() {
        assertMainThread("togglePlayPause")
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun changePlaybackSpeed(value: Float) {
        assertMainThread("changePlaybackSpeed")
        _speed.value = value
        val pitchMultiplier = if (effectsProcessor.pitchSemitones.value == 0f) 1.0f else {
            2.0f.pow(effectsProcessor.pitchSemitones.value / 12.0f)
        }
        exoPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(value, pitchMultiplier)
        crossfader.setPlaybackSpeed(value)
    }

    override fun toggleShuffle() {
        assertMainThread("toggleShuffle")
        val wasShuffled = _shuffleMode.value
        _shuffleMode.value = !wasShuffled
        val player = exoPlayer ?: return

        if (_shuffleMode.value) {
            val q = _queue.value
            val curIdx = _currentIndex.value
            unshuffledQueue = q
            unshuffledIndex = curIdx
            if (q.size <= 1) return
            val current = q.getOrNull(curIdx)
            val others = q.filterIndexed { i, _ -> i != curIdx }.toMutableList()
            others.shuffle()
            val newQueue = if (current != null) listOf(current) + others else others
            _queue.value = newQueue
            _currentIndex.value = 0
            scope.launch {
                val mediaItems = newQueue.mapNotNull { qi ->
                    mediaItemCache.get(qi.id) ?: buildMediaItemForQueueItem(qi)?.also {
                        mediaItemCache.put(qi.id, it)
                    }
                }
                if (mediaItems.isNotEmpty() && exoPlayer == player) {
                    val currentPos = player.currentPosition
                    player.setMediaItems(mediaItems, 0, currentPos)
                    player.prepare()
                }
            }
        } else {
            val currentItemId = _currentPlayingItemId.value
            val original = unshuffledQueue
            if (original.isNotEmpty()) {
                _queue.value = original
                val restoreIndex = original.indexOfFirst { it.id == currentItemId }.coerceAtLeast(0)
                _currentIndex.value = restoreIndex
                unshuffledQueue = emptyList()
                unshuffledIndex = -1
                scope.launch {
                    val mediaItems = original.mapNotNull { qi ->
                        mediaItemCache.get(qi.id) ?: buildMediaItemForQueueItem(qi)?.also {
                            mediaItemCache.put(qi.id, it)
                        }
                    }
                    if (mediaItems.isNotEmpty() && exoPlayer == player) {
                        val currentPos = player.currentPosition
                        player.setMediaItems(mediaItems, restoreIndex, currentPos)
                        player.prepare()
                    }
                }
            }
        }
    }

    override fun cycleRepeatMode() {
        assertMainThread("cycleRepeatMode")
        val nextMode = (_repeatMode.value + 1) % 3
        setRepeatMode(nextMode)
    }

    /**
     * Set the repeat mode explicitly.
     * @param mode 0 = RepeatNone, 1 = RepeatAll, 2 = RepeatOne.
     */
    override fun setRepeatMode(mode: Int) {
        assertMainThread("setRepeatMode")
        val coerced = mode.coerceIn(0, 2)
        _repeatMode.value = coerced
        exoPlayer?.repeatMode = getExoPlayerRepeatMode(coerced)
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
        if (_shuffleMode.value == enabled) return
        toggleShuffle()
    }

    /**
     * Pause the audio player if a session is active.
     */
    fun pause() {
        assertMainThread("pause")
        exoPlayer?.takeIf { it.isPlaying }?.pause()
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
        if (queueLoadingJob != null) return
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        crossfader.cancel()
        _currentIndex.value = index
        val player = exoPlayer ?: return
        player.seekTo(index, 0L)
        if (!player.isPlaying) {
            player.play()
        }
    }

    override fun toggleNightMode() {
        effectsProcessor.toggleNightMode()
    }

    override fun toggleDialogueBoost() {
        effectsProcessor.toggleDialogueBoost()
    }

    override fun setDialogueBoostStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        effectsProcessor.setDialogueBoostStrength(strength)
    }

    override fun setNightModeStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        effectsProcessor.setNightModeStrength(strength)
    }

    override fun toggleEqualizer() {
        effectsProcessor.toggleEqualizer()
    }

    override fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        effectsProcessor.setEqualizerBand(bandIndex, levelDb)
    }

    override fun resetEqualizer() {
        effectsProcessor.resetEqualizer()
    }

    override fun setNightModeParams(volume: Float, gain: Int) {
        effectsProcessor.setNightModeParams(volume, gain)
    }

    fun setSkipPreviousThreshold(ms: Long) {
        skipPreviousThresholdMs = ms
    }

    override fun setReplayGainMode(mode: AudioNormalizationMode) {
        val currentIdx = _currentIndex.value
        val q = _queue.value
        val normalizationGain = if (currentIdx in q.indices) q[currentIdx].normalizationGain else null
        effectsProcessor.setReplayGainMode(mode, normalizationGain, _shuffleMode.value)
    }

    override fun setReplayGainPreAmpDb(db: Float) {
        val currentIdx = _currentIndex.value
        val q = _queue.value
        val normalizationGain = if (currentIdx in q.indices) q[currentIdx].normalizationGain else null
        effectsProcessor.setReplayGainPreAmpDb(db, normalizationGain, _shuffleMode.value)
    }

    override fun setChannelMix(mode: ChannelMixMode, enabled: Boolean) {
        effectsProcessor.setChannelMix(mode, enabled)
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    override fun setEqualizerPreset(preset: EqualizerPreset) {
        effectsProcessor.setEqualizerPreset(preset)
    }

    override fun toggleBassBoost() {
        effectsProcessor.toggleBassBoost()
    }

    override fun setBassBoostStrength(strength: EffectStrength) {
        effectsProcessor.setBassBoostStrength(strength)
    }

    override fun toggleVirtualizer() {
        effectsProcessor.toggleVirtualizer()
    }

    override fun setVirtualizerStrength(strength: Int) {
        effectsProcessor.setVirtualizerStrength(strength)
    }

    override fun setReverbPreset(preset: ReverbPreset) {
        effectsProcessor.setReverbPreset(preset)
    }

    override fun setLrBalance(balance: Float) {
        effectsProcessor.setLrBalance(balance)
    }

    override fun setPitchSemitones(semitones: Float) {
        effectsProcessor.setPitchSemitones(semitones, _speed.value)
    }

    override fun setAutoEqByGenre(enabled: Boolean) {
        effectsProcessor.setAutoEqByGenre(enabled)
    }

    override fun applyAutoEqForGenre(genres: List<String>?) {
        effectsProcessor.applyAutoEqForGenre(genres)
    }

    override fun enableVisualizer(enabled: Boolean) {
        effectsProcessor.enableVisualizer(enabled)
    }

    /**
     * Called by [androidx.media3.common.Player.Listener.onPlaybackStateChanged] when the
     * engine reaches `STATE_ENDED`.
     *
     * ExoPlayer owns repeat behaviour directly via its `repeatMode` property (mirrored from
     * [_repeatMode] in the collector at line 378). Under REPEAT_MODE_ALL or REPEAT_MODE_ONE
     * the player never reaches `STATE_ENDED`, so this callback only fires under mode 0
     * (RepeatNone). The branches that previously handled modes 1 and 2 here were therefore
     * dead code and have been removed.
     *
     * Auto-advance under mode 0 is handled separately by `onMediaItemTransitioned` (see
     * [onTrackTransitioned]); this method just clears the local `_isPlaying` flag so the UI
     * reflects that nothing is currently playing.
     */
    private fun onTrackEnded() {
        _isPlaying.value = false
        // Arm-and-fire hook for the "End of episode" sleep timer. triggerEndOfEpisode() is a
        // no-op unless the timer is in end-of-episode mode and active, so this is safe to call
        // on every track end. When it fires, it invokes onTimerExpired (set by the player VM to
        // togglePlayPause), pausing playback after the current track.
        sleepTimerManager.triggerEndOfEpisode()
    }

    private fun onTrackTransitioned() {
        val player = exoPlayer ?: return
        val currentMediaId = player.currentMediaItem?.mediaId
        val queueItems = _queue.value
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
            val prevPosTicks = if (_currentPosition.value > 0) _currentPosition.value * 10_000 else _duration.value * 10_000

            _currentIndex.value = targetIndex
            val nextItem = queueItems[targetIndex]
            currentItemId = nextItem.id
            _currentPlayingItemId.value = nextItem.id
            _title.value = nextItem.name
            _artist.value = nextItem.artist
            _album.value = nextItem.album ?: ""
            _albumArtUrl.value = nextItem.imageUrl ?: ""

            effectsProcessor.applyReplayGain(nextItem.normalizationGain, _shuffleMode.value)

            scope.launch {
                progressReporter.reportStopped(
                    itemId = prevItemId,
                    sessionId = prevSessionId,
                    positionTicks = prevPosTicks,
                )

                val detail = mediaRepository.getMediaDetail(nextItem.id)
                detail.onSuccess { d ->
                    fetchLyrics(
                        itemId = nextItem.id,
                        artistName = d.item.albumArtist ?: d.item.artistItems.firstOrNull()?.name,
                        trackName = d.item.name,
                        durationSec = d.item.runTimeTicks?.let { it / 10_000_000.0 },
                    )
                    // Auto-EQ-by-genre: previously the pref toggle only
                    // persisted the flag and applyAutoEqForGenre was never
                    // invoked on transitions, leaving the feature dead beyond
                    // the first manual preset pick. applyAutoEqForGenre no-ops
                    // when autoEqByGenre is disabled or no genre matches.
                    effectsProcessor.applyAutoEqForGenre(d.item.genres)
                }

                playbackRepository.reportPlaybackStart(
                    PlaybackStartInfo(
                        itemId = nextItem.id,
                        sessionId = playSessionId,
                        mediaSourceId = nextItem.mediaSourceId,
                    )
                )
            }
        }
    }

    private suspend fun onCrossfadeTransition(secondary: ExoPlayer, nextIndex: Int, nextItem: AudioQueueItem) {
        val prevItemId = currentItemId
        val prevSessionId = playSessionId
        val prevPosTicks = if (_currentPosition.value > 0) _currentPosition.value * 10_000 else _duration.value * 10_000

        progressReporter.reportStopped(
            itemId = prevItemId,
            sessionId = prevSessionId,
            positionTicks = prevPosTicks,
        )

        exoPlayer = secondary

        _currentIndex.value = nextIndex
        currentItemId = nextItem.id
        _currentPlayingItemId.value = nextItem.id
        _title.value = nextItem.name
        _artist.value = nextItem.artist
        _album.value = nextItem.album ?: ""
        _albumArtUrl.value = nextItem.imageUrl ?: ""

        mediaSession?.release()
        val newSession = MediaSession.Builder(context, secondary)
            .setId(playSessionId)
            .build()
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

        val queueItems = _queue.value
        if (queueItems.size > 1) {
            val itemsAfter = mutableListOf<MediaItem>()
            val itemsBefore = mutableListOf<MediaItem>()
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                for (i in (nextIndex + 1) until queueItems.size) {
                    val qi = queueItems[i]
                    val cached = mediaItemCache.get(qi.id)
                    val mediaItem = cached ?: buildMediaItemForQueueItem(qi)
                    if (mediaItem != null) {
                        itemsAfter.add(mediaItem)
                        mediaItemCache.put(qi.id, mediaItem)
                    }
                }
                for (i in 0 until nextIndex) {
                    val qi = queueItems[i]
                    val cached = mediaItemCache.get(qi.id)
                    val mediaItem = cached ?: buildMediaItemForQueueItem(qi)
                    if (mediaItem != null) {
                        itemsBefore.add(mediaItem)
                        mediaItemCache.put(qi.id, mediaItem)
                    }
                }
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (exoPlayer == secondary) {
                        if (itemsAfter.isNotEmpty()) {
                            secondary.addMediaItems(itemsAfter)
                        }
                        if (itemsBefore.isNotEmpty()) {
                            secondary.addMediaItems(0, itemsBefore)
                        }
                    }
                }
            }
        }

        playbackRepository.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = nextItem.id,
                sessionId = playSessionId,
                mediaSourceId = nextItem.mediaSourceId,
            )
        )
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
            val q = _queue.value
            if (nextIndex !in q.indices) return@launch
            _currentIndex.value = nextIndex
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

    fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        lyricsManager.searchLyrics(query, callback)
    }

    fun applyLyrics(lrcLibId: Long) {
        lyricsManager.applyLyrics(lrcLibId, currentItemId)
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var lastPosition = 0L
            var lastDuration = 0L
            var bandwidthSampleTick = 0
            var lastBufferedPosition = 0L
            while (true) {
                val player = exoPlayer
                if (player == null) {
                    // No player yet — wait briefly and re-check rather than busy-looping.
                    delay(250)
                    continue
                }
                // While paused, the position/duration/lyrics/crossfade work below
                // is a no-op (position doesn't move, crossfader only runs when
                // playing, lyrics index is stable). Suspend reactively on the
                // play→resume edge instead of polling at 4 Hz for the whole
                // paused session — mirrors the EnginePositionTicker pattern on
                // the video side and removes continuous background CPU/battery.
                if (!player.isPlaying) {
                    delay(POSITION_PAUSED_RECHECK_MS)
                    continue
                }

                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                // A→B loop: when both markers are set and we reach B while
                // playing, jump back to A.
                val abEnd = _abLoopEndMs.value
                val abStart = _abLoopStartMs.value
                if (abEnd != null && abStart != null && player.isPlaying && pos >= abEnd) {
                    player.seekTo(abStart)
                }
                if (pos != lastPosition) {
                    _currentPosition.value = pos
                    lastPosition = pos
                }
                if (dur != lastDuration) {
                    _duration.value = dur
                    lastDuration = dur
                }
                if (lyricsManager.lyrics.value.isNotEmpty()) {
                    lyricsManager.updateCurrentLyricIndex(_currentPosition.value)
                }

                if (_crossfadeDurationMs.value > 0 && player.isPlaying && _repeatMode.value != 2) {
                    crossfader.maybeStart()
                }

                bandwidthSampleTick++
                if (bandwidthSampleTick >= 20) {
                    bandwidthSampleTick = 0
                    val buffered = player.bufferedPosition
                    val deltaMs = (buffered - lastBufferedPosition).coerceAtLeast(0L)
                    lastBufferedPosition = buffered
                    if (deltaMs > 0) {
                        val assumedKbps = currentAudioBitrateTier.value.targetKbps
                        val estimatedBytes = (assumedKbps.toLong() * deltaMs) / 8L / 1000L
                        if (estimatedBytes > 0) {
                            bandwidthMonitor.addSample(estimatedBytes, deltaMs)
                        }
                    }
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopAndRelease() {
        audioPrefetchEngine.stop()
        crossfader.cancel()

        val player = exoPlayer
        val itemId = currentItemId
        val sid = playSessionId
        val pos = player?.currentPosition?.let { it * 10_000 } ?: 0L

        positionJob?.cancel()
        progressReporter.cancel()
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

        currentItemId = null
        _currentPlayingItemId.value = null
        _isPlaying.value = false
        _title.value = ""
        _artist.value = ""
        _album.value = ""
        _albumArtUrl.value = ""
        _currentPosition.value = 0L
        _duration.value = 0L
        lyricsManager.reset()
        playSessionId = UUID.randomUUID().toString()

        if (player != null && itemId != null && pos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(
                    itemId = itemId,
                    sessionId = sid,
                    positionTicks = pos,
                )
            }
        }
    }
}
