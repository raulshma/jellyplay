package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
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
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.AppearancePreferences
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.AudioPlayerPreferences
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-layer that projects the store-owned slices into the per-domain and
 * per-screen preference types defined in `core.model.PreferenceGroups`.
 *
 * Each [StateFlow] here is the successor to the legacy
 * `UserPreferencesStore.<slice>Preferences` flows. Those derived from the
 * whole `UserPreferences` aggregate (rebuilt on every edit anywhere); these
 * combine only the store slices a projection actually needs, so a sub-screen
 * collecting one slice recomposes only when its own fields change. The
 * projection shape — which store feeds which field — is a 1:1 port of the
 * `val UserPreferences.<slice>` extension properties in `PreferenceGroups.kt`,
 * so consumers keep reading the same field names.
 *
 * Field names on each slice match the projection target on purpose: it keeps
 * screen bodies (`slice.field`) untouched when a screen swaps from the
 * aggregate to its slice.
 */
@Singleton
class PreferenceProjections @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val playbackStore: PlaybackStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val engineStore: PlayerEngineStore,
    private val subtitleStore: SubtitleLanguageStore,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val audioCacheStore: AudioCacheStore,
    private val appearanceStore: AppearanceStore,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val libraryStore: LibraryStore,
    private val navigationStore: NavigationStore,
    private val downloadsStore: DownloadsStore,
    private val networkOfflineStore: NetworkOfflineStore,
    private val notificationStore: NotificationStore,
    private val syncPlayCastStore: SyncPlayCastStore,
    private val securityStore: SecurityStore,
    private val experimentalStore: ExperimentalStore,
) {
    // -------------------------------------------------------------------------
    // Per-domain projections.
    // -------------------------------------------------------------------------

    /** Fields one video-player surface reads, projected across the stores that own them. */
    val videoPlayerPreferences: StateFlow<VideoPlayerPreferences> =
        combine(
            playbackStore.playback,
            videoPlayerStore.videoPlayer,
            audioEffectsStore.audioEffects,
            appearanceStore.appearance,
            combine(subtitleStore.subtitle, engineStore.playerEngine) { sub, eng -> sub to eng },
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
        combine(audioStore.audio, audioEffectsStore.audioEffects) { audio, effects ->
            AudioPlayerPreferences(
                audioDefaultSpeed = audio.audioDefaultSpeed,
                audioNightModeVolume = audio.audioNightModeVolume,
                audioNightModeGain = audio.audioNightModeGain,
                audioSkipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs,
                audioAutoplayNext = audio.audioAutoplayNext,
                audioPreloadBufferSize = audio.audioPreloadBufferSize,
                audioNormalizationMode = audio.audioNormalizationMode,
                audioNormalizationEnabled = audio.audioNormalizationEnabled,
                replayGainPreAmpDb = audio.replayGainPreAmpDb,
                channelMixMode = audio.channelMixMode,
                channelMixEnabled = audio.channelMixEnabled,
                audioGaplessEnabled = audio.audioGaplessEnabled,
                audioCrossfadeDurationMs = audio.audioCrossfadeDurationMs,
                equalizerEnabled = effects.equalizerEnabled,
                equalizerSettings = effects.equalizerSettings,
                equalizerPreset = effects.equalizerPreset,
                bassBoostEnabled = effects.bassBoostEnabled,
                bassBoostStrength = effects.bassBoostStrength,
                virtualizerEnabled = effects.virtualizerEnabled,
                virtualizerStrength = effects.virtualizerStrength,
                reverbPreset = effects.reverbPreset,
                lrBalance = effects.lrBalance,
                autoEqByGenre = effects.autoEqByGenre,
                pitchSemitones = effects.pitchSemitones,
                audioDelayMs = audio.audioDelayMs,
                dialogueBoostEnabled = effects.dialogueBoostEnabled,
                dialogueBoostStrength = effects.dialogueBoostStrength,
                nightModeEnabled = effects.nightModeEnabled,
                nightModeStrength = effects.nightModeStrength,
                audioVisualizerEnabled = audio.audioVisualizerEnabled,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioPlayerPreferences())

    val subtitlePreferences: StateFlow<SubtitlePreferences> =
        subtitleStore.subtitle.map { sub ->
            SubtitlePreferences(
                subtitleStyle = sub.subtitleStyle,
                preferredSubtitleLanguage = sub.preferredSubtitleLanguage,
                preferredAudioLanguage = sub.preferredAudioLanguage,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SubtitlePreferences())

    val securityPreferences: StateFlow<SecurityPreferences> =
        combine(securityStore.security, videoPlayerStore.videoPlayer) { security, video ->
            SecurityPreferences(
                pinLockEnabled = security.pinLockEnabled,
                pinHash = security.pinHash,
                biometricLockEnabled = security.biometricLockEnabled,
                usePinForPlayerLock = security.usePinForPlayerLock,
                autoLockTimerMs = security.autoLockTimerMs,
                incognitoModeEnabled = video.incognitoModeEnabled,
                remoteControlEnabled = security.remoteControlEnabled,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SecurityPreferences())

    val downloadPreferences: StateFlow<DownloadPreferences> =
        combine(downloadsStore.downloads, networkOfflineStore.networkOffline) { downloads, network ->
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
        syncPlayCastStore.syncPlayCast.map { sp ->
            SyncPlayPreferences(
                syncPlayJoinBehavior = sp.syncPlayJoinBehavior,
                syncPlayToleranceMs = sp.syncPlayToleranceMs,
                syncPlayAutoAcceptInvites = sp.syncPlayAutoAcceptInvites,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncPlayPreferences())

    val appearancePreferences: StateFlow<AppearancePreferences> =
        combine(
            appearanceStore.appearance,
            navigationStore.navigation,
            homeDiscoveryStore.homeDiscovery,
            libraryStore.library,
        ) { appearance, navigation, home, library ->
            AppearancePreferences(
                dynamicTheming = appearance.dynamicTheming,
                themeMode = appearance.themeMode,
                contrastLevel = appearance.contrastLevel,
                oledMode = appearance.oledMode,
                accentColorSwatch = appearance.accentColorSwatch,
                colorStyle = appearance.colorStyle,
                navBarShowLabels = navigation.navBarShowLabels,
                homeHeroEnabled = home.homeHeroEnabled,
                homeBackdropEnabled = home.homeBackdropEnabled,
                performanceMode = appearance.performanceMode,
                synthwaveMode = appearance.synthwaveMode,
                synthwaveAccent = appearance.synthwaveAccent,
                soothingMode = appearance.soothingMode,
                soothingAccent = appearance.soothingAccent,
                monochromeMode = appearance.monochromeMode,
                libraryViewMode = library.libraryViewMode,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppearancePreferences())

    /** Notification sub-domain (matches the legacy aggregate shape). Eagerly cached. */
    val notificationPreferences: StateFlow<NotificationPreferences> =
        notificationStore.notification.map { it.notificationPreferences }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, NotificationPreferences())

    // -------------------------------------------------------------------------
    // Per-screen projections.
    // -------------------------------------------------------------------------

    /**
     * Fields read by `PlaybackSettingsScreen`. The broadest slice — spans the
     * video, playback, audio-effects, subtitle, audio, engine, and syncplay
     * stores. Nested combines keep within Flow's 5-arg combine arity.
     */
    val playbackPreferences: StateFlow<PlaybackPreferences> =
        combine(
            combine(
                playbackStore.playback,
                videoPlayerStore.videoPlayer,
                audioEffectsStore.audioEffects,
                combine(subtitleStore.subtitle, audioStore.audio) { sub, au -> sub to au },
                engineStore.playerEngine,
            ) { playback, video, effects, (subtitle, audio), engine ->
                PlaybackGroup1(playback, video, effects, subtitle, audio, engine)
            },
            syncPlayCastStore.syncPlayCast,
        ) { g1, syncPlayCast ->
            g1.toPlaybackPreferences(syncPlayCast)
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PlaybackPreferences())

    /** Fields read by `AudioSettingsScreen`. */
    val audioPreferences: StateFlow<AudioPreferences> =
        combine(
            audioStore.audio,
            audioEffectsStore.audioEffects,
            audioCacheStore.audioCache,
            experimentalStore.experimental,
        ) { audio, effects, cache, experimental ->
            AudioPreferences(
                audioDefaultSpeed = audio.audioDefaultSpeed,
                audioNightModeVolume = audio.audioNightModeVolume,
                audioNightModeGain = audio.audioNightModeGain,
                audioSkipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs,
                audioAutoplayNext = audio.audioAutoplayNext,
                audioPreloadBufferSize = audio.audioPreloadBufferSize,
                audioNormalizationMode = audio.audioNormalizationMode,
                audioNormalizationEnabled = audio.audioNormalizationEnabled,
                replayGainPreAmpDb = audio.replayGainPreAmpDb,
                channelMixMode = audio.channelMixMode,
                channelMixEnabled = audio.channelMixEnabled,
                audioGaplessEnabled = audio.audioGaplessEnabled,
                audioCrossfadeDurationMs = audio.audioCrossfadeDurationMs,
                equalizerEnabled = effects.equalizerEnabled,
                equalizerSettings = effects.equalizerSettings,
                equalizerPreset = effects.equalizerPreset,
                bassBoostEnabled = effects.bassBoostEnabled,
                bassBoostStrength = effects.bassBoostStrength,
                virtualizerEnabled = effects.virtualizerEnabled,
                virtualizerStrength = effects.virtualizerStrength,
                reverbPreset = effects.reverbPreset,
                lrBalance = effects.lrBalance,
                autoEqByGenre = effects.autoEqByGenre,
                pitchSemitones = effects.pitchSemitones,
                dialogueBoostEnabled = effects.dialogueBoostEnabled,
                dialogueBoostStrength = effects.dialogueBoostStrength,
                nightModeEnabled = effects.nightModeEnabled,
                nightModeStrength = effects.nightModeStrength,
                audioVisualizerEnabled = audio.audioVisualizerEnabled,
                audioCachingEnabled = cache.audioCachingEnabled,
                audioCacheSizeMb = cache.audioCacheSizeMb,
                audioPrefetchLookahead = cache.audioPrefetchLookahead,
                audioPrefetchBackfill = cache.audioPrefetchBackfill,
                audioCacheNetworkPolicy = cache.audioCacheNetworkPolicy,
                sleepTimerDurationMs = audio.sleepTimerDurationMs,
                preferAudioDescription = experimental.preferAudioDescription,
                volumeBoostEnabled = effects.volumeBoostEnabled,
                volumeBoostGain = effects.volumeBoostGain,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioPreferences())

    /** Fields read by `StorageSettingsScreen`. */
    val storagePreferences: StateFlow<StoragePreferences> =
        combine(
            downloadsStore.downloads,
            networkOfflineStore.networkOffline,
            playbackStore.playback,
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
        navigationStore.navigation.map { nav ->
            NavigationCustomizationPreferences(
                hiddenNavItems = nav.hiddenNavItems,
                navItemOrder = nav.navItemOrder,
                hideBottomNavOnScroll = nav.hideBottomNavOnScroll,
                navigationStyle = nav.navigationStyle,
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), NavigationCustomizationPreferences())

    /** Fields read by `LanguageSettingsScreen`. */
    val languagePreferences: StateFlow<LanguagePreferences> =
        combine(subtitleStore.subtitle, playbackStore.playback) { subtitle, playback ->
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
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LanguagePreferences())

    /** Fields read by `ExperimentalSettingsScreen`. */
    val experimentalPreferences: StateFlow<ExperimentalPreferences> =
        experimentalStore.experimental.map { exp ->
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
            appearanceStore.appearance,
            navigationStore.navigation,
            homeDiscoveryStore.homeDiscovery,
            libraryStore.library,
            experimentalStore.experimental,
            ::AppearanceGroup1,
        ),
        notificationStore.notification,
    ) { g1, notification ->
        AppearanceScreenPreferences(
            dynamicTheming = g1.appearance.dynamicTheming,
            themeMode = g1.appearance.themeMode,
            contrastLevel = g1.appearance.contrastLevel,
            oledMode = g1.appearance.oledMode,
            accentColorSwatch = g1.appearance.accentColorSwatch,
            colorStyle = g1.appearance.colorStyle,
            navBarShowLabels = g1.navigation.navBarShowLabels,
            homeHeroEnabled = g1.home.homeHeroEnabled,
            homeBackdropEnabled = g1.home.homeBackdropEnabled,
            performanceMode = g1.appearance.performanceMode,
            synthwaveMode = g1.appearance.synthwaveMode,
            synthwaveAccent = g1.appearance.synthwaveAccent,
            soothingMode = g1.appearance.soothingMode,
            soothingAccent = g1.appearance.soothingAccent,
            monochromeMode = g1.appearance.monochromeMode,
            libraryViewMode = g1.library.libraryViewMode,
            reduceMotionEnabled = g1.appearance.reduceMotionEnabled,
            blueLightFilterEnabled = g1.appearance.blueLightFilterEnabled,
            blueLightFilterStrength = g1.appearance.blueLightFilterStrength,
            appFontScale = g1.appearance.appFontScale,
            dateFormatPreference = g1.appearance.dateFormatPreference,
            colorBlindMode = g1.appearance.colorBlindMode,
            handMode = g1.appearance.handMode,
            hapticsEnabled = g1.appearance.hapticsEnabled,
            scheduledThemeStartHour = g1.appearance.scheduledThemeStartHour,
            scheduledThemeEndHour = g1.appearance.scheduledThemeEndHour,
            backdropThemeMusicEnabled = g1.appearance.backdropThemeMusicEnabled,
            homeMode = g1.home.homeMode,
            enabledHomeSectionTypes = g1.home.enabledHomeSectionTypes,
            homeSectionOrder = g1.home.homeSectionOrder,
            pinnedHomeSections = g1.home.pinnedHomeSections,
            homeLayoutPresets = g1.home.homeLayoutPresets,
            libraryHomeSectionOverrides = g1.home.libraryHomeSectionOverrides,
            hiddenCwItemIds = g1.home.hiddenCwItemIds,
            showUnwatchedBadge = g1.home.showUnwatchedBadge,
            hideWatchedItems = g1.home.hideWatchedItems,
            mergeContinueWatchingAndNextUp = g1.home.mergeContinueWatchingAndNextUp,
            nextUpMaxDays = g1.home.nextUpMaxDays,
            nextUpRewatching = g1.home.nextUpRewatching,
            continueWatchingClickBehavior = g1.home.continueWatchingClickBehavior,
            showWatchedCheckmark = g1.home.showWatchedCheckmark,
            hideEpisodeThumbnails = g1.library.hideEpisodeThumbnails,
            skipSpecials = g1.library.skipSpecials,
            compactEpisodeList = g1.library.compactEpisodeList,
            showExternalRatings = g1.home.showExternalRatings,
            showShareMediaOption = g1.experimental.showShareMediaOption,
            hideSearchHistory = g1.experimental.hideSearchHistory,
            showClockOnHome = g1.home.showClockOnHome,
            showSettingsInHomeSearch = g1.home.showSettingsInHomeSearch,
            newsletterEnabled = notification.newsletterEnabled,
            newsletterDayOfWeek = notification.newsletterDayOfWeek,
            enabledNewsletterSections = notification.enabledNewsletterSections,
            newsletterSectionOrder = notification.newsletterSectionOrder,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppearanceScreenPreferences())

    /** Tuple carrier for the nested playback combine (private). */
    private data class PlaybackGroup1(
        val playback: PlaybackSlice,
        val video: VideoPlayerSlice,
        val effects: AudioEffectsSlice,
        val subtitle: SubtitleSlice,
        val audio: AudioSlice,
        val engine: PlayerEngineSlice,
    ) {
        fun toPlaybackPreferences(sp: SyncPlayCastSlice) = PlaybackPreferences(
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
            cinemaModeEnabled = video.cinemaModeEnabled,
            videoSwipeSeekMaxMs = video.videoSwipeSeekMaxMs,
            videoRememberBrightness = video.videoRememberBrightness,
            videoBrightnessLevel = video.videoBrightnessLevel,
            videoGestureIndicatorSide = video.videoGestureIndicatorSide,
            videoSkipBackOnResumeMs = video.videoSkipBackOnResumeMs,
            videoPassOutProtectionHours = video.videoPassOutProtectionHours,
            trickplayEnabled = video.trickplayEnabled,
            trickplayOnSeekGesture = video.trickplayOnSeekGesture,
            segmentBehaviors = video.segmentBehaviors,
            videoEpisodeBrowserEnabled = video.videoEpisodeBrowserEnabled,
            videoShowPlaybackMetadata = video.videoShowPlaybackMetadata,
            videoPreloadBufferSize = video.videoPreloadBufferSize,
            keepScreenOnDuringVideo = playback.keepScreenOnDuringVideo,
            showTimeRemaining = video.showTimeRemaining,
            pauseOnAudioFocusLoss = playback.pauseOnAudioFocusLoss,
            duckOnTransientFocusLoss = playback.duckOnTransientFocusLoss,
            dialogueBoostEnabled = effects.dialogueBoostEnabled,
            dialogueBoostStrength = effects.dialogueBoostStrength,
            audioDelayMs = audio.audioDelayMs,
            backgroundVideoAudioEnabled = playback.backgroundVideoAudioEnabled,
            autoPlayCountdownSec = playback.autoPlayCountdownSec,
            incognitoModeEnabled = video.incognitoModeEnabled,
            showClockInPlayer = video.showClockInPlayer,
            tvZoomModePercent = video.tvZoomModePercent,
            streamingQuality = playback.streamingQuality,
            liveStreamOption = playback.liveStreamOption,
            mpvConfig = engine.mpvConfig,
            libVlcConfig = engine.libVlcConfig,
            exoPlayerConfig = engine.exoPlayerConfig,
            syncPlayJoinBehavior = sp.syncPlayJoinBehavior,
            syncPlayToleranceMs = sp.syncPlayToleranceMs,
            syncPlayAutoAcceptInvites = sp.syncPlayAutoAcceptInvites,
            defaultCastingStrategy = sp.defaultCastingStrategy,
            backgroundCastingEnabled = sp.backgroundCastingEnabled,
            preferredRenderer = sp.preferredRenderer,
            dvrPrePaddingMinutes = sp.dvrPrePaddingMinutes,
            dvrPostPaddingMinutes = sp.dvrPostPaddingMinutes,
            dvrRecordingQuality = sp.dvrRecordingQuality,
            androidTvWatchNextEnabled = playback.androidTvWatchNextEnabled,
        )
    }

    /** Tuple carrier for the nested appearance-screen combine (private). */
    private data class AppearanceGroup1(
        val appearance: AppearanceSlice,
        val navigation: NavigationSlice,
        val home: HomeDiscoverySlice,
        val library: LibrarySlice,
        val experimental: ExperimentalSlice,
    )
}
