package com.raulshma.jellyplay.core.model.legacy

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.*
import kotlinx.serialization.Serializable

/**
 * Legacy v0/v1 backup aggregate shape. **Decode-only — do not extend.**
 *
 * Historically this was the single ~150-field preference aggregate. The live
 * read/write path is now the 18 domain stores (`core/datastore/.../<domain>`)
 * and the [com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections]
 * read-layer. This model survives only as: (1) the v0/v1 backup decode target
 * in `SettingsViewModel` import, and (2) the diff snapshot shape constructed
 * one-shot by `FactoryResetViewModel.buildFromSlices`. No screen reads it live.
 *
 * This type survives solely so the v0/v1 backup-import path can decode a
 * legacy JSON blob and fan its fields back to the per-store `restorePreferences`
 * overloads. New preferences belong on a domain slice — never here.
 */
@Immutable
@Serializable
data class UserPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val preferredSubtitleLanguage: String? = null,
    val subtitlesForcedOnly: Boolean = false,
    val preferredAudioLanguage: String? = null,
    val mediaStreamSelections: Map<String, MediaStreamSelection> = emptyMap(),
    val videoEffectsByItem: Map<String, VideoEffectsConfig> = emptyMap(),
    /**
     * Per-item subtitle-sync delay (ms), keyed by itemId. Lets a user's sync
     * correction for a badly-timed subtitle track survive a re-watch / resume,
     * instead of resetting to the global default each time (G9). A missing key
     * falls back to the global `subtitleStyle.offsetMs`.
     */
    val subtitleDelayByItem: Map<String, Long> = emptyMap(),
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    val maxCacheSizeMb: Int = 0,
    val autoDeleteCache: Boolean = true,
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val pinFailedAttempts: Int = 0,
    val pinLockoutUntilEpochMs: Long = 0L,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val audioDelayMs: Long = 0L,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGesturesEnabled: Boolean = true,
    val videoPassOutProtectionHours: Int = 0,
    val videoSkipBackOnResumeMs: Long = 0L,
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
    val videoRememberVolume: Boolean = true,
    val videoVolumeLevel: Float = 1.0f,
    val videoAutoSkipIntro: Boolean = false,
    val videoAutoSkipOutro: Boolean = false,
    val videoRememberMuted: Boolean = true,
    val videoMuted: Boolean = false,
    val subtitlePreviewInSettings: Boolean = true,
    val videoGestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
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
    val audioCachingEnabled: Boolean = true,
    val audioCacheSizeMb: Int = 1024,
    val audioPrefetchLookahead: Int = 3,
    val audioPrefetchBackfill: Int = 5,
    val audioCacheNetworkPolicy: AudioCacheNetworkPolicy = AudioCacheNetworkPolicy.WIFI_ONLY,
    val audioCacheCellularMonthlyCapMb: Int = 500,
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
    /**
     * Maximum number of downloads allowed to run concurrently. WorkManager may
     * enqueue many workers; this cap gates how many actually transfer at once
     * (the rest block on a permit). Distinct from [downloadConnections], which
     * is per-file parallel streams.
     */
    val maxConcurrentDownloads: Int = 3,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val navBarShowLabels: Boolean = true,
    /**
     * Whether the floating navigation bar auto-hides on scroll-down. When false
     * the bar stays pinned.
     */
    val hideBottomNavOnScroll: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    /**
     * Whether the home screen renders an ambient backdrop behind its content:
     * the hero artwork's BlurHash when available, otherwise an animated
     * palette-derived ambient gradient. Falls back to the flat background
     * colour when disabled or in performance mode.
     */
    val homeBackdropEnabled: Boolean = true,
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
    /**
     * Whether the lyrics overlay is visible on the audio player. Persisted so a
     * user who prefers to read along doesn't have to re-enable it per session.
     */
    val audioLyricsVisible: Boolean = false,
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
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val showWatchedCheckmark: Boolean = true,
    val defaultLibrarySortOrders: Map<String, String> = emptyMap(),
    val libraryViewModes: Map<String, String> = emptyMap(),
    val libraryFilters: Map<String, String> = emptyMap(),
    val keepScreenOnDuringVideo: Boolean = true,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    val incognitoModeEnabled: Boolean = false,
    val showTimeRemaining: Boolean = false,
    val showClockOnHome: Boolean = false,
    val showClockInPlayer: Boolean = false,
    /** Show settings search results alongside media in the home search bar. */
    val showSettingsInHomeSearch: Boolean = true,
    val pauseOnAudioFocusLoss: Boolean = true,
    val duckOnTransientFocusLoss: Boolean = false,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
    val showShareMediaOption: Boolean = true,
    val showExternalRatings: Boolean = true,
    val dataSaverEnabled: Boolean = false,
    val verboseNetworkLogging: Boolean = false,
    val networkTimeoutPreset: NetworkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
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
    val androidTvWatchNextEnabled: Boolean = true,
    val userDataSyncEnabled: Boolean = true,
    val appLanguage: String? = null,
    val pgsSubtitleDirectPlay: Boolean = false,
    val hdrSubtitleStyleEnabled: Boolean = false,
    val hdrSubtitleStyle: SubtitleStyle = SubtitleStyle(
        fontSize = 28,
        backgroundOpacity = 0.5f,
        edgeType = SubtitleEdgeType.OUTLINE,
    ),
    val backdropThemeMusicEnabled: Boolean = false,
    val hiddenNavItems: Set<String> = emptySet(),
    val navItemOrder: List<String> = emptyList(),
    val selfUpdateCheckEnabled: Boolean = true,
    /**
     * Version of the last update the user dismissed via "Later"/"Close", plus
     * the wall-clock millis at which it was dismissed. Used to suppress the
     * launch-time auto-prompt for the same version for 24 hours. Manual checks
     * (Settings → Check for updates) ignore this and always surface the result.
     */
    val dismissedUpdateVersion: String? = null,
    val dismissedUpdateAtMs: Long = 0L,
    /**
     * Cached Jellyfin playlist id backing the pinned "Watch Later" row in the
     * Add-to-Playlist picker. `null` until the first time the user adds to
     * Watch Later, at which point the playlist is created and its id stored
     * here so subsequent adds reuse it instead of creating duplicates.
     */
    val watchLaterPlaylistId: String? = null,
    val hideEpisodeThumbnails: Boolean = false,
    val episodesDescending: Boolean = true,
    val skipSpecials: Boolean = false,
    val compactEpisodeList: Boolean = false,
    val cellularDownloadSizeWarningMb: Int = 0,
    val hapticsEnabled: Boolean = true,
    val dateFormatPreference: DateFormatPreference = DateFormatPreference.SYSTEM,
    val appFontScale: AppFontScale = AppFontScale.DEFAULT,
    val scheduledThemeStartHour: Int = 22,
    val scheduledThemeEndHour: Int = 7,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val handMode: HandMode = HandMode.RIGHT,
    val downloadScheduleEnabled: Boolean = false,
    val downloadScheduleWindow: DownloadScheduleWindow = DownloadScheduleWindow(),
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
) {
    /**
     * Returns the subtitle style actually applied to the player, honouring the
     * [highContrastSubtitles] accessibility override and the optional HDR
     * subtitle style. When high-contrast mode is enabled, the user's custom
     * style is replaced with a maximally legible preset (large yellow text,
     * opaque black background, thick black outline) regardless of their
     * per-colour selections. The user's underlying style is preserved in
     * [subtitleStyle] so disabling the toggle restores it.
     *
     * When [isHdr] is true (caller-derived from the active video stream),
     * [hdrSubtitleStyleEnabled] is on and [highContrastSubtitles] is off,
     * [hdrSubtitleStyle] is returned instead so HDR content can use a more
     * legible colour configuration.
     */
    fun resolvedSubtitleStyle(isHdr: Boolean = false): SubtitleStyle = when {
        highContrastSubtitles -> SubtitleStyle(
            applyCustomStyle = true,
            fontSize = (subtitleStyle.fontSize.coerceAtLeast(24) + 4).coerceAtMost(48),
            fontColor = SubtitleColor.YELLOW,
            backgroundColor = SubtitleColor.BLACK,
            backgroundOpacity = 1.0f,
            edgeType = SubtitleEdgeType.OUTLINE,
            edgeColor = SubtitleColor.BLACK,
            offsetMs = subtitleStyle.offsetMs,
            verticalPosition = subtitleStyle.verticalPosition,
        )
        isHdr && hdrSubtitleStyleEnabled -> hdrSubtitleStyle.copy(applyCustomStyle = true)
        else -> subtitleStyle  // respect the user's "Override Subtitle Styles" toggle
    }

    /**
     * Returns `true` when any of the supplied media streams represents HDR
     * content (HDR10/HDR10+/HLG/Dolby Vision) — used by callers to decide
     * whether to switch to the HDR subtitle style.
     */
    fun isHdrFromStreams(streams: List<com.raulshma.jellyplay.core.model.MediaStream>): Boolean {
        return streams.any { stream ->
            val range = stream.videoRange?.lowercase()
            val rangeType = stream.videoRangeType?.lowercase()
            val raw = (range ?: "") + " " + (rangeType ?: "")
            raw.contains("hdr") || raw.contains("hlg") || raw.contains("dovi") || raw.contains("dolbyvision")
        }
    }
}
