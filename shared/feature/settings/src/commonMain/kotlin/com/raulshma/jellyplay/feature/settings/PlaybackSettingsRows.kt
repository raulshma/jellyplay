package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_double_tap_hold_seek
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_osd_on_pause
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_player_engine
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_resume_headset_plug
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_resume_headset_plug_subtitle
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_double_tap_hold_seek_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_decoder_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_double_tap_hold_seek_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_osd_on_pause_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_osd_on_pause_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_resume_headset_plug_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_resume_headset_plug_title
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
 * The playback/video domain's fused row declarations — the feature-side single
 * home of every playback row's presentation, ordering, and capability (the
 * [AppearanceRows] template, applied to the wave's largest domain). Each
 * [SettingsRow] replaces the trio the domain used to declare per row: the
 * `SettingsSearchBinding` entry, the `SettingsRowRecord` entry, and the
 * `PlaybackSettingsIds` holder constant (all retired). One declaration per
 * row: id, title faces, icon, search faces, and the admission gate
 * ([SettingsRow.gate] — the one declaration both the group totals and the
 * screen emission `if`s read).
 *
 * The SEMANTICS stay two-homed by design: each spec-backed row's keywords,
 * advanced flag, platform rule and route kind live on the datastore-side
 * [PreferenceSearchSpec] declarations ([PlaybackPreferenceSpecs.searchEntries]
 * + [VideoPlayerPreferenceSpecs.searchEntries]); the engine-config, SyncPlay,
 * casting, DVR and audio-effect residual rows carry their full hand search
 * faces (their knobs live in spec-less stores). The projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * row-spec drift; search results, catalog order, group membership and
 * per-gate visibility are byte-identical to the retired record+binding
 * declarations (`SpecDerivedSearchItemsTest`,
 * `SettingsCatalogScreenContractTest` keep pinning them).
 */
internal object PlaybackRows {

    // -- The "Video Player" group's 40 rows (39 spec-backed + the desktop volume-memory residual), in catalog order. --

    val PlayerEngine = SettingsRow(
        id = "player_engine",
        icon = Tabler.Outline.PlayerPlay,
        titleRes = Res.string.settings_player_engine,
        searchTitleRes = Res.string.ss_player_engine_title,
        searchSubtitleRes = Res.string.ss_player_engine_subtitle,
    )

    val SeekDuration = SettingsRow(
        id = "seek_duration",
        icon = Tabler.Outline.PlayerTrackNext,
        titleRes = Res.string.settings_seek_duration,
        searchTitleRes = Res.string.ss_seek_duration_title,
        searchSubtitleRes = Res.string.ss_seek_duration_subtitle,
        gate = RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    )

