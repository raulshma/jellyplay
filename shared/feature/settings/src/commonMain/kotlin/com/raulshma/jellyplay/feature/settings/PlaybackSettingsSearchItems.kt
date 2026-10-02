package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
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
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_playback
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_delay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_device
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_exclusive
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_fallback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_offload
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_output
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_passthrough
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_time_stretch
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_accept_invites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_pip
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_countdown
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_back_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_audio
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_casting
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_buffer_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_strategy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_controls_timeout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_debanding
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_fallback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_threads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_aspect
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_brightness_level
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_speed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dialogue_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dialogue_boost_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downmix_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_drop_late_frames
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_duck_on_phone_call
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_post_padding
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_pre_padding
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_recording_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_episode_browser
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_external_player_app
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_frame_drop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_frame_rate_strategy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_side
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_passthrough
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hold_to_seek_speed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hwdec_override
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incognito_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation_tscale
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_behavior
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_keep_screen_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_tv_stream
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_audio_channels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_caching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_orientation
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pass_out_protection
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_ac3
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_dts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_dtshd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_eac3
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_truehd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_on_focus_loss
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_player_engine
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_codecs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_renderer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preload_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_match
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remember_brightness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_render_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_to_defaults
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_scaler
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seek_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_shader_pack
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_time_remaining
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_back_on_resume
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_frames
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_loop_filter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_segments_on_seek
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_silence
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_still_watching_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_still_watching_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_swipe_seek_range
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_tolerance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_preview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_output
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_scaling
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_android_tv_watch_next_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_android_tv_watch_next_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_delay_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_delay_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_passthrough_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_passthrough_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_pip_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_pip_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dialogue_boost_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dialogue_boost_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_downmix_boost_subtitle
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_external_player_app_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_external_player_app_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_audio_channels_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_device_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_device_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_exclusive_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_exclusive_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_fallback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_fallback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_output_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_audio_output_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_buffer_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_buffer_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_debanding_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_debanding_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_extra_config_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_frame_drop_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_frame_drop_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hdr_passthrough_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hdr_passthrough_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hwdec_override_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_hwdec_override_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_interpolation_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_interpolation_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_render_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_render_quality_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_scaler_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_scaler_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_shader_pack_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_shader_pack_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_skip_loop_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_skip_loop_filter_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tone_mapping_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tone_mapping_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tscale_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_tscale_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_video_output_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_mpv_video_output_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_offline_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_orientation_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_orientation_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pass_out_protection_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pass_out_protection_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_passthrough_codec_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pause_on_focus_loss_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pause_on_focus_loss_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_playback_metadata_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_playback_metadata_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_player_engine_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_player_engine_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preferred_renderer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preferred_renderer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preload_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_preload_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_brightness_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_brightness_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_volume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remember_volume_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reset_engine_defaults_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seek_duration_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seek_duration_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_clock_player_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_clock_player_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_time_remaining_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_time_remaining_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_back_on_resume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_back_on_resume_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_segments_on_seek_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_still_watching_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_still_watching_episodes_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_still_watching_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_still_watching_mode_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_video_cache_size_subtitle
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
    const val EXTERNAL_PLAYER_APP = "external_player_app"
    const val SEEK_DURATION = "seek_duration"
    const val ORIENTATION = "orientation"
    const val GESTURES = "gestures"
    const val GESTURE_INDICATOR_SIDE = "gesture_indicator_side"
    const val DEFAULT_SPEED = "default_speed"
    const val DEFAULT_ASPECT = "default_aspect"
    const val VIDEO_AUTOPLAY_NEXT = "video_autoplay_next"
    const val AUTOPLAY_COUNTDOWN = "autoplay_countdown"
    const val STILL_WATCHING_MODE = "still_watching_mode"
    const val STILL_WATCHING_EPISODES = "still_watching_episodes"
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
    const val AUTO_ENTER_PIP = "auto_enter_pip"
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
    const val PASSTHROUGH_CODEC_AC3 = "passthrough_codec_ac3"
    const val PASSTHROUGH_CODEC_EAC3 = "passthrough_codec_eac3"
    const val PASSTHROUGH_CODEC_DTS = "passthrough_codec_dts"
    const val PASSTHROUGH_CODEC_DTSHD = "passthrough_codec_dtshd"
    const val PASSTHROUGH_CODEC_TRUEHD = "passthrough_codec_truehd"
    const val MAX_AUDIO_CHANNELS = "max_audio_channels"
    const val DOWNMIX_BOOST = "downmix_boost"
    const val FRAME_RATE_MATCHING = "frame_rate_matching"
    const val STREAMING_QUALITY = "streaming_quality"
    const val OFFLINE_PLAYBACK = "offline_playback"
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

// ═══════════════════════════════════════════════════════════════════════
// The spec-derived derivation inputs: the
// semantics (ids, keywords, categories, isAdvanced, platform rules, route
// kinds) live on the datastore-side spec declarations
// ([PlaybackPreferenceSpecs.searchEntries] +
// [VideoPlayerPreferenceSpecs.searchEntries]); this file declares only the
// id → resource/icon binding tables, the routeKind → Route map, and the
// per-group derivation calls. The record lists below stay the ordered
// spine — the catalog order, the [rowTitle]/[rowIcon] screen faces and the
// residual rows' hand search faces.
// ═══════════════════════════════════════════════════════════════════════

private val searchRoutes: Map<String, Route> = mapOf(
    PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS to Route.PlaybackSettings(),
)

/** Both playback-screen domains' search declarations (see [searchRoutes]). */
private val playbackSpecEntries: List<PreferenceSearchSpec> =
    PlaybackPreferenceSpecs.searchEntries + VideoPlayerPreferenceSpecs.searchEntries

/** The group's spec slice for its binding table — order-free (the spine owns order), fail-fast on a binding without a spec. */
private fun specsFor(bindings: List<SettingsSearchBinding>): List<PreferenceSearchSpec> {
    val ids = bindings.map { it.id }.toSet()
    val matched = playbackSpecEntries.filter { it.id in ids }
    val missing = ids - matched.map { it.id }.toSet()
    require(missing.isEmpty()) { "settings-search binding ids without a spec entry: $missing" }
    return matched
}

private val playbackCategory = CoreUiRes.string.ss_cat_playback

/**
 * The player group's binding table — the search faces of the 36 spec-backed
 * rows (the volume-memory row is the group's one feature-side residual).
 */
