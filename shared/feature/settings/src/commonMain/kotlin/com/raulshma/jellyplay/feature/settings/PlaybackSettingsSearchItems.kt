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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_device_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_device_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_exclusive_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_exclusive_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hdr_passthrough_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hdr_passthrough_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_render_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_render_quality_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_shader_pack_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_shader_pack_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tone_mapping_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tone_mapping_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tscale_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tscale_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_mode_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_volume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_volume_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_segments_on_seek_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_segments_on_seek_title
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
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object PlaybackSettingsIds {
    const val PLAYER_ENGINE = "player_engine"
    const val SEEK_DURATION = "seek_duration"
    const val ORIENTATION = "orientation"
    const val GESTURES = "gestures"
    const val GESTURE_INDICATOR_SIDE = "gesture_indicator_side"
    const val DEFAULT_SPEED = "default_speed"
    const val DEFAULT_ASPECT = "default_aspect"
    const val VIDEO_AUTOPLAY_NEXT = "video_autoplay_next"
    const val AUTOPLAY_COUNTDOWN = "autoplay_countdown"
    const val CONTROLS_TIMEOUT = "controls_timeout"
    const val SKIP_BACK_ON_RESUME = "skip_back_on_resume"
    const val SHOW_CLOCK_PLAYER = "show_clock_player"
    const val PASS_OUT_PROTECTION = "pass_out_protection"
    const val DUCK_ON_TRANSIENT_FOCUS_LOSS = "duck_on_transient_focus_loss"
    const val AUTOPLAY_TRAILERS = "autoplay_trailers"
    const val CINEMA_MODE = "cinema_mode"
    const val EPISODE_BROWSER = "episode_browser"
    const val PLAYBACK_METADATA = "playback_metadata"
    const val SWIPE_SEEK_RANGE = "swipe_seek_range"
    const val REMEMBER_BRIGHTNESS = "remember_brightness"
    const val TRICKPLAY_PREVIEW = "trickplay_preview"
    const val PRELOAD_BUFFER = "preload_buffer"
    const val VIDEO_CACHE_SIZE = "video_cache_size"
    const val BACKGROUND_AUDIO = "background_audio"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val INCOGNITO_MODE = "incognito_mode"
    const val HOLD_SPEED_MULTIPLIER = "hold_speed_multiplier"
    const val ANDROID_TV_WATCH_NEXT = "android_tv_watch_next"
    const val TV_ZOOM_MODE = "tv_zoom_mode"
    const val DEFAULT_BRIGHTNESS_LEVEL = "default_brightness_level"
    const val TRICKPLAY_ON_GESTURES = "trickplay_on_gestures"
    const val SHOW_TIME_REMAINING = "show_time_remaining"
    const val PAUSE_ON_FOCUS_LOSS = "pause_on_focus_loss"
    const val DIALOGUE_BOOST = "dialogue_boost"
    const val DIALOGUE_BOOST_STRENGTH = "dialogue_boost_strength"
    const val DECODER = "decoder"
    const val AUDIO_PASSTHROUGH = "audio_passthrough"
    const val FRAME_RATE_MATCHING = "frame_rate_matching"
    const val STREAMING_QUALITY = "streaming_quality"
    const val AUDIO_DELAY = "audio_delay"
    const val LIVE_STREAM_OPTION = "live_stream_option"
    const val MPV_VIDEO_OUTPUT = "mpv_video_output"
    const val MPV_SCALER = "mpv_scaler"
    const val MPV_DEBANDING = "mpv_debanding"
    const val MPV_INTERPOLATION = "mpv_interpolation"
    const val MPV_AUDIO_OUTPUT = "mpv_audio_output"
    const val MPV_AUDIO_FALLBACK = "mpv_audio_fallback"
    const val MPV_AUDIO_DEVICE = "mpv_audio_device"
    const val MPV_AUDIO_EXCLUSIVE = "mpv_audio_exclusive"
    const val MPV_AUDIO_MODE = "mpv_audio_mode"
    const val MPV_SHADER_PACK = "mpv_shader_pack"
    const val MPV_TONE_MAPPING = "mpv_tone_mapping"
    const val MPV_RENDER_QUALITY = "mpv_render_quality"
    const val MPV_HDR_PASSTHROUGH = "mpv_hdr_passthrough"
    const val MPV_INTERPOLATION_TSCALE = "mpv_interpolation_tscale"
    const val MPV_BUFFER_SIZE = "mpv_buffer_size"
    const val MPV_HWDEC_OVERRIDE = "mpv_hwdec_override"
    const val MPV_SKIP_LOOP_FILTER = "mpv_skip_loop_filter"
    const val MPV_FRAME_DROP = "mpv_frame_drop"
    const val MPV_EXTRA_CONFIG = "mpv_extra_config"
    const val RESET_ENGINE_DEFAULTS = "reset_engine_defaults"
    const val VLC_AUDIO_OUTPUT = "vlc_audio_output"
    const val VLC_AUDIO_TIME_STRETCH = "vlc_audio_time_stretch"
    const val VLC_VIDEO_OUTPUT = "vlc_video_output"
    const val VLC_NETWORK_CACHING = "vlc_network_caching"
    const val VLC_SKIP_LOOP_FILTER = "vlc_skip_loop_filter"
    const val VLC_SKIP_FRAMES = "vlc_skip_frames"
    const val VLC_DECODER_THREADS = "vlc_decoder_threads"
    const val VLC_DROP_LATE_FRAMES = "vlc_drop_late_frames"
    const val EXO_VIDEO_SCALING = "exo_video_scaling"
    const val EXO_FRAME_RATE_STRATEGY = "exo_frame_rate_strategy"
    const val EXO_SKIP_SILENCE = "exo_skip_silence"
    const val EXO_AUDIO_OFFLOAD = "exo_audio_offload"
    const val EXO_DECODER_FALLBACK = "exo_decoder_fallback"
    const val EXO_BACK_BUFFER = "exo_back_buffer"
    const val EXO_PREFERRED_CODECS = "exo_preferred_codecs"
    const val SYNCPLAY_JOIN_BEHAVIOR = "syncplay_join_behavior"
    const val SYNCPLAY_TOLERANCE = "syncplay_tolerance"
    const val SYNCPLAY_AUTO_ACCEPT_INVITES = "syncplay_auto_accept_invites"
    const val CASTING_STRATEGY = "casting_strategy"
    const val BACKGROUND_CASTING = "background_casting"
    const val PREFERRED_RENDERER = "preferred_renderer"
    const val DVR_PRE_PADDING = "dvr_pre_padding"
    const val DVR_POST_PADDING = "dvr_post_padding"
    const val DVR_RECORDING_QUALITY = "dvr_recording_quality"
    const val MEDIA_SEGMENT_INTRO = "media_segment_intro"
    const val MEDIA_SEGMENT_OUTRO = "media_segment_outro"
    const val MEDIA_SEGMENT_PREVIEW = "media_segment_preview"
    const val MEDIA_SEGMENT_RECAP = "media_segment_recap"
    const val MEDIA_SEGMENT_COMMERCIAL = "media_segment_commercial"
    const val MEDIA_SEGMENT_UNKNOWN = "media_segment_unknown"
    const val SKIP_SEGMENTS_ON_SEEK = "skip_segments_on_seek"
    const val REMEMBER_VOLUME_PER_CONTENT_TYPE = "remember_volume_per_content_type"
}

