package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_commercial
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_commercial_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_intro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_intro_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_outro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_outro_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_preview
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_preview_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_recap
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_recap_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_unknown
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_unknown_desc
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_android_tv_watch_next_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_android_tv_watch_next_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_delay_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_delay_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_passthrough_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_passthrough_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_autoplay_countdown_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_autoplay_countdown_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_autoplay_trailers_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_autoplay_trailers_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_background_audio_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_background_audio_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_background_casting_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_background_casting_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_casting_strategy_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_casting_strategy_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_cinema_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_cinema_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_controls_timeout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_controls_timeout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_decoder_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_decoder_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_default_aspect_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_default_aspect_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_default_brightness_level_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_default_brightness_level_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_default_speed_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_default_speed_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dialogue_boost_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dialogue_boost_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dialogue_boost_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dialogue_boost_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_duck_on_transient_focus_loss_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_duck_on_transient_focus_loss_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dvr_post_padding_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dvr_post_padding_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dvr_pre_padding_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dvr_pre_padding_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dvr_recording_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dvr_recording_quality_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_episode_browser_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_episode_browser_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_audio_offload_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_audio_offload_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_back_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_back_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_decoder_fallback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_decoder_fallback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_frame_rate_strategy_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_frame_rate_strategy_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_preferred_codecs_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_preferred_codecs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_skip_silence_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_skip_silence_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_video_scaling_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_video_scaling_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_frame_rate_matching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_frame_rate_matching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_gesture_indicator_side_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_gesture_indicator_side_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_gestures_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_gestures_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hold_speed_multiplier_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hold_speed_multiplier_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_incognito_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_incognito_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_keep_screen_on_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_keep_screen_on_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_live_stream_option_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_live_stream_option_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_fallback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_fallback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_output_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_output_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_buffer_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_buffer_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_debanding_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_debanding_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_extra_config_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_extra_config_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_frame_drop_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_frame_drop_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hwdec_override_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hwdec_override_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_interpolation_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_interpolation_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_scaler_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_scaler_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_skip_loop_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_skip_loop_filter_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_video_output_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_video_output_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_orientation_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_orientation_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pass_out_protection_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pass_out_protection_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pause_on_focus_loss_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pause_on_focus_loss_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_playback_metadata_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_playback_metadata_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_player_engine_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_player_engine_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preferred_renderer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preferred_renderer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preload_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_video_cache_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_video_cache_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preload_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_brightness_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_brightness_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reset_engine_defaults_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reset_engine_defaults_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seek_duration_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seek_duration_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_clock_player_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_clock_player_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_time_remaining_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_time_remaining_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_back_on_resume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_back_on_resume_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_streaming_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_streaming_quality_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_swipe_seek_range_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_swipe_seek_range_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_syncplay_auto_accept_invites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_syncplay_auto_accept_invites_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_syncplay_join_behavior_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_syncplay_join_behavior_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_syncplay_tolerance_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_syncplay_tolerance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_trickplay_on_gestures_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_trickplay_on_gestures_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_trickplay_preview_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_trickplay_preview_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_tv_zoom_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_tv_zoom_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_video_autoplay_next_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_video_autoplay_next_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_audio_output_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_audio_output_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_audio_time_stretch_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_audio_time_stretch_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_decoder_threads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_decoder_threads_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_drop_late_frames_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_drop_late_frames_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_network_caching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_network_caching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_skip_frames_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_skip_frames_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_skip_loop_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_skip_loop_filter_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_video_output_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_vlc_video_output_title