private val playbackPlayerBindings = listOf(
    SettingsSearchBinding(PlaybackSettingsIds.PLAYER_ENGINE, Res.string.ss_player_engine_title, Res.string.ss_player_engine_subtitle, playbackCategory, Tabler.Outline.PlayerPlay),
    SettingsSearchBinding(PlaybackSettingsIds.SEEK_DURATION, Res.string.ss_seek_duration_title, Res.string.ss_seek_duration_subtitle, playbackCategory, Tabler.Outline.PlayerTrackNext),
    SettingsSearchBinding(
        PlaybackSettingsIds.ORIENTATION,
        Res.string.ss_orientation_title,
        Res.string.ss_orientation_subtitle,
        playbackCategory,
        Tabler.Outline.DeviceMobileRotated,
        platforms = platformsForCapability(settingsCapabilities.supportsScreenOrientation),
    ),
    SettingsSearchBinding(
        PlaybackSettingsIds.GESTURES,
        Res.string.ss_gestures_title,
        Res.string.ss_gestures_subtitle,
        playbackCategory,
        Tabler.Outline.HandMove,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
    ),
    SettingsSearchBinding(
        PlaybackSettingsIds.GESTURE_INDICATOR_SIDE,
        Res.string.ss_gesture_indicator_side_title,
        Res.string.ss_gesture_indicator_side_subtitle,
        playbackCategory,
        Tabler.Outline.ArrowsHorizontal,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
    ),
    SettingsSearchBinding(PlaybackSettingsIds.DEFAULT_SPEED, Res.string.ss_default_speed_title, Res.string.ss_default_speed_subtitle, playbackCategory, Tabler.Outline.Gauge),
    SettingsSearchBinding(PlaybackSettingsIds.DEFAULT_ASPECT, Res.string.ss_default_aspect_title, Res.string.ss_default_aspect_subtitle, playbackCategory, Tabler.Outline.ArrowAutofitHeight),
    SettingsSearchBinding(PlaybackSettingsIds.VIDEO_AUTOPLAY_NEXT, Res.string.ss_video_autoplay_next_title, Res.string.ss_video_autoplay_next_subtitle, playbackCategory, Tabler.Outline.PlayerSkipForward),
    SettingsSearchBinding(PlaybackSettingsIds.AUTOPLAY_COUNTDOWN, Res.string.ss_autoplay_countdown_title, Res.string.ss_autoplay_countdown_subtitle, playbackCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(PlaybackSettingsIds.STILL_WATCHING_MODE, Res.string.ss_still_watching_mode_title, Res.string.ss_still_watching_mode_subtitle, playbackCategory, Tabler.Outline.EyeCheck),
    SettingsSearchBinding(PlaybackSettingsIds.STILL_WATCHING_EPISODES, Res.string.ss_still_watching_episodes_title, Res.string.ss_still_watching_episodes_subtitle, playbackCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(PlaybackSettingsIds.CONTROLS_TIMEOUT, Res.string.ss_controls_timeout_title, Res.string.ss_controls_timeout_subtitle, playbackCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(PlaybackSettingsIds.SKIP_BACK_ON_RESUME, Res.string.ss_skip_back_on_resume_title, Res.string.ss_skip_back_on_resume_subtitle, playbackCategory, Tabler.Outline.History),
    SettingsSearchBinding(PlaybackSettingsIds.SHOW_CLOCK_PLAYER, Res.string.ss_show_clock_player_title, Res.string.ss_show_clock_player_subtitle, playbackCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(PlaybackSettingsIds.PASS_OUT_PROTECTION, Res.string.ss_pass_out_protection_title, Res.string.ss_pass_out_protection_subtitle, playbackCategory, Tabler.Outline.Moon),
    SettingsSearchBinding(PlaybackSettingsIds.DUCK_ON_TRANSIENT_FOCUS_LOSS, Res.string.ss_duck_on_transient_focus_loss_title, Res.string.ss_duck_on_transient_focus_loss_subtitle, playbackCategory, Tabler.Outline.Phone),
    SettingsSearchBinding(PlaybackSettingsIds.AUTOPLAY_TRAILERS, Res.string.ss_autoplay_trailers_title, Res.string.ss_autoplay_trailers_subtitle, playbackCategory, Tabler.Outline.Clipboard),
    SettingsSearchBinding(PlaybackSettingsIds.CINEMA_MODE, Res.string.ss_cinema_mode_title, Res.string.ss_cinema_mode_subtitle, playbackCategory, Tabler.Outline.Video),
    SettingsSearchBinding(PlaybackSettingsIds.EPISODE_BROWSER, Res.string.ss_episode_browser_title, Res.string.ss_episode_browser_subtitle, playbackCategory, Tabler.Outline.List),
    SettingsSearchBinding(PlaybackSettingsIds.PLAYBACK_METADATA, Res.string.ss_playback_metadata_title, Res.string.ss_playback_metadata_subtitle, playbackCategory, Tabler.Outline.InfoCircle),
    SettingsSearchBinding(PlaybackSettingsIds.SWIPE_SEEK_RANGE, Res.string.ss_swipe_seek_range_title, Res.string.ss_swipe_seek_range_subtitle, playbackCategory, Tabler.Outline.ArrowBarRight),
    SettingsSearchBinding(PlaybackSettingsIds.REMEMBER_BRIGHTNESS, Res.string.ss_remember_brightness_title, Res.string.ss_remember_brightness_subtitle, playbackCategory, Tabler.Outline.BrightnessHalf),
    SettingsSearchBinding(PlaybackSettingsIds.TRICKPLAY_PREVIEW, Res.string.ss_trickplay_preview_title, Res.string.ss_trickplay_preview_subtitle, playbackCategory, Tabler.Outline.Photo),
    SettingsSearchBinding(PlaybackSettingsIds.PRELOAD_BUFFER, Res.string.ss_preload_buffer_title, Res.string.ss_preload_buffer_subtitle, playbackCategory, Tabler.Outline.Refresh),
    // The search hit restates the row's screen title (the default-title fold),
    // so the binding names the settings_* resource the fold used to resolve.
    SettingsSearchBinding(PlaybackSettingsIds.VIDEO_CACHE_SIZE, Res.string.settings_video_cache_size, Res.string.ss_video_cache_size_subtitle, playbackCategory, Tabler.Outline.Database),
    SettingsSearchBinding(PlaybackSettingsIds.BACKGROUND_AUDIO, Res.string.ss_background_audio_title, Res.string.ss_background_audio_subtitle, playbackCategory, Tabler.Outline.Music),
    SettingsSearchBinding(PlaybackSettingsIds.AUTO_ENTER_PIP, Res.string.ss_auto_pip_title, Res.string.ss_auto_pip_subtitle, playbackCategory, Tabler.Outline.PictureInPicture),
    SettingsSearchBinding(PlaybackSettingsIds.KEEP_SCREEN_ON, Res.string.ss_keep_screen_on_title, Res.string.ss_keep_screen_on_subtitle, playbackCategory, Tabler.Outline.Eye),
    SettingsSearchBinding(PlaybackSettingsIds.INCOGNITO_MODE, Res.string.ss_incognito_mode_title, Res.string.ss_incognito_mode_subtitle, playbackCategory, Tabler.Outline.Ghost),
    SettingsSearchBinding(PlaybackSettingsIds.HOLD_SPEED_MULTIPLIER, Res.string.ss_hold_speed_multiplier_title, Res.string.ss_hold_speed_multiplier_subtitle, playbackCategory, Tabler.Outline.Rocket),
    SettingsSearchBinding(PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT, Res.string.ss_android_tv_watch_next_title, Res.string.ss_android_tv_watch_next_subtitle, playbackCategory, Tabler.Outline.DeviceTv),
    SettingsSearchBinding(PlaybackSettingsIds.TV_ZOOM_MODE, Res.string.ss_tv_zoom_mode_title, Res.string.ss_tv_zoom_mode_subtitle, playbackCategory, Tabler.Outline.Crop),
    SettingsSearchBinding(PlaybackSettingsIds.DEFAULT_BRIGHTNESS_LEVEL, Res.string.ss_default_brightness_level_title, Res.string.ss_default_brightness_level_subtitle, playbackCategory, Tabler.Outline.Sun),
    SettingsSearchBinding(PlaybackSettingsIds.TRICKPLAY_ON_GESTURES, Res.string.ss_trickplay_on_gestures_title, Res.string.ss_trickplay_on_gestures_subtitle, playbackCategory, Tabler.Outline.HandMove),
    SettingsSearchBinding(PlaybackSettingsIds.SHOW_TIME_REMAINING, Res.string.ss_show_time_remaining_title, Res.string.ss_show_time_remaining_subtitle, playbackCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(PlaybackSettingsIds.PAUSE_ON_FOCUS_LOSS, Res.string.ss_pause_on_focus_loss_title, Res.string.ss_pause_on_focus_loss_subtitle, playbackCategory, Tabler.Outline.PlayerPause),
)

/**
 * Settings-search items for the "Video Player" group of PlaybackSettingsScreen
 * (player defaults: engine picker, transport, autoplay, player UX). The list is
 * the group declaration: SettingsScreenGroups.playbackPlayer decorates it, and
 * PlaybackSettingsScreen derives its scroll group, expand set and row total
 * from it. Aggregated in [SettingsSearchCatalog].
 *
 * Spec-derived: the record list below is the ordered
 * spine — screen faces + the one residual row (the desktop volume-memory
 * toggle, whose knob lives in the spec-less VolumeProfileStore) — and every
 * other row's search faces derive from its spec entry + binding.
 */
internal val PlaybackSettingsRowRecords = listOf(
    SettingsRowRecord(id = PlaybackSettingsIds.PLAYER_ENGINE, titleRes = Res.string.settings_player_engine, icon = Tabler.Outline.PlayerPlay),
    SettingsRowRecord(id = PlaybackSettingsIds.SEEK_DURATION, titleRes = Res.string.settings_seek_duration, icon = Tabler.Outline.PlayerTrackNext),
    SettingsRowRecord(id = PlaybackSettingsIds.ORIENTATION, titleRes = Res.string.settings_orientation, icon = Tabler.Outline.DeviceMobileRotated),
    SettingsRowRecord(id = PlaybackSettingsIds.GESTURES, titleRes = Res.string.settings_gestures, icon = Tabler.Outline.HandMove),
    SettingsRowRecord(id = PlaybackSettingsIds.GESTURE_INDICATOR_SIDE, titleRes = Res.string.settings_gesture_indicator_side, icon = Tabler.Outline.ArrowsHorizontal),
    SettingsRowRecord(id = PlaybackSettingsIds.DEFAULT_SPEED, titleRes = Res.string.settings_default_speed, icon = Tabler.Outline.Gauge),
    SettingsRowRecord(id = PlaybackSettingsIds.DEFAULT_ASPECT, titleRes = Res.string.settings_default_aspect, icon = Tabler.Outline.ArrowAutofitHeight),
    SettingsRowRecord(id = PlaybackSettingsIds.VIDEO_AUTOPLAY_NEXT, titleRes = Res.string.settings_auto_play_next, icon = Tabler.Outline.PlayerSkipForward),
    SettingsRowRecord(id = PlaybackSettingsIds.AUTOPLAY_COUNTDOWN, titleRes = Res.string.settings_auto_play_countdown, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = PlaybackSettingsIds.STILL_WATCHING_MODE, titleRes = Res.string.settings_still_watching_mode, icon = Tabler.Outline.EyeCheck),
    SettingsRowRecord(id = PlaybackSettingsIds.STILL_WATCHING_EPISODES, titleRes = Res.string.settings_still_watching_episodes, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = PlaybackSettingsIds.CONTROLS_TIMEOUT, titleRes = Res.string.settings_controls_timeout, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = PlaybackSettingsIds.SKIP_BACK_ON_RESUME, titleRes = Res.string.settings_skip_back_on_resume, icon = Tabler.Outline.History),
    SettingsRowRecord(id = PlaybackSettingsIds.SHOW_CLOCK_PLAYER, titleRes = Res.string.settings_show_clock_player, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = PlaybackSettingsIds.PASS_OUT_PROTECTION, titleRes = Res.string.settings_pass_out_protection, icon = Tabler.Outline.Moon),
    SettingsRowRecord(id = PlaybackSettingsIds.DUCK_ON_TRANSIENT_FOCUS_LOSS, titleRes = Res.string.settings_duck_on_phone_call, icon = Tabler.Outline.Phone),
    SettingsRowRecord(id = PlaybackSettingsIds.AUTOPLAY_TRAILERS, titleRes = Res.string.settings_autoplay_trailers, icon = Tabler.Outline.Clipboard),
    SettingsRowRecord(id = PlaybackSettingsIds.CINEMA_MODE, titleRes = Res.string.settings_cinema_mode, icon = Tabler.Outline.Video),
    SettingsRowRecord(id = PlaybackSettingsIds.EPISODE_BROWSER, titleRes = Res.string.settings_episode_browser, icon = Tabler.Outline.List),
    SettingsRowRecord(id = PlaybackSettingsIds.PLAYBACK_METADATA, titleRes = Res.string.settings_playback_metadata, icon = Tabler.Outline.InfoCircle),
    SettingsRowRecord(id = PlaybackSettingsIds.SWIPE_SEEK_RANGE, titleRes = Res.string.settings_swipe_seek_range, icon = Tabler.Outline.ArrowBarRight),
    SettingsRowRecord(id = PlaybackSettingsIds.REMEMBER_BRIGHTNESS, titleRes = Res.string.settings_remember_brightness, icon = Tabler.Outline.BrightnessHalf),
    SettingsRowRecord(id = PlaybackSettingsIds.TRICKPLAY_PREVIEW, titleRes = Res.string.settings_trickplay_preview, icon = Tabler.Outline.Photo),
    SettingsRowRecord(id = PlaybackSettingsIds.PRELOAD_BUFFER, titleRes = Res.string.settings_preload_buffer, icon = Tabler.Outline.Refresh),
    SettingsRowRecord(id = PlaybackSettingsIds.VIDEO_CACHE_SIZE, titleRes = Res.string.settings_video_cache_size, icon = Tabler.Outline.Database),
    SettingsRowRecord(id = PlaybackSettingsIds.BACKGROUND_AUDIO, titleRes = Res.string.settings_background_audio, icon = Tabler.Outline.Music),
    // ── Auto-PiP on Home/recents (issue #167): Android-only — the declared
    // All(Advanced, Platform(Pip)) admission hides the row wholesale on
    // desktop, where NoOpPipController binds and windowing covers it.
    SettingsRowRecord(id = PlaybackSettingsIds.AUTO_ENTER_PIP, titleRes = Res.string.settings_auto_pip, icon = Tabler.Outline.PictureInPicture),
    SettingsRowRecord(id = PlaybackSettingsIds.KEEP_SCREEN_ON, titleRes = Res.string.settings_keep_screen_on, icon = Tabler.Outline.Eye),
    SettingsRowRecord(id = PlaybackSettingsIds.INCOGNITO_MODE, titleRes = Res.string.settings_incognito_mode, icon = Tabler.Outline.Ghost),
    SettingsRowRecord(id = PlaybackSettingsIds.HOLD_SPEED_MULTIPLIER, titleRes = Res.string.settings_hold_to_seek_speed, icon = Tabler.Outline.Rocket),
    SettingsRowRecord(id = PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT, titleRes = Res.string.settings_watch_next_row, icon = Tabler.Outline.DeviceTv),
    SettingsRowRecord(id = PlaybackSettingsIds.TV_ZOOM_MODE, titleRes = Res.string.settings_tv_zoom_mode, icon = Tabler.Outline.Crop),
    SettingsRowRecord(id = PlaybackSettingsIds.DEFAULT_BRIGHTNESS_LEVEL, titleRes = Res.string.settings_default_brightness_level, icon = Tabler.Outline.Sun),
    SettingsRowRecord(id = PlaybackSettingsIds.TRICKPLAY_ON_GESTURES, titleRes = Res.string.settings_trickplay_on_gestures, icon = Tabler.Outline.HandMove),
    SettingsRowRecord(id = PlaybackSettingsIds.SHOW_TIME_REMAINING, titleRes = Res.string.settings_show_time_remaining, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = PlaybackSettingsIds.PAUSE_ON_FOCUS_LOSS, titleRes = Res.string.settings_pause_on_focus_loss, icon = Tabler.Outline.PlayerPause),
    // ── RESIDUAL (feature-side hand row): the desktop-only volume-memory
    // toggle's knob lives in the spec-less VolumeProfileStore — one
    // remembered level per content type (video / music / audiobook), applied
    // at item start on the surfaces where the app owns a volume scalar
    // (desktop mpv). Android's video volume is the system stream's — the row
    // is structurally absent.
    SettingsRowRecord(
        id = PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE,
        titleRes = Res.string.ss_remember_volume_title,
        searchSubtitleRes = Res.string.ss_remember_volume_subtitle,
        keywords = listOf("volume", "remember", "memory", "per content", "content type", "loudness", "level", "movies", "audiobooks"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
)

/** The catalog projection of the spec-backed player rows + the residual record. */
internal val PlaybackSettingsSearchItems: List<SettingsSearchItem> =
    PlaybackSettingsRowRecords.toSearchItems(
        specEntries = specsFor(playbackPlayerBindings),
        bindings = playbackPlayerBindings,
        routes = searchRoutes,
        categoryRes = playbackCategory,
    )


/**
 * The player group's per-id declared row admissions — full coverage, so
 * `rowTotalFor` and PlaybackSettingsScreen's emission `if`s read one gate per
 * id. The base gate is each row's declared advanced flag (the spec's
 * `isAdvanced` for the 36 spec-derived rows, the residual record's own flag
 * otherwise); the overrides are the capability rows, which drop where the
 * platform cannot back them, and the two TV rows, which ride the TV form
 * factor alone (they are declared `isAdvanced` yet the count has always
 * admitted them on `isTv` only — the shipped semantics, preserved verbatim in
 * their override).
 */
internal val PlaybackPlayerRowAdmissions: Map<String, RowAdmission> =
    PlaybackSettingsSearchItems.admissionsByAdvancedFlag() + mapOf(
        PlaybackSettingsIds.SEEK_DURATION to RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
        PlaybackSettingsIds.ORIENTATION to RowAdmission.Platform(RowAdmissionCapability.ScreenOrientation),
        PlaybackSettingsIds.GESTURES to RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
        PlaybackSettingsIds.GESTURE_INDICATOR_SIDE to RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
        // The still-watching pair rides the autoplay toggle — no autoplay, no
        // confirm prompt to configure.
        PlaybackSettingsIds.STILL_WATCHING_MODE to RowAdmission.WhenOn(PlaybackSettingsIds.VIDEO_AUTOPLAY_NEXT),
        PlaybackSettingsIds.STILL_WATCHING_EPISODES to RowAdmission.WhenOn(PlaybackSettingsIds.VIDEO_AUTOPLAY_NEXT),
        PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT to RowAdmission.Tv,
        PlaybackSettingsIds.TV_ZOOM_MODE to RowAdmission.Tv,
        PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE to RowAdmission.Platform(RowAdmissionCapability.VolumeMemory),
        // The auto-PiP toggle rides the advanced toggle AND the platform PiP
        // capability (Android's PlayerActivity stack) — both gates must hold.
        PlaybackSettingsIds.AUTO_ENTER_PIP to RowAdmission.All(
            RowAdmission.Advanced,
            RowAdmission.Platform(RowAdmissionCapability.Pip),
        ),
    )

/**
 * The advanced-video group's binding table — the search faces of its 13
 * spec-backed rows (the dialogue-boost pair and the audio-delay row are the
 * group's feature-side residuals).
 */
private val playbackAdvancedVideoBindings = listOf(
    SettingsSearchBinding(PlaybackSettingsIds.DECODER, Res.string.ss_decoder_title, Res.string.ss_decoder_subtitle, playbackCategory, Tabler.Outline.BadgeHd),
    SettingsSearchBinding(PlaybackSettingsIds.AUDIO_PASSTHROUGH, Res.string.ss_audio_passthrough_title, Res.string.ss_audio_passthrough_subtitle, playbackCategory, Tabler.Outline.Movie),
    // The per-codec passthrough rows restate their screen titles (the fold).
    SettingsSearchBinding(PlaybackSettingsIds.PASSTHROUGH_CODEC_AC3, Res.string.settings_passthrough_codec_ac3, Res.string.ss_passthrough_codec_subtitle, playbackCategory, Tabler.Outline.Speakerphone),
    SettingsSearchBinding(PlaybackSettingsIds.PASSTHROUGH_CODEC_EAC3, Res.string.settings_passthrough_codec_eac3, Res.string.ss_passthrough_codec_subtitle, playbackCategory, Tabler.Outline.Speakerphone),
    SettingsSearchBinding(PlaybackSettingsIds.PASSTHROUGH_CODEC_DTS, Res.string.settings_passthrough_codec_dts, Res.string.ss_passthrough_codec_subtitle, playbackCategory, Tabler.Outline.Speakerphone),
    SettingsSearchBinding(PlaybackSettingsIds.PASSTHROUGH_CODEC_DTSHD, Res.string.settings_passthrough_codec_dtshd, Res.string.ss_passthrough_codec_subtitle, playbackCategory, Tabler.Outline.Speakerphone),
    SettingsSearchBinding(PlaybackSettingsIds.PASSTHROUGH_CODEC_TRUEHD, Res.string.settings_passthrough_codec_truehd, Res.string.ss_passthrough_codec_subtitle, playbackCategory, Tabler.Outline.Speakerphone),
    SettingsSearchBinding(PlaybackSettingsIds.MAX_AUDIO_CHANNELS, Res.string.settings_max_audio_channels, Res.string.ss_max_audio_channels_subtitle, playbackCategory, Tabler.Outline.WaveSine),
    SettingsSearchBinding(PlaybackSettingsIds.DOWNMIX_BOOST, Res.string.settings_downmix_boost, Res.string.ss_downmix_boost_subtitle, playbackCategory, Tabler.Outline.Volume),
    SettingsSearchBinding(PlaybackSettingsIds.FRAME_RATE_MATCHING, Res.string.ss_frame_rate_matching_title, Res.string.ss_frame_rate_matching_subtitle, playbackCategory, Tabler.Outline.Maximize),
    SettingsSearchBinding(PlaybackSettingsIds.OFFLINE_PLAYBACK, Res.string.settings_offline_playback, Res.string.ss_offline_playback_subtitle, playbackCategory, Tabler.Outline.Download),
    SettingsSearchBinding(PlaybackSettingsIds.STREAMING_QUALITY, Res.string.ss_streaming_quality_title, Res.string.ss_streaming_quality_subtitle, playbackCategory, Tabler.Outline.BadgeHd),
    SettingsSearchBinding(PlaybackSettingsIds.LIVE_STREAM_OPTION, Res.string.ss_live_stream_option_title, Res.string.ss_live_stream_option_subtitle, playbackCategory, Tabler.Outline.DeviceTv),
)

/**
 * Settings-search items for the "Advanced Video" group of PlaybackSettingsScreen
 * (dialogue boost, decoder, passthrough, refresh rate, streaming quality, live
 * stream option, audio delay). Split out of [PlaybackSettingsSearchItems] along
 * the screen-group line: these rows render in the advanced-video group, not the
 * player group. Aggregated in [SettingsSearchCatalog].
 *
 * Spec-derived for the 13 rows whose knobs live in the playback store; the
 * dialogue-boost pair (AudioEffectsStore) and the audio-delay row (AudioStore)
 * stay feature-side residuals — their stores have no spec machinery yet.
 */
internal val PlaybackAdvancedVideoRowRecords = listOf(
    // ── RESIDUAL rows (AudioEffectsStore — no spec machinery): the boost
    // toggle and its strength row (the strength row rides the toggle).
    SettingsRowRecord(
        id = PlaybackSettingsIds.DIALOGUE_BOOST,
        titleRes = Res.string.settings_dialogue_boost,
        searchTitleRes = Res.string.ss_dialogue_boost_title,
        searchSubtitleRes = Res.string.ss_dialogue_boost_subtitle,
        keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH,
        titleRes = Res.string.settings_dialogue_boost_strength,
        searchSubtitleRes = Res.string.ss_dialogue_boost_strength_subtitle,
        keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Microphone2,
        isAdvanced = true,
    ),
    SettingsRowRecord(id = PlaybackSettingsIds.DECODER, titleRes = Res.string.settings_decoder, icon = Tabler.Outline.BadgeHd),
    SettingsRowRecord(id = PlaybackSettingsIds.AUDIO_PASSTHROUGH, titleRes = Res.string.settings_audio_passthrough, icon = Tabler.Outline.Movie),
    SettingsRowRecord(id = PlaybackSettingsIds.PASSTHROUGH_CODEC_AC3, titleRes = Res.string.settings_passthrough_codec_ac3, icon = Tabler.Outline.Speakerphone),
    SettingsRowRecord(id = PlaybackSettingsIds.PASSTHROUGH_CODEC_EAC3, titleRes = Res.string.settings_passthrough_codec_eac3, icon = Tabler.Outline.Speakerphone),
    SettingsRowRecord(id = PlaybackSettingsIds.PASSTHROUGH_CODEC_DTS, titleRes = Res.string.settings_passthrough_codec_dts, icon = Tabler.Outline.Speakerphone),
    SettingsRowRecord(id = PlaybackSettingsIds.PASSTHROUGH_CODEC_DTSHD, titleRes = Res.string.settings_passthrough_codec_dtshd, icon = Tabler.Outline.Speakerphone),
    SettingsRowRecord(id = PlaybackSettingsIds.PASSTHROUGH_CODEC_TRUEHD, titleRes = Res.string.settings_passthrough_codec_truehd, icon = Tabler.Outline.Speakerphone),
    SettingsRowRecord(id = PlaybackSettingsIds.MAX_AUDIO_CHANNELS, titleRes = Res.string.settings_max_audio_channels, icon = Tabler.Outline.WaveSine),
    SettingsRowRecord(id = PlaybackSettingsIds.DOWNMIX_BOOST, titleRes = Res.string.settings_downmix_boost, icon = Tabler.Outline.Volume),
    SettingsRowRecord(id = PlaybackSettingsIds.FRAME_RATE_MATCHING, titleRes = Res.string.settings_refresh_rate_match, icon = Tabler.Outline.Maximize),
    SettingsRowRecord(id = PlaybackSettingsIds.OFFLINE_PLAYBACK, titleRes = Res.string.settings_offline_playback, icon = Tabler.Outline.Download),
    SettingsRowRecord(id = PlaybackSettingsIds.STREAMING_QUALITY, titleRes = Res.string.settings_streaming_quality, icon = Tabler.Outline.BadgeHd),
    // ── RESIDUAL row (AudioStore — no spec machinery).
    SettingsRowRecord(
        id = PlaybackSettingsIds.AUDIO_DELAY,
        titleRes = Res.string.settings_audio_delay,
        searchTitleRes = Res.string.ss_audio_delay_title,
        searchSubtitleRes = Res.string.ss_audio_delay_subtitle,
        keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
    ),
    SettingsRowRecord(id = PlaybackSettingsIds.LIVE_STREAM_OPTION, titleRes = Res.string.settings_live_tv_stream, icon = Tabler.Outline.DeviceTv),
)

/** The catalog projection of the spec-backed advanced-video rows + the residuals. */
internal val PlaybackAdvancedVideoSearchItems: List<SettingsSearchItem> =
    PlaybackAdvancedVideoRowRecords.toSearchItems(
        specEntries = specsFor(playbackAdvancedVideoBindings),
        bindings = playbackAdvancedVideoBindings,
        routes = searchRoutes,
        categoryRes = playbackCategory,
    )


/**
 * The advanced-video group's per-id declared row admissions — full coverage
 * (every row is advanced, so the base gate is the advanced toggle; the
 * group only composes behind it). The strength row additionally rides its
 * parent toggle — `All(Advanced, WhenOn)`, the one declaration
 * `rowTotalFor` and both screens' emission `if`s read (the audio screen
 * renders the dialogue-boost pair inside its equalizer block, behind the
 * same gates).
 */
internal val PlaybackAdvancedVideoRowAdmissions: Map<String, RowAdmission> =
    PlaybackAdvancedVideoSearchItems.admissionsByAdvancedFlag() + mapOf(
        PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackSettingsIds.DIALOGUE_BOOST)),
        // The per-codec passthrough rows ride the master passthrough toggle —
        // no bitstreaming, no per-codec allow-list to configure.
        PlaybackSettingsIds.PASSTHROUGH_CODEC_AC3 to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackSettingsIds.AUDIO_PASSTHROUGH)),
        PlaybackSettingsIds.PASSTHROUGH_CODEC_EAC3 to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackSettingsIds.AUDIO_PASSTHROUGH)),
        PlaybackSettingsIds.PASSTHROUGH_CODEC_DTS to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackSettingsIds.AUDIO_PASSTHROUGH)),
        PlaybackSettingsIds.PASSTHROUGH_CODEC_DTSHD to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackSettingsIds.AUDIO_PASSTHROUGH)),
        PlaybackSettingsIds.PASSTHROUGH_CODEC_TRUEHD to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackSettingsIds.AUDIO_PASSTHROUGH)),
    )

