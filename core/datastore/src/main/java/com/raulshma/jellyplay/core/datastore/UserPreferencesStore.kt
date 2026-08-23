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
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    // — those consumers now inject those stores directly.)
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
                "compact_episode_list",
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
     * (preferences change slowly).
     */
    suspend fun snapshotForBackup(): SettingsBackupSnapshot {
        // All slice reads are independent DataStores — fetch them concurrently
        // (backup-only path; was ~19 sequential .first() round-trips).
        val slices = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        coroutineScope {
            val jobs = linkedMapOf<String, kotlinx.coroutines.Deferred<kotlinx.serialization.json.JsonElement>>()
            jobs[BackupSliceKey.PLAYBACK] = async { encodeSliceElement(playbackStore.playback.first(), PlaybackSlice.serializer()) }
            jobs[BackupSliceKey.APPEARANCE] = async { encodeSliceElement(appearanceStore.appearance.first(), AppearanceSlice.serializer()) }
            jobs[BackupSliceKey.VIDEO_PLAYER] = async { encodeSliceElement(videoPlayerStore.videoPlayer.first(), VideoPlayerSlice.serializer()) }
            jobs[BackupSliceKey.DOWNLOADS] = async { encodeSliceElement(downloadsStore.downloads.first(), DownloadsSlice.serializer()) }
            jobs[BackupSliceKey.PLAYER_ENGINE] = async { encodeSliceElement(engineStore.playerEngine.first(), PlayerEngineSlice.serializer()) }
            jobs[BackupSliceKey.HOME_DISCOVERY] = async { encodeSliceElement(homeDiscoveryStore.homeDiscovery.first(), HomeDiscoverySlice.serializer()) }
            jobs[BackupSliceKey.AUDIO] = async { encodeSliceElement(audioStore.audio.first(), AudioSlice.serializer()) }
            jobs[BackupSliceKey.AUDIO_EFFECTS] = async { encodeSliceElement(audioEffectsStore.audioEffects.first(), AudioEffectsSlice.serializer()) }
            jobs[BackupSliceKey.AUDIO_CACHE] = async { encodeSliceElement(audioCacheStore.audioCache.first(), AudioCacheSlice.serializer()) }
            jobs[BackupSliceKey.LIBRARY] = async { encodeSliceElement(libraryStore.library.first(), LibrarySlice.serializer()) }
            jobs[BackupSliceKey.NAVIGATION] = async { encodeSliceElement(navigationStore.navigation.first(), NavigationSlice.serializer()) }
            jobs[BackupSliceKey.NETWORK_OFFLINE] = async { encodeSliceElement(networkOfflineStore.networkOffline.first(), NetworkOfflineSlice.serializer()) }
            jobs[BackupSliceKey.NOTIFICATION] = async { encodeSliceElement(notificationStore.notification.first(), NotificationSlice.serializer()) }
            jobs[BackupSliceKey.SCREENSAVER] = async { encodeSliceElement(screensaverStore.screensaver.first(), ScreensaverSlice.serializer()) }
            jobs[BackupSliceKey.SECURITY] = async { encodeSliceElement(securityStore.security.first(), SecuritySlice.serializer()) }
            jobs[BackupSliceKey.SUBTITLE] = async { encodeSliceElement(subtitleLanguageStore.subtitle.first(), SubtitleSlice.serializer()) }
            jobs[BackupSliceKey.SYNC_PLAY_CAST] = async { encodeSliceElement(syncPlayCastStore.syncPlayCast.first(), SyncPlayCastSlice.serializer()) }
            jobs[BackupSliceKey.EXPERIMENTAL] = async { encodeSliceElement(experimentalStore.experimental.first(), ExperimentalSlice.serializer()) }
            for ((key, deferred) in jobs) slices[key] = deferred.await()
        }
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

    suspend fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        // Forwarded to NotificationStore: the 10-key atomic read-modify-write
        // (decode aggregate → apply transform → re-encode) has a single owner
        // there.
        notificationStore.updateNotificationPreferences(transform)
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
            // The home-discovery keys are per-user namespaced (`u_<userId>::`) —
            // dynamic, so they cannot sit in the static key list. The owning
            // store strips every user's entries (plus its migration marker)
            // itself; it no-ops for every other category.
            homeDiscoveryStore.removeDynamicResetKeys(category, prefs)
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
