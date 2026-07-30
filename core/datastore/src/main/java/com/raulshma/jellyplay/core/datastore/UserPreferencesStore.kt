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
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.WidgetConfig
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    @com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    private val widgetDataStore: com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore,
    private val serverIdentityStore: com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore,
    private val pinRateLimiter: com.raulshma.jellyplay.core.datastore.security.PinRateLimiter,
) {
    private val scope = externalScope

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emit(emptyPreferences()) }

    private object Keys {
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val SUBTITLES_FORCED_ONLY = booleanPreferencesKey("subtitles_forced_only")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val MEDIA_STREAM_SELECTIONS = stringPreferencesKey("media_stream_selections")
        val VIDEO_EFFECTS_SELECTIONS = stringPreferencesKey("video_effects_selections")
        val SUBTITLE_DELAY_BY_ITEM = stringPreferencesKey("subtitle_delay_by_item")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CONTRAST_LEVEL = stringPreferencesKey("contrast_level")
        val SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val FORCE_DIRECT_PLAY = booleanPreferencesKey("force_direct_play")
        val PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        val LIVE_STREAM_OPTION = stringPreferencesKey("live_stream_option")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val DIALOGUE_BOOST_STRENGTH = stringPreferencesKey("dialogue_boost_strength")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val NIGHT_MODE_STRENGTH = stringPreferencesKey("night_mode_strength")
        val HOME_MODE = stringPreferencesKey("home_mode")
        val VIDEO_DEFAULT_ORIENTATION = stringPreferencesKey("video_default_orientation")
        val VIDEO_DEFAULT_ASPECT_RATIO = stringPreferencesKey("video_default_aspect_ratio")
        val VIDEO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("video_preload_buffer_size")
        val AUDIO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("audio_preload_buffer_size")
        val AUDIO_CACHING_ENABLED = booleanPreferencesKey("audio_caching_enabled")
        val AUDIO_CACHE_SIZE_MB = intPreferencesKey("audio_cache_size_mb")
        val AUDIO_PREFETCH_LOOKAHEAD = intPreferencesKey("audio_prefetch_lookahead")
        val AUDIO_PREFETCH_BACKFILL = intPreferencesKey("audio_prefetch_backfill")
        val AUDIO_CACHE_NETWORK_POLICY = stringPreferencesKey("audio_cache_network_policy")
        val AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB = intPreferencesKey("audio_cache_cellular_monthly_cap_mb")
        val AUDIO_NORMALIZATION_MODE = stringPreferencesKey("audio_normalization_mode")
        val CHANNEL_MIX_MODE = stringPreferencesKey("channel_mix_mode")
        val DREAM_IMAGE_CATEGORIES = stringPreferencesKey("dream_image_categories")
        val DREAM_TRANSITION_STYLE = stringPreferencesKey("dream_transition_style")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val BASS_BOOST_STRENGTH = stringPreferencesKey("bass_boost_strength")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val HOME_ENABLED_SECTION_TYPES = stringPreferencesKey("home_enabled_section_types")
        val HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        val HOME_LIBRARY_SECTION_OVERRIDES = stringPreferencesKey("home_library_section_overrides")
        /** Legacy all-or-nothing "hide library from home" key — kept only to migrate. */
        val HOME_HIDDEN_LIBRARY_SECTION_IDS = stringPreferencesKey("home_hidden_library_section_ids")
        val MPV_CONFIG = stringPreferencesKey("mpv_config")
        val LIBVLC_CONFIG = stringPreferencesKey("libvlc_config")
        val EXO_CONFIG = stringPreferencesKey("exo_config")
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
        val USE_PIN_FOR_PLAYER_LOCK = booleanPreferencesKey("use_pin_for_player_lock")
        val DIALOGUE_BOOST_ENABLED = booleanPreferencesKey("dialogue_boost_enabled")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val AUDIO_PASSTHROUGH = booleanPreferencesKey("audio_passthrough")
        val FRAME_RATE_MATCHING = booleanPreferencesKey("frame_rate_matching")
        val REFRESH_RATE_MODE = stringPreferencesKey("refresh_rate_mode")
        val NIGHT_MODE_ENABLED = booleanPreferencesKey("night_mode_enabled")
        val VIDEO_GESTURES_ENABLED = booleanPreferencesKey("video_gestures_enabled")
        val VIDEO_PASS_OUT_PROTECTION_HOURS = intPreferencesKey("video_pass_out_protection_hours")
        val VIDEO_SKIP_BACK_ON_RESUME_MS = longPreferencesKey("video_skip_back_on_resume_ms")
        val VIDEO_HOLD_SPEED_ENABLED = booleanPreferencesKey("video_hold_speed_enabled")
        val VIDEO_HOLD_SPEED_MULTIPLIER = floatPreferencesKey("video_hold_speed_multiplier")
        val VIDEO_AUTOPLAY_NEXT = booleanPreferencesKey("video_autoplay_next")
        val TRAILER_AUTOPLAY = booleanPreferencesKey("trailer_autoplay")
        val CINEMA_MODE_ENABLED = booleanPreferencesKey("cinema_mode_enabled")
        val VIDEO_REMEMBER_BRIGHTNESS = booleanPreferencesKey("video_remember_brightness")
        val VIDEO_REMEMBER_VOLUME = booleanPreferencesKey("video_remember_volume")
        val VIDEO_VOLUME_LEVEL = floatPreferencesKey("video_volume_level")
        val VIDEO_AUTO_SKIP_INTRO = booleanPreferencesKey("video_auto_skip_intro")
        val VIDEO_AUTO_SKIP_OUTRO = booleanPreferencesKey("video_auto_skip_outro")
        val VIDEO_REMEMBER_MUTED = booleanPreferencesKey("video_remember_muted")
        val VIDEO_MUTED = booleanPreferencesKey("video_muted")
        val SUBTITLE_PREVIEW_IN_SETTINGS = booleanPreferencesKey("subtitle_preview_in_settings")
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
        val HOME_BACKDROP_ENABLED = booleanPreferencesKey("home_backdrop_enabled")
        val NAV_BAR_SHOW_LABELS = booleanPreferencesKey("nav_bar_show_labels")
        val HIDE_BOTTOM_NAV_ON_SCROLL = booleanPreferencesKey("hide_bottom_nav_on_scroll")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        val NEWSLETTER_ENABLED = booleanPreferencesKey("newsletter_enabled")

        val MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
        val AUDIO_NIGHT_MODE_GAIN = intPreferencesKey("audio_night_mode_gain")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_CONNECTIONS = intPreferencesKey("download_connections")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val NEWSLETTER_DAY_OF_WEEK = intPreferencesKey("newsletter_day_of_week")

        val VIDEO_DEFAULT_SPEED = floatPreferencesKey("video_default_speed")
        val VIDEO_BRIGHTNESS_LEVEL = floatPreferencesKey("video_brightness_level")
        val VIDEO_GESTURE_INDICATOR_SIDE = stringPreferencesKey("video_gesture_indicator_side")
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

        val SHOW_ADVANCED_SETTINGS = booleanPreferencesKey("show_advanced_settings")
        val AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey("audio_visualizer_enabled")
        val ENABLED_EXPERIMENTAL_FEATURES = stringPreferencesKey("enabled_experimental_features")

        val SYNC_PLAY_JOIN_BEHAVIOR = stringPreferencesKey("sync_play_join_behavior")
        val SYNC_PLAY_TOLERANCE_MS = longPreferencesKey("sync_play_tolerance_ms")
        val SYNC_PLAY_AUTO_ACCEPT_INVITES = booleanPreferencesKey("sync_play_auto_accept_invites")
        val DEFAULT_CASTING_STRATEGY = stringPreferencesKey("default_casting_strategy")
        val BACKGROUND_CASTING_ENABLED = booleanPreferencesKey("background_casting_enabled")
        val PREFERRED_RENDERER = stringPreferencesKey("preferred_renderer")
        val DVR_PRE_PADDING_MINUTES = intPreferencesKey("dvr_pre_padding_minutes")
        val DVR_POST_PADDING_MINUTES = intPreferencesKey("dvr_post_padding_minutes")
        val DVR_RECORDING_QUALITY = stringPreferencesKey("dvr_recording_quality")
        val FAVORITE_CHANNELS = stringPreferencesKey("favorite_channels")
        val LIVE_TV_LAST_CHANNEL_ID = stringPreferencesKey("live_tv_last_channel_id")
        val ENABLED_NEWSLETTER_SECTIONS = stringPreferencesKey("enabled_newsletter_sections")
        val NEWSLETTER_SECTION_ORDER = stringPreferencesKey("newsletter_section_order")
        val MANUAL_OFFLINE_ENABLED = booleanPreferencesKey("manual_offline_enabled")
        val AUTO_OFFLINE_ENABLED = booleanPreferencesKey("auto_offline_enabled")
        val MANUAL_BANDWIDTH_CAP = longPreferencesKey("manual_bandwidth_cap")
        val METERED_NETWORK_BEHAVIOR = stringPreferencesKey("metered_network_behavior")
        val ADAPTIVE_BITRATE_ENABLED = booleanPreferencesKey("adaptive_bitrate_enabled")
        val BACKGROUND_VIDEO_AUDIO_ENABLED = booleanPreferencesKey("background_video_audio_enabled")
        val AUTO_PLAY_COUNTDOWN_SEC = intPreferencesKey("auto_play_countdown_sec")
        val SHOW_UNWATCHED_BADGE = booleanPreferencesKey("show_unwatched_badge")
        val HIDE_WATCHED_ITEMS = booleanPreferencesKey("hide_watched_items")
        val MERGE_CONTINUE_WATCHING_NEXT_UP = booleanPreferencesKey("merge_continue_watching_next_up")
        val NEXT_UP_MAX_DAYS = intPreferencesKey("next_up_max_days")
        val NEXT_UP_REWATCHING = booleanPreferencesKey("next_up_rewatching")
        val NEXT_UP_EXCLUDED_SERIES_IDS = stringPreferencesKey("next_up_excluded_series_ids")
        val HIDDEN_CW_ITEM_IDS = stringPreferencesKey("hidden_cw_item_ids")
        val PINNED_HOME_SECTIONS = stringPreferencesKey("pinned_home_sections")
        val HOME_LAYOUT_PRESETS = stringPreferencesKey("home_layout_presets")
        val CONTINUE_WATCHING_CLICK_BEHAVIOR = stringPreferencesKey("continue_watching_click_behavior")
        val CELLULAR_STREAMING_QUALITY = stringPreferencesKey("cellular_streaming_quality")
        val SHOW_WATCHED_CHECKMARK = booleanPreferencesKey("show_watched_checkmark")
        val DEFAULT_LIBRARY_SORT_ORDERS = stringPreferencesKey("default_library_sort_orders")
        val LIBRARY_VIEW_MODES = stringPreferencesKey("library_view_modes")
        val LIBRARY_FILTERS = stringPreferencesKey("library_filters")
        val KEEP_SCREEN_ON_DURING_VIDEO = booleanPreferencesKey("keep_screen_on_during_video")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SMART_DOWNLOADS_ENABLED = booleanPreferencesKey("smart_downloads_enabled")
        val AUTO_DOWNLOAD_NEW_EPISODES = booleanPreferencesKey("auto_download_new_episodes")
        val INCOGNITO_MODE_ENABLED = booleanPreferencesKey("incognito_mode_enabled")
        val SHOW_TIME_REMAINING = booleanPreferencesKey("show_time_remaining")
        val SHOW_CLOCK_ON_HOME = booleanPreferencesKey("show_clock_on_home")
        val SHOW_CLOCK_IN_PLAYER = booleanPreferencesKey("show_clock_in_player")
        val SHOW_SETTINGS_IN_HOME_SEARCH = booleanPreferencesKey("show_settings_in_home_search")
        val PAUSE_ON_AUDIO_FOCUS_LOSS = booleanPreferencesKey("pause_on_audio_focus_loss")
        val DUCK_ON_TRANSIENT_FOCUS_LOSS = booleanPreferencesKey("duck_on_transient_focus_loss")
        val VOLUME_BOOST_ENABLED = booleanPreferencesKey("volume_boost_enabled")
        val VOLUME_BOOST_GAIN = intPreferencesKey("volume_boost_gain")
        val AUDIO_LYRICS_VISIBLE = booleanPreferencesKey("audio_lyrics_visible")
        val SHOW_SHARE_MEDIA_OPTION = booleanPreferencesKey("show_share_media_option")
        val SHOW_EXTERNAL_RATINGS = booleanPreferencesKey("show_external_ratings")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
        val VERBOSE_NETWORK_LOGGING = booleanPreferencesKey("verbose_network_logging")
        val NETWORK_TIMEOUT_PRESET = stringPreferencesKey("network_timeout_preset")
        val REDUCE_MOTION_ENABLED = booleanPreferencesKey("reduce_motion_enabled")
        val PREFER_AUDIO_DESCRIPTION = booleanPreferencesKey("prefer_audio_description")
        val HIGH_CONTRAST_SUBTITLES = booleanPreferencesKey("high_contrast_subtitles")
        val HIDE_SEARCH_HISTORY = booleanPreferencesKey("hide_search_history")
        val BLUE_LIGHT_FILTER_ENABLED = booleanPreferencesKey("blue_light_filter_enabled")
        val BLUE_LIGHT_FILTER_STRENGTH = floatPreferencesKey("blue_light_filter_strength")
        val TV_ZOOM_MODE_PERCENT = floatPreferencesKey("tv_zoom_mode_percent")
        val REMOTE_CONTROL_ENABLED = booleanPreferencesKey("remote_control_enabled")
        val MAX_DOWNLOAD_STORAGE_GB = intPreferencesKey("max_download_storage_gb")
        val DOWNLOAD_STORAGE_LOCATION = stringPreferencesKey("download_storage_location")
        val ANDROID_TV_WATCH_NEXT_ENABLED = booleanPreferencesKey("android_tv_watch_next_enabled")
        val USER_DATA_SYNC_ENABLED = booleanPreferencesKey("user_data_sync_enabled")
        val SYNTHWAVE_MODE = booleanPreferencesKey("synthwave_mode")
        val SYNTHWAVE_ACCENT = stringPreferencesKey("synthwave_accent")
        val SOOTHING_MODE = booleanPreferencesKey("soothing_mode")
        val SOOTHING_ACCENT = stringPreferencesKey("soothing_accent")
        val MONOCHROME_MODE = booleanPreferencesKey("monochrome_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val PGS_SUBTITLE_DIRECT_PLAY = booleanPreferencesKey("pgs_subtitle_direct_play")
        val HDR_SUBTITLE_STYLE_ENABLED = booleanPreferencesKey("hdr_subtitle_style_enabled")
        val HDR_SUBTITLE_STYLE = stringPreferencesKey("hdr_subtitle_style")
        val BACKDROP_THEME_MUSIC_ENABLED = booleanPreferencesKey("backdrop_theme_music_enabled")
        val HIDDEN_NAV_ITEMS = stringPreferencesKey("hidden_nav_items")
        val NAV_ITEM_ORDER = stringPreferencesKey("nav_item_order")
        val SELF_UPDATE_CHECK_ENABLED = booleanPreferencesKey("self_update_check_enabled")
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKOUT_UNTIL_MS = longPreferencesKey("pin_lockout_until_ms")
        val HIDE_EPISODE_THUMBNAILS = booleanPreferencesKey("hide_episode_thumbnails")
        val EPISODES_DESCENDING = booleanPreferencesKey("episodes_descending")
        val SKIP_SPECIALS = booleanPreferencesKey("skip_specials")
        val CELLULAR_DOWNLOAD_SIZE_WARNING_MB = intPreferencesKey("cellular_download_size_warning_mb")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DATE_FORMAT_PREFERENCE = stringPreferencesKey("date_format_preference")
        val APP_FONT_SCALE = stringPreferencesKey("app_font_scale")
        val SCHEDULED_THEME_START_HOUR = intPreferencesKey("scheduled_theme_start_hour")
        val SCHEDULED_THEME_END_HOUR = intPreferencesKey("scheduled_theme_end_hour")
        val COLOR_BLIND_MODE = stringPreferencesKey("color_blind_mode")
        val HAND_MODE = stringPreferencesKey("hand_mode")
        val DOWNLOAD_SCHEDULE_ENABLED = booleanPreferencesKey("download_schedule_enabled")
        val DOWNLOAD_SCHEDULE_START = intPreferencesKey("download_schedule_start")
        val DOWNLOAD_SCHEDULE_END = intPreferencesKey("download_schedule_end")
        val DOWNLOAD_SCHEDULE_WIFI_ONLY = booleanPreferencesKey("download_schedule_wifi_only")
    }

    private companion object {
        private val ENCODE_DEFAULTS_JSON = Json { encodeDefaults = true }
    }

    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch { migrateToTypedKeys() }
    }

    private suspend fun migrateToTypedKeys() {
        dataStore.edit { prefs ->
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
                "virtualizer_enabled", "auto_eq_by_genre", "home_hero_enabled", "home_backdrop_enabled",
                "nav_bar_show_labels", "onboarding_completed", "performance_mode",
                "newsletter_enabled", "wifi_only_downloads", "monochrome_mode",
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
            val legacy = prefs.legacyString(name) ?: continue
            prefs[booleanPreferencesKey(name)] = legacy.toBoolean()
        }
    }

    private fun migrateInts(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            legacy.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
        }
    }

    private fun migrateFloats(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            legacy.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it }
        }
    }

    private fun migrateLongs(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            legacy.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
        }
    }

    /**
     * Reads a legacy string slot, tolerating a typed value (Boolean/Int/...)
     * already living under [name] — e.g. after `clearAllPreferences` preserved
     * some typed state but reset the migration flag. Returns null when the slot
     * is absent or holds a non-string value, so callers `?: continue`.
     */
    private fun MutablePreferences.legacyString(name: String): String? =
        try { this[stringPreferencesKey(name)] } catch (_: ClassCastException) { null }


    private fun readBool(prefs: Preferences, key: Preferences.Key<Boolean>, name: String, default: Boolean): Boolean {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        // Once the one-shot typed-key migration has run, the legacy string-key
        // fallback is no longer needed — every key was rewritten in place — so
        // skip the extra string lookup on every preference emission.
        if (typed != null || prefs[Keys.TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toBoolean() ?: default
        }
        return typed ?: default
    }

    /**
     * Reads [UserPreferences.playbackMode]. Migrates the legacy boolean
     * `force_direct_play` key when the new enum key is absent: a legacy
     * value of `true` (the historical default) maps to
     * [PlaybackMode.FORCE_DIRECT_PLAY] to preserve the prior behaviour of
     * always requesting a static stream; `false` maps to
     * [PlaybackMode.AUTO] so the server negotiates the best method.
     */
    private fun readPlaybackMode(prefs: Preferences): PlaybackMode {
        prefs[Keys.PLAYBACK_MODE]?.let { raw ->
            return try { PlaybackMode.valueOf(raw) } catch (_: Exception) { PlaybackMode.AUTO }
        }
        val legacyForce = readBool(prefs, Keys.FORCE_DIRECT_PLAY, "force_direct_play", true)
        return if (legacyForce) PlaybackMode.FORCE_DIRECT_PLAY else PlaybackMode.AUTO
    }

    private fun readInt(prefs: Preferences, key: Preferences.Key<Int>, name: String, default: Int): Int {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        if (typed != null || prefs[Keys.TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toIntOrNull() ?: default
        }
        return typed ?: default
    }

    private fun readFloat(prefs: Preferences, key: Preferences.Key<Float>, name: String, default: Float): Float {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        if (typed != null || prefs[Keys.TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toFloatOrNull() ?: default
        }
        return typed ?: default
    }

    private fun readLong(prefs: Preferences, key: Preferences.Key<Long>, name: String, default: Long): Long {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        if (typed != null || prefs[Keys.TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toLongOrNull() ?: default
        }
        return typed ?: default
    }

    private fun readMediaStreamSelections(prefs: Preferences): Map<String, MediaStreamSelection> {
        val raw = prefs[Keys.MEDIA_STREAM_SELECTIONS] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, MediaStreamSelection>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readVideoEffectsByItem(prefs: Preferences): Map<String, VideoEffectsConfig> {
        val raw = prefs[Keys.VIDEO_EFFECTS_SELECTIONS] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, VideoEffectsConfig>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readSegmentBehaviors(prefs: Preferences): Map<MediaSegmentType, SegmentBehavior> {
        val raw = prefs[Keys.SEGMENT_BEHAVIORS]
        if (raw != null) {
            return try {
                val stored = json.decodeFromString<Map<String, String>>(raw)
                val parsed = stored.mapNotNull { (typeStr, behaviorStr) ->
                    try {
                        MediaSegmentType.valueOf(typeStr) to SegmentBehavior.valueOf(behaviorStr)
                    } catch (_: Exception) { null }
                }.toMap()
                // Merge: defaults fill in any types not explicitly saved, stored values override
                SegmentBehavior.DEFAULT_BEHAVIORS + parsed
            } catch (_: Exception) { SegmentBehavior.DEFAULT_BEHAVIORS }
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
        dataStore.edit { prefs ->
            val current = readMediaStreamSelections(prefs).toMutableMap()
            if (audioStreamIndex == null && subtitleStreamIndex == null) {
                current.remove(itemId)
            } else {
                current[itemId] = MediaStreamSelection(
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                )
            }
            if (current.size > 100) {
                val excess = current.size - 100
                current.keys.take(excess).forEach { current.remove(it) }
            }
            prefs[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(current)
        }
    }

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

    private suspend fun writeVideoEffectsForItem(itemId: String, effects: VideoEffectsConfig) {
        dataStore.edit { prefs ->
            val current = readVideoEffectsByItem(prefs).toMutableMap()
            if (effects.isNeutral) {
                current.remove(itemId)
            } else {
                current[itemId] = effects
            }
            // Match the MediaStreamSelection LRU cap so per-item state stays bounded.
            if (current.size > 100) {
                val excess = current.size - 100
                current.keys.take(excess).forEach { current.remove(it) }
            }
            prefs[Keys.VIDEO_EFFECTS_SELECTIONS] = json.encodeToString(current)
        }
    }

    private data class ParsedCache<T>(
        val raw: String?,
        val value: T,
    )

    private var cachedSubtitleStyle: ParsedCache<SubtitleStyle?> = ParsedCache(null, null)
    private var cachedHdrSubtitleStyle: ParsedCache<SubtitleStyle?> = ParsedCache(null, null)
    private var cachedEqualizerSettings: ParsedCache<EqualizerSettings?> = ParsedCache(null, null)
    private var cachedMpvConfig: ParsedCache<MpvEngineConfig> = ParsedCache(null, MpvEngineConfig())
    private var cachedLibVlcConfig: ParsedCache<LibVlcEngineConfig> = ParsedCache(null, LibVlcEngineConfig())
    private var cachedExoPlayerConfig: ParsedCache<ExoPlayerEngineConfig> = ParsedCache(null, ExoPlayerEngineConfig())
    private var cachedDreamImageCategories: ParsedCache<Set<DreamImageCategory>> = ParsedCache(null, setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES))
    private var cachedEnabledHomeSectionTypes: ParsedCache<Set<HomeSectionType>> = ParsedCache(null, HomeSectionType.CONFIGURABLE.toSet())
    private var cachedHomeSectionOrder: ParsedCache<List<HomeSectionType>> = ParsedCache(null, HomeSectionType.CONFIGURABLE)
    private var cachedLibraryHomeSectionOverrides: ParsedCache<Map<String, Set<HomeSectionType>>> = ParsedCache(null, emptyMap())
    private var cachedMediaStreamSelections: ParsedCache<Map<String, MediaStreamSelection>> = ParsedCache(null, emptyMap())
    private var cachedVideoEffectsByItem: ParsedCache<Map<String, VideoEffectsConfig>> = ParsedCache(null, emptyMap())
    private var cachedSubtitleDelayByItem: ParsedCache<Map<String, Long>> = ParsedCache(null, emptyMap())
    private var cachedSegmentBehaviors: ParsedCache<Map<MediaSegmentType, SegmentBehavior>> = ParsedCache(null, SegmentBehavior.DEFAULT_BEHAVIORS)
    private var cachedNotificationLibraryConfigs: ParsedCache<Map<String, LibraryNotificationConfig>> = ParsedCache(null, emptyMap())
    private var cachedEnabledExperimentalFeatures: ParsedCache<Set<com.raulshma.jellyplay.core.model.ExperimentalFeature>> = ParsedCache(null, emptySet())
    private var cachedFavoriteChannels: ParsedCache<Set<String>> = ParsedCache(null, emptySet())
    private var cachedEnabledNewsletterSections: ParsedCache<Set<NewsletterSectionType>> = ParsedCache(null, NewsletterSectionType.entries.toSet())
    private var cachedNewsletterSectionOrder: ParsedCache<List<NewsletterSectionType>> = ParsedCache(null, NewsletterSectionType.DEFAULT_ORDER)
    private var cachedNextUpExcludedSeriesIds: ParsedCache<Set<String>> = ParsedCache(null, emptySet())
    private var cachedHiddenCwItemIds: ParsedCache<Set<String>> = ParsedCache(null, emptySet())
    private var cachedPinnedHomeSections: ParsedCache<List<PinnedHomeSection>> = ParsedCache(null, emptyList())
    private var cachedHomeLayoutPresets: ParsedCache<List<HomeLayoutPreset>> = ParsedCache(null, emptyList())
    private var cachedDefaultLibrarySortOrders: ParsedCache<Map<String, String>> = ParsedCache(null, emptyMap())
    private var cachedLibraryViewModes: ParsedCache<Map<String, String>> = ParsedCache(null, emptyMap())
    private var cachedLibraryFilters: ParsedCache<Map<String, String>> = ParsedCache(null, emptyMap())
    private var cachedHiddenNavItems: ParsedCache<Set<String>> = ParsedCache(null, emptySet())
    private var cachedNavItemOrder: ParsedCache<List<String>> = ParsedCache(null, emptyList())

    val preferences: StateFlow<UserPreferences> = sharedPrefs.map { prefs ->
        val subtitleStyleRaw = prefs[Keys.SUBTITLE_STYLE]
        val subtitleStyle = if (subtitleStyleRaw != cachedSubtitleStyle.raw) {
            try {
                subtitleStyleRaw?.let { json.decodeFromString<SubtitleStyle>(it) }
            } catch (_: Exception) { null }.also { cachedSubtitleStyle = ParsedCache(subtitleStyleRaw, it) }
        } else cachedSubtitleStyle.value

        val hdrSubtitleStyleRaw = prefs[Keys.HDR_SUBTITLE_STYLE]
        val hdrSubtitleStyleParsed = if (hdrSubtitleStyleRaw != cachedHdrSubtitleStyle.raw) {
            try {
                hdrSubtitleStyleRaw?.let { json.decodeFromString<SubtitleStyle>(it) }
            } catch (_: Exception) { null }.also { cachedHdrSubtitleStyle = ParsedCache(hdrSubtitleStyleRaw, it) }
        } else cachedHdrSubtitleStyle.value

        val equalizerSettingsRaw = prefs[Keys.EQUALIZER_SETTINGS]
        val equalizerSettings = if (equalizerSettingsRaw != cachedEqualizerSettings.raw) {
            try {
                equalizerSettingsRaw?.let { json.decodeFromString<EqualizerSettings>(it) }
            } catch (_: Exception) { null }.also { cachedEqualizerSettings = ParsedCache(equalizerSettingsRaw, it) }
        } else cachedEqualizerSettings.value

        val mpvConfigRaw = prefs[Keys.MPV_CONFIG]
        val mpvConfig = if (mpvConfigRaw != cachedMpvConfig.raw) {
            try {
                mpvConfigRaw?.let { json.decodeFromString<MpvEngineConfig>(it) } ?: MpvEngineConfig()
            } catch (_: Exception) { MpvEngineConfig() }.also { cachedMpvConfig = ParsedCache(mpvConfigRaw, it) }
        } else cachedMpvConfig.value

        val libVlcConfigRaw = prefs[Keys.LIBVLC_CONFIG]
        val libVlcConfig = if (libVlcConfigRaw != cachedLibVlcConfig.raw) {
            try {
                libVlcConfigRaw?.let { json.decodeFromString<LibVlcEngineConfig>(it) } ?: LibVlcEngineConfig()
            } catch (_: Exception) { LibVlcEngineConfig() }.also { cachedLibVlcConfig = ParsedCache(libVlcConfigRaw, it) }
        } else cachedLibVlcConfig.value

        val exoPlayerConfigRaw = prefs[Keys.EXO_CONFIG]
        val exoPlayerConfig = if (exoPlayerConfigRaw != cachedExoPlayerConfig.raw) {
            try {
                exoPlayerConfigRaw?.let { json.decodeFromString<ExoPlayerEngineConfig>(it) } ?: ExoPlayerEngineConfig()
            } catch (_: Exception) { ExoPlayerEngineConfig() }.also { cachedExoPlayerConfig = ParsedCache(exoPlayerConfigRaw, it) }
        } else cachedExoPlayerConfig.value

        val dreamImageCategoriesRaw = prefs[Keys.DREAM_IMAGE_CATEGORIES]
        val dreamImageCategories = if (dreamImageCategoriesRaw != cachedDreamImageCategories.raw) {
            try {
                dreamImageCategoriesRaw?.let { json.decodeFromString<Set<DreamImageCategory>>(it) }
                    ?: setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)
            } catch (_: Exception) { setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES) }
                .also { cachedDreamImageCategories = ParsedCache(dreamImageCategoriesRaw, it) }
        } else cachedDreamImageCategories.value

        val enabledHomeSectionTypesRaw = prefs[Keys.HOME_ENABLED_SECTION_TYPES]
        val enabledHomeSectionTypes = if (enabledHomeSectionTypesRaw != cachedEnabledHomeSectionTypes.raw) {
            try {
                enabledHomeSectionTypesRaw?.let {
                    json.decodeFromString<Set<String>>(it)
                        .mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                        .toSet()
                } ?: HomeSectionType.CONFIGURABLE.toSet()
            } catch (_: Exception) { HomeSectionType.CONFIGURABLE.toSet() }
                .also { cachedEnabledHomeSectionTypes = ParsedCache(enabledHomeSectionTypesRaw, it) }
        } else cachedEnabledHomeSectionTypes.value

        val homeSectionOrderRaw = prefs[Keys.HOME_SECTION_ORDER]
        val homeSectionOrder = if (homeSectionOrderRaw != cachedHomeSectionOrder.raw) {
            try {
                homeSectionOrderRaw?.let {
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
            } catch (_: Exception) { HomeSectionType.CONFIGURABLE }
                .also { cachedHomeSectionOrder = ParsedCache(homeSectionOrderRaw, it) }
        } else cachedHomeSectionOrder.value

        val libraryHomeSectionOverridesRaw = prefs[Keys.HOME_LIBRARY_SECTION_OVERRIDES]
        val libraryHomeSectionOverrides = if (libraryHomeSectionOverridesRaw != cachedLibraryHomeSectionOverrides.raw) {
            try {
                libraryHomeSectionOverridesRaw?.let {
                    json.decodeFromString<Map<String, Set<HomeSectionType>>>(it)
                } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedLibraryHomeSectionOverrides = ParsedCache(libraryHomeSectionOverridesRaw, it) }
        } else cachedLibraryHomeSectionOverrides.value

        // One-shot migration: a prior version stored an all-or-nothing
        // "hide library from home" Set<String>. Migrate each id to
        // {LATEST_MEDIA, RECENTLY_ADDED} disabled, then drop the legacy key.
        val legacyHiddenLibraryIdsRaw = prefs[Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS]
        val libraryHomeSectionOverridesMigrated = if (
            legacyHiddenLibraryIdsRaw != null && libraryHomeSectionOverrides.isEmpty()
        ) {
            try {
                val legacyIds = json.decodeFromString<Set<String>>(legacyHiddenLibraryIdsRaw)
                if (legacyIds.isNotEmpty()) {
                    val migrated = legacyIds.associateWith {
                        setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED)
                    }
                    dataStore.edit {
                        it.remove(Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS)
                        it[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(migrated)
                    }
                    migrated
                } else libraryHomeSectionOverrides
            } catch (_: Exception) { libraryHomeSectionOverrides }
        } else libraryHomeSectionOverrides

        val mediaStreamSelectionsRaw = prefs[Keys.MEDIA_STREAM_SELECTIONS]
        val mediaStreamSelections = if (mediaStreamSelectionsRaw != cachedMediaStreamSelections.raw) {
            try {
                mediaStreamSelectionsRaw?.let { json.decodeFromString<Map<String, MediaStreamSelection>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedMediaStreamSelections = ParsedCache(mediaStreamSelectionsRaw, it) }
        } else cachedMediaStreamSelections.value

        val videoEffectsByItemRaw = prefs[Keys.VIDEO_EFFECTS_SELECTIONS]
        val videoEffectsByItem = if (videoEffectsByItemRaw != cachedVideoEffectsByItem.raw) {
            try {
                videoEffectsByItemRaw?.let { json.decodeFromString<Map<String, VideoEffectsConfig>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedVideoEffectsByItem = ParsedCache(videoEffectsByItemRaw, it) }
        } else cachedVideoEffectsByItem.value

        val subtitleDelayByItemRaw = prefs[Keys.SUBTITLE_DELAY_BY_ITEM]
        val subtitleDelayByItem = if (subtitleDelayByItemRaw != cachedSubtitleDelayByItem.raw) {
            try {
                subtitleDelayByItemRaw?.let { json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedSubtitleDelayByItem = ParsedCache(subtitleDelayByItemRaw, it) }
        } else cachedSubtitleDelayByItem.value

        val segmentBehaviorsRaw = prefs[Keys.SEGMENT_BEHAVIORS]
        val segmentBehaviors = if (segmentBehaviorsRaw != cachedSegmentBehaviors.raw) {
            readSegmentBehaviors(prefs).also { cachedSegmentBehaviors = ParsedCache(segmentBehaviorsRaw, it) }
        } else cachedSegmentBehaviors.value

        val notificationLibraryConfigsRaw = prefs[Keys.NOTIFICATIONS_LIBRARY_CONFIGS]
        val notificationLibraryConfigs = if (notificationLibraryConfigsRaw != cachedNotificationLibraryConfigs.raw) {
            try {
                notificationLibraryConfigsRaw?.let {
                    json.decodeFromString<Map<String, LibraryNotificationConfig>>(it)
                } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedNotificationLibraryConfigs = ParsedCache(notificationLibraryConfigsRaw, it) }
        } else cachedNotificationLibraryConfigs.value

        val enabledExperimentalFeaturesRaw = prefs[Keys.ENABLED_EXPERIMENTAL_FEATURES]
        val enabledExperimentalFeatures = if (enabledExperimentalFeaturesRaw != cachedEnabledExperimentalFeatures.raw) {
            try {
                enabledExperimentalFeaturesRaw?.let {
                    json.decodeFromString<Set<String>>(it)
                        .mapNotNull { name ->
                            com.raulshma.jellyplay.core.model.ExperimentalFeature.entries.find { e -> e.name == name }
                        }
                        .toSet()
                } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedEnabledExperimentalFeatures = ParsedCache(enabledExperimentalFeaturesRaw, it) }
        } else cachedEnabledExperimentalFeatures.value

        val favoriteChannelsRaw = prefs[Keys.FAVORITE_CHANNELS]
        val favoriteChannels = if (favoriteChannelsRaw != cachedFavoriteChannels.raw) {
            try {
                favoriteChannelsRaw?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedFavoriteChannels = ParsedCache(favoriteChannelsRaw, it) }
        } else cachedFavoriteChannels.value

        val enabledNewsletterSectionsRaw = prefs[Keys.ENABLED_NEWSLETTER_SECTIONS]
        val enabledNewsletterSections = if (enabledNewsletterSectionsRaw != cachedEnabledNewsletterSections.raw) {
            try {
                enabledNewsletterSectionsRaw?.let { json.decodeFromString<Set<NewsletterSectionType>>(it) }
                    ?: NewsletterSectionType.entries.toSet()
            } catch (_: Exception) { NewsletterSectionType.entries.toSet() }
                .also { cachedEnabledNewsletterSections = ParsedCache(enabledNewsletterSectionsRaw, it) }
        } else cachedEnabledNewsletterSections.value

        val newsletterSectionOrderRaw = prefs[Keys.NEWSLETTER_SECTION_ORDER]
        val newsletterSectionOrder = if (newsletterSectionOrderRaw != cachedNewsletterSectionOrder.raw) {
            try {
                newsletterSectionOrderRaw?.let { json.decodeFromString<List<NewsletterSectionType>>(it) }
                    ?: NewsletterSectionType.DEFAULT_ORDER
            } catch (_: Exception) { NewsletterSectionType.DEFAULT_ORDER }
                .also { cachedNewsletterSectionOrder = ParsedCache(newsletterSectionOrderRaw, it) }
        } else cachedNewsletterSectionOrder.value

        val nextUpExcludedSeriesIdsRaw = prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS]
        val nextUpExcludedSeriesIds = if (nextUpExcludedSeriesIdsRaw != cachedNextUpExcludedSeriesIds.raw) {
            try {
                nextUpExcludedSeriesIdsRaw?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedNextUpExcludedSeriesIds = ParsedCache(nextUpExcludedSeriesIdsRaw, it) }
        } else cachedNextUpExcludedSeriesIds.value

        val hiddenCwItemIdsRaw = prefs[Keys.HIDDEN_CW_ITEM_IDS]
        val hiddenCwItemIds = if (hiddenCwItemIdsRaw != cachedHiddenCwItemIds.raw) {
            try {
                hiddenCwItemIdsRaw?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedHiddenCwItemIds = ParsedCache(hiddenCwItemIdsRaw, it) }
        } else cachedHiddenCwItemIds.value

        val pinnedHomeSectionsRaw = prefs[Keys.PINNED_HOME_SECTIONS]
        val pinnedHomeSections = if (pinnedHomeSectionsRaw != cachedPinnedHomeSections.raw) {
            try {
                pinnedHomeSectionsRaw?.let { json.decodeFromString<List<PinnedHomeSection>>(it) } ?: emptyList()
            } catch (_: Exception) { emptyList() }
                .also { cachedPinnedHomeSections = ParsedCache(pinnedHomeSectionsRaw, it) }
        } else cachedPinnedHomeSections.value

        val homeLayoutPresetsRaw = prefs[Keys.HOME_LAYOUT_PRESETS]
        val homeLayoutPresets = if (homeLayoutPresetsRaw != cachedHomeLayoutPresets.raw) {
            try {
                homeLayoutPresetsRaw?.let { json.decodeFromString<List<HomeLayoutPreset>>(it) } ?: emptyList()
            } catch (_: Exception) { emptyList() }
                .also { cachedHomeLayoutPresets = ParsedCache(homeLayoutPresetsRaw, it) }
        } else cachedHomeLayoutPresets.value

        val defaultLibrarySortOrdersRaw = prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS]
        val defaultLibrarySortOrders = if (defaultLibrarySortOrdersRaw != cachedDefaultLibrarySortOrders.raw) {
            try {
                defaultLibrarySortOrdersRaw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedDefaultLibrarySortOrders = ParsedCache(defaultLibrarySortOrdersRaw, it) }
        } else cachedDefaultLibrarySortOrders.value

        val libraryViewModesRaw = prefs[Keys.LIBRARY_VIEW_MODES]
        val libraryViewModes = if (libraryViewModesRaw != cachedLibraryViewModes.raw) {
            try {
                libraryViewModesRaw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedLibraryViewModes = ParsedCache(libraryViewModesRaw, it) }
        } else cachedLibraryViewModes.value

        val libraryFiltersRaw = prefs[Keys.LIBRARY_FILTERS]
        val libraryFilters = if (libraryFiltersRaw != cachedLibraryFilters.raw) {
            try {
                libraryFiltersRaw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedLibraryFilters = ParsedCache(libraryFiltersRaw, it) }
        } else cachedLibraryFilters.value

        val hiddenNavItemsRaw = prefs[Keys.HIDDEN_NAV_ITEMS]
        val hiddenNavItems = if (hiddenNavItemsRaw != cachedHiddenNavItems.raw) {
            try {
                hiddenNavItemsRaw?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedHiddenNavItems = ParsedCache(hiddenNavItemsRaw, it) }
        } else cachedHiddenNavItems.value

        val navItemOrderRaw = prefs[Keys.NAV_ITEM_ORDER]
        val navItemOrder = if (navItemOrderRaw != cachedNavItemOrder.raw) {
            try {
                navItemOrderRaw?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            } catch (_: Exception) { emptyList() }
                .also { cachedNavItemOrder = ParsedCache(navItemOrderRaw, it) }
        } else cachedNavItemOrder.value

        UserPreferences(
            preferredPlayer = try {
                PlayerType.fromStoredName(prefs[Keys.PREFERRED_PLAYER] ?: PlayerType.EXO_PLAYER.name)
            } catch (_: Exception) { PlayerType.EXO_PLAYER },
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANG],
            subtitlesForcedOnly = readBool(prefs, Keys.SUBTITLES_FORCED_ONLY, "subtitles_forced_only", false),
            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG],
            mediaStreamSelections = mediaStreamSelections,
            videoEffectsByItem = videoEffectsByItem,
            subtitleDelayByItem = subtitleDelayByItem,
            dynamicTheming = readBool(prefs, Keys.DYNAMIC_THEMING, "dynamic_theming", true),
            themeMode = try {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            } catch (_: Exception) { ThemeMode.SYSTEM },
            contrastLevel = try {
                ContrastLevel.valueOf(prefs[Keys.CONTRAST_LEVEL] ?: ContrastLevel.DEFAULT.name)
            } catch (_: Exception) { ContrastLevel.DEFAULT },
            oledMode = readBool(prefs, Keys.OLED_MODE, "oled_mode", false),
            subtitleStyle = subtitleStyle ?: SubtitleStyle(),
            hdrSubtitleStyleEnabled = readBool(prefs, Keys.HDR_SUBTITLE_STYLE_ENABLED, "hdr_subtitle_style_enabled", false),
            hdrSubtitleStyle = hdrSubtitleStyleParsed ?: SubtitleStyle(
                fontSize = 28,
                backgroundOpacity = 0.5f,
                edgeType = SubtitleEdgeType.OUTLINE,
            ),
            streamingQuality = try {
                StreamingQuality.valueOf(prefs[Keys.STREAMING_QUALITY] ?: StreamingQuality.AUTO.name)
            } catch (_: Exception) { StreamingQuality.AUTO },
            playbackMode = readPlaybackMode(prefs),
            liveStreamOption = try {
                LiveStreamOption.valueOf(prefs[Keys.LIVE_STREAM_OPTION] ?: LiveStreamOption.AUTO.name)
            } catch (_: Exception) { LiveStreamOption.AUTO },
            maxCacheSizeMb = readInt(prefs, Keys.MAX_CACHE_SIZE_MB, "max_cache_size_mb", 0),
            autoDeleteCache = readBool(prefs, Keys.AUTO_DELETE_CACHE, "auto_delete_cache", true),
            pinLockEnabled = readBool(prefs, Keys.PIN_LOCK_ENABLED, "pin_lock_enabled", false),
            pinHash = prefs[Keys.PIN_HASH],
            biometricLockEnabled = readBool(prefs, Keys.BIOMETRIC_LOCK_ENABLED, "biometric_lock_enabled", false),
            usePinForPlayerLock = readBool(prefs, Keys.USE_PIN_FOR_PLAYER_LOCK, "use_pin_for_player_lock", false),
            autoLockTimerMs = readLong(prefs, Keys.AUTO_LOCK_TIMER_MS, "auto_lock_timer_ms", 30_000L),
            pinFailedAttempts = prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0,
            pinLockoutUntilEpochMs = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L,
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
            refreshRateMode = try {
                com.raulshma.jellyplay.core.model.RefreshRateMode.valueOf(
                    prefs[Keys.REFRESH_RATE_MODE] ?: com.raulshma.jellyplay.core.model.RefreshRateMode.OFF.name
                )
            } catch (_: Exception) {
                // Legacy migration: a user with the old boolean on but no mode
                // stored is mapped to FRAME_RATE_ONLY (the old behaviour).
                if (readBool(prefs, Keys.FRAME_RATE_MATCHING, "frame_rate_matching", false)) {
                    com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_ONLY
                } else {
                    com.raulshma.jellyplay.core.model.RefreshRateMode.OFF
                }
            },
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
            videoPassOutProtectionHours = readInt(prefs, Keys.VIDEO_PASS_OUT_PROTECTION_HOURS, "video_pass_out_protection_hours", 0),
            videoSkipBackOnResumeMs = readLong(prefs, Keys.VIDEO_SKIP_BACK_ON_RESUME_MS, "video_skip_back_on_resume_ms", 0L),
            videoHoldSpeedEnabled = readBool(prefs, Keys.VIDEO_HOLD_SPEED_ENABLED, "video_hold_speed_enabled", true),
            videoHoldSpeedMultiplier = readFloat(prefs, Keys.VIDEO_HOLD_SPEED_MULTIPLIER, "video_hold_speed_multiplier", 2.0f),
            videoDefaultSpeed = readFloat(prefs, Keys.VIDEO_DEFAULT_SPEED, "video_default_speed", 1.0f),
            videoDefaultAspectRatio = prefs[Keys.VIDEO_DEFAULT_ASPECT_RATIO] ?: "AUTO",
            videoAutoplayNext = readBool(prefs, Keys.VIDEO_AUTOPLAY_NEXT, "video_autoplay_next", true),
            trailerAutoplay = readBool(prefs, Keys.TRAILER_AUTOPLAY, "trailer_autoplay", true),
            cinemaModeEnabled = readBool(prefs, Keys.CINEMA_MODE_ENABLED, "cinema_mode_enabled", false),
            videoSwipeSeekMaxMs = readLong(prefs, Keys.VIDEO_SWIPE_SEEK_MAX_MS, "video_swipe_seek_max_ms", 120_000L),
            videoRememberBrightness = readBool(prefs, Keys.VIDEO_REMEMBER_BRIGHTNESS, "video_remember_brightness", true),
            videoBrightnessLevel = readFloat(prefs, Keys.VIDEO_BRIGHTNESS_LEVEL, "video_brightness_level", 0.5f),
            videoRememberVolume = readBool(prefs, Keys.VIDEO_REMEMBER_VOLUME, "video_remember_volume", true),
            videoVolumeLevel = readFloat(prefs, Keys.VIDEO_VOLUME_LEVEL, "video_volume_level", 1.0f),
            videoAutoSkipIntro = readBool(prefs, Keys.VIDEO_AUTO_SKIP_INTRO, "video_auto_skip_intro", false),
            videoAutoSkipOutro = readBool(prefs, Keys.VIDEO_AUTO_SKIP_OUTRO, "video_auto_skip_outro", false),
            videoRememberMuted = readBool(prefs, Keys.VIDEO_REMEMBER_MUTED, "video_remember_muted", true),
            videoMuted = readBool(prefs, Keys.VIDEO_MUTED, "video_muted", false),
            subtitlePreviewInSettings = readBool(prefs, Keys.SUBTITLE_PREVIEW_IN_SETTINGS, "subtitle_preview_in_settings", true),
            videoGestureIndicatorSide = try {
                GestureIndicatorSide.valueOf(prefs[Keys.VIDEO_GESTURE_INDICATOR_SIDE] ?: GestureIndicatorSide.OPPOSITE.name)
            } catch (_: Exception) { GestureIndicatorSide.OPPOSITE },
            audioDefaultSpeed = readFloat(prefs, Keys.AUDIO_DEFAULT_SPEED, "audio_default_speed", 1.0f),
            audioNightModeVolume = readFloat(prefs, Keys.AUDIO_NIGHT_MODE_VOLUME, "audio_night_mode_volume", 0.4f),
            audioNightModeGain = readInt(prefs, Keys.AUDIO_NIGHT_MODE_GAIN, "audio_night_mode_gain", 1200),
            audioSkipPreviousThresholdMs = readLong(prefs, Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS, "audio_skip_previous_threshold_ms", 3_000L),
            audioAutoplayNext = readBool(prefs, Keys.AUDIO_AUTOPLAY_NEXT, "audio_autoplay_next", true),
            trickplayEnabled = readBool(prefs, Keys.TRICKPLAY_ENABLED, "trickplay_enabled", true),
            trickplayOnSeekGesture = readBool(prefs, Keys.TRICKPLAY_ON_SEEK_GESTURE, "trickplay_on_seek_gesture", true),
            segmentBehaviors = segmentBehaviors,
            videoEpisodeBrowserEnabled = readBool(prefs, Keys.VIDEO_EPISODE_BROWSER_ENABLED, "video_episode_browser_enabled", true),
            videoShowPlaybackMetadata = readBool(prefs, Keys.VIDEO_SHOW_PLAYBACK_METADATA, "video_show_playback_metadata", true),
            videoPreloadBufferSize = try {
                PreloadBufferSize.valueOf(prefs[Keys.VIDEO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
            } catch (_: Exception) { PreloadBufferSize.MEDIUM },
            audioPreloadBufferSize = try {
                PreloadBufferSize.valueOf(prefs[Keys.AUDIO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
            } catch (_: Exception) { PreloadBufferSize.MEDIUM },
            audioCachingEnabled = prefs[Keys.AUDIO_CACHING_ENABLED] ?: true,
            audioCacheSizeMb = prefs[Keys.AUDIO_CACHE_SIZE_MB] ?: 1024,
            audioPrefetchLookahead = prefs[Keys.AUDIO_PREFETCH_LOOKAHEAD] ?: 3,
            audioPrefetchBackfill = prefs[Keys.AUDIO_PREFETCH_BACKFILL] ?: 5,
            audioCacheNetworkPolicy = try {
                AudioCacheNetworkPolicy.valueOf(
                    prefs[Keys.AUDIO_CACHE_NETWORK_POLICY] ?: AudioCacheNetworkPolicy.DEFAULT.name
                )
            } catch (_: Exception) { AudioCacheNetworkPolicy.DEFAULT },
            audioCacheCellularMonthlyCapMb = prefs[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] ?: 500,
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
            dreamImageCategories = dreamImageCategories,
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
            maxConcurrentDownloads = readInt(prefs, Keys.MAX_CONCURRENT_DOWNLOADS, "max_concurrent_downloads", 3)
                .coerceIn(1, 6),
            enabledHomeSectionTypes = enabledHomeSectionTypes,
            homeSectionOrder = homeSectionOrder,
            libraryHomeSectionOverrides = libraryHomeSectionOverridesMigrated,
            navBarShowLabels = readBool(prefs, Keys.NAV_BAR_SHOW_LABELS, "nav_bar_show_labels", true),
            hideBottomNavOnScroll = readBool(prefs, Keys.HIDE_BOTTOM_NAV_ON_SCROLL, "hide_bottom_nav_on_scroll", true),
            homeHeroEnabled = readBool(prefs, Keys.HOME_HERO_ENABLED, "home_hero_enabled", true),
            homeBackdropEnabled = readBool(prefs, Keys.HOME_BACKDROP_ENABLED, "home_backdrop_enabled", true),
            onboardingCompleted = readBool(prefs, Keys.ONBOARDING_COMPLETED, "onboarding_completed", false),
            mpvConfig = mpvConfig,
            libVlcConfig = libVlcConfig,
            exoPlayerConfig = exoPlayerConfig,
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
            synthwaveMode = readBool(prefs, Keys.SYNTHWAVE_MODE, "synthwave_mode", false),
            synthwaveAccent = prefs[Keys.SYNTHWAVE_ACCENT] ?: "magenta",
            soothingMode = readBool(prefs, Keys.SOOTHING_MODE, "soothing_mode", false),
            soothingAccent = prefs[Keys.SOOTHING_ACCENT] ?: "ocean",
            monochromeMode = readBool(prefs, Keys.MONOCHROME_MODE, "monochrome_mode", false),
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
                libraryConfigs = notificationLibraryConfigs,
            ),
            showAdvancedSettings = readBool(prefs, Keys.SHOW_ADVANCED_SETTINGS, "show_advanced_settings", false),
            audioVisualizerEnabled = readBool(prefs, Keys.AUDIO_VISUALIZER_ENABLED, "audio_visualizer_enabled", false),
            syncPlayJoinBehavior = try {
                SyncPlayJoinBehavior.valueOf(prefs[Keys.SYNC_PLAY_JOIN_BEHAVIOR] ?: SyncPlayJoinBehavior.ASK.name)
            } catch (_: Exception) { SyncPlayJoinBehavior.ASK },
            syncPlayToleranceMs = prefs[Keys.SYNC_PLAY_TOLERANCE_MS] ?: 100L,
            syncPlayAutoAcceptInvites = readBool(prefs, Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES, "sync_play_auto_accept_invites", false),
            defaultCastingStrategy = try {
                CastingStrategy.valueOf(prefs[Keys.DEFAULT_CASTING_STRATEGY] ?: CastingStrategy.ASK.name)
            } catch (_: Exception) { CastingStrategy.ASK },
            backgroundCastingEnabled = readBool(prefs, Keys.BACKGROUND_CASTING_ENABLED, "background_casting_enabled", true),
            preferredRenderer = prefs[Keys.PREFERRED_RENDERER],
            dvrPrePaddingMinutes = readInt(prefs, Keys.DVR_PRE_PADDING_MINUTES, "dvr_pre_padding_minutes", 0),
            dvrPostPaddingMinutes = readInt(prefs, Keys.DVR_POST_PADDING_MINUTES, "dvr_post_padding_minutes", 0),
            dvrRecordingQuality = prefs[Keys.DVR_RECORDING_QUALITY] ?: "AUTO",
            favoriteChannels = favoriteChannels,
            enabledNewsletterSections = enabledNewsletterSections,
            newsletterSectionOrder = newsletterSectionOrder,
            manualOfflineEnabled = readBool(prefs, Keys.MANUAL_OFFLINE_ENABLED, "manual_offline_enabled", false),
            autoOfflineEnabled = readBool(prefs, Keys.AUTO_OFFLINE_ENABLED, "auto_offline_enabled", true),
            manualBandwidthCap = prefs[Keys.MANUAL_BANDWIDTH_CAP] ?: 0L,
            meteredNetworkBehavior = try {
                MeteredNetworkBehavior.valueOf(prefs[Keys.METERED_NETWORK_BEHAVIOR] ?: MeteredNetworkBehavior.WARN.name)
            } catch (_: Exception) { MeteredNetworkBehavior.WARN },
            adaptiveBitrateEnabled = readBool(prefs, Keys.ADAPTIVE_BITRATE_ENABLED, "adaptive_bitrate_enabled", true),
            backgroundVideoAudioEnabled = readBool(prefs, Keys.BACKGROUND_VIDEO_AUDIO_ENABLED, "background_video_audio_enabled", false),
            autoPlayCountdownSec = readInt(prefs, Keys.AUTO_PLAY_COUNTDOWN_SEC, "auto_play_countdown_sec", 10),
            showUnwatchedBadge = readBool(prefs, Keys.SHOW_UNWATCHED_BADGE, "show_unwatched_badge", true),
            hideWatchedItems = readBool(prefs, Keys.HIDE_WATCHED_ITEMS, "hide_watched_items", false),
            mergeContinueWatchingAndNextUp = readBool(prefs, Keys.MERGE_CONTINUE_WATCHING_NEXT_UP, "merge_continue_watching_next_up", false),
            nextUpMaxDays = readInt(prefs, Keys.NEXT_UP_MAX_DAYS, "next_up_max_days", 0),
            nextUpRewatching = readBool(prefs, Keys.NEXT_UP_REWATCHING, "next_up_rewatching", false),
            nextUpExcludedSeriesIds = nextUpExcludedSeriesIds,
            hiddenCwItemIds = hiddenCwItemIds,
            pinnedHomeSections = pinnedHomeSections,
            homeLayoutPresets = homeLayoutPresets,
            continueWatchingClickBehavior = try {
                ContinueWatchingClickBehavior.valueOf(prefs[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] ?: ContinueWatchingClickBehavior.DETAILS.name)
            } catch (_: Exception) { ContinueWatchingClickBehavior.DETAILS },
            cellularStreamingQuality = try {
                StreamingQuality.valueOf(prefs[Keys.CELLULAR_STREAMING_QUALITY] ?: StreamingQuality.AUTO.name)
            } catch (_: Exception) { StreamingQuality.AUTO },
            showWatchedCheckmark = readBool(prefs, Keys.SHOW_WATCHED_CHECKMARK, "show_watched_checkmark", true),
            defaultLibrarySortOrders = defaultLibrarySortOrders,
            libraryViewModes = libraryViewModes,
            libraryFilters = libraryFilters,
            keepScreenOnDuringVideo = readBool(prefs, Keys.KEEP_SCREEN_ON_DURING_VIDEO, "keep_screen_on_during_video", true),
            downloadQuality = try {
                DownloadQuality.valueOf(prefs[Keys.DOWNLOAD_QUALITY] ?: DownloadQuality.ORIGINAL.name)
            } catch (_: Exception) { DownloadQuality.ORIGINAL },
            smartDownloadsEnabled = readBool(prefs, Keys.SMART_DOWNLOADS_ENABLED, "smart_downloads_enabled", false),
            autoDownloadNewEpisodes = readBool(prefs, Keys.AUTO_DOWNLOAD_NEW_EPISODES, "auto_download_new_episodes", false),
            incognitoModeEnabled = readBool(prefs, Keys.INCOGNITO_MODE_ENABLED, "incognito_mode_enabled", false),
            showTimeRemaining = readBool(prefs, Keys.SHOW_TIME_REMAINING, "show_time_remaining", false),
            showClockOnHome = readBool(prefs, Keys.SHOW_CLOCK_ON_HOME, "show_clock_on_home", false),
            showClockInPlayer = readBool(prefs, Keys.SHOW_CLOCK_IN_PLAYER, "show_clock_in_player", false),
            showSettingsInHomeSearch = readBool(prefs, Keys.SHOW_SETTINGS_IN_HOME_SEARCH, "show_settings_in_home_search", true),
            pauseOnAudioFocusLoss = readBool(prefs, Keys.PAUSE_ON_AUDIO_FOCUS_LOSS, "pause_on_audio_focus_loss", true),
            duckOnTransientFocusLoss = readBool(prefs, Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS, "duck_on_transient_focus_loss", false),
            volumeBoostEnabled = readBool(prefs, Keys.VOLUME_BOOST_ENABLED, "volume_boost_enabled", false),
            volumeBoostGain = readInt(prefs, Keys.VOLUME_BOOST_GAIN, "volume_boost_gain", 0),
            audioLyricsVisible = readBool(prefs, Keys.AUDIO_LYRICS_VISIBLE, "audio_lyrics_visible", false),
            showShareMediaOption = readBool(prefs, Keys.SHOW_SHARE_MEDIA_OPTION, "show_share_media_option", true),
            showExternalRatings = readBool(prefs, Keys.SHOW_EXTERNAL_RATINGS, "show_external_ratings", true),
            dataSaverEnabled = readBool(prefs, Keys.DATA_SAVER_ENABLED, "data_saver_enabled", false),
            verboseNetworkLogging = readBool(prefs, Keys.VERBOSE_NETWORK_LOGGING, "verbose_network_logging", false),
            networkTimeoutPreset = try {
                NetworkTimeoutPreset.valueOf(prefs[Keys.NETWORK_TIMEOUT_PRESET] ?: NetworkTimeoutPreset.DEFAULT.name)
            } catch (_: Exception) { NetworkTimeoutPreset.DEFAULT },
            reduceMotionEnabled = readBool(prefs, Keys.REDUCE_MOTION_ENABLED, "reduce_motion_enabled", false),
            preferAudioDescription = readBool(prefs, Keys.PREFER_AUDIO_DESCRIPTION, "prefer_audio_description", false),
            highContrastSubtitles = readBool(prefs, Keys.HIGH_CONTRAST_SUBTITLES, "high_contrast_subtitles", false),
            hideSearchHistory = readBool(prefs, Keys.HIDE_SEARCH_HISTORY, "hide_search_history", false),
            blueLightFilterEnabled = readBool(prefs, Keys.BLUE_LIGHT_FILTER_ENABLED, "blue_light_filter_enabled", false),
            blueLightFilterStrength = readFloat(prefs, Keys.BLUE_LIGHT_FILTER_STRENGTH, "blue_light_filter_strength", 0.3f),
            tvZoomModePercent = readFloat(prefs, Keys.TV_ZOOM_MODE_PERCENT, "tv_zoom_mode_percent", 0f),
            remoteControlEnabled = readBool(prefs, Keys.REMOTE_CONTROL_ENABLED, "remote_control_enabled", true),
            maxDownloadStorageGb = readInt(prefs, Keys.MAX_DOWNLOAD_STORAGE_GB, "max_download_storage_gb", 0),
            downloadStorageLocation = prefs[Keys.DOWNLOAD_STORAGE_LOCATION] ?: "INTERNAL",
            androidTvWatchNextEnabled = readBool(prefs, Keys.ANDROID_TV_WATCH_NEXT_ENABLED, "android_tv_watch_next_enabled", true),
            userDataSyncEnabled = readBool(prefs, Keys.USER_DATA_SYNC_ENABLED, "user_data_sync_enabled", true),
            appLanguage = prefs[Keys.APP_LANGUAGE],
            pgsSubtitleDirectPlay = readBool(prefs, Keys.PGS_SUBTITLE_DIRECT_PLAY, "pgs_subtitle_direct_play", false),
            backdropThemeMusicEnabled = readBool(prefs, Keys.BACKDROP_THEME_MUSIC_ENABLED, "backdrop_theme_music_enabled", false),
            hiddenNavItems = hiddenNavItems,
            navItemOrder = navItemOrder,
            selfUpdateCheckEnabled = readBool(prefs, Keys.SELF_UPDATE_CHECK_ENABLED, "self_update_check_enabled", true),
            hideEpisodeThumbnails = readBool(prefs, Keys.HIDE_EPISODE_THUMBNAILS, "hide_episode_thumbnails", false),
            episodesDescending = readBool(prefs, Keys.EPISODES_DESCENDING, "episodes_descending", true),
            skipSpecials = readBool(prefs, Keys.SKIP_SPECIALS, "skip_specials", false),
            cellularDownloadSizeWarningMb = readInt(prefs, Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB, "cellular_download_size_warning_mb", 0),
            hapticsEnabled = readBool(prefs, Keys.HAPTICS_ENABLED, "haptics_enabled", true),
            dateFormatPreference = try {
                DateFormatPreference.valueOf(prefs[Keys.DATE_FORMAT_PREFERENCE] ?: DateFormatPreference.SYSTEM.name)
            } catch (_: Exception) { DateFormatPreference.SYSTEM },
            appFontScale = try {
                AppFontScale.valueOf(prefs[Keys.APP_FONT_SCALE] ?: AppFontScale.DEFAULT.name)
            } catch (_: Exception) { AppFontScale.DEFAULT },
            scheduledThemeStartHour = readInt(prefs, Keys.SCHEDULED_THEME_START_HOUR, "scheduled_theme_start_hour", 22),
            scheduledThemeEndHour = readInt(prefs, Keys.SCHEDULED_THEME_END_HOUR, "scheduled_theme_end_hour", 7),
            colorBlindMode = try {
                ColorBlindMode.valueOf(prefs[Keys.COLOR_BLIND_MODE] ?: ColorBlindMode.NONE.name)
            } catch (_: Exception) { ColorBlindMode.NONE },
            handMode = try {
                HandMode.valueOf(prefs[Keys.HAND_MODE] ?: HandMode.RIGHT.name)
            } catch (_: Exception) { HandMode.RIGHT },
            downloadScheduleEnabled = prefs[Keys.DOWNLOAD_SCHEDULE_ENABLED] ?: false,
            downloadScheduleWindow = DownloadScheduleWindow(
                startHour = prefs[Keys.DOWNLOAD_SCHEDULE_START] ?: 0,
                endHour = prefs[Keys.DOWNLOAD_SCHEDULE_END] ?: 6,
                wifiOnly = prefs[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] ?: true,
            ),
            enabledExperimentalFeatures = enabledExperimentalFeatures,
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    val activeServerId: Flow<String?> get() = serverIdentityStore.activeServerId
    val activeUserId: Flow<String?> get() = serverIdentityStore.activeUserId
    val deviceId: Flow<String?> get() = serverIdentityStore.deviceId

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
        preferences.map { it.videoPlayer }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.VideoPlayerPreferences())
    val audioPlayerPreferences: StateFlow<com.raulshma.jellyplay.core.model.AudioPlayerPreferences> =
        preferences.map { it.audioPlayer }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.AudioPlayerPreferences())
    val subtitlePreferences: StateFlow<com.raulshma.jellyplay.core.model.SubtitlePreferences> =
        preferences.map { it.subtitle }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.SubtitlePreferences())
    val securityPreferences: StateFlow<com.raulshma.jellyplay.core.model.SecurityPreferences> =
        preferences.map { it.security }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.SecurityPreferences())
    val downloadPreferences: StateFlow<com.raulshma.jellyplay.core.model.DownloadPreferences> =
        preferences.map { it.download }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.DownloadPreferences())
    val syncPlayPreferences: StateFlow<com.raulshma.jellyplay.core.model.SyncPlayPreferences> =
        preferences.map { it.syncPlay }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.SyncPlayPreferences())
    val appearancePreferences: StateFlow<com.raulshma.jellyplay.core.model.AppearancePreferences> =
        preferences.map { it.appearance }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.AppearancePreferences())

    // Per-screen preference slices. Each mirrors the exact fields one settings
    // sub-screen reads (see the slice types in `PreferenceGroups.kt`), so a
    // sub-screen collecting its slice recomposes only when one of its fields
    // changes — not on every preference mutation app-wide. Like the per-domain
    // slices above, these derive from the single source-of-truth [preferences]
    // StateFlow and de-duplicate via the slice's structural equality.
    val playbackPreferences: StateFlow<com.raulshma.jellyplay.core.model.PlaybackPreferences> =
        preferences.map { it.playback }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.PlaybackPreferences())
    val audioPreferences: StateFlow<com.raulshma.jellyplay.core.model.AudioPreferences> =
        preferences.map { it.audio }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.AudioPreferences())
    val storagePreferences: StateFlow<com.raulshma.jellyplay.core.model.StoragePreferences> =
        preferences.map { it.storage }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.StoragePreferences())
    val appearanceScreenPreferences: StateFlow<com.raulshma.jellyplay.core.model.AppearanceScreenPreferences> =
        preferences.map { it.appearanceScreen }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.AppearanceScreenPreferences())
    val navigationCustomizationPreferences: StateFlow<com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences> =
        preferences.map { it.navigationCustomization }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences())
    val languagePreferences: StateFlow<com.raulshma.jellyplay.core.model.LanguagePreferences> =
        preferences.map { it.language }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.LanguagePreferences())
    val experimentalPreferences: StateFlow<com.raulshma.jellyplay.core.model.ExperimentalPreferences> =
        preferences.map { it.experimental }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.ExperimentalPreferences())

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

    suspend fun ensureDeviceId(): String = serverIdentityStore.ensureDeviceId()

    suspend fun setActiveServer(serverId: String) = serverIdentityStore.setActiveServer(serverId)

    suspend fun setActiveUser(userId: String) = serverIdentityStore.setActiveUser(userId)

    /**
     * Sets the active server and user in a single DataStore edit. Two back-to-
     * back `setActiveServer` + `setActiveUser` calls (the previous call-site
     * pattern) each opened their own `edit {}` → 2 disk reads + 2 atomic writes
     * + 2 full `preferences` re-emissions. Batching them halves the I/O and the
     * downstream re-derivation cascade.
     */
    suspend fun setActiveSession(serverId: String, userId: String) =
        serverIdentityStore.setActiveSession(serverId, userId)

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        dataStore.edit { it[Keys.PREFERRED_PLAYER] = playerType.name }
    }

    suspend fun setLiveStreamOption(option: LiveStreamOption) {
        dataStore.edit { it[Keys.LIVE_STREAM_OPTION] = option.name }
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.PREFERRED_SUBTITLE_LANG] = language
            else it.remove(Keys.PREFERRED_SUBTITLE_LANG)
        }
    }

    suspend fun setAppLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.APP_LANGUAGE] = language
            else it.remove(Keys.APP_LANGUAGE)
        }
    }

    suspend fun setPgsSubtitleDirectPlay(enabled: Boolean) {
        dataStore.edit { it[Keys.PGS_SUBTITLE_DIRECT_PLAY] = enabled }
    }

    suspend fun setBackdropThemeMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKDROP_THEME_MUSIC_ENABLED] = enabled }
    }

    suspend fun setHiddenNavItems(items: Set<String>) {
        dataStore.edit { it[Keys.HIDDEN_NAV_ITEMS] = json.encodeToString(items) }
    }

    suspend fun setNavItemOrder(order: List<String>) {
        dataStore.edit { it[Keys.NAV_ITEM_ORDER] = json.encodeToString(order) }
    }

    suspend fun setSelfUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SELF_UPDATE_CHECK_ENABLED] = enabled }
    }

    suspend fun setHideEpisodeThumbnails(enabled: Boolean) {
        dataStore.edit { it[Keys.HIDE_EPISODE_THUMBNAILS] = enabled }
    }

    suspend fun setEpisodesDescending(descending: Boolean) {
        dataStore.edit { it[Keys.EPISODES_DESCENDING] = descending }
    }

    suspend fun setSkipSpecials(enabled: Boolean) {
        dataStore.edit { it[Keys.SKIP_SPECIALS] = enabled }
    }

    suspend fun setCellularDownloadSizeWarningMb(sizeMb: Int) {
        dataStore.edit { it[Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB] = sizeMb }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setDateFormatPreference(preference: DateFormatPreference) {
        dataStore.edit { it[Keys.DATE_FORMAT_PREFERENCE] = preference.name }
    }

    suspend fun setAppFontScale(scale: AppFontScale) {
        dataStore.edit { it[Keys.APP_FONT_SCALE] = scale.name }
    }

    suspend fun setScheduledThemeStartHour(hour: Int) {
        dataStore.edit { it[Keys.SCHEDULED_THEME_START_HOUR] = hour }
    }

    suspend fun setScheduledThemeEndHour(hour: Int) {
        dataStore.edit { it[Keys.SCHEDULED_THEME_END_HOUR] = hour }
    }

    suspend fun setColorBlindMode(mode: ColorBlindMode) {
        dataStore.edit { it[Keys.COLOR_BLIND_MODE] = mode.name }
    }

    suspend fun setHandMode(mode: HandMode) {
        dataStore.edit { it[Keys.HAND_MODE] = mode.name }
    }

    suspend fun setDownloadScheduleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DOWNLOAD_SCHEDULE_ENABLED] = enabled }
    }

    suspend fun setDownloadScheduleWindow(window: DownloadScheduleWindow) {
        dataStore.edit {
            it[Keys.DOWNLOAD_SCHEDULE_START] = window.startHour
            it[Keys.DOWNLOAD_SCHEDULE_END] = window.endHour
            it[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] = window.wifiOnly
        }
    }

    suspend fun setSubtitlesForcedOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.SUBTITLES_FORCED_ONLY] = enabled }
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        dataStore.edit {
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

    /**
     * Persist the per-item video filter settings. Passing a neutral config
     * (all defaults) clears the entry so storage does not grow unbounded.
     */
    suspend fun setVideoEffectsForItem(itemId: String, effects: VideoEffectsConfig) {
        writeVideoEffectsForItem(itemId, effects)
    }

    suspend fun setDynamicTheming(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_THEMING] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        dataStore.edit { it[Keys.CONTRAST_LEVEL] = level.name }
    }

    suspend fun setOledMode(enabled: Boolean) {
        dataStore.edit { it[Keys.OLED_MODE] = enabled }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit { it[Keys.SUBTITLE_STYLE] = json.encodeToString(style) }
    }

    /**
     * Persists a per-item subtitle-sync delay (G9). A `delayMs` of 0 removes the
     * entry so the map doesn't grow unbounded with neutral values.
     */
    suspend fun setSubtitleDelayForItem(itemId: String, delayMs: Long) {
        dataStore.edit { prefs ->
            val current = try {
                prefs[Keys.SUBTITLE_DELAY_BY_ITEM]
                    ?.let { json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            val updated = if (delayMs == 0L) current - itemId else current + (itemId to delayMs)
            prefs[Keys.SUBTITLE_DELAY_BY_ITEM] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, Long>>(), updated,
            )
        }
    }

    suspend fun setHdrSubtitleStyleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HDR_SUBTITLE_STYLE_ENABLED] = enabled }
    }

    suspend fun setHdrSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit { it[Keys.HDR_SUBTITLE_STYLE] = json.encodeToString(style) }
    }

    suspend fun setStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { it[Keys.STREAMING_QUALITY] = quality.name }
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        dataStore.edit { it[Keys.PLAYBACK_MODE] = mode.name }
    }

    suspend fun setMaxCacheSize(sizeMb: Int) {
        dataStore.edit { it[Keys.MAX_CACHE_SIZE_MB] = sizeMb }
    }

    suspend fun setAutoDeleteCache(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DELETE_CACHE] = enabled }
    }

    suspend fun setPinLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PIN_LOCK_ENABLED] = enabled }
    }

    suspend fun setPinHash(hash: String?) {
        dataStore.edit {
            if (hash != null) it[Keys.PIN_HASH] = hash
            else it.remove(Keys.PIN_HASH)
        }
    }

    /**
     * Sets a new PIN atomically: hashes [pin] and writes both the hash and
     * `pinLockEnabled = true` in a single DataStore transaction so a concurrent
     * reader can never observe `pinLockEnabled = true` with the previous hash.
     */
    suspend fun setPin(pin: String) {
        if (pin.isBlank()) return
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_LOCK_ENABLED] = true
        }
    }

    /**
     * Clears the PIN atomically: disables the lock and removes the hash in a
     * single DataStore transaction.
     */
    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_LOCK_ENABLED] = false
            prefs.remove(Keys.PIN_HASH)
        }
    }

    /**
     * Silently upgrades a legacy (unsalted SHA-256) PIN hash to the v2 PBKDF2
     * format after the user has successfully unlocked with [pin]. No-op when
     * the stored hash is already v2 or when no PIN is set. Safe to call after
     * every successful [verifyPin] — callers do not need to gate on
     * [pinHashNeedsMigration] first.
     *
     * @return `true` if the hash was upgraded, `false` otherwise.
     */
    suspend fun upgradePinHashIfLegacy(pin: String): Boolean {
        if (pin.isBlank()) return false
        val current = preferences.value.pinHash ?: return false
        if (!pinHashNeedsMigration(current)) return false
        // Re-verify against the legacy hash before persisting — protects
        // against an inadvertent upgrade with the wrong PIN if the caller
        // invokes this without first verifying.
        if (!PinHasher.verify(pin, current)) return false
        val upgraded = hashPin(pin)
        // Persist only if the user hasn't cleared the PIN concurrently.
        dataStore.edit { prefs ->
            if (prefs[Keys.PIN_HASH] == current) {
                prefs[Keys.PIN_HASH] = upgraded
            }
        }
        return true
    }

    // ----- PIN rate limiting -------------------------------------------------
    //
    // Delegated to [pinRateLimiter] — the escalation state machine lives there
    // so the policy concentrates in one module. The PIN *storage keys*
    // (`pin_failed_attempts`, `pin_lockout_until_ms`) remain in the shared
    // `"user_prefs"` DataStore; both classes reach the same singleton.

    fun getPinLockoutState(): com.raulshma.jellyplay.core.model.PinLockoutState =
        pinRateLimiter.getPinLockoutState()

    /** @see com.raulshma.jellyplay.core.datastore.security.PinRateLimiter.recordFailedPinAttempt */
    suspend fun recordFailedPinAttempt(): com.raulshma.jellyplay.core.model.PinLockoutState =
        pinRateLimiter.recordFailedPinAttempt()

    /** Clears the failed-attempt counter and any active lockout. Call on successful unlock. */
    suspend fun resetPinLockout() = pinRateLimiter.resetPinLockout()

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setUsePinForPlayerLock(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_PIN_FOR_PLAYER_LOCK] = enabled }
    }

    /**
     * Verifies [pin] against the stored hash, running the PBKDF2 derivation on
     * [Dispatchers.Default] so callers never block the UI thread. Returns `false`
     * when no PIN is set. On success with a legacy hash, the hash is silently
     * upgraded to PBKDF2 (v2).
     */
    suspend fun verifyPinOffMainThread(pin: String): Boolean {
        val storedHash = preferences.value.pinHash ?: return false
        val valid = withContext(Dispatchers.Default) { PinHasher.verify(pin, storedHash) }
        if (valid && pinHashNeedsMigration(storedHash)) {
            upgradePinHashIfLegacy(pin)
        }
        return valid
    }

    suspend fun setShowAdvancedSettings(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_ADVANCED_SETTINGS] = enabled }
    }

    suspend fun setEnabledExperimentalFeatures(features: Set<com.raulshma.jellyplay.core.model.ExperimentalFeature>) {
        dataStore.edit {
            it[Keys.ENABLED_EXPERIMENTAL_FEATURES] =
                json.encodeToString(features.map { f -> f.name }.toSet())
        }
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_VISUALIZER_ENABLED] = enabled }
    }

    suspend fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) {
        dataStore.edit { it[Keys.SYNC_PLAY_JOIN_BEHAVIOR] = behavior.name }
    }

    suspend fun setSyncPlayToleranceMs(ms: Long) {
        dataStore.edit { it[Keys.SYNC_PLAY_TOLERANCE_MS] = ms }
    }

    suspend fun setSyncPlayAutoAcceptInvites(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES] = enabled }
    }

    suspend fun setDefaultCastingStrategy(strategy: CastingStrategy) {
        dataStore.edit { it[Keys.DEFAULT_CASTING_STRATEGY] = strategy.name }
    }

    suspend fun setBackgroundCastingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_CASTING_ENABLED] = enabled }
    }

    suspend fun setPreferredRenderer(renderer: String?) {
        dataStore.edit {
            if (renderer != null) it[Keys.PREFERRED_RENDERER] = renderer else it.remove(Keys.PREFERRED_RENDERER)
        }
    }

    suspend fun setDvrPrePaddingMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DVR_PRE_PADDING_MINUTES] = minutes }
    }

    suspend fun setDvrPostPaddingMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DVR_POST_PADDING_MINUTES] = minutes }
    }

    suspend fun setDvrRecordingQuality(quality: String) {
        dataStore.edit { it[Keys.DVR_RECORDING_QUALITY] = quality }
    }

    suspend fun setFavoriteChannels(channels: Set<String>) {
        dataStore.edit { it[Keys.FAVORITE_CHANNELS] = json.encodeToString(channels) }
    }

    suspend fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) {
        dataStore.edit { it[Keys.ENABLED_NEWSLETTER_SECTIONS] = json.encodeToString(sections) }
    }

    suspend fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) {
        dataStore.edit { it[Keys.NEWSLETTER_SECTION_ORDER] = json.encodeToString(order) }
    }

    suspend fun setManualOffline(enabled: Boolean) {
        dataStore.edit { it[Keys.MANUAL_OFFLINE_ENABLED] = enabled }
    }

    suspend fun setAutoOfflineEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_OFFLINE_ENABLED] = enabled }
    }

    suspend fun setManualBandwidthCap(cap: Long) {
        dataStore.edit { it[Keys.MANUAL_BANDWIDTH_CAP] = cap }
    }

    suspend fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) {
        dataStore.edit { it[Keys.METERED_NETWORK_BEHAVIOR] = behavior.name }
    }

    suspend fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ADAPTIVE_BITRATE_ENABLED] = enabled }
    }


    suspend fun setBackgroundVideoAudioEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_VIDEO_AUDIO_ENABLED] = enabled }
    }

    suspend fun setAutoPlayCountdownSec(sec: Int) {
        dataStore.edit { it[Keys.AUTO_PLAY_COUNTDOWN_SEC] = sec }
    }

    suspend fun setShowUnwatchedBadge(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_UNWATCHED_BADGE] = enabled }
    }

    suspend fun setHideWatchedItems(enabled: Boolean) {
        dataStore.edit { it[Keys.HIDE_WATCHED_ITEMS] = enabled }
    }

    suspend fun setMergeContinueWatchingAndNextUp(enabled: Boolean) {
        dataStore.edit { it[Keys.MERGE_CONTINUE_WATCHING_NEXT_UP] = enabled }
    }

    suspend fun setNextUpMaxDays(days: Int) {
        dataStore.edit { it[Keys.NEXT_UP_MAX_DAYS] = days.coerceAtLeast(0) }
    }

    suspend fun setNextUpRewatching(enabled: Boolean) {
        dataStore.edit { it[Keys.NEXT_UP_REWATCHING] = enabled }
    }

    suspend fun setNextUpExcludedSeriesIds(ids: Set<String>) {
        dataStore.edit { it[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(ids) }
    }

    suspend fun excludeSeriesFromNextUp(seriesId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(current + seriesId)
        }
    }

    suspend fun includeSeriesInNextUp(seriesId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(current - seriesId)
        }
    }

    suspend fun setHiddenCwItemIds(ids: Set<String>) {
        dataStore.edit { it[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(ids) }
    }

    suspend fun hideCwItem(itemId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_CW_ITEM_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(current + itemId)
        }
    }

    suspend fun unhideCwItem(itemId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_CW_ITEM_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(current - itemId)
        }
    }

    suspend fun unhideAllCwItems() {
        dataStore.edit { it.remove(Keys.HIDDEN_CW_ITEM_IDS) }
    }

    suspend fun setPinnedHomeSections(sections: List<PinnedHomeSection>) {
        dataStore.edit { prefs ->
            prefs[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(sections)
        }
    }

    suspend fun addPinnedHomeSection(section: PinnedHomeSection) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_HOME_SECTIONS]?.let {
                try { json.decodeFromString<List<PinnedHomeSection>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            if (current.none { it.id == section.id }) {
                prefs[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(current + section)
            }
        }
    }

    suspend fun removePinnedHomeSection(sectionId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_HOME_SECTIONS]?.let {
                try { json.decodeFromString<List<PinnedHomeSection>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            prefs[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(current.filterNot { it.id == sectionId })
        }
    }

    suspend fun setHomeLayoutPresets(presets: List<HomeLayoutPreset>) {
        dataStore.edit { prefs ->
            prefs[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(presets)
        }
    }

    suspend fun saveHomeLayoutPreset(preset: HomeLayoutPreset) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_LAYOUT_PRESETS]?.let {
                try { json.decodeFromString<List<HomeLayoutPreset>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            val next = if (current.any { it.id == preset.id }) {
                current.map { if (it.id == preset.id) preset else it }
            } else {
                current + preset
            }
            prefs[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(next)
        }
    }

    suspend fun deleteHomeLayoutPreset(presetId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_LAYOUT_PRESETS]?.let {
                try { json.decodeFromString<List<HomeLayoutPreset>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            prefs[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(current.filterNot { it.id == presetId })
        }
    }

    suspend fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) {
        dataStore.edit { it[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] = behavior.name }
    }

    suspend fun setCellularStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { it[Keys.CELLULAR_STREAMING_QUALITY] = quality.name }
    }

    suspend fun setShowWatchedCheckmark(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_WATCHED_CHECKMARK] = enabled }
    }

    suspend fun setDefaultLibrarySortOrder(libraryId: String, order: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, order) }
            prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS] = json.encodeToString(next)
        }
    }

    suspend fun setLibraryViewMode(libraryId: String, viewMode: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_VIEW_MODES]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, viewMode) }
            prefs[Keys.LIBRARY_VIEW_MODES] = json.encodeToString(next)
        }
    }

    suspend fun setLibraryFilters(libraryId: String, filters: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_FILTERS]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, filters) }
            prefs[Keys.LIBRARY_FILTERS] = json.encodeToString(next)
        }
    }

    suspend fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON_DURING_VIDEO] = enabled }
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality.name }
    }

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SMART_DOWNLOADS_ENABLED] = enabled }
    }

    suspend fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DOWNLOAD_NEW_EPISODES] = enabled }
    }

    suspend fun setIncognitoModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.INCOGNITO_MODE_ENABLED] = enabled }
    }

    suspend fun setShowTimeRemaining(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_TIME_REMAINING] = enabled }
    }

    suspend fun setShowClockOnHome(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_CLOCK_ON_HOME] = enabled }
    }

    suspend fun setShowSettingsInHomeSearch(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SETTINGS_IN_HOME_SEARCH] = enabled }
    }

    suspend fun setShowClockInPlayer(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_CLOCK_IN_PLAYER] = enabled }
    }

    suspend fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        dataStore.edit { it[Keys.PAUSE_ON_AUDIO_FOCUS_LOSS] = enabled }
    }

    suspend fun setDuckOnTransientFocusLoss(enabled: Boolean) {
        dataStore.edit { it[Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS] = enabled }
    }

    suspend fun setVolumeBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VOLUME_BOOST_ENABLED] = enabled }
    }

    suspend fun setVolumeBoostGain(gain: Int) {
        dataStore.edit { it[Keys.VOLUME_BOOST_GAIN] = gain }
    }

    suspend fun setAudioLyricsVisible(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_LYRICS_VISIBLE] = enabled }
    }

    suspend fun setShowShareMediaOption(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SHARE_MEDIA_OPTION] = enabled }
    }

    suspend fun setShowExternalRatings(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_EXTERNAL_RATINGS] = enabled }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    /**
     * Clears only the active server/user selection, preserving the stable
     * [DEVICE_ID], user preferences (theme, player, equalizer, onboarding, …)
     * and everything else. Use this on logout instead of [clearAll] so the
     * device id stays stable for Jellyfin session tracking and the user does
     * not lose all preferences on re-login.
     */
    suspend fun clearSession() = serverIdentityStore.clearSession()

    fun verifyPin(input: String, storedHash: String?): Boolean = PinHasher.verify(input, storedHash)

    fun hashPin(pin: String): String = PinHasher.hash(pin)

    /**
     * Returns `true` when [storedHash] is in the legacy unsalted-SHA-256
     * format and should be upgraded to PBKDF2 (v2) on the next successful
     * unlock. Callers with write access should re-hash the user's PIN with
     * [hashPin] after a successful [verifyPin] when this returns true.
     */
    fun pinHashNeedsMigration(storedHash: String?): Boolean = PinHasher.needsMigration(storedHash)

    suspend fun setContinueWatching(items: List<com.raulshma.jellyplay.core.model.MediaItem>) =
        widgetDataStore.setContinueWatching(items)

    suspend fun setAutoLockTimerMs(ms: Long) {
        dataStore.edit { it[Keys.AUTO_LOCK_TIMER_MS] = ms }
    }

    suspend fun setDialogueBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DIALOGUE_BOOST_ENABLED] = enabled }
    }

    suspend fun setDialogueBoostStrength(strength: EffectStrength) {
        dataStore.edit { it[Keys.DIALOGUE_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EQUALIZER_ENABLED] = enabled }
    }

    suspend fun setEqualizerSettings(settings: EqualizerSettings) {
        dataStore.edit { it[Keys.EQUALIZER_SETTINGS] = json.encodeToString(settings) }
    }

    suspend fun setAudioDelay(ms: Long) {
        dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_PASSTHROUGH] = enabled }
    }

    suspend fun setFrameRateMatching(enabled: Boolean) {
        // Keep the legacy boolean in sync with the new mode so both the old
        // toggle and the new picker reflect the same state. `true` maps to the
        // least-surprising frame-rate-only mode (the old single-resolution
        // behaviour); the picker can then upgrade it to include resolution.
        dataStore.edit {
            it[Keys.FRAME_RATE_MATCHING] = enabled
            if (enabled && it[Keys.REFRESH_RATE_MODE] == null) {
                it[Keys.REFRESH_RATE_MODE] = com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_ONLY.name
            } else if (!enabled) {
                it[Keys.REFRESH_RATE_MODE] = com.raulshma.jellyplay.core.model.RefreshRateMode.OFF.name
            }
        }
    }

    suspend fun setRefreshRateMode(mode: com.raulshma.jellyplay.core.model.RefreshRateMode) {
        dataStore.edit {
            it[Keys.REFRESH_RATE_MODE] = mode.name
            it[Keys.FRAME_RATE_MATCHING] = mode != com.raulshma.jellyplay.core.model.RefreshRateMode.OFF
        }
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NIGHT_MODE_ENABLED] = enabled }
    }

    suspend fun setNightModeStrength(strength: EffectStrength) {
        dataStore.edit { it[Keys.NIGHT_MODE_STRENGTH] = strength.name }
    }

    suspend fun setHomeMode(mode: HomeMode) {
        dataStore.edit { it[Keys.HOME_MODE] = mode.name }
    }

    suspend fun setVideoSeekDurationMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_SEEK_DURATION_MS] = ms }
    }

    suspend fun setVideoDefaultOrientation(mode: OrientationMode) {
        dataStore.edit { it[Keys.VIDEO_DEFAULT_ORIENTATION] = mode.name }
    }

    suspend fun setVideoControlsTimeoutMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = ms }
    }

    suspend fun setVideoGesturesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_GESTURES_ENABLED] = enabled }
    }

    suspend fun setVideoSkipBackOnResumeMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_SKIP_BACK_ON_RESUME_MS] = ms.coerceAtLeast(0L) }
    }

    suspend fun setVideoPassOutProtectionHours(hours: Int) {
        dataStore.edit { it[Keys.VIDEO_PASS_OUT_PROTECTION_HOURS] = hours.coerceAtLeast(0) }
    }

    suspend fun setVideoHoldSpeedEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_HOLD_SPEED_ENABLED] = enabled }
    }

    suspend fun setVideoHoldSpeedMultiplier(multiplier: Float) {
        dataStore.edit { it[Keys.VIDEO_HOLD_SPEED_MULTIPLIER] = multiplier }
    }

    suspend fun setVideoDefaultSpeed(speed: Float) {
        dataStore.edit { it[Keys.VIDEO_DEFAULT_SPEED] = speed }
    }

    suspend fun setVideoDefaultAspectRatio(ratio: String) {
        dataStore.edit { it[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = ratio }
    }

    suspend fun setVideoAutoplayNext(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setTrailerAutoplay(enabled: Boolean) {
        dataStore.edit { it[Keys.TRAILER_AUTOPLAY] = enabled }
    }

    suspend fun setCinemaModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CINEMA_MODE_ENABLED] = enabled }
    }

    suspend fun setVideoSwipeSeekMaxMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = ms }
    }

    suspend fun setVideoRememberBrightness(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_REMEMBER_BRIGHTNESS] = enabled }
    }

    suspend fun setVideoBrightnessLevel(level: Float) {
        dataStore.edit { it[Keys.VIDEO_BRIGHTNESS_LEVEL] = level }
    }

    suspend fun setVideoRememberVolume(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_REMEMBER_VOLUME] = enabled }
    }

    suspend fun setVideoVolumeLevel(level: Float) {
        dataStore.edit { it[Keys.VIDEO_VOLUME_LEVEL] = level }
    }

    suspend fun setVideoAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_AUTO_SKIP_INTRO] = enabled }
    }

    suspend fun setVideoAutoSkipOutro(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_AUTO_SKIP_OUTRO] = enabled }
    }

    suspend fun setVideoRememberMuted(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_REMEMBER_MUTED] = enabled }
    }

    suspend fun setVideoMuted(muted: Boolean) {
        dataStore.edit { it[Keys.VIDEO_MUTED] = muted }
    }

    suspend fun setSubtitlePreviewInSettings(enabled: Boolean) {
        dataStore.edit { it[Keys.SUBTITLE_PREVIEW_IN_SETTINGS] = enabled }
    }

    suspend fun setVideoGestureIndicatorSide(side: GestureIndicatorSide) {
        dataStore.edit { it[Keys.VIDEO_GESTURE_INDICATOR_SIDE] = side.name }
    }

    suspend fun setAudioDefaultSpeed(speed: Float) {
        dataStore.edit { it[Keys.AUDIO_DEFAULT_SPEED] = speed }
    }

    suspend fun setAudioNightModeVolume(volume: Float) {
        dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_VOLUME] = volume }
    }

    suspend fun setAudioNightModeGain(gain: Int) {
        dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_GAIN] = gain }
    }

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) {
        dataStore.edit { it[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = ms }
    }

    suspend fun setAudioAutoplayNext(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setTrickplayEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TRICKPLAY_ENABLED] = enabled }
    }

    suspend fun setTrickplayOnSeekGesture(enabled: Boolean) {
        dataStore.edit { it[Keys.TRICKPLAY_ON_SEEK_GESTURE] = enabled }
    }

    suspend fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) {
        dataStore.edit { prefs ->
            val current = readSegmentBehaviors(prefs).toMutableMap()
            current[type] = behavior
            prefs[Keys.SEGMENT_BEHAVIORS] = json.encodeToString(
                current.mapKeys { it.key.name }.mapValues { it.value.name }
            )
        }
    }

    suspend fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_EPISODE_BROWSER_ENABLED] = enabled }
    }

    suspend fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_SHOW_PLAYBACK_METADATA] = enabled }
    }

    suspend fun setVideoPreloadBufferSize(size: PreloadBufferSize) {
        dataStore.edit { it[Keys.VIDEO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) {
        dataStore.edit { it[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioCachingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_CACHING_ENABLED] = enabled }
    }

    suspend fun setAudioCacheSizeMb(sizeMb: Int) {
        dataStore.edit { it[Keys.AUDIO_CACHE_SIZE_MB] = sizeMb }
    }

    suspend fun setAudioPrefetchLookahead(lookahead: Int) {
        dataStore.edit { it[Keys.AUDIO_PREFETCH_LOOKAHEAD] = lookahead }
    }

    suspend fun setAudioPrefetchBackfill(backfill: Int) {
        dataStore.edit { it[Keys.AUDIO_PREFETCH_BACKFILL] = backfill }
    }

    suspend fun setAudioCacheNetworkPolicy(policy: AudioCacheNetworkPolicy) {
        dataStore.edit { it[Keys.AUDIO_CACHE_NETWORK_POLICY] = policy.name }
    }

    suspend fun setAudioCacheCellularMonthlyCapMb(capMb: Int) {
        dataStore.edit { it[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] = capMb }
    }

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        dataStore.edit { it[Keys.AUDIO_NORMALIZATION_MODE] = mode.name }
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_NORMALIZATION_ENABLED] = enabled }
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        dataStore.edit { it[Keys.REPLAYGAIN_PRE_AMP_DB] = db }
    }

    suspend fun setChannelMixMode(mode: ChannelMixMode) {
        dataStore.edit { it[Keys.CHANNEL_MIX_MODE] = mode.name }
    }

    suspend fun setChannelMixEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CHANNEL_MIX_ENABLED] = enabled }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_GAPLESS_ENABLED] = enabled }
    }

    suspend fun setCrossfadeDurationMs(ms: Long) {
        dataStore.edit { it[Keys.AUDIO_CROSSFADE_DURATION_MS] = ms }
    }

    suspend fun setSleepTimerDurationMs(ms: Long) {
        dataStore.edit { it[Keys.SLEEP_TIMER_DURATION_MS] = ms }
    }

    suspend fun setSleepTimerEndOfEpisode(enabled: Boolean) {
        dataStore.edit { it[Keys.SLEEP_TIMER_END_OF_EPISODE] = enabled }
    }

    suspend fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        dataStore.edit { it[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(categories) }
    }

    suspend fun setDreamSlideshowIntervalMs(ms: Long) {
        dataStore.edit { it[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = ms }
    }

    suspend fun setDreamKenBurnsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DREAM_KEN_BURNS_ENABLED] = enabled }
    }

    suspend fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        dataStore.edit { it[Keys.DREAM_TRANSITION_STYLE] = style.name }
    }

    suspend fun setDreamShowTitle(enabled: Boolean) {
        dataStore.edit { it[Keys.DREAM_SHOW_TITLE] = enabled }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        dataStore.edit { it[Keys.EQUALIZER_PRESET] = preset.name }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BASS_BOOST_ENABLED] = enabled }
    }

    suspend fun setBassBoostStrength(strength: EffectStrength) {
        dataStore.edit { it[Keys.BASS_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIRTUALIZER_ENABLED] = enabled }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = strength }
    }

    suspend fun setReverbPreset(preset: ReverbPreset) {
        dataStore.edit { it[Keys.REVERB_PRESET] = preset.name }
    }

    suspend fun setLrBalance(balance: Float) {
        dataStore.edit { it[Keys.LR_BALANCE] = balance }
    }

    suspend fun setAutoEqByGenre(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_EQ_BY_GENRE] = enabled }
    }

    suspend fun setPitchSemitones(semitones: Float) {
        dataStore.edit { it[Keys.PITCH_SEMITONES] = semitones }
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun setDownloadConnections(count: Int) {
        dataStore.edit { it[Keys.DOWNLOAD_CONNECTIONS] = count }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 6) }
    }

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        dataStore.edit {
            it[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(types.map { t -> t.name }.toSet())
        }
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) {
        dataStore.edit {
            val normalized = buildList {
                addAll(order.filter { it in HomeSectionType.CONFIGURABLE }.distinct())
                addAll(HomeSectionType.CONFIGURABLE.filterNot { it in this })
            }
            it[Keys.HOME_SECTION_ORDER] = json.encodeToString(normalized.map { t -> t.name })
        }
    }

    suspend fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) {
        // Drop entries with empty disabled-sets so the map stays clean and
        // "fully enabled" libraries simply have no key.
        val cleaned = overrides.filterValues { it.isNotEmpty() }
        dataStore.edit {
            it[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(cleaned)
        }
    }

    suspend fun setNavBarShowLabels(show: Boolean) {
        dataStore.edit { it[Keys.NAV_BAR_SHOW_LABELS] = show }
    }

    suspend fun setHideBottomNavOnScroll(hide: Boolean) {
        dataStore.edit { it[Keys.HIDE_BOTTOM_NAV_ON_SCROLL] = hide }
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HOME_HERO_ENABLED] = enabled }
    }

    suspend fun setHomeBackdropEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HOME_BACKDROP_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMpvConfig(config: MpvEngineConfig) {
        dataStore.edit { it[Keys.MPV_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setLibVlcConfig(config: LibVlcEngineConfig) {
        dataStore.edit { it[Keys.LIBVLC_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        dataStore.edit { it[Keys.EXO_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setPerformanceMode(enabled: Boolean) {
        dataStore.edit { it[Keys.PERFORMANCE_MODE] = enabled }
    }

    val continueWatching: kotlinx.coroutines.flow.Flow<List<com.raulshma.jellyplay.core.model.MediaItem>>
        get() = widgetDataStore.continueWatching

    val widgetConfig: kotlinx.coroutines.flow.Flow<WidgetConfig>
        get() = widgetDataStore.widgetConfig

    val libraryWidgetItems: kotlinx.coroutines.flow.Flow<List<LibraryWidgetItem>>
        get() = widgetDataStore.libraryWidgetItems

    val libraryWidgetVersion: kotlinx.coroutines.flow.Flow<Long>
        get() = widgetDataStore.libraryWidgetVersion

    val libraryWidgetUpdatedAtMs: kotlinx.coroutines.flow.Flow<Long>
        get() = widgetDataStore.libraryWidgetUpdatedAtMs

    val seerrWidgetItems: kotlinx.coroutines.flow.Flow<List<SeerrWidgetItem>>
        get() = widgetDataStore.seerrWidgetItems

    val seerrWidgetVersion: kotlinx.coroutines.flow.Flow<Long>
        get() = widgetDataStore.seerrWidgetVersion

    val seerrWidgetUpdatedAtMs: kotlinx.coroutines.flow.Flow<Long>
        get() = widgetDataStore.seerrWidgetUpdatedAtMs

    val widgetLastRefreshMs: kotlinx.coroutines.flow.Flow<Long>
        get() = widgetDataStore.widgetLastRefreshMs

    suspend fun setWidgetConfig(config: WidgetConfig) = widgetDataStore.setWidgetConfig(config)

    fun getWidgetConfigForIdSync(appWidgetId: Int): WidgetConfig =
        widgetDataStore.getWidgetConfigForIdSync(appWidgetId)

    fun getWidgetConfigForId(appWidgetId: Int): kotlinx.coroutines.flow.Flow<WidgetConfig> =
        widgetDataStore.getWidgetConfigForId(appWidgetId)

    suspend fun setWidgetConfigForId(appWidgetId: Int, config: WidgetConfig) =
        widgetDataStore.setWidgetConfigForId(appWidgetId, config)

    suspend fun removeWidgetConfigForId(appWidgetId: Int) =
        widgetDataStore.removeWidgetConfigForId(appWidgetId)

    suspend fun setLibraryWidgetItems(
        items: List<LibraryWidgetItem>,
        version: Long,
        updatedAtMs: Long,
    ) = widgetDataStore.setLibraryWidgetItems(items, version, updatedAtMs)

    suspend fun setSeerrWidgetItems(
        items: List<SeerrWidgetItem>,
        version: Long,
        updatedAtMs: Long,
    ) = widgetDataStore.setSeerrWidgetItems(items, version, updatedAtMs)

    suspend fun setWidgetLastRefreshMs(ms: Long) = widgetDataStore.setWidgetLastRefreshMs(ms)

    suspend fun setNewsletterEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NEWSLETTER_ENABLED] = enabled }
    }

    suspend fun setNewsletterDayOfWeek(day: Int) {
        dataStore.edit { it[Keys.NEWSLETTER_DAY_OF_WEEK] = day }
    }

    suspend fun setNewsletterLastViewed(timestampMs: Long) {
        dataStore.edit { it[Keys.NEWSLETTER_LAST_VIEWED_MS] = timestampMs }
    }

    suspend fun setAccentColorSwatch(swatch: String) {
        dataStore.edit { it[Keys.ACCENT_COLOR_SWATCH] = swatch }
    }

    suspend fun setColorStyle(style: ColorStyle) {
        dataStore.edit { it[Keys.COLOR_STYLE] = style.name }
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[Keys.LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun restorePreferences(prefs: UserPreferences, restoreSecuritySensitive: Boolean = true) {
        val json = ENCODE_DEFAULTS_JSON
        dataStore.edit { settings ->
            settings[Keys.PREFERRED_PLAYER] = prefs.preferredPlayer.name
            prefs.preferredSubtitleLanguage?.let { settings[Keys.PREFERRED_SUBTITLE_LANG] = it }
            settings[Keys.SUBTITLES_FORCED_ONLY] = prefs.subtitlesForcedOnly
            prefs.preferredAudioLanguage?.let { settings[Keys.PREFERRED_AUDIO_LANG] = it }
            settings[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, com.raulshma.jellyplay.core.model.MediaStreamSelection>>(),
                prefs.mediaStreamSelections,
            )
            settings[Keys.VIDEO_EFFECTS_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, VideoEffectsConfig>>(),
                prefs.videoEffectsByItem,
            )
            settings[Keys.SUBTITLE_DELAY_BY_ITEM] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, Long>>(),
                prefs.subtitleDelayByItem,
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
            settings[Keys.PLAYBACK_MODE] = prefs.playbackMode.name
            settings[Keys.MAX_CACHE_SIZE_MB] = prefs.maxCacheSizeMb
            settings[Keys.AUTO_DELETE_CACHE] = prefs.autoDeleteCache
            // Security-sensitive fields (PIN hash, biometric lock, player lock,
            // auto-lock timer) are only restored when explicitly opted in. An
            // imported backup otherwise silently replaces the device's lock
            // config — a footgun when the backup came from another device.
            if (restoreSecuritySensitive) {
                settings[Keys.PIN_LOCK_ENABLED] = prefs.pinLockEnabled
                prefs.pinHash?.let { settings[Keys.PIN_HASH] = it }
                settings[Keys.BIOMETRIC_LOCK_ENABLED] = prefs.biometricLockEnabled
                settings[Keys.USE_PIN_FOR_PLAYER_LOCK] = prefs.usePinForPlayerLock
                settings[Keys.AUTO_LOCK_TIMER_MS] = prefs.autoLockTimerMs
            }
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
            settings[Keys.REFRESH_RATE_MODE] = prefs.refreshRateMode.name
            settings[Keys.NIGHT_MODE_ENABLED] = prefs.nightModeEnabled
            settings[Keys.NIGHT_MODE_STRENGTH] = prefs.nightModeStrength.name
            settings[Keys.HOME_MODE] = prefs.homeMode.name
            settings[Keys.VIDEO_SEEK_DURATION_MS] = prefs.videoSeekDurationMs
            settings[Keys.VIDEO_DEFAULT_ORIENTATION] = prefs.videoDefaultOrientation.name
            settings[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = prefs.videoControlsTimeoutMs
            settings[Keys.VIDEO_GESTURES_ENABLED] = prefs.videoGesturesEnabled
            settings[Keys.VIDEO_PASS_OUT_PROTECTION_HOURS] = prefs.videoPassOutProtectionHours
            settings[Keys.VIDEO_SKIP_BACK_ON_RESUME_MS] = prefs.videoSkipBackOnResumeMs
            settings[Keys.VIDEO_HOLD_SPEED_ENABLED] = prefs.videoHoldSpeedEnabled
            settings[Keys.VIDEO_HOLD_SPEED_MULTIPLIER] = prefs.videoHoldSpeedMultiplier
            settings[Keys.VIDEO_DEFAULT_SPEED] = prefs.videoDefaultSpeed
            settings[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = prefs.videoDefaultAspectRatio
            settings[Keys.VIDEO_AUTOPLAY_NEXT] = prefs.videoAutoplayNext
            settings[Keys.TRAILER_AUTOPLAY] = prefs.trailerAutoplay
            settings[Keys.CINEMA_MODE_ENABLED] = prefs.cinemaModeEnabled
            settings[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = prefs.videoSwipeSeekMaxMs
            settings[Keys.VIDEO_REMEMBER_BRIGHTNESS] = prefs.videoRememberBrightness
            settings[Keys.VIDEO_BRIGHTNESS_LEVEL] = prefs.videoBrightnessLevel
            settings[Keys.VIDEO_REMEMBER_VOLUME] = prefs.videoRememberVolume
            settings[Keys.VIDEO_VOLUME_LEVEL] = prefs.videoVolumeLevel
            settings[Keys.VIDEO_AUTO_SKIP_INTRO] = prefs.videoAutoSkipIntro
            settings[Keys.VIDEO_AUTO_SKIP_OUTRO] = prefs.videoAutoSkipOutro
            settings[Keys.VIDEO_REMEMBER_MUTED] = prefs.videoRememberMuted
            settings[Keys.VIDEO_MUTED] = prefs.videoMuted
            settings[Keys.SUBTITLE_PREVIEW_IN_SETTINGS] = prefs.subtitlePreviewInSettings
            settings[Keys.VIDEO_GESTURE_INDICATOR_SIDE] = prefs.videoGestureIndicatorSide.name
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
            settings[Keys.AUDIO_CACHING_ENABLED] = prefs.audioCachingEnabled
            settings[Keys.AUDIO_CACHE_SIZE_MB] = prefs.audioCacheSizeMb
            settings[Keys.AUDIO_PREFETCH_LOOKAHEAD] = prefs.audioPrefetchLookahead
            settings[Keys.AUDIO_PREFETCH_BACKFILL] = prefs.audioPrefetchBackfill
            settings[Keys.AUDIO_CACHE_NETWORK_POLICY] = prefs.audioCacheNetworkPolicy.name
            settings[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] = prefs.audioCacheCellularMonthlyCapMb
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
            settings[Keys.MAX_CONCURRENT_DOWNLOADS] = prefs.maxConcurrentDownloads
            settings[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(
                kotlinx.serialization.serializer<Set<com.raulshma.jellyplay.core.model.HomeSectionType>>(),
                prefs.enabledHomeSectionTypes,
            )
            settings[Keys.HOME_SECTION_ORDER] = json.encodeToString(
                kotlinx.serialization.serializer<List<com.raulshma.jellyplay.core.model.HomeSectionType>>(),
                prefs.homeSectionOrder,
            )
            settings[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, Set<HomeSectionType>>>(),
                prefs.libraryHomeSectionOverrides,
            )
            settings[Keys.NAV_BAR_SHOW_LABELS] = prefs.navBarShowLabels
            settings[Keys.HIDE_BOTTOM_NAV_ON_SCROLL] = prefs.hideBottomNavOnScroll
            settings[Keys.HOME_HERO_ENABLED] = prefs.homeHeroEnabled
            settings[Keys.HOME_BACKDROP_ENABLED] = prefs.homeBackdropEnabled
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
            settings[Keys.SHOW_ADVANCED_SETTINGS] = prefs.showAdvancedSettings
            settings[Keys.AUDIO_VISUALIZER_ENABLED] = prefs.audioVisualizerEnabled
            settings[Keys.ENABLED_EXPERIMENTAL_FEATURES] = json.encodeToString(
                kotlinx.serialization.serializer<Set<com.raulshma.jellyplay.core.model.ExperimentalFeature>>(),
                prefs.enabledExperimentalFeatures,
            )

            settings[Keys.SYNC_PLAY_JOIN_BEHAVIOR] = prefs.syncPlayJoinBehavior.name
            settings[Keys.SYNC_PLAY_TOLERANCE_MS] = prefs.syncPlayToleranceMs
            settings[Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES] = prefs.syncPlayAutoAcceptInvites
            settings[Keys.DEFAULT_CASTING_STRATEGY] = prefs.defaultCastingStrategy.name
            settings[Keys.BACKGROUND_CASTING_ENABLED] = prefs.backgroundCastingEnabled
            prefs.preferredRenderer?.let { settings[Keys.PREFERRED_RENDERER] = it }
            settings[Keys.DVR_PRE_PADDING_MINUTES] = prefs.dvrPrePaddingMinutes
            settings[Keys.DVR_POST_PADDING_MINUTES] = prefs.dvrPostPaddingMinutes
            settings[Keys.DVR_RECORDING_QUALITY] = prefs.dvrRecordingQuality
            settings[Keys.FAVORITE_CHANNELS] = json.encodeToString(prefs.favoriteChannels)
            settings[Keys.ENABLED_NEWSLETTER_SECTIONS] = json.encodeToString(prefs.enabledNewsletterSections)
            settings[Keys.NEWSLETTER_SECTION_ORDER] = json.encodeToString(prefs.newsletterSectionOrder)
            settings[Keys.MANUAL_OFFLINE_ENABLED] = prefs.manualOfflineEnabled
            settings[Keys.AUTO_OFFLINE_ENABLED] = prefs.autoOfflineEnabled
            settings[Keys.MANUAL_BANDWIDTH_CAP] = prefs.manualBandwidthCap
            settings[Keys.METERED_NETWORK_BEHAVIOR] = prefs.meteredNetworkBehavior.name
            settings[Keys.ADAPTIVE_BITRATE_ENABLED] = prefs.adaptiveBitrateEnabled
            settings[Keys.BACKGROUND_VIDEO_AUDIO_ENABLED] = prefs.backgroundVideoAudioEnabled
            settings[Keys.AUTO_PLAY_COUNTDOWN_SEC] = prefs.autoPlayCountdownSec
            settings[Keys.SHOW_UNWATCHED_BADGE] = prefs.showUnwatchedBadge
            settings[Keys.HIDE_WATCHED_ITEMS] = prefs.hideWatchedItems
            settings[Keys.MERGE_CONTINUE_WATCHING_NEXT_UP] = prefs.mergeContinueWatchingAndNextUp
            settings[Keys.NEXT_UP_MAX_DAYS] = prefs.nextUpMaxDays
            settings[Keys.NEXT_UP_REWATCHING] = prefs.nextUpRewatching
            settings[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(prefs.nextUpExcludedSeriesIds)
            settings[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(prefs.hiddenCwItemIds)
            settings[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(prefs.pinnedHomeSections)
            settings[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(prefs.homeLayoutPresets)
            settings[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] = prefs.continueWatchingClickBehavior.name
            settings[Keys.CELLULAR_STREAMING_QUALITY] = prefs.cellularStreamingQuality.name
            settings[Keys.SHOW_WATCHED_CHECKMARK] = prefs.showWatchedCheckmark
            settings[Keys.DEFAULT_LIBRARY_SORT_ORDERS] = json.encodeToString(prefs.defaultLibrarySortOrders)
            settings[Keys.LIBRARY_VIEW_MODES] = json.encodeToString(prefs.libraryViewModes)
            settings[Keys.LIBRARY_FILTERS] = json.encodeToString(prefs.libraryFilters)
            settings[Keys.KEEP_SCREEN_ON_DURING_VIDEO] = prefs.keepScreenOnDuringVideo
            settings[Keys.DOWNLOAD_QUALITY] = prefs.downloadQuality.name
            settings[Keys.SMART_DOWNLOADS_ENABLED] = prefs.smartDownloadsEnabled
            settings[Keys.AUTO_DOWNLOAD_NEW_EPISODES] = prefs.autoDownloadNewEpisodes
            settings[Keys.INCOGNITO_MODE_ENABLED] = prefs.incognitoModeEnabled
            settings[Keys.SHOW_TIME_REMAINING] = prefs.showTimeRemaining
            settings[Keys.SHOW_CLOCK_ON_HOME] = prefs.showClockOnHome
            settings[Keys.SHOW_CLOCK_IN_PLAYER] = prefs.showClockInPlayer
            settings[Keys.SHOW_SETTINGS_IN_HOME_SEARCH] = prefs.showSettingsInHomeSearch
            settings[Keys.PAUSE_ON_AUDIO_FOCUS_LOSS] = prefs.pauseOnAudioFocusLoss
            settings[Keys.VOLUME_BOOST_ENABLED] = prefs.volumeBoostEnabled
            settings[Keys.VOLUME_BOOST_GAIN] = prefs.volumeBoostGain
            settings[Keys.SHOW_SHARE_MEDIA_OPTION] = prefs.showShareMediaOption
            settings[Keys.SHOW_EXTERNAL_RATINGS] = prefs.showExternalRatings
            settings[Keys.DATA_SAVER_ENABLED] = prefs.dataSaverEnabled
            settings[Keys.VERBOSE_NETWORK_LOGGING] = prefs.verboseNetworkLogging
            settings[Keys.NETWORK_TIMEOUT_PRESET] = prefs.networkTimeoutPreset.name
            settings[Keys.REDUCE_MOTION_ENABLED] = prefs.reduceMotionEnabled
            settings[Keys.PREFER_AUDIO_DESCRIPTION] = prefs.preferAudioDescription
            settings[Keys.HIGH_CONTRAST_SUBTITLES] = prefs.highContrastSubtitles
            settings[Keys.HIDE_SEARCH_HISTORY] = prefs.hideSearchHistory
            settings[Keys.BLUE_LIGHT_FILTER_ENABLED] = prefs.blueLightFilterEnabled
            settings[Keys.BLUE_LIGHT_FILTER_STRENGTH] = prefs.blueLightFilterStrength
            settings[Keys.TV_ZOOM_MODE_PERCENT] = prefs.tvZoomModePercent
            settings[Keys.REMOTE_CONTROL_ENABLED] = prefs.remoteControlEnabled
            settings[Keys.MAX_DOWNLOAD_STORAGE_GB] = prefs.maxDownloadStorageGb
            settings[Keys.DOWNLOAD_STORAGE_LOCATION] = prefs.downloadStorageLocation
            settings[Keys.ANDROID_TV_WATCH_NEXT_ENABLED] = prefs.androidTvWatchNextEnabled
            settings[Keys.USER_DATA_SYNC_ENABLED] = prefs.userDataSyncEnabled
            prefs.appLanguage?.let { settings[Keys.APP_LANGUAGE] = it }
            settings[Keys.PGS_SUBTITLE_DIRECT_PLAY] = prefs.pgsSubtitleDirectPlay
            settings[Keys.BACKDROP_THEME_MUSIC_ENABLED] = prefs.backdropThemeMusicEnabled
            settings[Keys.HIDDEN_NAV_ITEMS] = json.encodeToString(prefs.hiddenNavItems)
            settings[Keys.NAV_ITEM_ORDER] = json.encodeToString(prefs.navItemOrder)
            settings[Keys.SELF_UPDATE_CHECK_ENABLED] = prefs.selfUpdateCheckEnabled
            settings[Keys.HDR_SUBTITLE_STYLE_ENABLED] = prefs.hdrSubtitleStyleEnabled
            settings[Keys.HDR_SUBTITLE_STYLE] = json.encodeToString(prefs.hdrSubtitleStyle)
        }
    }

    val notificationPreferences: StateFlow<NotificationPreferences> = preferences.map { it.notificationPreferences }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, NotificationPreferences())

    suspend fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        dataStore.edit { prefs ->
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
        dataStore.edit { it[Keys.DATA_SAVER_ENABLED] = enabled }
    }

    suspend fun setVerboseNetworkLogging(enabled: Boolean) {
        dataStore.edit { it[Keys.VERBOSE_NETWORK_LOGGING] = enabled }
    }

    suspend fun setNetworkTimeoutPreset(preset: NetworkTimeoutPreset) {
        dataStore.edit { it[Keys.NETWORK_TIMEOUT_PRESET] = preset.name }
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION_ENABLED] = enabled }
    }

    suspend fun setPreferAudioDescription(enabled: Boolean) {
        dataStore.edit { it[Keys.PREFER_AUDIO_DESCRIPTION] = enabled }
    }

    suspend fun setHighContrastSubtitles(enabled: Boolean) {
        dataStore.edit { it[Keys.HIGH_CONTRAST_SUBTITLES] = enabled }
    }

    suspend fun setHideSearchHistory(enabled: Boolean) {
        dataStore.edit { it[Keys.HIDE_SEARCH_HISTORY] = enabled }
    }

    suspend fun setBlueLightFilterEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BLUE_LIGHT_FILTER_ENABLED] = enabled }
    }

    suspend fun setBlueLightFilterStrength(strength: Float) {
        dataStore.edit { it[Keys.BLUE_LIGHT_FILTER_STRENGTH] = strength }
    }

    suspend fun setTvZoomModePercent(percent: Float) {
        dataStore.edit { it[Keys.TV_ZOOM_MODE_PERCENT] = percent }
    }

    suspend fun setRemoteControlEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMOTE_CONTROL_ENABLED] = enabled }
    }

    suspend fun setMaxDownloadStorageGb(gb: Int) {
        dataStore.edit { it[Keys.MAX_DOWNLOAD_STORAGE_GB] = gb }
    }

    suspend fun setDownloadStorageLocation(location: String) {
        dataStore.edit { it[Keys.DOWNLOAD_STORAGE_LOCATION] = location }
    }

    suspend fun setAndroidTvWatchNextEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ANDROID_TV_WATCH_NEXT_ENABLED] = enabled }
    }

    suspend fun setUserDataSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.USER_DATA_SYNC_ENABLED] = enabled }
    }

    suspend fun setSynthwaveMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNTHWAVE_MODE] = enabled
            if (enabled) {
                prefs[Keys.SOOTHING_MODE] = false
                prefs[Keys.MONOCHROME_MODE] = false
            }
        }
    }

    suspend fun setSynthwaveAccent(accent: String) {
        dataStore.edit { it[Keys.SYNTHWAVE_ACCENT] = accent }
    }

    suspend fun setSoothingMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SOOTHING_MODE] = enabled
            if (enabled) {
                prefs[Keys.SYNTHWAVE_MODE] = false
                prefs[Keys.MONOCHROME_MODE] = false
            }
        }
    }

    suspend fun setSoothingAccent(accent: String) {
        dataStore.edit { it[Keys.SOOTHING_ACCENT] = accent }
    }

    suspend fun setMonochromeMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.MONOCHROME_MODE] = enabled
            if (enabled) {
                prefs[Keys.SYNTHWAVE_MODE] = false
                prefs[Keys.SOOTHING_MODE] = false
            }
        }
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
    internal fun resetCategoryKeys(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.APPEARANCE -> listOf(
            Keys.THEME_MODE, Keys.CONTRAST_LEVEL, Keys.DYNAMIC_THEMING, Keys.OLED_MODE,
            Keys.ACCENT_COLOR_SWATCH, Keys.COLOR_STYLE, Keys.PERFORMANCE_MODE,
            Keys.REDUCE_MOTION_ENABLED, Keys.SYNTHWAVE_MODE, Keys.SYNTHWAVE_ACCENT,
            Keys.SOOTHING_MODE, Keys.SOOTHING_ACCENT, Keys.MONOCHROME_MODE,
            Keys.BACKDROP_THEME_MUSIC_ENABLED,
            Keys.BLUE_LIGHT_FILTER_ENABLED, Keys.BLUE_LIGHT_FILTER_STRENGTH,
            Keys.DATE_FORMAT_PREFERENCE, Keys.APP_FONT_SCALE,
            Keys.SCHEDULED_THEME_START_HOUR, Keys.SCHEDULED_THEME_END_HOUR,
            Keys.COLOR_BLIND_MODE, Keys.HAND_MODE,
        )
        PreferenceResetCategory.PLAYBACK -> listOf(
            Keys.PREFERRED_PLAYER, Keys.STREAMING_QUALITY, Keys.CELLULAR_STREAMING_QUALITY,
            Keys.FORCE_DIRECT_PLAY, Keys.PLAYBACK_MODE, Keys.DECODER_MODE,
            Keys.AUDIO_PASSTHROUGH, Keys.FRAME_RATE_MATCHING, Keys.REFRESH_RATE_MODE,
            Keys.VIDEO_DEFAULT_ORIENTATION, Keys.VIDEO_DEFAULT_ASPECT_RATIO,
            Keys.VIDEO_PRELOAD_BUFFER_SIZE, Keys.VIDEO_GESTURES_ENABLED,
            Keys.VIDEO_PASS_OUT_PROTECTION_HOURS, Keys.VIDEO_SKIP_BACK_ON_RESUME_MS,
            Keys.VIDEO_HOLD_SPEED_ENABLED, Keys.VIDEO_HOLD_SPEED_MULTIPLIER,
            Keys.VIDEO_DEFAULT_SPEED, Keys.VIDEO_BRIGHTNESS_LEVEL,
            Keys.VIDEO_AUTOPLAY_NEXT, Keys.TRAILER_AUTOPLAY, Keys.CINEMA_MODE_ENABLED,
            Keys.VIDEO_REMEMBER_BRIGHTNESS, Keys.VIDEO_REMEMBER_VOLUME,
            Keys.VIDEO_VOLUME_LEVEL, Keys.VIDEO_AUTO_SKIP_INTRO,
            Keys.VIDEO_AUTO_SKIP_OUTRO, Keys.VIDEO_REMEMBER_MUTED, Keys.VIDEO_MUTED,
            Keys.VIDEO_GESTURE_INDICATOR_SIDE, Keys.VIDEO_SEEK_DURATION_MS,
            Keys.VIDEO_CONTROLS_TIMEOUT_MS, Keys.VIDEO_SWIPE_SEEK_MAX_MS,
            Keys.AUDIO_DELAY_MS, Keys.TRICKPLAY_ENABLED, Keys.TRICKPLAY_ON_SEEK_GESTURE,
            Keys.VIDEO_EPISODE_BROWSER_ENABLED, Keys.VIDEO_SHOW_PLAYBACK_METADATA,
            Keys.BACKGROUND_VIDEO_AUDIO_ENABLED, Keys.AUTO_PLAY_COUNTDOWN_SEC,
            Keys.KEEP_SCREEN_ON_DURING_VIDEO, Keys.INCOGNITO_MODE_ENABLED,
            Keys.SHOW_CLOCK_IN_PLAYER, Keys.SHOW_TIME_REMAINING,
            Keys.PAUSE_ON_AUDIO_FOCUS_LOSS, Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS,
            Keys.TV_ZOOM_MODE_PERCENT,
            Keys.SEGMENT_BEHAVIORS, Keys.SKIP_INTRO_ENABLED, Keys.SKIP_OUTRO_ENABLED,
            Keys.AUTO_SKIP_INTRO, Keys.AUTO_SKIP_OUTRO,
        )
        PreferenceResetCategory.AUDIO -> listOf(
            Keys.AUDIO_DEFAULT_SPEED, Keys.AUDIO_VISUALIZER_ENABLED,
            Keys.AUDIO_GAPLESS_ENABLED, Keys.AUDIO_CROSSFADE_DURATION_MS,
            Keys.AUDIO_NORMALIZATION_ENABLED, Keys.AUDIO_NORMALIZATION_MODE,
            Keys.CHANNEL_MIX_ENABLED, Keys.CHANNEL_MIX_MODE,
            Keys.EQUALIZER_ENABLED, Keys.EQUALIZER_SETTINGS,
            Keys.EQUALIZER_PRESET, Keys.BASS_BOOST_ENABLED, Keys.BASS_BOOST_STRENGTH,
            Keys.VIRTUALIZER_ENABLED, Keys.VIRTUALIZER_STRENGTH,
            Keys.REVERB_PRESET, Keys.VOLUME_BOOST_ENABLED, Keys.VOLUME_BOOST_GAIN,
            Keys.LR_BALANCE, Keys.AUTO_EQ_BY_GENRE, Keys.PITCH_SEMITONES,
            Keys.AUDIO_AUTOPLAY_NEXT, Keys.AUDIO_PRELOAD_BUFFER_SIZE,
            Keys.AUDIO_NIGHT_MODE_VOLUME, Keys.AUDIO_NIGHT_MODE_GAIN,
            Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS, Keys.REPLAYGAIN_PRE_AMP_DB,
            Keys.NIGHT_MODE_ENABLED, Keys.NIGHT_MODE_STRENGTH,
            Keys.DIALOGUE_BOOST_ENABLED, Keys.DIALOGUE_BOOST_STRENGTH,
            Keys.SLEEP_TIMER_DURATION_MS, Keys.SLEEP_TIMER_END_OF_EPISODE,
            Keys.AUDIO_LYRICS_VISIBLE,
        )
        PreferenceResetCategory.SUBTITLES_LANGUAGE -> listOf(
            Keys.PREFERRED_SUBTITLE_LANG, Keys.PREFERRED_AUDIO_LANG,
            Keys.SUBTITLES_FORCED_ONLY, Keys.SUBTITLE_PREVIEW_IN_SETTINGS,
            Keys.SUBTITLE_STYLE, Keys.HIGH_CONTRAST_SUBTITLES,
            Keys.PGS_SUBTITLE_DIRECT_PLAY, Keys.HDR_SUBTITLE_STYLE_ENABLED,
            Keys.HDR_SUBTITLE_STYLE, Keys.SUBTITLE_DELAY_BY_ITEM,
        )
        PreferenceResetCategory.DOWNLOADS_NETWORK -> listOf(
            Keys.WIFI_ONLY_DOWNLOADS, Keys.DOWNLOAD_CONNECTIONS,
            Keys.MAX_CONCURRENT_DOWNLOADS, Keys.DOWNLOAD_QUALITY,
            Keys.SMART_DOWNLOADS_ENABLED, Keys.AUTO_DOWNLOAD_NEW_EPISODES,
            Keys.MAX_DOWNLOAD_STORAGE_GB, Keys.DOWNLOAD_STORAGE_LOCATION,
            Keys.MAX_CACHE_SIZE_MB, Keys.AUTO_DELETE_CACHE,
            Keys.MANUAL_OFFLINE_ENABLED, Keys.AUTO_OFFLINE_ENABLED,
            Keys.MANUAL_BANDWIDTH_CAP, Keys.METERED_NETWORK_BEHAVIOR,
            Keys.ADAPTIVE_BITRATE_ENABLED, Keys.DATA_SAVER_ENABLED,
            Keys.VERBOSE_NETWORK_LOGGING, Keys.NETWORK_TIMEOUT_PRESET,
            Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB,
            Keys.DOWNLOAD_SCHEDULE_ENABLED, Keys.DOWNLOAD_SCHEDULE_START,
            Keys.DOWNLOAD_SCHEDULE_END, Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY,
        )
        PreferenceResetCategory.HOME_DISCOVERY -> listOf(
            Keys.HOME_MODE, Keys.HOME_HERO_ENABLED, Keys.HOME_BACKDROP_ENABLED,
            Keys.HOME_ENABLED_SECTION_TYPES, Keys.HOME_SECTION_ORDER,
            Keys.HOME_LIBRARY_SECTION_OVERRIDES, Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS,
            Keys.LIBRARY_VIEW_MODE, Keys.NAV_BAR_SHOW_LABELS,
            Keys.HIDE_BOTTOM_NAV_ON_SCROLL, Keys.NAV_ITEM_ORDER, Keys.HIDDEN_NAV_ITEMS,
            Keys.SHOW_UNWATCHED_BADGE, Keys.HIDE_WATCHED_ITEMS,
            Keys.SHOW_WATCHED_CHECKMARK, Keys.SHOW_EXTERNAL_RATINGS,
            Keys.MERGE_CONTINUE_WATCHING_NEXT_UP, Keys.NEXT_UP_MAX_DAYS,
            Keys.NEXT_UP_REWATCHING, Keys.NEXT_UP_EXCLUDED_SERIES_IDS,
            Keys.HIDDEN_CW_ITEM_IDS, Keys.PINNED_HOME_SECTIONS,
            Keys.HOME_LAYOUT_PRESETS, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR,
            Keys.DEFAULT_LIBRARY_SORT_ORDERS, Keys.LIBRARY_VIEW_MODES,
            Keys.LIBRARY_FILTERS,
            Keys.HIDE_EPISODE_THUMBNAILS, Keys.EPISODES_DESCENDING,
            Keys.SKIP_SPECIALS, Keys.SHOW_CLOCK_ON_HOME,
            Keys.SHOW_SETTINGS_IN_HOME_SEARCH,
        )
        PreferenceResetCategory.AUDIO_CACHE -> listOf(
            Keys.AUDIO_CACHING_ENABLED, Keys.AUDIO_CACHE_SIZE_MB,
            Keys.AUDIO_PREFETCH_LOOKAHEAD, Keys.AUDIO_PREFETCH_BACKFILL,
            Keys.AUDIO_CACHE_NETWORK_POLICY, Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB,
        )
        PreferenceResetCategory.SECURITY -> listOf(
            Keys.PIN_LOCK_ENABLED, Keys.PIN_HASH, Keys.BIOMETRIC_LOCK_ENABLED,
            Keys.USE_PIN_FOR_PLAYER_LOCK, Keys.AUTO_LOCK_TIMER_MS,
            Keys.REMOTE_CONTROL_ENABLED,
        )
        PreferenceResetCategory.NOTIFICATIONS -> listOf(
            Keys.NOTIFICATIONS_ENABLED, Keys.NOTIFICATIONS_CHECK_FREQUENCY,
            Keys.NOTIFICATIONS_QUIET_HOURS_ENABLED,
            Keys.NOTIFICATIONS_QUIET_HOURS_START, Keys.NOTIFICATIONS_QUIET_HOURS_END,
            Keys.NOTIFICATIONS_SOUND_ENABLED, Keys.NOTIFICATIONS_VIBRATE_ENABLED,
            Keys.NOTIFICATIONS_LIGHTS_ENABLED, Keys.NOTIFICATIONS_MAX_PER_CHECK,
            Keys.NOTIFICATIONS_LIBRARY_CONFIGS,
        )
        PreferenceResetCategory.SCREENSAVER -> listOf(
            Keys.DREAM_IMAGE_CATEGORIES, Keys.DREAM_TRANSITION_STYLE,
            Keys.DREAM_KEN_BURNS_ENABLED, Keys.DREAM_SHOW_TITLE,
            Keys.DREAM_SLIDESHOW_INTERVAL_MS,
        )
        PreferenceResetCategory.NEWSLETTER -> listOf(
            Keys.NEWSLETTER_ENABLED, Keys.NEWSLETTER_DAY_OF_WEEK,
            Keys.ENABLED_NEWSLETTER_SECTIONS, Keys.NEWSLETTER_SECTION_ORDER,
        )
        PreferenceResetCategory.SYNCPLAY_CASTING -> listOf(
            Keys.SYNC_PLAY_JOIN_BEHAVIOR, Keys.SYNC_PLAY_TOLERANCE_MS,
            Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES, Keys.DEFAULT_CASTING_STRATEGY,
            Keys.BACKGROUND_CASTING_ENABLED, Keys.PREFERRED_RENDERER,
            Keys.DVR_PRE_PADDING_MINUTES, Keys.DVR_POST_PADDING_MINUTES,
            Keys.DVR_RECORDING_QUALITY, Keys.LIVE_STREAM_OPTION,
        )
        PreferenceResetCategory.PLAYER_ENGINES -> listOf(
            Keys.MPV_CONFIG, Keys.LIBVLC_CONFIG, Keys.EXO_CONFIG,
        )
        PreferenceResetCategory.EXPERIMENTAL -> listOf(
            Keys.ENABLED_EXPERIMENTAL_FEATURES, Keys.SHOW_ADVANCED_SETTINGS,
        )
        PreferenceResetCategory.MISC_APP -> listOf(
            Keys.HAPTICS_ENABLED, Keys.SELF_UPDATE_CHECK_ENABLED,
            Keys.APP_LANGUAGE, Keys.USER_DATA_SYNC_ENABLED,
            Keys.SHOW_SHARE_MEDIA_OPTION, Keys.HIDE_SEARCH_HISTORY,
            Keys.ANDROID_TV_WATCH_NEXT_ENABLED, Keys.PREFER_AUDIO_DESCRIPTION,
        )
    }

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
     * Reflectively enumerates every `Preferences.Key<*>` declared in the private
     * [Keys] object. Uses Java reflection (no kotlin-reflect dependency) and is
     * only invoked from the debug/test coverage guard [uncoveredResetKeys], so
     * the reflection cost is never paid in production paths.
     */
    internal fun declaredKeys(): List<Preferences.Key<*>> {
        val keyType = androidx.datastore.preferences.core.Preferences.Key::class.java
        return Keys::class.java.declaredFields
            .filter { keyType.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
            .mapNotNull { runCatching { it.get(Keys) as? Preferences.Key<*> }.getOrNull() }
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
