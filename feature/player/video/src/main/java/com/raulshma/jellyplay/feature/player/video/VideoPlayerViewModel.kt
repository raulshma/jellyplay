package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private var _exoPlayer by mutableStateOf<ExoPlayer?>(null)
    val exoPlayer get() = _exoPlayer

    private var _trackSelector by mutableStateOf<DefaultTrackSelector?>(null)

    private var _mediaSession by mutableStateOf<MediaSession?>(null)

    private var _streamUrl by mutableStateOf<String?>(null)
    val streamUrl get() = _streamUrl

    private var _title by mutableStateOf("")
    val title get() = _title

    private var _subtitle by mutableStateOf("")
    val subtitle get() = _subtitle

    private var _isPlaying by mutableStateOf(false)
    val isPlaying get() = _isPlaying

    private var _currentPosition by mutableLongStateOf(0L)
    val currentPosition get() = _currentPosition

    private var _duration by mutableLongStateOf(0L)
    val duration get() = _duration

    private var _playbackSpeed by mutableFloatStateOf(1.0f)
    val playbackSpeed get() = _playbackSpeed

    private var _audioTracks by mutableStateOf<List<TrackOption>>(emptyList())
    val audioTracks get() = _audioTracks

    private var _subtitleTracks by mutableStateOf<List<TrackOption>>(emptyList())
    val subtitleTracks get() = _subtitleTracks

    private var _chapters by mutableStateOf<List<ChapterInfo>>(emptyList())
    val chapters get() = _chapters

    private var _mediaDetail by mutableStateOf<MediaDetail?>(null)
    val mediaDetail get() = _mediaDetail

    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var currentItemId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateTracks()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            updateTracks()
        }
    }

    fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        currentItemId = itemId

        val trackSelector = DefaultTrackSelector(context)
        _trackSelector = trackSelector

        val player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
        _exoPlayer = player
        player.addListener(playerListener)

        _mediaSession = MediaSession.Builder(context, player).build()

        startPositionTracking()

        viewModelScope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    _mediaDetail = detail
                    _title = detail.item.name
                    _subtitle = detail.item.seriesName ?: (detail.item.overview?.take(60) ?: "")
                    _chapters = detail.chapters

                    val source = if (mediaSourceId != null) {
                        detail.mediaSources.find { it.id == mediaSourceId }
                    } else {
                        detail.mediaSources.firstOrNull()
                    }
                    val url = playbackRepository.getStreamUrl(
                        itemId,
                        source?.id ?: "",
                        startPositionTicks,
                    )
                    _streamUrl = url

                    playbackRepository.reportPlaybackStart(
                        com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = playSessionId,
                            mediaSourceId = source?.id,
                        )
                    )

                    startProgressReporting()
                }
                .onFailure {
                    _title = "Error loading media"
                }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed = speed
        _exoPlayer?.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(option: TrackOption) {
        val selector = _trackSelector ?: return
        val params = selector.buildUponParameters()
        if (option.index < 0) {
            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        } else {
            val lang = option.language
            if (lang != null) {
                params.setPreferredAudioLanguage(lang)
            }
        }
        selector.setParameters(params)
        updateTracks()
    }

    fun selectSubtitleTrack(option: TrackOption) {
        val selector = _trackSelector ?: return
        val params = selector.buildUponParameters()
        if (option.index < 0) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val lang = option.language
            if (lang != null) {
                params.setPreferredTextLanguage(lang)
            }
        }
        selector.setParameters(params)
        updateTracks()
    }

    private fun updateTracks() {
        val player = _exoPlayer ?: return
        val tracks = player.currentTracks

        val audioOptions = mutableListOf<TrackOption>()
        val subtitleOptions = mutableListOf<TrackOption>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        val label = buildTrackLabel(format)
                        audioOptions.add(TrackOption(i, label, format.language, isSelected))
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        val label = buildTrackLabel(format)
                        subtitleOptions.add(TrackOption(i, label, format.language, isSelected))
                    }
                }
            }
        }

        _audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            listOf(TrackOption(-1, "Default", null, true)) + audioOptions
        }

        _subtitleTracks = if (subtitleOptions.isEmpty()) {
            listOf(TrackOption(-1, "None", null, true))
        } else {
            listOf(TrackOption(-1, "Off", null, true)) + subtitleOptions
        }
    }

    private fun buildTrackLabel(format: Format): String {
        val lang = format.language?.let {
            try {
                java.util.Locale(it).displayLanguage.ifBlank { it }
            } catch (_: Exception) { it }
        }
        val codec = format.sampleMimeType?.let { mimeToName(it) }
        val channels = format.channelCount
        val channelLabel = when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> null
        }
        return listOfNotNull(lang, codec, channelLabel).joinToString(" · ").ifBlank { "Unknown" }
    }

    private fun mimeToName(mime: String): String = when {
        mime.startsWith("audio/") -> mime.removePrefix("audio/")
        mime.startsWith("text/") -> mime.removePrefix("text/")
        else -> mime
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                _exoPlayer?.let { player ->
                    _currentPosition = player.currentPosition
                    _duration = player.duration.coerceAtLeast(0L)
                }
                delay(250)
            }
        }
    }

    private fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(10_000)
                val player = _exoPlayer ?: continue
                val itemId = currentItemId ?: continue
                playbackRepository.reportPlaybackProgress(
                    com.raulshma.jellyplay.core.model.PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionId,
                        positionTicks = player.currentPosition * 10_000,
                        isPaused = !player.isPlaying,
                    )
                )
            }
        }
    }

    fun release() {
        viewModelScope.launch {
            val player = _exoPlayer ?: return@launch
            val itemId = currentItemId ?: return@launch
            playbackRepository.reportPlaybackStopped(
                itemId = itemId,
                sessionId = playSessionId,
                positionTicks = player.currentPosition * 10_000,
            )
        }
        progressJob?.cancel()
        positionJob?.cancel()
        _exoPlayer?.removeListener(playerListener)
        _mediaSession?.release()
        _mediaSession = null
        _exoPlayer?.release()
        _exoPlayer = null
        _trackSelector = null
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        positionJob?.cancel()
        _exoPlayer?.removeListener(playerListener)
        _mediaSession?.release()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}

private fun mutableLongStateOf(initial: Long) = mutableStateOf(initial)
