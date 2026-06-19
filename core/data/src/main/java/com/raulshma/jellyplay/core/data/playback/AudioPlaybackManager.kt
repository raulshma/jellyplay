package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
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
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val sessionManager: PlaybackSessionManager,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
    private val audioSettingsStore: com.raulshma.jellyplay.core.datastore.AudioPlaybackSettingsStore,
    private val queuePersistenceHelper: QueuePersistenceHelper,
    private val bandwidthMonitor: com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor,
    private val adaptiveBitrateSelector: com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector,
    private val bandwidthInterceptor: com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor,
    private val lyricsManager: AudioLyricsManager,
    private val effectsProcessor: AudioEffectsProcessor,
) : AudioEffectsManager, AudioQueueManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var currentPreferences = com.raulshma.jellyplay.core.model.UserPreferences()

    private val libraryBrowser = AudioLibraryBrowser(
        scope = scope,
        mediaRepository = mediaRepository,
        downloadRepository = downloadRepository,
        playbackRepository = playbackRepository,
        streamingQualityProvider = { currentPreferences.streamingQuality },
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
        downloadRepository = downloadRepository,
        playbackRepository = playbackRepository,
        repeatModeProvider = { _repeatMode.value },
        crossfadeDurationMsProvider = { _crossfadeDurationMs.value },
        isCrossfadingProvider = { _isCrossfading.value },
        isCrossfadingSetter = { _isCrossfading.value = it },
        exoPlayerProvider = { exoPlayer },
        queueSizeProvider = { _queue.value.size },
        onGetNextItem = { idx -> _queue.value.getOrNull(idx) },
        speedProvider = { _speed.value },
        audioBufferProvider = {
            val buf = currentPreferences.audioPreloadBufferSize
            buf.minBufferMs to buf.maxBufferMs
        },
        onCrossfadeTransition = { secondary, nextIndex, nextItem ->
            onCrossfadeTransition(secondary, nextIndex, nextItem)
        },
    )

    @Volatile
    var remoteSessionActive: Boolean = false
        internal set

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist.asStateFlow()

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

    var skipPreviousThresholdMs = 3_000L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
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
            preferencesStore.preferences.collect { prefs ->
                val prevVisualizer = currentPreferences.audioVisualizerEnabled
                val prevPreset = currentPreferences.equalizerPreset
                val prevBalance = currentPreferences.lrBalance
                val prevPitch = currentPreferences.pitchSemitones
                val prevEqEnabled = currentPreferences.equalizerEnabled
                val prevBassEnabled = currentPreferences.bassBoostEnabled
                val prevBassStrength = currentPreferences.bassBoostStrength
                val prevVirtEnabled = currentPreferences.virtualizerEnabled
                val prevVirtStrength = currentPreferences.virtualizerStrength
                val prevDialogueEnabled = currentPreferences.dialogueBoostEnabled
                val prevDialogueStrength = currentPreferences.dialogueBoostStrength
                val prevNightEnabled = currentPreferences.nightModeEnabled
                val prevNightStrength = currentPreferences.nightModeStrength
                val prevReverb = currentPreferences.reverbPreset

                currentPreferences = prefs

                if (prefs.audioVisualizerEnabled != prevVisualizer) {
                    enableVisualizer(prefs.audioVisualizerEnabled)
                }
                if (prefs.equalizerPreset != prevPreset) {
                    setEqualizerPreset(prefs.equalizerPreset)
                }
                if (prefs.lrBalance != prevBalance) {
                    setLrBalance(prefs.lrBalance)
                }
                if (prefs.pitchSemitones != prevPitch) {
                    setPitchSemitones(prefs.pitchSemitones)
                }
                // Strengths must be applied before their enabled flag so the
                // effect uses the correct value when it attaches.
                if (prefs.bassBoostStrength != prevBassStrength) {
                    effectsProcessor.setBassBoostStrength(prefs.bassBoostStrength)
                }
                if (prefs.virtualizerStrength != prevVirtStrength) {
                    effectsProcessor.setVirtualizerStrength(prefs.virtualizerStrength)
                }
                if (prefs.dialogueBoostStrength != prevDialogueStrength) {
                    effectsProcessor.setDialogueBoostStrength(prefs.dialogueBoostStrength)
                }
                if (prefs.nightModeStrength != prevNightStrength) {
                    effectsProcessor.setNightModeStrength(prefs.nightModeStrength)
                }
                // Restore effect-enabled state from persisted preferences so
                // effects survive process restart. Each setter updates the flag
                // and re-applies (a no-op when no player exists yet; the flags
                // are honored when onAudioSessionIdChanged attaches effects).
                if (prefs.equalizerEnabled != prevEqEnabled) {
                    effectsProcessor.setEqualizerEnabled(prefs.equalizerEnabled)
                }
                if (prefs.bassBoostEnabled != prevBassEnabled) {
                    effectsProcessor.setBassBoostEnabled(prefs.bassBoostEnabled)
                }
                if (prefs.virtualizerEnabled != prevVirtEnabled) {
                    effectsProcessor.setVirtualizerEnabled(prefs.virtualizerEnabled)
                }
                if (prefs.dialogueBoostEnabled != prevDialogueEnabled) {
                    effectsProcessor.setDialogueBoostEnabled(prefs.dialogueBoostEnabled)
                }
                if (prefs.nightModeEnabled != prevNightEnabled) {
                    effectsProcessor.setNightModeEnabled(prefs.nightModeEnabled)
                }
                if (prefs.reverbPreset != prevReverb) {
                    effectsProcessor.setReverbPreset(prefs.reverbPreset)
                }
            }
        }
        scope.launch {
            _repeatMode.collect { mode ->
                exoPlayer?.repeatMode = getExoPlayerRepeatMode(mode)
            }
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
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(effectsProcessor.replayGainProcessor, effectsProcessor.balanceProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                currentPreferences.audioPreloadBufferSize.minBufferMs,
                currentPreferences.audioPreloadBufferSize.maxBufferMs,
                1_000,
                3_000
            )
            .setTargetBufferBytes(-1)
            .build()

        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
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
        if (currentItemId == itemId) {
            if (_isLoadingItemFlag) return
            val state = exoPlayer?.playbackState
            if (state != null && state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
                return
            }
        }

        crossfader.cancel()
        progressReporter.reportStopped()
        currentItemId = itemId
        _isLoadingItemFlag = true
        _isLoadingItem.value = true

        val player = getOrCreatePlayer()

        scope.launch {
            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrNull()

            if (detail != null) {
                _playbackError.value = null
                _currentPlayingItemId.value = itemId
                _title.value = detail.item.name
                _artist.value = detail.item.albumArtist
                    ?: detail.item.artistItems.firstOrNull()?.name
                    ?: ""
                _album.value = detail.item.album ?: ""
                _albumArtUrl.value = playbackRepository.getImageUrl(itemId, maxWidth = 600)

                val source = detail.mediaSources.firstOrNull()
                val resumeTicks = detail.item.playbackPositionTicks ?: 0L
                val startPositionMs = if (resumeTicks > 0) resumeTicks / 10_000 else 0L

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
                                    async { buildMediaItemForQueueItem(qi) }
                                }
                            }
                            val beforeJobs = (0 until playIndex).map { i ->
                                val qi = queueItems[i]
                                val cached = mediaItemCache.get(qi.id)
                                if (cached != null) {
                                    kotlinx.coroutines.CompletableDeferred<MediaItem?>(cached)
                                } else {
                                    async { buildMediaItemForQueueItem(qi) }
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
                val localDownload = downloadRepository.getDownloadByMediaItemId(itemId)
                if (localDownload != null && localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
                    val file = java.io.File(localDownload.downloadPath)
                    if (file.exists()) {
                        val offlineItem = offlineRepository.getOfflineItem(itemId)
                        _currentPlayingItemId.value = itemId
                        _title.value = offlineItem?.name ?: localDownload.name
                        _artist.value = offlineItem?.seriesName ?: ""
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
                                mediaSourceId = localDownload.mediaSourceId,
                            )
                            _queue.value = _queue.value + queueItem
                            _currentIndex.value = _queue.value.lastIndex
                        }

                        val mediaItem = MediaItem.Builder()
                            .setMediaId(itemId)
                            .setUri(Uri.fromFile(file).toString())
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
        val wasPlaying = index == _currentIndex.value
        _queue.value = q.toMutableList().apply { removeAt(index) }.toList()
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
        _currentIndex.value = prev
        player.seekTo(prev, 0L)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekByDelta(deltaMs: Long) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun changePlaybackSpeed(value: Float) {
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
        exoPlayer?.takeIf { it.isPlaying }?.pause()
    }

    /**
     * Resume the audio player if a session is active.
     */
    fun resume() {
        exoPlayer?.takeIf { !it.isPlaying }?.play()
    }

    /**
     * Set the player volume in [0f, 1f]. Also mirrors the value onto the
     * system [android.media.AudioManager.STREAM_MUSIC] stream so remote
     * "SetVolume" is actually audible (the player software gain alone is
     * silent when the system stream is muted or at zero).
     */
    fun setVolume(volume: Float) {
        val pct = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = pct
        crossfader.setVolume(pct)
        MediaStreamVolume.setNormalized(context, pct)
    }

    /**
     * Convenience: 5% increment.
     */
    fun increaseVolume() {
        val current = exoPlayer?.volume ?: 1f
        setVolume(current + 0.05f)
    }

    /**
     * Convenience: 5% decrement.
     */
    fun decreaseVolume() {
        val current = exoPlayer?.volume ?: 1f
        setVolume(current - 0.05f)
    }

    /**
     * Mute / unmute the audio player. Uses an internal flag so [toggleMute]
     * can restore the prior volume.
     */
    private var preMuteVolume: Float = 1f

    fun setMuted(muted: Boolean) {
        val current = exoPlayer?.volume ?: 1f
        if (muted) {
            preMuteVolume = if (current > 0f) current else 1f
            setVolume(0f)
        } else {
            setVolume(preMuteVolume.coerceIn(0f, 1f))
        }
    }

    fun toggleMute() {
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

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

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

    private fun onTrackEnded() {
        when {
            _repeatMode.value == 2 -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            _repeatMode.value == 1 -> {
                val q = _queue.value
                if (q.size > 1) {
                    _currentIndex.value = 0
                    exoPlayer?.seekTo(0, 0L)
                    exoPlayer?.play()
                } else {
                    exoPlayer?.seekTo(0)
                    exoPlayer?.play()
                }
            }
            else -> {
                _isPlaying.value = false
            }
        }
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
                progressReporter.reportStopped()

                val detail = mediaRepository.getMediaDetail(nextItem.id)
                detail.onSuccess { d ->
                    fetchLyrics(
                        itemId = nextItem.id,
                        artistName = d.item.albumArtist ?: d.item.artistItems.firstOrNull()?.name,
                        trackName = d.item.name,
                        durationSec = d.item.runTimeTicks?.let { it / 10_000_000.0 },
                    )
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
                exoPlayer?.let { player ->
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
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
            }
            delay(250)
            }
        }
    }

    fun stopAndRelease() {
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
