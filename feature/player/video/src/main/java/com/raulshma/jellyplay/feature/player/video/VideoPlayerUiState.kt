package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.LyricsLine

/**
 * Lightweight description of an in-progress pre-roll intro for Cinema Mode.
 * Surfaced to the UI to render a "Skip Intro" affordance.
 */
@Immutable
data class CinemaIntroUiState(
    val title: String,
    val currentIndex: Int,
    val totalCount: Int,
)

@Immutable
data class VideoPlayerUiState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<TrackOption> = emptyList(),
    val hasAudioOverride: Boolean = false,
    val hasSubtitleOverride: Boolean = false,
    val overview: String = "",
    val people: List<PersonInfo> = emptyList(),
    val artworkUrl: String? = null,
    val lyricsLines: List<LyricsLine> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val currentSubtitleCues: List<String> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
    val aspectRatio: AspectRatio = AspectRatio.AUTO,
    val detectedAspectRatio: AspectRatio? = null,
    val playMethod: String = "Direct Play",
    val isDirectPlayForced: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioPassthrough: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isLoadingRemoteSubtitles: Boolean = false,
    val syncPlayGroupName: String? = null,
    val syncPlayParticipantCount: Int = 0,
    val isSyncPlaySynced: Boolean = false,
    val isSyncPlaySyncing: Boolean = false,
    val syncPlayRepeatMode: SyncPlayRepeatMode = SyncPlayRepeatMode.REPEAT_NONE,
    val syncPlayShuffleMode: SyncPlayShuffleMode = SyncPlayShuffleMode.SORTED,
    val nextEpisode: JellyfinMediaItem? = null,
    val streamUrl: String? = null,
    val preferredPlayerType: PlayerType = PlayerType.EXO_PLAYER,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val seekDurationMs: Long = 10_000L,
    val defaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val controlsTimeoutMs: Long = 5_000L,
    val passOutProtectionHours: Int = 0,
    val gesturesEnabled: Boolean = true,
    val holdSpeedEnabled: Boolean = true,
    val holdSpeedMultiplier: Float = 2.0f,
    val isHoldSpeedActive: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val swipeSeekMaxMs: Long = 120_000L,
    val rememberBrightness: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    val frameRateMatching: Boolean = false,
    val audioDelayMs: Long = 0L,
    val segments: List<MediaSegment> = emptyList(),
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    val seriesId: String? = null,
    val isInSyncPlaySession: Boolean = false,
    val engineCapabilities: EngineCapabilities = EngineCapabilities(),
    val usesSubtitleOverlay: Boolean = false,
    val playerError: String? = null,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val trickplayInfo: TrickplayInfo? = null,
    val seriesSeasons: List<JellyfinMediaItem> = emptyList(),
    val seasonEpisodes: List<JellyfinMediaItem> = emptyList(),
    val currentSeasonId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val bufferedPosition: Long = 0L,
    val showVideoStats: Boolean = false,
    val videoStats: EngineVideoStats = EngineVideoStats(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val forceDirectPlay: Boolean = true,
    val showPlaybackErrorDialog: Boolean = false,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val sleepTimerActive: Boolean = false,
    val sleepTimerEndOfEpisode: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerLastUsedDurationMs: Long = 0L,
    val videoEffects: VideoEffectsConfig = VideoEffectsConfig(),
    val isScreenLocked: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    val pinHash: String? = null,
    val showPlaybackMetadata: Boolean = true,
    val showClock: Boolean = false,
    val keepScreenOnDuringVideo: Boolean = true,
    val cinemaIntroState: CinemaIntroUiState? = null,
    val isMuted: Boolean = false,
) {

    @Transient
    private var cachedSegmentPosition: Long = Long.MIN_VALUE
    @Transient
    private var cachedSegmentResult: MediaSegment? = null
    @Transient
    private var cachedSegmentKey: Pair<List<MediaSegment>, List<ChapterInfo>>? = null

    fun behaviorForType(type: MediaSegmentType): SegmentBehavior =
        segmentBehaviors[type] ?: SegmentBehavior.IGNORE

    fun computeActiveSegment(): MediaSegment? {
        val currentKey = Pair(segments, chapters)
        if (cachedSegmentKey != currentKey) {
            cachedSegmentPosition = Long.MIN_VALUE
            cachedSegmentResult = null
            cachedSegmentKey = currentKey
        }
        if (cachedSegmentPosition == currentPosition && cachedSegmentResult != null) return cachedSegmentResult
        cachedSegmentPosition = currentPosition
        cachedSegmentResult = computeActiveSegmentInternal()
        return cachedSegmentResult
    }

    companion object {
        private val INTRO_CHAPTER_NAMES = setOf("intro", "introduction", "opening", "op")
        private val OUTRO_CHAPTER_NAMES = setOf("outro", "credits", "end credits", "ending", "ed")
        private val PREVIEW_CHAPTER_NAMES = setOf("preview", "coming up", "cold open", "teaser")
        private val RECAP_CHAPTER_NAMES = setOf("recap", "previously on", "previously")
        private val COMMERCIAL_CHAPTER_NAMES = setOf("commercial", "ad break", "advertisement")

        val CHAPTER_NAME_MAP: Map<MediaSegmentType, Set<String>> = mapOf(
            MediaSegmentType.INTRO to INTRO_CHAPTER_NAMES,
            MediaSegmentType.OUTRO to OUTRO_CHAPTER_NAMES,
            MediaSegmentType.PREVIEW to PREVIEW_CHAPTER_NAMES,
            MediaSegmentType.RECAP to RECAP_CHAPTER_NAMES,
            MediaSegmentType.COMMERCIAL to COMMERCIAL_CHAPTER_NAMES,
        )
    }

    private fun computeActiveSegmentInternal(): MediaSegment? {
        val posTicks = currentPosition * 10_000
        val apiMatches = segments.filter { seg ->
            seg.hasSegment && posTicks >= seg.startTicks && posTicks < seg.endTicks
        }
        if (apiMatches.isNotEmpty()) {
            return MediaSegmentType.SEGMENT_PRIORITY.firstNotNullOfOrNull { priority ->
                apiMatches.firstOrNull { it.type == priority }
            } ?: apiMatches.first()
        }
        return detectChapterSegment()
    }

    private fun detectChapterSegment(): MediaSegment? {
        if (chapters.isEmpty()) return null
        val posTicks = currentPosition * 10_000
        val idx = chapters.indexOfLast { it.startPositionTicks <= posTicks }
        if (idx < 0) return null
        val chapter = chapters[idx]
        val name = chapter.name.lowercase().trim()
        for (type in MediaSegmentType.SEGMENT_PRIORITY) {
            val names = CHAPTER_NAME_MAP[type] ?: continue
            val isMatch = names.any { keyword ->
                if (keyword.length <= 2) name == keyword || name.startsWith("$keyword ") || name.endsWith(" $keyword") || name.contains(" $keyword ")
                else name.contains(keyword)
            }
            if (isMatch) {
                val chapterEndTicks = if (idx + 1 < chapters.size) {
                    chapters[idx + 1].startPositionTicks
                } else {
                    duration * 10_000
                }
                return MediaSegment(
                    id = "chapter-${type.name}-$idx",
                    itemId = "",
                    type = type,
                    startTicks = chapter.startPositionTicks,
                    endTicks = chapterEndTicks,
                )
            }
        }
        return null
    }

    val activeSegment: MediaSegment?
        get() = computeActiveSegment()

    val isInIntro: Boolean
        get() = isInSegmentType(MediaSegmentType.INTRO)

    val isInCredits: Boolean
        get() = isInSegmentType(MediaSegmentType.OUTRO)

    fun isInSegmentType(type: MediaSegmentType): Boolean {
        val behavior = behaviorForType(type)
        if (behavior == SegmentBehavior.IGNORE) return false
        val seg = activeSegment
        return seg != null && seg.type == type
    }

    val introSegmentEndTicks: Long?
        get() = segmentEndTicksForType(MediaSegmentType.INTRO)

    val creditSegmentEndTicks: Long?
        get() = segmentEndTicksForType(MediaSegmentType.OUTRO)

    fun segmentEndTicksForType(type: MediaSegmentType): Long? {
        val seg = activeSegment ?: return null
        if (seg.type != type) return null
        return seg.endTicks
    }

    fun segmentEndTicks(segment: MediaSegment): Long? {
        if (!segment.hasSegment) return null
        val apiMatch = segments.firstOrNull { it.id == segment.id }
        if (apiMatch != null) return apiMatch.endTicks
        return segment.endTicks
    }

    val shouldShowUpNext: Boolean
        get() {
            if (isInSyncPlaySession) return false
            if (nextEpisode == null) return false
            if (seriesId == null) return false
            if (isOutroNearEnd) return true
            if (duration > 0 && currentPosition >= (duration - 30_000)) return true
            return false
        }

    val isOutroNearEnd: Boolean
        get() {
            val outroEnd = creditSegmentEndTicks ?: return false
            val durationTicks = duration * 10_000
            if (durationTicks <= 0) return false
            return (durationTicks - outroEnd).coerceAtLeast(0) < 300_000_000
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
