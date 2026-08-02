package com.raulshma.jellyplay.core.datastore.legacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the 18 domain-store slices back to the legacy
 * [com.raulshma.jellyplay.core.model.legacy.UserPreferences] aggregate shape for the
 * handful of UI screens that still consume the whole object:
 *
 *  - [com.raulshma.jellyplay.feature.settings.FactoryResetViewModel] /
 *    `PreferenceCategoryPresentation` — full-object diff against a factory
 *    baseline across ~150 fields.
 *  - [com.raulshma.jellyplay.feature.details.DetailViewModel] /
 *    `DetailContentState` — the whole object is param-drilled through 4 child
 *    composables.
 *  - [com.raulshma.jellyplay.MainViewModel] (→ `MainActivity` theme/security/
 *    locale resolver, ~27 fields) and `JellyPlayApp` (~12 fields).
 *  - [com.raulshma.jellyplay.feature.onboarding.OnboardingViewModel] /
 *    `OnboardingScreen` (~25 fields spanning 5 domains).
 *  - [com.raulshma.jellyplay.feature.settings.SettingsViewModel] /
 *    `SettingsScreen` (~17 fields).
 *  - [com.raulshma.jellyplay.feature.details.SeerrDetailViewModel] /
 *    `SeerrDetailScreen` (5 theme fields).
 *
 * This is the **single sanctioned producer** of the legacy aggregate at
 * runtime. **Do not extend.** New screens should read store slices directly or
 * via
 * [com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections].
 * Rewriting the consumers above to slice-based reads and deleting this class is
 * the remaining cleanup step toward retiring `UserPreferences` entirely.
 *
 * @param dataStore the shared `"user_prefs"` DataStore — only the facade-owned
 *  extras (PIN rate-limit counters, favorite channels) are read off it
 *  directly; every other field comes from a store slice flow.
 */
