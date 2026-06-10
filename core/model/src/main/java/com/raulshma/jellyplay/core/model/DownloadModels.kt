package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DownloadItem(
    val id: String,
    val mediaItemId: String,
    val name: String,
    val mediaType: MediaType,
    val downloadPath: String,
    val downloadUrl: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val speedBytesPerSec: Long = 0L,
    val mediaSourceId: String? = null,
    val imageUrl: String? = null,
    val imageBlurHash: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
)

@Immutable
@Serializable
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Immutable
@Serializable
data class UserPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val mediaStreamSelections: Map<String, MediaStreamSelection> = emptyMap(),
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val useBottomNav: Boolean = true,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val maxCacheSizeMb: Int = 0,
    val autoDeleteCache: Boolean = true,
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val audioDelayMs: Long = 0L,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGesturesEnabled: Boolean = true,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = false,
    val trailerAutoplay: Boolean = true,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = false,
    val videoBrightnessLevel: Float = 0.5f,
    val audioDefaultSpeed: Float = 1.0f,
    val audioNightModeVolume: Float = 0.4f,
    val audioNightModeGain: Int = 1200,
    val audioSkipPreviousThresholdMs: Long = 3_000L,
    val audioAutoplayNext: Boolean = true,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val replayGainPreAmpDb: Float = 0f,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val sleepTimerDurationMs: Long = 0L,
    val sleepTimerEndOfEpisode: Boolean = false,
    val dreamImageCategories: Set<DreamImageCategory> = setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES),
    val dreamSlideshowIntervalMs: Long = 15_000L,
    val dreamKenBurnsEnabled: Boolean = true,
    val dreamTransitionStyle: DreamTransitionStyle = DreamTransitionStyle.CROSSFADE,
    val dreamShowTitle: Boolean = true,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val lrBalance: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val pitchSemitones: Float = 0f,
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val hiddenLibrarySectionIds: Set<String> = emptySet(),
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
    val performanceMode: Boolean = false,
    val newsletterEnabled: Boolean = true,
    val newsletterDayOfWeek: Int = 7,
    val newsletterLastViewedMs: Long = 0L,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val showAdvancedSettings: Boolean = false,
    val audioVisualizerEnabled: Boolean = false,
    val synthwaveMode: Boolean = false,
    val synthwaveAccent: String = "magenta",
    val soothingMode: Boolean = false,
    val soothingAccent: String = "ocean",
    val monochromeMode: Boolean = false,
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayJoinBehavior.ASK,
    val syncPlayToleranceMs: Long = 100L,
    val syncPlayAutoAcceptInvites: Boolean = false,
    val defaultCastingStrategy: CastingStrategy = CastingStrategy.ASK,
    val backgroundCastingEnabled: Boolean = true,
    val preferredRenderer: String? = null,
    val dvrPrePaddingMinutes: Int = 0,
    val dvrPostPaddingMinutes: Int = 0,
    val dvrRecordingQuality: String = "AUTO",
    val favoriteChannels: Set<String> = emptySet(),
    val enabledNewsletterSections: Set<NewsletterSectionType> = setOf(
        NewsletterSectionType.RECENTLY_ADDED,
        NewsletterSectionType.LIBRARY_STATS,
        NewsletterSectionType.CONTINUE_WATCHING,
        NewsletterSectionType.NEXT_UP,
        NewsletterSectionType.CURATED_PICKS,
        NewsletterSectionType.ACTIVITY_DIGEST
    ),
    val newsletterSectionOrder: List<NewsletterSectionType> = NewsletterSectionType.DEFAULT_ORDER,
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
    val manualBandwidthCap: Long = 0L,
    val meteredNetworkBehavior: MeteredNetworkBehavior = MeteredNetworkBehavior.WARN,
    val adaptiveBitrateEnabled: Boolean = true,
    val backgroundVideoAudioEnabled: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val showUnwatchedBadge: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val showWatchedCheckmark: Boolean = true,
    val defaultLibrarySortOrders: Map<String, String> = emptyMap(),
    val keepScreenOnDuringVideo: Boolean = true,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    val incognitoModeEnabled: Boolean = false,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
    val showShareMediaOption: Boolean = true,
    val showExternalRatings: Boolean = true,
    val dataSaverEnabled: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
    val preferAudioDescription: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val hideSearchHistory: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val tvZoomModePercent: Float = 0f,
    val remoteControlEnabled: Boolean = true,
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val kidsModeEnabled: Boolean = false,
    val kidsModeMaxRating: String = "G",
)

@Immutable
@Serializable
enum class DownloadQuality(val displayName: String) {
    ORIGINAL("Original"),
    HIGH_1080P("High (1080p transcoded)"),
    MEDIUM_720P("Medium (720p)"),
    LOW_480P("Low (480p)"),
}

