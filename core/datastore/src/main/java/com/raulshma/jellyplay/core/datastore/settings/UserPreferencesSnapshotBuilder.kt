package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.json.JsonElement

/**
 * Builds a one-shot [UserPreferences] snapshot from the 18 domain-store slices
 * plus [AppRuntimeState] and [PinLockoutState]. Pure (no stores, no IO): the
 * caller reads each slice once via `.first()` and hands the values in. Extracted
 * from `FactoryResetViewModel.buildFromSlices` so the mapping is independently
 * testable and the VM is left with just the slice reads.
 *
 * Field mapping is a verbatim port of the former `buildUserPreferences`
 * aggregate projection — every slice field flows to the same
 * [UserPreferences] arg it always did.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun buildUserPreferencesSnapshot(
    playback: PlaybackSlice,
    videoPlayer: VideoPlayerSlice,
    engine: PlayerEngineSlice,
    subtitle: SubtitleSlice,
    audio: AudioSlice,
    audioEffects: AudioEffectsSlice,
    audioCache: AudioCacheSlice,
    appearance: AppearanceSlice,
    homeDiscovery: HomeDiscoverySlice,
    library: LibrarySlice,
    navigation: NavigationSlice,
    downloads: DownloadsSlice,
    networkOffline: NetworkOfflineSlice,
    notification: NotificationSlice,
    syncPlayCast: SyncPlayCastSlice,
    screensaver: ScreensaverSlice,
    security: SecuritySlice,
    experimental: ExperimentalSlice,
    runtime: AppRuntimeState,
    pinLockout: PinLockoutState,
): UserPreferences = UserPreferences(
    preferredPlayer = playback.preferredPlayer,
    preferredSubtitleLanguage = subtitle.preferredSubtitleLanguage,
    subtitlesForcedOnly = subtitle.subtitlesForcedOnly,
    preferredAudioLanguage = subtitle.preferredAudioLanguage,
    mediaStreamSelections = engine.mediaStreamSelections,
    videoEffectsByItem = engine.videoEffectsByItem,
    subtitleDelayByItem = subtitle.subtitleDelayByItem,
    dynamicTheming = appearance.dynamicTheming,
    themeMode = appearance.themeMode,
    contrastLevel = appearance.contrastLevel,
    oledMode = appearance.oledMode,
    subtitleStyle = subtitle.subtitleStyle,
    streamingQuality = playback.streamingQuality,
    playbackMode = playback.playbackMode,
    liveStreamOption = playback.liveStreamOption,
    maxCacheSizeMb = networkOffline.maxCacheSizeMb,
    autoDeleteCache = networkOffline.autoDeleteCache,
    pinLockEnabled = security.pinLockEnabled,
    pinHash = security.pinHash,
    biometricLockEnabled = security.biometricLockEnabled,
    usePinForPlayerLock = security.usePinForPlayerLock,
    autoLockTimerMs = security.autoLockTimerMs,
    pinFailedAttempts = pinLockout.failedAttempts,
    pinLockoutUntilEpochMs = pinLockout.lockoutUntilEpochMs,
    dialogueBoostEnabled = audioEffects.dialogueBoostEnabled,
    dialogueBoostStrength = audioEffects.dialogueBoostStrength,
    equalizerEnabled = audioEffects.equalizerEnabled,
    equalizerSettings = audioEffects.equalizerSettings,
    audioDelayMs = audio.audioDelayMs,
    decoderMode = playback.decoderMode,
    audioPassthrough = playback.audioPassthrough,
    frameRateMatching = playback.frameRateMatching,
    refreshRateMode = playback.refreshRateMode,
    nightModeEnabled = audioEffects.nightModeEnabled,
    nightModeStrength = audioEffects.nightModeStrength,
    homeMode = homeDiscovery.homeMode,
    videoSeekDurationMs = videoPlayer.videoSeekDurationMs,
    videoDefaultOrientation = videoPlayer.videoDefaultOrientation,
    videoControlsTimeoutMs = videoPlayer.videoControlsTimeoutMs,
    videoGesturesEnabled = videoPlayer.videoGesturesEnabled,
    videoPassOutProtectionHours = videoPlayer.videoPassOutProtectionHours,
    videoSkipBackOnResumeMs = videoPlayer.videoSkipBackOnResumeMs,
    videoHoldSpeedEnabled = videoPlayer.videoHoldSpeedEnabled,
    videoHoldSpeedMultiplier = videoPlayer.videoHoldSpeedMultiplier,
    videoDefaultSpeed = videoPlayer.videoDefaultSpeed,
    videoDefaultAspectRatio = videoPlayer.videoDefaultAspectRatio,
    videoAutoplayNext = videoPlayer.videoAutoplayNext,
    trailerAutoplay = videoPlayer.trailerAutoplay,
    cinemaModeEnabled = videoPlayer.cinemaModeEnabled,
    videoSwipeSeekMaxMs = videoPlayer.videoSwipeSeekMaxMs,
    videoRememberBrightness = videoPlayer.videoRememberBrightness,
    videoBrightnessLevel = videoPlayer.videoBrightnessLevel,
    videoAutoSkipIntro = videoPlayer.videoAutoSkipIntro,
    videoAutoSkipOutro = videoPlayer.videoAutoSkipOutro,
    videoRememberMuted = videoPlayer.videoRememberMuted,
    videoMuted = videoPlayer.videoMuted,
    subtitlePreviewInSettings = subtitle.subtitlePreviewInSettings,
    videoGestureIndicatorSide = videoPlayer.videoGestureIndicatorSide,
    audioDefaultSpeed = audio.audioDefaultSpeed,
    audioNightModeVolume = audio.audioNightModeVolume,
    audioNightModeGain = audio.audioNightModeGain,
    audioSkipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs,
    audioAutoplayNext = audio.audioAutoplayNext,
    trickplayEnabled = videoPlayer.trickplayEnabled,
    trickplayOnSeekGesture = videoPlayer.trickplayOnSeekGesture,
    segmentBehaviors = videoPlayer.segmentBehaviors,
    videoEpisodeBrowserEnabled = videoPlayer.videoEpisodeBrowserEnabled,
    videoShowPlaybackMetadata = videoPlayer.videoShowPlaybackMetadata,
    videoPreloadBufferSize = videoPlayer.videoPreloadBufferSize,
    audioPreloadBufferSize = audio.audioPreloadBufferSize,
    audioNormalizationMode = audio.audioNormalizationMode,
    audioNormalizationEnabled = audio.audioNormalizationEnabled,
    replayGainPreAmpDb = audio.replayGainPreAmpDb,
    channelMixMode = audio.channelMixMode,
    channelMixEnabled = audio.channelMixEnabled,
    audioGaplessEnabled = audio.audioGaplessEnabled,
    audioCrossfadeDurationMs = audio.audioCrossfadeDurationMs,
    audioCachingEnabled = audioCache.audioCachingEnabled,
    audioCacheSizeMb = audioCache.audioCacheSizeMb,
    audioPrefetchLookahead = audioCache.audioPrefetchLookahead,
    audioPrefetchBackfill = audioCache.audioPrefetchBackfill,
    audioCacheNetworkPolicy = audioCache.audioCacheNetworkPolicy,
    audioCacheCellularMonthlyCapMb = audioCache.audioCacheCellularMonthlyCapMb,
    sleepTimerDurationMs = audio.sleepTimerDurationMs,
    sleepTimerEndOfEpisode = audio.sleepTimerEndOfEpisode,
    dreamImageCategories = screensaver.dreamImageCategories,
    dreamSlideshowIntervalMs = screensaver.dreamSlideshowIntervalMs,
    dreamKenBurnsEnabled = screensaver.dreamKenBurnsEnabled,
    dreamTransitionStyle = screensaver.dreamTransitionStyle,
    dreamShowTitle = screensaver.dreamShowTitle,
    equalizerPreset = audioEffects.equalizerPreset,
    bassBoostEnabled = audioEffects.bassBoostEnabled,
    bassBoostStrength = audioEffects.bassBoostStrength,
    virtualizerEnabled = audioEffects.virtualizerEnabled,
    virtualizerStrength = audioEffects.virtualizerStrength,
    reverbPreset = audioEffects.reverbPreset,
    lrBalance = audioEffects.lrBalance,
    autoEqByGenre = audioEffects.autoEqByGenre,
    pitchSemitones = audioEffects.pitchSemitones,
    wifiOnlyDownloads = downloads.wifiOnlyDownloads,
    downloadConnections = downloads.downloadConnections,
    maxConcurrentDownloads = downloads.maxConcurrentDownloads,
    enabledHomeSectionTypes = homeDiscovery.enabledHomeSectionTypes,
    homeSectionOrder = homeDiscovery.homeSectionOrder,
    libraryHomeSectionOverrides = homeDiscovery.libraryHomeSectionOverrides,
    navBarShowLabels = navigation.navBarShowLabels,
    hideBottomNavOnScroll = navigation.hideBottomNavOnScroll,
    homeHeroEnabled = homeDiscovery.homeHeroEnabled,
    homeBackdropEnabled = homeDiscovery.homeBackdropEnabled,
    hideTopHeaderOnScroll = homeDiscovery.hideTopHeaderOnScroll,
    onboardingCompleted = runtime.onboardingCompleted,
    mpvConfig = engine.mpvConfig,
    libVlcConfig = engine.libVlcConfig,
    exoPlayerConfig = engine.exoPlayerConfig,
    performanceMode = appearance.performanceMode,
    newsletterEnabled = notification.newsletterEnabled,
    newsletterDayOfWeek = notification.newsletterDayOfWeek,
    newsletterLastViewedMs = notification.newsletterLastViewedMs,
    accentColorSwatch = appearance.accentColorSwatch,
    colorStyle = appearance.colorStyle,
    libraryViewMode = library.libraryViewMode,
    notificationPreferences = notification.notificationPreferences,
    showAdvancedSettings = appearance.showAdvancedSettings,
    audioVisualizerEnabled = audio.audioVisualizerEnabled,
    audioLyricsVisible = audio.audioLyricsVisible,
    synthwaveMode = appearance.themeVariant == "synthwave",
    synthwaveAccent = appearance.synthwaveAccent,
    soothingMode = appearance.themeVariant == "soothing",
    soothingAccent = appearance.soothingAccent,
    monochromeMode = appearance.themeVariant == "monochrome",
    syncPlayJoinBehavior = syncPlayCast.syncPlayJoinBehavior,
    syncPlayToleranceMs = syncPlayCast.syncPlayToleranceMs,
    syncPlayAutoAcceptInvites = syncPlayCast.syncPlayAutoAcceptInvites,
    defaultCastingStrategy = syncPlayCast.defaultCastingStrategy,
    backgroundCastingEnabled = syncPlayCast.backgroundCastingEnabled,
    preferredRenderer = syncPlayCast.preferredRenderer,
    dvrPrePaddingMinutes = syncPlayCast.dvrPrePaddingMinutes,
    dvrPostPaddingMinutes = syncPlayCast.dvrPostPaddingMinutes,
    dvrRecordingQuality = syncPlayCast.dvrRecordingQuality,
    favoriteChannels = runtime.favoriteChannels,
    enabledNewsletterSections = notification.enabledNewsletterSections,
    newsletterSectionOrder = notification.newsletterSectionOrder,
    manualOfflineEnabled = networkOffline.manualOfflineEnabled,
    autoOfflineEnabled = networkOffline.autoOfflineEnabled,
    manualBandwidthCap = networkOffline.manualBandwidthCap,
    meteredNetworkBehavior = networkOffline.meteredNetworkBehavior,
    adaptiveBitrateEnabled = networkOffline.adaptiveBitrateEnabled,
    backgroundVideoAudioEnabled = playback.backgroundVideoAudioEnabled,
    autoPlayCountdownSec = playback.autoPlayCountdownSec,
    showUnwatchedBadge = homeDiscovery.showUnwatchedBadge,
    hideWatchedItems = homeDiscovery.hideWatchedItems,
    mergeContinueWatchingAndNextUp = homeDiscovery.mergeContinueWatchingAndNextUp,
    nextUpMaxDays = homeDiscovery.nextUpMaxDays,
    nextUpRewatching = homeDiscovery.nextUpRewatching,
    nextUpExcludedSeriesIds = homeDiscovery.nextUpExcludedSeriesIds,
    hiddenCwItemIds = homeDiscovery.hiddenCwItemIds,
    pinnedHomeSections = homeDiscovery.pinnedHomeSections,
    homeLayoutPresets = homeDiscovery.homeLayoutPresets,
    continueWatchingClickBehavior = homeDiscovery.continueWatchingClickBehavior,
    cellularStreamingQuality = playback.cellularStreamingQuality,
    showWatchedCheckmark = homeDiscovery.showWatchedCheckmark,
    defaultLibrarySortOrders = library.defaultLibrarySortOrders,
    libraryViewModes = library.libraryViewModes,
    libraryFilters = library.libraryFilters,
    keepScreenOnDuringVideo = playback.keepScreenOnDuringVideo,
    downloadQuality = downloads.downloadQuality,
    smartDownloadsEnabled = downloads.smartDownloadsEnabled,
    autoDownloadNewEpisodes = downloads.autoDownloadNewEpisodes,
    incognitoModeEnabled = videoPlayer.incognitoModeEnabled,
    showTimeRemaining = videoPlayer.showTimeRemaining,
    showClockOnHome = homeDiscovery.showClockOnHome,
    showClockInPlayer = videoPlayer.showClockInPlayer,
    showSettingsInHomeSearch = homeDiscovery.showSettingsInHomeSearch,
    pauseOnAudioFocusLoss = playback.pauseOnAudioFocusLoss,
    duckOnTransientFocusLoss = playback.duckOnTransientFocusLoss,
    volumeBoostEnabled = audioEffects.volumeBoostEnabled,
    volumeBoostGain = audioEffects.volumeBoostGain,
    showShareMediaOption = experimental.showShareMediaOption,
    showExternalRatings = homeDiscovery.showExternalRatings,
    dataSaverEnabled = networkOffline.dataSaverEnabled,
    verboseNetworkLogging = networkOffline.verboseNetworkLogging,
    networkTimeoutPreset = networkOffline.networkTimeoutPreset,
    reduceMotionEnabled = appearance.reduceMotionEnabled,
    preferAudioDescription = experimental.preferAudioDescription,
    highContrastSubtitles = subtitle.highContrastSubtitles,
    hideSearchHistory = experimental.hideSearchHistory,
    blueLightFilterEnabled = appearance.blueLightFilterEnabled,
    blueLightFilterStrength = appearance.blueLightFilterStrength,
    tvZoomModePercent = videoPlayer.tvZoomModePercent,
    remoteControlEnabled = security.remoteControlEnabled,
    maxDownloadStorageGb = downloads.maxDownloadStorageGb,
    downloadStorageLocation = downloads.downloadStorageLocation,
    androidTvWatchNextEnabled = playback.androidTvWatchNextEnabled,
    userDataSyncEnabled = playback.userDataSyncEnabled,
    appLanguage = experimental.appLanguage,
    pgsSubtitleDirectPlay = playback.pgsSubtitleDirectPlay,
    hdrSubtitleStyleEnabled = subtitle.hdrSubtitleStyleEnabled,
    hdrSubtitleStyle = subtitle.hdrSubtitleStyle,
    backdropThemeMusicEnabled = appearance.backdropThemeMusicEnabled,
    hiddenNavItems = navigation.hiddenNavItems,
    navItemOrder = navigation.navItemOrder,
    selfUpdateCheckEnabled = experimental.selfUpdateCheckEnabled,
    selfUpdateDownloadEnabled = experimental.selfUpdateDownloadEnabled,
    dismissedUpdateVersion = experimental.dismissedUpdateVersion,
    dismissedUpdateAtMs = experimental.dismissedUpdateAtMs,
    updateDismissPeriod = experimental.updateDismissPeriod,
    watchLaterPlaylistId = runtime.watchLaterPlaylistId,
    hideEpisodeThumbnails = library.hideEpisodeThumbnails,
    episodesDescending = library.episodesDescending,
    skipSpecials = library.skipSpecials,
    cellularDownloadSizeWarningMb = downloads.cellularDownloadSizeWarningMb,
    hapticsEnabled = appearance.hapticsEnabled,
    dateFormatPreference = appearance.dateFormatPreference,
    appFontScale = appearance.appFontScale,
    scheduledThemeStartHour = appearance.scheduledThemeStartHour,
    scheduledThemeEndHour = appearance.scheduledThemeEndHour,
    colorBlindMode = appearance.colorBlindMode,
    handMode = appearance.handMode,
    downloadScheduleEnabled = downloads.downloadScheduleEnabled,
    downloadScheduleWindow = downloads.downloadScheduleWindow,
    enabledExperimentalFeatures = experimental.enabledExperimentalFeatures,
)

/**
 * Builds a [UserPreferences] snapshot from a decoded v2 [SettingsBackup].
 * Each slice is decoded leniently: missing keys fall back to the slice's
 * defaults, and malformed elements are ignored (matching `restoreV2`
 * forward-compat). Pure (no IO) so the ViewModel only handles the
 * `ContentResolver` read.
 *
 * `pinLockout` is not part of the backup (it is runtime state in
 * `PinRateLimiter`); callers should pass `PinLockoutState()` for the
 * incoming snapshot unless they have a reason to preserve it.
 */
