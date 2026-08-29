package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.datastore.BackupParser
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
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
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.security.hasSecuritySensitive
import com.raulshma.jellyplay.core.datastore.settings.buildUserPreferencesFromBackup
import com.raulshma.jellyplay.core.datastore.settings.buildUserPreferencesSnapshot
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Full-screen import preview. Mirrors `FactoryResetViewModel`'s one-shot
 * snapshot approach but compares **live** vs **incoming backup** (not vs
 * factory). Supports per-category and import-all, plus the synthetic
 * `AppRuntimeState` extras card ("everything" per user request).
 *
 * The backup file is loaded from the `uri` passed via navigation. v2 per-slice,
 * v1 enveloped aggregate and v0 bare aggregate are all supported — the decoded
 * `incomingPrefs` is produced via [buildUserPreferencesFromBackup] for v2 or
 * directly from the legacy `UserPreferences` for v0/v1.
 *
 * Security-sensitive lock fields are gated by the caller's
 * `restoreSecuritySensitive` flag (defaults false; UI exposes a checkbox).
 */
@HiltViewModel
class ImportPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesStore: UserPreferencesStore,
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
) : JellyPlayViewModel() {

    var currentPrefs by composeState(UserPreferences())
        private set
    var incomingPrefs by composeState<UserPreferences?>(null)
        private set

    var currentExtras by composeState(AppRuntimeState())
        private set
    var incomingExtras by composeState<AppRuntimeState?>(null)
        private set

    var rawBackup by composeState<SettingsBackup?>(null)
        private set
    var legacyIncoming by composeState<UserPreferences?>(null)
        private set

    var schemaVersion by composeState<Int?>(null)
        private set
    var isLegacy by composeState(false)
        private set
    var versionMismatch by composeState(false)
        private set
    var hasSecuritySensitive by composeState(false)
        private set

    var isLoading by composeState(true)
        private set
    var error by composeState<String?>(null)
        private set

    sealed interface ImportEvent {
        data object AllImported : ImportEvent
        data object CategoryImported : ImportEvent
        data object ExtrasImported : ImportEvent
        data class Failed(val message: String) : ImportEvent
    }

    var importEvent by composeState<ImportEvent?>(null)
        private set
    // Backward compat for previous String-based API (used by tests if any)
    var importStatus: String?
        get() = when (val e = importEvent) {
            is ImportEvent.AllImported -> "All settings imported"
            is ImportEvent.CategoryImported -> "Category imported"
            is ImportEvent.ExtrasImported -> "App state imported"
            is ImportEvent.Failed -> "Import failed: ${e.message}"
            null -> null
        }
        set(_) {}

    private val loadMutex = kotlinx.coroutines.sync.Mutex()
    private var loadedUri: String? = null

    init {
        launch {
            try {
                currentPrefs = buildCurrentSnapshot()
                currentExtras = appRuntimeStateStore.state.first()
            } catch (e: Exception) {
                error = e.message ?: "Failed to load current settings"
                isLoading = false
            }
            // Incoming remains loading until `loadBackup` is called from the screen.
            // Do not clear isLoading here — the screen will show the spinner.
        }
    }

    fun loadBackup(uriString: String) {
        launch {
            // Guard against concurrent loads; allow a *different* uri to reload
            // (config change or back→pick another file). The previous
            // `hasLoadedIncoming` boolean permanently blocked a second file.
            if (!loadMutex.tryLock()) return@launch
            try {
                if (loadedUri == uriString) return@launch
                loadedUri = uriString
                isLoading = true
                error = null
                // Ensure current snapshot is ready (init may still be running).
                // `buildCurrentSnapshot` is idempotent — re-reading the stores is cheap.
                if (currentPrefs == UserPreferences() && currentExtras == AppRuntimeState()) {
                    currentPrefs = buildCurrentSnapshot()
                    currentExtras = appRuntimeStateStore.state.first()
                }
                val uri = Uri.parse(uriString)
                loadIncoming(uri)
            } catch (e: Exception) {
                // Allow retry with same uri after failure.
                loadedUri = null
                error = e.message ?: "Failed to load backup"
            } finally {
                isLoading = false
                loadMutex.unlock()
            }
        }
    }

    private suspend fun buildCurrentSnapshot(): UserPreferences =
        buildUserPreferencesSnapshot(
            playback = playbackStore.playback.first(),
            videoPlayer = videoPlayerStore.videoPlayer.first(),
            engine = engineStore.playerEngine.first(),
            subtitle = subtitleLanguageStore.subtitle.first(),
            audio = audioStore.audio.first(),
            audioEffects = audioEffectsStore.audioEffects.first(),
            audioCache = audioCacheStore.audioCache.first(),
            appearance = appearanceStore.appearance.first(),
            homeDiscovery = homeDiscoveryStore.homeDiscovery.first(),
            library = libraryStore.library.first(),
            navigation = navigationStore.navigation.first(),
            downloads = downloadsStore.downloads.first(),
            networkOffline = networkOfflineStore.networkOffline.first(),
            notification = notificationStore.notification.first(),
            syncPlayCast = syncPlayCastStore.syncPlayCast.first(),
            screensaver = screensaverStore.screensaver.first(),
            security = securityStore.security.first(),
            experimental = experimentalStore.experimental.first(),
            runtime = appRuntimeStateStore.state.first(),
            pinLockout = pinRateLimiter.getPinLockoutState(),
        )

    private suspend fun loadIncoming(uri: Uri) {
        val jsonString = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: throw IllegalStateException("Cannot open backup file")
        }
        when (val parsed = BackupParser.parse(jsonString)) {
            is BackupParser.Parsed.V2 -> {
                rawBackup = parsed.backup
                schemaVersion = parsed.backup.schemaVersion
                isLegacy = false
                versionMismatch = false
                incomingPrefs = buildUserPreferencesFromBackup(parsed.backup)
                incomingExtras = parsed.backup.extras
                hasSecuritySensitive = parsed.hasSecuritySensitive
            }
            is BackupParser.Parsed.Future -> {
                rawBackup = parsed.backup
                schemaVersion = parsed.backup.schemaVersion
                isLegacy = false
                versionMismatch = true
                incomingPrefs = buildUserPreferencesFromBackup(parsed.backup)
                incomingExtras = parsed.backup.extras
                hasSecuritySensitive = parsed.hasSecuritySensitive
            }
            is BackupParser.Parsed.V1 -> {
                schemaVersion = SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION
                isLegacy = true
                versionMismatch = true
                incomingPrefs = parsed.preferences
                legacyIncoming = parsed.preferences
                incomingExtras = AppRuntimeState(
                    favoriteChannels = parsed.preferences.favoriteChannels,
                    watchLaterPlaylistId = parsed.preferences.watchLaterPlaylistId,
                    onboardingCompleted = parsed.preferences.onboardingCompleted,
                )
                hasSecuritySensitive = parsed.hasSecuritySensitive
            }
            is BackupParser.Parsed.V0 -> {
                schemaVersion = SettingsBackup.LEGACY_UNENVELOPED_SCHEMA_VERSION
                isLegacy = true
                versionMismatch = true
                incomingPrefs = parsed.preferences
                legacyIncoming = parsed.preferences
                incomingExtras = AppRuntimeState(
                    favoriteChannels = parsed.preferences.favoriteChannels,
                    watchLaterPlaylistId = parsed.preferences.watchLaterPlaylistId,
                    onboardingCompleted = parsed.preferences.onboardingCompleted,
                )
                hasSecuritySensitive = parsed.hasSecuritySensitive
            }
        }
    }

    /** Import every category plus extras. */
    fun importAll(restoreSecuritySensitive: Boolean, onDone: () -> Unit) {
        launch {
            try {
                val backup = rawBackup
                if (backup != null) {
                    userPreferencesStore.restoreV2(backup, restoreSecuritySensitive)
                } else {
                    val incoming = incomingPrefs ?: return@launch
                    userPreferencesStore.restorePreferences(incoming, restoreSecuritySensitive)
                    // Legacy extras are part of UserPreferences, already handled; still ensure runtime store.
                    incomingExtras?.let { appRuntimeStateStore.restore(it, clearNullIds = true) }
                }
                importEvent = ImportEvent.AllImported
                onDone()
            } catch (e: Exception) {
                importEvent = ImportEvent.Failed(e.message ?: "Unknown error")
            }
        }
    }

    /** Import a single preference category. */
    fun importCategory(category: PreferenceResetCategory, restoreSecuritySensitive: Boolean, onDone: () -> Unit) {
        launch {
            try {
                val backup = rawBackup
                if (backup != null) {
                    userPreferencesStore.restoreV2Categories(
                        backup = backup,
                        categories = setOf(category),
                        restoreSecuritySensitive = restoreSecuritySensitive,
                        includeExtras = false,
                    )
                } else {
                    // Legacy: merge at UserPreferences level for the single category.
                    val cur = currentPrefs
                    val inc = incomingPrefs ?: return@launch
                    val merged = mergeForCategory(cur, inc, category, restoreSecuritySensitive)
                    userPreferencesStore.restorePreferences(merged, restoreSecuritySensitive)
                }
                importEvent = ImportEvent.CategoryImported
                // Refresh current snapshot so diff updates without leaving screen.
                currentPrefs = buildCurrentSnapshot()
                currentExtras = appRuntimeStateStore.state.first()
                onDone()
            } catch (e: Exception) {
                importEvent = ImportEvent.Failed(e.message ?: "Unknown error")
            }
        }
    }

    /** Import only the AppRuntime extras card. */
    fun importExtras(onDone: () -> Unit) {
        launch {
            try {
                val backup = rawBackup
                if (backup != null) {
                    userPreferencesStore.restoreExtras(backup)
                } else {
                    incomingExtras?.let { appRuntimeStateStore.restore(it, clearNullIds = true) }
                }
                importEvent = ImportEvent.ExtrasImported
                currentExtras = appRuntimeStateStore.state.first()
                onDone()
            } catch (e: Exception) {
                importEvent = ImportEvent.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun clearImportStatus() {
        importEvent = null
    }

    fun clearImportEvent() {
        importEvent = null
    }

    /**
     * Very small, presentation-free merge for legacy per-category import.
     * Mirrors the 15 `PreferenceCategoryView` groupings but as pure data copy.
     * Only the fields surfaced in `PreferenceCategoryPresentation` are copied —
     * hidden runtime keys stay as-is, which matches the factory-reset surface.
     */
    private fun mergeForCategory(
        cur: UserPreferences,
        inc: UserPreferences,
        category: PreferenceResetCategory,
        restoreSecuritySensitive: Boolean,
    ): UserPreferences = when (category) {
        PreferenceResetCategory.APPEARANCE -> cur.copy(
            themeMode = inc.themeMode,
            contrastLevel = inc.contrastLevel,
            dynamicTheming = inc.dynamicTheming,
            oledMode = inc.oledMode,
            accentColorSwatch = inc.accentColorSwatch,
            colorStyle = inc.colorStyle,
            performanceMode = inc.performanceMode,
            reduceMotionEnabled = inc.reduceMotionEnabled,
            synthwaveMode = inc.synthwaveMode,
            synthwaveAccent = inc.synthwaveAccent,
            soothingMode = inc.soothingMode,
            soothingAccent = inc.soothingAccent,
            monochromeMode = inc.monochromeMode,
            backdropThemeMusicEnabled = inc.backdropThemeMusicEnabled,
            blueLightFilterEnabled = inc.blueLightFilterEnabled,
            blueLightFilterStrength = inc.blueLightFilterStrength,
            dateFormatPreference = inc.dateFormatPreference,
            appFontScale = inc.appFontScale,
            scheduledThemeStartHour = inc.scheduledThemeStartHour,
            scheduledThemeEndHour = inc.scheduledThemeEndHour,
            colorBlindMode = inc.colorBlindMode,
            handMode = inc.handMode,
        )
        PreferenceResetCategory.PLAYBACK -> cur.copy(
            preferredPlayer = inc.preferredPlayer,
            streamingQuality = inc.streamingQuality,
            cellularStreamingQuality = inc.cellularStreamingQuality,
            playbackMode = inc.playbackMode,
            decoderMode = inc.decoderMode,
            audioPassthrough = inc.audioPassthrough,
            frameRateMatching = inc.frameRateMatching,
            refreshRateMode = inc.refreshRateMode,
            videoDefaultOrientation = inc.videoDefaultOrientation,
            videoDefaultAspectRatio = inc.videoDefaultAspectRatio,
            videoPreloadBufferSize = inc.videoPreloadBufferSize,
            videoGesturesEnabled = inc.videoGesturesEnabled,
            videoPassOutProtectionHours = inc.videoPassOutProtectionHours,
            videoSkipBackOnResumeMs = inc.videoSkipBackOnResumeMs,
            videoHoldSpeedEnabled = inc.videoHoldSpeedEnabled,
            videoHoldSpeedMultiplier = inc.videoHoldSpeedMultiplier,
            videoDefaultSpeed = inc.videoDefaultSpeed,
            videoBrightnessLevel = inc.videoBrightnessLevel,
            videoAutoplayNext = inc.videoAutoplayNext,
            trailerAutoplay = inc.trailerAutoplay,
            cinemaModeEnabled = inc.cinemaModeEnabled,
            videoRememberBrightness = inc.videoRememberBrightness,
            videoAutoSkipIntro = inc.videoAutoSkipIntro,
            videoAutoSkipOutro = inc.videoAutoSkipOutro,
            videoRememberMuted = inc.videoRememberMuted,
            videoMuted = inc.videoMuted,
            videoGestureIndicatorSide = inc.videoGestureIndicatorSide,
            videoSeekDurationMs = inc.videoSeekDurationMs,
            videoControlsTimeoutMs = inc.videoControlsTimeoutMs,
            videoSwipeSeekMaxMs = inc.videoSwipeSeekMaxMs,
            audioDelayMs = inc.audioDelayMs,
            trickplayEnabled = inc.trickplayEnabled,
            trickplayOnSeekGesture = inc.trickplayOnSeekGesture,
            videoEpisodeBrowserEnabled = inc.videoEpisodeBrowserEnabled,
            videoShowPlaybackMetadata = inc.videoShowPlaybackMetadata,
            backgroundVideoAudioEnabled = inc.backgroundVideoAudioEnabled,
            autoPlayCountdownSec = inc.autoPlayCountdownSec,
            keepScreenOnDuringVideo = inc.keepScreenOnDuringVideo,
            incognitoModeEnabled = inc.incognitoModeEnabled,
            showClockInPlayer = inc.showClockInPlayer,
            showTimeRemaining = inc.showTimeRemaining,
            pauseOnAudioFocusLoss = inc.pauseOnAudioFocusLoss,
            duckOnTransientFocusLoss = inc.duckOnTransientFocusLoss,
            tvZoomModePercent = inc.tvZoomModePercent,
            segmentBehaviors = inc.segmentBehaviors,
        )
        PreferenceResetCategory.AUDIO -> cur.copy(
            audioDefaultSpeed = inc.audioDefaultSpeed,
            audioVisualizerEnabled = inc.audioVisualizerEnabled,
            audioGaplessEnabled = inc.audioGaplessEnabled,
            audioCrossfadeDurationMs = inc.audioCrossfadeDurationMs,
            audioNormalizationEnabled = inc.audioNormalizationEnabled,
            audioNormalizationMode = inc.audioNormalizationMode,
            replayGainPreAmpDb = inc.replayGainPreAmpDb,
            channelMixEnabled = inc.channelMixEnabled,
            channelMixMode = inc.channelMixMode,
            equalizerEnabled = inc.equalizerEnabled,
            equalizerSettings = inc.equalizerSettings,
            equalizerPreset = inc.equalizerPreset,
            bassBoostEnabled = inc.bassBoostEnabled,
            bassBoostStrength = inc.bassBoostStrength,
            virtualizerEnabled = inc.virtualizerEnabled,
            virtualizerStrength = inc.virtualizerStrength,
            reverbPreset = inc.reverbPreset,
            volumeBoostEnabled = inc.volumeBoostEnabled,
            volumeBoostGain = inc.volumeBoostGain,
            lrBalance = inc.lrBalance,
            autoEqByGenre = inc.autoEqByGenre,
            pitchSemitones = inc.pitchSemitones,
            audioAutoplayNext = inc.audioAutoplayNext,
            audioPreloadBufferSize = inc.audioPreloadBufferSize,
            audioNightModeVolume = inc.audioNightModeVolume,
            audioNightModeGain = inc.audioNightModeGain,
            audioSkipPreviousThresholdMs = inc.audioSkipPreviousThresholdMs,
            nightModeEnabled = inc.nightModeEnabled,
            nightModeStrength = inc.nightModeStrength,
            dialogueBoostEnabled = inc.dialogueBoostEnabled,
            dialogueBoostStrength = inc.dialogueBoostStrength,
            sleepTimerDurationMs = inc.sleepTimerDurationMs,
            sleepTimerEndOfEpisode = inc.sleepTimerEndOfEpisode,
            audioLyricsVisible = inc.audioLyricsVisible,
        )
        PreferenceResetCategory.SUBTITLES_LANGUAGE -> cur.copy(
            preferredSubtitleLanguage = inc.preferredSubtitleLanguage,
            preferredAudioLanguage = inc.preferredAudioLanguage,
            subtitlesForcedOnly = inc.subtitlesForcedOnly,
            subtitlePreviewInSettings = inc.subtitlePreviewInSettings,
            subtitleStyle = inc.subtitleStyle,
            highContrastSubtitles = inc.highContrastSubtitles,
            pgsSubtitleDirectPlay = inc.pgsSubtitleDirectPlay,
            hdrSubtitleStyleEnabled = inc.hdrSubtitleStyleEnabled,
            hdrSubtitleStyle = inc.hdrSubtitleStyle,
        )
        PreferenceResetCategory.DOWNLOADS_NETWORK -> cur.copy(
            wifiOnlyDownloads = inc.wifiOnlyDownloads,
            downloadConnections = inc.downloadConnections,
            maxConcurrentDownloads = inc.maxConcurrentDownloads,
            downloadQuality = inc.downloadQuality,
            smartDownloadsEnabled = inc.smartDownloadsEnabled,
            autoDownloadNewEpisodes = inc.autoDownloadNewEpisodes,
            maxDownloadStorageGb = inc.maxDownloadStorageGb,
            downloadStorageLocation = inc.downloadStorageLocation,
            maxCacheSizeMb = inc.maxCacheSizeMb,
            autoDeleteCache = inc.autoDeleteCache,
            manualOfflineEnabled = inc.manualOfflineEnabled,
            autoOfflineEnabled = inc.autoOfflineEnabled,
            manualBandwidthCap = inc.manualBandwidthCap,
            meteredNetworkBehavior = inc.meteredNetworkBehavior,
            adaptiveBitrateEnabled = inc.adaptiveBitrateEnabled,
            dataSaverEnabled = inc.dataSaverEnabled,
            verboseNetworkLogging = inc.verboseNetworkLogging,
            networkTimeoutPreset = inc.networkTimeoutPreset,
            cellularDownloadSizeWarningMb = inc.cellularDownloadSizeWarningMb,
            downloadScheduleEnabled = inc.downloadScheduleEnabled,
            downloadScheduleWindow = inc.downloadScheduleWindow,
        )
        PreferenceResetCategory.HOME_DISCOVERY -> cur.copy(
            homeMode = inc.homeMode,
            homeHeroEnabled = inc.homeHeroEnabled,
            hideTopHeaderOnScroll = inc.hideTopHeaderOnScroll,
            enabledHomeSectionTypes = inc.enabledHomeSectionTypes,
            homeSectionOrder = inc.homeSectionOrder,
            libraryHomeSectionOverrides = inc.libraryHomeSectionOverrides,
            libraryViewMode = inc.libraryViewMode,
            navBarShowLabels = inc.navBarShowLabels,
            hideBottomNavOnScroll = inc.hideBottomNavOnScroll,
            navItemOrder = inc.navItemOrder,
            hiddenNavItems = inc.hiddenNavItems,
            showUnwatchedBadge = inc.showUnwatchedBadge,
            hideWatchedItems = inc.hideWatchedItems,
            showWatchedCheckmark = inc.showWatchedCheckmark,
            showExternalRatings = inc.showExternalRatings,
            mergeContinueWatchingAndNextUp = inc.mergeContinueWatchingAndNextUp,
            nextUpMaxDays = inc.nextUpMaxDays,
            nextUpRewatching = inc.nextUpRewatching,
            nextUpExcludedSeriesIds = inc.nextUpExcludedSeriesIds,
            hiddenCwItemIds = inc.hiddenCwItemIds,
            pinnedHomeSections = inc.pinnedHomeSections,
            homeLayoutPresets = inc.homeLayoutPresets,
            continueWatchingClickBehavior = inc.continueWatchingClickBehavior,
            defaultLibrarySortOrders = inc.defaultLibrarySortOrders,
            libraryViewModes = inc.libraryViewModes,
            libraryFilters = inc.libraryFilters,
            hideEpisodeThumbnails = inc.hideEpisodeThumbnails,
            episodesDescending = inc.episodesDescending,
            skipSpecials = inc.skipSpecials,
            showClockOnHome = inc.showClockOnHome,
            showSettingsInHomeSearch = inc.showSettingsInHomeSearch,
        )
        PreferenceResetCategory.AUDIO_CACHE -> cur.copy(
            audioCachingEnabled = inc.audioCachingEnabled,
            audioCacheSizeMb = inc.audioCacheSizeMb,
            audioPrefetchLookahead = inc.audioPrefetchLookahead,
            audioPrefetchBackfill = inc.audioPrefetchBackfill,
            audioCacheNetworkPolicy = inc.audioCacheNetworkPolicy,
            audioCacheCellularMonthlyCapMb = inc.audioCacheCellularMonthlyCapMb,
        )
        PreferenceResetCategory.SECURITY -> if (restoreSecuritySensitive) {
            cur.copy(
                pinLockEnabled = inc.pinLockEnabled,
                pinHash = inc.pinHash,
                biometricLockEnabled = inc.biometricLockEnabled,
                usePinForPlayerLock = inc.usePinForPlayerLock,
                autoLockTimerMs = inc.autoLockTimerMs,
                remoteControlEnabled = inc.remoteControlEnabled,
            )
        } else {
            cur.copy(remoteControlEnabled = inc.remoteControlEnabled)
        }
        PreferenceResetCategory.NOTIFICATIONS -> cur.copy(
            notificationPreferences = inc.notificationPreferences,
        )
        PreferenceResetCategory.SCREENSAVER -> cur.copy(
            dreamImageCategories = inc.dreamImageCategories,
            dreamTransitionStyle = inc.dreamTransitionStyle,
            dreamKenBurnsEnabled = inc.dreamKenBurnsEnabled,
            dreamShowTitle = inc.dreamShowTitle,
            dreamSlideshowIntervalMs = inc.dreamSlideshowIntervalMs,
        )
        PreferenceResetCategory.NEWSLETTER -> cur.copy(
            newsletterEnabled = inc.newsletterEnabled,
            newsletterDayOfWeek = inc.newsletterDayOfWeek,
            enabledNewsletterSections = inc.enabledNewsletterSections,
            newsletterSectionOrder = inc.newsletterSectionOrder,
        )
        PreferenceResetCategory.SYNCPLAY_CASTING -> cur.copy(
            syncPlayJoinBehavior = inc.syncPlayJoinBehavior,
            syncPlayToleranceMs = inc.syncPlayToleranceMs,
            syncPlayAutoAcceptInvites = inc.syncPlayAutoAcceptInvites,
            defaultCastingStrategy = inc.defaultCastingStrategy,
            backgroundCastingEnabled = inc.backgroundCastingEnabled,
            preferredRenderer = inc.preferredRenderer,
            dvrPrePaddingMinutes = inc.dvrPrePaddingMinutes,
            dvrPostPaddingMinutes = inc.dvrPostPaddingMinutes,
            dvrRecordingQuality = inc.dvrRecordingQuality,
            liveStreamOption = inc.liveStreamOption,
        )
        PreferenceResetCategory.PLAYER_ENGINES -> cur.copy(
            mpvConfig = inc.mpvConfig,
            libVlcConfig = inc.libVlcConfig,
            exoPlayerConfig = inc.exoPlayerConfig,
        )
        PreferenceResetCategory.EXPERIMENTAL -> cur.copy(
            enabledExperimentalFeatures = inc.enabledExperimentalFeatures,
        )
        PreferenceResetCategory.MISC_APP -> cur.copy(
            hapticsEnabled = inc.hapticsEnabled,
            selfUpdateCheckEnabled = inc.selfUpdateCheckEnabled,
            selfUpdateDownloadEnabled = inc.selfUpdateDownloadEnabled,
            updateDismissPeriod = inc.updateDismissPeriod,
            appLanguage = inc.appLanguage,
            userDataSyncEnabled = inc.userDataSyncEnabled,
            showShareMediaOption = inc.showShareMediaOption,
            hideSearchHistory = inc.hideSearchHistory,
            androidTvWatchNextEnabled = inc.androidTvWatchNextEnabled,
            preferAudioDescription = inc.preferAudioDescription,
            showAdvancedSettings = inc.showAdvancedSettings,
        )
    }
}
