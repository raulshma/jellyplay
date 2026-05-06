package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio

data class VideoPlayerUiState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
    val aspectRatio: AspectRatio = AspectRatio.AUTO,
    val detectedAspectRatio: AspectRatio? = null,
    val playMethod: String = "Direct Play",
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val dialogueBoostEnabled: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val audioPassthrough: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val ocrText: String? = null,
    val isOcrRunning: Boolean = false,
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isLoadingRemoteSubtitles: Boolean = false,
    val syncPlayGroupName: String? = null,
    val syncPlayParticipantCount: Int = 0,
    val isSyncPlaySynced: Boolean = false,
    val nextEpisode: JellyfinMediaItem? = null,
    val streamUrl: String? = null,
    val preferredPlayerType: PlayerType = PlayerType.EXO_PLAYER,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val seekDurationMs: Long = 10_000L,
    val defaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val controlsTimeoutMs: Long = 5_000L,
    val gesturesEnabled: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val swipeSeekMaxMs: Long = 120_000L,
    val rememberBrightness: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    val frameRateMatching: Boolean = false,
    val audioDelayMs: Long = 0L,
    val secondarySubtitleTrack: MediaStream? = null,
    val introTimestamps: IntroTimestamps? = null,
    val creditTimestamps: CreditTimestamps? = null,
    val seriesId: String? = null,
    val isInSyncPlaySession: Boolean = false,
    val engineCapabilities: EngineCapabilities = EngineCapabilities(),
    val playerError: String? = null,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
) {
    val isInIntro: Boolean
        get() {
            val ts = introTimestamps ?: return false
            if (!ts.hasIntro) return false
            val posTicks = currentPosition * 10_000
            val promptStart = if (ts.showSkipPromptAtTicks > 0) ts.showSkipPromptAtTicks else ts.introStartTicks
            val promptEnd = if (ts.hideSkipPromptAtTicks > 0) ts.hideSkipPromptAtTicks else ts.introEndTicks
            return posTicks >= promptStart && posTicks < promptEnd
        }

    val isInCredits: Boolean
        get() {
            val ts = creditTimestamps ?: return false
            if (!ts.hasCredits) return false
            val posTicks = currentPosition * 10_000
            val promptStart = if (ts.showSkipPromptAtTicks > 0) ts.showSkipPromptAtTicks else ts.creditStartTicks
            val promptEnd = if (ts.hideSkipPromptAtTicks > 0) ts.hideSkipPromptAtTicks else ts.creditEndTicks
            return posTicks >= promptStart && posTicks < promptEnd
        }

    val shouldShowUpNext: Boolean
        get() {
            if (nextEpisode == null) return false
            if (seriesId == null) return false
            if (isInCredits) return true
            val ct = creditTimestamps
            if (ct == null || !ct.hasCredits) {
                if (duration > 0 && currentPosition >= duration - 30_000) return true
            }
            return false
        }

    val hdrType: String?
        get() {
            val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
            val range = videoStream.videoRange ?: return null
            return if (range.equals("SDR", ignoreCase = true)) null else range
        }

    val videoFrameRate: Float?
        get() = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }?.realFrameRate
}

data class EngineCapabilities(
    val audioDelay: Boolean = false,
    val subtitleDelay: Boolean = false,
    val audioPassthrough: Boolean = false,
    val subtitleStyle: Boolean = false,
    val dialogueBoost: Boolean = false,
    val nightMode: Boolean = false,
    val ocr: Boolean = false,
    val cues: Boolean = false,
)
