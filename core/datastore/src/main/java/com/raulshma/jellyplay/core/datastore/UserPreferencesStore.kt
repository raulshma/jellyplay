package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.appearance
import com.raulshma.jellyplay.core.model.appearanceScreen
import com.raulshma.jellyplay.core.model.audio
import com.raulshma.jellyplay.core.model.audioPlayer
import com.raulshma.jellyplay.core.model.download
import com.raulshma.jellyplay.core.model.experimental
import com.raulshma.jellyplay.core.model.language
import com.raulshma.jellyplay.core.model.navigationCustomization
import com.raulshma.jellyplay.core.model.playback
import com.raulshma.jellyplay.core.model.security
import com.raulshma.jellyplay.core.model.storage
import com.raulshma.jellyplay.core.model.subtitle
import com.raulshma.jellyplay.core.model.syncPlay
import com.raulshma.jellyplay.core.model.videoPlayer
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    @com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    // Read-layer that projects the store slices into the per-domain / per-screen
    // preference types. The slice flows below delegate here so consumers keep
    // the same call sites while the aggregate read path is being retired.
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    // Domain stores: the facade forwards invariant-bearing setters to these so
    // the cross-key mutex / coerce / LRU / migration logic has a single owner.
    // All stores share the same `"user_prefs"` DataStore, so writes are
    // consistent regardless of which entry point a consumer uses.
    // (The Widget / ServerIdentity / PinRateLimiter collaborators were pruned
    // in Phase D — those consumers now inject those stores directly.)
    private val playbackStore: com.raulshma.jellyplay.core.datastore.playback.PlaybackStore,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val videoPlayerStore: com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore,
    private val downloadsStore: com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore,
    private val engineStore: com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore,
    private val homeDiscoveryStore: com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore,
    private val audioStore: com.raulshma.jellyplay.core.datastore.audio.AudioStore,
    private val audioEffectsStore: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore,
    private val audioCacheStore: com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore,
    private val libraryStore: com.raulshma.jellyplay.core.datastore.library.LibraryStore,
    private val navigationStore: com.raulshma.jellyplay.core.datastore.navigation.NavigationStore,
    private val networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore,
    private val notificationStore: com.raulshma.jellyplay.core.datastore.notification.NotificationStore,
    private val screensaverStore: com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore,
    private val securityStore: com.raulshma.jellyplay.core.datastore.security.SecurityStore,
    private val subtitleLanguageStore: com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore,
    private val syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
    private val experimentalStore: com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore,
    // Owns the 5 app-runtime-state keys (favorite channels, last live-TV channel,
    // watch-later playlist, onboarding flag, recent DLNA devices). Injected here
    // so backup export/import can fan out to it alongside the 18 domain stores.
    private val appRuntimeStateStore: com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore,
) {
    private val scope = externalScope

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emit(emptyPreferences()) }

    /**
     * Keys the facade itself owns — runtime / per-account / one-time state that
     * has no domain store. Every other preference key has a single owner: one
     * of the 18 domain-store `Keys` objects or `PinRateLimiter.Keys`. Those are
     * enumerated reflectively by [declaredKeys] (via `PreferenceCodec.reflectKeys`),
     * so they are not re-declared here.
     *
     * The aliases below point at the owning store's key for the few store-owned
     * keys the facade still reads directly (the per-item recall maps, the
     * notification last-viewed slot, the offline-mode toggles and the PIN
     * rate-limit counters). They keep a single declaration per key — a rename
     * in the owner is a compile error here, not a silent drift.
     */
    private object Keys {
        // Facade-owned: first-run / one-time / per-account runtime state.
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val TYPED_MIGRATION_DONE = PreferenceCodec.TYPED_MIGRATION_DONE
        val FAVORITE_CHANNELS = stringPreferencesKey("favorite_channels")
        val LIVE_TV_LAST_CHANNEL_ID = stringPreferencesKey("live_tv_last_channel_id")
        val RECENT_DLNA_DEVICES = stringPreferencesKey("recent_dlna_devices")
        val WATCH_LATER_PLAYLIST_ID = stringPreferencesKey("watch_later_playlist_id")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val DISMISSED_UPDATE_AT_MS = longPreferencesKey("dismissed_update_at_ms")

        // Aliases for store-owned keys the facade reads directly.
        val MEDIA_STREAM_SELECTIONS = com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore.Keys.MEDIA_STREAM_SELECTIONS
        val VIDEO_EFFECTS_SELECTIONS = com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore.Keys.VIDEO_EFFECTS_SELECTIONS
        val NEWSLETTER_LAST_VIEWED_MS = com.raulshma.jellyplay.core.datastore.notification.NotificationStore.Keys.NEWSLETTER_LAST_VIEWED_MS
        val MANUAL_OFFLINE_ENABLED = com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore.Keys.MANUAL_OFFLINE_ENABLED
        val AUTO_OFFLINE_ENABLED = com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore.Keys.AUTO_OFFLINE_ENABLED
        val PIN_FAILED_ATTEMPTS = PinRateLimiter.Keys.PIN_FAILED_ATTEMPTS
        val PIN_LOCKOUT_UNTIL_MS = PinRateLimiter.Keys.PIN_LOCKOUT_UNTIL_MS
    }

    private companion object {
        private val ENCODE_DEFAULTS_JSON get() = PreferenceCodec.encodeDefaultsJson
    }

    private val json: Json get() = PreferenceCodec.json

    init {
        scope.launch { migrateToTypedKeys() }
    }

    private suspend fun migrateToTypedKeys() {
        PreferenceCodec.runTypedKeyMigration(
            dataStore,
            booleans = arrayOf(
                "dynamic_theming", "oled_mode", "auto_delete_cache",
                "pin_lock_enabled", "biometric_lock_enabled", "dialogue_boost_enabled",
                "equalizer_enabled", "audio_passthrough", "frame_rate_matching",
                "night_mode_enabled", "video_gestures_enabled", "video_autoplay_next", "trailer_autoplay",
                "video_remember_brightness", "audio_autoplay_next", "trickplay_enabled",
                "trickplay_on_seek_gesture", "video_episode_browser_enabled",
                "video_show_playback_metadata", "audio_normalization_enabled",
                "channel_mix_enabled", "audio_gapless_enabled", "sleep_timer_end_of_episode",
                "dream_ken_burns_enabled", "dream_show_title", "bass_boost_enabled",
                "virtualizer_enabled", "auto_eq_by_genre", "home_hero_enabled", "home_backdrop_enabled",
                "nav_bar_show_labels", "onboarding_completed", "performance_mode",
                "newsletter_enabled", "wifi_only_downloads", "monochrome_mode",
            ),
            ints = arrayOf(
                "max_cache_size_mb", "audio_night_mode_gain", "download_connections",
                "virtualizer_strength", "newsletter_day_of_week",
            ),
            floats = arrayOf(
                "video_default_speed", "video_brightness_level", "audio_default_speed",
                "audio_night_mode_volume", "replaygain_pre_amp_db", "lr_balance",
                "pitch_semitones",
            ),
            longs = arrayOf(
                "audio_delay_ms", "auto_lock_timer_ms", "video_seek_duration_ms",
                "video_controls_timeout_ms", "video_swipe_seek_max_ms",
                "audio_skip_previous_threshold_ms", "audio_crossfade_duration_ms",
                "sleep_timer_duration_ms", "dream_slideshow_interval_ms",
                "newsletter_last_viewed_ms",
            ),
        )
    }

    private fun readBool(prefs: Preferences, key: Preferences.Key<Boolean>, name: String, default: Boolean): Boolean =
        PreferenceCodec.readBool(prefs, key, name, default)

    /**
     * Last-watched live TV channel id, used by `:feature:player:live` to
     * reopen the player on the same channel across launches. `null` when no
     * channel has been watched yet (or after [setLiveTvLastChannelId] is
     * called with `null`).
     */
    fun observeLiveTvLastChannelId(): Flow<String?> = sharedPrefs.map { prefs ->
        prefs[Keys.LIVE_TV_LAST_CHANNEL_ID]
    }

    suspend fun setLiveTvLastChannelId(channelId: String?) {
        dataStore.edit { prefs ->
            if (channelId == null) {
                prefs.remove(Keys.LIVE_TV_LAST_CHANNEL_ID)
            } else {
                prefs[Keys.LIVE_TV_LAST_CHANNEL_ID] = channelId
            }
        }
    }

    /** Facade-owned fields that no domain slice owns (see Phase C plan). */
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
            pinFailedAttempts = prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0,
            pinLockoutUntilEpochMs = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L,
            favoriteChannels = readFavoriteChannels(prefs),
            onboardingCompleted = readBool(prefs, Keys.ONBOARDING_COMPLETED, "onboarding_completed", false),
            watchLaterPlaylistId = prefs[Keys.WATCH_LATER_PLAYLIST_ID],
        )
    }

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
        val raw = prefs[Keys.FAVORITE_CHANNELS] ?: return emptySet()
        return try {
            json.decodeFromString<Set<String>>(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

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

    // Narrow per-key flows for hot-path consumers. Reading these avoids
    // collecting the full ~150-field `preferences` StateFlow (rebuilt on every
    // pref edit anywhere in the app) just to observe one or two booleans.
    val manualOfflineEnabled: Flow<Boolean> =
        sharedPrefs.map { it[Keys.MANUAL_OFFLINE_ENABLED] ?: false }.distinctUntilChanged()
    val autoOfflineEnabled: Flow<Boolean> =
        sharedPrefs.map { it[Keys.AUTO_OFFLINE_ENABLED] ?: true }.distinctUntilChanged()

    // Per-domain preference slices. Each is derived from the single source-of-truth
    // [preferences] StateFlow and de-duplicated on the slice's structural equality, so
    // a sub-screen collecting only its slice recomposes only when that slice actually
    // changes — not on every preference mutation app-wide. The group projections are
    // defined in `PreferenceGroups.kt`; deriving from [preferences] (rather than
    // re-reading the raw DataStore keys) keeps a single read path and avoids drift.
    val videoPlayerPreferences: StateFlow<com.raulshma.jellyplay.core.model.VideoPlayerPreferences> =
        projections.videoPlayerPreferences
    val audioPlayerPreferences: StateFlow<com.raulshma.jellyplay.core.model.AudioPlayerPreferences> =
        projections.audioPlayerPreferences
    val subtitlePreferences: StateFlow<com.raulshma.jellyplay.core.model.SubtitlePreferences> =
        projections.subtitlePreferences
    val securityPreferences: StateFlow<com.raulshma.jellyplay.core.model.SecurityPreferences> =
        projections.securityPreferences
    val downloadPreferences: StateFlow<com.raulshma.jellyplay.core.model.DownloadPreferences> =
        projections.downloadPreferences
    val syncPlayPreferences: StateFlow<com.raulshma.jellyplay.core.model.SyncPlayPreferences> =
        projections.syncPlayPreferences
    val appearancePreferences: StateFlow<com.raulshma.jellyplay.core.model.AppearancePreferences> =
        projections.appearancePreferences

    // Per-screen preference slices. Each mirrors the exact fields one settings
    // sub-screen reads (see the slice types in `PreferenceGroups.kt`), so a
    // sub-screen collecting its slice recomposes only when one of its fields
    // changes — not on every preference mutation app-wide. Like the per-domain
    // slices above, these derive from the single source-of-truth [preferences]
    // StateFlow and de-duplicate via the slice's structural equality.
    val playbackPreferences: StateFlow<com.raulshma.jellyplay.core.model.PlaybackPreferences> =
        projections.playbackPreferences
    val audioPreferences: StateFlow<com.raulshma.jellyplay.core.model.AudioPreferences> =
        projections.audioPreferences
    val storagePreferences: StateFlow<com.raulshma.jellyplay.core.model.StoragePreferences> =
        projections.storagePreferences
    val appearanceScreenPreferences: StateFlow<com.raulshma.jellyplay.core.model.AppearanceScreenPreferences> =
        projections.appearanceScreenPreferences
    val navigationCustomizationPreferences: StateFlow<com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences> =
        projections.navigationCustomizationPreferences
    val languagePreferences: StateFlow<com.raulshma.jellyplay.core.model.LanguagePreferences> =
        projections.languagePreferences
    val experimentalPreferences: StateFlow<com.raulshma.jellyplay.core.model.ExperimentalPreferences> =
        projections.experimentalPreferences

    /** Narrow flow of the pinned home-sections list for the pinned-sections screen. */
    val pinnedHomeSectionsFlow: StateFlow<List<com.raulshma.jellyplay.core.model.PinnedHomeSection>> =
        preferences.map { it.pinnedHomeSections }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Per-library disabled home sub-sections slice. The configure-libraries
     * screen collects this so it recomposes only when the override map changes.
     */
    val libraryHomeSectionOverridesFlow: StateFlow<Map<String, Set<HomeSectionType>>> =
        preferences.map { it.libraryHomeSectionOverrides }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        playbackStore.setPreferredPlayer(playerType)
    }

    suspend fun setLiveStreamOption(option: LiveStreamOption) {
        playbackStore.setLiveStreamOption(option)
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        subtitleLanguageStore.setPreferredSubtitleLanguage(language)
    }

    suspend fun setAppLanguage(language: String?) {
        subtitleLanguageStore.setAppLanguage(language)
    }

    suspend fun setPgsSubtitleDirectPlay(enabled: Boolean) {
        playbackStore.setPgsSubtitleDirectPlay(enabled)
    }

    suspend fun setBackdropThemeMusicEnabled(enabled: Boolean) {
        appearanceStore.setBackdropThemeMusicEnabled(enabled)
    }

    suspend fun setHiddenNavItems(items: Set<String>) {
        navigationStore.setHiddenNavItems(items)
    }

    suspend fun setNavItemOrder(order: List<String>) {
        navigationStore.setNavItemOrder(order)
    }

    suspend fun setSelfUpdateCheckEnabled(enabled: Boolean) {
        experimentalStore.setSelfUpdateCheckEnabled(enabled)
    }

    /**
     * Records that the user dismissed the prompt for [version], stamping the
     * current wall-clock time so the auto-check can suppress the same version
     * for 24 hours. Pass `null` to clear a prior dismissal (e.g. on a fresh
     * update check or after installing).
     */
    suspend fun setDismissedUpdate(version: String?, atMs: Long = System.currentTimeMillis()) {
        dataStore.edit {
            if (version == null) {
                it.remove(Keys.DISMISSED_UPDATE_VERSION)
                it.remove(Keys.DISMISSED_UPDATE_AT_MS)
            } else {
                it[Keys.DISMISSED_UPDATE_VERSION] = version
                it[Keys.DISMISSED_UPDATE_AT_MS] = atMs
            }
        }
    }

    suspend fun setHideEpisodeThumbnails(enabled: Boolean) {
        libraryStore.setHideEpisodeThumbnails(enabled)
    }

    suspend fun setEpisodesDescending(descending: Boolean) {
        libraryStore.setEpisodesDescending(descending)
    }

    suspend fun setSkipSpecials(enabled: Boolean) {
        libraryStore.setSkipSpecials(enabled)
    }

    suspend fun setCellularDownloadSizeWarningMb(sizeMb: Int) {
        downloadsStore.setCellularDownloadSizeWarningMb(sizeMb)
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        appearanceStore.setHapticsEnabled(enabled)
    }

    suspend fun setDateFormatPreference(preference: DateFormatPreference) {
        appearanceStore.setDateFormatPreference(preference)
    }

    suspend fun setAppFontScale(scale: AppFontScale) {
        appearanceStore.setAppFontScale(scale)
    }

    suspend fun setScheduledThemeStartHour(hour: Int) {
        appearanceStore.setScheduledThemeStartHour(hour)
    }

    suspend fun setScheduledThemeEndHour(hour: Int) {
        appearanceStore.setScheduledThemeEndHour(hour)
    }

    suspend fun setColorBlindMode(mode: ColorBlindMode) {
        appearanceStore.setColorBlindMode(mode)
    }

    suspend fun setHandMode(mode: HandMode) {
        appearanceStore.setHandMode(mode)
    }

    suspend fun setDownloadScheduleEnabled(enabled: Boolean) {
        downloadsStore.setDownloadScheduleEnabled(enabled)
    }

    suspend fun setDownloadScheduleWindow(window: DownloadScheduleWindow) {
        downloadsStore.setDownloadScheduleWindow(window)
    }

    suspend fun setSubtitlesForcedOnly(enabled: Boolean) {
        subtitleLanguageStore.setSubtitlesForcedOnly(enabled)
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        subtitleLanguageStore.setPreferredAudioLanguage(language)
    }

    suspend fun setMediaStreamSelection(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        // Forwarded to PlayerEngineStore: the per-item 100-entry LRU cap has a
        // single owner there.
        engineStore.setMediaStreamSelection(
            itemId = itemId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
    }

    /**
     * Persist the per-item video filter settings. Passing a neutral config
     * (all defaults) clears the entry so storage does not grow unbounded.
     */
    suspend fun setVideoEffectsForItem(itemId: String, effects: VideoEffectsConfig) {
        engineStore.setVideoEffectsForItem(itemId, effects)
    }

    suspend fun setDynamicTheming(enabled: Boolean) {
        appearanceStore.setDynamicTheming(enabled)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appearanceStore.setThemeMode(mode)
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        appearanceStore.setContrastLevel(level)
    }

    suspend fun setOledMode(enabled: Boolean) {
        appearanceStore.setOledMode(enabled)
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        subtitleLanguageStore.setSubtitleStyle(style)
    }

    /**
     * Persists a per-item subtitle-sync delay (G9). A `delayMs` of 0 removes the
     * entry so the map doesn't grow unbounded with neutral values.
     */
    suspend fun setSubtitleDelayForItem(itemId: String, delayMs: Long) {
        subtitleLanguageStore.setSubtitleDelayForItem(itemId, delayMs)
    }

    suspend fun setHdrSubtitleStyleEnabled(enabled: Boolean) {
        subtitleLanguageStore.setHdrSubtitleStyleEnabled(enabled)
    }

    suspend fun setHdrSubtitleStyle(style: SubtitleStyle) {
        subtitleLanguageStore.setHdrSubtitleStyle(style)
    }

    suspend fun setStreamingQuality(quality: StreamingQuality) {
        playbackStore.setStreamingQuality(quality)
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        playbackStore.setPlaybackMode(mode)
    }

    suspend fun setMaxCacheSize(sizeMb: Int) {
        networkOfflineStore.setMaxCacheSize(sizeMb)
    }

    suspend fun setAutoDeleteCache(enabled: Boolean) {
        networkOfflineStore.setAutoDeleteCache(enabled)
    }

    suspend fun setShowAdvancedSettings(enabled: Boolean) {
        appearanceStore.setShowAdvancedSettings(enabled)
    }

    suspend fun setEnabledExperimentalFeatures(features: Set<com.raulshma.jellyplay.core.model.ExperimentalFeature>) {
        experimentalStore.setEnabledExperimentalFeatures(features)
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        audioStore.setAudioVisualizerEnabled(enabled)
    }

    suspend fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) {
        syncPlayCastStore.setSyncPlayJoinBehavior(behavior)
    }

    suspend fun setSyncPlayToleranceMs(ms: Long) {
        syncPlayCastStore.setSyncPlayToleranceMs(ms)
    }

    suspend fun setSyncPlayAutoAcceptInvites(enabled: Boolean) {
        syncPlayCastStore.setSyncPlayAutoAcceptInvites(enabled)
    }

    suspend fun setDefaultCastingStrategy(strategy: CastingStrategy) {
        syncPlayCastStore.setDefaultCastingStrategy(strategy)
    }

    suspend fun setBackgroundCastingEnabled(enabled: Boolean) {
        syncPlayCastStore.setBackgroundCastingEnabled(enabled)
    }

    suspend fun setPreferredRenderer(renderer: String?) {
        syncPlayCastStore.setPreferredRenderer(renderer)
    }

    suspend fun setWatchLaterPlaylistId(playlistId: String?) {
        dataStore.edit {
            if (playlistId != null) it[Keys.WATCH_LATER_PLAYLIST_ID] = playlistId
            else it.remove(Keys.WATCH_LATER_PLAYLIST_ID)
        }
    }

    suspend fun setDvrPrePaddingMinutes(minutes: Int) {
        syncPlayCastStore.setDvrPrePaddingMinutes(minutes)
    }

    suspend fun setDvrPostPaddingMinutes(minutes: Int) {
        syncPlayCastStore.setDvrPostPaddingMinutes(minutes)
    }

    suspend fun setDvrRecordingQuality(quality: String) {
        syncPlayCastStore.setDvrRecordingQuality(quality)
    }

    suspend fun setFavoriteChannels(channels: Set<String>) {
        dataStore.edit { it[Keys.FAVORITE_CHANNELS] = json.encodeToString(channels) }
    }

    suspend fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) {
        notificationStore.setEnabledNewsletterSections(sections)
    }

    suspend fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) {
        notificationStore.setNewsletterSectionOrder(order)
    }

    suspend fun setManualOffline(enabled: Boolean) {
        networkOfflineStore.setManualOffline(enabled)
    }

    suspend fun setAutoOfflineEnabled(enabled: Boolean) {
        networkOfflineStore.setAutoOfflineEnabled(enabled)
    }

    suspend fun setManualBandwidthCap(cap: Long) {
        networkOfflineStore.setManualBandwidthCap(cap)
    }

    suspend fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) {
        networkOfflineStore.setMeteredNetworkBehavior(behavior)
    }

    suspend fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        networkOfflineStore.setAdaptiveBitrateEnabled(enabled)
    }


    suspend fun setBackgroundVideoAudioEnabled(enabled: Boolean) {
        playbackStore.setBackgroundVideoAudioEnabled(enabled)
    }

    suspend fun setAutoPlayCountdownSec(sec: Int) {
        playbackStore.setAutoPlayCountdownSec(sec)
    }

    suspend fun setShowUnwatchedBadge(enabled: Boolean) {
        homeDiscoveryStore.setShowUnwatchedBadge(enabled)
    }

    suspend fun setHideWatchedItems(enabled: Boolean) {
        homeDiscoveryStore.setHideWatchedItems(enabled)
    }

    suspend fun setMergeContinueWatchingAndNextUp(enabled: Boolean) {
        homeDiscoveryStore.setMergeContinueWatchingAndNextUp(enabled)
    }

    suspend fun setNextUpMaxDays(days: Int) {
        // Forwarded to HomeDiscoveryStore: the coerceAtLeast(0) invariant has a
        // single owner there.
        homeDiscoveryStore.setNextUpMaxDays(days)
    }

    suspend fun setNextUpRewatching(enabled: Boolean) {
        homeDiscoveryStore.setNextUpRewatching(enabled)
    }

    suspend fun setNextUpExcludedSeriesIds(ids: Set<String>) {
        homeDiscoveryStore.setNextUpExcludedSeriesIds(ids)
    }

    suspend fun excludeSeriesFromNextUp(seriesId: String) {
        homeDiscoveryStore.excludeSeriesFromNextUp(seriesId)
    }

    suspend fun includeSeriesInNextUp(seriesId: String) {
        homeDiscoveryStore.includeSeriesInNextUp(seriesId)
    }

    suspend fun setHiddenCwItemIds(ids: Set<String>) {
        homeDiscoveryStore.setHiddenCwItemIds(ids)
    }

    suspend fun hideCwItem(itemId: String) {
        homeDiscoveryStore.hideCwItem(itemId)
    }

    suspend fun unhideCwItem(itemId: String) {
        homeDiscoveryStore.unhideCwItem(itemId)
    }

    suspend fun unhideAllCwItems() {
        homeDiscoveryStore.unhideAllCwItems()
    }

    suspend fun setPinnedHomeSections(sections: List<PinnedHomeSection>) {
        homeDiscoveryStore.setPinnedHomeSections(sections)
    }

    suspend fun addPinnedHomeSection(section: PinnedHomeSection) {
        homeDiscoveryStore.addPinnedHomeSection(section)
    }

    suspend fun removePinnedHomeSection(sectionId: String) {
        homeDiscoveryStore.removePinnedHomeSection(sectionId)
    }

    suspend fun setHomeLayoutPresets(presets: List<HomeLayoutPreset>) {
        homeDiscoveryStore.setHomeLayoutPresets(presets)
    }

    suspend fun saveHomeLayoutPreset(preset: HomeLayoutPreset) {
        homeDiscoveryStore.saveHomeLayoutPreset(preset)
    }

    suspend fun deleteHomeLayoutPreset(presetId: String) {
        homeDiscoveryStore.deleteHomeLayoutPreset(presetId)
    }

    suspend fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) {
        homeDiscoveryStore.setContinueWatchingClickBehavior(behavior)
    }

    suspend fun setCellularStreamingQuality(quality: StreamingQuality) {
        playbackStore.setCellularStreamingQuality(quality)
    }

    suspend fun setShowWatchedCheckmark(enabled: Boolean) {
        homeDiscoveryStore.setShowWatchedCheckmark(enabled)
    }

    suspend fun setDefaultLibrarySortOrder(libraryId: String, order: String) {
        libraryStore.setDefaultLibrarySortOrder(libraryId, order)
    }

    suspend fun setLibraryViewMode(libraryId: String, viewMode: String) {
        libraryStore.setLibraryViewMode(libraryId, viewMode)
    }

    suspend fun setLibraryFilters(libraryId: String, filters: String) {
        libraryStore.setLibraryFilters(libraryId, filters)
    }

    suspend fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        playbackStore.setKeepScreenOnDuringVideo(enabled)
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        downloadsStore.setDownloadQuality(quality)
    }

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        downloadsStore.setSmartDownloadsEnabled(enabled)
    }

    suspend fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        downloadsStore.setAutoDownloadNewEpisodes(enabled)
    }

    suspend fun setIncognitoModeEnabled(enabled: Boolean) {
        videoPlayerStore.setIncognitoModeEnabled(enabled)
    }

    suspend fun setShowTimeRemaining(enabled: Boolean) {
        videoPlayerStore.setShowTimeRemaining(enabled)
    }

    suspend fun setShowClockOnHome(enabled: Boolean) {
        homeDiscoveryStore.setShowClockOnHome(enabled)
    }

    suspend fun setShowSettingsInHomeSearch(enabled: Boolean) {
        homeDiscoveryStore.setShowSettingsInHomeSearch(enabled)
    }

    suspend fun setShowClockInPlayer(enabled: Boolean) {
        videoPlayerStore.setShowClockInPlayer(enabled)
    }

    suspend fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        playbackStore.setPauseOnAudioFocusLoss(enabled)
    }

    suspend fun setDuckOnTransientFocusLoss(enabled: Boolean) {
        playbackStore.setDuckOnTransientFocusLoss(enabled)
    }

    suspend fun setVolumeBoostEnabled(enabled: Boolean) {
        audioEffectsStore.setVolumeBoostEnabled(enabled)
    }

    suspend fun setVolumeBoostGain(gain: Int) {
        audioEffectsStore.setVolumeBoostGain(gain)
    }

    suspend fun setAudioLyricsVisible(enabled: Boolean) {
        audioStore.setAudioLyricsVisible(enabled)
    }

    suspend fun setShowShareMediaOption(enabled: Boolean) {
        experimentalStore.setShowShareMediaOption(enabled)
    }

    suspend fun setShowExternalRatings(enabled: Boolean) {
        homeDiscoveryStore.setShowExternalRatings(enabled)
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    suspend fun setDialogueBoostEnabled(enabled: Boolean) {
        audioEffectsStore.setDialogueBoostEnabled(enabled)
    }

    suspend fun setDialogueBoostStrength(strength: EffectStrength) {
        audioEffectsStore.setDialogueBoostStrength(strength)
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        audioEffectsStore.setEqualizerEnabled(enabled)
    }

    suspend fun setEqualizerSettings(settings: EqualizerSettings) {
        audioEffectsStore.setEqualizerSettings(settings)
    }

    suspend fun setAudioDelay(ms: Long) {
        audioStore.setAudioDelay(ms)
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        playbackStore.setDecoderMode(mode)
    }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        playbackStore.setAudioPassthrough(enabled)
    }

    suspend fun setFrameRateMatching(enabled: Boolean) {
        // Forwarded to PlaybackStore: the frame-rate↔refresh-rate mutex has a
        // single owner there, so callers going through the facade and callers
        // injecting PlaybackStore directly cannot diverge.
        playbackStore.setFrameRateMatching(enabled)
    }

    suspend fun setRefreshRateMode(mode: com.raulshma.jellyplay.core.model.RefreshRateMode) {
        playbackStore.setRefreshRateMode(mode)
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        audioEffectsStore.setNightModeEnabled(enabled)
    }

    suspend fun setNightModeStrength(strength: EffectStrength) {
        audioEffectsStore.setNightModeStrength(strength)
    }

    suspend fun setHomeMode(mode: HomeMode) {
        homeDiscoveryStore.setHomeMode(mode)
    }

    suspend fun setVideoSeekDurationMs(ms: Long) {
        videoPlayerStore.setVideoSeekDurationMs(ms)
    }

    suspend fun setVideoDefaultOrientation(mode: OrientationMode) {
        videoPlayerStore.setVideoDefaultOrientation(mode)
    }

    suspend fun setVideoControlsTimeoutMs(ms: Long) {
        videoPlayerStore.setVideoControlsTimeoutMs(ms)
    }

    suspend fun setVideoGesturesEnabled(enabled: Boolean) {
        videoPlayerStore.setVideoGesturesEnabled(enabled)
    }

    suspend fun setVideoSkipBackOnResumeMs(ms: Long) {
        // Forwarded to VideoPlayerStore: the coerceAtLeast(0L) invariant has a
        // single owner there.
        videoPlayerStore.setVideoSkipBackOnResumeMs(ms)
    }

    suspend fun setVideoPassOutProtectionHours(hours: Int) {
        // Forwarded to VideoPlayerStore: the coerceAtLeast(0) invariant has a
        // single owner there.
        videoPlayerStore.setVideoPassOutProtectionHours(hours)
    }

    suspend fun setVideoHoldSpeedEnabled(enabled: Boolean) {
        videoPlayerStore.setVideoHoldSpeedEnabled(enabled)
    }

    suspend fun setVideoHoldSpeedMultiplier(multiplier: Float) {
        videoPlayerStore.setVideoHoldSpeedMultiplier(multiplier)
    }

    suspend fun setVideoDefaultSpeed(speed: Float) {
        videoPlayerStore.setVideoDefaultSpeed(speed)
    }

    suspend fun setVideoDefaultAspectRatio(ratio: String) {
        videoPlayerStore.setVideoDefaultAspectRatio(ratio)
    }

    suspend fun setVideoAutoplayNext(enabled: Boolean) {
        videoPlayerStore.setVideoAutoplayNext(enabled)
    }

    suspend fun setTrailerAutoplay(enabled: Boolean) {
        videoPlayerStore.setTrailerAutoplay(enabled)
    }

    suspend fun setCinemaModeEnabled(enabled: Boolean) {
        videoPlayerStore.setCinemaModeEnabled(enabled)
    }

    suspend fun setVideoSwipeSeekMaxMs(ms: Long) {
        videoPlayerStore.setVideoSwipeSeekMaxMs(ms)
    }

    suspend fun setVideoRememberBrightness(enabled: Boolean) {
        videoPlayerStore.setVideoRememberBrightness(enabled)
    }

    suspend fun setVideoBrightnessLevel(level: Float) {
        videoPlayerStore.setVideoBrightnessLevel(level)
    }

    suspend fun setVideoRememberVolume(enabled: Boolean) {
        videoPlayerStore.setVideoRememberVolume(enabled)
    }

    suspend fun setVideoVolumeLevel(level: Float) {
        videoPlayerStore.setVideoVolumeLevel(level)
    }

    suspend fun setVideoAutoSkipIntro(enabled: Boolean) {
        videoPlayerStore.setVideoAutoSkipIntro(enabled)
    }

    suspend fun setVideoAutoSkipOutro(enabled: Boolean) {
        videoPlayerStore.setVideoAutoSkipOutro(enabled)
    }

    suspend fun setVideoRememberMuted(enabled: Boolean) {
        videoPlayerStore.setVideoRememberMuted(enabled)
    }

    suspend fun setVideoMuted(muted: Boolean) {
        videoPlayerStore.setVideoMuted(muted)
    }

    suspend fun setSubtitlePreviewInSettings(enabled: Boolean) {
        subtitleLanguageStore.setSubtitlePreviewInSettings(enabled)
    }

    suspend fun setVideoGestureIndicatorSide(side: GestureIndicatorSide) {
        videoPlayerStore.setVideoGestureIndicatorSide(side)
    }

    suspend fun setAudioDefaultSpeed(speed: Float) {
        audioStore.setAudioDefaultSpeed(speed)
    }

    suspend fun setAudioNightModeVolume(volume: Float) {
        audioStore.setAudioNightModeVolume(volume)
    }

    suspend fun setAudioNightModeGain(gain: Int) {
        audioStore.setAudioNightModeGain(gain)
    }

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) {
        audioStore.setAudioSkipPreviousThresholdMs(ms)
    }

    suspend fun setAudioAutoplayNext(enabled: Boolean) {
        audioStore.setAudioAutoplayNext(enabled)
    }

    suspend fun setTrickplayEnabled(enabled: Boolean) {
        videoPlayerStore.setTrickplayEnabled(enabled)
    }

    suspend fun setTrickplayOnSeekGesture(enabled: Boolean) {
        videoPlayerStore.setTrickplayOnSeekGesture(enabled)
    }

    suspend fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) {
        // Forwarded to VideoPlayerStore: the segment-behavior legacy migration
        // + read-modify-write has a single owner there.
        videoPlayerStore.setSegmentBehavior(type, behavior)
    }

    suspend fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        videoPlayerStore.setVideoEpisodeBrowserEnabled(enabled)
    }

    suspend fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        videoPlayerStore.setVideoShowPlaybackMetadata(enabled)
    }

    suspend fun setVideoPreloadBufferSize(size: PreloadBufferSize) {
        videoPlayerStore.setVideoPreloadBufferSize(size)
    }

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) {
        audioStore.setAudioPreloadBufferSize(size)
    }

    suspend fun setAudioCachingEnabled(enabled: Boolean) {
        audioCacheStore.setAudioCachingEnabled(enabled)
    }

    suspend fun setAudioCacheSizeMb(sizeMb: Int) {
        audioCacheStore.setAudioCacheSizeMb(sizeMb)
    }

    suspend fun setAudioPrefetchLookahead(lookahead: Int) {
        audioCacheStore.setAudioPrefetchLookahead(lookahead)
    }

    suspend fun setAudioPrefetchBackfill(backfill: Int) {
        audioCacheStore.setAudioPrefetchBackfill(backfill)
    }

    suspend fun setAudioCacheNetworkPolicy(policy: AudioCacheNetworkPolicy) {
        audioCacheStore.setAudioCacheNetworkPolicy(policy)
    }

    suspend fun setAudioCacheCellularMonthlyCapMb(capMb: Int) {
        audioCacheStore.setAudioCacheCellularMonthlyCapMb(capMb)
    }

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        audioStore.setAudioNormalizationMode(mode)
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        audioStore.setAudioNormalizationEnabled(enabled)
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        audioStore.setReplayGainPreAmpDb(db)
    }

    suspend fun setChannelMixMode(mode: ChannelMixMode) {
        audioStore.setChannelMixMode(mode)
    }

    suspend fun setChannelMixEnabled(enabled: Boolean) {
        audioStore.setChannelMixEnabled(enabled)
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        audioStore.setAudioGaplessEnabled(enabled)
    }

    suspend fun setCrossfadeDurationMs(ms: Long) {
        audioStore.setAudioCrossfadeDurationMs(ms)
    }

    suspend fun setSleepTimerDurationMs(ms: Long) {
        audioStore.setSleepTimerDurationMs(ms)
    }

    suspend fun setSleepTimerEndOfEpisode(enabled: Boolean) {
        audioStore.setSleepTimerEndOfEpisode(enabled)
    }

    suspend fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        screensaverStore.setDreamImageCategories(categories)
    }

    suspend fun setDreamSlideshowIntervalMs(ms: Long) {
        screensaverStore.setDreamSlideshowIntervalMs(ms)
    }

    suspend fun setDreamKenBurnsEnabled(enabled: Boolean) {
        screensaverStore.setDreamKenBurnsEnabled(enabled)
    }

    suspend fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        screensaverStore.setDreamTransitionStyle(style)
    }

    suspend fun setDreamShowTitle(enabled: Boolean) {
        screensaverStore.setDreamShowTitle(enabled)
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        audioEffectsStore.setEqualizerPreset(preset)
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        audioEffectsStore.setBassBoostEnabled(enabled)
    }

    suspend fun setBassBoostStrength(strength: EffectStrength) {
        audioEffectsStore.setBassBoostStrength(strength)
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        audioEffectsStore.setVirtualizerEnabled(enabled)
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        audioEffectsStore.setVirtualizerStrength(strength)
    }

    suspend fun setReverbPreset(preset: ReverbPreset) {
        audioEffectsStore.setReverbPreset(preset)
    }

    suspend fun setLrBalance(balance: Float) {
        audioEffectsStore.setLrBalance(balance)
    }

    suspend fun setAutoEqByGenre(enabled: Boolean) {
        audioEffectsStore.setAutoEqByGenre(enabled)
    }

    suspend fun setPitchSemitones(semitones: Float) {
        audioEffectsStore.setPitchSemitones(semitones)
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        downloadsStore.setWifiOnlyDownloads(enabled)
    }

    suspend fun setDownloadConnections(count: Int) {
        downloadsStore.setDownloadConnections(count)
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        // Forwarded to DownloadsStore: the coerceIn(1, 6) invariant has a single
        // owner there.
        downloadsStore.setMaxConcurrentDownloads(count)
    }

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        homeDiscoveryStore.setEnabledHomeSectionTypes(types)
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) {
        homeDiscoveryStore.setHomeSectionOrder(order)
    }

    suspend fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) {
        homeDiscoveryStore.setLibraryHomeSectionOverrides(overrides)
    }

    suspend fun setNavBarShowLabels(show: Boolean) {
        navigationStore.setNavBarShowLabels(show)
    }

    suspend fun setHideBottomNavOnScroll(hide: Boolean) {
        navigationStore.setHideBottomNavOnScroll(hide)
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) {
        homeDiscoveryStore.setHomeHeroEnabled(enabled)
    }

    suspend fun setHomeBackdropEnabled(enabled: Boolean) {
        homeDiscoveryStore.setHomeBackdropEnabled(enabled)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMpvConfig(config: MpvEngineConfig) {
        engineStore.setMpvConfig(config)
    }

    suspend fun setLibVlcConfig(config: LibVlcEngineConfig) {
        engineStore.setLibVlcConfig(config)
    }

    suspend fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        engineStore.setExoPlayerConfig(config)
    }

    suspend fun setPerformanceMode(enabled: Boolean) {
        appearanceStore.setPerformanceMode(enabled)
    }

    suspend fun setNewsletterEnabled(enabled: Boolean) {
        notificationStore.setNewsletterEnabled(enabled)
    }

    suspend fun setNewsletterDayOfWeek(day: Int) {
        notificationStore.setNewsletterDayOfWeek(day)
    }

    suspend fun setNewsletterLastViewed(timestampMs: Long) {
        notificationStore.setNewsletterLastViewed(timestampMs)
    }

    suspend fun setAccentColorSwatch(swatch: String) {
        appearanceStore.setAccentColorSwatch(swatch)
    }

    suspend fun setColorStyle(style: ColorStyle) {
        appearanceStore.setColorStyle(style)
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        libraryStore.setLibraryViewMode(mode)
    }

    suspend fun restorePreferences(prefs: UserPreferences, restoreSecuritySensitive: Boolean = true) {
        playbackStore.restorePreferences(prefs)
        appearanceStore.restorePreferences(prefs)
        videoPlayerStore.restorePreferences(prefs)
        downloadsStore.restorePreferences(prefs)
        engineStore.restorePreferences(prefs)
        homeDiscoveryStore.restorePreferences(prefs)
        audioStore.restorePreferences(prefs)
        audioEffectsStore.restorePreferences(prefs)
        audioCacheStore.restorePreferences(prefs)
        libraryStore.restorePreferences(prefs)
        navigationStore.restorePreferences(prefs)
        networkOfflineStore.restorePreferences(prefs)
        notificationStore.restorePreferences(prefs)
        screensaverStore.restorePreferences(prefs)
        // SecurityStore is the only store with security-sensitive keys. The
        // remote-control switch restores unconditionally; the lock config only
        // when the caller explicitly opts in via restoreSecuritySensitive.
        securityStore.restorePreferences(prefs)
        if (restoreSecuritySensitive) {
            securityStore.restoreSecuritySensitive(prefs)
        }
        subtitleLanguageStore.restorePreferences(prefs)
        syncPlayCastStore.restorePreferences(prefs)
        experimentalStore.restorePreferences(prefs)

        engineStore.restorePerItemMaps(
            mediaStreamSelections = prefs.mediaStreamSelections,
            videoEffectsByItem = prefs.videoEffectsByItem,
        )

        val json = ENCODE_DEFAULTS_JSON
        dataStore.edit { settings ->
            settings[Keys.ONBOARDING_COMPLETED] = prefs.onboardingCompleted
            prefs.watchLaterPlaylistId?.let { settings[Keys.WATCH_LATER_PLAYLIST_ID] = it }
            settings[Keys.FAVORITE_CHANNELS] = json.encodeToString(prefs.favoriteChannels)
        }
    }

    // ----------------------------------------------------------------------
    // Backup v2 — per-slice export / import (no aggregate round-trip)
    // ----------------------------------------------------------------------

    /**
     * Snapshot of the live store state for export, ready to be wrapped in a
     * [SettingsBackup]. Holding the decoded slices (rather than pre-encoded
     * JSON) lets the caller stamp `schemaVersion` / `exportedAt` and pick the
     * encoder (`PreferencesJson.export`, encodeDefaults) in one place.
     */
    data class SettingsBackupSnapshot(
        val slices: Map<String, kotlinx.serialization.json.JsonElement>,
        val extras: com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState,
    )

    /**
     * Builds the v2 backup payload: one [kotlinx.serialization.json.JsonElement]
     * per domain slice (keyed by [BackupSliceKey]) plus the [ ]AppRuntimeState]
     * extras. Reads the `.first()` of every store slice flow, so a concurrent
     * write may produce a torn snapshot — acceptable for a user-initiated export
     * (preferences change slowly). No `buildUserPreferences` round-trip.
     */
    suspend fun snapshotForBackup(): SettingsBackupSnapshot {
        val slices = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        slices[BackupSliceKey.PLAYBACK] = encodeSliceElement(playbackStore.playback.first(), PlaybackSlice.serializer())
        slices[BackupSliceKey.APPEARANCE] = encodeSliceElement(appearanceStore.appearance.first(), AppearanceSlice.serializer())
        slices[BackupSliceKey.VIDEO_PLAYER] = encodeSliceElement(videoPlayerStore.videoPlayer.first(), VideoPlayerSlice.serializer())
        slices[BackupSliceKey.DOWNLOADS] = encodeSliceElement(downloadsStore.downloads.first(), DownloadsSlice.serializer())
        slices[BackupSliceKey.PLAYER_ENGINE] = encodeSliceElement(engineStore.playerEngine.first(), PlayerEngineSlice.serializer())
        slices[BackupSliceKey.HOME_DISCOVERY] = encodeSliceElement(homeDiscoveryStore.homeDiscovery.first(), HomeDiscoverySlice.serializer())
        slices[BackupSliceKey.AUDIO] = encodeSliceElement(audioStore.audio.first(), AudioSlice.serializer())
        slices[BackupSliceKey.AUDIO_EFFECTS] = encodeSliceElement(audioEffectsStore.audioEffects.first(), AudioEffectsSlice.serializer())
        slices[BackupSliceKey.AUDIO_CACHE] = encodeSliceElement(audioCacheStore.audioCache.first(), AudioCacheSlice.serializer())
        slices[BackupSliceKey.LIBRARY] = encodeSliceElement(libraryStore.library.first(), LibrarySlice.serializer())
        slices[BackupSliceKey.NAVIGATION] = encodeSliceElement(navigationStore.navigation.first(), NavigationSlice.serializer())
        slices[BackupSliceKey.NETWORK_OFFLINE] = encodeSliceElement(networkOfflineStore.networkOffline.first(), NetworkOfflineSlice.serializer())
        slices[BackupSliceKey.NOTIFICATION] = encodeSliceElement(notificationStore.notification.first(), NotificationSlice.serializer())
        slices[BackupSliceKey.SCREENSAVER] = encodeSliceElement(screensaverStore.screensaver.first(), ScreensaverSlice.serializer())
        slices[BackupSliceKey.SECURITY] = encodeSliceElement(securityStore.security.first(), SecuritySlice.serializer())
        slices[BackupSliceKey.SUBTITLE] = encodeSliceElement(subtitleLanguageStore.subtitle.first(), SubtitleSlice.serializer())
        slices[BackupSliceKey.SYNC_PLAY_CAST] = encodeSliceElement(syncPlayCastStore.syncPlayCast.first(), SyncPlayCastSlice.serializer())
        slices[BackupSliceKey.EXPERIMENTAL] = encodeSliceElement(experimentalStore.experimental.first(), ExperimentalSlice.serializer())
        return SettingsBackupSnapshot(slices, appRuntimeStateStore.state.first())
    }

    /**
     * Restores a v2 backup: decodes each slice element and fans it to the
     * owning store's `restore(slice)`, then writes the [ ]extras] to
     * `AppRuntimeStateStore`. Missing slice keys are skipped (an older v2
     * export that predates a slice is still importable), and unknown keys are
     * ignored — forward-compat is handled here, not by the slice decoders.
     *
     * `SecurityStore` keeps its split: the non-sensitive remote-control switch
     * restores unconditionally; the lock config only when the caller explicitly
     * opts in via [restoreSecuritySensitive] (an imported backup never silently
     * replaces the device's lock config).
     */
    suspend fun restoreV2(
        backup: SettingsBackup,
        restoreSecuritySensitive: Boolean = true,
    ) {
        val json = PreferencesJson.import
        val slices = backup.slices
        decodeOrNull(slices, BackupSliceKey.PLAYBACK, PlaybackSlice.serializer(), json)?.let { playbackStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.APPEARANCE, AppearanceSlice.serializer(), json)?.let { appearanceStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.VIDEO_PLAYER, VideoPlayerSlice.serializer(), json)?.let { videoPlayerStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.DOWNLOADS, DownloadsSlice.serializer(), json)?.let { downloadsStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.PLAYER_ENGINE, PlayerEngineSlice.serializer(), json)?.let { engineStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.HOME_DISCOVERY, HomeDiscoverySlice.serializer(), json)?.let { homeDiscoveryStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.AUDIO, AudioSlice.serializer(), json)?.let { audioStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.AUDIO_EFFECTS, AudioEffectsSlice.serializer(), json)?.let { audioEffectsStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.AUDIO_CACHE, AudioCacheSlice.serializer(), json)?.let { audioCacheStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.LIBRARY, LibrarySlice.serializer(), json)?.let { libraryStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.NAVIGATION, NavigationSlice.serializer(), json)?.let { navigationStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.NETWORK_OFFLINE, NetworkOfflineSlice.serializer(), json)?.let { networkOfflineStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.NOTIFICATION, NotificationSlice.serializer(), json)?.let { notificationStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.SCREENSAVER, ScreensaverSlice.serializer(), json)?.let { screensaverStore.restore(it) }
        // Security split: remote-control switch unconditional, lock config gated.
        decodeOrNull(slices, BackupSliceKey.SECURITY, SecuritySlice.serializer(), json)?.let { slice ->
            securityStore.restore(slice)
            if (restoreSecuritySensitive) securityStore.restoreSecuritySensitive(slice)
        }
        decodeOrNull(slices, BackupSliceKey.SUBTITLE, SubtitleSlice.serializer(), json)?.let { subtitleLanguageStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.SYNC_PLAY_CAST, SyncPlayCastSlice.serializer(), json)?.let { syncPlayCastStore.restore(it) }
        decodeOrNull(slices, BackupSliceKey.EXPERIMENTAL, ExperimentalSlice.serializer(), json)?.let { experimentalStore.restore(it) }
        appRuntimeStateStore.restore(backup.extras)
    }

    private fun <T> encodeSliceElement(slice: T, serializer: kotlinx.serialization.KSerializer<T>): kotlinx.serialization.json.JsonElement =
        PreferencesJson.export.encodeToJsonElement(serializer, slice)

    private fun <T> decodeOrNull(
        slices: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        json: kotlinx.serialization.json.Json,
    ): T? {
        val element = slices[key] ?: return null
        return runCatching { json.decodeFromJsonElement(serializer, element) }.getOrNull()
    }

    val notificationPreferences: StateFlow<NotificationPreferences> =
        projections.notificationPreferences

    suspend fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        // Forwarded to NotificationStore: the 10-key atomic read-modify-write
        // (decode aggregate → apply transform → re-encode) has a single owner
        // there.
        notificationStore.updateNotificationPreferences(transform)
    }

    val recentDlnaDevices: Flow<List<DlnaDeviceRef>>
        get() = sharedPrefs.map { prefs ->
            prefs[Keys.RECENT_DLNA_DEVICES]?.let {
                try {
                    json.decodeFromString<List<DlnaDeviceRef>>(it)
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }

    suspend fun addRecentDlnaDevice(device: DlnaDeviceRef) {
        dataStore.edit { prefs ->
            val current = try {
                prefs[Keys.RECENT_DLNA_DEVICES]?.let {
                    json.decodeFromString<List<DlnaDeviceRef>>(it)
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val updated = (listOf(device) + current.filter { it.id != device.id })
                .distinctBy { it.id }
                .take(5)
            prefs[Keys.RECENT_DLNA_DEVICES] = json.encodeToString(updated)
        }
    }

    suspend fun removeRecentDlnaDevice(deviceId: String) {
        dataStore.edit { prefs ->
            val current = try {
                prefs[Keys.RECENT_DLNA_DEVICES]?.let {
                    json.decodeFromString<List<DlnaDeviceRef>>(it)
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            prefs[Keys.RECENT_DLNA_DEVICES] = json.encodeToString(current.filter { it.id != deviceId })
        }
    }

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        networkOfflineStore.setDataSaverEnabled(enabled)
    }

    suspend fun setVerboseNetworkLogging(enabled: Boolean) {
        networkOfflineStore.setVerboseNetworkLogging(enabled)
    }

    suspend fun setNetworkTimeoutPreset(preset: NetworkTimeoutPreset) {
        networkOfflineStore.setNetworkTimeoutPreset(preset)
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        appearanceStore.setReduceMotionEnabled(enabled)
    }

    suspend fun setPreferAudioDescription(enabled: Boolean) {
        subtitleLanguageStore.setPreferAudioDescription(enabled)
    }

    suspend fun setHighContrastSubtitles(enabled: Boolean) {
        subtitleLanguageStore.setHighContrastSubtitles(enabled)
    }

    suspend fun setHideSearchHistory(enabled: Boolean) {
        experimentalStore.setHideSearchHistory(enabled)
    }

    suspend fun setBlueLightFilterEnabled(enabled: Boolean) {
        appearanceStore.setBlueLightFilterEnabled(enabled)
    }

    suspend fun setBlueLightFilterStrength(strength: Float) {
        appearanceStore.setBlueLightFilterStrength(strength)
    }

    suspend fun setTvZoomModePercent(percent: Float) {
        videoPlayerStore.setTvZoomModePercent(percent)
    }

    suspend fun setMaxDownloadStorageGb(gb: Int) {
        downloadsStore.setMaxDownloadStorageGb(gb)
    }

    suspend fun setDownloadStorageLocation(location: String) {
        downloadsStore.setDownloadStorageLocation(location)
    }

    suspend fun setAndroidTvWatchNextEnabled(enabled: Boolean) {
        playbackStore.setAndroidTvWatchNextEnabled(enabled)
    }

    suspend fun setUserDataSyncEnabled(enabled: Boolean) {
        playbackStore.setUserDataSyncEnabled(enabled)
    }

    suspend fun setSynthwaveMode(enabled: Boolean) {
        // Forwarded to AppearanceStore: the synthwave/soothing/monochrome 3-way
        // mutex has a single owner there.
        appearanceStore.setSynthwaveMode(enabled)
    }

    suspend fun setSynthwaveAccent(accent: String) {
        appearanceStore.setSynthwaveAccent(accent)
    }

    suspend fun setSoothingMode(enabled: Boolean) {
        appearanceStore.setSoothingMode(enabled)
    }

    suspend fun setSoothingAccent(accent: String) {
        appearanceStore.setSoothingAccent(accent)
    }

    suspend fun setMonochromeMode(enabled: Boolean) {
        appearanceStore.setMonochromeMode(enabled)
    }

    /**
     * Resets all preferences in a specific category to their default values.
     *
     * The union of every category's key list (see [allResetCategoryKeys]) covers
     * every user-tunable preference key — enforced by [uncoveredResetKeys]
     * (asserts the diff is empty; exercised by
     * `UserPreferencesStoreResetCoverageTest`).
     * Runtime / per-item / one-time state (PIN rate-limit counters, DLNA/channel
     * recall slots, onboarding + migration flags, per-item stream/effect maps,
     * `newsletter_last_viewed_ms`) is intentionally excluded so a category reset
     * never wipes runtime data.
     *
     * @param category The [PreferenceResetCategory] to reset.
     */
    suspend fun resetCategory(category: PreferenceResetCategory) {
        val keysToReset = resetCategoryKeys(category)

        dataStore.edit { prefs ->
            keysToReset.forEach { key ->
                prefs.remove(key)
            }
        }
    }

    /**
     * Keys cleared by [resetCategory] for [category]. Extracted as a pure function
     * so it can be inspected by tooling (and asserted complete via
     * [assertAllUserKeysCovered]) without touching the DataStore.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    internal fun resetCategoryKeys(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        listOf(
            playbackStore.resetKeysFor(category),
            appearanceStore.resetKeysFor(category),
            videoPlayerStore.resetKeysFor(category),
            downloadsStore.resetKeysFor(category),
            engineStore.resetKeysFor(category),
            homeDiscoveryStore.resetKeysFor(category),
            audioStore.resetKeysFor(category),
            audioEffectsStore.resetKeysFor(category),
            audioCacheStore.resetKeysFor(category),
            libraryStore.resetKeysFor(category),
            navigationStore.resetKeysFor(category),
            networkOfflineStore.resetKeysFor(category),
            notificationStore.resetKeysFor(category),
            screensaverStore.resetKeysFor(category),
            securityStore.resetKeysFor(category),
            subtitleLanguageStore.resetKeysFor(category),
            syncPlayCastStore.resetKeysFor(category),
            experimentalStore.resetKeysFor(category),
        ).flatten()

    /**
     * Preference keys deliberately excluded from category reset because they are
     * runtime / per-item / one-time state rather than user-tunable settings:
     * per-item stream + video-effect maps, onboarding + typed-migration flags,
     * PIN rate-limit counters, recall slots (DLNA devices, live-TV channel, last
     * newsletter view) and live-TV favorite channels.
     */
    private val resetExcludedKeys: Set<Preferences.Key<*>> = setOf(
        Keys.MEDIA_STREAM_SELECTIONS,
        Keys.VIDEO_EFFECTS_SELECTIONS,
        Keys.ONBOARDING_COMPLETED,
        Keys.TYPED_MIGRATION_DONE,
        Keys.NEWSLETTER_LAST_VIEWED_MS,
        Keys.RECENT_DLNA_DEVICES,
        Keys.LIVE_TV_LAST_CHANNEL_ID,
        Keys.FAVORITE_CHANNELS,
        Keys.PIN_FAILED_ATTEMPTS,
        Keys.PIN_LOCKOUT_UNTIL_MS,
        // Per-account / one-time update state: a category reset must not drop
        // the user's Watch Later playlist binding or re-prompt an update they
        // already dismissed.
        Keys.WATCH_LATER_PLAYLIST_ID,
        Keys.DISMISSED_UPDATE_VERSION,
        Keys.DISMISSED_UPDATE_AT_MS,
    )

    /**
     * Union of every key cleared across all categories. Exposed for the coverage
     * guard; not part of the stable public API.
     */
    internal fun allResetCategoryKeys(): Set<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMapTo(mutableSetOf()) { resetCategoryKeys(it) }

    /**
     * Coverage guard: asserts that the union of every category's key list plus
     * [resetExcludedKeys] covers every key declared in [Keys]. Call from debug
     * builds / unit tests so a future key addition can't silently slip out of
     * the reset surface. Returns the uncovered keys (empty when coverage holds).
     */
    internal fun uncoveredResetKeys(): Set<Preferences.Key<*>> {
        val covered = allResetCategoryKeys() + resetExcludedKeys
        return declaredKeys().filterNot { it in covered }.toSet()
    }

    /**
     * Reflectively enumerates every `Preferences.Key<*>` declared across the
     * facade-owned [Keys] object and each domain store's `Keys` object (and
     * `PinRateLimiter.Keys`). Uses Java reflection (no kotlin-reflect
     * dependency) via [PreferenceCodec.reflectKeys] and is only invoked from
     * the debug/test coverage guard [uncoveredResetKeys], so the reflection
     * cost is never paid in production paths.
     *
     * Aggregating from the stores — not a facade copy of their keys — keeps a
     * single declaration owner per key; a store key rename cannot silently
     * drift out of the coverage guard.
     */
    internal fun declaredKeys(): List<Preferences.Key<*>> = buildList {
        addAll(PreferenceCodec.reflectKeys(Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.playback.PlaybackStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.audio.AudioStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.library.LibraryStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.navigation.NavigationStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.notification.NotificationStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.security.SecurityStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore.Keys))
        addAll(PreferenceCodec.reflectKeys(com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore.Keys))
        addAll(PreferenceCodec.reflectKeys(PinRateLimiter.Keys))
    }

    /**
     * Clears the preferences DataStore only, resetting every stored preference
     * to its default. This does **not** sign out the user (the active
     * server/user selection is also cleared because it lives in the same
     * DataStore, but `AuthRepository` session state is untouched) and does
     * **not** delete downloaded media, the cache, or the database. Callers that
     * need a true factory reset must additionally sign out and clear those.
     *
     * One-time state flags are preserved so a settings reset never forces the
     * user back through first-run onboarding or re-triggers the legacy
     * typed-key migration on the next launch (which would crash if any typed
     * value survived the clear — see [resetExcludedKeys]).
     */
    suspend fun clearAllPreferencesOnly() {
        dataStore.edit { prefs ->
            val onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED]
            val typedMigrationDone = prefs[Keys.TYPED_MIGRATION_DONE]
            prefs.clear()
            if (onboardingCompleted != null) {
                prefs[Keys.ONBOARDING_COMPLETED] = onboardingCompleted
            }
            if (typedMigrationDone == true) {
                prefs[Keys.TYPED_MIGRATION_DONE] = true
            }
        }
    }
}