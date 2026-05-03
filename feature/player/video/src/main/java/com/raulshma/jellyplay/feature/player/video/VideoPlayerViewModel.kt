package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleParserHelper
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private val preferencesStore: UserPreferencesStore,
    private val sessionManager: PlaybackSessionManager,
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

    private var _currentMediaSource by mutableStateOf<MediaSource?>(null)
    val currentMediaSource get() = _currentMediaSource

    private var _mediaStreams by mutableStateOf<List<MediaStream>>(emptyList())
    val mediaStreams get() = _mediaStreams

    private var _aspectRatio by mutableStateOf(AspectRatio.FIT)
    val aspectRatio get() = _aspectRatio

    private var _playMethod by mutableStateOf("Direct Play")
    val playMethod get() = _playMethod

    private var _trickplayUrl by mutableStateOf<String?>(null)
    val trickplayUrl get() = _trickplayUrl

    private var _subtitleStyle by mutableStateOf(SubtitleStyle())
    val subtitleStyle get() = _subtitleStyle

    private var _secondarySubtitleTrack by mutableStateOf<MediaStream?>(null)
    val secondarySubtitleTrack get() = _secondarySubtitleTrack

    private var _secondarySubtitleCues by mutableStateOf<List<TimedCue>>(emptyList())
    val secondarySubtitleCues get() = _secondarySubtitleCues

    private var _secondarySubtitleOffsetMs by mutableStateOf(0L)
    val secondarySubtitleOffsetMs get() = _secondarySubtitleOffsetMs

    private var _dialogueBoostEnabled by mutableStateOf(false)
    val dialogueBoostEnabled get() = _dialogueBoostEnabled

    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private val dialogueBoost = DialogueBoostHelper()

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

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setSubtitleParserFactory(
                OffsettingSubtitleParserFactory(
                    DefaultSubtitleParserFactory(),
                ) { subtitleOffsetMs * 1000L }
            )

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        _exoPlayer = player
        player.addListener(playerListener)

        val session = MediaSession.Builder(context, player).build()
        _mediaSession = session
        sessionManager.setActiveSession(session)

        startPositionTracking()

        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                _subtitleStyle = prefs.subtitleStyle
            }
        }

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
                    _currentMediaSource = source
                    _mediaStreams = source?.mediaStreams ?: emptyList()

                    _playMethod = when {
                        source?.supportsDirectPlay == true -> "Direct Play"
                        source?.supportsDirectStream == true -> "Direct Stream"
                        source?.supportsTranscoding == true -> "Transcode"
                        else -> "Direct Play"
                    }

                    val url = playbackRepository.getStreamUrl(
                        itemId,
                        source?.id ?: "",
                        startPositionTicks,
                    )
                    _streamUrl = url

                    val subtitleConfigs = buildSubtitleConfigurations(source?.mediaStreams ?: emptyList())

                    val artworkUri = Uri.parse(
                        playbackRepository.getImageUrl(itemId, maxWidth = 300)
                    )

                    val mediaItem = MediaItem.Builder()
                        .setUri(url)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(detail.item.name)
                                .setSubtitle(detail.item.seriesName ?: detail.item.overview?.take(60))
                                .setArtworkUri(artworkUri)
                                .build()
                        )
                        .build()

                    _exoPlayer?.setMediaItem(mediaItem)
                    _exoPlayer?.prepare()

                    if (startPositionTicks > 0) {
                        _exoPlayer?.seekTo(startPositionTicks / 10_000)
                    }

                    _exoPlayer?.play()

                    val prefs = preferencesStore.preferences.first()
                    _dialogueBoostEnabled = prefs.dialogueBoostEnabled
                    if (_dialogueBoostEnabled) {
                        applyDialogueBoost()
                    }

                    if (prefs.preferredSubtitleLanguage != null) {
                        val selector = _trackSelector ?: return@launch
                        val params = selector.buildUponParameters()
                        params.setPreferredTextLanguage(prefs.preferredSubtitleLanguage!!)
                        selector.setParameters(params)
                    }

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

    fun selectSecondarySubtitleStream(stream: MediaStream?) {
        _secondarySubtitleTrack = stream
        if (stream == null || stream.deliveryUrl.isNullOrBlank()) {
            _secondarySubtitleCues = emptyList()
            return
        }
        viewModelScope.launch {
            loadSecondarySubtitle(stream)
        }
    }

    fun setSecondarySubtitleOffset(offsetMs: Long) {
        _secondarySubtitleOffsetMs = offsetMs
    }

    private suspend fun loadSecondarySubtitle(stream: MediaStream) {
        try {
            val url = playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
            val mimeType = mapSubtitleCodecToMime(stream.codec) ?: MimeTypes.APPLICATION_SUBRIP
            val request = okhttp3.Request.Builder().url(url).build()
            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val bytes = response.body?.bytes() ?: return
            val cues = SubtitleParserHelper.parseSubtitles(bytes, mimeType)
            _secondarySubtitleCues = cues
        } catch (_: Exception) {
            _secondarySubtitleCues = emptyList()
        }
    }

    fun getSecondarySubtitleText(positionMs: Long): String? {
        val cues = _secondarySubtitleCues
        if (cues.isEmpty()) return null
        val cue = SubtitleParserHelper.findActiveCue(
            cues,
            positionMs * 1000L,
            _secondarySubtitleOffsetMs * 1000L,
        ) ?: return null
        return cue.text.toString().takeIf { it.isNotBlank() }
    }

    fun getCurrentPrimarySubtitleText(): String? {
        val player = _exoPlayer ?: return null
        val cues = player.currentCues.cues
        if (cues.isEmpty()) return null
        return cues.joinToString("\n") { it.text?.toString() ?: "" }
            .takeIf { it.isNotBlank() }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio = ratio
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle = style
        subtitleOffsetMs = style.offsetMs
        viewModelScope.launch {
            preferencesStore.setSubtitleStyle(style)
        }
    }

    fun updateSubtitleOffset(offsetMs: Long) {
        subtitleOffsetMs = offsetMs
    }

    fun toggleDialogueBoost() {
        _dialogueBoostEnabled = !_dialogueBoostEnabled
        applyDialogueBoost()
        viewModelScope.launch {
            preferencesStore.setDialogueBoostEnabled(_dialogueBoostEnabled)
        }
    }

    private fun applyDialogueBoost() {
        val player = _exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        dialogueBoost.attach(audioSessionId)
        dialogueBoost.setEnabled(_dialogueBoostEnabled)
    }

    @Volatile
    var subtitleOffsetMs: Long = 0L
        private set

    fun getTrickplayImageUrl(positionMs: Long): String? {
        val itemId = currentItemId ?: return null
        val server = playbackRepository.getImageUrl(itemId).substringBefore("/Items")
        val index = (positionMs / 10_000).toInt()
        return "$server/Items/$itemId/Trickplay/320/$index.jpg"
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

    private fun buildSubtitleConfigurations(streams: List<MediaStream>): List<MediaItem.SubtitleConfiguration> {
        return streams
            .filter { it.type == StreamType.SUBTITLE && it.isExternal && !it.deliveryUrl.isNullOrBlank() }
            .map { stream ->
                val mimeType = mapSubtitleCodecToMime(stream.codec)
                val url = playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                    .setMimeType(mimeType)
                    .setLanguage(stream.language)
                    .setLabel(stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown")
                    .setSelectionFlags(
                        if (stream.isDefault) C.SELECTION_FLAG_DEFAULT else 0 or
                        if (stream.isForced) C.SELECTION_FLAG_FORCED else 0
                    )
                    .build()
            }
    }

    private fun mapSubtitleCodecToMime(codec: String?): String? {
        if (codec == null) return null
        return when (codec.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            "pgs" -> MimeTypes.APPLICATION_PGS
            else -> null
        }
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
        _mediaSession?.let { sessionManager.clearSession(it) }
        _mediaSession?.release()
        _mediaSession = null
        _exoPlayer?.release()
        _exoPlayer = null
        _trackSelector = null
        dialogueBoost.detach()
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        positionJob?.cancel()
        _exoPlayer?.removeListener(playerListener)
        _mediaSession?.let { sessionManager.clearSession(it) }
        _mediaSession?.release()
        _exoPlayer?.release()
        _exoPlayer = null
        dialogueBoost.detach()
    }
}

private fun mutableLongStateOf(initial: Long) = mutableStateOf(initial)
