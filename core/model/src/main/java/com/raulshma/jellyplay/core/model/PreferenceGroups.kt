package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class VideoPlayerPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    /**
     * Granular refresh-rate / resolution switching mode. Supersedes
     * [frameRateMatching] (which stays as a legacy boolean alias: `true` ≈
     * [RefreshRateMode.FRAME_RATE_ONLY]). When both are set, this mode wins.
     */
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGesturesEnabled: Boolean = true,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = true,
    val trailerAutoplay: Boolean = true,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = true,
    val videoBrightnessLevel: Float = 0.5f,
    val videoGestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val keepScreenOnDuringVideo: Boolean = true,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
    val backgroundVideoAudioEnabled: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val reduceMotionEnabled: Boolean = false,
    val preferAudioDescription: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val tvZoomModePercent: Float = 0f,
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
)

@Immutable
@Serializable
data class AudioPlayerPreferences(
    val audioDefaultSpeed: Float = 1.0f,
    val audioNightModeVolume: Float = 0.4f,
    val audioNightModeGain: Int = 1200,
    val audioSkipPreviousThresholdMs: Long = 3_000L,
    val audioAutoplayNext: Boolean = true,
    val audioPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val replayGainPreAmpDb: Float = 0f,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val lrBalance: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val pitchSemitones: Float = 0f,
    val audioDelayMs: Long = 0L,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioVisualizerEnabled: Boolean = false,
)

@Immutable
@Serializable
data class SubtitlePreferences(
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
)

@Immutable
@Serializable
data class SecurityPreferences(
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val incognitoModeEnabled: Boolean = false,
    val remoteControlEnabled: Boolean = true,
)

@Immutable
@Serializable
data class DownloadPreferences(
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
)

@Immutable
@Serializable
data class SyncPlayPreferences(
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayJoinBehavior.ASK,
    val syncPlayToleranceMs: Long = 100L,
    val syncPlayAutoAcceptInvites: Boolean = false,
)

@Immutable
@Serializable
data class AppearancePreferences(
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val synthwaveMode: Boolean = false,
    val synthwaveAccent: String = "magenta",
    val soothingMode: Boolean = false,
    val soothingAccent: String = "ocean",
    val monochromeMode: Boolean = false,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
)

// ---------------------------------------------------------------------------
// Per-screen preference slices.
//
// The seven slices above group fields by *logical domain* but several settings
// screens read fields that span domains (e.g. the Playback screen shows casting
// + DVR + syncplay + video settings; the Appearance screen shows theme + home
// layout + newsletter settings). Each slice below is the *exact* set of fields
// one settings sub-screen reads, so collecting it recomposes only when one of
// that screen's fields changes. Field names are identical to [UserPreferences]
// on purpose: that keeps the screen bodies (`preferences.X`) untouched when a
// screen swaps from the whole [UserPreferences] to its slice.
//
// A field that two screens both display (e.g. `dialogueBoostEnabled` appears on
// both Playback and Audio) is projected into both slices. A write to such a
// field legitimately recomposes both screens; `distinctUntilChanged` keeps each
// slice de-duplicated.
// ---------------------------------------------------------------------------

/** Fields read by `PlaybackSettingsScreen`. */
@Immutable
@Serializable
data class PlaybackPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    /**
     * Granular refresh-rate / resolution switching mode. Supersedes
     * [frameRateMatching] (which stays as a legacy boolean alias: `true` ≈
     * [RefreshRateMode.FRAME_RATE_ONLY]). When both are set, this mode wins.
     */
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGesturesEnabled: Boolean = true,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = true,
    val trailerAutoplay: Boolean = true,
    val cinemaModeEnabled: Boolean = false,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = true,
    val videoBrightnessLevel: Float = 0.5f,
    val videoGestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val videoSkipBackOnResumeMs: Long = 0L,
    val videoPassOutProtectionHours: Int = 0,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val keepScreenOnDuringVideo: Boolean = true,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val duckOnTransientFocusLoss: Boolean = false,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val audioDelayMs: Long = 0L,
    val backgroundVideoAudioEnabled: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val incognitoModeEnabled: Boolean = false,
    val showClockInPlayer: Boolean = false,
    val tvZoomModePercent: Float = 0f,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayJoinBehavior.ASK,
    val syncPlayToleranceMs: Long = 100L,
    val syncPlayAutoAcceptInvites: Boolean = false,
    val defaultCastingStrategy: CastingStrategy = CastingStrategy.ASK,
    val backgroundCastingEnabled: Boolean = true,
    val preferredRenderer: String? = null,
    val dvrPrePaddingMinutes: Int = 0,
    val dvrPostPaddingMinutes: Int = 0,
    val dvrRecordingQuality: String = "AUTO",
    val androidTvWatchNextEnabled: Boolean = true,
)