/**
 * Settings-search items for the "Playback Settings" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val PlaybackSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "player_engine",
        titleRes = Res.string.ss_player_engine_title,
        subtitleRes = Res.string.ss_player_engine_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerPlay
    ),
    SettingsSearchItem(
        id = "seek_duration",
        titleRes = Res.string.ss_seek_duration_title,
        subtitleRes = Res.string.ss_seek_duration_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("seek", "duration", "skip", "double tap", "seconds"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerTrackNext
    ),
    SettingsSearchItem(
        id = "orientation",
        titleRes = Res.string.ss_orientation_title,
        subtitleRes = Res.string.ss_orientation_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("orientation", "rotation", "landscape", "portrait", "sensor"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceMobileRotated
    ),
    SettingsSearchItem(
        id = "gestures",
        titleRes = Res.string.ss_gestures_title,
        subtitleRes = Res.string.ss_gestures_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("gestures", "swipe", "brightness", "volume", "seeking"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.HandMove
    ),
    SettingsSearchItem(
        id = "gesture_indicator_side",
        titleRes = Res.string.ss_gesture_indicator_side_title,
        subtitleRes = Res.string.ss_gesture_indicator_side_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("indicator", "brightness", "volume", "bar", "side", "gesture", "opposite"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal
    ),
    SettingsSearchItem(
        id = "default_speed",
        titleRes = Res.string.ss_default_speed_title,
        subtitleRes = Res.string.ss_default_speed_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("speed", "rate", "fast", "slow", "playback speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "default_aspect",
        titleRes = Res.string.ss_default_aspect_title,
        subtitleRes = Res.string.ss_default_aspect_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("aspect", "ratio", "stretch", "zoom", "fit", "fill"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight
    ),
    SettingsSearchItem(
        id = "video_autoplay_next",
        titleRes = Res.string.ss_video_autoplay_next_title,
        subtitleRes = Res.string.ss_video_autoplay_next_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("autoplay", "next", "continuous", "episode", "sequence"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward
    ),
    SettingsSearchItem(
        id = "autoplay_countdown",
        titleRes = Res.string.ss_autoplay_countdown_title,
        subtitleRes = Res.string.ss_autoplay_countdown_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("countdown", "timer", "autoplay", "next"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "controls_timeout",
        titleRes = Res.string.ss_controls_timeout_title,
        subtitleRes = Res.string.ss_controls_timeout_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("controls", "timeout", "hide", "overlay"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "skip_back_on_resume",
        titleRes = Res.string.ss_skip_back_on_resume_title,
        subtitleRes = Res.string.ss_skip_back_on_resume_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("skip", "back", "resume", "rewind", "unpause", "seek"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.History,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_clock_player",
        titleRes = Res.string.ss_show_clock_player_title,
        subtitleRes = Res.string.ss_show_clock_player_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("clock", "time", "player", "wall", "current"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "pass_out_protection",
        titleRes = Res.string.ss_pass_out_protection_title,
        subtitleRes = Res.string.ss_pass_out_protection_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("pass out", "fall asleep", "auto pause", "sleep", "hours"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "duck_on_transient_focus_loss",
        titleRes = Res.string.ss_duck_on_transient_focus_loss_title,
        subtitleRes = Res.string.ss_duck_on_transient_focus_loss_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("duck", "phone", "call", "focus", "transient", "volume", "rewind"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Phone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "autoplay_trailers",
        titleRes = Res.string.ss_autoplay_trailers_title,
        subtitleRes = Res.string.ss_autoplay_trailers_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("trailer", "autoplay", "preview", "details"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clipboard,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "cinema_mode",
        titleRes = Res.string.ss_cinema_mode_title,
        subtitleRes = Res.string.ss_cinema_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("cinema", "intro", "preroll", "pre-roll", "trailer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "episode_browser",
        titleRes = Res.string.ss_episode_browser_title,
        subtitleRes = Res.string.ss_episode_browser_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("episodes", "browser", "list", "in-player"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.List,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "playback_metadata",
        titleRes = Res.string.ss_playback_metadata_title,
        subtitleRes = Res.string.ss_playback_metadata_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("metadata", "codec", "bitrate", "stream stats", "debug"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.InfoCircle,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "swipe_seek_range",
        titleRes = Res.string.ss_swipe_seek_range_title,
        subtitleRes = Res.string.ss_swipe_seek_range_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("seek range", "swipe limit", "skip max"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBarRight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "remember_brightness",
        titleRes = Res.string.ss_remember_brightness_title,
        subtitleRes = Res.string.ss_remember_brightness_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("brightness", "remember", "save", "light"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BrightnessHalf,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "trickplay_preview",
        titleRes = Res.string.ss_trickplay_preview_title,
        subtitleRes = Res.string.ss_trickplay_preview_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("trickplay", "thumbnails", "scrubbing", "preview", "seek preview"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "preload_buffer",
        titleRes = Res.string.ss_preload_buffer_title,
        subtitleRes = Res.string.ss_preload_buffer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("buffer", "preload", "cache", "size", "network cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "video_cache_size",
        titleRes = Res.string.ss_video_cache_size_title,
        subtitleRes = Res.string.ss_video_cache_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("cache", "video cache", "size", "storage", "stream cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "background_audio",
        titleRes = Res.string.ss_background_audio_title,
        subtitleRes = Res.string.ss_background_audio_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("background", "audio", "video background", "pip"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "keep_screen_on",
        titleRes = Res.string.ss_keep_screen_on_title,
        subtitleRes = Res.string.ss_keep_screen_on_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("screen", "awake", "lock", "stay on", "timeout"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "incognito_mode",
        titleRes = Res.string.ss_incognito_mode_title,
        subtitleRes = Res.string.ss_incognito_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("incognito", "private", "history", "stealth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Ghost,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "dialogue_boost",
        titleRes = Res.string.ss_dialogue_boost_title,
        subtitleRes = Res.string.ss_dialogue_boost_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "dialogue_boost_strength",
        titleRes = Res.string.ss_dialogue_boost_strength_title,
        subtitleRes = Res.string.ss_dialogue_boost_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "decoder",
        titleRes = Res.string.ss_decoder_title,
        subtitleRes = Res.string.ss_decoder_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("decoder", "hardware", "software", "decoding", "codec"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_passthrough",
        titleRes = Res.string.ss_audio_passthrough_title,
        subtitleRes = Res.string.ss_audio_passthrough_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("passthrough", "surround", "hdmi", "receiver", "raw"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Movie,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "frame_rate_matching",
        titleRes = Res.string.ss_frame_rate_matching_title,
        subtitleRes = Res.string.ss_frame_rate_matching_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("refresh rate", "frame rate", "hz", "judder", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Maximize,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "streaming_quality",
        titleRes = Res.string.ss_streaming_quality_title,
        subtitleRes = Res.string.ss_streaming_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("quality", "streaming", "resolution", "4k", "1080p", "sd"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_delay",
        titleRes = Res.string.ss_audio_delay_title,
        subtitleRes = Res.string.ss_audio_delay_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "live_stream_option",
        titleRes = Res.string.ss_live_stream_option_title,
        subtitleRes = Res.string.ss_live_stream_option_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("live tv", "direct stream", "transcode", "tuner", "htsp", "tvheadend", "channel", "mpeg-ts", "mpeg ts", "broadcast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceTv,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hold_speed_multiplier",
        titleRes = Res.string.ss_hold_speed_multiplier_title,
        subtitleRes = Res.string.ss_hold_speed_multiplier_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("hold", "seek", "speed", "multiplier", "fast", "fast forward", "rewind", "long press", "off", "disable"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Rocket
    ),
    SettingsSearchItem(
        id = "android_tv_watch_next",
        titleRes = Res.string.ss_android_tv_watch_next_title,
        subtitleRes = Res.string.ss_android_tv_watch_next_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("android tv", "watch next", "home", "tv", "continue"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceTv,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "tv_zoom_mode",
        titleRes = Res.string.ss_tv_zoom_mode_title,
        subtitleRes = Res.string.ss_tv_zoom_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("tv", "zoom", "crop", "fill", "screen"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Crop,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "default_brightness_level",
        titleRes = Res.string.ss_default_brightness_level_title,
        subtitleRes = Res.string.ss_default_brightness_level_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("brightness", "default", "screen", "light", "level"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Sun,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "trickplay_on_gestures",
        titleRes = Res.string.ss_trickplay_on_gestures_title,
        subtitleRes = Res.string.ss_trickplay_on_gestures_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("trickplay", "thumbnails", "gesture", "swipe", "seek"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.HandMove,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_time_remaining",
        titleRes = Res.string.ss_show_time_remaining_title,
        subtitleRes = Res.string.ss_show_time_remaining_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("time", "remaining", "elapsed", "duration", "countdown"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "pause_on_focus_loss",
        titleRes = Res.string.ss_pause_on_focus_loss_title,
        subtitleRes = Res.string.ss_pause_on_focus_loss_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
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
        titleRes = Res.string.ss_mpv_video_output_title,
        subtitleRes = Res.string.ss_mpv_video_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "video output", "vo", "gpu", "render"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_scaler",
        titleRes = Res.string.ss_mpv_scaler_title,
        subtitleRes = Res.string.ss_mpv_scaler_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "scaler", "scaling", "interpolation", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_debanding",
        titleRes = Res.string.ss_mpv_debanding_title,
        subtitleRes = Res.string.ss_mpv_debanding_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "deband", "debanding", "banding", "gradient"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ColorFilter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_interpolation",
        titleRes = Res.string.ss_mpv_interpolation_title,
        subtitleRes = Res.string.ss_mpv_interpolation_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "interpolation", "smooth", "motion", "judder"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_audio_output",
        titleRes = Res.string.ss_mpv_audio_output_title,
        subtitleRes = Res.string.ss_mpv_audio_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "audio output", "ao", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_audio_fallback",
        titleRes = Res.string.ss_mpv_audio_fallback_title,
        subtitleRes = Res.string.ss_mpv_audio_fallback_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "audio", "fallback", "secondary", "output"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBack,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_buffer_size",
        titleRes = Res.string.ss_mpv_buffer_size_title,
        subtitleRes = Res.string.ss_mpv_buffer_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "buffer", "demuxer", "size", "bytes", "cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_hwdec_override",
        titleRes = Res.string.ss_mpv_hwdec_override_title,
        subtitleRes = Res.string.ss_mpv_hwdec_override_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "hardware", "hwdec", "decoder", "override", "gpu"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_skip_loop_filter",
        titleRes = Res.string.ss_mpv_skip_loop_filter_title,
        subtitleRes = Res.string.ss_mpv_skip_loop_filter_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "skip", "loop filter", "h264", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_frame_drop",
        titleRes = Res.string.ss_mpv_frame_drop_title,
        subtitleRes = Res.string.ss_mpv_frame_drop_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "frame", "drop", "vdrop", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PhotoDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "mpv_extra_config",
        titleRes = Res.string.ss_mpv_extra_config_title,
        subtitleRes = Res.string.ss_mpv_extra_config_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "advanced", "config", "raw", "options", "editor", "custom"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "reset_engine_defaults",
        titleRes = Res.string.ss_reset_engine_defaults_title,
        subtitleRes = Res.string.ss_reset_engine_defaults_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
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
        titleRes = Res.string.ss_vlc_audio_output_title,
        subtitleRes = Res.string.ss_vlc_audio_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "libvlc", "audio output", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_audio_time_stretch",
        titleRes = Res.string.ss_vlc_audio_time_stretch_title,
        subtitleRes = Res.string.ss_vlc_audio_time_stretch_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "time stretch", "pitch", "speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_video_output",
        titleRes = Res.string.ss_vlc_video_output_title,
        subtitleRes = Res.string.ss_vlc_video_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "libvlc", "video output", "display"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_network_caching",
        titleRes = Res.string.ss_vlc_network_caching_title,
        subtitleRes = Res.string.ss_vlc_network_caching_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "network", "caching", "buffer", "streaming"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_skip_loop_filter",
        titleRes = Res.string.ss_vlc_skip_loop_filter_title,
        subtitleRes = Res.string.ss_vlc_skip_loop_filter_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "skip", "loop filter", "h264", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_skip_frames",
        titleRes = Res.string.ss_vlc_skip_frames_title,
        subtitleRes = Res.string.ss_vlc_skip_frames_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "skip frames", "performance", "frame"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_decoder_threads",
        titleRes = Res.string.ss_vlc_decoder_threads_title,
        subtitleRes = Res.string.ss_vlc_decoder_threads_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "decoder", "threads", "cpu", "multithreading"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "vlc_drop_late_frames",
        titleRes = Res.string.ss_vlc_drop_late_frames_title,
        subtitleRes = Res.string.ss_vlc_drop_late_frames_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
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
        titleRes = Res.string.ss_exo_video_scaling_title,
        subtitleRes = Res.string.ss_exo_video_scaling_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "scaling", "video", "resize"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_frame_rate_strategy",
        titleRes = Res.string.ss_exo_frame_rate_strategy_title,
        subtitleRes = Res.string.ss_exo_frame_rate_strategy_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "frame rate", "refresh", "strategy"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_skip_silence",
        titleRes = Res.string.ss_exo_skip_silence_title,
        subtitleRes = Res.string.ss_exo_skip_silence_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "skip", "silence", "audio", "gap"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_audio_offload",
        titleRes = Res.string.ss_exo_audio_offload_title,
        subtitleRes = Res.string.ss_exo_audio_offload_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "audio", "offload", "battery"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Headphones,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_decoder_fallback",
        titleRes = Res.string.ss_exo_decoder_fallback_title,
        subtitleRes = Res.string.ss_exo_decoder_fallback_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "decoder", "fallback", "secondary"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ToggleLeft,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_back_buffer",
        titleRes = Res.string.ss_exo_back_buffer_title,
        subtitleRes = Res.string.ss_exo_back_buffer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "back buffer", "rewind", "buffer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "exo_preferred_codecs",
        titleRes = Res.string.ss_exo_preferred_codecs_title,
        subtitleRes = Res.string.ss_exo_preferred_codecs_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
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
        titleRes = Res.string.ss_syncplay_join_behavior_title,
        subtitleRes = Res.string.ss_syncplay_join_behavior_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.MessageQuestion
    ),
    SettingsSearchItem(
        id = "syncplay_tolerance",
        titleRes = Res.string.ss_syncplay_tolerance_title,
        subtitleRes = Res.string.ss_syncplay_tolerance_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.WaveSine
    ),
    SettingsSearchItem(
        id = "syncplay_auto_accept_invites",
        titleRes = Res.string.ss_syncplay_auto_accept_invites_title,
        subtitleRes = Res.string.ss_syncplay_auto_accept_invites_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
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
        titleRes = Res.string.ss_casting_strategy_title,
        subtitleRes = Res.string.ss_casting_strategy_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cast
    ),
    SettingsSearchItem(
        id = "background_casting",
        titleRes = Res.string.ss_background_casting_title,
        subtitleRes = Res.string.ss_background_casting_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Settings
    ),
    SettingsSearchItem(
        id = "preferred_renderer",
        titleRes = Res.string.ss_preferred_renderer_title,
        subtitleRes = Res.string.ss_preferred_renderer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
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
        titleRes = Res.string.ss_dvr_pre_padding_title,
        subtitleRes = Res.string.ss_dvr_pre_padding_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "dvr_post_padding",
        titleRes = Res.string.ss_dvr_post_padding_title,
        subtitleRes = Res.string.ss_dvr_post_padding_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "dvr_recording_quality",
        titleRes = Res.string.ss_dvr_recording_quality_title,
        subtitleRes = Res.string.ss_dvr_recording_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_intro",
        titleRes = CoreUiRes.string.core_segment_intro,
        subtitleRes = CoreUiRes.string.core_segment_intro_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "intro", "skip", "opening", "credits", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_outro",
        titleRes = CoreUiRes.string.core_segment_outro,
        subtitleRes = CoreUiRes.string.core_segment_outro_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "outro", "ending", "skip", "credits", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_preview",
        titleRes = CoreUiRes.string.core_segment_preview,
        subtitleRes = CoreUiRes.string.core_segment_preview_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "preview", "next episode", "recap", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_recap",
        titleRes = CoreUiRes.string.core_segment_recap,
        subtitleRes = CoreUiRes.string.core_segment_recap_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "recap", "previously on", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_commercial",
        titleRes = CoreUiRes.string.core_segment_commercial,
        subtitleRes = CoreUiRes.string.core_segment_commercial_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "commercial", "ad", "advertisement", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "media_segment_unknown",
        titleRes = CoreUiRes.string.core_segment_unknown,
        subtitleRes = CoreUiRes.string.core_segment_unknown_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "unknown", "skip", "marker", "unidentified"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
)