    val Orientation = SettingsRow(
        id = "orientation",
        icon = Tabler.Outline.DeviceMobileRotated,
        titleRes = Res.string.settings_orientation,
        searchTitleRes = Res.string.ss_orientation_title,
        searchSubtitleRes = Res.string.ss_orientation_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsScreenOrientation),
        gate = RowAdmission.Platform(RowAdmissionCapability.ScreenOrientation),
    )

    val Gestures = SettingsRow(
        id = "gestures",
        icon = Tabler.Outline.HandMove,
        titleRes = Res.string.settings_gestures,
        searchTitleRes = Res.string.ss_gestures_title,
        searchSubtitleRes = Res.string.ss_gestures_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
        gate = RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    )

    val GestureIndicatorSide = SettingsRow(
        id = "gesture_indicator_side",
        icon = Tabler.Outline.ArrowsHorizontal,
        titleRes = Res.string.settings_gesture_indicator_side,
        searchTitleRes = Res.string.ss_gesture_indicator_side_title,
        searchSubtitleRes = Res.string.ss_gesture_indicator_side_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
        gate = RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    )

    // double-tap-and-hold continuous seek — extends the
    // double-tap seek knobs (same touch-gesture capability gate as
    // [SeekDuration] / [Gestures], which this row renders beside).
    val DoubleTapHoldSeek = SettingsRow(
        id = "double_tap_hold_seek",
        icon = Tabler.Outline.PlayerTrackNext,
        titleRes = Res.string.settings_double_tap_hold_seek,
        searchTitleRes = Res.string.ss_double_tap_hold_seek_title,
        searchSubtitleRes = Res.string.ss_double_tap_hold_seek_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsTouchGestures),
        gate = RowAdmission.Platform(RowAdmissionCapability.TouchGestures),
    )

    val DefaultSpeed = SettingsRow(
        id = "default_speed",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_default_speed,
        searchTitleRes = Res.string.ss_default_speed_title,
        searchSubtitleRes = Res.string.ss_default_speed_subtitle,
    )

    val DefaultAspect = SettingsRow(
        id = "default_aspect",
        icon = Tabler.Outline.ArrowAutofitHeight,
        titleRes = Res.string.settings_default_aspect,
        searchTitleRes = Res.string.ss_default_aspect_title,
        searchSubtitleRes = Res.string.ss_default_aspect_subtitle,
    )

    val VideoAutoplayNext = SettingsRow(
        id = "video_autoplay_next",
        icon = Tabler.Outline.PlayerSkipForward,
        titleRes = Res.string.settings_auto_play_next,
        searchTitleRes = Res.string.ss_video_autoplay_next_title,
        searchSubtitleRes = Res.string.ss_video_autoplay_next_subtitle,
    )

    val AutoplayCountdown = SettingsRow(
        id = "autoplay_countdown",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_auto_play_countdown,
        searchTitleRes = Res.string.ss_autoplay_countdown_title,
        searchSubtitleRes = Res.string.ss_autoplay_countdown_subtitle,
    )

    val StillWatchingMode = SettingsRow(
        id = "still_watching_mode",
        icon = Tabler.Outline.EyeCheck,
        titleRes = Res.string.settings_still_watching_mode,
        searchTitleRes = Res.string.ss_still_watching_mode_title,
        searchSubtitleRes = Res.string.ss_still_watching_mode_subtitle,
        gate = RowAdmission.WhenOn(PlaybackRows.VideoAutoplayNext.id),
    )

    val StillWatchingEpisodes = SettingsRow(
        id = "still_watching_episodes",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_still_watching_episodes,
        searchTitleRes = Res.string.ss_still_watching_episodes_title,
        searchSubtitleRes = Res.string.ss_still_watching_episodes_subtitle,
        gate = RowAdmission.WhenOn(PlaybackRows.VideoAutoplayNext.id),
    )

    val ControlsTimeout = SettingsRow(
        id = "controls_timeout",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_controls_timeout,
        searchTitleRes = Res.string.ss_controls_timeout_title,
        searchSubtitleRes = Res.string.ss_controls_timeout_subtitle,
    )

    // (jellyfin-androidtv #3924): pausing should not summon the control
    // overlay. Renders beside [ControlsTimeout] — the other controls-visibility
    // knob — in the advanced block, sharing its admission shape.
    val HideOsdOnPause = SettingsRow(
        id = "hide_osd_on_pause",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_hide_osd_on_pause,
        searchTitleRes = Res.string.ss_hide_osd_on_pause_title,
        searchSubtitleRes = Res.string.ss_hide_osd_on_pause_subtitle,
    )

    val SkipBackOnResume = SettingsRow(
        id = "skip_back_on_resume",
        icon = Tabler.Outline.History,
        titleRes = Res.string.settings_skip_back_on_resume,
        searchTitleRes = Res.string.ss_skip_back_on_resume_title,
        searchSubtitleRes = Res.string.ss_skip_back_on_resume_subtitle,
    )

    val ShowClockPlayer = SettingsRow(
        id = "show_clock_player",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_show_clock_player,
        searchTitleRes = Res.string.ss_show_clock_player_title,
        searchSubtitleRes = Res.string.ss_show_clock_player_subtitle,
    )

    val PassOutProtection = SettingsRow(
        id = "pass_out_protection",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_pass_out_protection,
        searchTitleRes = Res.string.ss_pass_out_protection_title,
        searchSubtitleRes = Res.string.ss_pass_out_protection_subtitle,
    )

    val DuckOnTransientFocusLoss = SettingsRow(
        id = "duck_on_transient_focus_loss",
        icon = Tabler.Outline.Phone,
        titleRes = Res.string.settings_duck_on_phone_call,
        searchTitleRes = Res.string.ss_duck_on_transient_focus_loss_title,
        searchSubtitleRes = Res.string.ss_duck_on_transient_focus_loss_subtitle,
    )

    // opt-in resume when headphones reconnect after the
    // becoming-noisy auto-pause. Renders beside [DuckOnTransientFocusLoss] —
    // the same headphones/focus playback-interruption family — in the
    // advanced block, sharing its admission shape.
    val ResumeHeadsetPlug = SettingsRow(
        id = "resume_on_headset_plug",
        icon = Tabler.Outline.Headphones,
        titleRes = Res.string.settings_resume_headset_plug,
        searchTitleRes = Res.string.ss_resume_headset_plug_title,
        searchSubtitleRes = Res.string.ss_resume_headset_plug_subtitle,
    )

    val AutoplayTrailers = SettingsRow(
        id = "autoplay_trailers",
        icon = Tabler.Outline.Clipboard,
        titleRes = Res.string.settings_autoplay_trailers,
        searchTitleRes = Res.string.ss_autoplay_trailers_title,
        searchSubtitleRes = Res.string.ss_autoplay_trailers_subtitle,
    )

    val CinemaMode = SettingsRow(
        id = "cinema_mode",
        icon = Tabler.Outline.Video,
        titleRes = Res.string.settings_cinema_mode,
        searchTitleRes = Res.string.ss_cinema_mode_title,
        searchSubtitleRes = Res.string.ss_cinema_mode_subtitle,
    )

    val EpisodeBrowser = SettingsRow(
        id = "episode_browser",
        icon = Tabler.Outline.List,
        titleRes = Res.string.settings_episode_browser,
        searchTitleRes = Res.string.ss_episode_browser_title,
        searchSubtitleRes = Res.string.ss_episode_browser_subtitle,
    )

    val PlaybackMetadata = SettingsRow(
        id = "playback_metadata",
        icon = Tabler.Outline.InfoCircle,
        titleRes = Res.string.settings_playback_metadata,
        searchTitleRes = Res.string.ss_playback_metadata_title,
        searchSubtitleRes = Res.string.ss_playback_metadata_subtitle,
    )

    val SwipeSeekRange = SettingsRow(
        id = "swipe_seek_range",
        icon = Tabler.Outline.ArrowBarRight,
        titleRes = Res.string.settings_swipe_seek_range,
        searchTitleRes = Res.string.ss_swipe_seek_range_title,
        searchSubtitleRes = Res.string.ss_swipe_seek_range_subtitle,
    )

    val RememberBrightness = SettingsRow(
        id = "remember_brightness",
        icon = Tabler.Outline.BrightnessHalf,
        titleRes = Res.string.settings_remember_brightness,
        searchTitleRes = Res.string.ss_remember_brightness_title,
        searchSubtitleRes = Res.string.ss_remember_brightness_subtitle,
    )

    val TrickplayPreview = SettingsRow(
        id = "trickplay_preview",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_trickplay_preview,
        searchTitleRes = Res.string.ss_trickplay_preview_title,
        searchSubtitleRes = Res.string.ss_trickplay_preview_subtitle,
    )

    val PreloadBuffer = SettingsRow(
        id = "preload_buffer",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_preload_buffer,
        searchTitleRes = Res.string.ss_preload_buffer_title,
        searchSubtitleRes = Res.string.ss_preload_buffer_subtitle,
    )

    val VideoCacheSize = SettingsRow(
        id = "video_cache_size",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_video_cache_size,
        searchSubtitleRes = Res.string.ss_video_cache_size_subtitle,
    )

    val BackgroundAudio = SettingsRow(
        id = "background_audio",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_background_audio,
        searchTitleRes = Res.string.ss_background_audio_title,
        searchSubtitleRes = Res.string.ss_background_audio_subtitle,
    )

    // ── Auto-PiP on Home/recents (issue #167): Android-only — the declared
    // All(Advanced, Platform(Pip)) admission hides the row wholesale on
    // desktop, where NoOpPipController binds and windowing covers it.
    val AutoEnterPip = SettingsRow(
        id = "auto_enter_pip",
        icon = Tabler.Outline.PictureInPicture,
        titleRes = Res.string.settings_auto_pip,
        searchTitleRes = Res.string.ss_auto_pip_title,
        searchSubtitleRes = Res.string.ss_auto_pip_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.Platform(RowAdmissionCapability.Pip)),
    )

    val KeepScreenOn = SettingsRow(
        id = "keep_screen_on",
        icon = Tabler.Outline.Eye,
        titleRes = Res.string.settings_keep_screen_on,
        searchTitleRes = Res.string.ss_keep_screen_on_title,
        searchSubtitleRes = Res.string.ss_keep_screen_on_subtitle,
    )

    val IncognitoMode = SettingsRow(
        id = "incognito_mode",
        icon = Tabler.Outline.Ghost,
        titleRes = Res.string.settings_incognito_mode,
        searchTitleRes = Res.string.ss_incognito_mode_title,
        searchSubtitleRes = Res.string.ss_incognito_mode_subtitle,
    )

    val HoldSpeedMultiplier = SettingsRow(
        id = "hold_speed_multiplier",
        icon = Tabler.Outline.Rocket,
        titleRes = Res.string.settings_hold_to_seek_speed,
        searchTitleRes = Res.string.ss_hold_speed_multiplier_title,
        searchSubtitleRes = Res.string.ss_hold_speed_multiplier_subtitle,
    )

    val AndroidTvWatchNext = SettingsRow(
        id = "android_tv_watch_next",
        icon = Tabler.Outline.DeviceTv,
        titleRes = Res.string.settings_watch_next_row,
        searchTitleRes = Res.string.ss_android_tv_watch_next_title,
        searchSubtitleRes = Res.string.ss_android_tv_watch_next_subtitle,
        gate = RowAdmission.Tv,
    )

    val TvZoomMode = SettingsRow(
        id = "tv_zoom_mode",
        icon = Tabler.Outline.Crop,
        titleRes = Res.string.settings_tv_zoom_mode,
        searchTitleRes = Res.string.ss_tv_zoom_mode_title,
        searchSubtitleRes = Res.string.ss_tv_zoom_mode_subtitle,
        gate = RowAdmission.Tv,
    )

    val DefaultBrightnessLevel = SettingsRow(
        id = "default_brightness_level",
        icon = Tabler.Outline.Sun,
        titleRes = Res.string.settings_default_brightness_level,
        searchTitleRes = Res.string.ss_default_brightness_level_title,
        searchSubtitleRes = Res.string.ss_default_brightness_level_subtitle,
    )

    val TrickplayOnGestures = SettingsRow(
        id = "trickplay_on_gestures",
        icon = Tabler.Outline.HandMove,
        titleRes = Res.string.settings_trickplay_on_gestures,
        searchTitleRes = Res.string.ss_trickplay_on_gestures_title,
        searchSubtitleRes = Res.string.ss_trickplay_on_gestures_subtitle,
    )

    val ShowTimeRemaining = SettingsRow(
        id = "show_time_remaining",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_show_time_remaining,
        searchTitleRes = Res.string.ss_show_time_remaining_title,
        searchSubtitleRes = Res.string.ss_show_time_remaining_subtitle,
    )

    val PauseOnFocusLoss = SettingsRow(
        id = "pause_on_focus_loss",
        icon = Tabler.Outline.PlayerPause,
        titleRes = Res.string.settings_pause_on_focus_loss,
        searchTitleRes = Res.string.ss_pause_on_focus_loss_title,
        searchSubtitleRes = Res.string.ss_pause_on_focus_loss_subtitle,
    )

    // ── RESIDUAL (feature-side hand row): the desktop-only volume-memory
    // toggle's knob lives in the spec-less VolumeProfileStore — one
    // remembered level per content type (video / music / audiobook), applied
    // at item start on the surfaces where the app owns a volume scalar
    // (desktop mpv). Android's video volume is the system stream's — the row
    // is structurally absent.
    val RememberVolumePerContentType = SettingsRow(
        id = "remember_volume_per_content_type",
        icon = Tabler.Outline.Volume,
        titleRes = Res.string.ss_remember_volume_title,
        searchSubtitleRes = Res.string.ss_remember_volume_subtitle,
        keywords = listOf("volume", "remember", "memory", "per content", "content type", "loudness", "level", "movies", "audiobooks"),
        route = Route.PlaybackSettings(),
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.VolumeMemory),
    )

    // -- The "Advanced Video" group's 16 rows (13 spec-backed + the dialogue-boost pair and audio-delay residuals), in catalog order. --

    // ── RESIDUAL rows (AudioEffectsStore — no spec machinery): the boost
    // toggle and its strength row (the strength row rides the toggle).
    val DialogueBoost = SettingsRow(
        id = "dialogue_boost",
        icon = Tabler.Outline.Microphone2,
        titleRes = Res.string.settings_dialogue_boost,
        searchTitleRes = Res.string.ss_dialogue_boost_title,
        searchSubtitleRes = Res.string.ss_dialogue_boost_subtitle,
        keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val DialogueBoostStrength = SettingsRow(
        id = "dialogue_boost_strength",
        icon = Tabler.Outline.Microphone2,
        titleRes = Res.string.settings_dialogue_boost_strength,
        searchSubtitleRes = Res.string.ss_dialogue_boost_strength_subtitle,
        keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackRows.DialogueBoost.id)),
    )

    val Decoder = SettingsRow(
        id = "decoder",
        icon = Tabler.Outline.BadgeHd,
        titleRes = Res.string.settings_decoder,
        searchTitleRes = Res.string.ss_decoder_title,
        searchSubtitleRes = Res.string.ss_decoder_subtitle,
    )

    val AudioPassthrough = SettingsRow(
        id = "audio_passthrough",
        icon = Tabler.Outline.Movie,
        titleRes = Res.string.settings_audio_passthrough,
        searchTitleRes = Res.string.ss_audio_passthrough_title,
        searchSubtitleRes = Res.string.ss_audio_passthrough_subtitle,
    )

    val PassthroughCodecAc3 = SettingsRow(
        id = "passthrough_codec_ac3",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_passthrough_codec_ac3,
        searchSubtitleRes = Res.string.ss_passthrough_codec_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackRows.AudioPassthrough.id)),
    )

    val PassthroughCodecEac3 = SettingsRow(
        id = "passthrough_codec_eac3",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_passthrough_codec_eac3,
        searchSubtitleRes = Res.string.ss_passthrough_codec_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackRows.AudioPassthrough.id)),
    )

    val PassthroughCodecDts = SettingsRow(
        id = "passthrough_codec_dts",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_passthrough_codec_dts,
        searchSubtitleRes = Res.string.ss_passthrough_codec_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackRows.AudioPassthrough.id)),
    )

    val PassthroughCodecDtshd = SettingsRow(
        id = "passthrough_codec_dtshd",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_passthrough_codec_dtshd,
        searchSubtitleRes = Res.string.ss_passthrough_codec_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackRows.AudioPassthrough.id)),
    )

    val PassthroughCodecTruehd = SettingsRow(
        id = "passthrough_codec_truehd",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_passthrough_codec_truehd,
        searchSubtitleRes = Res.string.ss_passthrough_codec_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(PlaybackRows.AudioPassthrough.id)),
    )

    val MaxAudioChannels = SettingsRow(
        id = "max_audio_channels",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_max_audio_channels,
        searchSubtitleRes = Res.string.ss_max_audio_channels_subtitle,
    )

    val DownmixBoost = SettingsRow(
        id = "downmix_boost",
        icon = Tabler.Outline.Volume,
        titleRes = Res.string.settings_downmix_boost,
        searchSubtitleRes = Res.string.ss_downmix_boost_subtitle,
    )

    val FrameRateMatching = SettingsRow(
        id = "frame_rate_matching",
        icon = Tabler.Outline.Maximize,
        titleRes = Res.string.settings_refresh_rate_match,
        searchTitleRes = Res.string.ss_frame_rate_matching_title,
        searchSubtitleRes = Res.string.ss_frame_rate_matching_subtitle,
    )

    val OfflinePlayback = SettingsRow(
        id = "offline_playback",
        icon = Tabler.Outline.Download,
        titleRes = Res.string.settings_offline_playback,
        searchSubtitleRes = Res.string.ss_offline_playback_subtitle,
    )

    val StreamingQuality = SettingsRow(
        id = "streaming_quality",
        icon = Tabler.Outline.BadgeHd,
        titleRes = Res.string.settings_streaming_quality,
        searchTitleRes = Res.string.ss_streaming_quality_title,
        searchSubtitleRes = Res.string.ss_streaming_quality_subtitle,
    )

    // ── RESIDUAL row (AudioStore — no spec machinery).
    val AudioDelay = SettingsRow(
        id = "audio_delay",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_audio_delay,
        searchTitleRes = Res.string.ss_audio_delay_title,
        searchSubtitleRes = Res.string.ss_audio_delay_subtitle,
        keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val LiveStreamOption = SettingsRow(
        id = "live_stream_option",
        icon = Tabler.Outline.DeviceTv,
        titleRes = Res.string.settings_live_tv_stream,
        searchTitleRes = Res.string.ss_live_stream_option_title,
        searchSubtitleRes = Res.string.ss_live_stream_option_subtitle,
    )

    // -- The "Engine Config" group's rows — HAND-MAINTAINED residuals: the engine-config knobs live in the spec-less engine store. One screen group fed by four adjacent branch lists (MPV / VLC / ExoPlayer / external), the screen renders the branch its preferred player selects. --

    // the MPV branch rows

    val MpvVideoOutput = SettingsRow(
        id = "mpv_video_output",
        icon = Tabler.Outline.Video,
        titleRes = Res.string.settings_video_output,
        searchTitleRes = Res.string.ss_mpv_video_output_title,
        searchSubtitleRes = Res.string.ss_mpv_video_output_subtitle,
        keywords = listOf("mpv", "video output", "vo", "gpu", "render"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvScaler = SettingsRow(
        id = "mpv_scaler",
        icon = Tabler.Outline.ArrowAutofitHeight,
        titleRes = Res.string.settings_scaler,
        searchTitleRes = Res.string.ss_mpv_scaler_title,
        searchSubtitleRes = Res.string.ss_mpv_scaler_subtitle,
        keywords = listOf("mpv", "scaler", "scaling", "interpolation", "quality"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvDebanding = SettingsRow(
        id = "mpv_debanding",
        icon = Tabler.Outline.ColorFilter,
        titleRes = Res.string.settings_debanding,
        searchTitleRes = Res.string.ss_mpv_debanding_title,
        searchSubtitleRes = Res.string.ss_mpv_debanding_subtitle,
        keywords = listOf("mpv", "deband", "debanding", "banding", "gradient"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvInterpolation = SettingsRow(
        id = "mpv_interpolation",
        icon = Tabler.Outline.ArrowsHorizontal,
        titleRes = Res.string.settings_interpolation,
        searchTitleRes = Res.string.ss_mpv_interpolation_title,
        searchSubtitleRes = Res.string.ss_mpv_interpolation_subtitle,
        keywords = listOf("mpv", "interpolation", "smooth", "motion", "judder"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvAudioOutput = SettingsRow(
        id = "mpv_audio_output",
        icon = Tabler.Outline.Volume,
        titleRes = Res.string.settings_audio_output,
        searchTitleRes = Res.string.ss_mpv_audio_output_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_output_subtitle,
        keywords = listOf("mpv", "audio output", "ao", "sound"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvAudioFallback = SettingsRow(
        id = "mpv_audio_fallback",
        icon = Tabler.Outline.ArrowBack,
        titleRes = Res.string.settings_audio_fallback,
        searchTitleRes = Res.string.ss_mpv_audio_fallback_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_fallback_subtitle,
        keywords = listOf("mpv", "audio", "fallback", "secondary", "output"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvAudioDevice = SettingsRow(
        id = "mpv_audio_device",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_audio_device,
        searchTitleRes = Res.string.ss_mpv_audio_device_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_device_subtitle,
        keywords = listOf("mpv", "audio device", "output device", "sound card", "speaker", "wasapi", "directsound"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.AudioDeviceSelection),
    )

    val MpvAudioExclusive = SettingsRow(
        id = "mpv_audio_exclusive",
        icon = Tabler.Outline.Lock,
        titleRes = Res.string.settings_audio_exclusive,
        searchTitleRes = Res.string.ss_mpv_audio_exclusive_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_exclusive_subtitle,
        keywords = listOf("mpv", "exclusive", "bit-perfect", "bitperfect", "wasapi exclusive", "device lock"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.AudioDeviceSelection),
    )

    val MpvAudioMode = SettingsRow(
        id = "mpv_audio_mode",
        icon = Tabler.Outline.Transfer,
        titleRes = Res.string.settings_audio_mode,
        searchTitleRes = Res.string.ss_mpv_audio_mode_title,
        searchSubtitleRes = Res.string.ss_mpv_audio_mode_subtitle,
        keywords = listOf("mpv", "passthrough", "spdif", "optical", "hdmi", "bitstream", "stereo downmix", "surround", "receiver"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.AudioDeviceSelection),
    )

    // ── Desktop-only render rows: the Anime4K extraction,
    // tone-mapping, quality-profile and vo=gpu-next HDR machinery is
    // desktop's (the HWND-embed path); Android's mpv hides all five.
    val MpvShaderPack = SettingsRow(
        id = "mpv_shader_pack",
        icon = Tabler.Outline.Wand,
        titleRes = Res.string.settings_shader_pack,
        searchTitleRes = Res.string.ss_mpv_shader_pack_title,
        searchSubtitleRes = Res.string.ss_mpv_shader_pack_subtitle,
        keywords = listOf("mpv", "shader", "anime4k", "glsl", "upscale", "pack", "fsrcnnx", "artcnn"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    )

    val MpvToneMapping = SettingsRow(
        id = "mpv_tone_mapping",
        icon = Tabler.Outline.Brightness,
        titleRes = Res.string.settings_tone_mapping,
        searchTitleRes = Res.string.ss_mpv_tone_mapping_title,
        searchSubtitleRes = Res.string.ss_mpv_tone_mapping_subtitle,
        keywords = listOf("mpv", "tone mapping", "hdr", "sdr", "bt2390", "hable", "reinhard", "mobius", "brightness"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    )

    val MpvRenderQuality = SettingsRow(
        id = "mpv_render_quality",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_render_quality,
        searchTitleRes = Res.string.ss_mpv_render_quality_title,
        searchSubtitleRes = Res.string.ss_mpv_render_quality_subtitle,
        keywords = listOf("mpv", "quality", "performance", "profile", "scaler", "deband", "high"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    )

    val MpvHdrPassthrough = SettingsRow(
        id = "mpv_hdr_passthrough",
        icon = Tabler.Outline.SunHigh,
        titleRes = Res.string.settings_hdr_passthrough,
        searchTitleRes = Res.string.ss_mpv_hdr_passthrough_title,
        searchSubtitleRes = Res.string.ss_mpv_hdr_passthrough_subtitle,
        keywords = listOf("mpv", "hdr", "hdr10", "passthrough", "gpu-next", "colorspace", "tv"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    )

    val MpvInterpolationTscale = SettingsRow(
        id = "mpv_interpolation_tscale",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_interpolation_tscale,
        searchTitleRes = Res.string.ss_mpv_tscale_title,
        searchSubtitleRes = Res.string.ss_mpv_tscale_subtitle,
        keywords = listOf("mpv", "tscale", "interpolation", "motion", "temporal", "mitchell", "oversample"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = DESKTOP_ONLY_PLATFORMS,
        gate = RowAdmission.Platform(RowAdmissionCapability.MpvRenderProfiles),
    )

    val MpvBufferSize = SettingsRow(
        id = "mpv_buffer_size",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_buffer_size,
        searchTitleRes = Res.string.ss_mpv_buffer_size_title,
        searchSubtitleRes = Res.string.ss_mpv_buffer_size_subtitle,
        keywords = listOf("mpv", "buffer", "demuxer", "size", "bytes", "cache"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvHwdecOverride = SettingsRow(
        id = "mpv_hwdec_override",
        icon = Tabler.Outline.Cpu,
        titleRes = Res.string.settings_hwdec_override,
        searchTitleRes = Res.string.ss_mpv_hwdec_override_title,
        searchSubtitleRes = Res.string.ss_mpv_hwdec_override_subtitle,
        keywords = listOf("mpv", "hardware", "hwdec", "decoder", "override", "gpu"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvSkipLoopFilter = SettingsRow(
        id = "mpv_skip_loop_filter",
        icon = Tabler.Outline.Filter,
        titleRes = Res.string.settings_skip_loop_filter,
        searchTitleRes = Res.string.ss_mpv_skip_loop_filter_title,
        searchSubtitleRes = Res.string.ss_mpv_skip_loop_filter_subtitle,
        keywords = listOf("mpv", "skip", "loop filter", "h264", "performance"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvFrameDrop = SettingsRow(
        id = "mpv_frame_drop",
        icon = Tabler.Outline.PhotoDown,
        titleRes = Res.string.settings_frame_drop,
        searchTitleRes = Res.string.ss_mpv_frame_drop_title,
        searchSubtitleRes = Res.string.ss_mpv_frame_drop_subtitle,
        keywords = listOf("mpv", "frame", "drop", "vdrop", "performance"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val MpvExtraConfig = SettingsRow(
        id = "mpv_extra_config",
        icon = Tabler.Outline.Code,
        titleRes = Res.string.settings_advanced_config,
        searchSubtitleRes = Res.string.ss_mpv_extra_config_subtitle,
        keywords = listOf("mpv", "advanced", "config", "raw", "options", "editor", "custom"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    val ResetEngineDefaults = SettingsRow(
        id = "reset_engine_defaults",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_reset_to_defaults,
        searchSubtitleRes = Res.string.ss_reset_engine_defaults_subtitle,
        keywords = listOf("reset", "defaults", "restore", "engine", "mpv", "vlc", "exoplayer", "configuration"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
    )

    // the VLC branch rows (Android-only via the platform tags)

    val VlcAudioOutput = SettingsRow(
        id = "vlc_audio_output",
        icon = Tabler.Outline.Volume,
        titleRes = Res.string.settings_audio_output,
        searchTitleRes = Res.string.ss_vlc_audio_output_title,
        searchSubtitleRes = Res.string.ss_vlc_audio_output_subtitle,
        keywords = listOf("vlc", "libvlc", "audio output", "sound"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcAudioTimeStretch = SettingsRow(
        id = "vlc_audio_time_stretch",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_audio_time_stretch,
        searchTitleRes = Res.string.ss_vlc_audio_time_stretch_title,
        searchSubtitleRes = Res.string.ss_vlc_audio_time_stretch_subtitle,
        keywords = listOf("vlc", "time stretch", "pitch", "speed"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcVideoOutput = SettingsRow(
        id = "vlc_video_output",
        icon = Tabler.Outline.Video,
        titleRes = Res.string.settings_video_output,
        searchTitleRes = Res.string.ss_vlc_video_output_title,
        searchSubtitleRes = Res.string.ss_vlc_video_output_subtitle,
        keywords = listOf("vlc", "libvlc", "video output", "display"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcNetworkCaching = SettingsRow(
        id = "vlc_network_caching",
        icon = Tabler.Outline.Wifi,
        titleRes = Res.string.settings_network_caching,
        searchTitleRes = Res.string.ss_vlc_network_caching_title,
        searchSubtitleRes = Res.string.ss_vlc_network_caching_subtitle,
        keywords = listOf("vlc", "network", "caching", "buffer", "streaming"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcSkipLoopFilter = SettingsRow(
        id = "vlc_skip_loop_filter",
        icon = Tabler.Outline.Filter,
        titleRes = Res.string.settings_skip_loop_filter,
        searchTitleRes = Res.string.ss_vlc_skip_loop_filter_title,
        searchSubtitleRes = Res.string.ss_vlc_skip_loop_filter_subtitle,
        keywords = listOf("vlc", "skip", "loop filter", "h264", "quality"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcSkipFrames = SettingsRow(
        id = "vlc_skip_frames",
        icon = Tabler.Outline.PlayerSkipForward,
        titleRes = Res.string.settings_skip_frames,
        searchTitleRes = Res.string.ss_vlc_skip_frames_title,
        searchSubtitleRes = Res.string.ss_vlc_skip_frames_subtitle,
        keywords = listOf("vlc", "skip frames", "performance", "frame"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcDecoderThreads = SettingsRow(
        id = "vlc_decoder_threads",
        icon = Tabler.Outline.Cpu,
        titleRes = Res.string.settings_decoder_threads,
        searchTitleRes = Res.string.ss_vlc_decoder_threads_title,
        searchSubtitleRes = Res.string.ss_vlc_decoder_threads_subtitle,
        keywords = listOf("vlc", "decoder", "threads", "cpu", "multithreading"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val VlcDropLateFrames = SettingsRow(
        id = "vlc_drop_late_frames",
        icon = Tabler.Outline.Trash,
        titleRes = Res.string.settings_drop_late_frames,
        searchTitleRes = Res.string.ss_vlc_drop_late_frames_title,
        searchSubtitleRes = Res.string.ss_vlc_drop_late_frames_subtitle,
        keywords = listOf("vlc", "drop", "late", "frames", "delayed"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    // the ExoPlayer branch rows (Android-only via the platform tags)

    val ExoVideoScaling = SettingsRow(
        id = "exo_video_scaling",
        icon = Tabler.Outline.ArrowAutofitHeight,
        titleRes = Res.string.settings_video_scaling,
        searchTitleRes = Res.string.ss_exo_video_scaling_title,
        searchSubtitleRes = Res.string.ss_exo_video_scaling_subtitle,
        keywords = listOf("exoplayer", "exo", "scaling", "video", "resize"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val ExoFrameRateStrategy = SettingsRow(
        id = "exo_frame_rate_strategy",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_frame_rate_strategy,
        searchTitleRes = Res.string.ss_exo_frame_rate_strategy_title,
        searchSubtitleRes = Res.string.ss_exo_frame_rate_strategy_subtitle,
        keywords = listOf("exoplayer", "exo", "frame rate", "refresh", "strategy"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val ExoSkipSilence = SettingsRow(
        id = "exo_skip_silence",
        icon = Tabler.Outline.Volume,
        titleRes = Res.string.settings_skip_silence,
        searchTitleRes = Res.string.ss_exo_skip_silence_title,
        searchSubtitleRes = Res.string.ss_exo_skip_silence_subtitle,
        keywords = listOf("exoplayer", "exo", "skip", "silence", "audio", "gap"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val ExoAudioOffload = SettingsRow(
        id = "exo_audio_offload",
        icon = Tabler.Outline.Headphones,
        titleRes = Res.string.settings_audio_offload,
        searchTitleRes = Res.string.ss_exo_audio_offload_title,
        searchSubtitleRes = Res.string.ss_exo_audio_offload_subtitle,
        keywords = listOf("exoplayer", "exo", "audio", "offload", "battery"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val ExoDecoderFallback = SettingsRow(
        id = "exo_decoder_fallback",
        icon = Tabler.Outline.ToggleLeft,
        titleRes = Res.string.settings_decoder_fallback,
        searchTitleRes = Res.string.ss_exo_decoder_fallback_title,
        searchSubtitleRes = Res.string.ss_exo_decoder_fallback_subtitle,
        keywords = listOf("exoplayer", "exo", "decoder", "fallback", "secondary"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val ExoBackBuffer = SettingsRow(
        id = "exo_back_buffer",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_back_buffer,
        searchTitleRes = Res.string.ss_exo_back_buffer_title,
        searchSubtitleRes = Res.string.ss_exo_back_buffer_subtitle,
        keywords = listOf("exoplayer", "exo", "back buffer", "rewind", "buffer"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    val ExoPreferredCodecs = SettingsRow(
        id = "exo_preferred_codecs",
        icon = Tabler.Outline.Code,
        titleRes = Res.string.settings_preferred_codecs,
        searchTitleRes = Res.string.ss_exo_preferred_codecs_title,
        searchSubtitleRes = Res.string.ss_exo_preferred_codecs_subtitle,
        keywords = listOf("exoplayer", "exo", "codec", "mime", "preferred", "video"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    )

    // the external-player branch row

    val ExternalPlayerApp = SettingsRow(
        id = "external_player_app",
        icon = Tabler.Outline.Devices,
        titleRes = Res.string.settings_external_player_app,
        searchTitleRes = Res.string.ss_external_player_app_title,
        searchSubtitleRes = Res.string.ss_external_player_app_subtitle,
    )

    // -- The "SyncPlay" group's rows — HAND-MAINTAINED residuals (the SyncPlay knobs live in the spec-less syncplay/casting store). --

    val SyncplayJoinBehavior = SettingsRow(
        id = "syncplay_join_behavior",
        icon = Tabler.Outline.MessageQuestion,
        titleRes = Res.string.settings_join_behavior,
        searchTitleRes = Res.string.ss_syncplay_join_behavior_title,
        searchSubtitleRes = Res.string.ss_syncplay_join_behavior_subtitle,
        keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
        route = Route.PlaybackSettings(),
    )

    val SyncplayTolerance = SettingsRow(
        id = "syncplay_tolerance",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_sync_tolerance,
        searchTitleRes = Res.string.ss_syncplay_tolerance_title,
        searchSubtitleRes = Res.string.ss_syncplay_tolerance_subtitle,
        keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
        route = Route.PlaybackSettings(),
    )

    val SyncplayAutoAcceptInvites = SettingsRow(
        id = "syncplay_auto_accept_invites",
        icon = Tabler.Outline.CircleCheck,
        titleRes = Res.string.settings_auto_accept_invites,
        searchTitleRes = Res.string.ss_syncplay_auto_accept_invites_title,
        searchSubtitleRes = Res.string.ss_syncplay_auto_accept_invites_subtitle,
        keywords = listOf("syncplay", "auto", "accept", "invites", "friends"),
        route = Route.PlaybackSettings(),
    )

    // -- The "Casting & DLNA" group's rows — HAND-MAINTAINED residuals (same spec-less store). --

    val CastingStrategy = SettingsRow(
        id = "casting_strategy",
        icon = Tabler.Outline.Cast,
        titleRes = Res.string.settings_casting_strategy,
        searchTitleRes = Res.string.ss_casting_strategy_title,
        searchSubtitleRes = Res.string.ss_casting_strategy_subtitle,
        keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
        route = Route.PlaybackSettings(),
    )

    val BackgroundCasting = SettingsRow(
        id = "background_casting",
        icon = Tabler.Outline.Settings,
        titleRes = Res.string.settings_background_casting,
        searchTitleRes = Res.string.ss_background_casting_title,
        searchSubtitleRes = Res.string.ss_background_casting_subtitle,
        keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
        route = Route.PlaybackSettings(),
    )

    val PreferredRenderer = SettingsRow(
        id = "preferred_renderer",
        icon = Tabler.Outline.Devices,
        titleRes = Res.string.settings_preferred_renderer,
        searchTitleRes = Res.string.ss_preferred_renderer_title,
        searchSubtitleRes = Res.string.ss_preferred_renderer_subtitle,
        keywords = listOf("renderer", "preferred", "cast", "device", "target", "tv"),
        route = Route.PlaybackSettings(),
    )

    // -- The "Live TV & DVR" group's DVR rows — HAND-MAINTAINED residuals (the padding/quality knobs live in the spec-less syncplay/casting store). --

    // ── RESIDUAL rows (SyncPlayCastStore — no spec machinery).
    val DvrPrePadding = SettingsRow(
        id = "dvr_pre_padding",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_dvr_pre_padding,
        searchTitleRes = Res.string.ss_dvr_pre_padding_title,
        searchSubtitleRes = Res.string.ss_dvr_pre_padding_subtitle,
        keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
        route = Route.PlaybackSettings(),
    )

    val DvrPostPadding = SettingsRow(
        id = "dvr_post_padding",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_dvr_post_padding,
        searchTitleRes = Res.string.ss_dvr_post_padding_title,
        searchSubtitleRes = Res.string.ss_dvr_post_padding_subtitle,
        keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
        route = Route.PlaybackSettings(),
    )

    // Advanced-tagged yet always rendered: the DVR block sits outside the
    // advanced structural block and feeds the declaration size itself, so the
    // legacy tag the screen never honored gets the explicit Always override
    // (the appearance HapticsEnabled shape).
    val DvrRecordingQuality = SettingsRow(
        id = "dvr_recording_quality",
        icon = Tabler.Outline.BadgeHd,
        titleRes = Res.string.settings_dvr_recording_quality,
        searchTitleRes = Res.string.ss_dvr_recording_quality_title,
        searchSubtitleRes = Res.string.ss_dvr_recording_quality_subtitle,
        keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
        route = Route.PlaybackSettings(),
        isAdvanced = true,
        gate = RowAdmission.Always,
    )

    // -- The media-segment group's rows — the spec-backed per-type rows plus the skip-on-seek toggle, in catalog order. --

    val MediaSegmentIntro = SettingsRow(
        id = "media_segment_intro",
        icon = Tabler.Outline.SquareRounded,
        titleRes = CoreUiRes.string.core_segment_intro,
        searchSubtitleRes = CoreUiRes.string.core_segment_intro_desc,
    )

    val MediaSegmentOutro = SettingsRow(
        id = "media_segment_outro",
        icon = Tabler.Outline.SquareRounded,
        titleRes = CoreUiRes.string.core_segment_outro,
        searchSubtitleRes = CoreUiRes.string.core_segment_outro_desc,
    )

    val MediaSegmentPreview = SettingsRow(
        id = "media_segment_preview",
        icon = Tabler.Outline.SquareRounded,
        titleRes = CoreUiRes.string.core_segment_preview,
        searchSubtitleRes = CoreUiRes.string.core_segment_preview_desc,
    )

    val MediaSegmentRecap = SettingsRow(
        id = "media_segment_recap",
        icon = Tabler.Outline.SquareRounded,
        titleRes = CoreUiRes.string.core_segment_recap,
        searchSubtitleRes = CoreUiRes.string.core_segment_recap_desc,
    )

    val MediaSegmentCommercial = SettingsRow(
        id = "media_segment_commercial",
        icon = Tabler.Outline.SquareRounded,
        titleRes = CoreUiRes.string.core_segment_commercial,
        searchSubtitleRes = CoreUiRes.string.core_segment_commercial_desc,
    )

    val MediaSegmentUnknown = SettingsRow(
        id = "media_segment_unknown",
        icon = Tabler.Outline.SquareRounded,
        titleRes = CoreUiRes.string.core_segment_unknown,
        searchSubtitleRes = CoreUiRes.string.core_segment_unknown_desc,
    )

    val SkipSegmentsOnSeek = SettingsRow(
        id = "skip_segments_on_seek",
        icon = Tabler.Outline.PlayerTrackNext,
        titleRes = Res.string.settings_skip_segments_on_seek,
        searchSubtitleRes = Res.string.ss_skip_segments_on_seek_subtitle,
    )

    /**
     * Every fused playback row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = PlaybackPlayerRows + PlaybackAdvancedVideoRows + PlaybackEngineRows + PlaybackSyncPlayRows + PlaybackCastingRows + PlaybackDvrRows + PlaybackMediaSegmentsRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = mapOf(
    PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS to Route.PlaybackSettings(),
)

private val playbackSpecEntries: List<PreferenceSearchSpec> =
    PlaybackPreferenceSpecs.searchEntries + VideoPlayerPreferenceSpecs.searchEntries

private val playbackCategory = CoreUiRes.string.ss_cat_playback

internal val PlaybackPlayerRows: List<SettingsRow> = listOf(
    PlaybackRows.PlayerEngine,
    PlaybackRows.SeekDuration,
    PlaybackRows.Orientation,
    PlaybackRows.Gestures,
    PlaybackRows.GestureIndicatorSide,
    PlaybackRows.DoubleTapHoldSeek,
    PlaybackRows.DefaultSpeed,
    PlaybackRows.DefaultAspect,
    PlaybackRows.VideoAutoplayNext,
    PlaybackRows.AutoplayCountdown,
    PlaybackRows.StillWatchingMode,
    PlaybackRows.StillWatchingEpisodes,
    PlaybackRows.ControlsTimeout,
    PlaybackRows.HideOsdOnPause,
    PlaybackRows.SkipBackOnResume,
    PlaybackRows.ShowClockPlayer,
    PlaybackRows.PassOutProtection,
    PlaybackRows.DuckOnTransientFocusLoss,
    PlaybackRows.ResumeHeadsetPlug,
    PlaybackRows.AutoplayTrailers,
    PlaybackRows.CinemaMode,
    PlaybackRows.EpisodeBrowser,
    PlaybackRows.PlaybackMetadata,
    PlaybackRows.SwipeSeekRange,
    PlaybackRows.RememberBrightness,
    PlaybackRows.TrickplayPreview,
    PlaybackRows.PreloadBuffer,
    PlaybackRows.VideoCacheSize,
    PlaybackRows.BackgroundAudio,
    PlaybackRows.AutoEnterPip,
    PlaybackRows.KeepScreenOn,
    PlaybackRows.IncognitoMode,
    PlaybackRows.HoldSpeedMultiplier,
    PlaybackRows.AndroidTvWatchNext,
    PlaybackRows.TvZoomMode,
    PlaybackRows.DefaultBrightnessLevel,
    PlaybackRows.TrickplayOnGestures,
    PlaybackRows.ShowTimeRemaining,
    PlaybackRows.PauseOnFocusLoss,
    PlaybackRows.RememberVolumePerContentType,
)

internal val PlaybackAdvancedVideoRows: List<SettingsRow> = listOf(
    PlaybackRows.DialogueBoost,
    PlaybackRows.DialogueBoostStrength,
    PlaybackRows.Decoder,
    PlaybackRows.AudioPassthrough,
    PlaybackRows.PassthroughCodecAc3,
    PlaybackRows.PassthroughCodecEac3,
    PlaybackRows.PassthroughCodecDts,
    PlaybackRows.PassthroughCodecDtshd,
    PlaybackRows.PassthroughCodecTruehd,
    PlaybackRows.MaxAudioChannels,
    PlaybackRows.DownmixBoost,
    PlaybackRows.FrameRateMatching,
    PlaybackRows.OfflinePlayback,
    PlaybackRows.StreamingQuality,
    PlaybackRows.AudioDelay,
    PlaybackRows.LiveStreamOption,
)

internal val PlaybackEngineMpvRows: List<SettingsRow> = listOf(
    PlaybackRows.MpvVideoOutput,
    PlaybackRows.MpvScaler,
    PlaybackRows.MpvDebanding,
    PlaybackRows.MpvInterpolation,
    PlaybackRows.MpvAudioOutput,
    PlaybackRows.MpvAudioFallback,
    PlaybackRows.MpvAudioDevice,
    PlaybackRows.MpvAudioExclusive,
    PlaybackRows.MpvAudioMode,
    PlaybackRows.MpvShaderPack,
    PlaybackRows.MpvToneMapping,
    PlaybackRows.MpvRenderQuality,
    PlaybackRows.MpvHdrPassthrough,
    PlaybackRows.MpvInterpolationTscale,
    PlaybackRows.MpvBufferSize,
    PlaybackRows.MpvHwdecOverride,
    PlaybackRows.MpvSkipLoopFilter,
    PlaybackRows.MpvFrameDrop,
    PlaybackRows.MpvExtraConfig,
    PlaybackRows.ResetEngineDefaults,
)

internal val PlaybackEngineVlcRows: List<SettingsRow> = listOf(
    PlaybackRows.VlcAudioOutput,
    PlaybackRows.VlcAudioTimeStretch,
    PlaybackRows.VlcVideoOutput,
    PlaybackRows.VlcNetworkCaching,
    PlaybackRows.VlcSkipLoopFilter,
    PlaybackRows.VlcSkipFrames,
    PlaybackRows.VlcDecoderThreads,
    PlaybackRows.VlcDropLateFrames,
)

internal val PlaybackEngineExoRows: List<SettingsRow> = listOf(
    PlaybackRows.ExoVideoScaling,
    PlaybackRows.ExoFrameRateStrategy,
    PlaybackRows.ExoSkipSilence,
    PlaybackRows.ExoAudioOffload,
    PlaybackRows.ExoDecoderFallback,
    PlaybackRows.ExoBackBuffer,
    PlaybackRows.ExoPreferredCodecs,
)

internal val PlaybackEngineExternalRows: List<SettingsRow> = listOf(
    PlaybackRows.ExternalPlayerApp,
)

/** PlaybackEngineRows — the concatenated branch lists, in catalog order. */
internal val PlaybackEngineRows: List<SettingsRow> = PlaybackEngineMpvRows + PlaybackEngineVlcRows + PlaybackEngineExoRows + PlaybackEngineExternalRows

internal val PlaybackSyncPlayRows: List<SettingsRow> = listOf(
    PlaybackRows.SyncplayJoinBehavior,
    PlaybackRows.SyncplayTolerance,
    PlaybackRows.SyncplayAutoAcceptInvites,
)

internal val PlaybackCastingRows: List<SettingsRow> = listOf(
    PlaybackRows.CastingStrategy,
    PlaybackRows.BackgroundCasting,
    PlaybackRows.PreferredRenderer,
)

internal val PlaybackDvrRows: List<SettingsRow> = listOf(
    PlaybackRows.DvrPrePadding,
    PlaybackRows.DvrPostPadding,
    PlaybackRows.DvrRecordingQuality,
)

internal val PlaybackMediaSegmentsRows: List<SettingsRow> = listOf(
    PlaybackRows.MediaSegmentIntro,
    PlaybackRows.MediaSegmentOutro,
    PlaybackRows.MediaSegmentPreview,
    PlaybackRows.MediaSegmentRecap,
    PlaybackRows.MediaSegmentCommercial,
    PlaybackRows.MediaSegmentUnknown,
    PlaybackRows.SkipSegmentsOnSeek,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val PlaybackPlayerGroup =
    PlaybackPlayerRows.asRowGroup("playback.player", specEntriesFor(PlaybackPlayerRows, playbackSpecEntries), searchRoutes, playbackCategory)

internal val PlaybackAdvancedVideoGroup =
    PlaybackAdvancedVideoRows.asRowGroup("playback.advancedVideo", specEntriesFor(PlaybackAdvancedVideoRows, playbackSpecEntries), searchRoutes, playbackCategory)

internal val PlaybackEngineGroup =
    PlaybackEngineRows.asRowGroup("playback.engine", specEntriesFor(PlaybackEngineRows, playbackSpecEntries), searchRoutes, playbackCategory)

internal val PlaybackSyncPlayGroup =
    PlaybackSyncPlayRows.asRowGroup("playback.syncPlay", emptyList(), searchRoutes, playbackCategory)

internal val PlaybackCastingGroup =
    PlaybackCastingRows.asRowGroup("playback.casting", emptyList(), searchRoutes, playbackCategory)

internal val PlaybackDvrGroup =
    PlaybackDvrRows.asRowGroup("playback.dvr", emptyList(), searchRoutes, playbackCategory)

internal val PlaybackMediaSegmentsGroup =
    PlaybackMediaSegmentsRows.asRowGroup("playback.mediaSegments", specEntriesFor(PlaybackMediaSegmentsRows, playbackSpecEntries), searchRoutes, playbackCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val PlaybackSettingsSearchItems: List<SettingsSearchItem> = PlaybackPlayerGroup.items

internal val PlaybackAdvancedVideoSearchItems: List<SettingsSearchItem> = PlaybackAdvancedVideoGroup.items

internal val SyncPlaySearchItems: List<SettingsSearchItem> = PlaybackSyncPlayGroup.items

internal val CastingSearchItems: List<SettingsSearchItem> = PlaybackCastingGroup.items

/** The catalog projection of the MPV engine branch (the `playback.engine` group's mpv_-prefixed rows). */
internal val MpvEngineSearchItems: List<SettingsSearchItem> = PlaybackEngineGroup.items.filter { it.id.startsWith("mpv_") }

/** The catalog projection of the VLC engine branch (Android-only via the rows' platform tags). */
internal val VlcEngineSearchItems: List<SettingsSearchItem> = PlaybackEngineGroup.items.filter { it.id.startsWith("vlc_") }

/** The catalog projection of the ExoPlayer engine branch (Android-only via the rows' platform tags). */
internal val ExoPlayerEngineSearchItems: List<SettingsSearchItem> = PlaybackEngineGroup.items.filter { it.id.startsWith("exo_") }

/** The catalog projection of the external-player branch (the one-row branch). */
internal val ExternalEngineSearchItems: List<SettingsSearchItem> = PlaybackEngineGroup.items.filter { it.id == PlaybackRows.ExternalPlayerApp.id }

/** The whole Live TV & DVR declaration list — the two screen groups' concatenation (the DVR residuals then the spec-backed segment rows), the pinned catalog order. */
internal val LiveTvSearchItems: List<SettingsSearchItem> = PlaybackDvrGroup.items + PlaybackMediaSegmentsGroup.items

// -- The domain's root-screen entrance declaration --

/** The playback domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_playback` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val PlaybackEntrance = SettingsEntranceSectionRow(
    key = "item_playback",
    rowId = "playback",
    icon = Tabler.Outline.PlayerPlay,
    titleRes = Res.string.settings_playback,
)
