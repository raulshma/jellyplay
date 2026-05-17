package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
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
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioPassthrough: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val ocrText: String? = null,
    val isOcrRunning: Boolean = false,
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isLoadingRemoteSubtitles: Boolean = false,
    val syncPlayGroupName: String? = null,
    val syncPlayParticipantCount: Int = 0,
    val isSyncPlaySynced: Boolean = false,
    val isSyncPlaySyncing: Boolean = false,
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
    val introTimestamps: IntroTimestamps? = null,
    val creditTimestamps: CreditTimestamps? = null,
    val seriesId: String? = null,
    val isInSyncPlaySession: Boolean = false,
    val engineCapabilities: EngineCapabilities = EngineCapabilities(),
    val playerError: String? = null,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val trickplayInfo: TrickplayInfo? = null,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val autoSkipIntro: Boolean = false,
    val autoSkipOutro: Boolean = false,
    val seriesSeasons: List<JellyfinMediaItem> = emptyList(),
    val seasonEpisodes: List<JellyfinMediaItem> = emptyList(),
    val currentSeasonId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val bufferedPosition: Long = 0L,
    val showVideoStats: Boolean = false,
    val videoStats: EngineVideoStats = EngineVideoStats(),
) {
    /** Chapter names that indicate an intro segment (case-insensitive). */
    private val introChapterNames: Set<String> get() = setOf("intro", "introduction", "opening", "op")

    /** Chapter names that indicate an outro/credits segment (case-insensitive). */
    private val outroChapterNames: Set<String> get() = setOf("outro", "credits", "end credits", "ending", "ed")

    val isInIntro: Boolean
        get() {
            if (!skipIntroEnabled) return false
            val ts = introTimestamps
            if (ts != null && ts.hasIntro) {
                val posTicks = currentPosition * 10_000
                val promptStart = if (ts.showSkipPromptAtTicks > 0) ts.showSkipPromptAtTicks else ts.introStartTicks
                val promptEnd = if (ts.hideSkipPromptAtTicks > 0) ts.hideSkipPromptAtTicks else ts.introEndTicks
                if (posTicks >= promptStart && posTicks < promptEnd) return true
            }
            // Fallback: check chapter-based intro segment
            return isInChapterSegment(introChapterNames)
        }

    val isInCredits: Boolean
        get() {
            if (!skipOutroEnabled) return false
            val ts = creditTimestamps
            if (ts != null && ts.hasCredits) {
                val posTicks = currentPosition * 10_000
                val promptStart = if (ts.showSkipPromptAtTicks > 0) ts.showSkipPromptAtTicks else ts.creditStartTicks
                val promptEnd = if (ts.hideSkipPromptAtTicks > 0) ts.hideSkipPromptAtTicks else ts.creditEndTicks
                if (posTicks >= promptStart && posTicks < promptEnd) return true
            }
            // Fallback: check chapter-based outro/credits segment
            return isInChapterSegment(outroChapterNames)
        }

    val shouldShowUpNext: Boolean
        get() {
            if (isInSyncPlaySession) return false
            if (nextEpisode == null) return false
            if (seriesId == null) return false

            // Priority 1: We are in an outro segment that goes near the end
            if (isOutroNearEnd) return true

            // Priority 2: Generic "near end of video" fallback (30s)
            if (duration > 0 && currentPosition >= (duration - 30_000)) return true

            return false
        }

    /**
     * Checks if the current playback position falls within a chapter whose name
     * matches one of the given segment names (case-insensitive).
     *
     * A chapter segment spans from the chapter's [ChapterInfo.startPositionTicks]
     * to the start of the next chapter (or the end of the video if it's the last chapter).
     */
    private fun isInChapterSegment(segmentNames: Set<String>): Boolean {
        if (chapters.isEmpty()) return false
        val posTicks = currentPosition * 10_000

        // Find the chapter that contains the current position
        val currentChapterIndex = chapters.indexOfLast { it.startPositionTicks <= posTicks }
        if (currentChapterIndex < 0) return false

        val chapter = chapters[currentChapterIndex]
        val name = chapter.name.lowercase().trim()
        
        // Strict matching for short codes, contains matching for full words
        val isMatch = segmentNames.any { keyword ->
            if (keyword.length <= 2) name == keyword || name.startsWith("$keyword ") || name.endsWith(" $keyword") || name.contains(" $keyword ")
            else name.contains(keyword)
        }
        if (!isMatch) return false

        // Make sure we're still within the chapter bounds
        val chapterEndTicks = if (currentChapterIndex + 1 < chapters.size) {
            chapters[currentChapterIndex + 1].startPositionTicks
        } else {
            duration * 10_000
        }

        return posTicks < chapterEndTicks
    }

    /**
     * Finds the outro/credits chapter that the current position is within,
     * or null if there is no matching chapter.
     */
    private fun findCurrentOutroChapter(): ChapterInfo? {
        if (chapters.isEmpty()) return null
        val posTicks = currentPosition * 10_000

        val currentChapterIndex = chapters.indexOfLast { it.startPositionTicks <= posTicks }
        if (currentChapterIndex < 0) return null

        val chapter = chapters[currentChapterIndex]
        val chapterNameLower = chapter.name.trim().lowercase()
        if (chapterNameLower !in outroChapterNames) return null

        return chapter
    }

    /** The end position of the current intro segment in ticks, or null if not in an intro. */
    val introSegmentEndTicks: Long?
        get() {
            val ts = introTimestamps
            if (ts != null && ts.hasIntro && isInIntro) return ts.introEndTicks
            // Chapter-based fallback
            if (chapters.isEmpty() || !isInIntro) return null
            val posTicks = currentPosition * 10_000
            val idx = chapters.indexOfLast { it.startPositionTicks <= posTicks }
            if (idx < 0) return null
            val chapterNameLower = chapters[idx].name.trim().lowercase()
            if (chapterNameLower !in introChapterNames) return null
            return if (idx + 1 < chapters.size) chapters[idx + 1].startPositionTicks else duration * 10_000
        }

    /** The end position of the current credits/outro segment in ticks, or null if not in credits. */
    val creditSegmentEndTicks: Long?
        get() {
            val ts = creditTimestamps
            if (ts != null && ts.hasCredits && isInCredits) return ts.creditEndTicks
            // Chapter-based fallback
            if (chapters.isEmpty() || !isInCredits) return null
            val posTicks = currentPosition * 10_000
            val idx = chapters.indexOfLast { it.startPositionTicks <= posTicks }
            if (idx < 0) return null
            val chapterNameLower = chapters[idx].name.trim().lowercase()
            if (chapterNameLower !in outroChapterNames) return null
            return if (idx + 1 < chapters.size) chapters[idx + 1].startPositionTicks else duration * 10_000
        }

    /** Whether the outro/credits segment end is near the video duration, indicating the next episode should play. */
    val isOutroNearEnd: Boolean
        get() {
            val outroEnd = creditSegmentEndTicks ?: return false
            val durationTicks = duration * 10_000
            if (durationTicks <= 0) return false
            // If the outro end is within 30 seconds of the video duration
            return (durationTicks - outroEnd).coerceAtLeast(0) < 300_000_000 // 30s in ticks
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