@Singleton
class UserPreferencesAggregator @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
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
) {
    private val scope = externalScope

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emit(emptyPreferences()) }

    private val json get() = PreferenceCodec.json

    /**
     * Facade-owned fields no domain slice owns: the PIN rate-limit counters
     * (owned by [PinRateLimiter] but read here as part of the aggregate) and
     * the favorite-channels / onboarding / watch-later runtime state.
     */
    private data class FacadeExtras(
        val pinFailedAttempts: Int,
        val pinLockoutUntilEpochMs: Long,
        val favoriteChannels: Set<String>,
        val onboardingCompleted: Boolean,
        val watchLaterPlaylistId: String?,
    )

    private data class GroupA(
        val playback: PlaybackSlice,
        val videoPlayer: VideoPlayerSlice,
        val engine: PlayerEngineSlice,
        val subtitle: SubtitleSlice,
        val audio: AudioSlice,
    )

    private data class GroupB(
        val audioEffects: AudioEffectsSlice,
        val audioCache: AudioCacheSlice,
        val appearance: AppearanceSlice,
        val homeDiscovery: HomeDiscoverySlice,
        val library: LibrarySlice,
    )

    private data class GroupC(
        val navigation: NavigationSlice,
        val downloads: DownloadsSlice,
        val networkOffline: NetworkOfflineSlice,
        val notification: NotificationSlice,
        val syncPlayCast: SyncPlayCastSlice,
    )

    private data class GroupD(
        val screensaver: ScreensaverSlice,
        val security: SecuritySlice,
        val experimental: ExperimentalSlice,
    )

    private val extrasFlow: Flow<FacadeExtras> = sharedPrefs.map { prefs ->
        FacadeExtras(
            pinFailedAttempts = prefs[PinRateLimiter.Keys.PIN_FAILED_ATTEMPTS] ?: 0,
            pinLockoutUntilEpochMs = prefs[PinRateLimiter.Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L,
            favoriteChannels = readFavoriteChannels(prefs),
            onboardingCompleted = PreferenceCodec.readBool(
                prefs,
                booleanPreferencesKey("onboarding_completed"),
                "onboarding_completed",
                false,
            ),
            watchLaterPlaylistId = prefs[stringPreferencesKey("watch_later_playlist_id")],
        )
    }

    /**
     * The legacy [UserPreferences] aggregate, rebuilt by combining the 18
     * store slices + the facade-extras. De-duplicated on the aggregate's
     * structural equality. This is the only `StateFlow<UserPreferences>` in
     * the app — consumers that need the whole object inject this aggregator.
     */
    val preferences: StateFlow<UserPreferences> = combine(
        combine(
            playbackStore.playback,
            videoPlayerStore.videoPlayer,
            engineStore.playerEngine,
            subtitleLanguageStore.subtitle,
            audioStore.audio,
        ) { playback, videoPlayer, engine, subtitle, audio ->
            GroupA(playback, videoPlayer, engine, subtitle, audio)
        },
        combine(
            audioEffectsStore.audioEffects,
            audioCacheStore.audioCache,
            appearanceStore.appearance,
            homeDiscoveryStore.homeDiscovery,
            libraryStore.library,
        ) { audioEffects, audioCache, appearance, homeDiscovery, library ->
            GroupB(audioEffects, audioCache, appearance, homeDiscovery, library)
        },
        combine(
            navigationStore.navigation,
            downloadsStore.downloads,
            networkOfflineStore.networkOffline,
            notificationStore.notification,
            syncPlayCastStore.syncPlayCast,
        ) { navigation, downloads, networkOffline, notification, syncPlayCast ->
            GroupC(navigation, downloads, networkOffline, notification, syncPlayCast)
        },
        combine(
            screensaverStore.screensaver,
            securityStore.security,
            experimentalStore.experimental,
        ) { screensaver, security, experimental ->
            GroupD(screensaver, security, experimental)
        },
        extrasFlow,
    ) { a, b, c, d, extras ->
        buildUserPreferences(a, b, c, d, extras)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    private fun readFavoriteChannels(prefs: Preferences): Set<String> {
        val raw = prefs[stringPreferencesKey("favorite_channels")] ?: return emptySet()
        return try {
            json.decodeFromString<Set<String>>(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun buildUserPreferences(
        a: GroupA,
        b: GroupB,
        c: GroupC,
        d: GroupD,
        extras: FacadeExtras,
    ): UserPreferences = UserPreferences(
        preferredPlayer = a.playback.preferredPlayer,
        preferredSubtitleLanguage = a.subtitle.preferredSubtitleLanguage,
        subtitlesForcedOnly = a.subtitle.subtitlesForcedOnly,
        preferredAudioLanguage = a.subtitle.preferredAudioLanguage,
        mediaStreamSelections = a.engine.mediaStreamSelections,
        videoEffectsByItem = a.engine.videoEffectsByItem,
        subtitleDelayByItem = a.subtitle.subtitleDelayByItem,
        dynamicTheming = b.appearance.dynamicTheming,
        themeMode = b.appearance.themeMode,
        contrastLevel = b.appearance.contrastLevel,
        oledMode = b.appearance.oledMode,
        subtitleStyle = a.subtitle.subtitleStyle,
        streamingQuality = a.playback.streamingQuality,
        playbackMode = a.playback.playbackMode,
        liveStreamOption = a.playback.liveStreamOption,
        maxCacheSizeMb = c.networkOffline.maxCacheSizeMb,
        autoDeleteCache = c.networkOffline.autoDeleteCache,
        pinLockEnabled = d.security.pinLockEnabled,
        pinHash = d.security.pinHash,
        biometricLockEnabled = d.security.biometricLockEnabled,
        usePinForPlayerLock = d.security.usePinForPlayerLock,
        autoLockTimerMs = d.security.autoLockTimerMs,
        pinFailedAttempts = extras.pinFailedAttempts,
        pinLockoutUntilEpochMs = extras.pinLockoutUntilEpochMs,
        dialogueBoostEnabled = b.audioEffects.dialogueBoostEnabled,
        dialogueBoostStrength = b.audioEffects.dialogueBoostStrength,
        equalizerEnabled = b.audioEffects.equalizerEnabled,
        equalizerSettings = b.audioEffects.equalizerSettings,
        audioDelayMs = a.audio.audioDelayMs,
        decoderMode = a.playback.decoderMode,
        audioPassthrough = a.playback.audioPassthrough,
        frameRateMatching = a.playback.frameRateMatching,
        refreshRateMode = a.playback.refreshRateMode,
        nightModeEnabled = b.audioEffects.nightModeEnabled,
        nightModeStrength = b.audioEffects.nightModeStrength,
        homeMode = b.homeDiscovery.homeMode,
        videoSeekDurationMs = a.videoPlayer.videoSeekDurationMs,
        videoDefaultOrientation = a.videoPlayer.videoDefaultOrientation,
        videoControlsTimeoutMs = a.videoPlayer.videoControlsTimeoutMs,
        videoGesturesEnabled = a.videoPlayer.videoGesturesEnabled,
        videoPassOutProtectionHours = a.videoPlayer.videoPassOutProtectionHours,
        videoSkipBackOnResumeMs = a.videoPlayer.videoSkipBackOnResumeMs,
        videoHoldSpeedEnabled = a.videoPlayer.videoHoldSpeedEnabled,
        videoHoldSpeedMultiplier = a.videoPlayer.videoHoldSpeedMultiplier,
        videoDefaultSpeed = a.videoPlayer.videoDefaultSpeed,
        videoDefaultAspectRatio = a.videoPlayer.videoDefaultAspectRatio,
        videoAutoplayNext = a.videoPlayer.videoAutoplayNext,
        trailerAutoplay = a.videoPlayer.trailerAutoplay,
        cinemaModeEnabled = a.videoPlayer.cinemaModeEnabled,
        videoSwipeSeekMaxMs = a.videoPlayer.videoSwipeSeekMaxMs,
        videoRememberBrightness = a.videoPlayer.videoRememberBrightness,
        videoBrightnessLevel = a.videoPlayer.videoBrightnessLevel,
        videoRememberVolume = a.videoPlayer.videoRememberVolume,
        videoVolumeLevel = a.videoPlayer.videoVolumeLevel,
        videoAutoSkipIntro = a.videoPlayer.videoAutoSkipIntro,
        videoAutoSkipOutro = a.videoPlayer.videoAutoSkipOutro,
        videoRememberMuted = a.videoPlayer.videoRememberMuted,
        videoMuted = a.videoPlayer.videoMuted,
        subtitlePreviewInSettings = a.subtitle.subtitlePreviewInSettings,
        videoGestureIndicatorSide = a.videoPlayer.videoGestureIndicatorSide,
        audioDefaultSpeed = a.audio.audioDefaultSpeed,
        audioNightModeVolume = a.audio.audioNightModeVolume,
        audioNightModeGain = a.audio.audioNightModeGain,
        audioSkipPreviousThresholdMs = a.audio.audioSkipPreviousThresholdMs,
        audioAutoplayNext = a.audio.audioAutoplayNext,
        trickplayEnabled = a.videoPlayer.trickplayEnabled,
        trickplayOnSeekGesture = a.videoPlayer.trickplayOnSeekGesture,
        segmentBehaviors = a.videoPlayer.segmentBehaviors,
        videoEpisodeBrowserEnabled = a.videoPlayer.videoEpisodeBrowserEnabled,
        videoShowPlaybackMetadata = a.videoPlayer.videoShowPlaybackMetadata,
        videoPreloadBufferSize = a.videoPlayer.videoPreloadBufferSize,
        audioPreloadBufferSize = a.audio.audioPreloadBufferSize,
        audioNormalizationMode = a.audio.audioNormalizationMode,
        audioNormalizationEnabled = a.audio.audioNormalizationEnabled,
        replayGainPreAmpDb = a.audio.replayGainPreAmpDb,
        channelMixMode = a.audio.channelMixMode,
        channelMixEnabled = a.audio.channelMixEnabled,
        audioGaplessEnabled = a.audio.audioGaplessEnabled,
        audioCrossfadeDurationMs = a.audio.audioCrossfadeDurationMs,
        audioCachingEnabled = b.audioCache.audioCachingEnabled,
        audioCacheSizeMb = b.audioCache.audioCacheSizeMb,
        audioPrefetchLookahead = b.audioCache.audioPrefetchLookahead,
        audioPrefetchBackfill = b.audioCache.audioPrefetchBackfill,
        audioCacheNetworkPolicy = b.audioCache.audioCacheNetworkPolicy,
        audioCacheCellularMonthlyCapMb = b.audioCache.audioCacheCellularMonthlyCapMb,
        sleepTimerDurationMs = a.audio.sleepTimerDurationMs,
        sleepTimerEndOfEpisode = a.audio.sleepTimerEndOfEpisode,
        dreamImageCategories = d.screensaver.dreamImageCategories,
        dreamSlideshowIntervalMs = d.screensaver.dreamSlideshowIntervalMs,
        dreamKenBurnsEnabled = d.screensaver.dreamKenBurnsEnabled,
        dreamTransitionStyle = d.screensaver.dreamTransitionStyle,
        dreamShowTitle = d.screensaver.dreamShowTitle,
        equalizerPreset = b.audioEffects.equalizerPreset,
        bassBoostEnabled = b.audioEffects.bassBoostEnabled,
        bassBoostStrength = b.audioEffects.bassBoostStrength,
        virtualizerEnabled = b.audioEffects.virtualizerEnabled,
        virtualizerStrength = b.audioEffects.virtualizerStrength,
        reverbPreset = b.audioEffects.reverbPreset,
        lrBalance = b.audioEffects.lrBalance,
        autoEqByGenre = b.audioEffects.autoEqByGenre,
        pitchSemitones = b.audioEffects.pitchSemitones,
        wifiOnlyDownloads = c.downloads.wifiOnlyDownloads,
        downloadConnections = c.downloads.downloadConnections,
        maxConcurrentDownloads = c.downloads.maxConcurrentDownloads,
        enabledHomeSectionTypes = b.homeDiscovery.enabledHomeSectionTypes,
        homeSectionOrder = b.homeDiscovery.homeSectionOrder,
        libraryHomeSectionOverrides = b.homeDiscovery.libraryHomeSectionOverrides,
        navBarShowLabels = c.navigation.navBarShowLabels,
        hideBottomNavOnScroll = c.navigation.hideBottomNavOnScroll,
        homeHeroEnabled = b.homeDiscovery.homeHeroEnabled,
        homeBackdropEnabled = b.homeDiscovery.homeBackdropEnabled,
        onboardingCompleted = extras.onboardingCompleted,
        mpvConfig = a.engine.mpvConfig,
        libVlcConfig = a.engine.libVlcConfig,
        exoPlayerConfig = a.engine.exoPlayerConfig,
        performanceMode = b.appearance.performanceMode,
        newsletterEnabled = c.notification.newsletterEnabled,
        newsletterDayOfWeek = c.notification.newsletterDayOfWeek,
        newsletterLastViewedMs = c.notification.newsletterLastViewedMs,
        accentColorSwatch = b.appearance.accentColorSwatch,
        colorStyle = b.appearance.colorStyle,
        libraryViewMode = b.library.libraryViewMode,
        notificationPreferences = c.notification.notificationPreferences,
        showAdvancedSettings = b.appearance.showAdvancedSettings,
        audioVisualizerEnabled = a.audio.audioVisualizerEnabled,
        audioLyricsVisible = a.audio.audioLyricsVisible,
        synthwaveMode = b.appearance.synthwaveMode,
        synthwaveAccent = b.appearance.synthwaveAccent,
        soothingMode = b.appearance.soothingMode,
        soothingAccent = b.appearance.soothingAccent,
        monochromeMode = b.appearance.monochromeMode,
        syncPlayJoinBehavior = c.syncPlayCast.syncPlayJoinBehavior,
        syncPlayToleranceMs = c.syncPlayCast.syncPlayToleranceMs,
        syncPlayAutoAcceptInvites = c.syncPlayCast.syncPlayAutoAcceptInvites,
        defaultCastingStrategy = c.syncPlayCast.defaultCastingStrategy,
        backgroundCastingEnabled = c.syncPlayCast.backgroundCastingEnabled,
        preferredRenderer = c.syncPlayCast.preferredRenderer,
        dvrPrePaddingMinutes = c.syncPlayCast.dvrPrePaddingMinutes,
        dvrPostPaddingMinutes = c.syncPlayCast.dvrPostPaddingMinutes,
        dvrRecordingQuality = c.syncPlayCast.dvrRecordingQuality,
        favoriteChannels = extras.favoriteChannels,
        enabledNewsletterSections = c.notification.enabledNewsletterSections,
        newsletterSectionOrder = c.notification.newsletterSectionOrder,
        manualOfflineEnabled = c.networkOffline.manualOfflineEnabled,
        autoOfflineEnabled = c.networkOffline.autoOfflineEnabled,
        manualBandwidthCap = c.networkOffline.manualBandwidthCap,
        meteredNetworkBehavior = c.networkOffline.meteredNetworkBehavior,
        adaptiveBitrateEnabled = c.networkOffline.adaptiveBitrateEnabled,
        backgroundVideoAudioEnabled = a.playback.backgroundVideoAudioEnabled,
        autoPlayCountdownSec = a.playback.autoPlayCountdownSec,
        showUnwatchedBadge = b.homeDiscovery.showUnwatchedBadge,
        hideWatchedItems = b.homeDiscovery.hideWatchedItems,
        mergeContinueWatchingAndNextUp = b.homeDiscovery.mergeContinueWatchingAndNextUp,
        nextUpMaxDays = b.homeDiscovery.nextUpMaxDays,
        nextUpRewatching = b.homeDiscovery.nextUpRewatching,
        nextUpExcludedSeriesIds = b.homeDiscovery.nextUpExcludedSeriesIds,
        hiddenCwItemIds = b.homeDiscovery.hiddenCwItemIds,
        pinnedHomeSections = b.homeDiscovery.pinnedHomeSections,
        homeLayoutPresets = b.homeDiscovery.homeLayoutPresets,
        continueWatchingClickBehavior = b.homeDiscovery.continueWatchingClickBehavior,
        cellularStreamingQuality = a.playback.cellularStreamingQuality,
        showWatchedCheckmark = b.homeDiscovery.showWatchedCheckmark,
        defaultLibrarySortOrders = b.library.defaultLibrarySortOrders,
        libraryViewModes = b.library.libraryViewModes,
        libraryFilters = b.library.libraryFilters,
        keepScreenOnDuringVideo = a.playback.keepScreenOnDuringVideo,
        downloadQuality = c.downloads.downloadQuality,
        smartDownloadsEnabled = c.downloads.smartDownloadsEnabled,
        autoDownloadNewEpisodes = c.downloads.autoDownloadNewEpisodes,
        incognitoModeEnabled = a.videoPlayer.incognitoModeEnabled,
        showTimeRemaining = a.videoPlayer.showTimeRemaining,
        showClockOnHome = b.homeDiscovery.showClockOnHome,
        showClockInPlayer = a.videoPlayer.showClockInPlayer,
        showSettingsInHomeSearch = b.homeDiscovery.showSettingsInHomeSearch,
        pauseOnAudioFocusLoss = a.playback.pauseOnAudioFocusLoss,
        duckOnTransientFocusLoss = a.playback.duckOnTransientFocusLoss,
        volumeBoostEnabled = b.audioEffects.volumeBoostEnabled,
        volumeBoostGain = b.audioEffects.volumeBoostGain,
        showShareMediaOption = d.experimental.showShareMediaOption,
        showExternalRatings = b.homeDiscovery.showExternalRatings,
        dataSaverEnabled = c.networkOffline.dataSaverEnabled,
        verboseNetworkLogging = c.networkOffline.verboseNetworkLogging,
        networkTimeoutPreset = c.networkOffline.networkTimeoutPreset,
        reduceMotionEnabled = b.appearance.reduceMotionEnabled,
        preferAudioDescription = d.experimental.preferAudioDescription,
        highContrastSubtitles = a.subtitle.highContrastSubtitles,
        hideSearchHistory = d.experimental.hideSearchHistory,
        blueLightFilterEnabled = b.appearance.blueLightFilterEnabled,
        blueLightFilterStrength = b.appearance.blueLightFilterStrength,
        tvZoomModePercent = a.videoPlayer.tvZoomModePercent,
        remoteControlEnabled = d.security.remoteControlEnabled,
        maxDownloadStorageGb = c.downloads.maxDownloadStorageGb,
        downloadStorageLocation = c.downloads.downloadStorageLocation,
        androidTvWatchNextEnabled = a.playback.androidTvWatchNextEnabled,
        userDataSyncEnabled = a.playback.userDataSyncEnabled,
        appLanguage = d.experimental.appLanguage,
        pgsSubtitleDirectPlay = a.playback.pgsSubtitleDirectPlay,
        hdrSubtitleStyleEnabled = a.subtitle.hdrSubtitleStyleEnabled,
        hdrSubtitleStyle = a.subtitle.hdrSubtitleStyle,
        backdropThemeMusicEnabled = b.appearance.backdropThemeMusicEnabled,
        hiddenNavItems = c.navigation.hiddenNavItems,
        navItemOrder = c.navigation.navItemOrder,
        selfUpdateCheckEnabled = d.experimental.selfUpdateCheckEnabled,
        dismissedUpdateVersion = d.experimental.dismissedUpdateVersion,
        dismissedUpdateAtMs = d.experimental.dismissedUpdateAtMs,
        watchLaterPlaylistId = extras.watchLaterPlaylistId,
        hideEpisodeThumbnails = b.library.hideEpisodeThumbnails,
        episodesDescending = b.library.episodesDescending,
        skipSpecials = b.library.skipSpecials,
        cellularDownloadSizeWarningMb = c.downloads.cellularDownloadSizeWarningMb,
        hapticsEnabled = b.appearance.hapticsEnabled,
        dateFormatPreference = b.appearance.dateFormatPreference,
        appFontScale = b.appearance.appFontScale,
        scheduledThemeStartHour = b.appearance.scheduledThemeStartHour,
        scheduledThemeEndHour = b.appearance.scheduledThemeEndHour,
        colorBlindMode = b.appearance.colorBlindMode,
        handMode = b.appearance.handMode,
        downloadScheduleEnabled = c.downloads.downloadScheduleEnabled,
        downloadScheduleWindow = c.downloads.downloadScheduleWindow,
        enabledExperimentalFeatures = d.experimental.enabledExperimentalFeatures,
    )
}