/**
 * Settings-search items for the "MPV Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the engine-config knobs live in the spec-less engine store —
 * these rows stay feature-side records until that store migrates.
 */
internal val MpvEngineRowRecords = listOf(
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_VIDEO_OUTPUT,
        titleRes = Res.string.settings_video_output,
        searchTitleRes = Res.string.ss_mpv_video_output_title,
        searchSubtitleRes = Res.string.ss_mpv_video_output_subtitle,
        keywords = listOf("mpv", "video output", "vo", "gpu", "render"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_SCALER,
        titleRes = Res.string.settings_scaler,
        searchTitleRes = Res.string.ss_mpv_scaler_title,
        searchSubtitleRes = Res.string.ss_mpv_scaler_subtitle,
        keywords = listOf("mpv", "scaler", "scaling", "interpolation", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_DEBANDING,
        titleRes = Res.string.settings_debanding,
        searchTitleRes = Res.string.ss_mpv_debanding_title,
        searchSubtitleRes = Res.string.ss_mpv_debanding_subtitle,
        keywords = listOf("mpv", "deband", "debanding", "banding", "gradient"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ColorFilter,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_INTERPOLATION,
        titleRes = Res.string.settings_interpolation,
        searchTitleRes = Res.string.ss_mpv_interpolation_title,
        searchSubtitleRes = Res.string.ss_mpv_interpolation_subtitle,
        keywords = listOf("mpv", "interpolation", "smooth", "motion", "judder"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowsHorizontal,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_AUDIO_OUTPUT,
        titleRes = Res.string.settings_audio_output,
        searchTitleRes = Res.string.ss_mpv_audio_output_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_output_subtitle,
        keywords = listOf("mpv", "audio output", "ao", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_AUDIO_FALLBACK,
        titleRes = Res.string.settings_audio_fallback,
        searchTitleRes = Res.string.ss_mpv_audio_fallback_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_fallback_subtitle,
        keywords = listOf("mpv", "audio", "fallback", "secondary", "output"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowBack,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_AUDIO_DEVICE,
        titleRes = Res.string.settings_audio_device,
        searchTitleRes = Res.string.ss_mpv_audio_device_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_device_subtitle,
        keywords = listOf("mpv", "audio device", "output device", "sound card", "speaker", "wasapi", "directsound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_AUDIO_EXCLUSIVE,
        titleRes = Res.string.settings_audio_exclusive,
        searchTitleRes = Res.string.ss_mpv_audio_exclusive_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_exclusive_subtitle,
        keywords = listOf("mpv", "exclusive", "bit-perfect", "bitperfect", "wasapi exclusive", "device lock"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Lock,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_AUDIO_MODE,
        titleRes = Res.string.settings_audio_mode,
        searchTitleRes = Res.string.ss_mpv_audio_mode_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_mode_subtitle,
        keywords = listOf("mpv", "passthrough", "spdif", "optical", "hdmi", "bitstream", "stereo downmix", "surround", "receiver"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Transfer,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    // ── Desktop-only render rows: the Anime4K extraction,
    // tone-mapping, quality-profile and vo=gpu-next HDR machinery is
    // desktop's (the HWND-embed path); Android's mpv hides all five.
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_SHADER_PACK,
        titleRes = Res.string.settings_shader_pack,
        searchTitleRes = Res.string.ss_mpv_shader_pack_title,
        searchSubtitleRes = Res.string.ss_mpv_shader_pack_subtitle,
        keywords = listOf("mpv", "shader", "anime4k", "glsl", "upscale", "pack", "fsrcnnx", "artcnn"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Wand,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_TONE_MAPPING,
        titleRes = Res.string.settings_tone_mapping,
        searchTitleRes = Res.string.ss_mpv_tone_mapping_title,
        searchSubtitleRes = Res.string.ss_mpv_tone_mapping_subtitle,
        keywords = listOf("mpv", "tone mapping", "hdr", "sdr", "bt2390", "hable", "reinhard", "mobius", "brightness"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Brightness,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_RENDER_QUALITY,
        titleRes = Res.string.settings_render_quality,
        searchTitleRes = Res.string.ss_mpv_render_quality_title,
        searchSubtitleRes = Res.string.ss_mpv_render_quality_subtitle,
        keywords = listOf("mpv", "quality", "performance", "profile", "scaler", "deband", "high"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_HDR_PASSTHROUGH,
        titleRes = Res.string.settings_hdr_passthrough,
        searchTitleRes = Res.string.ss_mpv_hdr_passthrough_title,
        searchSubtitleRes = Res.string.ss_mpv_hdr_passthrough_subtitle,
        keywords = listOf("mpv", "hdr", "hdr10", "passthrough", "gpu-next", "colorspace", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.SunHigh,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_INTERPOLATION_TSCALE,
        titleRes = Res.string.settings_interpolation_tscale,
        searchTitleRes = Res.string.ss_mpv_tscale_title,
        searchSubtitleRes = Res.string.ss_mpv_tscale_subtitle,
        keywords = listOf("mpv", "tscale", "interpolation", "motion", "temporal", "mitchell", "oversample"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_BUFFER_SIZE,
        titleRes = Res.string.settings_buffer_size,
        searchTitleRes = Res.string.ss_mpv_buffer_size_title,
        searchSubtitleRes = Res.string.ss_mpv_buffer_size_subtitle,
        keywords = listOf("mpv", "buffer", "demuxer", "size", "bytes", "cache"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_HWDEC_OVERRIDE,
        titleRes = Res.string.settings_hwdec_override,
        searchTitleRes = Res.string.ss_mpv_hwdec_override_title,
        searchSubtitleRes = Res.string.ss_mpv_hwdec_override_subtitle,
        keywords = listOf("mpv", "hardware", "hwdec", "decoder", "override", "gpu"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_SKIP_LOOP_FILTER,
        titleRes = Res.string.settings_skip_loop_filter,
        searchTitleRes = Res.string.ss_mpv_skip_loop_filter_title,
        searchSubtitleRes = Res.string.ss_mpv_skip_loop_filter_subtitle,
        keywords = listOf("mpv", "skip", "loop filter", "h264", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_FRAME_DROP,
        titleRes = Res.string.settings_frame_drop,
        searchTitleRes = Res.string.ss_mpv_frame_drop_title,
        searchSubtitleRes = Res.string.ss_mpv_frame_drop_subtitle,
        keywords = listOf("mpv", "frame", "drop", "vdrop", "performance"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PhotoDown,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.MPV_EXTRA_CONFIG,
        titleRes = Res.string.settings_advanced_config,
        searchSubtitleRes = Res.string.ss_mpv_extra_config_subtitle,
        keywords = listOf("mpv", "advanced", "config", "raw", "options", "editor", "custom"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.RESET_ENGINE_DEFAULTS,
        titleRes = Res.string.settings_reset_to_defaults,
        searchSubtitleRes = Res.string.ss_reset_engine_defaults_subtitle,
        keywords = listOf("reset", "defaults", "restore", "engine", "mpv", "vlc", "exoplayer", "configuration"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ))

/** The catalog projection of `MpvEngineRowRecords`: the search faces + the shared category. */
internal val MpvEngineSearchItems: List<SettingsSearchItem> = MpvEngineRowRecords.toSearchItems(CoreUiRes.string.ss_cat_playback)


/**
 * Settings-search items for the "VLC Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the engine-config knobs live in the spec-less engine store —
 * these rows stay feature-side records until that store migrates.
 */
internal val VlcEngineRowRecords = listOf(
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_AUDIO_OUTPUT,
        titleRes = Res.string.settings_audio_output,
        searchTitleRes = Res.string.ss_vlc_audio_output_title,
        searchSubtitleRes = Res.string.ss_vlc_audio_output_subtitle,
        keywords = listOf("vlc", "libvlc", "audio output", "sound"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_AUDIO_TIME_STRETCH,
        titleRes = Res.string.settings_audio_time_stretch,
        searchTitleRes = Res.string.ss_vlc_audio_time_stretch_title,
        searchSubtitleRes = Res.string.ss_vlc_audio_time_stretch_subtitle,
        keywords = listOf("vlc", "time stretch", "pitch", "speed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_VIDEO_OUTPUT,
        titleRes = Res.string.settings_video_output,
        searchTitleRes = Res.string.ss_vlc_video_output_title,
        searchSubtitleRes = Res.string.ss_vlc_video_output_subtitle,
        keywords = listOf("vlc", "libvlc", "video output", "display"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Video,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_NETWORK_CACHING,
        titleRes = Res.string.settings_network_caching,
        searchTitleRes = Res.string.ss_vlc_network_caching_title,
        searchSubtitleRes = Res.string.ss_vlc_network_caching_subtitle,
        keywords = listOf("vlc", "network", "caching", "buffer", "streaming"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_SKIP_LOOP_FILTER,
        titleRes = Res.string.settings_skip_loop_filter,
        searchTitleRes = Res.string.ss_vlc_skip_loop_filter_title,
        searchSubtitleRes = Res.string.ss_vlc_skip_loop_filter_subtitle,
        keywords = listOf("vlc", "skip", "loop filter", "h264", "quality"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Filter,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_SKIP_FRAMES,
        titleRes = Res.string.settings_skip_frames,
        searchTitleRes = Res.string.ss_vlc_skip_frames_title,
        searchSubtitleRes = Res.string.ss_vlc_skip_frames_subtitle,
        keywords = listOf("vlc", "skip frames", "performance", "frame"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_DECODER_THREADS,
        titleRes = Res.string.settings_decoder_threads,
        searchTitleRes = Res.string.ss_vlc_decoder_threads_title,
        searchSubtitleRes = Res.string.ss_vlc_decoder_threads_subtitle,
        keywords = listOf("vlc", "decoder", "threads", "cpu", "multithreading"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cpu,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.VLC_DROP_LATE_FRAMES,
        titleRes = Res.string.settings_drop_late_frames,
        searchTitleRes = Res.string.ss_vlc_drop_late_frames_title,
        searchSubtitleRes = Res.string.ss_vlc_drop_late_frames_subtitle,
        keywords = listOf("vlc", "drop", "late", "frames", "delayed"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Trash,
        isAdvanced = true
    ))

/** The catalog projection of `VlcEngineRowRecords`: the search faces + the shared category. */
internal val VlcEngineSearchItems: List<SettingsSearchItem> = VlcEngineRowRecords.toSearchItems(CoreUiRes.string.ss_cat_playback).androidOnly()


/**
 * Settings-search items for the "ExoPlayer Engine Config" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the engine-config knobs live in the spec-less engine store —
 * these rows stay feature-side records until that store migrates.
 */
internal val ExoPlayerEngineRowRecords = listOf(
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_VIDEO_SCALING,
        titleRes = Res.string.settings_video_scaling,
        searchTitleRes = Res.string.ss_exo_video_scaling_title,
        searchSubtitleRes = Res.string.ss_exo_video_scaling_subtitle,
        keywords = listOf("exoplayer", "exo", "scaling", "video", "resize"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ArrowAutofitHeight,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_FRAME_RATE_STRATEGY,
        titleRes = Res.string.settings_frame_rate_strategy,
        searchTitleRes = Res.string.ss_exo_frame_rate_strategy_title,
        searchSubtitleRes = Res.string.ss_exo_frame_rate_strategy_subtitle,
        keywords = listOf("exoplayer", "exo", "frame rate", "refresh", "strategy"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_SKIP_SILENCE,
        titleRes = Res.string.settings_skip_silence,
        searchTitleRes = Res.string.ss_exo_skip_silence_title,
        searchSubtitleRes = Res.string.ss_exo_skip_silence_subtitle,
        keywords = listOf("exoplayer", "exo", "skip", "silence", "audio", "gap"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Volume,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_AUDIO_OFFLOAD,
        titleRes = Res.string.settings_audio_offload,
        searchTitleRes = Res.string.ss_exo_audio_offload_title,
        searchSubtitleRes = Res.string.ss_exo_audio_offload_subtitle,
        keywords = listOf("exoplayer", "exo", "audio", "offload", "battery"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Headphones,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_DECODER_FALLBACK,
        titleRes = Res.string.settings_decoder_fallback,
        searchTitleRes = Res.string.ss_exo_decoder_fallback_title,
        searchSubtitleRes = Res.string.ss_exo_decoder_fallback_subtitle,
        keywords = listOf("exoplayer", "exo", "decoder", "fallback", "secondary"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.ToggleLeft,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_BACK_BUFFER,
        titleRes = Res.string.settings_back_buffer,
        searchTitleRes = Res.string.ss_exo_back_buffer_title,
        searchSubtitleRes = Res.string.ss_exo_back_buffer_subtitle,
        keywords = listOf("exoplayer", "exo", "back buffer", "rewind", "buffer"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.EXO_PREFERRED_CODECS,
        titleRes = Res.string.settings_preferred_codecs,
        searchTitleRes = Res.string.ss_exo_preferred_codecs_title,
        searchSubtitleRes = Res.string.ss_exo_preferred_codecs_subtitle,
        keywords = listOf("exoplayer", "exo", "codec", "mime", "preferred", "video"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ))

/** The catalog projection of `ExoPlayerEngineRowRecords`: the search faces + the shared category. */
internal val ExoPlayerEngineSearchItems: List<SettingsSearchItem> = ExoPlayerEngineRowRecords.toSearchItems(CoreUiRes.string.ss_cat_playback).androidOnly()

/**
 * The `playback.engine` group's EXTERNAL branch rows: which third-party app
 * the external hand-off targets. One row, always admitted (the branch only
 * composes when the preferred player IS external, so the advanced toggle
 * would double-gate a single picker).
 *
 * Spec-derived: the knob is the playback store's `preferred_external_player`.
 */
internal val ExternalEngineRowRecords = listOf(
    SettingsRowRecord(id = PlaybackSettingsIds.EXTERNAL_PLAYER_APP, titleRes = Res.string.settings_external_player_app, icon = Tabler.Outline.Devices),
)

private val playbackExternalBindings = listOf(
    SettingsSearchBinding(PlaybackSettingsIds.EXTERNAL_PLAYER_APP, Res.string.ss_external_player_app_title, Res.string.ss_external_player_app_subtitle, playbackCategory, Tabler.Outline.Devices),
)

/** The catalog projection of the spec-derived external-player row. */
internal val ExternalEngineSearchItems: List<SettingsSearchItem> =
    ExternalEngineRowRecords.toSearchItems(
        specEntries = specsFor(playbackExternalBindings),
        bindings = playbackExternalBindings,
        routes = searchRoutes,
        categoryRes = playbackCategory,
    )


/**
 * The engine-config group's per-id declared row admissions — full coverage
 * across the engine lists (every record is advanced; the branches only
 * compose behind the advanced toggle). The overrides are the desktop-backed
 * mpv rows: [RowAdmission.Platform] hides the three audio-device rows where
 * no enumerator exists and the five render rows where no render-profile
 * plumbing exists — the one declaration `playbackEngineScreenRowTotal` (via
 * `rowTotalFor`) and the screen's emission `if`s read. Declared after all
 * the engine record lists (same-file top-level initialization order).
 */
internal val PlaybackEngineRowAdmissions: Map<String, RowAdmission> =
    (MpvEngineRowRecords + VlcEngineRowRecords + ExoPlayerEngineRowRecords + ExternalEngineRowRecords).admissionsByAdvancedFlag() + mapOf(
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
 * Settings-search items for the "SyncPlay" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the SyncPlay knobs live in the spec-less syncplay/casting
 * store — these rows stay feature-side records until that store migrates.
 */
internal val SyncPlayRowRecords = listOf(
    SettingsRowRecord(
        id = PlaybackSettingsIds.SYNCPLAY_JOIN_BEHAVIOR,
        titleRes = Res.string.settings_join_behavior,
        searchTitleRes = Res.string.ss_syncplay_join_behavior_title,
        searchSubtitleRes = Res.string.ss_syncplay_join_behavior_subtitle,
        keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.MessageQuestion
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.SYNCPLAY_TOLERANCE,
        titleRes = Res.string.settings_sync_tolerance,
        searchTitleRes = Res.string.ss_syncplay_tolerance_title,
        searchSubtitleRes = Res.string.ss_syncplay_tolerance_subtitle,
        keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.WaveSine
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.SYNCPLAY_AUTO_ACCEPT_INVITES,
        titleRes = Res.string.settings_auto_accept_invites,
        searchTitleRes = Res.string.ss_syncplay_auto_accept_invites_title,
        searchSubtitleRes = Res.string.ss_syncplay_auto_accept_invites_subtitle,
        keywords = listOf("syncplay", "auto", "accept", "invites", "friends"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.CircleCheck
    ))

/** The catalog projection of `SyncPlayRowRecords`: the search faces + the shared category. */
internal val SyncPlaySearchItems: List<SettingsSearchItem> = SyncPlayRowRecords.toSearchItems(CoreUiRes.string.ss_cat_playback)


/**
 * Settings-search items for the "Casting & DLNA" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the casting knobs live in the spec-less syncplay/casting
 * store — these rows stay feature-side records until that store migrates.
 */
internal val CastingRowRecords = listOf(
    SettingsRowRecord(
        id = PlaybackSettingsIds.CASTING_STRATEGY,
        titleRes = Res.string.settings_casting_strategy,
        searchTitleRes = Res.string.ss_casting_strategy_title,
        searchSubtitleRes = Res.string.ss_casting_strategy_subtitle,
        keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Cast
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.BACKGROUND_CASTING,
        titleRes = Res.string.settings_background_casting,
        searchTitleRes = Res.string.ss_background_casting_title,
        searchSubtitleRes = Res.string.ss_background_casting_subtitle,
        keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Settings
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.PREFERRED_RENDERER,
        titleRes = Res.string.settings_preferred_renderer,
        searchTitleRes = Res.string.ss_preferred_renderer_title,
        searchSubtitleRes = Res.string.ss_preferred_renderer_subtitle,
        keywords = listOf("renderer", "preferred", "cast", "device", "target", "tv"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Devices
    ))

/** The catalog projection of `CastingRowRecords`: the search faces + the shared category. */
internal val CastingSearchItems: List<SettingsSearchItem> = CastingRowRecords.toSearchItems(CoreUiRes.string.ss_cat_playback)


/**
 * Settings-search items for the "Live TV & DVR" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to PlaybackSettingsScreen (player defaults, MPV/VLC/ExoPlayer engine config, SyncPlay, casting, Live TV & DVR). Aggregated in [SettingsSearchCatalog].
 *
 * Spec-derived for the media-segment rows + the skip-on-seek toggle (their
 * knobs live in the videoplayer store); the DVR padding/quality trio stays a
 * feature-side residual — its knobs live in the spec-less syncplay/casting
 * store.
 */
internal val LiveTvRowRecords = listOf(
    // ── RESIDUAL rows (SyncPlayCastStore — no spec machinery).
    SettingsRowRecord(
        id = PlaybackSettingsIds.DVR_PRE_PADDING,
        titleRes = Res.string.settings_dvr_pre_padding,
        searchTitleRes = Res.string.ss_dvr_pre_padding_title,
        searchSubtitleRes = Res.string.ss_dvr_pre_padding_subtitle,
        keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.DVR_POST_PADDING,
        titleRes = Res.string.settings_dvr_post_padding,
        searchTitleRes = Res.string.ss_dvr_post_padding_title,
        searchSubtitleRes = Res.string.ss_dvr_post_padding_subtitle,
        keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsRowRecord(
        id = PlaybackSettingsIds.DVR_RECORDING_QUALITY,
        titleRes = Res.string.settings_dvr_recording_quality,
        searchTitleRes = Res.string.ss_dvr_recording_quality_title,
        searchSubtitleRes = Res.string.ss_dvr_recording_quality_subtitle,
        keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
        route = Route.PlaybackSettings(),
        icon = Tabler.Outline.BadgeHd,
        isAdvanced = true
    ),
    SettingsRowRecord(id = PlaybackSettingsIds.MEDIA_SEGMENT_INTRO, titleRes = CoreUiRes.string.core_segment_intro, icon = Tabler.Outline.SquareRounded),
    SettingsRowRecord(id = PlaybackSettingsIds.MEDIA_SEGMENT_OUTRO, titleRes = CoreUiRes.string.core_segment_outro, icon = Tabler.Outline.SquareRounded),
    SettingsRowRecord(id = PlaybackSettingsIds.MEDIA_SEGMENT_PREVIEW, titleRes = CoreUiRes.string.core_segment_preview, icon = Tabler.Outline.SquareRounded),
    SettingsRowRecord(id = PlaybackSettingsIds.MEDIA_SEGMENT_RECAP, titleRes = CoreUiRes.string.core_segment_recap, icon = Tabler.Outline.SquareRounded),
    SettingsRowRecord(id = PlaybackSettingsIds.MEDIA_SEGMENT_COMMERCIAL, titleRes = CoreUiRes.string.core_segment_commercial, icon = Tabler.Outline.SquareRounded),
    SettingsRowRecord(id = PlaybackSettingsIds.MEDIA_SEGMENT_UNKNOWN, titleRes = CoreUiRes.string.core_segment_unknown, icon = Tabler.Outline.SquareRounded),
    SettingsRowRecord(id = PlaybackSettingsIds.SKIP_SEGMENTS_ON_SEEK, titleRes = Res.string.settings_skip_segments_on_seek, icon = Tabler.Outline.PlayerTrackNext),
)

private val liveTvBindings = listOf(
    // The segment rows restate the enum's core_segment faces (the fold).
    SettingsSearchBinding(PlaybackSettingsIds.MEDIA_SEGMENT_INTRO, CoreUiRes.string.core_segment_intro, CoreUiRes.string.core_segment_intro_desc, playbackCategory, Tabler.Outline.SquareRounded),
    SettingsSearchBinding(PlaybackSettingsIds.MEDIA_SEGMENT_OUTRO, CoreUiRes.string.core_segment_outro, CoreUiRes.string.core_segment_outro_desc, playbackCategory, Tabler.Outline.SquareRounded),
    SettingsSearchBinding(PlaybackSettingsIds.MEDIA_SEGMENT_PREVIEW, CoreUiRes.string.core_segment_preview, CoreUiRes.string.core_segment_preview_desc, playbackCategory, Tabler.Outline.SquareRounded),
    SettingsSearchBinding(PlaybackSettingsIds.MEDIA_SEGMENT_RECAP, CoreUiRes.string.core_segment_recap, CoreUiRes.string.core_segment_recap_desc, playbackCategory, Tabler.Outline.SquareRounded),
    SettingsSearchBinding(PlaybackSettingsIds.MEDIA_SEGMENT_COMMERCIAL, CoreUiRes.string.core_segment_commercial, CoreUiRes.string.core_segment_commercial_desc, playbackCategory, Tabler.Outline.SquareRounded),
    SettingsSearchBinding(PlaybackSettingsIds.MEDIA_SEGMENT_UNKNOWN, CoreUiRes.string.core_segment_unknown, CoreUiRes.string.core_segment_unknown_desc, playbackCategory, Tabler.Outline.SquareRounded),
    SettingsSearchBinding(PlaybackSettingsIds.SKIP_SEGMENTS_ON_SEEK, Res.string.settings_skip_segments_on_seek, Res.string.ss_skip_segments_on_seek_subtitle, playbackCategory, Tabler.Outline.PlayerTrackNext),
)

/** The catalog projection of the spec-backed segment rows + the DVR residuals. */
internal val LiveTvSearchItems: List<SettingsSearchItem> =
    LiveTvRowRecords.toSearchItems(
        specEntries = specsFor(liveTvBindings),
        bindings = liveTvBindings,
        routes = searchRoutes,
        categoryRes = playbackCategory,
    )
