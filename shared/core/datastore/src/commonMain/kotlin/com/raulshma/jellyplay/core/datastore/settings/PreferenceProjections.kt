package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.model.AppearancePreferences
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.AudioPlayerPreferences
import com.raulshma.jellyplay.core.model.AudioPlayerUiPreferences
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.OnboardingPreferences
import com.raulshma.jellyplay.core.model.SeerrDetailPreferences
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.model.StoragePreferences
import com.raulshma.jellyplay.core.model.SubtitlePreferences
import com.raulshma.jellyplay.core.model.SyncPlayPreferences
import com.raulshma.jellyplay.core.model.VideoPlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Read-layer that projects the store-owned slices into the per-domain and
 * per-screen preference types defined in `core.model.PreferenceGroups`.
 *
 * Each [StateFlow] here combines only the store slices a projection actually
 * needs, so a sub-screen collecting one slice recomposes only when its own
 * fields change. Field-set declarations shared by more than one lane (the two
 * audio surfaces, the appearance core, the artwork `AppearanceTheme` quad)
 * live ONCE in `DeclaredProjectionFields.kt` — this file owns the
 * combine/stateIn plumbing and the single-lane field lists only.
 *
 * Field names on each slice match the projection target on purpose: it keeps
 * screen bodies (`slice.field`) untouched when a screen swaps from the
 * former aggregate to its slice.
 */
