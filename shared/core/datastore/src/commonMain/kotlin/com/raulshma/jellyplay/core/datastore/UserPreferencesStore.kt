package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.DownloadQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.settings.mergeWith
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

class UserPreferencesStore constructor(
    private val externalScope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
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

    /**
     * Keys the facade itself owns — runtime / per-account / one-time state that
     * has no domain store. Every other preference key has a single owner: one
     * of the 18 domain-store `Keys` objects or `PinRateLimiter.Keys`. Those are
     * not re-declared here; the JVM test-source reset-coverage guard
     * enumerates them reflectively (see `UserPreferencesStoreResetCoverageTest`).
     *
     * The aliases below point at the owning store's key for the few store-owned
     * keys the facade still reads directly (the per-item recall maps, the
     * notification last-viewed slot, the offline-mode toggles and the PIN
     * rate-limit counters). They keep a single declaration per key — a rename
     * in the owner is a compile error here, not a silent drift.
     */
    internal object Keys {
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

    // ----------------------------------------------------------------------
    // Backup v2 — per-slice export / import (no aggregate round-trip)
    // ----------------------------------------------------------------------

    /**
     * One row per backed-up domain slice: the single table every backup /
     * per-category-import / reset fan-out below iterates. A row names the
     * slice's wire key ([BackupSliceKey]), the [PreferenceResetCategory]s
     * whose fields live in that slice ([slicesForCategory] inverts this), how
     * to read the live slice, how to hand a decoded slice back to the owning
     * store, and which keys that store clears per category.
     *
     * Two optional hooks capture the only per-slice deviations:
     *  - [restoreSensitive] — `SecurityStore` only: the non-sensitive
     *    remote-control switch restores unconditionally, the lock config just
     *    when the caller opts in (`restoreSecuritySensitive`).
     *  - [merge] — the six slices co-owned by several categories (appearance,
     *    playback, audio, notification, subtitle, experimental): on the
     *    per-category import path they merge field-level via
     *    `SliceCategoryMergers` instead of being wholesale-replaced. `null`
     *    means the slice has a single owner and always restores wholesale.
     */
    private class SliceBinding<T>(
        val key: String,
        val categories: Set<PreferenceResetCategory>,
        private val read: suspend () -> T,
        private val serializer: kotlinx.serialization.KSerializer<T>,
        private val restore: suspend (T) -> Unit,
        private val restoreSensitive: (suspend (T) -> Unit)? = null,
        private val merge: ((T, T, Set<PreferenceResetCategory>) -> T)? = null,
        private val resetKeys: (PreferenceResetCategory) -> List<Preferences.Key<*>>,
    ) {
        /** Encodes the live slice state for [snapshotForBackup]. */
        suspend fun snapshotElement(): kotlinx.serialization.json.JsonElement =
            PreferencesJson.export.encodeToJsonElement(serializer, read())

        /**
         * Decodes this slice from [slices]; null when the key is absent or the
         * element fails to decode (an older v2 export that predates a slice is
         * still importable, a malformed slice is skipped — forward-compat is
         * handled here, not by the slice decoders).
         */
        fun decodeOrNull(
            slices: Map<String, kotlinx.serialization.json.JsonElement>,
            json: kotlinx.serialization.json.Json,
        ): T? {
            val element = slices[key] ?: return null
            return runCatching { json.decodeFromJsonElement(serializer, element) }.getOrNull()
        }

        /** Wholesale restore path — [restoreV2]. */
        suspend fun restoreFromBackup(
            slices: Map<String, kotlinx.serialization.json.JsonElement>,
            json: kotlinx.serialization.json.Json,
            restoreSecuritySensitive: Boolean,
        ) {
            val slice = decodeOrNull(slices, json) ?: return
            restoreDecoded(slice, restoreSecuritySensitive)
        }

        /**
         * Per-category path — [restoreV2Categories]: field-level merge for the
         * co-owned slices ([merge] != null), wholesale restore otherwise.
         */
        suspend fun restoreFromBackupForCategories(
            slices: Map<String, kotlinx.serialization.json.JsonElement>,
            json: kotlinx.serialization.json.Json,
            categories: Set<PreferenceResetCategory>,
            restoreSecuritySensitive: Boolean,
        ) {
            val incoming = decodeOrNull(slices, json) ?: return
            val merger = merge
            if (merger == null) {
                restoreDecoded(incoming, restoreSecuritySensitive)
            } else {
                val current = read()
                val merged = merger(current, incoming, categories)
                if (merged != current) restore(merged)
            }
        }

        /** Keys the owning store clears for [category] ([resetCategoryKeys]). */
        fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
            resetKeys(category)

        private suspend fun restoreDecoded(slice: T, restoreSecuritySensitive: Boolean) {
            restore(slice)
            val sensitive = restoreSensitive
            if (restoreSecuritySensitive && sensitive != null) sensitive(slice)
        }
    }

    /**
     * The 18 domain slices in [BackupSliceKey] order — snapshot keys, restore
     * fan-out and reset-key flattening all follow this order. Adding a domain
     * store means adding one row here (plus its constructor parameter); every
     * fan-out below picks it up. Extras (`AppRuntimeState`) stay orthogonal:
     * they have no category and are written by the restore entry points.
     */
    private val sliceBindings: List<SliceBinding<*>> = listOf(
        SliceBinding(
            key = BackupSliceKey.PLAYBACK,
            categories = setOf(
                PreferenceResetCategory.PLAYBACK,
                PreferenceResetCategory.SUBTITLES_LANGUAGE,
                PreferenceResetCategory.MISC_APP,
            ),
            read = { playbackStore.playback.first() },
            serializer = PlaybackSlice.serializer(),
            restore = { playbackStore.restore(it) },
            merge = { current, incoming, selected -> current.mergeWith(incoming, selected) },
            resetKeys = { playbackStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.APPEARANCE,
            categories = setOf(PreferenceResetCategory.APPEARANCE, PreferenceResetCategory.MISC_APP),
            read = { appearanceStore.appearance.first() },
            serializer = AppearanceSlice.serializer(),
            restore = { appearanceStore.restore(it) },
            merge = { current, incoming, selected -> current.mergeWith(incoming, selected) },
            resetKeys = { appearanceStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.VIDEO_PLAYER,
            categories = setOf(PreferenceResetCategory.PLAYBACK),
            read = { videoPlayerStore.videoPlayer.first() },
            serializer = VideoPlayerSlice.serializer(),
            restore = { videoPlayerStore.restore(it) },
            resetKeys = { videoPlayerStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.DOWNLOADS,
            categories = setOf(PreferenceResetCategory.DOWNLOADS_NETWORK),
            read = { downloadsStore.downloads.first() },
            serializer = DownloadsSlice.serializer(),
            restore = { downloadsStore.restore(it) },
            resetKeys = { downloadsStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.PLAYER_ENGINE,
            categories = setOf(PreferenceResetCategory.PLAYER_ENGINES),
            read = { engineStore.playerEngine.first() },
            serializer = PlayerEngineSlice.serializer(),
            restore = { engineStore.restore(it) },
            resetKeys = { engineStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.HOME_DISCOVERY,
            categories = setOf(PreferenceResetCategory.HOME_DISCOVERY),
            read = { homeDiscoveryStore.homeDiscovery.first() },
            serializer = HomeDiscoverySlice.serializer(),
            restore = { homeDiscoveryStore.restore(it) },
            resetKeys = { homeDiscoveryStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.AUDIO,
            categories = setOf(PreferenceResetCategory.PLAYBACK, PreferenceResetCategory.AUDIO),
            read = { audioStore.audio.first() },
            serializer = AudioSlice.serializer(),
            restore = { audioStore.restore(it) },
            merge = { current, incoming, selected -> current.mergeWith(incoming, selected) },
            resetKeys = { audioStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.AUDIO_EFFECTS,
            categories = setOf(PreferenceResetCategory.AUDIO),
            read = { audioEffectsStore.audioEffects.first() },
            serializer = AudioEffectsSlice.serializer(),
            restore = { audioEffectsStore.restore(it) },
            resetKeys = { audioEffectsStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.AUDIO_CACHE,
            categories = setOf(PreferenceResetCategory.AUDIO_CACHE),
            read = { audioCacheStore.audioCache.first() },
            serializer = AudioCacheSlice.serializer(),
            restore = { audioCacheStore.restore(it) },
            resetKeys = { audioCacheStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.LIBRARY,
            categories = setOf(PreferenceResetCategory.HOME_DISCOVERY),
            read = { libraryStore.library.first() },
            serializer = LibrarySlice.serializer(),
            restore = { libraryStore.restore(it) },
            resetKeys = { libraryStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.NAVIGATION,
            categories = setOf(PreferenceResetCategory.HOME_DISCOVERY),
            read = { navigationStore.navigation.first() },
            serializer = NavigationSlice.serializer(),
            restore = { navigationStore.restore(it) },
            resetKeys = { navigationStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.NETWORK_OFFLINE,
            categories = setOf(PreferenceResetCategory.DOWNLOADS_NETWORK),
            read = { networkOfflineStore.networkOffline.first() },
            serializer = NetworkOfflineSlice.serializer(),
            restore = { networkOfflineStore.restore(it) },
            resetKeys = { networkOfflineStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.NOTIFICATION,
            categories = setOf(PreferenceResetCategory.NOTIFICATIONS, PreferenceResetCategory.NEWSLETTER),
            read = { notificationStore.notification.first() },
            serializer = NotificationSlice.serializer(),
            restore = { notificationStore.restore(it) },
            merge = { current, incoming, selected -> current.mergeWith(incoming, selected) },
            resetKeys = { notificationStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.SCREENSAVER,
            categories = setOf(PreferenceResetCategory.SCREENSAVER),
            read = { screensaverStore.screensaver.first() },
            serializer = ScreensaverSlice.serializer(),
            restore = { screensaverStore.restore(it) },
            resetKeys = { screensaverStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.SECURITY,
            categories = setOf(PreferenceResetCategory.SECURITY),
            read = { securityStore.security.first() },
            serializer = SecuritySlice.serializer(),
            restore = { securityStore.restore(it) },
            restoreSensitive = { securityStore.restoreSecuritySensitive(it) },
            resetKeys = { securityStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.SUBTITLE,
            categories = setOf(PreferenceResetCategory.SUBTITLES_LANGUAGE, PreferenceResetCategory.MISC_APP),
            read = { subtitleLanguageStore.subtitle.first() },
            serializer = SubtitleSlice.serializer(),
            restore = { subtitleLanguageStore.restore(it) },
            merge = { current, incoming, selected -> current.mergeWith(incoming, selected) },
            resetKeys = { subtitleLanguageStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.SYNC_PLAY_CAST,
            categories = setOf(PreferenceResetCategory.SYNCPLAY_CASTING),
            read = { syncPlayCastStore.syncPlayCast.first() },
            serializer = SyncPlayCastSlice.serializer(),
            restore = { syncPlayCastStore.restore(it) },
            resetKeys = { syncPlayCastStore.resetKeysFor(it) },
        ),
        SliceBinding(
            key = BackupSliceKey.EXPERIMENTAL,
            categories = setOf(PreferenceResetCategory.EXPERIMENTAL, PreferenceResetCategory.MISC_APP),
            read = { experimentalStore.experimental.first() },
            serializer = ExperimentalSlice.serializer(),
            restore = { experimentalStore.restore(it) },
            merge = { current, incoming, selected -> current.mergeWith(incoming, selected) },
            resetKeys = { experimentalStore.resetKeysFor(it) },
        ),
    )

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
            for (binding in sliceBindings) jobs[binding.key] = async { binding.snapshotElement() }
            for (binding in sliceBindings) slices[binding.key] = jobs.getValue(binding.key).await()
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
        for (binding in sliceBindings) {
            binding.restoreFromBackup(slices, json, restoreSecuritySensitive)
        }
        appRuntimeStateStore.restore(backup.extras, clearNullIds = true)
    }

    /**
     * Per-category import support. Derives, by inverting the per-slice
     * `categories` in the [sliceBindings] table, the set of [BackupSliceKey]s
     * that carry [category]'s fields. Used only to decide *which* slices to
     * touch; the actual per-category write for co-owned slices is field-level
     * via `SliceCategoryMergers` (so `AUDIO` + `PLAYBACK` sharing
     * `AudioSlice.audioDelayMs` no longer bleeds). Keeping the routing here
     * (not in `feature:settings`'s presentation registry) avoids a
     * `core:datastore → feature:settings` dependency cycle — the registry
     * already lives in `feature:settings`, but the store must know the slice
     * keys without depending on UI.
     *
     * Extras (`AppRuntimeState`) are handled orthogonally — they have no
     * `PreferenceResetCategory` and are imported as part of `restoreV2` all or
     * via the synthetic `APP_RUNTIME` card.
     */
    fun slicesForCategory(category: PreferenceResetCategory): Set<String> =
        sliceBindings.filter { category in it.categories }.mapTo(linkedSetOf()) { it.key }

    /**
     * Restores only the slices that belong to [categories] with **field-level**
     * merging for co-owned slices. The previous wholesale per-slice replacement
     * bled across categories that share a slice (e.g. `AUDIO` + `PLAYBACK` both
     * touch `AudioSlice.audioDelayMs`). Each shared slice now merges via
     * `SliceCategoryMergers` so importing `PLAYBACK` does not silently overwrite
     * `AUDIO` fields and vice-versa. Exclusive slices are still wholesale-
     * restored when their single owning category is selected.
     *
     * Extras are written only when [includeExtras] is true (Import All).
     */
    suspend fun restoreV2Categories(
        backup: SettingsBackup,
        categories: Set<PreferenceResetCategory>,
        restoreSecuritySensitive: Boolean = true,
        includeExtras: Boolean = false,
    ) {
        if (categories.isEmpty() && !includeExtras) return
        val json = PreferencesJson.import
        val slices = backup.slices
        for (binding in sliceBindings) {
            if (binding.categories.none { it in categories }) continue
            binding.restoreFromBackupForCategories(slices, json, categories, restoreSecuritySensitive)
        }
        if (includeExtras) appRuntimeStateStore.restore(backup.extras, clearNullIds = true)
    }

    /**
     * Synthetic helper for the import-preview "App State" card (extras).
     * Not a `PreferenceResetCategory` — factory reset never shows it.
     */
    suspend fun restoreExtras(backup: SettingsBackup) {
        appRuntimeStateStore.restore(backup.extras, clearNullIds = true)
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
     * every user-tunable preference key — enforced by the reset-coverage guard
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
     * so it can be inspected by tooling (and asserted complete by the JVM
     * test-source reset-coverage guard, `uncoveredResetKeys`) without touching
     * the DataStore.
     */
    internal fun resetCategoryKeys(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        sliceBindings.flatMap { it.resetKeysFor(category) }

    /**
     * Preference keys deliberately excluded from category reset because they are
     * runtime / per-item / one-time state rather than user-tunable settings:
     * per-item stream + video-effect maps, onboarding + typed-migration flags,
     * PIN rate-limit counters, recall slots (DLNA devices, live-TV channel, last
     * newsletter view) and live-TV favorite channels.
     */
    internal val resetExcludedKeys: Set<Preferences.Key<*>> = setOf(
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
