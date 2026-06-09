package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerSessionState(
    val currentItemId: String? = null,
    val mediaDetail: MediaDetail? = null,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val title: String = "",
    val subtitle: String = "",
    val playMethodString: String = "Direct Play",
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val isReady: Boolean = false,
    val offlineTrickplayDir: java.io.File? = null,
    val streamUrl: String? = null,
)

class PlayerSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val preferencesStore: UserPreferencesStore,
    private val playerLifecycleManager: PlayerLifecycleManager,
    private val adaptiveBitrateManager: com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager,
) {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _engine = MutableStateFlow<MediaEngine?>(null)
    val engineFlow: StateFlow<MediaEngine?> = _engine.asStateFlow()
    val engine: MediaEngine? get() = _engine.value

    private var lastPlaybackRequest: PlaybackRequest? = null
    private var lastPlayerType: PlayerType? = null
    
    fun bindReclaimedEngine(engine: MediaEngine, itemId: String, detail: MediaDetail) {
        _engine.value = engine
        playerLifecycleManager.activeCallbacks = engine
        playerLifecycleManager.requestAutoEnterPip(engine.capabilities.supportsPip)
        
        _sessionState.update { 
            it.copy(
                currentItemId = itemId,
                mediaDetail = detail,
                title = detail.item.name,
                subtitle = detail.item.seriesName ?: (detail.item.overview?.take(60) ?: ""),
                isReady = true
            )
        }
    }

    suspend fun loadMedia(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        _sessionState.update { it.copy(currentItemId = itemId, isReady = false) }

        val localDownload = downloadRepository.getDownloadByMediaItemId(itemId)
        val localFile = localDownload?.let {
            java.io.File(it.downloadPath).takeIf { f -> f.exists() }
        }

        if (localDownload != null && localFile != null &&
            localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED
        ) {
            val offlineItem = offlineRepository.getOfflineItem(itemId)
            val title = offlineItem?.name ?: localDownload.name
            val subtitle = offlineItem?.seriesName ?: offlineItem?.overview?.take(60) ?: ""
            val url = Uri.fromFile(localFile).toString()

            _sessionState.update {
                it.copy(
                    title = title,
                    subtitle = subtitle,
                    playMethodString = "Offline",
                    playMethod = PlayMethod.DIRECT_PLAY,
                    streamUrl = url,
                )
            }

            val prefs = preferencesStore.preferences.first()
            val playerType = prefs.preferredPlayer

            if (playerType == PlayerType.EXTERNAL) {
                _sessionState.update { it.copy(isReady = true) }
                return
            }

            val detail = com.raulshma.jellyplay.core.model.MediaDetail(
                item = com.raulshma.jellyplay.core.model.MediaItem(
                    id = itemId,
                    name = title,
                    mediaType = localDownload.mediaType,
                    overview = offlineItem?.overview,
                    seriesName = offlineItem?.seriesName,
                    runTimeTicks = offlineItem?.runTimeTicks,
                ),
                mediaSources = emptyList(),
                chapters = emptyList(),
            )

            initializeEngine(playerType, detail, null, url, startPositionTicks, prefs)

            val trickplayDir = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
                .getLocalTrickplayDir(localDownload.downloadPath)

            _sessionState.update { it.copy(
                isReady = true,
                mediaDetail = detail,
                offlineTrickplayDir = trickplayDir,
            ) }
            return
        }

        val detailResult = mediaRepository.getMediaDetail(itemId)
        val detail = detailResult.getOrElse {
            _sessionState.update { it.copy(title = "Error loading media") }
            return
        }

        val source = if (mediaSourceId != null) {
            detail.mediaSources.find { it.id == mediaSourceId }
        } else {
            detail.mediaSources.firstOrNull()
        }
        val streams = source?.mediaStreams ?: emptyList()

        val playMethodStr = when {
            source?.supportsDirectPlay == true -> "Direct Play"
            source?.supportsDirectStream == true -> "Direct Stream"
            source?.supportsTranscoding == true -> "Transcode"
            else -> "Direct Play"
        }
        val resolvedPlayMethod = when {
            source?.supportsDirectPlay == true -> PlayMethod.DIRECT_PLAY
            source?.supportsDirectStream == true -> PlayMethod.DIRECT_STREAM
            source?.supportsTranscoding == true -> PlayMethod.TRANSCODE
            else -> PlayMethod.DIRECT_PLAY
        }

        _sessionState.update {
            it.copy(
                title = detail.item.name,
                subtitle = detail.item.seriesName ?: (detail.item.overview?.take(60) ?: ""),
                mediaDetail = detail,
                currentMediaSource = source,
                mediaStreams = streams,
                playMethodString = playMethodStr,
                playMethod = resolvedPlayMethod,
            )
        }

        val url = playbackRepository.getStreamUrl(
            itemId,
            source?.id ?: "",
            startPositionTicks,
        )

        val prefs = preferencesStore.preferences.first()
        val playerType = prefs.preferredPlayer
        
        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true, streamUrl = url) }
            return
        }

        initializeEngine(playerType, detail, source, url, startPositionTicks, prefs)
        _sessionState.update { it.copy(isReady = true) }
    }

    private fun initializeEngine(
        playerType: PlayerType,
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
    ) {
        _engine.value?.release()
        val eng = PlayerEngineFactory.create(context, playerType)
        _engine.value = eng
        lastPlayerType = playerType
        
        playerLifecycleManager.activeCallbacks = eng
        playerLifecycleManager.requestAutoEnterPip(eng.capabilities.supportsPip)

        val config = EngineConfig(
            decoderMode = prefs.decoderMode,
            audioPassthrough = prefs.audioPassthrough,
            audioDelayMs = prefs.audioDelayMs,
            subtitleDelayMs = prefs.subtitleStyle.offsetMs,
            subtitleStyle = prefs.subtitleStyle,
            audioEffects = AudioEffectsConfig(
                dialogueBoostEnabled = prefs.dialogueBoostEnabled,
                dialogueBoostStrength = prefs.dialogueBoostStrength,
                nightModeEnabled = prefs.nightModeEnabled,
                nightModeStrength = prefs.nightModeStrength,
                nightModeGain = prefs.audioNightModeGain,
                equalizerEnabled = prefs.equalizerEnabled,
                equalizerSettings = prefs.equalizerSettings,
                audioNormalizationMode = prefs.audioNormalizationMode,
                audioNormalizationEnabled = prefs.audioNormalizationEnabled,
                channelMixMode = prefs.channelMixMode,
                channelMixEnabled = prefs.channelMixEnabled,
            ),
            engineSpecific = resolveEngineConfig(playerType, prefs),
            pauseOnAudioFocusLoss = prefs.pauseOnAudioFocusLoss,
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(prefs.videoDefaultSpeed)

        val externalSubtitles = source?.mediaStreams
            ?.filter { it.type == StreamType.SUBTITLE }
            ?.mapNotNull { stream ->
                val subUrl = when {
                    !stream.deliveryUrl.isNullOrBlank() -> playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                    stream.isExternal -> playbackRepository.buildSubtitleDeliveryUrl(
                        detail.item.id, source.id, stream.index, stream.codec,
                    )
                    else -> return@mapNotNull null
                }
                if (subUrl.isBlank()) return@mapNotNull null
                
                SubtitleSource(
                    url = subUrl,
                    label = stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown",
                    language = stream.language,
                    mimeType = null, // Will be mapped by engine using codec or extension
                    codec = stream.codec,
                    isDefault = stream.isDefault,
                    isForced = stream.isForced,
                    id = "external:${stream.index}"
                )
            } ?: emptyList()

        val artworkUri = playbackRepository.getImageUrl(detail.item.id, maxWidth = 300)
        
        val headers = mutableMapOf<String, String>()
        val serverUrl = playbackRepository.getServerUrl()
        val token = playbackRepository.getAccessToken()
        if (!token.isNullOrBlank()) {
            headers["X-Emby-Token"] = token
        }

        val request = PlaybackRequest(
            uri = url,
            title = detail.item.name,
            startPositionMs = startPositionTicks / 10_000,
            artworkUri = artworkUri,
            externalSubtitles = externalSubtitles,
            headers = headers,
            preferredAudioLanguage = prefs.preferredAudioLanguage,
            preferredSubtitleLanguage = prefs.preferredSubtitleLanguage,
            maxVideoBitrate = adaptiveBitrateManager.resolveMaxBitrate(prefs.streamingQuality)?.toInt(),
            serverUrl = serverUrl,
            authToken = token,
            minBufferMs = prefs.videoPreloadBufferSize.minBufferMs,
            maxBufferMs = prefs.videoPreloadBufferSize.maxBufferMs,
        )

        lastPlaybackRequest = request
        eng.load(request)
    }

    private fun resolveEngineConfig(
        playerType: PlayerType,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
    ): EngineSpecificConfig? = when (playerType) {
        PlayerType.MPV -> prefs.mpvConfig
        PlayerType.LIBVLC -> prefs.libVlcConfig
        PlayerType.EXO_PLAYER -> prefs.exoPlayerConfig
        PlayerType.EXTERNAL -> null
    }

    suspend fun reloadWithEngine(
        playerType: PlayerType,
        currentPositionMs: Long,
        playbackSpeed: Float = 1.0f,
        maxVideoBitrate: Int? = null,
    ) {
        val last = lastPlaybackRequest ?: return
        val prefs = preferencesStore.preferences.first()

        _engine.value?.release()
        _engine.value = null

        val eng = PlayerEngineFactory.create(context, playerType)
        _engine.value = eng
        lastPlayerType = playerType

        playerLifecycleManager.activeCallbacks = eng
        playerLifecycleManager.requestAutoEnterPip(eng.capabilities.supportsPip)

        val config = EngineConfig(
            decoderMode = prefs.decoderMode,
            audioPassthrough = prefs.audioPassthrough,
            audioDelayMs = prefs.audioDelayMs,
            subtitleDelayMs = prefs.subtitleStyle.offsetMs,
            subtitleStyle = prefs.subtitleStyle,
            audioEffects = AudioEffectsConfig(
                dialogueBoostEnabled = prefs.dialogueBoostEnabled,
                dialogueBoostStrength = prefs.dialogueBoostStrength,
                nightModeEnabled = prefs.nightModeEnabled,
                nightModeStrength = prefs.nightModeStrength,
                nightModeGain = prefs.audioNightModeGain,
                equalizerEnabled = prefs.equalizerEnabled,
                equalizerSettings = prefs.equalizerSettings,
                audioNormalizationMode = prefs.audioNormalizationMode,
                audioNormalizationEnabled = prefs.audioNormalizationEnabled,
                channelMixMode = prefs.channelMixMode,
                channelMixEnabled = prefs.channelMixEnabled,
            ),
            engineSpecific = resolveEngineConfig(playerType, prefs),
            pauseOnAudioFocusLoss = prefs.pauseOnAudioFocusLoss,
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(playbackSpeed)

        val request = last.copy(
            startPositionMs = currentPositionMs,
            maxVideoBitrate = maxVideoBitrate,
        )
        lastPlaybackRequest = request
        eng.load(request)
    }

    fun addExternalSubtitle(source: SubtitleSource) {
        val last = lastPlaybackRequest ?: return
        val updatedSubtitles = last.externalSubtitles + source
        lastPlaybackRequest = last.copy(externalSubtitles = updatedSubtitles)
        _engine.value?.addExternalSubtitle(source)
    }

    fun release() {
        _engine.value?.release()
        _engine.value = null
        lastPlaybackRequest = null
        lastPlayerType = null
        _sessionState.update { PlayerSessionState() }
    }

    fun detachEngine() {
        _engine.value = null
    }
}
