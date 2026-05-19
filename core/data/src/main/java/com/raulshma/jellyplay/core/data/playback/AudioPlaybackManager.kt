package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AudioQueueItem(
    val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val imageUrl: String?,
    val mediaSourceId: String?,
    val durationMs: Long = 0L,
)

@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val sessionManager: PlaybackSessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var crossfadePlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val dialogueBoost = DialogueBoostHelper()
    private val equalizerHelper = EqualizerHelper()

    private var _dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.MODERATE
    private var _nightModeStrength = com.raulshma.jellyplay.core.model.EffectStrength.MODERATE

    private val _gaplessEnabled = MutableStateFlow(true)
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(0L)
    val crossfadeDurationMs: StateFlow<Long> = _crossfadeDurationMs.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private var crossfadeJob: Job? = null

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
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioQueueItem>>(emptyList())
    val queue: StateFlow<List<AudioQueueItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentPlayingItemId = MutableStateFlow<String?>(null)
    val currentPlayingItemId: StateFlow<String?> = _currentPlayingItemId.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyrics: StateFlow<List<LyricsLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _lyricsSource = MutableStateFlow(LyricsSource.UNKNOWN)
    val lyricsSource: StateFlow<LyricsSource> = _lyricsSource.asStateFlow()

    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()

    private val _nightModeEnabled = MutableStateFlow(false)
    val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private val _dialogueBoostEnabled = MutableStateFlow(false)
    val dialogueBoostEnabled: StateFlow<Boolean> = _dialogueBoostEnabled.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(com.raulshma.jellyplay.core.model.EqualizerSettings())
    val equalizerSettings: StateFlow<com.raulshma.jellyplay.core.model.EqualizerSettings> = _equalizerSettings.asStateFlow()

    private val nightModeVolumeForStrength: Float
        get() = when (_nightModeStrength) {
            com.raulshma.jellyplay.core.model.EffectStrength.LOW -> 0.7f
            com.raulshma.jellyplay.core.model.EffectStrength.MODERATE -> 0.4f
            com.raulshma.jellyplay.core.model.EffectStrength.HIGH -> 0.2f
        }

    private val nightModeGainForStrength: Int
        get() = when (_nightModeStrength) {
            com.raulshma.jellyplay.core.model.EffectStrength.LOW -> 1500
            com.raulshma.jellyplay.core.model.EffectStrength.MODERATE -> 3000
            com.raulshma.jellyplay.core.model.EffectStrength.HIGH -> 4500
        }

    var nightModeVolume = 0.4f
    var nightModeGain = 1200
    var skipPreviousThresholdMs = 3_000L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                onTrackTransitionedAuto()
            }
        }
    }

    val hasActiveSession: Boolean
        get() = exoPlayer != null && currentItemId != null

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        if (enabled) {
            _crossfadeDurationMs.value = 0L
            cancelCrossfade()
        }
    }

    fun setCrossfadeDurationMs(ms: Long) {
        _crossfadeDurationMs.value = ms
        if (ms > 0) {
            _gaplessEnabled.value = false
        } else {
            _gaplessEnabled.value = true
            cancelCrossfade()
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

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setPauseAtEndOfMediaItems(false)
            .build()
        player.addListener(playerListener)

        exoPlayer = player
        val session = MediaSession.Builder(context, player)
            .setId(playSessionId)
            .build()
        mediaSession = session
        sessionManager.setActiveSession(session)

        return player
    }

    private fun createCrossfadePlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private suspend fun buildMediaItemForQueueItem(queueItem: AudioQueueItem, startPositionMs: Long = 0L): MediaItem? {
        val detail = mediaRepository.getMediaDetail(queueItem.id).getOrNull() ?: return null
        val source = detail.mediaSources.firstOrNull()
        val localDownload = downloadRepository.getDownloadByMediaItemId(queueItem.id)
        val file = localDownload?.let { dl ->
            java.io.File(dl.downloadPath).takeIf { f -> f.exists() }
        }
        val url = if (localDownload != null && file != null &&
            localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED
        ) {
            Uri.fromFile(file).toString()
        } else {
            playbackRepository.getStreamUrl(queueItem.id, source?.id ?: "", if (startPositionMs > 0) startPositionMs * 10_000 else 0L)
        }
        val artUri = Uri.parse(playbackRepository.getImageUrl(queueItem.id, maxWidth = 600))
        return MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(detail.item.name)
                    .setArtist(detail.item.albumArtist ?: detail.item.artistItems.firstOrNull()?.name ?: "")
                    .setAlbumTitle(detail.item.album ?: "")
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    fun play(itemId: String) {
        if (currentItemId == itemId) {
            val state = exoPlayer?.playbackState
            if (state != null && state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
                return
            }
        }

        cancelCrossfade()
        reportCurrentItemStopped()

        val player = getOrCreatePlayer()

        scope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    currentItemId = itemId
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
                        )
                        _queue.value = _queue.value + queueItem
                        _currentIndex.value = _queue.value.lastIndex
                    }

                    val queueItems = _queue.value
                    val playIndex = _currentIndex.value

                    val mediaItems = mutableListOf<MediaItem>()
                    for (i in queueItems.indices) {
                        val qi = queueItems[i]
                        val startMs = if (i == playIndex) startPositionMs else 0L
                        buildMediaItemForQueueItem(qi, startMs)?.let { mediaItems.add(it) }
                    }

                    player.setMediaItems(mediaItems, playIndex, startPositionMs)
                    player.prepare()
                    player.playWhenReady = true

                    playbackRepository.reportPlaybackStart(
                        PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = playSessionId,
                            mediaSourceId = source?.id,
                        )
                    )

                    fetchLyrics(
                        itemId = itemId,
                        artistName = detail.item.albumArtist
                            ?: detail.item.artistItems.firstOrNull()?.name,
                        trackName = detail.item.name,
                        durationSec = detail.item.runTimeTicks?.let { it / 10_000_000.0 },
                    )
                    startPositionTracking()
                    startProgressReporting()
                }
        }
    }

    fun playQueue(items: List<AudioQueueItem>, startIndex: Int = 0) {
        _queue.value = items
        _currentIndex.value = startIndex
        val item = items.getOrNull(startIndex) ?: return
        play(item.id)
    }

    fun addToQueue(item: AudioQueueItem) {
        _queue.value = _queue.value + item
        val player = exoPlayer ?: return
        scope.launch {
            buildMediaItemForQueueItem(item)?.let { mediaItem ->
                player.addMediaItem(mediaItem)
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
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

    fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        cancelCrossfade()
        val next = when {
            _shuffleMode.value -> q.indices.filter { it != _currentIndex.value }.randomOrNull() ?: return
            _currentIndex.value < q.lastIndex -> _currentIndex.value + 1
            _repeatMode.value >= 1 -> 0
            else -> return
        }
        _currentIndex.value = next
        exoPlayer?.seekTo(next, 0L)
    }

    fun skipToPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        val player = exoPlayer ?: return
        cancelCrossfade()
        if (player.currentPosition > skipPreviousThresholdMs) {
            player.seekTo(0)
            return
        }
        val prev = when {
            _shuffleMode.value -> q.indices.filter { it != _currentIndex.value }.randomOrNull() ?: return
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

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun changePlaybackSpeed(value: Float) {
        _speed.value = value
        exoPlayer?.setPlaybackSpeed(value)
        crossfadePlayer?.setPlaybackSpeed(value)
    }

    fun toggleShuffle() {
        _shuffleMode.value = !_shuffleMode.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
    }

    fun playFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        cancelCrossfade()
        _currentIndex.value = index
        val player = exoPlayer ?: return
        player.seekTo(index, 0L)
        if (!player.isPlaying) {
            player.play()
        }
    }

    fun toggleNightMode() {
        _nightModeEnabled.value = !_nightModeEnabled.value
        applyNightMode()
    }

    fun toggleDialogueBoost() {
        _dialogueBoostEnabled.value = !_dialogueBoostEnabled.value
        applyDialogueBoost()
    }

    fun setDialogueBoostStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        _dialogueBoostStrength = strength
        dialogueBoost.setStrength(strength)
        if (_dialogueBoostEnabled.value) applyDialogueBoost()
    }

    fun setNightModeStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        _nightModeStrength = strength
        if (_nightModeEnabled.value) applyNightMode()
    }

    fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
        applyEqualizer()
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val newLevels = _equalizerSettings.value.bandLevels.toMutableList()
        newLevels[bandIndex] = levelDb
        _equalizerSettings.value = com.raulshma.jellyplay.core.model.EqualizerSettings(newLevels)
        equalizerHelper.setSettings(_equalizerSettings.value)
    }

    fun resetEqualizer() {
        _equalizerSettings.value = com.raulshma.jellyplay.core.model.EqualizerSettings()
        equalizerHelper.setSettings(_equalizerSettings.value)
    }

    fun setNightModeParams(volume: Float, gain: Int) {
        nightModeVolume = volume
        nightModeGain = gain
        if (_nightModeEnabled.value) applyNightMode()
    }

    fun setSkipPreviousThreshold(ms: Long) {
        skipPreviousThresholdMs = ms
    }

    private fun applyNightMode() {
        val player = exoPlayer ?: return
        if (_nightModeEnabled.value) {
            player.volume = nightModeVolumeForStrength
            attachLoudnessEnhancer(player.audioSessionId, nightModeGainForStrength)
        } else {
            player.volume = 1.0f
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }
    }

    private fun applyDialogueBoost() {
        val player = exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        dialogueBoost.attach(audioSessionId)
        dialogueBoost.setStrength(_dialogueBoostStrength)
        dialogueBoost.setEnabled(_dialogueBoostEnabled.value)
    }

    private fun applyEqualizer() {
        val player = exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizerHelper.attach(audioSessionId)
        equalizerHelper.setEnabled(_equalizerEnabled.value)
        equalizerHelper.setSettings(_equalizerSettings.value)
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int, gain: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudnessEnhancer?.release()
        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    private fun onTrackEnded() {
        when {
            _repeatMode.value == 2 -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            else -> {
                _isPlaying.value = false
            }
        }
    }

    private fun onTrackTransitionedAuto() {
        val player = exoPlayer ?: return
        val nextIndex = player.currentMediaItemIndex
        if (nextIndex >= 0 && nextIndex < _queue.value.size) {
            _currentIndex.value = nextIndex
            val nextItem = _queue.value[nextIndex]
            currentItemId = nextItem.id
            _currentPlayingItemId.value = nextItem.id
            _title.value = nextItem.name
            _artist.value = nextItem.artist
            _album.value = nextItem.album ?: ""
            _albumArtUrl.value = nextItem.imageUrl ?: ""

            scope.launch {
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

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        _isCrossfading.value = false
        crossfadePlayer?.let { player ->
            player.stop()
            player.release()
        }
        crossfadePlayer = null
        exoPlayer?.volume = 1.0f
    }

    private fun startCrossfadeIfNeeded() {
        val crossfadeMs = _crossfadeDurationMs.value
        if (crossfadeMs <= 0L || _repeatMode.value == 2) return

        val player = exoPlayer ?: return
        val duration = player.duration
        val position = player.currentPosition
        val timeRemaining = duration - position

        if (timeRemaining <= crossfadeMs && timeRemaining > 0) {
            val nextIndex = player.currentMediaItemIndex + 1
            if (nextIndex >= _queue.value.size && _repeatMode.value < 1) return
            prepareAndCrossfade(nextIndex, crossfadeMs)
        }
    }

    private fun prepareAndCrossfade(targetIndex: Int, crossfadeMs: Long) {
        if (_isCrossfading.value) return

        val actualIndex = if (targetIndex >= _queue.value.size) {
            if (_repeatMode.value >= 1) 0 else return
        } else {
            targetIndex
        }

        val nextItem = _queue.value.getOrNull(actualIndex) ?: return
        _isCrossfading.value = true

        scope.launch {
            val detail = mediaRepository.getMediaDetail(nextItem.id)
            detail.onSuccess { d ->
                val source = d.mediaSources.firstOrNull()
                val localDownload = downloadRepository.getDownloadByMediaItemId(nextItem.id)
                val file = localDownload?.let { dl ->
                    java.io.File(dl.downloadPath).takeIf { f -> f.exists() }
                }
                val url = if (localDownload != null && file != null &&
                    localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED
                ) {
                    Uri.fromFile(file).toString()
                } else {
                    playbackRepository.getStreamUrl(nextItem.id, source?.id ?: "", 0L)
                }

                val cfPlayer = createCrossfadePlayer()
                crossfadePlayer = cfPlayer

                val artUri = Uri.parse(playbackRepository.getImageUrl(nextItem.id, maxWidth = 600))
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(d.item.name)
                            .setArtist(d.item.albumArtist ?: d.item.artistItems.firstOrNull()?.name ?: "")
                            .setAlbumTitle(d.item.album ?: "")
                            .setArtworkUri(artUri)
                            .build()
                    )
                    .build()

                cfPlayer.setMediaItem(mediaItem)
                cfPlayer.prepare()

                val speed = _speed.value
                cfPlayer.setPlaybackSpeed(speed)

                cfPlayer.playWhenReady = true
                cfPlayer.play()

                performVolumeCrossfade(crossfadeMs, actualIndex, nextItem)
            }
        }
    }

    private suspend fun performVolumeCrossfade(
        crossfadeMs: Long,
        nextIndex: Int,
        nextItem: AudioQueueItem,
    ) {
        val primary = exoPlayer ?: return
        val secondary = crossfadePlayer ?: return

        val steps = 30
        val stepDelay = crossfadeMs / steps
        val volumeStep = 1.0f / steps

        for (i in 1..steps) {
            if (!scope.isActive || !_isCrossfading.value) {
                primary.volume = 1.0f
                secondary.volume = 0.0f
                return
            }

            primary.volume = 1.0f - (volumeStep * i)
            secondary.volume = volumeStep * i

            delay(stepDelay)
        }

        primary.volume = 0.0f
        secondary.volume = 1.0f

        primary.stop()
        primary.release()

        exoPlayer = secondary
        crossfadePlayer = null

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
        applyNightMode()
        applyDialogueBoost()
        applyEqualizer()

        _isCrossfading.value = false

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
        scope.launch {
            _isFetchingLyrics.value = true
            mediaRepository.getLyricsWithFallback(itemId, artistName, trackName, durationSec)
                .onSuccess {
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
                .onFailure {
                    _lyrics.value = emptyList()
                    _lyricsSource.value = LyricsSource.UNKNOWN
                }
            _isFetchingLyrics.value = false
        }
    }

    fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        scope.launch {
            val result = mediaRepository.searchLyrics(query)
            callback(result)
        }
    }

    fun applyLyrics(lrcLibId: Long) {
        val itemId = currentItemId ?: return
        scope.launch {
            mediaRepository.getLyricsById(lrcLibId, itemId)
                .onSuccess {
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
        }
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _currentPosition.value = player.currentPosition
                    _duration.value = player.duration.coerceAtLeast(0L)
                    if (_lyrics.value.isNotEmpty()) {
                        _currentLyricIndex.value = findCurrentLyricLine(
                            _lyrics.value, _currentPosition.value
                        )
                    }

                    if (_crossfadeDurationMs.value > 0 && player.isPlaying && _repeatMode.value != 2) {
                        startCrossfadeIfNeeded()
                    }
                }
                delay(100)
            }
        }
    }

    private fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(10_000)
                val player = exoPlayer ?: continue
                val itemId = currentItemId ?: continue
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionId,
                        positionTicks = player.currentPosition * 10_000,
                        isPaused = !player.isPlaying,
                    )
                )
            }
        }
    }

    private fun reportCurrentItemStopped() {
        val player = exoPlayer ?: return
        val itemId = currentItemId ?: return
        val sid = playSessionId
        val pos = player.currentPosition * 10_000
        if (pos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sid, pos)
            }
        }
        playSessionId = UUID.randomUUID().toString()
    }

    fun stopAndRelease() {
        cancelCrossfade()

        val player = exoPlayer
        val itemId = currentItemId
        val sid = playSessionId
        val pos = player?.currentPosition?.let { it * 10_000 } ?: 0L

        positionJob?.cancel()
        progressJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        mediaSession?.let { sessionManager.clearSession(it) }
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        dialogueBoost.detach()
        equalizerHelper.detach()
        loudnessEnhancer?.release()
        loudnessEnhancer = null

        currentItemId = null
        _currentPlayingItemId.value = null
        _isPlaying.value = false
        _title.value = ""
        _artist.value = ""
        _album.value = ""
        _albumArtUrl.value = ""
        _currentPosition.value = 0L
        _duration.value = 0L
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        _lyricsSource.value = LyricsSource.UNKNOWN
        _isFetchingLyrics.value = false
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

    private fun findCurrentLyricLine(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                lines[mid].timeMs <= positionMs -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return (low - 1).coerceAtLeast(0)
    }
}
