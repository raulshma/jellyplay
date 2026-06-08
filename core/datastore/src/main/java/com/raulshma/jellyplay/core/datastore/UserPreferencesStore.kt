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
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.EffectStrength
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
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.WidgetConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sharedPrefs: StateFlow<Preferences> = context.dataStore.data
        .stateIn(scope, SharingStarted.Eagerly, emptyPreferences())

    private object Keys {
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val MEDIA_STREAM_SELECTIONS = stringPreferencesKey("media_stream_selections")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CONTRAST_LEVEL = stringPreferencesKey("contrast_level")
        val SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val DIALOGUE_BOOST_STRENGTH = stringPreferencesKey("dialogue_boost_strength")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val NIGHT_MODE_STRENGTH = stringPreferencesKey("night_mode_strength")
        val HOME_MODE = stringPreferencesKey("home_mode")
        val VIDEO_DEFAULT_ORIENTATION = stringPreferencesKey("video_default_orientation")
        val VIDEO_DEFAULT_ASPECT_RATIO = stringPreferencesKey("video_default_aspect_ratio")
        val VIDEO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("video_preload_buffer_size")
        val AUDIO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("audio_preload_buffer_size")
        val AUDIO_NORMALIZATION_MODE = stringPreferencesKey("audio_normalization_mode")
        val CHANNEL_MIX_MODE = stringPreferencesKey("channel_mix_mode")
        val DREAM_IMAGE_CATEGORIES = stringPreferencesKey("dream_image_categories")
        val DREAM_TRANSITION_STYLE = stringPreferencesKey("dream_transition_style")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val BASS_BOOST_STRENGTH = stringPreferencesKey("bass_boost_strength")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val HOME_ENABLED_SECTION_TYPES = stringPreferencesKey("home_enabled_section_types")
        val HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        val HOME_HIDDEN_LIBRARY_SECTION_IDS = stringPreferencesKey("home_hidden_library_section_ids")
        val MPV_CONFIG = stringPreferencesKey("mpv_config")
        val LIBVLC_CONFIG = stringPreferencesKey("libvlc_config")
        val EXO_CONFIG = stringPreferencesKey("exo_config")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CONTINUE_WATCHING = stringPreferencesKey("continue_watching")
        val SEGMENT_BEHAVIORS = stringPreferencesKey("segment_behaviors")
        val SKIP_INTRO_ENABLED = stringPreferencesKey("skip_intro_enabled")
        val SKIP_OUTRO_ENABLED = stringPreferencesKey("skip_outro_enabled")
        val AUTO_SKIP_INTRO = stringPreferencesKey("auto_skip_intro")
        val AUTO_SKIP_OUTRO = stringPreferencesKey("auto_skip_outro")
        val ACCENT_COLOR_SWATCH = stringPreferencesKey("accent_color_swatch")
        val COLOR_STYLE = stringPreferencesKey("color_style")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val EQUALIZER_SETTINGS = stringPreferencesKey("equalizer_settings")

        val DYNAMIC_THEMING = booleanPreferencesKey("dynamic_theming")
        val OLED_MODE = booleanPreferencesKey("oled_mode")
        val AUTO_DELETE_CACHE = booleanPreferencesKey("auto_delete_cache")
        val PIN_LOCK_ENABLED = booleanPreferencesKey("pin_lock_enabled")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val DIALOGUE_BOOST_ENABLED = booleanPreferencesKey("dialogue_boost_enabled")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val AUDIO_PASSTHROUGH = booleanPreferencesKey("audio_passthrough")
        val FRAME_RATE_MATCHING = booleanPreferencesKey("frame_rate_matching")
        val NIGHT_MODE_ENABLED = booleanPreferencesKey("night_mode_enabled")
        val VIDEO_GESTURES_ENABLED = booleanPreferencesKey("video_gestures_enabled")
        val VIDEO_AUTOPLAY_NEXT = booleanPreferencesKey("video_autoplay_next")
        val TRAILER_AUTOPLAY = booleanPreferencesKey("trailer_autoplay")
        val VIDEO_REMEMBER_BRIGHTNESS = booleanPreferencesKey("video_remember_brightness")
        val AUDIO_AUTOPLAY_NEXT = booleanPreferencesKey("audio_autoplay_next")
        val TRICKPLAY_ENABLED = booleanPreferencesKey("trickplay_enabled")
        val TRICKPLAY_ON_SEEK_GESTURE = booleanPreferencesKey("trickplay_on_seek_gesture")
        val VIDEO_EPISODE_BROWSER_ENABLED = booleanPreferencesKey("video_episode_browser_enabled")
        val VIDEO_SHOW_PLAYBACK_METADATA = booleanPreferencesKey("video_show_playback_metadata")
        val AUDIO_NORMALIZATION_ENABLED = booleanPreferencesKey("audio_normalization_enabled")
        val CHANNEL_MIX_ENABLED = booleanPreferencesKey("channel_mix_enabled")
        val AUDIO_GAPLESS_ENABLED = booleanPreferencesKey("audio_gapless_enabled")
        val SLEEP_TIMER_END_OF_EPISODE = booleanPreferencesKey("sleep_timer_end_of_episode")
        val DREAM_KEN_BURNS_ENABLED = booleanPreferencesKey("dream_ken_burns_enabled")
        val DREAM_SHOW_TITLE = booleanPreferencesKey("dream_show_title")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val AUTO_EQ_BY_GENRE = booleanPreferencesKey("auto_eq_by_genre")
        val HOME_HERO_ENABLED = booleanPreferencesKey("home_hero_enabled")
        val NAV_BAR_SHOW_LABELS = booleanPreferencesKey("nav_bar_show_labels")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        val NEWSLETTER_ENABLED = booleanPreferencesKey("newsletter_enabled")

        val MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
        val AUDIO_NIGHT_MODE_GAIN = intPreferencesKey("audio_night_mode_gain")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_CONNECTIONS = intPreferencesKey("download_connections")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val NEWSLETTER_DAY_OF_WEEK = intPreferencesKey("newsletter_day_of_week")

        val VIDEO_DEFAULT_SPEED = floatPreferencesKey("video_default_speed")
        val VIDEO_BRIGHTNESS_LEVEL = floatPreferencesKey("video_brightness_level")
        val AUDIO_DEFAULT_SPEED = floatPreferencesKey("audio_default_speed")
        val AUDIO_NIGHT_MODE_VOLUME = floatPreferencesKey("audio_night_mode_volume")
        val REPLAYGAIN_PRE_AMP_DB = floatPreferencesKey("replaygain_pre_amp_db")
        val LR_BALANCE = floatPreferencesKey("lr_balance")
        val PITCH_SEMITONES = floatPreferencesKey("pitch_semitones")

        val AUDIO_DELAY_MS = longPreferencesKey("audio_delay_ms")
        val AUTO_LOCK_TIMER_MS = longPreferencesKey("auto_lock_timer_ms")
        val VIDEO_SEEK_DURATION_MS = longPreferencesKey("video_seek_duration_ms")
        val VIDEO_CONTROLS_TIMEOUT_MS = longPreferencesKey("video_controls_timeout_ms")
        val VIDEO_SWIPE_SEEK_MAX_MS = longPreferencesKey("video_swipe_seek_max_ms")
        val AUDIO_SKIP_PREVIOUS_THRESHOLD_MS = longPreferencesKey("audio_skip_previous_threshold_ms")
        val AUDIO_CROSSFADE_DURATION_MS = longPreferencesKey("audio_crossfade_duration_ms")
        val SLEEP_TIMER_DURATION_MS = longPreferencesKey("sleep_timer_duration_ms")
        val DREAM_SLIDESHOW_INTERVAL_MS = longPreferencesKey("dream_slideshow_interval_ms")
        val NEWSLETTER_LAST_VIEWED_MS = longPreferencesKey("newsletter_last_viewed_ms")

        val TYPED_MIGRATION_DONE = booleanPreferencesKey("_typed_migration_done")

        // ── Home-screen recommendations widgets ──
        val WIDGET_CONFIG = stringPreferencesKey("widget_config")
        val LIBRARY_WIDGET_ITEMS = stringPreferencesKey("library_widget_items")
        val LIBRARY_WIDGET_VERSION = longPreferencesKey("library_widget_version")
        val LIBRARY_WIDGET_UPDATED_AT_MS = longPreferencesKey("library_widget_updated_at_ms")
        val SEERR_WIDGET_ITEMS = stringPreferencesKey("seerr_widget_items")
        val SEERR_WIDGET_VERSION = longPreferencesKey("seerr_widget_version")
        val SEERR_WIDGET_UPDATED_AT_MS = longPreferencesKey("seerr_widget_updated_at_ms")
        val WIDGET_LAST_REFRESH_MS = longPreferencesKey("widget_last_refresh_ms")

        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATIONS_CHECK_FREQUENCY = stringPreferencesKey("notifications_check_frequency")
        val NOTIFICATIONS_QUIET_HOURS_ENABLED = booleanPreferencesKey("notifications_quiet_hours_enabled")
        val NOTIFICATIONS_QUIET_HOURS_START = intPreferencesKey("notifications_quiet_hours_start")
        val NOTIFICATIONS_QUIET_HOURS_END = intPreferencesKey("notifications_quiet_hours_end")
        val NOTIFICATIONS_SOUND_ENABLED = booleanPreferencesKey("notifications_sound_enabled")
        val NOTIFICATIONS_VIBRATE_ENABLED = booleanPreferencesKey("notifications_vibrate_enabled")
        val NOTIFICATIONS_LIGHTS_ENABLED = booleanPreferencesKey("notifications_lights_enabled")
        val NOTIFICATIONS_MAX_PER_CHECK = intPreferencesKey("notifications_max_per_check")
        val NOTIFICATIONS_LIBRARY_CONFIGS = stringPreferencesKey("notifications_library_configs")

        val RECENT_DLNA_DEVICES = stringPreferencesKey("recent_dlna_devices")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Digest by lazy { MessageDigest.getInstance("SHA-256") }

    init {
        scope.launch { migrateToTypedKeys() }
    }

    private suspend fun migrateToTypedKeys() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.TYPED_MIGRATION_DONE] == true) return@edit

            migrateBooleans(prefs,
                "dynamic_theming", "oled_mode", "auto_delete_cache",
                "pin_lock_enabled", "biometric_lock_enabled", "dialogue_boost_enabled",
                "equalizer_enabled", "audio_passthrough", "frame_rate_matching",
                "night_mode_enabled", "video_gestures_enabled", "video_autoplay_next", "trailer_autoplay",
                "video_remember_brightness", "audio_autoplay_next", "trickplay_enabled",
                "trickplay_on_seek_gesture", "video_episode_browser_enabled",
                "video_show_playback_metadata", "audio_normalization_enabled",
                "channel_mix_enabled", "audio_gapless_enabled", "sleep_timer_end_of_episode",
                "dream_ken_burns_enabled", "dream_show_title", "bass_boost_enabled",
                "virtualizer_enabled", "auto_eq_by_genre", "home_hero_enabled",
                "nav_bar_show_labels", "onboarding_completed", "performance_mode",
                "newsletter_enabled", "wifi_only_downloads",
            )

            migrateInts(prefs,
                "max_cache_size_mb", "audio_night_mode_gain", "download_connections",
                "virtualizer_strength", "newsletter_day_of_week",
            )

            migrateFloats(prefs,
                "video_default_speed", "video_brightness_level", "audio_default_speed",
                "audio_night_mode_volume", "replaygain_pre_amp_db", "lr_balance",
                "pitch_semitones",
            )

            migrateLongs(prefs,
                "audio_delay_ms", "auto_lock_timer_ms", "video_seek_duration_ms",
                "video_controls_timeout_ms", "video_swipe_seek_max_ms",
                "audio_skip_previous_threshold_ms", "audio_crossfade_duration_ms",
                "sleep_timer_duration_ms", "dream_slideshow_interval_ms",
                "newsletter_last_viewed_ms",
            )

            prefs[Keys.TYPED_MIGRATION_DONE] = true
        }
    }

    private fun migrateBooleans(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs[stringPreferencesKey(name)] ?: continue
            prefs[booleanPreferencesKey(name)] = legacy.toBoolean()
        }
    }

    private fun migrateInts(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs[stringPreferencesKey(name)] ?: continue
            legacy.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
        }
    }

    private fun migrateFloats(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs[stringPreferencesKey(name)] ?: continue
            legacy.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it }
        }
    }

    private fun migrateLongs(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs[stringPreferencesKey(name)] ?: continue
            legacy.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
        }
    }

    private fun readBool(prefs: Preferences, key: Preferences.Key<Boolean>, name: String, default: Boolean): Boolean {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        return typed ?: prefs[stringPreferencesKey(name)]?.toBoolean() ?: default
    }

    private fun readInt(prefs: Preferences, key: Preferences.Key<Int>, name: String, default: Int): Int {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        return typed ?: prefs[stringPreferencesKey(name)]?.toIntOrNull() ?: default
    }

    private fun readFloat(prefs: Preferences, key: Preferences.Key<Float>, name: String, default: Float): Float {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        return typed ?: prefs[stringPreferencesKey(name)]?.toFloatOrNull() ?: default
    }

    private fun readLong(prefs: Preferences, key: Preferences.Key<Long>, name: String, default: Long): Long {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        return typed ?: prefs[stringPreferencesKey(name)]?.toLongOrNull() ?: default
    }

    private fun readMediaStreamSelections(prefs: Preferences): Map<String, MediaStreamSelection> {
        val raw = prefs[Keys.MEDIA_STREAM_SELECTIONS] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, MediaStreamSelection>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readSegmentBehaviors(prefs: Preferences): Map<MediaSegmentType, SegmentBehavior> {
        val raw = prefs[Keys.SEGMENT_BEHAVIORS]
        if (raw != null) {
            return try {
                val stored = json.decodeFromString<Map<String, String>>(raw)
                stored.mapNotNull { (typeStr, behaviorStr) ->
                    try {
                        MediaSegmentType.valueOf(typeStr) to SegmentBehavior.valueOf(behaviorStr)
                    } catch (_: Exception) { null }
                }.toMap()
            } catch (_: Exception) { emptyMap() }
        }

        val hasLegacyKeys = prefs.contains(Keys.SKIP_INTRO_ENABLED) ||
            prefs.contains(Keys.SKIP_OUTRO_ENABLED) ||
            prefs.contains(Keys.AUTO_SKIP_INTRO) ||
            prefs.contains(Keys.AUTO_SKIP_OUTRO)
        if (!hasLegacyKeys) return SegmentBehavior.DEFAULT_BEHAVIORS

        val migrated = mutableMapOf<MediaSegmentType, SegmentBehavior>()
        val skipIntro = prefs[Keys.SKIP_INTRO_ENABLED]?.toBoolean() ?: true
        val skipOutro = prefs[Keys.SKIP_OUTRO_ENABLED]?.toBoolean() ?: true
        val autoIntro = prefs[Keys.AUTO_SKIP_INTRO]?.toBoolean() ?: false
        val autoOutro = prefs[Keys.AUTO_SKIP_OUTRO]?.toBoolean() ?: false
        migrated[MediaSegmentType.INTRO] = when {
            autoIntro -> SegmentBehavior.AUTO_SKIP
            skipIntro -> SegmentBehavior.SHOW_BUTTON
            else -> SegmentBehavior.IGNORE
        }
        migrated[MediaSegmentType.OUTRO] = when {
            autoOutro -> SegmentBehavior.AUTO_SKIP
            skipOutro -> SegmentBehavior.SHOW_BUTTON
            else -> SegmentBehavior.IGNORE
        }
        return SegmentBehavior.DEFAULT_BEHAVIORS + migrated
    }

    private suspend fun writeMediaStreamSelections(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        context.dataStore.edit { prefs ->
            val current = readMediaStreamSelections(prefs).toMutableMap()
            current[itemId] = MediaStreamSelection(
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
            if (current.size > 100) {
                val excess = current.size - 100
                current.keys.take(excess).forEach { current.remove(it) }
            }
            prefs[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(current)
        }
    }

    val preferences: StateFlow<UserPreferences> = sharedPrefs.map { prefs ->
        val subtitleStyle = try {
            prefs[Keys.SUBTITLE_STYLE]?.let { json.decodeFromString<SubtitleStyle>(it) }
        } catch (_: Exception) { null }

        val streamingQuality = try {
            StreamingQuality.valueOf(prefs[Keys.STREAMING_QUALITY] ?: StreamingQuality.AUTO.name)
        } catch (_: Exception) { StreamingQuality.AUTO }

        val equalizerSettings = try {
            prefs[Keys.EQUALIZER_SETTINGS]?.let { json.decodeFromString<EqualizerSettings>(it) }
        } catch (_: Exception) { null }

        UserPreferences(
            preferredPlayer = try {
                PlayerType.fromStoredName(prefs[Keys.PREFERRED_PLAYER] ?: PlayerType.EXO_PLAYER.name)
            } catch (_: Exception) { PlayerType.EXO_PLAYER },
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANG],
            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG],
            mediaStreamSelections = readMediaStreamSelections(prefs),
            dynamicTheming = readBool(prefs, Keys.DYNAMIC_THEMING, "dynamic_theming", true),
            themeMode = try {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            } catch (_: Exception) { ThemeMode.SYSTEM },
            contrastLevel = try {
                ContrastLevel.valueOf(prefs[Keys.CONTRAST_LEVEL] ?: ContrastLevel.DEFAULT.name)
            } catch (_: Exception) { ContrastLevel.DEFAULT },
            oledMode = readBool(prefs, Keys.OLED_MODE, "oled_mode", false),
            subtitleStyle = subtitleStyle ?: SubtitleStyle(),
            streamingQuality = streamingQuality,
            maxCacheSizeMb = readInt(prefs, Keys.MAX_CACHE_SIZE_MB, "max_cache_size_mb", 0),
            autoDeleteCache = readBool(prefs, Keys.AUTO_DELETE_CACHE, "auto_delete_cache", true),
            pinLockEnabled = readBool(prefs, Keys.PIN_LOCK_ENABLED, "pin_lock_enabled", false),
            pinHash = prefs[Keys.PIN_HASH],
            biometricLockEnabled = readBool(prefs, Keys.BIOMETRIC_LOCK_ENABLED, "biometric_lock_enabled", false),
            autoLockTimerMs = readLong(prefs, Keys.AUTO_LOCK_TIMER_MS, "auto_lock_timer_ms", 30_000L),
            dialogueBoostEnabled = readBool(prefs, Keys.DIALOGUE_BOOST_ENABLED, "dialogue_boost_enabled", false),
            dialogueBoostStrength = try {
                EffectStrength.valueOf(prefs[Keys.DIALOGUE_BOOST_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) { EffectStrength.MODERATE },
            equalizerEnabled = readBool(prefs, Keys.EQUALIZER_ENABLED, "equalizer_enabled", false),
            equalizerSettings = equalizerSettings ?: EqualizerSettings(),
            audioDelayMs = readLong(prefs, Keys.AUDIO_DELAY_MS, "audio_delay_ms", 0L),
            decoderMode = try {
                DecoderMode.valueOf(prefs[Keys.DECODER_MODE] ?: DecoderMode.HW_PREFERRED.name)
            } catch (_: Exception) { DecoderMode.HW_PREFERRED },
            audioPassthrough = readBool(prefs, Keys.AUDIO_PASSTHROUGH, "audio_passthrough", false),
            frameRateMatching = readBool(prefs, Keys.FRAME_RATE_MATCHING, "frame_rate_matching", false),
            nightModeEnabled = readBool(prefs, Keys.NIGHT_MODE_ENABLED, "night_mode_enabled", false),
            nightModeStrength = try {
                EffectStrength.valueOf(prefs[Keys.NIGHT_MODE_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) { EffectStrength.MODERATE },
            homeMode = try {
                HomeMode.valueOf(prefs[Keys.HOME_MODE] ?: HomeMode.VIDEO.name)
            } catch (_: Exception) { HomeMode.VIDEO },
            videoSeekDurationMs = readLong(prefs, Keys.VIDEO_SEEK_DURATION_MS, "video_seek_duration_ms", 10_000L),
            videoDefaultOrientation = try {
                OrientationMode.valueOf(prefs[Keys.VIDEO_DEFAULT_ORIENTATION] ?: OrientationMode.SENSOR_LANDSCAPE.name)
            } catch (_: Exception) { OrientationMode.SENSOR_LANDSCAPE },
            videoControlsTimeoutMs = readLong(prefs, Keys.VIDEO_CONTROLS_TIMEOUT_MS, "video_controls_timeout_ms", 5_000L),
            videoGesturesEnabled = readBool(prefs, Keys.VIDEO_GESTURES_ENABLED, "video_gestures_enabled", true),
            videoDefaultSpeed = readFloat(prefs, Keys.VIDEO_DEFAULT_SPEED, "video_default_speed", 1.0f),
            videoDefaultAspectRatio = prefs[Keys.VIDEO_DEFAULT_ASPECT_RATIO] ?: "AUTO",
            videoAutoplayNext = readBool(prefs, Keys.VIDEO_AUTOPLAY_NEXT, "video_autoplay_next", false),
            trailerAutoplay = readBool(prefs, Keys.TRAILER_AUTOPLAY, "trailer_autoplay", true),
            videoSwipeSeekMaxMs = readLong(prefs, Keys.VIDEO_SWIPE_SEEK_MAX_MS, "video_swipe_seek_max_ms", 120_000L),
            videoRememberBrightness = readBool(prefs, Keys.VIDEO_REMEMBER_BRIGHTNESS, "video_remember_brightness", false),
            videoBrightnessLevel = readFloat(prefs, Keys.VIDEO_BRIGHTNESS_LEVEL, "video_brightness_level", 0.5f),
            audioDefaultSpeed = readFloat(prefs, Keys.AUDIO_DEFAULT_SPEED, "audio_default_speed", 1.0f),
            audioNightModeVolume = readFloat(prefs, Keys.AUDIO_NIGHT_MODE_VOLUME, "audio_night_mode_volume", 0.4f),
            audioNightModeGain = readInt(prefs, Keys.AUDIO_NIGHT_MODE_GAIN, "audio_night_mode_gain", 1200),
            audioSkipPreviousThresholdMs = readLong(prefs, Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS, "audio_skip_previous_threshold_ms", 3_000L),
            audioAutoplayNext = readBool(prefs, Keys.AUDIO_AUTOPLAY_NEXT, "audio_autoplay_next", true),
            trickplayEnabled = readBool(prefs, Keys.TRICKPLAY_ENABLED, "trickplay_enabled", true),
            trickplayOnSeekGesture = readBool(prefs, Keys.TRICKPLAY_ON_SEEK_GESTURE, "trickplay_on_seek_gesture", true),
            segmentBehaviors = readSegmentBehaviors(prefs),
            videoEpisodeBrowserEnabled = readBool(prefs, Keys.VIDEO_EPISODE_BROWSER_ENABLED, "video_episode_browser_enabled", true),
            videoShowPlaybackMetadata = readBool(prefs, Keys.VIDEO_SHOW_PLAYBACK_METADATA, "video_show_playback_metadata", true),
            videoPreloadBufferSize = try {
                PreloadBufferSize.valueOf(prefs[Keys.VIDEO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
            } catch (_: Exception) { PreloadBufferSize.MEDIUM },
            audioPreloadBufferSize = try {
                PreloadBufferSize.valueOf(prefs[Keys.AUDIO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
            } catch (_: Exception) { PreloadBufferSize.MEDIUM },
            audioNormalizationMode = try {
                when (val stored = prefs[Keys.AUDIO_NORMALIZATION_MODE] ?: AudioNormalizationMode.NONE.name) {
                    "REPLAYGAIN" -> AudioNormalizationMode.TRACK
                    else -> AudioNormalizationMode.valueOf(stored)
                }
            } catch (_: Exception) { AudioNormalizationMode.NONE },
            audioNormalizationEnabled = readBool(prefs, Keys.AUDIO_NORMALIZATION_ENABLED, "audio_normalization_enabled", false),
            replayGainPreAmpDb = readFloat(prefs, Keys.REPLAYGAIN_PRE_AMP_DB, "replaygain_pre_amp_db", 0f),
            channelMixMode = try {
                ChannelMixMode.valueOf(prefs[Keys.CHANNEL_MIX_MODE] ?: ChannelMixMode.AUTO.name)
            } catch (_: Exception) { ChannelMixMode.AUTO },
            channelMixEnabled = readBool(prefs, Keys.CHANNEL_MIX_ENABLED, "channel_mix_enabled", false),
            audioGaplessEnabled = readBool(prefs, Keys.AUDIO_GAPLESS_ENABLED, "audio_gapless_enabled", true),
            audioCrossfadeDurationMs = readLong(prefs, Keys.AUDIO_CROSSFADE_DURATION_MS, "audio_crossfade_duration_ms", 0L),
            sleepTimerDurationMs = readLong(prefs, Keys.SLEEP_TIMER_DURATION_MS, "sleep_timer_duration_ms", 0L),
            sleepTimerEndOfEpisode = readBool(prefs, Keys.SLEEP_TIMER_END_OF_EPISODE, "sleep_timer_end_of_episode", false),
            dreamImageCategories = try {
                prefs[Keys.DREAM_IMAGE_CATEGORIES]?.let {
                    json.decodeFromString<Set<DreamImageCategory>>(it)
                } ?: setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)
            } catch (_: Exception) { setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES) },
            dreamSlideshowIntervalMs = readLong(prefs, Keys.DREAM_SLIDESHOW_INTERVAL_MS, "dream_slideshow_interval_ms", 15_000L),
            dreamKenBurnsEnabled = readBool(prefs, Keys.DREAM_KEN_BURNS_ENABLED, "dream_ken_burns_enabled", true),
            dreamTransitionStyle = try {
                DreamTransitionStyle.valueOf(prefs[Keys.DREAM_TRANSITION_STYLE] ?: DreamTransitionStyle.CROSSFADE.name)
            } catch (_: Exception) { DreamTransitionStyle.CROSSFADE },
            dreamShowTitle = readBool(prefs, Keys.DREAM_SHOW_TITLE, "dream_show_title", true),
            equalizerPreset = try {
                EqualizerPreset.valueOf(prefs[Keys.EQUALIZER_PRESET] ?: EqualizerPreset.FLAT.name)
            } catch (_: Exception) { EqualizerPreset.FLAT },
            bassBoostEnabled = readBool(prefs, Keys.BASS_BOOST_ENABLED, "bass_boost_enabled", false),
            bassBoostStrength = try {
                EffectStrength.valueOf(prefs[Keys.BASS_BOOST_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) { EffectStrength.MODERATE },
            virtualizerEnabled = readBool(prefs, Keys.VIRTUALIZER_ENABLED, "virtualizer_enabled", false),
            virtualizerStrength = readInt(prefs, Keys.VIRTUALIZER_STRENGTH, "virtualizer_strength", 500),
            reverbPreset = try {
                ReverbPreset.valueOf(prefs[Keys.REVERB_PRESET] ?: ReverbPreset.NONE.name)
            } catch (_: Exception) { ReverbPreset.NONE },
            lrBalance = readFloat(prefs, Keys.LR_BALANCE, "lr_balance", 0f),
            autoEqByGenre = readBool(prefs, Keys.AUTO_EQ_BY_GENRE, "auto_eq_by_genre", false),
            pitchSemitones = readFloat(prefs, Keys.PITCH_SEMITONES, "pitch_semitones", 0f),
            wifiOnlyDownloads = readBool(prefs, Keys.WIFI_ONLY_DOWNLOADS, "wifi_only_downloads", true),
            downloadConnections = readInt(prefs, Keys.DOWNLOAD_CONNECTIONS, "download_connections", 4),
            enabledHomeSectionTypes = try {
                prefs[Keys.HOME_ENABLED_SECTION_TYPES]?.let {
                    json.decodeFromString<Set<String>>(it)
                        .mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                        .toSet()
                } ?: HomeSectionType.CONFIGURABLE.toSet()
            } catch (_: Exception) { HomeSectionType.CONFIGURABLE.toSet() },
            homeSectionOrder = try {
                prefs[Keys.HOME_SECTION_ORDER]?.let {
                    val parsed = try {
                        json.decodeFromString<List<String>>(it)
                    } catch (_: Exception) {
                        json.decodeFromString<Set<String>>(it).toList()
                    }
                    val mapped = parsed.mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                    buildList {
                        addAll(mapped)
                        addAll(HomeSectionType.CONFIGURABLE.filterNot { it in mapped })
                    }
                } ?: HomeSectionType.CONFIGURABLE
            } catch (_: Exception) { HomeSectionType.CONFIGURABLE },
            hiddenLibrarySectionIds = try {
                prefs[Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS]?.let {
                    json.decodeFromString<Set<String>>(it)
                } ?: emptySet()
            } catch (_: Exception) { emptySet() },
            navBarShowLabels = readBool(prefs, Keys.NAV_BAR_SHOW_LABELS, "nav_bar_show_labels", true),
            homeHeroEnabled = readBool(prefs, Keys.HOME_HERO_ENABLED, "home_hero_enabled", true),
            onboardingCompleted = readBool(prefs, Keys.ONBOARDING_COMPLETED, "onboarding_completed", false),
            mpvConfig = try {
                prefs[Keys.MPV_CONFIG]?.let { json.decodeFromString<MpvEngineConfig>(it) } ?: MpvEngineConfig()
            } catch (_: Exception) { MpvEngineConfig() },
            libVlcConfig = try {
                prefs[Keys.LIBVLC_CONFIG]?.let { json.decodeFromString<LibVlcEngineConfig>(it) } ?: LibVlcEngineConfig()
            } catch (_: Exception) { LibVlcEngineConfig() },
            exoPlayerConfig = try {
                prefs[Keys.EXO_CONFIG]?.let { json.decodeFromString<ExoPlayerEngineConfig>(it) } ?: ExoPlayerEngineConfig()
            } catch (_: Exception) { ExoPlayerEngineConfig() },
            performanceMode = readBool(prefs, Keys.PERFORMANCE_MODE, "performance_mode", false),
            newsletterEnabled = readBool(prefs, Keys.NEWSLETTER_ENABLED, "newsletter_enabled", true),
            newsletterDayOfWeek = readInt(prefs, Keys.NEWSLETTER_DAY_OF_WEEK, "newsletter_day_of_week", 7),
            newsletterLastViewedMs = readLong(prefs, Keys.NEWSLETTER_LAST_VIEWED_MS, "newsletter_last_viewed_ms", 0L),
            accentColorSwatch = prefs[Keys.ACCENT_COLOR_SWATCH] ?: "dynamic",
            colorStyle = try {
                ColorStyle.valueOf(prefs[Keys.COLOR_STYLE] ?: ColorStyle.TONAL_SPOT.name)
            } catch (_: Exception) { ColorStyle.TONAL_SPOT },
            libraryViewMode = try {
                LibraryViewMode.valueOf(prefs[Keys.LIBRARY_VIEW_MODE] ?: LibraryViewMode.GRID.name)
            } catch (_: Exception) { LibraryViewMode.GRID },
            notificationPreferences = NotificationPreferences(
                enabled = readBool(prefs, Keys.NOTIFICATIONS_ENABLED, "notifications_enabled", false),
                checkFrequency = try {
                    CheckFrequency.valueOf(prefs[Keys.NOTIFICATIONS_CHECK_FREQUENCY] ?: CheckFrequency.EVERY_6_HOURS.name)
                } catch (_: Exception) { CheckFrequency.EVERY_6_HOURS },
                quietHoursEnabled = readBool(prefs, Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED, "notifications_quiet_hours_enabled", false),
                quietHoursStart = readInt(prefs, Keys.NOTIFICATIONS_QUIET_HOURS_START, "notifications_quiet_hours_start", 1380),
                quietHoursEnd = readInt(prefs, Keys.NOTIFICATIONS_QUIET_HOURS_END, "notifications_quiet_hours_end", 420),
                soundEnabled = readBool(prefs, Keys.NOTIFICATIONS_SOUND_ENABLED, "notifications_sound_enabled", true),
                vibrateEnabled = readBool(prefs, Keys.NOTIFICATIONS_VIBRATE_ENABLED, "notifications_vibrate_enabled", true),
                lightsEnabled = readBool(prefs, Keys.NOTIFICATIONS_LIGHTS_ENABLED, "notifications_lights_enabled", true),
                maxPerCheck = readInt(prefs, Keys.NOTIFICATIONS_MAX_PER_CHECK, "notifications_max_per_check", 10),
                libraryConfigs = try {
                    prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS]?.let {
                        json.decodeFromString<Map<String, LibraryNotificationConfig>>(it)
                    } ?: emptyMap()
                } catch (_: Exception) { emptyMap() },
            ),
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    val activeServerId: Flow<String?> = sharedPrefs.map { it[Keys.ACTIVE_SERVER_ID] }.distinctUntilChanged()
    val activeUserId: Flow<String?> = sharedPrefs.map { it[Keys.ACTIVE_USER_ID] }.distinctUntilChanged()
    val deviceId: Flow<String?> = sharedPrefs.map { it[Keys.DEVICE_ID] }.distinctUntilChanged()

    suspend fun ensureDeviceId(): String {
        var id: String? = null
        context.dataStore.edit { prefs ->
            id = prefs[Keys.DEVICE_ID] ?: java.util.UUID.randomUUID().toString().also { prefs[Keys.DEVICE_ID] = it }
        }
        return id ?: error("deviceId could not be resolved")
    }

    suspend fun setActiveServer(serverId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_SERVER_ID] = serverId }
    }

    suspend fun setActiveUser(userId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_USER_ID] = userId }
    }

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        context.dataStore.edit { it[Keys.PREFERRED_PLAYER] = playerType.name }
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        context.dataStore.edit {
            if (language != null) it[Keys.PREFERRED_SUBTITLE_LANG] = language
            else it.remove(Keys.PREFERRED_SUBTITLE_LANG)
        }
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        context.dataStore.edit {
            if (language != null) it[Keys.PREFERRED_AUDIO_LANG] = language
            else it.remove(Keys.PREFERRED_AUDIO_LANG)
        }
    }

    suspend fun setMediaStreamSelection(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        writeMediaStreamSelections(
            itemId = itemId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
    }

    suspend fun setDynamicTheming(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_THEMING] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        context.dataStore.edit { it[Keys.CONTRAST_LEVEL] = level.name }
    }

    suspend fun setOledMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OLED_MODE] = enabled }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit { it[Keys.SUBTITLE_STYLE] = json.encodeToString(style) }
    }

    suspend fun setStreamingQuality(quality: StreamingQuality) {
        context.dataStore.edit { it[Keys.STREAMING_QUALITY] = quality.name }
    }

    suspend fun setMaxCacheSize(sizeMb: Int) {
        context.dataStore.edit { it[Keys.MAX_CACHE_SIZE_MB] = sizeMb }
    }

    suspend fun setAutoDeleteCache(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DELETE_CACHE] = enabled }
    }

    suspend fun setPinLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PIN_LOCK_ENABLED] = enabled }
    }

    suspend fun setPinHash(hash: String?) {
        context.dataStore.edit {
            if (hash != null) it[Keys.PIN_HASH] = hash
            else it.remove(Keys.PIN_HASH)
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    fun verifyPin(input: String, storedHash: String?): Boolean {
        if (storedHash == null) return false
        return hashPin(input) == storedHash
    }

    fun hashPin(pin: String): String {
        val digest = (sha256Digest.clone() as MessageDigest)
        return digest
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun setContinueWatching(items: List<com.raulshma.jellyplay.core.model.MediaItem>) {
        context.dataStore.edit { it[Keys.CONTINUE_WATCHING] = json.encodeToString(items) }
    }

    suspend fun setAutoLockTimerMs(ms: Long) {
        context.dataStore.edit { it[Keys.AUTO_LOCK_TIMER_MS] = ms }
    }

    suspend fun setDialogueBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIALOGUE_BOOST_ENABLED] = enabled }
    }

    suspend fun setDialogueBoostStrength(strength: EffectStrength) {
        context.dataStore.edit { it[Keys.DIALOGUE_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQUALIZER_ENABLED] = enabled }
    }

    suspend fun setEqualizerSettings(settings: EqualizerSettings) {
        context.dataStore.edit { it[Keys.EQUALIZER_SETTINGS] = json.encodeToString(settings) }
    }

    suspend fun setAudioDelay(ms: Long) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        context.dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_PASSTHROUGH] = enabled }
    }

    suspend fun setFrameRateMatching(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FRAME_RATE_MATCHING] = enabled }
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHT_MODE_ENABLED] = enabled }
    }

    suspend fun setNightModeStrength(strength: EffectStrength) {
        context.dataStore.edit { it[Keys.NIGHT_MODE_STRENGTH] = strength.name }
    }

    suspend fun setHomeMode(mode: HomeMode) {
        context.dataStore.edit { it[Keys.HOME_MODE] = mode.name }
    }

    suspend fun setVideoSeekDurationMs(ms: Long) {
        context.dataStore.edit { it[Keys.VIDEO_SEEK_DURATION_MS] = ms }
    }

    suspend fun setVideoDefaultOrientation(mode: OrientationMode) {
        context.dataStore.edit { it[Keys.VIDEO_DEFAULT_ORIENTATION] = mode.name }
    }

    suspend fun setVideoControlsTimeoutMs(ms: Long) {
        context.dataStore.edit { it[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = ms }
    }

    suspend fun setVideoGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_GESTURES_ENABLED] = enabled }
    }

    suspend fun setVideoDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.VIDEO_DEFAULT_SPEED] = speed }
    }

    suspend fun setVideoDefaultAspectRatio(ratio: String) {
        context.dataStore.edit { it[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = ratio }
    }

    suspend fun setVideoAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setTrailerAutoplay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRAILER_AUTOPLAY] = enabled }
    }

    suspend fun setVideoSwipeSeekMaxMs(ms: Long) {
        context.dataStore.edit { it[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = ms }
    }

    suspend fun setVideoRememberBrightness(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_REMEMBER_BRIGHTNESS] = enabled }
    }

    suspend fun setVideoBrightnessLevel(level: Float) {
        context.dataStore.edit { it[Keys.VIDEO_BRIGHTNESS_LEVEL] = level }
    }

    suspend fun setAudioDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.AUDIO_DEFAULT_SPEED] = speed }
    }

    suspend fun setAudioNightModeVolume(volume: Float) {
        context.dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_VOLUME] = volume }
    }

    suspend fun setAudioNightModeGain(gain: Int) {
        context.dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_GAIN] = gain }
    }

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) {
        context.dataStore.edit { it[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = ms }
    }

    suspend fun setAudioAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setTrickplayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRICKPLAY_ENABLED] = enabled }
    }

    suspend fun setTrickplayOnSeekGesture(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRICKPLAY_ON_SEEK_GESTURE] = enabled }
    }

    suspend fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) {
        context.dataStore.edit { prefs ->
            val current = readSegmentBehaviors(prefs).toMutableMap()
            current[type] = behavior
            prefs[Keys.SEGMENT_BEHAVIORS] = json.encodeToString(
                current.mapKeys { it.key.name }.mapValues { it.value.name }
            )
        }
    }

    suspend fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_EPISODE_BROWSER_ENABLED] = enabled }
    }

    suspend fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_SHOW_PLAYBACK_METADATA] = enabled }
    }

    suspend fun setVideoPreloadBufferSize(size: PreloadBufferSize) {
        context.dataStore.edit { it[Keys.VIDEO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) {
        context.dataStore.edit { it[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        context.dataStore.edit { it[Keys.AUDIO_NORMALIZATION_MODE] = mode.name }
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_NORMALIZATION_ENABLED] = enabled }
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        context.dataStore.edit { it[Keys.REPLAYGAIN_PRE_AMP_DB] = db }
    }

    suspend fun setChannelMixMode(mode: ChannelMixMode) {
        context.dataStore.edit { it[Keys.CHANNEL_MIX_MODE] = mode.name }
    }

    suspend fun setChannelMixEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHANNEL_MIX_ENABLED] = enabled }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_GAPLESS_ENABLED] = enabled }
    }

    suspend fun setCrossfadeDurationMs(ms: Long) {
        context.dataStore.edit { it[Keys.AUDIO_CROSSFADE_DURATION_MS] = ms }
    }

    suspend fun setSleepTimerDurationMs(ms: Long) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_DURATION_MS] = ms }
    }

    suspend fun setSleepTimerEndOfEpisode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_END_OF_EPISODE] = enabled }
    }

    suspend fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        context.dataStore.edit { it[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(categories) }
    }

    suspend fun setDreamSlideshowIntervalMs(ms: Long) {
        context.dataStore.edit { it[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = ms }
    }

    suspend fun setDreamKenBurnsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DREAM_KEN_BURNS_ENABLED] = enabled }
    }

    suspend fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        context.dataStore.edit { it[Keys.DREAM_TRANSITION_STYLE] = style.name }
    }

    suspend fun setDreamShowTitle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DREAM_SHOW_TITLE] = enabled }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        context.dataStore.edit { it[Keys.EQUALIZER_PRESET] = preset.name }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BASS_BOOST_ENABLED] = enabled }
    }

    suspend fun setBassBoostStrength(strength: EffectStrength) {
        context.dataStore.edit { it[Keys.BASS_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_ENABLED] = enabled }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = strength }
    }

    suspend fun setReverbPreset(preset: ReverbPreset) {
        context.dataStore.edit { it[Keys.REVERB_PRESET] = preset.name }
    }

    suspend fun setLrBalance(balance: Float) {
        context.dataStore.edit { it[Keys.LR_BALANCE] = balance }
    }

    suspend fun setAutoEqByGenre(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EQ_BY_GENRE] = enabled }
    }

    suspend fun setPitchSemitones(semitones: Float) {
        context.dataStore.edit { it[Keys.PITCH_SEMITONES] = semitones }
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun setDownloadConnections(count: Int) {
        context.dataStore.edit { it[Keys.DOWNLOAD_CONNECTIONS] = count }
    }

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        context.dataStore.edit {
            it[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(types.map { t -> t.name }.toSet())
        }
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) {
        context.dataStore.edit {
            val normalized = buildList {
                addAll(order.filter { it in HomeSectionType.CONFIGURABLE }.distinct())
                addAll(HomeSectionType.CONFIGURABLE.filterNot { it in this })
            }
            it[Keys.HOME_SECTION_ORDER] = json.encodeToString(normalized.map { t -> t.name })
        }
    }

    suspend fun setHiddenLibrarySectionIds(ids: Set<String>) {
        context.dataStore.edit {
            it[Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS] = json.encodeToString(ids)
        }
    }

    suspend fun setNavBarShowLabels(show: Boolean) {
        context.dataStore.edit { it[Keys.NAV_BAR_SHOW_LABELS] = show }
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HOME_HERO_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMpvConfig(config: MpvEngineConfig) {
        context.dataStore.edit { it[Keys.MPV_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setLibVlcConfig(config: LibVlcEngineConfig) {
        context.dataStore.edit { it[Keys.LIBVLC_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        context.dataStore.edit { it[Keys.EXO_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setPerformanceMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERFORMANCE_MODE] = enabled }
    }

    val continueWatching: kotlinx.coroutines.flow.Flow<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.CONTINUE_WATCHING]?.let {
                try {
                    json.decodeFromString<List<com.raulshma.jellyplay.core.model.MediaItem>>(it)
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }

    val widgetConfig: kotlinx.coroutines.flow.Flow<WidgetConfig> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.WIDGET_CONFIG]?.let {
                try { json.decodeFromString<WidgetConfig>(it) } catch (_: Exception) { null }
            } ?: WidgetConfig()
        }

    val libraryWidgetItems: kotlinx.coroutines.flow.Flow<List<LibraryWidgetItem>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.LIBRARY_WIDGET_ITEMS]?.let {
                try { json.decodeFromString<List<LibraryWidgetItem>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }

    val libraryWidgetVersion: kotlinx.coroutines.flow.Flow<Long> =
        context.dataStore.data.map { it[Keys.LIBRARY_WIDGET_VERSION] ?: 0L }.distinctUntilChanged()

    val libraryWidgetUpdatedAtMs: kotlinx.coroutines.flow.Flow<Long> =
        context.dataStore.data.map { it[Keys.LIBRARY_WIDGET_UPDATED_AT_MS] ?: 0L }.distinctUntilChanged()

    val seerrWidgetItems: kotlinx.coroutines.flow.Flow<List<SeerrWidgetItem>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.SEERR_WIDGET_ITEMS]?.let {
                try { json.decodeFromString<List<SeerrWidgetItem>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }

    val seerrWidgetVersion: kotlinx.coroutines.flow.Flow<Long> =
        context.dataStore.data.map { it[Keys.SEERR_WIDGET_VERSION] ?: 0L }.distinctUntilChanged()

    val seerrWidgetUpdatedAtMs: kotlinx.coroutines.flow.Flow<Long> =
        context.dataStore.data.map { it[Keys.SEERR_WIDGET_UPDATED_AT_MS] ?: 0L }.distinctUntilChanged()

    val widgetLastRefreshMs: kotlinx.coroutines.flow.Flow<Long> =
        context.dataStore.data.map { it[Keys.WIDGET_LAST_REFRESH_MS] ?: 0L }.distinctUntilChanged()

    suspend fun setWidgetConfig(config: WidgetConfig) {
        context.dataStore.edit { it[Keys.WIDGET_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setLibraryWidgetItems(
        items: List<LibraryWidgetItem>,
        version: Long,
        updatedAtMs: Long,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_WIDGET_ITEMS] = json.encodeToString(items)
            prefs[Keys.LIBRARY_WIDGET_VERSION] = version
            prefs[Keys.LIBRARY_WIDGET_UPDATED_AT_MS] = updatedAtMs
        }
    }

    suspend fun setSeerrWidgetItems(
        items: List<SeerrWidgetItem>,
        version: Long,
        updatedAtMs: Long,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEERR_WIDGET_ITEMS] = json.encodeToString(items)
            prefs[Keys.SEERR_WIDGET_VERSION] = version
            prefs[Keys.SEERR_WIDGET_UPDATED_AT_MS] = updatedAtMs
        }
    }

    suspend fun setWidgetLastRefreshMs(ms: Long) {
        context.dataStore.edit { it[Keys.WIDGET_LAST_REFRESH_MS] = ms }
    }

    suspend fun setNewsletterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NEWSLETTER_ENABLED] = enabled }
    }

    suspend fun setNewsletterDayOfWeek(day: Int) {
        context.dataStore.edit { it[Keys.NEWSLETTER_DAY_OF_WEEK] = day }
    }

    suspend fun setNewsletterLastViewed(timestampMs: Long) {
        context.dataStore.edit { it[Keys.NEWSLETTER_LAST_VIEWED_MS] = timestampMs }
    }

    suspend fun setAccentColorSwatch(swatch: String) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR_SWATCH] = swatch }
    }

    suspend fun setColorStyle(style: ColorStyle) {
        context.dataStore.edit { it[Keys.COLOR_STYLE] = style.name }
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        context.dataStore.edit { it[Keys.LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun restorePreferences(prefs: UserPreferences) {
        val json = Json { encodeDefaults = true }
        context.dataStore.edit { settings ->
            settings[Keys.PREFERRED_PLAYER] = prefs.preferredPlayer.name
            prefs.preferredSubtitleLanguage?.let { settings[Keys.PREFERRED_SUBTITLE_LANG] = it }
            prefs.preferredAudioLanguage?.let { settings[Keys.PREFERRED_AUDIO_LANG] = it }
            settings[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, com.raulshma.jellyplay.core.model.MediaStreamSelection>>(),
                prefs.mediaStreamSelections,
            )
            settings[Keys.DYNAMIC_THEMING] = prefs.dynamicTheming
            settings[Keys.THEME_MODE] = prefs.themeMode.name
            settings[Keys.CONTRAST_LEVEL] = prefs.contrastLevel.name
            settings[Keys.OLED_MODE] = prefs.oledMode
            settings[Keys.SUBTITLE_STYLE] = json.encodeToString(
                kotlinx.serialization.serializer<com.raulshma.jellyplay.core.model.SubtitleStyle>(),
                prefs.subtitleStyle,
            )
            settings[Keys.STREAMING_QUALITY] = prefs.streamingQuality.name
            settings[Keys.MAX_CACHE_SIZE_MB] = prefs.maxCacheSizeMb
            settings[Keys.AUTO_DELETE_CACHE] = prefs.autoDeleteCache
            settings[Keys.PIN_LOCK_ENABLED] = prefs.pinLockEnabled
            prefs.pinHash?.let { settings[Keys.PIN_HASH] = it }
            settings[Keys.BIOMETRIC_LOCK_ENABLED] = prefs.biometricLockEnabled
            settings[Keys.AUTO_LOCK_TIMER_MS] = prefs.autoLockTimerMs
            settings[Keys.DIALOGUE_BOOST_ENABLED] = prefs.dialogueBoostEnabled
            settings[Keys.DIALOGUE_BOOST_STRENGTH] = prefs.dialogueBoostStrength.name
            settings[Keys.EQUALIZER_ENABLED] = prefs.equalizerEnabled
            settings[Keys.EQUALIZER_SETTINGS] = json.encodeToString(
                kotlinx.serialization.serializer<com.raulshma.jellyplay.core.model.EqualizerSettings>(),
                prefs.equalizerSettings,
            )
            settings[Keys.AUDIO_DELAY_MS] = prefs.audioDelayMs
            settings[Keys.DECODER_MODE] = prefs.decoderMode.name
            settings[Keys.AUDIO_PASSTHROUGH] = prefs.audioPassthrough
            settings[Keys.FRAME_RATE_MATCHING] = prefs.frameRateMatching
            settings[Keys.NIGHT_MODE_ENABLED] = prefs.nightModeEnabled
            settings[Keys.NIGHT_MODE_STRENGTH] = prefs.nightModeStrength.name
            settings[Keys.HOME_MODE] = prefs.homeMode.name
            settings[Keys.VIDEO_SEEK_DURATION_MS] = prefs.videoSeekDurationMs
            settings[Keys.VIDEO_DEFAULT_ORIENTATION] = prefs.videoDefaultOrientation.name
            settings[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = prefs.videoControlsTimeoutMs
            settings[Keys.VIDEO_GESTURES_ENABLED] = prefs.videoGesturesEnabled
            settings[Keys.VIDEO_DEFAULT_SPEED] = prefs.videoDefaultSpeed
            settings[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = prefs.videoDefaultAspectRatio
            settings[Keys.VIDEO_AUTOPLAY_NEXT] = prefs.videoAutoplayNext
            settings[Keys.TRAILER_AUTOPLAY] = prefs.trailerAutoplay
            settings[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = prefs.videoSwipeSeekMaxMs
            settings[Keys.VIDEO_REMEMBER_BRIGHTNESS] = prefs.videoRememberBrightness
            settings[Keys.VIDEO_BRIGHTNESS_LEVEL] = prefs.videoBrightnessLevel
            settings[Keys.AUDIO_DEFAULT_SPEED] = prefs.audioDefaultSpeed
            settings[Keys.AUDIO_NIGHT_MODE_VOLUME] = prefs.audioNightModeVolume
            settings[Keys.AUDIO_NIGHT_MODE_GAIN] = prefs.audioNightModeGain
            settings[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = prefs.audioSkipPreviousThresholdMs
            settings[Keys.AUDIO_AUTOPLAY_NEXT] = prefs.audioAutoplayNext
            settings[Keys.TRICKPLAY_ENABLED] = prefs.trickplayEnabled
            settings[Keys.TRICKPLAY_ON_SEEK_GESTURE] = prefs.trickplayOnSeekGesture
            settings[Keys.SEGMENT_BEHAVIORS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<com.raulshma.jellyplay.core.model.MediaSegmentType, com.raulshma.jellyplay.core.model.SegmentBehavior>>(),
                prefs.segmentBehaviors,
            )
            settings[Keys.VIDEO_EPISODE_BROWSER_ENABLED] = prefs.videoEpisodeBrowserEnabled
            settings[Keys.VIDEO_SHOW_PLAYBACK_METADATA] = prefs.videoShowPlaybackMetadata
            settings[Keys.VIDEO_PRELOAD_BUFFER_SIZE] = prefs.videoPreloadBufferSize.name
            settings[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = prefs.audioPreloadBufferSize.name
            settings[Keys.AUDIO_NORMALIZATION_MODE] = prefs.audioNormalizationMode.name
            settings[Keys.AUDIO_NORMALIZATION_ENABLED] = prefs.audioNormalizationEnabled
            settings[Keys.REPLAYGAIN_PRE_AMP_DB] = prefs.replayGainPreAmpDb
            settings[Keys.CHANNEL_MIX_MODE] = prefs.channelMixMode.name
            settings[Keys.CHANNEL_MIX_ENABLED] = prefs.channelMixEnabled
            settings[Keys.AUDIO_GAPLESS_ENABLED] = prefs.audioGaplessEnabled
            settings[Keys.AUDIO_CROSSFADE_DURATION_MS] = prefs.audioCrossfadeDurationMs
            settings[Keys.SLEEP_TIMER_DURATION_MS] = prefs.sleepTimerDurationMs
            settings[Keys.SLEEP_TIMER_END_OF_EPISODE] = prefs.sleepTimerEndOfEpisode
            settings[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(
                kotlinx.serialization.serializer<Set<com.raulshma.jellyplay.core.model.DreamImageCategory>>(),
                prefs.dreamImageCategories,
            )
            settings[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = prefs.dreamSlideshowIntervalMs
            settings[Keys.DREAM_KEN_BURNS_ENABLED] = prefs.dreamKenBurnsEnabled
            settings[Keys.DREAM_TRANSITION_STYLE] = prefs.dreamTransitionStyle.name
            settings[Keys.DREAM_SHOW_TITLE] = prefs.dreamShowTitle
            settings[Keys.EQUALIZER_PRESET] = prefs.equalizerPreset.name
            settings[Keys.BASS_BOOST_ENABLED] = prefs.bassBoostEnabled
            settings[Keys.BASS_BOOST_STRENGTH] = prefs.bassBoostStrength.name
            settings[Keys.VIRTUALIZER_ENABLED] = prefs.virtualizerEnabled
            settings[Keys.VIRTUALIZER_STRENGTH] = prefs.virtualizerStrength
            settings[Keys.REVERB_PRESET] = prefs.reverbPreset.name
            settings[Keys.LR_BALANCE] = prefs.lrBalance
            settings[Keys.AUTO_EQ_BY_GENRE] = prefs.autoEqByGenre
            settings[Keys.PITCH_SEMITONES] = prefs.pitchSemitones
            settings[Keys.WIFI_ONLY_DOWNLOADS] = prefs.wifiOnlyDownloads
            settings[Keys.DOWNLOAD_CONNECTIONS] = prefs.downloadConnections
            settings[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(
                kotlinx.serialization.serializer<Set<com.raulshma.jellyplay.core.model.HomeSectionType>>(),
                prefs.enabledHomeSectionTypes,
            )
            settings[Keys.HOME_SECTION_ORDER] = json.encodeToString(
                kotlinx.serialization.serializer<List<com.raulshma.jellyplay.core.model.HomeSectionType>>(),
                prefs.homeSectionOrder,
            )
            settings[Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS] = json.encodeToString(
                kotlinx.serialization.serializer<Set<String>>(),
                prefs.hiddenLibrarySectionIds,
            )
            settings[Keys.NAV_BAR_SHOW_LABELS] = prefs.navBarShowLabels
            settings[Keys.HOME_HERO_ENABLED] = prefs.homeHeroEnabled
            settings[Keys.ONBOARDING_COMPLETED] = prefs.onboardingCompleted
            settings[Keys.MPV_CONFIG] = json.encodeToString(
                kotlinx.serialization.serializer<com.raulshma.jellyplay.core.model.MpvEngineConfig>(),
                prefs.mpvConfig,
            )
            settings[Keys.LIBVLC_CONFIG] = json.encodeToString(
                kotlinx.serialization.serializer<com.raulshma.jellyplay.core.model.LibVlcEngineConfig>(),
                prefs.libVlcConfig,
            )
            settings[Keys.EXO_CONFIG] = json.encodeToString(
                kotlinx.serialization.serializer<com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig>(),
                prefs.exoPlayerConfig,
            )
            settings[Keys.PERFORMANCE_MODE] = prefs.performanceMode
            settings[Keys.NEWSLETTER_ENABLED] = prefs.newsletterEnabled
            settings[Keys.NEWSLETTER_DAY_OF_WEEK] = prefs.newsletterDayOfWeek
            settings[Keys.NEWSLETTER_LAST_VIEWED_MS] = prefs.newsletterLastViewedMs
            settings[Keys.ACCENT_COLOR_SWATCH] = prefs.accentColorSwatch
            settings[Keys.COLOR_STYLE] = prefs.colorStyle.name
            settings[Keys.LIBRARY_VIEW_MODE] = prefs.libraryViewMode.name
            val np = prefs.notificationPreferences
            settings[Keys.NOTIFICATIONS_ENABLED] = np.enabled
            settings[Keys.NOTIFICATIONS_CHECK_FREQUENCY] = np.checkFrequency.name
            settings[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] = np.quietHoursEnabled
            settings[Keys.NOTIFICATIONS_QUIET_HOURS_START] = np.quietHoursStart
            settings[Keys.NOTIFICATIONS_QUIET_HOURS_END] = np.quietHoursEnd
            settings[Keys.NOTIFICATIONS_SOUND_ENABLED] = np.soundEnabled
            settings[Keys.NOTIFICATIONS_VIBRATE_ENABLED] = np.vibrateEnabled
            settings[Keys.NOTIFICATIONS_LIGHTS_ENABLED] = np.lightsEnabled
            settings[Keys.NOTIFICATIONS_MAX_PER_CHECK] = np.maxPerCheck
            settings[Keys.NOTIFICATIONS_LIBRARY_CONFIGS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, LibraryNotificationConfig>>(),
                np.libraryConfigs,
            )
        }
    }

    val notificationPreferences: StateFlow<NotificationPreferences> = preferences.map { it.notificationPreferences }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, NotificationPreferences())

    suspend fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        context.dataStore.edit { prefs ->
            val current = NotificationPreferences(
                enabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
                checkFrequency = try {
                    CheckFrequency.valueOf(prefs[Keys.NOTIFICATIONS_CHECK_FREQUENCY] ?: CheckFrequency.EVERY_6_HOURS.name)
                } catch (_: Exception) { CheckFrequency.EVERY_6_HOURS },
                quietHoursEnabled = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] ?: false,
                quietHoursStart = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_START] ?: 1380,
                quietHoursEnd = prefs[Keys.NOTIFICATIONS_QUIET_HOURS_END] ?: 420,
                soundEnabled = prefs[Keys.NOTIFICATIONS_SOUND_ENABLED] ?: true,
                vibrateEnabled = prefs[Keys.NOTIFICATIONS_VIBRATE_ENABLED] ?: true,
                lightsEnabled = prefs[Keys.NOTIFICATIONS_LIGHTS_ENABLED] ?: true,
                maxPerCheck = prefs[Keys.NOTIFICATIONS_MAX_PER_CHECK] ?: 10,
                libraryConfigs = try {
                    prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS]?.let {
                        json.decodeFromString<Map<String, LibraryNotificationConfig>>(it)
                    } ?: emptyMap()
                } catch (_: Exception) { emptyMap() },
            )
            val updated = transform(current)
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.enabled
            prefs[Keys.NOTIFICATIONS_CHECK_FREQUENCY] = updated.checkFrequency.name
            prefs[Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED] = updated.quietHoursEnabled
            prefs[Keys.NOTIFICATIONS_QUIET_HOURS_START] = updated.quietHoursStart
            prefs[Keys.NOTIFICATIONS_QUIET_HOURS_END] = updated.quietHoursEnd
            prefs[Keys.NOTIFICATIONS_SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.NOTIFICATIONS_VIBRATE_ENABLED] = updated.vibrateEnabled
            prefs[Keys.NOTIFICATIONS_LIGHTS_ENABLED] = updated.lightsEnabled
            prefs[Keys.NOTIFICATIONS_MAX_PER_CHECK] = updated.maxPerCheck
            prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, LibraryNotificationConfig>>(),
                updated.libraryConfigs,
            )
        }
    }

    val recentDlnaDevices: Flow<List<DlnaDeviceRef>>
        get() = context.dataStore.data.map { prefs ->
            prefs[Keys.RECENT_DLNA_DEVICES]?.let {
                try {
                    json.decodeFromString<List<DlnaDeviceRef>>(it)
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }

    suspend fun addRecentDlnaDevice(device: DlnaDeviceRef) {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            val current = try {
                prefs[Keys.RECENT_DLNA_DEVICES]?.let {
                    json.decodeFromString<List<DlnaDeviceRef>>(it)
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            prefs[Keys.RECENT_DLNA_DEVICES] = json.encodeToString(current.filter { it.id != deviceId })
        }
    }
}