fun buildUserPreferencesFromBackup(
    backup: SettingsBackup,
    pinLockout: PinLockoutState = PinLockoutState(failedAttempts = 0, lockoutUntilEpochMs = 0),
    json: kotlinx.serialization.json.Json = PreferencesJson.import,
): UserPreferences {
    fun <T> decodeOrDefault(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: T,
    ): T {
        val element: JsonElement = backup.slices[key] ?: return default
        return runCatching { json.decodeFromJsonElement(serializer, element) }.getOrDefault(default)
    }

    val playback = decodeOrDefault(BackupSliceKey.PLAYBACK, PlaybackSlice.serializer(), PlaybackSlice())
    val appearance = decodeOrDefault(BackupSliceKey.APPEARANCE, AppearanceSlice.serializer(), AppearanceSlice())
    val videoPlayer = decodeOrDefault(BackupSliceKey.VIDEO_PLAYER, VideoPlayerSlice.serializer(), VideoPlayerSlice())
    val downloads = decodeOrDefault(BackupSliceKey.DOWNLOADS, DownloadsSlice.serializer(), DownloadsSlice())
    val engine = decodeOrDefault(BackupSliceKey.PLAYER_ENGINE, PlayerEngineSlice.serializer(), PlayerEngineSlice())
    val homeDiscovery = decodeOrDefault(BackupSliceKey.HOME_DISCOVERY, HomeDiscoverySlice.serializer(), HomeDiscoverySlice())
    val audio = decodeOrDefault(BackupSliceKey.AUDIO, AudioSlice.serializer(), AudioSlice())
    val audioEffects = decodeOrDefault(BackupSliceKey.AUDIO_EFFECTS, AudioEffectsSlice.serializer(), AudioEffectsSlice())
    val audioCache = decodeOrDefault(BackupSliceKey.AUDIO_CACHE, AudioCacheSlice.serializer(), AudioCacheSlice())
    val library = decodeOrDefault(BackupSliceKey.LIBRARY, LibrarySlice.serializer(), LibrarySlice())
    val navigation = decodeOrDefault(BackupSliceKey.NAVIGATION, NavigationSlice.serializer(), NavigationSlice())
    val networkOffline = decodeOrDefault(BackupSliceKey.NETWORK_OFFLINE, NetworkOfflineSlice.serializer(), NetworkOfflineSlice())
    val notification = decodeOrDefault(BackupSliceKey.NOTIFICATION, NotificationSlice.serializer(), NotificationSlice())
    val syncPlayCast = decodeOrDefault(BackupSliceKey.SYNC_PLAY_CAST, SyncPlayCastSlice.serializer(), SyncPlayCastSlice())
    val screensaver = decodeOrDefault(BackupSliceKey.SCREENSAVER, ScreensaverSlice.serializer(), ScreensaverSlice())
    val security = decodeOrDefault(BackupSliceKey.SECURITY, SecuritySlice.serializer(), SecuritySlice())
    val subtitle = decodeOrDefault(BackupSliceKey.SUBTITLE, SubtitleSlice.serializer(), SubtitleSlice())
    val experimental = decodeOrDefault(BackupSliceKey.EXPERIMENTAL, ExperimentalSlice.serializer(), ExperimentalSlice())

    return buildUserPreferencesSnapshot(
        playback = playback,
        videoPlayer = videoPlayer,
        engine = engine,
        subtitle = subtitle,
        audio = audio,
        audioEffects = audioEffects,
        audioCache = audioCache,
        appearance = appearance,
        homeDiscovery = homeDiscovery,
        library = library,
        navigation = navigation,
        downloads = downloads,
        networkOffline = networkOffline,
        notification = notification,
        syncPlayCast = syncPlayCast,
        screensaver = screensaver,
        security = security,
        experimental = experimental,
        runtime = backup.extras,
        pinLockout = pinLockout,
    )
}