class PreferenceProjections constructor(
    private val scope: CoroutineScope,
    /**
     * The NINETEEN domain stores, bundled (the PlayerStores construction-seam
     * precedent): the list is enumerated once in [PreferenceStores] and shared
     * with [PreferenceSnapshotReader]'s one-shot lane, so a new store widens
     * the bundle + Koin definition — not this constructor again.
     */
    private val stores: PreferenceStores,
) {
    // -------------------------------------------------------------------------
    // Per-domain projections.
    // -------------------------------------------------------------------------

    /** Fields one video-player surface reads, projected across the stores that own them. */
    val videoPlayerPreferences: StateFlow<VideoPlayerPreferences> =
        combine(
            stores.playback.playback,
            stores.videoPlayer.videoPlayer,
            stores.audioEffects.audioEffects,
            stores.appearance.appearance,
            combine(stores.subtitle.subtitle, stores.engine.playerEngine) { sub, eng -> sub to eng },
        ) { playback, video, effects, appearance, (subtitle, engine) ->
            VideoPlayerPreferences(
                preferredPlayer = playback.preferredPlayer,
                decoderMode = playback.decoderMode,
                audioPassthrough = playback.audioPassthrough,
                frameRateMatching = playback.frameRateMatching,
                refreshRateMode = playback.refreshRateMode,
                videoSeekDurationMs = video.videoSeekDurationMs,
                videoDefaultOrientation = video.videoDefaultOrientation,
                videoControlsTimeoutMs = video.videoControlsTimeoutMs,
                videoGesturesEnabled = video.videoGesturesEnabled,
                videoHoldSpeedEnabled = video.videoHoldSpeedEnabled,
                videoHoldSpeedMultiplier = video.videoHoldSpeedMultiplier,
                videoDefaultSpeed = video.videoDefaultSpeed,
                videoDefaultAspectRatio = video.videoDefaultAspectRatio,
                videoAutoplayNext = video.videoAutoplayNext,
                trailerAutoplay = video.trailerAutoplay,
                videoSwipeSeekMaxMs = video.videoSwipeSeekMaxMs,
                videoRememberBrightness = video.videoRememberBrightness,
                videoBrightnessLevel = video.videoBrightnessLevel,
                videoGestureIndicatorSide = video.videoGestureIndicatorSide,
                trickplayEnabled = video.trickplayEnabled,
                trickplayOnSeekGesture = video.trickplayOnSeekGesture,
                videoEpisodeBrowserEnabled = video.videoEpisodeBrowserEnabled,
                videoShowPlaybackMetadata = video.videoShowPlaybackMetadata,
                videoPreloadBufferSize = video.videoPreloadBufferSize,
                videoCacheSizeMb = video.videoCacheSizeMb,
                keepScreenOnDuringVideo = playback.keepScreenOnDuringVideo,
                showTimeRemaining = video.showTimeRemaining,
                pauseOnAudioFocusLoss = playback.pauseOnAudioFocusLoss,
                volumeBoostEnabled = effects.volumeBoostEnabled,
                volumeBoostGain = effects.volumeBoostGain,
                backgroundVideoAudioEnabled = playback.backgroundVideoAudioEnabled,
                autoPlayCountdownSec = playback.autoPlayCountdownSec,
                reduceMotionEnabled = appearance.reduceMotionEnabled,
                preferAudioDescription = subtitle.preferAudioDescription,
                highContrastSubtitles = subtitle.highContrastSubtitles,
                blueLightFilterEnabled = appearance.blueLightFilterEnabled,
                blueLightFilterStrength = appearance.blueLightFilterStrength,
                tvZoomModePercent = video.tvZoomModePercent,
                mpvConfig = engine.mpvConfig,
                libVlcConfig = engine.libVlcConfig,
                exoPlayerConfig = engine.exoPlayerConfig,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), VideoPlayerPreferences())

    /** Audio playback + audio-effects fields one audio surface reads. */
    val audioPlayerPreferences: StateFlow<AudioPlayerPreferences> =
        combine(stores.audio.audio, stores.audioEffects.audioEffects) { audio, effects ->
            audioSurfaceValues(audio, effects).toAudioPlayerPreferences()
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioPlayerPreferences())

    val subtitlePreferences: StateFlow<SubtitlePreferences> =
        stores.subtitle.subtitle.map { sub ->
            SubtitlePreferences(
                subtitleStyle = sub.subtitleStyle,
                preferredSubtitleLanguage = sub.preferredSubtitleLanguage,
                preferredAudioLanguage = sub.preferredAudioLanguage,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SubtitlePreferences())

    val securityPreferences: StateFlow<SecurityPreferences> =
        combine(stores.security.security, stores.videoPlayer.videoPlayer) { security, video ->
            SecurityPreferences(
                pinLockEnabled = security.pinLockEnabled,
                pinHash = security.pinHash,
                biometricLockEnabled = security.biometricLockEnabled,
                usePinForPlayerLock = security.usePinForPlayerLock,
                autoLockTimerMs = security.autoLockTimerMs,
                incognitoModeEnabled = video.incognitoModeEnabled,
                remoteControlEnabled = security.remoteControlEnabled,
                remoteDisplayContentEnabled = security.remoteDisplayContentEnabled,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SecurityPreferences())

    val downloadPreferences: StateFlow<DownloadPreferences> =
        combine(stores.downloads.downloads, stores.networkOffline.networkOffline) { downloads, network ->
            DownloadPreferences(
                wifiOnlyDownloads = downloads.wifiOnlyDownloads,
                downloadConnections = downloads.downloadConnections,
                downloadQuality = downloads.downloadQuality,
                smartDownloadsEnabled = downloads.smartDownloadsEnabled,
                autoDownloadNewEpisodes = downloads.autoDownloadNewEpisodes,
                maxDownloadStorageGb = downloads.maxDownloadStorageGb,
                downloadStorageLocation = downloads.downloadStorageLocation,
                manualOfflineEnabled = network.manualOfflineEnabled,
                autoOfflineEnabled = network.autoOfflineEnabled,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DownloadPreferences())

    val syncPlayPreferences: StateFlow<SyncPlayPreferences> =
        stores.syncPlayCast.syncPlayCast.map { sp ->
            SyncPlayPreferences(
                syncPlayJoinBehavior = sp.syncPlayJoinBehavior,
                syncPlayToleranceMs = sp.syncPlayToleranceMs,
                syncPlayAutoAcceptInvites = sp.syncPlayAutoAcceptInvites,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncPlayPreferences())

    val appearancePreferences: StateFlow<AppearancePreferences> =
        combine(
            stores.appearance.appearance,
            stores.navigation.navigation,
            stores.homeDiscovery.homeDiscovery,
            stores.library.library,
        ) { appearance, navigation, home, library ->
            appearanceCoreValues(appearance, navigation, home, library).toAppearancePreferences()
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppearancePreferences())

    /** Notification sub-domain (matches the legacy aggregate shape). Eagerly cached. */
    val notificationPreferences: StateFlow<NotificationPreferences> =
        stores.notification.notification.map { it.notificationPreferences }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, NotificationPreferences())

    // -------------------------------------------------------------------------
    // Per-screen projections.
    // -------------------------------------------------------------------------

    /**
     * Fields read by `PlaybackSettingsScreen`. The broadest slice — spans the
     * video, playback, audio-effects, subtitle, audio, engine, syncplay and
     * volume-profile stores. Nested combines keep within Flow's 5-arg combine
     * arity.
     */
    val playbackPreferences: StateFlow<PlaybackPreferences> =
        combine(
            combine(
                stores.playback.playback,
                stores.videoPlayer.videoPlayer,
                stores.audioEffects.audioEffects,
                combine(stores.subtitle.subtitle, stores.audio.audio) { sub, au -> sub to au },
                stores.engine.playerEngine,
            ) { playback, video, effects, (subtitle, audio), engine ->
                PlaybackCoreBundle(playback, video, effects, subtitle, audio, engine)
            },
            stores.syncPlayCast.syncPlayCast,
            stores.volumeProfile.volumeProfile,
        ) { g1, syncPlayCast, volumeProfile ->
            g1.toPlaybackPreferences(syncPlayCast, volumeProfile)
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PlaybackPreferences())

    /** Fields read by `AudioSettingsScreen` — derived from the declared audio-surface field set. */
    val audioPreferences: StateFlow<AudioPreferences> =
        combine(
            stores.audio.audio,
            stores.audioEffects.audioEffects,
            stores.audioCache.audioCache,
            stores.experimental.experimental,
        ) { audio, effects, cache, experimental ->
            audioSurfaceValues(audio, effects).toAudioPreferences(cache, experimental)
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioPreferences())

    /** Fields read by `StorageSettingsScreen`. */
    val storagePreferences: StateFlow<StoragePreferences> =
        combine(
            stores.downloads.downloads,
            stores.networkOffline.networkOffline,
            stores.playback.playback,
        ) { downloads, network, playback ->
            StoragePreferences(
                wifiOnlyDownloads = downloads.wifiOnlyDownloads,
                downloadConnections = downloads.downloadConnections,
                maxConcurrentDownloads = downloads.maxConcurrentDownloads,
                downloadQuality = downloads.downloadQuality,
                smartDownloadsEnabled = downloads.smartDownloadsEnabled,
                autoDownloadNewEpisodes = downloads.autoDownloadNewEpisodes,
                maxDownloadStorageGb = downloads.maxDownloadStorageGb,
                downloadStorageLocation = downloads.downloadStorageLocation,
                autoDeleteAfterWatch = downloads.autoDeleteAfterWatch,
                manualOfflineEnabled = network.manualOfflineEnabled,
                autoOfflineEnabled = network.autoOfflineEnabled,
                maxCacheSizeMb = network.maxCacheSizeMb,
                autoDeleteCache = network.autoDeleteCache,
                cellularDownloadSizeWarningMb = downloads.cellularDownloadSizeWarningMb,
                downloadScheduleEnabled = downloads.downloadScheduleEnabled,
                downloadScheduleWindow = downloads.downloadScheduleWindow,
                streamingQuality = playback.streamingQuality,
                cellularStreamingQuality = playback.cellularStreamingQuality,
                meteredNetworkBehavior = network.meteredNetworkBehavior,
                adaptiveBitrateEnabled = network.adaptiveBitrateEnabled,
                manualBandwidthCap = network.manualBandwidthCap,
                dataSaverEnabled = network.dataSaverEnabled,
                verboseNetworkLogging = network.verboseNetworkLogging,
                networkTimeoutPreset = network.networkTimeoutPreset,
                userDataSyncEnabled = playback.userDataSyncEnabled,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), StoragePreferences())

    /** Fields read by `NavigationCustomizationGroup`. */
    val navigationCustomizationPreferences: StateFlow<NavigationCustomizationPreferences> =
        stores.navigation.navigation.map { nav ->
            NavigationCustomizationPreferences(
                hiddenNavItems = nav.hiddenNavItems,
                navItemOrder = nav.navItemOrder,
                hideBottomNavOnScroll = nav.hideBottomNavOnScroll,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), NavigationCustomizationPreferences())

    /** Fields read by `LanguageSettingsScreen`. */
    val languagePreferences: StateFlow<LanguagePreferences> =
        combine(stores.subtitle.subtitle, stores.playback.playback) { subtitle, playback ->
            LanguagePreferences(
                subtitleStyle = subtitle.subtitleStyle,
                preferredSubtitleLanguage = subtitle.preferredSubtitleLanguage,
                preferredAudioLanguage = subtitle.preferredAudioLanguage,
                subtitlesForcedOnly = subtitle.subtitlesForcedOnly,
                highContrastSubtitles = subtitle.highContrastSubtitles,
                pgsSubtitleDirectPlay = playback.pgsSubtitleDirectPlay,
                hdrSubtitleStyleEnabled = subtitle.hdrSubtitleStyleEnabled,
                hdrSubtitleStyle = subtitle.hdrSubtitleStyle,
                appLanguage = subtitle.appLanguage,
                languageRules = subtitle.languageRules,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LanguagePreferences())

    /** Fields read by `ExperimentalSettingsScreen`. */
    val experimentalPreferences: StateFlow<ExperimentalPreferences> =
        stores.experimental.experimental.map { exp ->
            ExperimentalPreferences(
                enabledExperimentalFeatures = exp.enabledExperimentalFeatures,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ExperimentalPreferences())

    /**
     * Fields read by `AppearanceSettingsScreen`. The broadest screen slice:
     * theme + home layout + discovery + newsletter + accessibility, projected
     * across 6 stores. Navigation-customization fields are excluded (they live
     * in [navigationCustomizationPreferences]).
     */
    val appearanceScreenPreferences: StateFlow<AppearanceScreenPreferences> = combine(
        combine(
            stores.appearance.appearance,
            stores.navigation.navigation,
            stores.homeDiscovery.homeDiscovery,
            stores.library.library,
            stores.experimental.experimental,
            ::AppearanceScreenBundle,
        ),
        stores.notification.notification,
    ) { g1, notification ->
        g1.appearanceCoreValues().toAppearanceScreenPreferences(
            appearance = g1.appearance,
            home = g1.home,
            library = g1.library,
            experimental = g1.experimental,
            notification = notification,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppearanceScreenPreferences())

    /**
     * Fields read by `HomeSettingsScreen` — a single-store slice over
     * [HomeDiscoveryStore] so the home config hub recomposes only on
     * home-discovery writes. The card-display toggles stay out (app-wide card
     * settings owned by the Appearance screen).
     */
    val homeScreenPreferences: StateFlow<HomeScreenPreferences> =
        stores.homeDiscovery.homeDiscovery.map { home ->
            HomeScreenPreferences(
                homeMode = home.homeMode,
                homeHeroEnabled = home.homeHeroEnabled,
                homeBackdropEnabled = home.homeBackdropEnabled,
                showClockOnHome = home.showClockOnHome,
                showSettingsInHomeSearch = home.showSettingsInHomeSearch,
                hideTopHeaderOnScroll = home.hideTopHeaderOnScroll,
                continueWatchingClickBehavior = home.continueWatchingClickBehavior,
                hiddenCwItemIds = home.hiddenCwItemIds,
                mergeContinueWatchingAndNextUp = home.mergeContinueWatchingAndNextUp,
                nextUpMaxDays = home.nextUpMaxDays,
                nextUpRewatching = home.nextUpRewatching,
                enabledHomeSectionTypes = home.enabledHomeSectionTypes,
                homeSectionOrder = home.homeSectionOrder,
                pinnedHomeSections = home.pinnedHomeSections,
                discoverRows = home.discoverRows,
                homeLayoutPresets = home.homeLayoutPresets,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), HomeScreenPreferences())

    // -------------------------------------------------------------------------
    // Consumer-screen projections (non-settings surfaces).
    //
    // These replace the bespoke `combine(...)` projections the feature
    // ViewModels used to hand-roll (several with vararg + `UNCHECKED_CAST`).
    // Field sets are a verbatim port of the former local shadows so the screen
    // bodies keep reading `preferences.X`.
    // -------------------------------------------------------------------------

    /** Fields read by the audio player screen (lyrics toggle + artwork theme). */
    val audioPlayerUiPreferences: StateFlow<AudioPlayerUiPreferences> =
        combine(stores.audio.audio, stores.appearance.appearance) { audio, appearance ->
            AudioPlayerUiPreferences(
                audioLyricsVisible = audio.audioLyricsVisible,
                theme = appearance.appearanceTheme(),
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioPlayerUiPreferences())

    /** Fields read by `SeerrDetailScreen` — artwork theme + inline-trailer autoplay. */
    val seerrDetailPreferences: StateFlow<SeerrDetailPreferences> =
        combine(stores.appearance.appearance, stores.videoPlayer.videoPlayer) { appearance, video ->
            SeerrDetailPreferences(
                theme = appearance.appearanceTheme(),
                trailerAutoplay = video.trailerAutoplay,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrDetailPreferences())

    /** Fields read by the media `DetailScreen`, projected across 6 stores. */
    val detailPreferences: StateFlow<DetailPreferences> = combine(
        combine(
            stores.appearance.appearance,
            stores.videoPlayer.videoPlayer,
            stores.subtitle.subtitle,
            stores.experimental.experimental,
            ::DetailScreenBundle,
        ),
        stores.homeDiscovery.homeDiscovery,
        stores.library.library,
    ) { g1, home, library ->
        DetailPreferences(
            theme = g1.appearance.appearanceTheme(),
            trailerAutoplay = g1.video.trailerAutoplay,
            preferredAudioLanguage = g1.subtitle.preferredAudioLanguage,
            preferredSubtitleLanguage = g1.subtitle.preferredSubtitleLanguage,
            showShareMediaOption = g1.experimental.showShareMediaOption,
            showExternalRatings = home.showExternalRatings,
            nextUpExcludedSeriesIds = home.nextUpExcludedSeriesIds,
            hiddenCwItemIds = home.hiddenCwItemIds,
            lastViewedSeasonBySeries = home.lastViewedSeasonBySeries,
            skipSpecials = library.skipSpecials,
            hideEpisodeThumbnails = library.hideEpisodeThumbnails,
            episodesDescending = library.episodesDescending,
            compactEpisodeList = library.compactEpisodeList,
            showDetailUpNext = library.showDetailUpNext,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailPreferences())

    /** Fields read by `OnboardingViewModel` across the multi-step onboarding flow. */
    val onboardingPreferences: StateFlow<OnboardingPreferences> = combine(
        combine(
            stores.appearance.appearance,
            stores.homeDiscovery.homeDiscovery,
            stores.navigation.navigation,
            stores.playback.playback,
            ::OnboardingThemeHomeNavPlaybackBundle,
        ),
        combine(
            stores.videoPlayer.videoPlayer,
            stores.audio.audio,
            stores.subtitle.subtitle,
            stores.security.security,
            ::OnboardingPlayerAudioSubSecurityBundle,
        ),
    ) { g1, g2 ->
        OnboardingPreferences(
            themeMode = g1.appearance.themeMode,
            theme = g1.appearance.appearanceTheme(),
            contrastLevel = g1.appearance.contrastLevel,
            homeHeroEnabled = g1.home.homeHeroEnabled,
            performanceMode = g1.appearance.performanceMode,
            homeMode = g1.home.homeMode,
            navBarShowLabels = g1.navigation.navBarShowLabels,
            enabledHomeSectionTypes = g1.home.enabledHomeSectionTypes,
            preferredPlayer = g1.playback.preferredPlayer,
            streamingQuality = g1.playback.streamingQuality,
            videoSeekDurationMs = g2.video.videoSeekDurationMs,
            videoGesturesEnabled = g2.video.videoGesturesEnabled,
            videoDefaultOrientation = g2.video.videoDefaultOrientation,
            videoAutoplayNext = g2.video.videoAutoplayNext,
            audioDefaultSpeed = g2.audio.audioDefaultSpeed,
            audioGaplessEnabled = g2.audio.audioGaplessEnabled,
            audioCrossfadeDurationMs = g2.audio.audioCrossfadeDurationMs,
            audioNormalizationEnabled = g2.audio.audioNormalizationEnabled,
            audioAutoplayNext = g2.audio.audioAutoplayNext,
            subtitleStyle = g2.subtitle.subtitleStyle,
            pinLockEnabled = g2.security.pinLockEnabled,
            biometricLockEnabled = g2.security.biometricLockEnabled,
            autoLockTimerMs = g2.security.autoLockTimerMs,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), OnboardingPreferences())

    /** Fields read by the top-level `SettingsScreen` landing page. */
    val settingsScreenPreferences: StateFlow<SettingsScreenPreferences> = combine(
        combine(
            stores.appearance.appearance,
            stores.playback.playback,
            stores.audio.audio,
            stores.subtitle.subtitle,
            stores.security.security,
            ::SettingsCoreBundle,
        ),
        combine(
            stores.experimental.experimental,
            stores.notification.notification,
            stores.screensaver.screensaver,
            ::SettingsAuxBundle,
        ),
    ) { g1, g2 ->
        SettingsScreenPreferences(
            showAdvancedSettings = g1.appearance.showAdvancedSettings,
            themeMode = g1.appearance.themeMode,
            dynamicTheming = g1.appearance.dynamicTheming,
            oledMode = g1.appearance.oledMode,
            contrastLevel = g1.appearance.contrastLevel,
            performanceMode = g1.appearance.performanceMode,
            preferredPlayer = g1.playback.preferredPlayer,
            audioDefaultSpeed = g1.audio.audioDefaultSpeed,
            preferredAudioLanguage = g1.subtitle.preferredAudioLanguage,
            notificationPreferences = g2.notification.notificationPreferences,
            pinLockEnabled = g1.security.pinLockEnabled,
            biometricLockEnabled = g1.security.biometricLockEnabled,
            dreamImageCategories = g2.screensaver.dreamImageCategories,
            dreamSlideshowIntervalMs = g2.screensaver.dreamSlideshowIntervalMs,
            dreamShowTitle = g2.screensaver.dreamShowTitle,
            dreamKenBurnsEnabled = g2.screensaver.dreamKenBurnsEnabled,
            dreamTransitionStyle = g2.screensaver.dreamTransitionStyle,
            idleAmbientEnabled = g2.screensaver.idleAmbientEnabled,
            idleAmbientTimeoutMin = g2.screensaver.idleAmbientTimeoutMin,
            enabledExperimentalFeatures = g2.experimental.enabledExperimentalFeatures,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SettingsScreenPreferences())

    /**
     * Slice-derived portion of `MainPreferences`. The two runtime-only fields
     * (`pinLockoutUntilEpochMs`, `onboardingCompleted`) are merged in by
     * `MainViewModel` via a typed combine off this projection; they are absent
     * here because they do not live in a preference slice.
     */
    val mainPreferences: StateFlow<MainPreferences> = combine(
        combine(
            stores.appearance.appearance,
            stores.security.security,
            stores.homeDiscovery.homeDiscovery,
            stores.navigation.navigation,
            stores.experimental.experimental,
            ::MainScreenBundle,
        ),
        stores.playback.playback,
    ) { g1, playback ->
        mainScreenPreferences(
            appearance = g1.appearance,
            security = g1.security,
            home = g1.home,
            navigation = g1.navigation,
            experimental = g1.experimental,
            playback = playback,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), MainPreferences())

    // Tuple-carrier data classes for the nested combines above live in
    // PreferenceProjectionBundles.kt — extracted so combine-shape plumbing
    // changes separately from the projection field-sets here.
}
