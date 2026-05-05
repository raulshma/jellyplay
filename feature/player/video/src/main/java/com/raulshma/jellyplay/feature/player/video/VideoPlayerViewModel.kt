package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.ExoPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
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
    val trackGroup: androidx.media3.common.TrackGroup? = null,
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val preferencesStore: UserPreferencesStore,
    private val sessionManager: PlaybackSessionManager,
    private val castManager: CastManager,
    private val syncPlayManager: SyncPlayManager,
) : ViewModel() {

    private var _exoPlayer by mutableStateOf<ExoPlayer?>(null)
    val exoPlayer get() = _exoPlayer

    private var _playerEngine by mutableStateOf<PlayerEngine?>(null)
    val playerEngine: PlayerEngine? get() = _playerEngine

    private var _preferredPlayerType by mutableStateOf(PlayerType.EXO_PLAYER)
    val preferredPlayerType get() = _preferredPlayerType

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

    private var _aspectRatio by mutableStateOf(AspectRatio.AUTO)
    val aspectRatio get() = _aspectRatio

    private var _detectedAspectRatio by mutableStateOf<AspectRatio?>(null)
    val detectedAspectRatio get() = _detectedAspectRatio

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

    private var _nightModeEnabled by mutableStateOf(false)
    val nightModeEnabled get() = _nightModeEnabled

    var seekDurationMs by mutableLongStateOf(10_000L)
        private set

    var defaultOrientation by mutableStateOf(OrientationMode.SENSOR_LANDSCAPE)
        private set

    var controlsTimeoutMs by mutableLongStateOf(5_000L)
        private set

    var gesturesEnabled by mutableStateOf(true)
        private set

    var defaultSpeed by mutableFloatStateOf(1.0f)
        private set

    var swipeSeekMaxMs by mutableLongStateOf(120_000L)
        private set

    var rememberBrightness by mutableStateOf(false)
        private set

    var brightnessLevel by mutableFloatStateOf(0.5f)
        private set

    private var _audioDelayMs by mutableLongStateOf(0L)
    val audioDelayMs get() = _audioDelayMs

    private var _decoderMode by mutableStateOf(DecoderMode.HW_PREFERRED)
    val decoderMode get() = _decoderMode

    private var _audioPassthrough by mutableStateOf(false)
    val audioPassthrough get() = _audioPassthrough

    private var _frameRateMatching by mutableStateOf(false)
    val frameRateMatching get() = _frameRateMatching

    private var _ocrText by mutableStateOf<String?>(null)
    val ocrText get() = _ocrText

    private var _isOcrRunning by mutableStateOf(false)
    val isOcrRunning get() = _isOcrRunning

    private var _introTimestamps by mutableStateOf<IntroTimestamps?>(null)
    val introTimestamps get() = _introTimestamps

    val isInIntro: Boolean
        get() {
            val ts = _introTimestamps ?: return false
            if (!ts.hasIntro) return false
            val posTicks = _currentPosition * 10_000
            val promptStart = if (ts.showSkipPromptAtTicks > 0) ts.showSkipPromptAtTicks else ts.introStartTicks
            val promptEnd = if (ts.hideSkipPromptAtTicks > 0) ts.hideSkipPromptAtTicks else ts.introEndTicks
            return posTicks >= promptStart && posTicks < promptEnd
        }

    val hdrType: String?
        get() {
            val videoStream = _mediaStreams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
            val range = videoStream.videoRange ?: return null
            return if (range.equals("SDR", ignoreCase = true)) null else range
        }

    val videoFrameRate: Float?
        get() = _mediaStreams.firstOrNull { it.type == StreamType.VIDEO }?.realFrameRate

    private var _remoteSubtitles by mutableStateOf<List<com.raulshma.jellyplay.core.model.RemoteSubtitleInfo>>(emptyList())
    val remoteSubtitles get() = _remoteSubtitles

    private var _isLoadingRemoteSubtitles by mutableStateOf(false)
    val isLoadingRemoteSubtitles get() = _isLoadingRemoteSubtitles

    private var _syncPlayGroupName by mutableStateOf<String?>(null)
    val syncPlayGroupName get() = _syncPlayGroupName

    private var _syncPlayParticipantCount by mutableStateOf(0)
    val syncPlayParticipantCount get() = _syncPlayParticipantCount

    private var _isSyncPlaySynced by mutableStateOf(false)
    val isSyncPlaySynced get() = _isSyncPlaySynced

    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private val dialogueBoost = DialogueBoostHelper()
    private val nightMode = NightModeHelper()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateTracks()
            if (playbackState == Player.STATE_READY) {
                if (_dialogueBoostEnabled) applyDialogueBoost()
                if (_nightModeEnabled) applyNightMode()
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            updateTracks()
        }
    }

    fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        if (currentItemId == itemId) return
        releaseInternals()
        playSessionId = java.util.UUID.randomUUID().toString()
        currentItemId = itemId

        viewModelScope.launch {
            val prefs = preferencesStore.preferences.first()
            _preferredPlayerType = prefs.preferredPlayer
            _subtitleStyle = prefs.subtitleStyle
            _audioDelayMs = prefs.audioDelayMs
            _decoderMode = prefs.decoderMode
            _audioPassthrough = prefs.audioPassthrough
            _frameRateMatching = prefs.frameRateMatching
            _nightModeEnabled = prefs.nightModeEnabled
            seekDurationMs = prefs.videoSeekDurationMs
            defaultOrientation = prefs.videoDefaultOrientation
            controlsTimeoutMs = prefs.videoControlsTimeoutMs
            gesturesEnabled = prefs.videoGesturesEnabled
            defaultSpeed = prefs.videoDefaultSpeed
            swipeSeekMaxMs = prefs.videoSwipeSeekMaxMs
            rememberBrightness = prefs.videoRememberBrightness
            brightnessLevel = prefs.videoBrightnessLevel

            val defaultAspectName = prefs.videoDefaultAspectRatio
            try {
                _aspectRatio = when (defaultAspectName) {
                    "FIT" -> AspectRatio.FIT
                    "FILL" -> AspectRatio.FILL
                    "CROP" -> AspectRatio.CROP
                    "16:9" -> AspectRatio.RATIO_16_9
                    "4:3" -> AspectRatio.RATIO_4_3
                    "21:9" -> AspectRatio.RATIO_21_9
                    else -> AspectRatio.AUTO
                }
            } catch (_: Exception) {
                _aspectRatio = AspectRatio.AUTO
            }

            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrElse {
                _title = "Error loading media"
                return@launch
            }

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
            detectBestAspectRatio()

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

            when (_preferredPlayerType) {
                PlayerType.EXO_PLAYER -> initializeExoPlayer(
                    detail, source, url, startPositionTicks, prefs
                )
                PlayerType.MPV, PlayerType.LIBVLC -> initializeAlternativeEngine(
                    url, detail.item.name, startPositionTicks
                )
                PlayerType.EXTERNAL -> return@launch
            }

            playbackRepository.reportPlaybackStart(
                com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                    itemId = itemId,
                    sessionId = playSessionId,
                    mediaSourceId = source?.id,
                )
            )

            startPositionTracking()
            startProgressReporting()
            fetchIntroTimestamps(itemId)
        }
    }

    private suspend fun initializeExoPlayer(
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
    ) {
        val trackSelector = DefaultTrackSelector(context)
        _trackSelector = trackSelector

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setSubtitleParserFactory(
                OffsettingSubtitleParserFactory(
                    DefaultSubtitleParserFactory(),
                ) { subtitleOffsetMs * 1000L }
            )

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        _exoPlayer = player
        player.addListener(playerListener)

        val session = MediaSession.Builder(context, player)
            .setId(playSessionId)
            .build()
        _mediaSession = session
        sessionManager.setActiveSession(session)

        val subtitleConfigs = buildSubtitleConfigurations(source?.mediaStreams ?: emptyList())
        val artworkUri = Uri.parse(
            playbackRepository.getImageUrl(detail.item.id, maxWidth = 300)
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

        player.setMediaItem(mediaItem)
        player.prepare()
        if (startPositionTicks > 0) player.seekTo(startPositionTicks / 10_000)
        if (defaultSpeed != 1.0f) {
            _playbackSpeed = defaultSpeed
            player.setPlaybackSpeed(defaultSpeed)
        }
        player.play()

        _dialogueBoostEnabled = prefs.dialogueBoostEnabled
        if (_dialogueBoostEnabled) applyDialogueBoost()

        _nightModeEnabled = prefs.nightModeEnabled
        if (_nightModeEnabled) applyNightMode()

        if (prefs.preferredAudioLanguage != null) {
            val params = trackSelector.buildUponParameters()
            params.setPreferredAudioLanguage(prefs.preferredAudioLanguage!!)
            trackSelector.setParameters(params)
        }

        if (prefs.preferredSubtitleLanguage != null) {
            val params = trackSelector.buildUponParameters()
            params.setPreferredTextLanguage(prefs.preferredSubtitleLanguage!!)
            trackSelector.setParameters(params)
        }
    }

    private fun initializeAlternativeEngine(
        url: String,
        title: String,
        startPositionTicks: Long,
    ) {
        val engine = PlayerEngineFactory.create(context, _preferredPlayerType)
        _playerEngine = engine

        engine.setDecoderMode(_decoderMode)
        engine.setAudioDelay(_audioDelayMs)
        engine.setAudioPassthrough(_audioPassthrough)

        engine.setOnStateChanged { playing ->
            _isPlaying = playing
        }
        engine.setOnTracksChanged {
            updateTracksFromEngine(engine)
        }

        engine.initialize(url, title, startPositionTicks / 10_000)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed = speed
        if (_playerEngine != null) {
            _playerEngine?.setPlaybackSpeed(speed)
        } else {
            _exoPlayer?.setPlaybackSpeed(speed)
        }
    }

    fun selectAudioTrack(option: TrackOption) {
        val engine = _playerEngine
        if (engine != null) {
            engine.selectAudioTrack(option.index)
            updateTracksFromEngine(engine)
            return
        }
        val selector = _trackSelector ?: return
        if (option.index < 0) {
            val params = selector.buildUponParameters()
            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            selector.setParameters(params)
        } else {
            val group = option.trackGroup ?: return
            val override = androidx.media3.common.TrackSelectionOverride(group, listOf(option.index))
            val params = selector.buildUponParameters()
                .setOverrideForType(override)
                .build()
            selector.setParameters(params)
        }
    }

    fun selectSubtitleTrack(option: TrackOption) {
        val engine = _playerEngine
        if (engine != null) {
            engine.selectSubtitleTrack(option.index)
            updateTracksFromEngine(engine)
            return
        }
        val selector = _trackSelector ?: return
        if (option.index < 0) {
            val params = selector.buildUponParameters()
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            selector.setParameters(params)
        } else {
            val group = option.trackGroup ?: return
            val override = androidx.media3.common.TrackSelectionOverride(group, listOf(option.index))
            val params = selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
                .build()
            selector.setParameters(params)
        }
    }

    fun selectSecondarySubtitleStream(stream: MediaStream?) {
        _secondarySubtitleTrack = stream
        if (stream == null) {
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
            val url = if (!stream.deliveryUrl.isNullOrBlank()) {
                playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
            } else {
                val itemId = currentItemId ?: return
                val sourceId = _currentMediaSource?.id ?: return
                val codec = stream.codec ?: "srt"
                    val ext = when (codec.lowercase()) {
                        "ass", "ssa" -> "ass"
                        "vtt", "webvtt" -> "vtt"
                        "ttml", "dfxp" -> "ttml"
                        "mov_text" -> "vtt"
                        else -> "srt"
                    }
                playbackRepository.getSubtitleDeliveryUrl("/Videos/$itemId/$sourceId/Subtitles/${stream.index}/0.$ext")
            }
            val mimeType = mapSubtitleCodecToMime(stream.codec) ?: MimeTypes.APPLICATION_SUBRIP
            val request = okhttp3.Request.Builder().url(url).build()
            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()
            response.use { resp ->
                val bytes = resp.body?.bytes() ?: return
                val cues = SubtitleParserHelper.parseSubtitles(bytes, mimeType)
                _secondarySubtitleCues = cues
            }
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
        if (ratio == AspectRatio.AUTO) {
            detectBestAspectRatio()
        }
    }

    private fun detectBestAspectRatio() {
        val videoStream = _mediaStreams.firstOrNull { it.type == StreamType.VIDEO } ?: return
        val width = videoStream.width ?: return
        val height = videoStream.height ?: return
        if (height == 0) return

        val nativeRatio = width.toFloat() / height.toFloat()
        _detectedAspectRatio = when {
            nativeRatio >= 2.3f -> AspectRatio.RATIO_21_9
            nativeRatio >= 1.7f -> AspectRatio.RATIO_16_9
            nativeRatio >= 1.3f -> AspectRatio.RATIO_4_3
            else -> AspectRatio.FIT
        }
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

    fun toggleNightMode() {
        _nightModeEnabled = !_nightModeEnabled
        applyNightMode()
        viewModelScope.launch {
            preferencesStore.setNightModeEnabled(_nightModeEnabled)
        }
    }

    private fun applyNightMode() {
        val player = _exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        nightMode.attach(audioSessionId)
        nightMode.setEnabled(_nightModeEnabled)
    }

    fun setAudioDelay(ms: Long) {
        _audioDelayMs = ms
        _playerEngine?.setAudioDelay(ms)
        viewModelScope.launch {
            preferencesStore.setAudioDelay(ms)
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _decoderMode = mode
        _playerEngine?.setDecoderMode(mode)
        viewModelScope.launch {
            preferencesStore.setDecoderMode(mode)
        }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        _audioPassthrough = enabled
        _playerEngine?.setAudioPassthrough(enabled)
        viewModelScope.launch {
            preferencesStore.setAudioPassthrough(enabled)
        }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        _frameRateMatching = enabled
        viewModelScope.launch {
            preferencesStore.setFrameRateMatching(enabled)
        }
    }

    fun skipIntro() {
        val ts = _introTimestamps ?: return
        val targetMs = ts.introEndTicks / 10_000
        if (_playerEngine != null) {
            _playerEngine?.seekTo(targetMs)
        } else {
            _exoPlayer?.seekTo(targetMs)
        }
    }

    private fun fetchIntroTimestamps(itemId: String) {
        viewModelScope.launch {
            _introTimestamps = playbackRepository.getIntroTimestamps(itemId).getOrNull()
        }
    }

    fun loadRemoteSubtitles() {
        val itemId = currentItemId ?: return
        _isLoadingRemoteSubtitles = true
        viewModelScope.launch {
            _remoteSubtitles = playbackRepository.getRemoteSubtitles(itemId).getOrElse { emptyList() }
            _isLoadingRemoteSubtitles = false
        }
    }

    fun downloadSubtitle(subtitleInfo: com.raulshma.jellyplay.core.model.RemoteSubtitleInfo) {
        val itemId = currentItemId ?: return
        viewModelScope.launch {
            playbackRepository.downloadSubtitle(itemId, subtitleInfo.id)
            val detailResult = mediaRepository.getMediaDetail(itemId)
            detailResult.getOrNull()?.let { detail ->
                _mediaDetail = detail
                val source = detail.mediaSources.firstOrNull()
                _currentMediaSource = source
                _mediaStreams = source?.mediaStreams ?: emptyList()
                detectBestAspectRatio()
            }
        }
    }

    fun joinSyncPlay(groupId: String) {
        viewModelScope.launch {
            syncPlayManager.joinGroup(groupId)
            _syncPlayGroupName = groupId
        }
    }

    fun leaveSyncPlay() {
        viewModelScope.launch {
            syncPlayManager.leaveGroup()
            _syncPlayGroupName = null
            _syncPlayParticipantCount = 0
            _isSyncPlaySynced = false
        }
    }

    val isCastAvailable: Boolean
        get() = castManager.isCastAvailable

    val isCastConnected: Boolean
        get() = castManager.isConnected

    val isInSyncPlaySession: Boolean
        get() = syncPlayManager.isInSyncPlaySession

    fun castToDevice() {
        val currentMedia = _exoPlayer?.currentMediaItem
            ?: run {
                val url = _streamUrl ?: return
                val artworkUri = currentItemId?.let {
                    try { Uri.parse(playbackRepository.getImageUrl(it, maxWidth = 300)) } catch (_: Exception) { null }
                }
                MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(_title)
                            .setSubtitle(_subtitle)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()
            }
        val positionMs = _exoPlayer?.currentPosition ?: _playerEngine?.currentPositionMs ?: 0L
        castManager.loadMedia(currentMedia, positionMs, playerListener)
    }

    fun captureOcrSubtitle(bitmap: android.graphics.Bitmap?) {
        if (_isOcrRunning) return
        if (bitmap == null) {
            _ocrText = null
            return
        }
        _isOcrRunning = true
        viewModelScope.launch {
            try {
                val text = com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleOcrHelper
                    .extractSubtitleTextFromFrame(bitmap)
                _ocrText = text
            } catch (_: Exception) {
                _ocrText = null
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                _isOcrRunning = false
            }
        }
    }

    fun clearOcrText() {
        _ocrText = null
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
                        audioOptions.add(TrackOption(i, label, format.language, isSelected, group.mediaTrackGroup))
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        val label = buildTrackLabel(format)
                        subtitleOptions.add(TrackOption(i, label, format.language, isSelected, group.mediaTrackGroup))
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

    private fun updateTracksFromEngine(engine: PlayerEngine) {
        val audioOptions = engine.audioTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected)
        }
        val subtitleOptions = engine.subtitleTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected)
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
            .filter { it.type == StreamType.SUBTITLE }
            .mapNotNull { stream ->
                val mimeType = mapSubtitleCodecToMime(stream.codec) ?: return@mapNotNull null
                val url = if (!stream.deliveryUrl.isNullOrBlank()) {
                    playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                } else {
                    val itemId = currentItemId ?: return@mapNotNull null
                    val sourceId = _currentMediaSource?.id ?: return@mapNotNull null
                    val ext = when (stream.codec?.lowercase()) {
                        "ass", "ssa" -> "ass"
                        "vtt", "webvtt" -> "vtt"
                        "ttml", "dfxp" -> "ttml"
                        "mov_text" -> "vtt"
                        else -> "srt"
                    }
                    playbackRepository.getSubtitleDeliveryUrl("/Videos/$itemId/$sourceId/Subtitles/${stream.index}/0.$ext")
                }
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
            "mov_text" -> MimeTypes.TEXT_VTT
            else -> null
        }
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                val engine = _playerEngine
                if (engine != null) {
                    _currentPosition = engine.currentPositionMs
                    _duration = engine.durationMs.coerceAtLeast(0L)
                } else {
                    _exoPlayer?.let { player ->
                        _currentPosition = player.currentPosition
                        _duration = player.duration.coerceAtLeast(0L)
                    }
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
                val itemId = currentItemId ?: continue
                val engine = _playerEngine
                val positionTicks: Long
                val isPaused: Boolean
                if (engine != null) {
                    positionTicks = engine.currentPositionMs * 10_000
                    isPaused = !engine.isPlaying
                } else {
                    val player = _exoPlayer ?: continue
                    positionTicks = player.currentPosition * 10_000
                    isPaused = !player.isPlaying
                }
                playbackRepository.reportPlaybackProgress(
                    com.raulshma.jellyplay.core.model.PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionId,
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                    )
                )
            }
        }
    }

    private fun releaseInternals() {
        progressJob?.cancel()
        positionJob?.cancel()
        _playerEngine?.release()
        _playerEngine = null
        _exoPlayer?.removeListener(playerListener)
        _mediaSession?.let { sessionManager.clearSession(it) }
        _mediaSession?.release()
        _mediaSession = null
        _exoPlayer?.release()
        _exoPlayer = null
        _trackSelector = null
        _introTimestamps = null
        _remoteSubtitles = emptyList()
        dialogueBoost.detach()
        nightMode.detach()
        syncPlayManager.reset()
    }

    fun release() {
        val player = _exoPlayer
        val engine = _playerEngine
        val itemId = currentItemId
        val sessionId = playSessionId
        val positionTicks = when {
            engine != null -> engine.currentPositionMs * 10_000
            player != null -> player.currentPosition * 10_000
            else -> 0L
        }
        releaseInternals()
        castManager.release()
        if (itemId != null && positionTicks > 0) {
            viewModelScope.launch {
                playbackRepository.reportPlaybackStopped(
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseInternals()
    }
}