/** Fields read by `AudioSettingsScreen`. */
@Immutable
@Serializable
data class AudioPreferences(
    val audioDefaultSpeed: Float = 1.0f,
    val audioNightModeVolume: Float = 0.4f,
    val audioNightModeGain: Int = 1200,
    val audioSkipPreviousThresholdMs: Long = 3_000L,
    val audioAutoplayNext: Boolean = true,
    val audioPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val replayGainPreAmpDb: Float = 0f,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val lrBalance: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val pitchSemitones: Float = 0f,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioVisualizerEnabled: Boolean = false,
    val audioCachingEnabled: Boolean = true,
    val audioCacheSizeMb: Int = 1024,
    val audioPrefetchLookahead: Int = 3,
    val audioPrefetchBackfill: Int = 5,
    val audioCacheNetworkPolicy: AudioCacheNetworkPolicy = AudioCacheNetworkPolicy.WIFI_ONLY,
    val sleepTimerDurationMs: Long = 0L,
    val preferAudioDescription: Boolean = false,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
)

/** Fields read by `StorageSettingsScreen`. */
@Immutable
@Serializable
data class StoragePreferences(
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val maxConcurrentDownloads: Int = 3,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
    val maxCacheSizeMb: Int = 0,
    val autoDeleteCache: Boolean = true,
    val cellularDownloadSizeWarningMb: Int = 0,
    val downloadScheduleEnabled: Boolean = false,
    val downloadScheduleWindow: DownloadScheduleWindow = DownloadScheduleWindow(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val meteredNetworkBehavior: MeteredNetworkBehavior = MeteredNetworkBehavior.WARN,
    val adaptiveBitrateEnabled: Boolean = true,
    val manualBandwidthCap: Long = 0L,
    val dataSaverEnabled: Boolean = false,
    val verboseNetworkLogging: Boolean = false,
    val networkTimeoutPreset: NetworkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
    val userDataSyncEnabled: Boolean = true,
)

/** Fields read by `NavigationCustomizationGroup`. */
@Immutable
@Serializable
data class NavigationCustomizationPreferences(
    val hiddenNavItems: Set<String> = emptySet(),
    val navItemOrder: List<String> = emptyList(),
    val hideBottomNavOnScroll: Boolean = true,
    val navigationStyle: NavigationStyle = NavigationStyle.CLASSIC,
)

/** Fields read by `LanguageSettingsScreen`. */
@Immutable
@Serializable
data class LanguagePreferences(
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val subtitlesForcedOnly: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val pgsSubtitleDirectPlay: Boolean = false,
    val hdrSubtitleStyleEnabled: Boolean = false,
    val hdrSubtitleStyle: SubtitleStyle = SubtitleStyle(
        fontSize = 28,
        backgroundOpacity = 0.5f,
        edgeType = SubtitleEdgeType.OUTLINE,
    ),
    val appLanguage: String? = null,
)

/** Fields read by `ExperimentalSettingsScreen`. */
@Immutable
@Serializable
data class ExperimentalPreferences(
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
)

/**
 * Fields read by `AppearanceSettingsScreen`. This is the broadest slice because
 * the Appearance screen surfaces theme, home layout, discovery, newsletter, and
 * accessibility settings together. Navigation-customization fields are excluded
 * — they live in [navigationCustomization].
 */
@Immutable
@Serializable
data class AppearanceScreenPreferences(
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val synthwaveMode: Boolean = false,
    val synthwaveAccent: String = "magenta",
    val soothingMode: Boolean = false,
    val soothingAccent: String = "ocean",
    val monochromeMode: Boolean = false,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val reduceMotionEnabled: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val appFontScale: AppFontScale = AppFontScale.DEFAULT,
    val dateFormatPreference: DateFormatPreference = DateFormatPreference.SYSTEM,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val handMode: HandMode = HandMode.RIGHT,
    val hapticsEnabled: Boolean = true,
    val scheduledThemeStartHour: Int = 22,
    val scheduledThemeEndHour: Int = 7,
    val backdropThemeMusicEnabled: Boolean = false,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val showUnwatchedBadge: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val showWatchedCheckmark: Boolean = true,
    val hideEpisodeThumbnails: Boolean = false,
    val skipSpecials: Boolean = false,
    val compactEpisodeList: Boolean = false,
    val showExternalRatings: Boolean = true,
    val showShareMediaOption: Boolean = true,
    val hideSearchHistory: Boolean = false,
    val showClockOnHome: Boolean = false,
    /** Show settings search results alongside media in the home search bar. */
    val showSettingsInHomeSearch: Boolean = true,
    val newsletterEnabled: Boolean = true,
    val newsletterDayOfWeek: Int = 7,
    val enabledNewsletterSections: Set<NewsletterSectionType> = setOf(
        NewsletterSectionType.RECENTLY_ADDED,
        NewsletterSectionType.LIBRARY_STATS,
        NewsletterSectionType.CONTINUE_WATCHING,
        NewsletterSectionType.NEXT_UP,
        NewsletterSectionType.CURATED_PICKS,
        NewsletterSectionType.ACTIVITY_DIGEST,
    ),
    val newsletterSectionOrder: List<NewsletterSectionType> = NewsletterSectionType.DEFAULT_ORDER,
)
