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
import com.raulshma.jellyplay.core.model.LyricsLine
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
    private var mediaSession: MediaSession? = null
    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val dialogueBoost = DialogueBoostHelper()
    private val equalizerHelper = EqualizerHelper()

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

    private val _nightModeEnabled = MutableStateFlow(false)
    val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private val _dialogueBoostEnabled = MutableStateFlow(false)
    val dialogueBoostEnabled: StateFlow<Boolean> = _dialogueBoostEnabled.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(com.raulshma.jellyplay.core.model.EqualizerSettings())
    val equalizerSettings: StateFlow<com.raulshma.jellyplay.core.model.EqualizerSettings> = _equalizerSettings.asStateFlow()

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
    }

    val hasActiveSession: Boolean
        get() = exoPlayer != null && currentItemId != null

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

    fun play(itemId: String) {
        if (currentItemId == itemId) {
            val state = exoPlayer?.playbackState
            if (state != null && state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
                return
            }
        }

        // Report stop position for the previous item before switching
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
                    val localDownload = downloadRepository.getDownloadByMediaItemId(itemId)
                    val file = localDownload?.let {
                        java.io.File(it.downloadPath).takeIf { f -> f.exists() }
                    }
                    val resumeTicks = detail.item.playbackPositionTicks ?: 0L
                    val url = if (localDownload != null && file != null &&
                        localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED
                    ) {
                        Uri.fromFile(file).toString()
                    } else {
                        playbackRepository.getStreamUrl(itemId, source?.id ?: "", resumeTicks)
                    }

                    val artworkUri = Uri.parse(_albumArtUrl.value)
                    val startPositionMs = if (resumeTicks > 0) resumeTicks / 10_000 else 0L

                    val mediaItem = MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(_title.value)
                                .setArtist(_artist.value)
                                .setAlbumTitle(_album.value)
                                .setArtworkUri(artworkUri)
                                .build()
                        )
                        .build()
                    player.setMediaItem(mediaItem, startPositionMs)
                    player.prepare()
                    player.playWhenReady = true

                    if (_currentIndex.value < 0 || _queue.value.getOrNull(_currentIndex.value)?.id != itemId) {
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

                    playbackRepository.reportPlaybackStart(
                        PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = playSessionId,
                            mediaSourceId = source?.id,
                        )
                    )

                    fetchLyrics(itemId)
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
    }

    fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val next = when {
            _shuffleMode.value -> q.indices.filter { it != _currentIndex.value }.randomOrNull() ?: return
            _currentIndex.value < q.lastIndex -> _currentIndex.value + 1
            _repeatMode.value >= 1 -> 0
            else -> return
        }
        _currentIndex.value = next
        play(q[next].id)
    }

    fun skipToPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        val player = exoPlayer ?: return
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
        play(q[prev].id)
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
        _currentIndex.value = index
        play(q[index].id)
    }

    fun toggleNightMode() {
        _nightModeEnabled.value = !_nightModeEnabled.value
        applyNightMode()
    }

    fun toggleDialogueBoost() {
        _dialogueBoostEnabled.value = !_dialogueBoostEnabled.value
        applyDialogueBoost()
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
            player.volume = nightModeVolume
            attachLoudnessEnhancer(player.audioSessionId)
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

    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        val currentGain = nightModeGain
        loudnessEnhancer?.release()
        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(currentGain)
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
            _currentIndex.value < _queue.value.lastIndex || _repeatMode.value >= 1 -> skipToNext()
            else -> {
                _isPlaying.value = false
            }
        }
    }

    private fun fetchLyrics(itemId: String) {
        scope.launch {
            mediaRepository.getLyrics(itemId)
                .onSuccess { _lyrics.value = it.lines }
                .onFailure { _lyrics.value = emptyList() }
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
                }
                delay(250)
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