/**
 * Settings-search items for the "Video Player" group of PlaybackSettingsScreen
 * (player defaults: engine picker, transport, autoplay, player UX). The list is
 * the group declaration: SettingsScreenGroups.playbackPlayer decorates it, and
 * PlaybackSettingsScreen derives its scroll group, expand set and row total
 * from it. Aggregated in [SettingsSearchCatalog].
 */
internal val PlaybackSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = PlaybackSettingsIds.PLAYER_ENGINE,
        titleRes = Res.string.ss_player_engine_title,
        subtitleRes = Res.string.ss_player_engine_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerPlay
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.SEEK_DURATION,
        titleRes = Res.string.ss_seek_duration_title,
        subtitleRes = Res.string.ss_seek_duration_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("seek", "duration", "skip", "double tap", "seconds"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerTrackNext,
        platforms = ANDROID_ONLY_PLATFORMS,
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.ORIENTATION,
        titleRes = Res.string.ss_orientation_title,
        subtitleRes = Res.string.ss_orientation_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("orientation", "rotation", "landscape", "portrait", "sensor"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceMobileRotated,
        platforms = platformsForCapability(settingsCapabilities.supportsScreenOrientation),
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.GESTURES,
        titleRes = Res.string.ss_gestures_title,
        subtitleRes = Res.string.ss_gestures_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("gestures", "swipe", "brightness", "volume", "seeking"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.HandMove,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.GESTURE_INDICATOR_SIDE,
        titleRes = Res.string.ss_gesture_indicator_side_title,
        subtitleRes = Res.string.ss_gesture_indicator_side_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("indicator", "brightness", "volume", "bar", "side", "gesture", "opposite"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.DEFAULT_SPEED,
        titleRes = Res.string.ss_default_speed_title,
        subtitleRes = Res.string.ss_default_speed_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("speed", "rate", "fast", "slow", "playback speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Gauge
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.DEFAULT_ASPECT,
        titleRes = Res.string.ss_default_aspect_title,
        subtitleRes = Res.string.ss_default_aspect_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("aspect", "ratio", "stretch", "zoom", "fit", "fill"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.VIDEO_AUTOPLAY_NEXT,
        titleRes = Res.string.ss_video_autoplay_next_title,
        subtitleRes = Res.string.ss_video_autoplay_next_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("autoplay", "next", "continuous", "episode", "sequence"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.AUTOPLAY_COUNTDOWN,
        titleRes = Res.string.ss_autoplay_countdown_title,
        subtitleRes = Res.string.ss_autoplay_countdown_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("countdown", "timer", "autoplay", "next"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.CONTROLS_TIMEOUT,
        titleRes = Res.string.ss_controls_timeout_title,
        subtitleRes = Res.string.ss_controls_timeout_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("controls", "timeout", "hide", "overlay"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.SKIP_BACK_ON_RESUME,
        titleRes = Res.string.ss_skip_back_on_resume_title,
        subtitleRes = Res.string.ss_skip_back_on_resume_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("skip", "back", "resume", "rewind", "unpause", "seek"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.History,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.SHOW_CLOCK_PLAYER,
        titleRes = Res.string.ss_show_clock_player_title,
        subtitleRes = Res.string.ss_show_clock_player_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("clock", "time", "player", "wall", "current"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.PASS_OUT_PROTECTION,
        titleRes = Res.string.ss_pass_out_protection_title,
        subtitleRes = Res.string.ss_pass_out_protection_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("pass out", "fall asleep", "auto pause", "sleep", "hours"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.DUCK_ON_TRANSIENT_FOCUS_LOSS,
        titleRes = Res.string.ss_duck_on_transient_focus_loss_title,
        subtitleRes = Res.string.ss_duck_on_transient_focus_loss_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("duck", "phone", "call", "focus", "transient", "volume", "rewind"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Phone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.AUTOPLAY_TRAILERS,
        titleRes = Res.string.ss_autoplay_trailers_title,
        subtitleRes = Res.string.ss_autoplay_trailers_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("trailer", "autoplay", "preview", "details"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clipboard,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.CINEMA_MODE,
        titleRes = Res.string.ss_cinema_mode_title,
        subtitleRes = Res.string.ss_cinema_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("cinema", "intro", "preroll", "pre-roll", "trailer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.EPISODE_BROWSER,
        titleRes = Res.string.ss_episode_browser_title,
        subtitleRes = Res.string.ss_episode_browser_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("episodes", "browser", "list", "in-player"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.List,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.PLAYBACK_METADATA,
        titleRes = Res.string.ss_playback_metadata_title,
        subtitleRes = Res.string.ss_playback_metadata_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("metadata", "codec", "bitrate", "stream stats", "debug"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.InfoCircle,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.SWIPE_SEEK_RANGE,
        titleRes = Res.string.ss_swipe_seek_range_title,
        subtitleRes = Res.string.ss_swipe_seek_range_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("seek range", "swipe limit", "skip max"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBarRight,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.REMEMBER_BRIGHTNESS,
        titleRes = Res.string.ss_remember_brightness_title,
        subtitleRes = Res.string.ss_remember_brightness_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("brightness", "remember", "save", "light"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BrightnessHalf,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.TRICKPLAY_PREVIEW,
        titleRes = Res.string.ss_trickplay_preview_title,
        subtitleRes = Res.string.ss_trickplay_preview_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("trickplay", "thumbnails", "scrubbing", "preview", "seek preview"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.PRELOAD_BUFFER,
        titleRes = Res.string.ss_preload_buffer_title,
        subtitleRes = Res.string.ss_preload_buffer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("buffer", "preload", "cache", "size", "network cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.VIDEO_CACHE_SIZE,
        titleRes = Res.string.ss_video_cache_size_title,
        subtitleRes = Res.string.ss_video_cache_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("cache", "video cache", "size", "storage", "stream cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.BACKGROUND_AUDIO,
        titleRes = Res.string.ss_background_audio_title,
        subtitleRes = Res.string.ss_background_audio_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("background", "audio", "video background", "pip"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.KEEP_SCREEN_ON,
        titleRes = Res.string.ss_keep_screen_on_title,
        subtitleRes = Res.string.ss_keep_screen_on_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("screen", "awake", "lock", "stay on", "timeout"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.INCOGNITO_MODE,
        titleRes = Res.string.ss_incognito_mode_title,
        subtitleRes = Res.string.ss_incognito_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("incognito", "private", "history", "stealth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Ghost,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.HOLD_SPEED_MULTIPLIER,
        titleRes = Res.string.ss_hold_speed_multiplier_title,
        subtitleRes = Res.string.ss_hold_speed_multiplier_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("hold", "seek", "speed", "multiplier", "fast", "fast forward", "rewind", "long press", "off", "disable"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Rocket
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT,
        titleRes = Res.string.ss_android_tv_watch_next_title,
        subtitleRes = Res.string.ss_android_tv_watch_next_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("android tv", "watch next", "home", "tv", "continue"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceTv,
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.TV_ZOOM_MODE,
        titleRes = Res.string.ss_tv_zoom_mode_title,
        subtitleRes = Res.string.ss_tv_zoom_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("tv", "zoom", "crop", "fill", "screen"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Crop,
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.DEFAULT_BRIGHTNESS_LEVEL,
        titleRes = Res.string.ss_default_brightness_level_title,
        subtitleRes = Res.string.ss_default_brightness_level_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("brightness", "default", "screen", "light", "level"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Sun,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.TRICKPLAY_ON_GESTURES,
        titleRes = Res.string.ss_trickplay_on_gestures_title,
        subtitleRes = Res.string.ss_trickplay_on_gestures_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("trickplay", "thumbnails", "gesture", "swipe", "seek"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.HandMove,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.SHOW_TIME_REMAINING,
        titleRes = Res.string.ss_show_time_remaining_title,
        subtitleRes = Res.string.ss_show_time_remaining_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("time", "remaining", "elapsed", "duration", "countdown"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.PAUSE_ON_FOCUS_LOSS,
        titleRes = Res.string.ss_pause_on_focus_loss_title,
        subtitleRes = Res.string.ss_pause_on_focus_loss_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("pause", "focus", "loss", "audio focus", "interruption"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerPause,
        isAdvanced = true
    ),
    // ── Desktop-only volume-memory toggle: one remembered level per
    // content type (video / music / audiobook), applied at item start on the
    // surfaces where the app owns a volume scalar (desktop mpv). Android's
    // video volume is the system stream's — the row is structurally absent.
    SettingsSearchItem(
        id = PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE,
        titleRes = Res.string.ss_remember_volume_title,
        subtitleRes = Res.string.ss_remember_volume_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("volume", "remember", "memory", "per content", "content type", "loudness", "level", "movies", "audiobooks"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),

)

/**
 * The player group's per-id declared row admissions — the single gate both
 * `playbackPlayerScreenRowTotal` and PlaybackSettingsScreen's emission `if`s
 * read: the capability rows drop where the platform cannot back them, the
 * two TV rows ride the TV form factor alone (they are declared `isAdvanced`
 * yet the count has always admitted them on `isTv` only — the shipped
 * semantics, preserved verbatim).
 */
internal val PlaybackPlayerRowAdmissions: Map<String, RowAdmission> = mapOf(
    PlaybackSettingsIds.SEEK_DURATION to RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    PlaybackSettingsIds.ORIENTATION to RowAdmission.Platform(RowAdmissionCapability.ScreenOrientation),
    PlaybackSettingsIds.GESTURES to RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    PlaybackSettingsIds.GESTURE_INDICATOR_SIDE to RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT to RowAdmission.Tv,
    PlaybackSettingsIds.TV_ZOOM_MODE to RowAdmission.Tv,
    PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE to RowAdmission.Platform(RowAdmissionCapability.VolumeMemory),
)

/**
 * Settings-search items for the "Advanced Video" group of PlaybackSettingsScreen
 * (dialogue boost, decoder, passthrough, refresh rate, streaming quality, live
 * stream option, audio delay). Split out of [PlaybackSettingsSearchItems] along
 * the screen-group line: these rows render in the advanced-video group, not the
 * player group. Aggregated in [SettingsSearchCatalog].
 */
internal val PlaybackAdvancedVideoSearchItems = listOf(
    SettingsSearchItem(
        id = PlaybackSettingsIds.DIALOGUE_BOOST,
        titleRes = Res.string.ss_dialogue_boost_title,
        subtitleRes = Res.string.ss_dialogue_boost_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH,
        titleRes = Res.string.ss_dialogue_boost_strength_title,
        subtitleRes = Res.string.ss_dialogue_boost_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.DECODER,
        titleRes = Res.string.ss_decoder_title,
        subtitleRes = Res.string.ss_decoder_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("decoder", "hardware", "software", "decoding", "codec"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.AUDIO_PASSTHROUGH,
        titleRes = Res.string.ss_audio_passthrough_title,
        subtitleRes = Res.string.ss_audio_passthrough_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("passthrough", "surround", "hdmi", "receiver", "raw"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Movie,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.FRAME_RATE_MATCHING,
        titleRes = Res.string.ss_frame_rate_matching_title,
        subtitleRes = Res.string.ss_frame_rate_matching_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("refresh rate", "frame rate", "hz", "judder", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Maximize,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.STREAMING_QUALITY,
        titleRes = Res.string.ss_streaming_quality_title,
        subtitleRes = Res.string.ss_streaming_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("quality", "streaming", "resolution", "4k", "1080p", "sd"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.AUDIO_DELAY,
        titleRes = Res.string.ss_audio_delay_title,
        subtitleRes = Res.string.ss_audio_delay_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = PlaybackSettingsIds.LIVE_STREAM_OPTION,
        titleRes = Res.string.ss_live_stream_option_title,
        subtitleRes = Res.string.ss_live_stream_option_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("live tv", "direct stream", "transcode", "tuner", "htsp", "tvheadend", "channel", "mpeg-ts", "mpeg ts", "broadcast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.DeviceTv,
        isAdvanced = true
    )
)

/**
 * The advanced-video group's per-id declared row admissions — the strength
 * row only renders while its parent toggle is on (`playbackAdvancedVideo-
 * ScreenRowTotal` and both screens' emission `if`s read this one gate).
 */
internal val PlaybackAdvancedVideoRowAdmissions: Map<String, RowAdmission> = mapOf(
    PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH to RowAdmission.WhenOn(PlaybackSettingsIds.DIALOGUE_BOOST),
)

/**
 * Settings-search items for the "MPV Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val MpvEngineSearchItems = listOf(
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_VIDEO_OUTPUT,
        titleRes = Res.string.ss_mpv_video_output_title,
        subtitleRes = Res.string.ss_mpv_video_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "video output", "vo", "gpu", "render"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_SCALER,
        titleRes = Res.string.ss_mpv_scaler_title,
        subtitleRes = Res.string.ss_mpv_scaler_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "scaler", "scaling", "interpolation", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_DEBANDING,
        titleRes = Res.string.ss_mpv_debanding_title,
        subtitleRes = Res.string.ss_mpv_debanding_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "deband", "debanding", "banding", "gradient"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ColorFilter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_INTERPOLATION,
        titleRes = Res.string.ss_mpv_interpolation_title,
        subtitleRes = Res.string.ss_mpv_interpolation_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "interpolation", "smooth", "motion", "judder"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_AUDIO_OUTPUT,
        titleRes = Res.string.ss_mpv_audio_output_title,
        subtitleRes = Res.string.ss_mpv_audio_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "audio output", "ao", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_AUDIO_FALLBACK,
        titleRes = Res.string.ss_mpv_audio_fallback_title,
        subtitleRes = Res.string.ss_mpv_audio_fallback_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "audio", "fallback", "secondary", "output"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBack,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_AUDIO_DEVICE,
        titleRes = Res.string.ss_mpv_audio_device_title,
        subtitleRes = Res.string.ss_mpv_audio_device_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "audio device", "output device", "sound card", "speaker", "wasapi", "directsound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_AUDIO_EXCLUSIVE,
        titleRes = Res.string.ss_mpv_audio_exclusive_title,
        subtitleRes = Res.string.ss_mpv_audio_exclusive_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "exclusive", "bit-perfect", "bitperfect", "wasapi exclusive", "device lock"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Lock,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_AUDIO_MODE,
        titleRes = Res.string.ss_mpv_audio_mode_title,
        subtitleRes = Res.string.ss_mpv_audio_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "passthrough", "spdif", "optical", "hdmi", "bitstream", "stereo downmix", "surround", "receiver"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Transfer,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    // ── Desktop-only render rows: the Anime4K extraction,
    // tone-mapping, quality-profile and vo=gpu-next HDR machinery is
    // desktop's (the HWND-embed path); Android's mpv hides all five.
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_SHADER_PACK,
        titleRes = Res.string.ss_mpv_shader_pack_title,
        subtitleRes = Res.string.ss_mpv_shader_pack_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "shader", "anime4k", "glsl", "upscale", "pack", "fsrcnnx", "artcnn"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Wand,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_TONE_MAPPING,
        titleRes = Res.string.ss_mpv_tone_mapping_title,
        subtitleRes = Res.string.ss_mpv_tone_mapping_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "tone mapping", "hdr", "sdr", "bt2390", "hable", "reinhard", "mobius", "brightness"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Brightness,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_RENDER_QUALITY,
        titleRes = Res.string.ss_mpv_render_quality_title,
        subtitleRes = Res.string.ss_mpv_render_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "quality", "performance", "profile", "scaler", "deband", "high"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_HDR_PASSTHROUGH,
        titleRes = Res.string.ss_mpv_hdr_passthrough_title,
        subtitleRes = Res.string.ss_mpv_hdr_passthrough_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "hdr", "hdr10", "passthrough", "gpu-next", "colorspace", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SunHigh,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_INTERPOLATION_TSCALE,
        titleRes = Res.string.ss_mpv_tscale_title,
        subtitleRes = Res.string.ss_mpv_tscale_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "tscale", "interpolation", "motion", "temporal", "mitchell", "oversample"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_BUFFER_SIZE,
        titleRes = Res.string.ss_mpv_buffer_size_title,
        subtitleRes = Res.string.ss_mpv_buffer_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "buffer", "demuxer", "size", "bytes", "cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_HWDEC_OVERRIDE,
        titleRes = Res.string.ss_mpv_hwdec_override_title,
        subtitleRes = Res.string.ss_mpv_hwdec_override_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "hardware", "hwdec", "decoder", "override", "gpu"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_SKIP_LOOP_FILTER,
        titleRes = Res.string.ss_mpv_skip_loop_filter_title,
        subtitleRes = Res.string.ss_mpv_skip_loop_filter_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "skip", "loop filter", "h264", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_FRAME_DROP,
        titleRes = Res.string.ss_mpv_frame_drop_title,
        subtitleRes = Res.string.ss_mpv_frame_drop_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "frame", "drop", "vdrop", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PhotoDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MPV_EXTRA_CONFIG,
        titleRes = Res.string.ss_mpv_extra_config_title,
        subtitleRes = Res.string.ss_mpv_extra_config_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("mpv", "advanced", "config", "raw", "options", "editor", "custom"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.RESET_ENGINE_DEFAULTS,
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
 * The engine-config group's per-id declared row admissions: the three
 * mpv audio-device rows are desktop-backed — [RowAdmission.Platform] hides
 * them where no enumerator exists, and both `playbackEngineScreenRowTotal`
 * and the screen's emission `if`s read this one gate.
 */
internal val PlaybackEngineRowAdmissions: Map<String, RowAdmission> = mapOf(
    PlaybackSettingsIds.MPV_AUDIO_DEVICE to RowAdmission.Platform(RowAdmissionCapability.AudioDeviceSelection),
    PlaybackSettingsIds.MPV_AUDIO_EXCLUSIVE to RowAdmission.Platform(RowAdmissionCapability.AudioDeviceSelection),
    PlaybackSettingsIds.MPV_AUDIO_MODE to RowAdmission.Platform(RowAdmissionCapability.AudioDeviceSelection),
    PlaybackSettingsIds.MPV_SHADER_PACK to RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    PlaybackSettingsIds.MPV_TONE_MAPPING to RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    PlaybackSettingsIds.MPV_RENDER_QUALITY to RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    PlaybackSettingsIds.MPV_HDR_PASSTHROUGH to RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    PlaybackSettingsIds.MPV_INTERPOLATION_TSCALE to RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
)

/**
 * Settings-search items for the "VLC Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val VlcEngineSearchItems = listOf(
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_AUDIO_OUTPUT,
        titleRes = Res.string.ss_vlc_audio_output_title,
        subtitleRes = Res.string.ss_vlc_audio_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "libvlc", "audio output", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_AUDIO_TIME_STRETCH,
        titleRes = Res.string.ss_vlc_audio_time_stretch_title,
        subtitleRes = Res.string.ss_vlc_audio_time_stretch_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "time stretch", "pitch", "speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_VIDEO_OUTPUT,
        titleRes = Res.string.ss_vlc_video_output_title,
        subtitleRes = Res.string.ss_vlc_video_output_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "libvlc", "video output", "display"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_NETWORK_CACHING,
        titleRes = Res.string.ss_vlc_network_caching_title,
        subtitleRes = Res.string.ss_vlc_network_caching_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "network", "caching", "buffer", "streaming"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_SKIP_LOOP_FILTER,
        titleRes = Res.string.ss_vlc_skip_loop_filter_title,
        subtitleRes = Res.string.ss_vlc_skip_loop_filter_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "skip", "loop filter", "h264", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_SKIP_FRAMES,
        titleRes = Res.string.ss_vlc_skip_frames_title,
        subtitleRes = Res.string.ss_vlc_skip_frames_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "skip frames", "performance", "frame"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_DECODER_THREADS,
        titleRes = Res.string.ss_vlc_decoder_threads_title,
        subtitleRes = Res.string.ss_vlc_decoder_threads_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "decoder", "threads", "cpu", "multithreading"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.VLC_DROP_LATE_FRAMES,
        titleRes = Res.string.ss_vlc_drop_late_frames_title,
        subtitleRes = Res.string.ss_vlc_drop_late_frames_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("vlc", "drop", "late", "frames", "delayed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Trash,
        isAdvanced = true
    ),
).androidOnly()

/**
 * Settings-search items for the "ExoPlayer Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val ExoPlayerEngineSearchItems = listOf(
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_VIDEO_SCALING,
        titleRes = Res.string.ss_exo_video_scaling_title,
        subtitleRes = Res.string.ss_exo_video_scaling_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "scaling", "video", "resize"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_FRAME_RATE_STRATEGY,
        titleRes = Res.string.ss_exo_frame_rate_strategy_title,
        subtitleRes = Res.string.ss_exo_frame_rate_strategy_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "frame rate", "refresh", "strategy"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_SKIP_SILENCE,
        titleRes = Res.string.ss_exo_skip_silence_title,
        subtitleRes = Res.string.ss_exo_skip_silence_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "skip", "silence", "audio", "gap"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_AUDIO_OFFLOAD,
        titleRes = Res.string.ss_exo_audio_offload_title,
        subtitleRes = Res.string.ss_exo_audio_offload_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "audio", "offload", "battery"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Headphones,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_DECODER_FALLBACK,
        titleRes = Res.string.ss_exo_decoder_fallback_title,
        subtitleRes = Res.string.ss_exo_decoder_fallback_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "decoder", "fallback", "secondary"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ToggleLeft,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_BACK_BUFFER,
        titleRes = Res.string.ss_exo_back_buffer_title,
        subtitleRes = Res.string.ss_exo_back_buffer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "back buffer", "rewind", "buffer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.EXO_PREFERRED_CODECS,
        titleRes = Res.string.ss_exo_preferred_codecs_title,
        subtitleRes = Res.string.ss_exo_preferred_codecs_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("exoplayer", "exo", "codec", "mime", "preferred", "video"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
).androidOnly()

/**
 * Settings-search items for the "SyncPlay" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 */
internal val SyncPlaySearchItems = listOf(
    SettingsSearchItem(
        id = PlaybackSettingsIds.SYNCPLAY_JOIN_BEHAVIOR,
        titleRes = Res.string.ss_syncplay_join_behavior_title,
        subtitleRes = Res.string.ss_syncplay_join_behavior_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.MessageQuestion
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.SYNCPLAY_TOLERANCE,
        titleRes = Res.string.ss_syncplay_tolerance_title,
        subtitleRes = Res.string.ss_syncplay_tolerance_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.WaveSine
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.SYNCPLAY_AUTO_ACCEPT_INVITES,
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
        id = PlaybackSettingsIds.CASTING_STRATEGY,
        titleRes = Res.string.ss_casting_strategy_title,
        subtitleRes = Res.string.ss_casting_strategy_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cast
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.BACKGROUND_CASTING,
        titleRes = Res.string.ss_background_casting_title,
        subtitleRes = Res.string.ss_background_casting_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Settings
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.PREFERRED_RENDERER,
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
        id = PlaybackSettingsIds.DVR_PRE_PADDING,
        titleRes = Res.string.ss_dvr_pre_padding_title,
        subtitleRes = Res.string.ss_dvr_pre_padding_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.DVR_POST_PADDING,
        titleRes = Res.string.ss_dvr_post_padding_title,
        subtitleRes = Res.string.ss_dvr_post_padding_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.DVR_RECORDING_QUALITY,
        titleRes = Res.string.ss_dvr_recording_quality_title,
        subtitleRes = Res.string.ss_dvr_recording_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MEDIA_SEGMENT_INTRO,
        titleRes = CoreUiRes.string.core_segment_intro,
        subtitleRes = CoreUiRes.string.core_segment_intro_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "intro", "skip", "opening", "credits", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MEDIA_SEGMENT_OUTRO,
        titleRes = CoreUiRes.string.core_segment_outro,
        subtitleRes = CoreUiRes.string.core_segment_outro_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "outro", "ending", "skip", "credits", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MEDIA_SEGMENT_PREVIEW,
        titleRes = CoreUiRes.string.core_segment_preview,
        subtitleRes = CoreUiRes.string.core_segment_preview_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "preview", "next episode", "recap", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MEDIA_SEGMENT_RECAP,
        titleRes = CoreUiRes.string.core_segment_recap,
        subtitleRes = CoreUiRes.string.core_segment_recap_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "recap", "previously on", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MEDIA_SEGMENT_COMMERCIAL,
        titleRes = CoreUiRes.string.core_segment_commercial,
        subtitleRes = CoreUiRes.string.core_segment_commercial_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "commercial", "ad", "advertisement", "skip", "marker"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.MEDIA_SEGMENT_UNKNOWN,
        titleRes = CoreUiRes.string.core_segment_unknown,
        subtitleRes = CoreUiRes.string.core_segment_unknown_desc,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "unknown", "skip", "marker", "unidentified"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SquareRounded,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = PlaybackSettingsIds.SKIP_SEGMENTS_ON_SEEK,
        titleRes = Res.string.ss_skip_segments_on_seek_title,
        subtitleRes = Res.string.ss_skip_segments_on_seek_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_playback,
        keywords = listOf("segment", "skip", "seek", "forward", "commercial", "auto"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerTrackNext,
        isAdvanced = true
    ),
)
