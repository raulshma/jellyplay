package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Playback Settings" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val PlaybackSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "player_engine",
        titleRes = R.string.ss_player_engine_title,
        subtitleRes = R.string.ss_player_engine_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerPlay
    ),
    SettingsSearchItem(
        id = "seek_duration",
        titleRes = R.string.ss_seek_duration_title,
        subtitleRes = R.string.ss_seek_duration_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("seek", "duration", "skip", "double tap", "seconds"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerTrackNext
    ),
    SettingsSearchItem(
        id = "orientation",
        titleRes = R.string.ss_orientation_title,
        subtitleRes = R.string.ss_orientation_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("orientation", "rotation", "landscape", "portrait", "sensor"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceMobileRotated
    ),
    SettingsSearchItem(
        id = "gestures",
        titleRes = R.string.ss_gestures_title,
        subtitleRes = R.string.ss_gestures_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("gestures", "swipe", "brightness", "volume", "seeking"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.HandMove
    ),
    SettingsSearchItem(
        id = "gesture_indicator_side",
        titleRes = R.string.ss_gesture_indicator_side_title,
        subtitleRes = R.string.ss_gesture_indicator_side_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("indicator", "brightness", "volume", "bar", "side", "gesture", "opposite"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal
    ),
    SettingsSearchItem(
        id = "default_speed",
        titleRes = R.string.ss_default_speed_title,
        subtitleRes = R.string.ss_default_speed_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("speed", "rate", "fast", "slow", "playback speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "default_aspect",
        titleRes = R.string.ss_default_aspect_title,
        subtitleRes = R.string.ss_default_aspect_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("aspect", "ratio", "stretch", "zoom", "fit", "fill"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight
    ),
    SettingsSearchItem(
        id = "video_autoplay_next",
        titleRes = R.string.ss_video_autoplay_next_title,
        subtitleRes = R.string.ss_video_autoplay_next_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("autoplay", "next", "continuous", "episode", "sequence"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward
    ),
    SettingsSearchItem(
        id = "autoplay_countdown",
        titleRes = R.string.ss_autoplay_countdown_title,
        subtitleRes = R.string.ss_autoplay_countdown_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("countdown", "timer", "autoplay", "next"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "controls_timeout",
        titleRes = R.string.ss_controls_timeout_title,
        subtitleRes = R.string.ss_controls_timeout_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("controls", "timeout", "hide", "overlay"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "skip_back_on_resume",
        titleRes = R.string.ss_skip_back_on_resume_title,
        subtitleRes = R.string.ss_skip_back_on_resume_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("skip", "back", "resume", "rewind", "unpause", "seek"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.History,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_clock_player",
        titleRes = R.string.ss_show_clock_player_title,
        subtitleRes = R.string.ss_show_clock_player_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("clock", "time", "player", "wall", "current"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "pass_out_protection",
        titleRes = R.string.ss_pass_out_protection_title,
        subtitleRes = R.string.ss_pass_out_protection_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("pass out", "fall asleep", "auto pause", "sleep", "hours"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "duck_on_transient_focus_loss",
        titleRes = R.string.ss_duck_on_transient_focus_loss_title,
        subtitleRes = R.string.ss_duck_on_transient_focus_loss_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("duck", "phone", "call", "focus", "transient", "volume", "rewind"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Phone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "autoplay_trailers",
        titleRes = R.string.ss_autoplay_trailers_title,
        subtitleRes = R.string.ss_autoplay_trailers_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("trailer", "autoplay", "preview", "details"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clipboard,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "cinema_mode",
        titleRes = R.string.ss_cinema_mode_title,
        subtitleRes = R.string.ss_cinema_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("cinema", "intro", "preroll", "pre-roll", "trailer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "episode_browser",
        titleRes = R.string.ss_episode_browser_title,
        subtitleRes = R.string.ss_episode_browser_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("episodes", "browser", "list", "in-player"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.List,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "playback_metadata",
        titleRes = R.string.ss_playback_metadata_title,
        subtitleRes = R.string.ss_playback_metadata_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("metadata", "codec", "bitrate", "stream stats", "debug"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.InfoCircle,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "swipe_seek_range",
        titleRes = R.string.ss_swipe_seek_range_title,
        subtitleRes = R.string.ss_swipe_seek_range_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("seek range", "swipe limit", "skip max"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBarRight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "remember_brightness",
        titleRes = R.string.ss_remember_brightness_title,
        subtitleRes = R.string.ss_remember_brightness_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("brightness", "remember", "save", "light"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BrightnessHalf,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "trickplay_preview",
        titleRes = R.string.ss_trickplay_preview_title,
        subtitleRes = R.string.ss_trickplay_preview_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("trickplay", "thumbnails", "scrubbing", "preview", "seek preview"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "preload_buffer",
        titleRes = R.string.ss_preload_buffer_title,
        subtitleRes = R.string.ss_preload_buffer_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("buffer", "preload", "cache", "size", "network cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "background_audio",
        titleRes = R.string.ss_background_audio_title,
        subtitleRes = R.string.ss_background_audio_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("background", "audio", "video background", "pip"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "keep_screen_on",
        titleRes = R.string.ss_keep_screen_on_title,
        subtitleRes = R.string.ss_keep_screen_on_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("screen", "awake", "lock", "stay on", "timeout"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "incognito_mode",
        titleRes = R.string.ss_incognito_mode_title,
        subtitleRes = R.string.ss_incognito_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("incognito", "private", "history", "stealth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Ghost,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "dialogue_boost",
        titleRes = R.string.ss_dialogue_boost_title,
        subtitleRes = R.string.ss_dialogue_boost_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "dialogue_boost_strength",
        titleRes = R.string.ss_dialogue_boost_strength_title,
        subtitleRes = R.string.ss_dialogue_boost_strength_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "decoder",
        titleRes = R.string.ss_decoder_title,
        subtitleRes = R.string.ss_decoder_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("decoder", "hardware", "software", "decoding", "codec"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_passthrough",
        titleRes = R.string.ss_audio_passthrough_title,
        subtitleRes = R.string.ss_audio_passthrough_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("passthrough", "surround", "hdmi", "receiver", "raw"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Movie,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "frame_rate_matching",
        titleRes = R.string.ss_frame_rate_matching_title,
        subtitleRes = R.string.ss_frame_rate_matching_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("refresh rate", "frame rate", "hz", "judder", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Maximize,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "streaming_quality",
        titleRes = R.string.ss_streaming_quality_title,
        subtitleRes = R.string.ss_streaming_quality_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("quality", "streaming", "resolution", "4k", "1080p", "sd"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_delay",
        titleRes = R.string.ss_audio_delay_title,
        subtitleRes = R.string.ss_audio_delay_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "live_stream_option",
        titleRes = R.string.ss_live_stream_option_title,
        subtitleRes = R.string.ss_live_stream_option_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("live tv", "direct stream", "transcode", "tuner", "htsp", "tvheadend", "channel", "mpeg-ts", "mpeg ts", "broadcast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceTv,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hold_speed_multiplier",
        titleRes = R.string.ss_hold_speed_multiplier_title,
        subtitleRes = R.string.ss_hold_speed_multiplier_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("hold", "seek", "speed", "multiplier", "fast", "fast forward", "rewind", "long press", "off", "disable"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Rocket
    ),
    SettingsSearchItem(
        id = "android_tv_watch_next",
        titleRes = R.string.ss_android_tv_watch_next_title,
        subtitleRes = R.string.ss_android_tv_watch_next_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("android tv", "watch next", "home", "tv", "continue"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceTv,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "tv_zoom_mode",
        titleRes = R.string.ss_tv_zoom_mode_title,
        subtitleRes = R.string.ss_tv_zoom_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("tv", "zoom", "crop", "fill", "screen"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Crop,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "default_brightness_level",
        titleRes = R.string.ss_default_brightness_level_title,
        subtitleRes = R.string.ss_default_brightness_level_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("brightness", "default", "screen", "light", "level"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Sun,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "trickplay_on_gestures",
        titleRes = R.string.ss_trickplay_on_gestures_title,
        subtitleRes = R.string.ss_trickplay_on_gestures_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("trickplay", "thumbnails", "gesture", "swipe", "seek"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.HandMove,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_time_remaining",
        titleRes = R.string.ss_show_time_remaining_title,
        subtitleRes = R.string.ss_show_time_remaining_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("time", "remaining", "elapsed", "duration", "countdown"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "pause_on_focus_loss",
        titleRes = R.string.ss_pause_on_focus_loss_title,
        subtitleRes = R.string.ss_pause_on_focus_loss_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("pause", "focus", "loss", "audio focus", "interruption"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerPause,
        isAdvanced = true
    ),
)

/**
 * Settings-search items for the "MPV Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val MpvEngineSearchItems = listOf(
    SettingsSearchItem(
        id = "mpv_video_output",
        titleRes = R.string.ss_mpv_video_output_title,
        subtitleRes = R.string.ss_mpv_video_output_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "video output", "vo", "gpu", "render"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_scaler",
        titleRes = R.string.ss_mpv_scaler_title,
        subtitleRes = R.string.ss_mpv_scaler_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "scaler", "scaling", "interpolation", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_debanding",
        titleRes = R.string.ss_mpv_debanding_title,
        subtitleRes = R.string.ss_mpv_debanding_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "deband", "debanding", "banding", "gradient"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ColorFilter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_interpolation",
        titleRes = R.string.ss_mpv_interpolation_title,
        subtitleRes = R.string.ss_mpv_interpolation_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "interpolation", "smooth", "motion", "judder"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_audio_output",
        titleRes = R.string.ss_mpv_audio_output_title,
        subtitleRes = R.string.ss_mpv_audio_output_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "audio output", "ao", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_audio_fallback",
        titleRes = R.string.ss_mpv_audio_fallback_title,
        subtitleRes = R.string.ss_mpv_audio_fallback_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "audio", "fallback", "secondary", "output"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBack,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_buffer_size",
        titleRes = R.string.ss_mpv_buffer_size_title,
        subtitleRes = R.string.ss_mpv_buffer_size_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "buffer", "demuxer", "size", "bytes", "cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_hwdec_override",
        titleRes = R.string.ss_mpv_hwdec_override_title,
        subtitleRes = R.string.ss_mpv_hwdec_override_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "hardware", "hwdec", "decoder", "override", "gpu"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_skip_loop_filter",
        titleRes = R.string.ss_mpv_skip_loop_filter_title,
        subtitleRes = R.string.ss_mpv_skip_loop_filter_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "skip", "loop filter", "h264", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_frame_drop",
        titleRes = R.string.ss_mpv_frame_drop_title,
        subtitleRes = R.string.ss_mpv_frame_drop_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "frame", "drop", "vdrop", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PhotoDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_extra_config",
        titleRes = R.string.ss_mpv_extra_config_title,
        subtitleRes = R.string.ss_mpv_extra_config_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("mpv", "advanced", "config", "raw", "options", "editor", "custom"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "reset_engine_defaults",
        titleRes = R.string.ss_reset_engine_defaults_title,
        subtitleRes = R.string.ss_reset_engine_defaults_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("reset", "defaults", "restore", "engine", "mpv", "vlc", "exoplayer", "configuration"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
)

/**
 * Settings-search items for the "VLC Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val VlcEngineSearchItems = listOf(
    SettingsSearchItem(
        id = "vlc_audio_output",
        titleRes = R.string.ss_vlc_audio_output_title,
        subtitleRes = R.string.ss_vlc_audio_output_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "libvlc", "audio output", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_audio_time_stretch",
        titleRes = R.string.ss_vlc_audio_time_stretch_title,
        subtitleRes = R.string.ss_vlc_audio_time_stretch_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "time stretch", "pitch", "speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_video_output",
        titleRes = R.string.ss_vlc_video_output_title,
        subtitleRes = R.string.ss_vlc_video_output_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "libvlc", "video output", "display"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_network_caching",
        titleRes = R.string.ss_vlc_network_caching_title,
        subtitleRes = R.string.ss_vlc_network_caching_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "network", "caching", "buffer", "streaming"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_skip_loop_filter",
        titleRes = R.string.ss_vlc_skip_loop_filter_title,
        subtitleRes = R.string.ss_vlc_skip_loop_filter_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "skip", "loop filter", "h264", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_skip_frames",
        titleRes = R.string.ss_vlc_skip_frames_title,
        subtitleRes = R.string.ss_vlc_skip_frames_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "skip frames", "performance", "frame"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_decoder_threads",
        titleRes = R.string.ss_vlc_decoder_threads_title,
        subtitleRes = R.string.ss_vlc_decoder_threads_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "decoder", "threads", "cpu", "multithreading"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_drop_late_frames",
        titleRes = R.string.ss_vlc_drop_late_frames_title,
        subtitleRes = R.string.ss_vlc_drop_late_frames_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("vlc", "drop", "late", "frames", "delayed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Trash,
        isAdvanced = true
    ),
)

/**
 * Settings-search items for the "ExoPlayer Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val ExoPlayerEngineSearchItems = listOf(
    SettingsSearchItem(
        id = "exo_video_scaling",
        titleRes = R.string.ss_exo_video_scaling_title,
        subtitleRes = R.string.ss_exo_video_scaling_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "scaling", "video", "resize"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_frame_rate_strategy",
        titleRes = R.string.ss_exo_frame_rate_strategy_title,
        subtitleRes = R.string.ss_exo_frame_rate_strategy_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "frame rate", "refresh", "strategy"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_skip_silence",
        titleRes = R.string.ss_exo_skip_silence_title,
        subtitleRes = R.string.ss_exo_skip_silence_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "skip", "silence", "audio", "gap"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_audio_offload",
        titleRes = R.string.ss_exo_audio_offload_title,
        subtitleRes = R.string.ss_exo_audio_offload_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "audio", "offload", "battery"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Headphones,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_decoder_fallback",
        titleRes = R.string.ss_exo_decoder_fallback_title,
        subtitleRes = R.string.ss_exo_decoder_fallback_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "decoder", "fallback", "secondary"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ToggleLeft,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_back_buffer",
        titleRes = R.string.ss_exo_back_buffer_title,
        subtitleRes = R.string.ss_exo_back_buffer_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "back buffer", "rewind", "buffer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_preferred_codecs",
        titleRes = R.string.ss_exo_preferred_codecs_title,
        subtitleRes = R.string.ss_exo_preferred_codecs_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "codec", "mime", "preferred", "video"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
)

/**
 * Settings-search items for the "SyncPlay" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val SyncPlaySearchItems = listOf(
    SettingsSearchItem(
        id = "syncplay_join_behavior",
        titleRes = R.string.ss_syncplay_join_behavior_title,
        subtitleRes = R.string.ss_syncplay_join_behavior_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.MessageQuestion
    ),
    SettingsSearchItem(
        id = "syncplay_tolerance",
        titleRes = R.string.ss_syncplay_tolerance_title,
        subtitleRes = R.string.ss_syncplay_tolerance_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.WaveSine
    ),
    SettingsSearchItem(
        id = "syncplay_auto_accept_invites",
        titleRes = R.string.ss_syncplay_auto_accept_invites_title,
        subtitleRes = R.string.ss_syncplay_auto_accept_invites_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("syncplay", "auto", "accept", "invites", "friends"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.CircleCheck
    ),
)

/**
 * Settings-search items for the "Casting & DLNA" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val CastingSearchItems = listOf(
    SettingsSearchItem(
        id = "casting_strategy",
        titleRes = R.string.ss_casting_strategy_title,
        subtitleRes = R.string.ss_casting_strategy_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cast
    ),
    SettingsSearchItem(
        id = "background_casting",
        titleRes = R.string.ss_background_casting_title,
        subtitleRes = R.string.ss_background_casting_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Settings
    ),
    SettingsSearchItem(
        id = "preferred_renderer",
        titleRes = R.string.ss_preferred_renderer_title,
        subtitleRes = R.string.ss_preferred_renderer_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("renderer", "preferred", "cast", "device", "target", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Devices
    ),
)

/**
 * Settings-search items for the "Live TV & DVR" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val LiveTvSearchItems = listOf(
    SettingsSearchItem(
        id = "dvr_pre_padding",
        titleRes = R.string.ss_dvr_pre_padding_title,
        subtitleRes = R.string.ss_dvr_pre_padding_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "dvr_post_padding",
        titleRes = R.string.ss_dvr_post_padding_title,
        subtitleRes = R.string.ss_dvr_post_padding_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "dvr_recording_quality",
        titleRes = R.string.ss_dvr_recording_quality_title,
        subtitleRes = R.string.ss_dvr_recording_quality_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_intro",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_intro,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_intro_desc,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("segment", "intro", "skip", "opening", "credits", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_outro",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_outro,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_outro_desc,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("segment", "outro", "ending", "skip", "credits", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_preview",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_preview,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_preview_desc,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("segment", "preview", "next episode", "recap", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_recap",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_recap,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_recap_desc,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("segment", "recap", "previously on", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_commercial",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_commercial,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_commercial_desc,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("segment", "commercial", "ad", "advertisement", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_unknown",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_unknown,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_segment_unknown_desc,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_playback,
        keywords = listOf("segment", "unknown", "skip", "marker", "unidentified"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
)
