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
)

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