@Immutable
@Serializable
data class MediaStreamSelection(
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)

@Immutable
@Serializable
enum class HomeMode {
    VIDEO,
    MUSIC,
}

@Immutable
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Immutable
@Serializable
enum class ColorStyle(val displayName: String) {
    TONAL_SPOT("Tonal Spot"),
    VIBRANT("Vibrant"),
    EXPRESSIVE("Expressive"),
    MUTED("Muted"),
    MONOCHROME("Monochrome"),
}

@Immutable
@Serializable
enum class ContrastLevel {
    DEFAULT,
    MEDIUM,
    HIGH,
}

@Immutable
@Serializable
enum class PlayerType(val displayName: String, val description: String) {
    EXO_PLAYER("ExoPlayer", "Built-in Media3 player with full controls"),
    MPV("mpv", "Embedded libmpv engine with broad codec & HDR support"),
    LIBVLC("LibVLC", "Embedded VLC engine for maximum format compatibility"),
    EXTERNAL("External", "Open in an external app (e.g. MX Player)"),
    ;

    companion object {
        /** Migrate legacy stored values from the old INTERNAL/EXTERNAL enum. */
        fun fromStoredName(name: String): PlayerType = when (name) {
            "INTERNAL" -> EXO_PLAYER
            else -> entries.find { it.name == name } ?: EXO_PLAYER
        }
    }
}

@Immutable
@Serializable
data class SubtitleStyle(
    val fontSize: Int = 24,
    val fontColor: SubtitleColor = SubtitleColor.WHITE,
    val backgroundColor: SubtitleColor = SubtitleColor.BLACK,
    val backgroundOpacity: Float = 0.6f,
    val edgeType: SubtitleEdgeType = SubtitleEdgeType.NONE,
    val edgeColor: SubtitleColor = SubtitleColor.BLACK,
    val offsetMs: Long = 0L,
    val verticalPosition: Float = 0.05f,
)

@Immutable
@Serializable
enum class DecoderMode(val displayName: String) {
    HW_PREFERRED("Hardware (Preferred)"),
    HW_ONLY("Hardware Only"),
    SW_ONLY("Software Only"),
}

@Immutable
@Serializable
enum class SubtitleColor(val value: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFFF00.toInt()),
    GREEN(0xFF00FF00.toInt()),
    CYAN(0xFF00FFFF.toInt()),
    RED(0xFFFF0000.toInt()),
    BLACK(0xFF000000.toInt()),
    BLUE(0xFF0000FF.toInt()),
}

@Immutable
@Serializable
enum class SubtitleEdgeType {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED,
}

@Immutable
@Serializable
enum class StreamingQuality {
    AUTO,
    LOW_360P,
    SD_480P,
    HD_720P,
    FHD_1080P,
    UHD_4K,
}

@Immutable
@Serializable
data class EqualizerSettings(
    val bandLevels: List<Int> = List(10) { 0 },
) {
    companion object {
        val BAND_FREQUENCIES = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
    }
}

@Immutable
@Serializable
enum class EffectStrength(val displayName: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
}

@Immutable
@Serializable
enum class OrientationMode(val displayName: String, val constant: String) {
    SENSOR_LANDSCAPE("Landscape", "sensor_landscape"),
    SENSOR_PORTRAIT("Portrait", "sensor_portrait"),
    SENSOR("Auto Rotate", "sensor"),
    LOCKED_LANDSCAPE("Locked Landscape", "locked_landscape"),
    LOCKED_PORTRAIT("Locked Portrait", "locked_portrait"),
}

@Immutable
@Serializable
enum class PreloadBufferSize(
    val displayName: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
) {
    LOW("Low (10s)", 10_000, 25_000),
    MEDIUM("Medium (25s)", 25_000, 50_000),
    HIGH("High (50s)", 50_000, 120_000),
    UNLIMITED("Unlimited", 50_000, 500_000),
}

@Immutable
@Serializable
enum class SyncPlayJoinBehavior(val displayName: String) {
    ALWAYS_JOIN("Always Join"),
    ASK("Always Ask"),
    NEVER_JOIN("Never Join"),
}

@Immutable
@Serializable
enum class CastingStrategy(val displayName: String) {
    PREFER_CAST("Prefer Google Cast"),
    PREFER_DLNA("Prefer DLNA"),
    ASK("Always Ask"),
}

@Immutable
@Serializable
enum class MeteredNetworkBehavior(val displayName: String) {
    ALLOW("Allow Connection"),
    WARN("Warn Before Streaming"),
    BLOCK("Block Streaming"),
}

