package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Backs the Factory Reset review screen. Holds the live [preferences] (current)
 * alongside the immutable [factory] baseline (`UserPreferences()` with all
 * default args) so the UI can render a per-category current-vs-default diff and
 * changed-count without duplicating default values.
 *
 * This is a rarely-opened screen, so [preferences] is built ONE-SHOT on entry
 * from the 18 domain-store slices + `AppRuntimeStateStore` + `PinRateLimiter`
 * (see [buildFromSlices]) instead of subscribing to an eager aggregate
 * `StateFlow`. All writes flow through [PreferencesEditor] (the single
 * auditable write seam) — no new mutation path is introduced.
 */
@HiltViewModel
class FactoryResetViewModel @Inject constructor(
    private val playbackStore: PlaybackStore,
    private val appearanceStore: AppearanceStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val downloadsStore: DownloadsStore,
    private val engineStore: PlayerEngineStore,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val audioCacheStore: AudioCacheStore,
    private val libraryStore: LibraryStore,
    private val navigationStore: NavigationStore,
    private val networkOfflineStore: NetworkOfflineStore,
    private val notificationStore: NotificationStore,
    private val screensaverStore: ScreensaverStore,
    private val securityStore: SecurityStore,
    private val subtitleLanguageStore: SubtitleLanguageStore,
    private val syncPlayCastStore: SyncPlayCastStore,
    private val experimentalStore: ExperimentalStore,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val pinRateLimiter: PinRateLimiter,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Factory baseline — `UserPreferences` constructed with every default arg. */
    val factory: UserPreferences = UserPreferences()

    var preferences by composeState(UserPreferences())
        private set

    init {
        launch { preferences = buildFromSlices() }
    }

    /**
     * Builds the [UserPreferences] diff snapshot once from the 18 domain-store
     * slices + runtime/PIN extras. Each slice is read via a single `.first()`;
     * there is no live subscription. Field mapping mirrors
     * `UserPreferencesAggregator.buildUserPreferences` verbatim.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun buildFromSlices(): UserPreferences {
        val playback = playbackStore.playback.first()
        val videoPlayer = videoPlayerStore.videoPlayer.first()
        val engine = engineStore.playerEngine.first()
        val subtitle = subtitleLanguageStore.subtitle.first()
        val audio = audioStore.audio.first()
        val audioEffects = audioEffectsStore.audioEffects.first()
        val audioCache = audioCacheStore.audioCache.first()
        val appearance = appearanceStore.appearance.first()
        val homeDiscovery = homeDiscoveryStore.homeDiscovery.first()
        val library = libraryStore.library.first()
        val navigation = navigationStore.navigation.first()
        val downloads = downloadsStore.downloads.first()
        val networkOffline = networkOfflineStore.networkOffline.first()
        val notification = notificationStore.notification.first()
        val syncPlayCast = syncPlayCastStore.syncPlayCast.first()
        val screensaver = screensaverStore.screensaver.first()
        val security = securityStore.security.first()
        val experimental = experimentalStore.experimental.first()
        val runtime = appRuntimeStateStore.state.first()
        val pinLockout = pinRateLimiter.getPinLockoutState()

        return UserPreferences(
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
            videoRememberVolume = videoPlayer.videoRememberVolume,
            videoVolumeLevel = videoPlayer.videoVolumeLevel,
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
            synthwaveMode = appearance.synthwaveMode,
            synthwaveAccent = appearance.synthwaveAccent,
            soothingMode = appearance.soothingMode,
            soothingAccent = appearance.soothingAccent,
            monochromeMode = appearance.monochromeMode,
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
            dismissedUpdateVersion = experimental.dismissedUpdateVersion,
            dismissedUpdateAtMs = experimental.dismissedUpdateAtMs,
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
    }

    /** Resets every preference in [category] to its factory default. */
    fun resetCategory(category: PreferenceResetCategory) {
        editor.resetCategory(category)
    }

    /** Resets the entire preferences DataStore to factory defaults. */
    fun resetAll() {
        editor.clearAllPreferences()
    }
}
