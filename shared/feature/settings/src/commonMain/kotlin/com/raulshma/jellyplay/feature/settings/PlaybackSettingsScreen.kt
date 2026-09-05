package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.parseMpvConfigOptions
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvAudioOutput
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvFrameDrop
import com.raulshma.jellyplay.core.model.MpvHwdec
import com.raulshma.jellyplay.core.model.MpvScaler
import com.raulshma.jellyplay.core.model.MpvSkipLoopFilter
import com.raulshma.jellyplay.core.model.MpvVideoOutput
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.VlcAudioOutput
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.model.localizedDescription
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_mpv_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_all_codecs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_delay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_delay_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_fallback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_offload
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_output
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_passthrough
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_passthrough_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_passthrough_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_time_stretch
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_time_stretch_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_time_stretch_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_accept_invites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_accept_invites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_device_based
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_countdown
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_countdown_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_back_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_back_buffer_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_audio
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_audio_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_casting
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_casting_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_all_aggressive
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_all_fastest
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_bidir
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_level
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_none_best
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_none_no_skip
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_non_ref
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_buffer_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_dlna
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_prefer_cast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_prefer_dlna
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_strategy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_strategy_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_strategy_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_codecs_av1_hevc_avc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_codecs_avc_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_codecs_hevc_avc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_countdown_immediate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_countdown_seconds
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom_options
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_fallback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_fallback_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_fallback_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_threads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_aspect
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_aspect_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_brightness_level
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_brightness_level_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_speed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_speed_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_debanding
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_debanding_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_debanding_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dialogue_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dialogue_boost_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_double_tap_seek_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_drop_late_frames
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_drop_late_frames_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_drop_late_frames_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_duck_on_phone_call
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_duck_on_phone_call_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_duck_on_phone_call_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_padding_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_post_padding
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_post_padding_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_pre_padding
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_pre_padding_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_high
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_low
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_medium
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_recording_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_recording_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_start_early
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_start_on_time
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_stop_late
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_stop_on_time
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_engine_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_episode_browser
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_episode_browser_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_episode_browser_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_controls_timeout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_controls_timeout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_frame_drop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_frame_rate_strategy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_opposite
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_same
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_side
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hold_to_seek_speed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hold_to_seek_speed_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hwdec_override
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hwdec_universal
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incognito_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incognito_mode_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incognito_mode_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_always
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_behavior
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_behavior_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_never
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_keep_screen_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_keep_screen_on_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_living_room_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_direct
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto_adaptive
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_transcode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_tv_dvr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_tv_stream
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_media_segments
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_media_segments_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_mpv_helper_text
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_caching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_audio_delay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_delay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_fallback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_orientation
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_orientation_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pass_out_protection
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pass_out_protection_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_on_focus_loss
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_on_focus_loss_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_on_focus_loss_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_player_engine
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_player_engine_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_codecs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_renderer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_renderer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preload_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preload_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_cache_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_1080p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_1080p_full_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_360p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_360p_low
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_480p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_480p_sd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_4k
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_4k_ultra_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_720p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_720p_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_raw_mpv_options
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_desc_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_desc_rate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_desc_res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_match
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_matching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_rate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_rate_res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remember_brightness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remember_brightness_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remember_brightness_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_exoplayer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_libvlc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_mpv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_to_defaults
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_resolution_switching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_scaler
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seek_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seek_duration_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_player_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_player_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_time_remaining
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_time_remaining_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_time_remaining_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_back_on_resume
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_back_on_resume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_frames
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_frames_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_frames_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_loop_filter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_silence
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_silence_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_silence_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_swipe_seek_range
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_swipe_seek_range_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_balanced
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_loose
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_tight
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_tolerance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_tolerance_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_syncplay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_syncplay_join_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_threads_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_preview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_preview_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_preview_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_percent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_output
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_scaling
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_x_minutes

private fun streamingQualityLabelRes(quality: StreamingQuality): StringResource = when (quality) {
    StreamingQuality.AUTO -> Res.string.settings_streaming_quality_auto_adaptive
    StreamingQuality.LOW_360P -> Res.string.settings_quality_360p_low
    StreamingQuality.SD_480P -> Res.string.settings_quality_480p_sd
    StreamingQuality.HD_720P -> Res.string.settings_quality_720p_hd
    StreamingQuality.FHD_1080P -> Res.string.settings_quality_1080p_full_hd
    StreamingQuality.UHD_4K -> Res.string.settings_quality_4k_ultra_hd
}

private fun streamingQualityShortRes(quality: StreamingQuality): StringResource = when (quality) {
    StreamingQuality.AUTO -> Res.string.settings_streaming_quality_auto
    StreamingQuality.LOW_360P -> Res.string.settings_quality_360p
    StreamingQuality.SD_480P -> Res.string.settings_quality_480p
    StreamingQuality.HD_720P -> Res.string.settings_quality_720p
    StreamingQuality.FHD_1080P -> Res.string.settings_quality_1080p
    StreamingQuality.UHD_4K -> Res.string.settings_quality_4k
}

private fun liveStreamOptionLabelRes(option: LiveStreamOption): StringResource = when (option) {
    LiveStreamOption.AUTO -> Res.string.settings_live_auto
    LiveStreamOption.DIRECT_STREAM -> Res.string.settings_live_direct
    LiveStreamOption.TRANSCODE -> Res.string.settings_live_transcode
}

private fun vlcSkipLoopFilterLabelRes(level: Int): StringResource = when (level) {
    0 -> Res.string.settings_b_frames_none_best
    1 -> Res.string.settings_b_frames_default
    2 -> Res.string.settings_b_frames_non_ref
    3 -> Res.string.settings_b_frames_bidir
    4 -> Res.string.settings_b_frames_all_fastest
    else -> Res.string.settings_b_frames_level
}

private fun vlcSkipFrameLabelRes(level: Int): StringResource = when (level) {
    0 -> Res.string.settings_b_frames_none_no_skip
    1 -> Res.string.settings_b_frames_default
    2 -> Res.string.settings_b_frames_non_ref
    3 -> Res.string.settings_b_frames_bidir
    4 -> Res.string.settings_b_frames_all_aggressive
    else -> Res.string.settings_b_frames_level
}

private val PLAYBACK_ADVANCED_GROUP_IDS = setOf("dialogue_boost", "dialogue_boost_strength", "decoder", "audio_passthrough", "frame_rate_matching", "streaming_quality", "audio_delay")
private val PLAYBACK_ENGINE_GROUP_IDS = setOf(
    "mpv_video_output", "mpv_scaler", "mpv_debanding", "mpv_interpolation", "mpv_audio_output",
    "mpv_audio_fallback", "mpv_buffer_size", "mpv_hwdec_override", "mpv_skip_loop_filter", "mpv_frame_drop",
    "mpv_extra_config", "reset_engine_defaults",
    "vlc_audio_output", "vlc_audio_time_stretch", "vlc_network_caching",
    "vlc_skip_loop_filter", "vlc_skip_frames", "vlc_decoder_threads", "vlc_drop_late_frames",
    "exo_video_scaling", "exo_frame_rate_strategy", "exo_skip_silence", "exo_audio_offload",
    "exo_decoder_fallback", "exo_back_buffer", "exo_preferred_codecs",
)
private val PLAYBACK_SYNCPLAY_GROUP_IDS = setOf("syncplay_join_behavior", "syncplay_tolerance", "syncplay_auto_accept_invites")
private val PLAYBACK_CASTING_GROUP_IDS = setOf("casting_strategy", "background_casting", "preferred_renderer")
private val PLAYBACK_DVR_GROUP_IDS = setOf("dvr_pre_padding", "dvr_post_padding", "dvr_recording_quality")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "playback_init",
    )

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId, showAdvanced) {
        val playerGroup = listOf(
            "player_engine", "seek_duration", "orientation", "gestures", "default_speed",
            "hold_speed_multiplier", "default_aspect", "video_autoplay_next", "autoplay_countdown",
            "controls_timeout", "skip_back_on_resume", "pass_out_protection", "autoplay_trailers",
            "cinema_mode", "android_tv_watch_next", "tv_zoom_mode", "episode_browser", "playback_metadata",
            "swipe_seek_range", "remember_brightness", "default_brightness_level", "trickplay_preview",
            "trickplay_on_gestures", "preload_buffer", "video_cache_size", "background_audio", "keep_screen_on", "incognito_mode",
            "show_time_remaining", "show_clock_player", "pause_on_focus_loss", "duck_on_transient_focus_loss",
        )
        val advancedVideo = listOf(
            "dialogue_boost", "decoder", "audio_passthrough", "frame_rate_matching",
            "streaming_quality", "audio_delay",
        )
        val engineConfig = listOf(
            "mpv_video_output", "mpv_scaler", "mpv_debanding", "mpv_interpolation", "mpv_audio_output",
            "mpv_audio_fallback", "mpv_buffer_size", "mpv_hwdec_override", "mpv_skip_loop_filter", "mpv_frame_drop",
            "vlc_audio_output", "vlc_audio_time_stretch", "vlc_network_caching",
            "vlc_skip_loop_filter", "vlc_skip_frames", "vlc_decoder_threads", "vlc_drop_late_frames",
            "exo_video_scaling", "exo_frame_rate_strategy", "exo_skip_silence", "exo_audio_offload",
            "exo_decoder_fallback", "exo_back_buffer", "exo_preferred_codecs",
        )
        val syncPlay = listOf("syncplay_join_behavior", "syncplay_tolerance", "syncplay_auto_accept_invites")
        val casting = listOf("casting_strategy", "background_casting", "preferred_renderer")
        val dvr = listOf("dvr_pre_padding", "dvr_post_padding", "dvr_recording_quality")
        when (highlightSettingId) {
            in playerGroup -> 0
            in advancedVideo -> if (showAdvanced) 1 else -1
            in engineConfig -> if (showAdvanced) 2 else 1
            in syncPlay -> if (showAdvanced) 4 else 3
            in casting -> if (showAdvanced) 5 else 4
            in dvr -> if (showAdvanced) 6 else 5
            else -> -1
        }
    }

    // Phase 1 (coarse): scroll the containing group into the LazyColumn's composition window so the
    // target item is actually composed — items in off-screen groups (later sections) are otherwise
    // never mounted and their bringIntoViewRequester has no target. Phase 2 (centering) is then
    // performed by the highlighted item itself via CenterBringIntoViewSpec.
    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_playback_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
            IconButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.focusIndicator(CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = stringResource(Res.string.settings_reset_playback_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerPlay,
                    title = stringResource(Res.string.settings_video_player),
                    summary = { stringResource(Res.string.settings_playback_subtitle, preferences.preferredPlayer.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingsItemList(total = if (showAdvanced) 30 else 11) {
                    val preferredPlayerTitle = stringResource(Res.string.settings_preferred_player)
                    SettingListItem(
                        icon = Tabler.Outline.PlayerPlay,
                        title = stringResource(Res.string.settings_player_engine),
                        subtitle = stringResource(Res.string.settings_player_engine_subtitle),
                        trailingText = preferences.preferredPlayer.displayName,
                        highlighted = highlightSettingId == "player_engine",
                        onClick = {
                            activePicker = PickerState.List(
                                title = preferredPlayerTitle,
                                items = PlayerType.entries,
                                label = { it.displayName },
                                subtitle = { it.description },
                                isSelected = { it == preferences.preferredPlayer },
                                onSelect = { viewModel.edit { scope -> scope.playback.setPreferredPlayer(it) } },
                            )
                        },
                    )
                    val doubleTapSeekTitle = stringResource(Res.string.settings_double_tap_seek_duration)
                    SettingListItem(
                        icon = Tabler.Outline.PlayerTrackNext,
                        title = stringResource(Res.string.settings_seek_duration),
                        subtitle = stringResource(Res.string.settings_seek_duration_subtitle),
                        trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                        highlighted = highlightSettingId == "seek_duration",
                        onClick = {
                            val durations = listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L, 60_000L)
                            activePicker = pickerChip(
                                title = doubleTapSeekTitle,
                                values = durations,
                                current = preferences.videoSeekDurationMs,
                                label = { "${it / 1000}s" },
                                onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoSeekDurationMs(ms) } },
                            )
                        },
                    )
                    val orientationTitle = stringResource(Res.string.settings_orientation)
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobileRotated,
                        title = stringResource(Res.string.settings_orientation),
                        subtitle = stringResource(Res.string.settings_orientation_subtitle),
                        trailingText = preferences.videoDefaultOrientation.displayName,
                        highlighted = highlightSettingId == "orientation",
                        onClick = {
                            activePicker = PickerState.List(
                                title = orientationTitle,
                                items = OrientationMode.entries,
                                label = { it.displayName },
                                subtitle = { it.constant },
                                isSelected = { it == preferences.videoDefaultOrientation },
                                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoDefaultOrientation(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandMove,
                        title = stringResource(Res.string.settings_gestures),
                        subtitle = if (preferences.videoGesturesEnabled) stringResource(Res.string.settings_gestures_on) else stringResource(Res.string.settings_gestures_off),
                        checked = preferences.videoGesturesEnabled,
                        highlighted = highlightSettingId == "gestures",
                        onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoGesturesEnabled(it) } },
                    )
                    val gestureIndicatorTitle = stringResource(Res.string.settings_gesture_indicator_side)
                    SettingListItem(
                        icon = Tabler.Outline.ArrowsHorizontal,
                        title = gestureIndicatorTitle,
                        subtitle = if (preferences.videoGestureIndicatorSide == GestureIndicatorSide.OPPOSITE)
                            stringResource(Res.string.settings_gesture_indicator_opposite) else stringResource(Res.string.settings_gesture_indicator_same),
                        trailingText = preferences.videoGestureIndicatorSide.displayName,
                        highlighted = highlightSettingId == "gesture_indicator_side",
                        onClick = {
                            activePicker = PickerState.List(
                                title = gestureIndicatorTitle,
                                items = GestureIndicatorSide.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.videoGestureIndicatorSide },
                                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoGestureIndicatorSide(it) } },
                            )
                        },
                    )
                    val defaultSpeedTitle = stringResource(Res.string.settings_default_speed)
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(Res.string.settings_default_speed),
                        subtitle = stringResource(Res.string.settings_default_speed_subtitle),
                        trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        highlighted = highlightSettingId == "default_speed",
                        onClick = {
                            val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                            activePicker = pickerChip(
                                title = defaultSpeedTitle,
                                values = speeds,
                                current = preferences.videoDefaultSpeed,
                                label = { if (it == 1.0f) "1x" else "${it}x" },
                                onSelect = { speed -> viewModel.edit { it.videoPlayer.setVideoDefaultSpeed(speed) } },
                            )
                        },
                    )
                    val holdSpeedTitle = stringResource(Res.string.settings_hold_to_seek_speed)
                    val holdSpeedOffLabel = stringResource(Res.string.settings_off)
                    val holdSpeedOffSubtitle = stringResource(Res.string.settings_disabled)
                    SettingListItem(
                        icon = Tabler.Outline.Rocket,
                        title = holdSpeedTitle,
                        subtitle = stringResource(Res.string.settings_hold_to_seek_speed_subtitle),
                        trailingText = if (preferences.videoHoldSpeedEnabled) "${preferences.videoHoldSpeedMultiplier}x" else holdSpeedOffLabel,
                        highlighted = highlightSettingId == "hold_speed_multiplier",
                        onClick = {
                            // 0f encodes "Off" — selecting it replaces the former Hold-to-Seek toggle
                            val speeds = listOf(0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                            activePicker = PickerState.List(
                                title = holdSpeedTitle,
                                items = speeds,
                                label = { if (it == 0f) holdSpeedOffLabel else "${it}x" },
                                subtitle = { if (it == 0f) holdSpeedOffSubtitle else "" },
                                isSelected = { speed ->
                                    if (preferences.videoHoldSpeedEnabled) speed == preferences.videoHoldSpeedMultiplier else speed == 0f
                                },
                                onSelect = { speed ->
                                    if (speed == 0f) {
                                        viewModel.edit { it.videoPlayer.setVideoHoldSpeedEnabled(false) }
                                    } else {
                                        viewModel.edit { it.videoPlayer.setVideoHoldSpeedEnabled(true) }
                                        viewModel.edit { it.videoPlayer.setVideoHoldSpeedMultiplier(speed) }
                                    }
                                },
                            )
                        },
                    )
                    val defaultAspectTitle = stringResource(Res.string.settings_default_aspect)
                    SettingListItem(
                        icon = Tabler.Outline.ArrowAutofitHeight,
                        title = stringResource(Res.string.settings_default_aspect),
                        subtitle = stringResource(Res.string.settings_default_aspect_subtitle),
                        trailingText = preferences.videoDefaultAspectRatio,
                        highlighted = highlightSettingId == "default_aspect",
                        onClick = {
                            val aspectRatios = listOf("AUTO", "FIT", "FILL", "CROP", "16:9", "4:3", "21:9")
                            activePicker = PickerState.List(
                                title = defaultAspectTitle,
                                items = aspectRatios,
                                label = { it },
                                isSelected = { it == preferences.videoDefaultAspectRatio },
                                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoDefaultAspectRatio(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = stringResource(Res.string.settings_auto_play_next),
                        subtitle = if (preferences.videoAutoplayNext) stringResource(Res.string.settings_auto_play_next_on) else stringResource(Res.string.settings_auto_play_next_off),
                        checked = preferences.videoAutoplayNext,
                        highlighted = highlightSettingId == "video_autoplay_next",
                        onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoAutoplayNext(it) } },
                    )
                    val offLabel = stringResource(Res.string.settings_off)
                    val countdownLabel = if (preferences.autoPlayCountdownSec == 0) offLabel else "${preferences.autoPlayCountdownSec}s"
                    val countdownImmediate = stringResource(Res.string.settings_countdown_immediate)
                    val autoPlayCountdownTitle = stringResource(Res.string.settings_auto_play_countdown)
                    val countdownSecondsFormat = stringResource(Res.string.settings_countdown_seconds)
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = stringResource(Res.string.settings_auto_play_countdown),
                        subtitle = stringResource(Res.string.settings_auto_play_countdown_subtitle),
                        trailingText = countdownLabel,
                        highlighted = highlightSettingId == "autoplay_countdown",
                        onClick = {
                            val options = listOf(0, 5, 10, 15)
                            activePicker = PickerState.List(
                                title = autoPlayCountdownTitle,
                                items = options,
                                label = { if (it == 0) offLabel else "${it}s" },
                                subtitle = { if (it == 0) countdownImmediate else countdownSecondsFormat.format(it) },
                                isSelected = { it == preferences.autoPlayCountdownSec },
                                onSelect = { viewModel.edit { scope -> scope.playback.setAutoPlayCountdownSec(it) } },
                            )
                        },
                    )
                    if (showAdvanced) {
                        val controlsTimeoutTitle = stringResource(Res.string.settings_controls_timeout)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(Res.string.settings_controls_timeout),
                            subtitle = stringResource(Res.string.settings_controls_timeout_subtitle),
                            trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                            highlighted = highlightSettingId == "controls_timeout",
                            onClick = {
                                val timeouts = listOf(3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
                                activePicker = pickerChip(
                                    title = controlsTimeoutTitle,
                                    values = timeouts,
                                    current = preferences.videoControlsTimeoutMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoControlsTimeoutMs(ms) } },
                                )
                            },
                        )
                        val skipBackLabel = if (preferences.videoSkipBackOnResumeMs == 0L) offLabel else "${preferences.videoSkipBackOnResumeMs / 1000}s"
                        val skipBackOnResumeTitle = stringResource(Res.string.settings_skip_back_on_resume)
                        SettingListItem(
                            icon = Tabler.Outline.History,
                            title = stringResource(Res.string.settings_skip_back_on_resume),
                            subtitle = stringResource(Res.string.settings_skip_back_on_resume_subtitle),
                            trailingText = skipBackLabel,
                            highlighted = highlightSettingId == "skip_back_on_resume",
                            onClick = {
                                val durations = listOf(0L, 3_000L, 5_000L, 10_000L, 15_000L, 30_000L)
                                activePicker = pickerChip(
                                    title = skipBackOnResumeTitle,
                                    values = durations,
                                    current = preferences.videoSkipBackOnResumeMs,
                                    label = { if (it == 0L) offLabel else "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoSkipBackOnResumeMs(ms) } },
                                )
                            },
                        )
                        val passOutLabel = if (preferences.videoPassOutProtectionHours == 0) offLabel else "${preferences.videoPassOutProtectionHours}h"
                        val passOutProtectionTitle = stringResource(Res.string.settings_pass_out_protection)
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = stringResource(Res.string.settings_pass_out_protection),
                            subtitle = stringResource(Res.string.settings_pass_out_protection_subtitle),
                            trailingText = passOutLabel,
                            highlighted = highlightSettingId == "pass_out_protection",
                            onClick = {
                                val hours = listOf(0, 1, 2, 3, 4, 6, 8)
                                activePicker = pickerChip(
                                    title = passOutProtectionTitle,
                                    values = hours,
                                    current = preferences.videoPassOutProtectionHours,
                                    label = { if (it == 0) offLabel else "${it}h" },
                                    onSelect = { hours -> viewModel.edit { it.videoPlayer.setVideoPassOutProtectionHours(hours) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Clipboard,
                            title = stringResource(Res.string.settings_autoplay_trailers),
                            subtitle = if (preferences.trailerAutoplay) stringResource(Res.string.settings_autoplay_trailers_on) else stringResource(Res.string.settings_autoplay_trailers_off),
                            checked = preferences.trailerAutoplay,
                            highlighted = highlightSettingId == "autoplay_trailers",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrailerAutoplay(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Video,
                            title = stringResource(Res.string.settings_cinema_mode),
                            subtitle = if (preferences.cinemaModeEnabled) stringResource(Res.string.settings_cinema_mode_on) else stringResource(Res.string.settings_cinema_mode_off),
                            checked = preferences.cinemaModeEnabled,
                            highlighted = highlightSettingId == "cinema_mode",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setCinemaModeEnabled(it) } },
                        )
                        if (isTv) {
                            SettingToggleItem(
                                icon = Tabler.Outline.DeviceTv,
                                title = stringResource(Res.string.settings_watch_next_row),
                                subtitle = if (preferences.androidTvWatchNextEnabled) stringResource(Res.string.settings_watch_next_row_on) else stringResource(Res.string.settings_watch_next_row_off),
                                checked = preferences.androidTvWatchNextEnabled,
                                highlighted = highlightSettingId == "android_tv_watch_next",
                                onCheckedChange = { viewModel.setAndroidTvWatchNextEnabled(it) },
                            )
                            val tvZoomTitle = stringResource(Res.string.settings_tv_zoom_mode)
                            val tvZoomNoneLabel = stringResource(Res.string.settings_tv_zoom_none)
                            val tvZoomPercentLabels = listOf(0f, 5f, 10f, 15f, 20f, 25f, 33f, 50f).associateWith {
                                if (it == 0f) tvZoomNoneLabel else stringResource(Res.string.settings_tv_zoom_percent, it.toInt())
                            }
                            SettingListItem(
                                icon = Tabler.Outline.Crop,
                                title = tvZoomTitle,
                                subtitle = stringResource(Res.string.settings_tv_zoom_mode_subtitle),
                                trailingText = if (preferences.tvZoomModePercent == 0f) offLabel else "${preferences.tvZoomModePercent.toInt()}%",
                                highlighted = highlightSettingId == "tv_zoom_mode",
                                onClick = {
                                    val percents = listOf(0f, 5f, 10f, 15f, 20f, 25f, 33f, 50f)
                                    activePicker = PickerState.List(
                                        title = tvZoomTitle,
                                        items = percents,
                                        label = { if (it == 0f) offLabel else "${it.toInt()}%" },
                                        subtitle = { percent -> tvZoomPercentLabels[percent] ?: "" },
                                        isSelected = { it == preferences.tvZoomModePercent },
                                        onSelect = { viewModel.edit { scope -> scope.videoPlayer.setTvZoomModePercent(it) } },
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.List,
                            title = stringResource(Res.string.settings_episode_browser),
                            subtitle = if (preferences.videoEpisodeBrowserEnabled) stringResource(Res.string.settings_episode_browser_on) else stringResource(Res.string.settings_episode_browser_off),
                            checked = preferences.videoEpisodeBrowserEnabled,
                            highlighted = highlightSettingId == "episode_browser",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoEpisodeBrowserEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.InfoCircle,
                            title = stringResource(Res.string.settings_playback_metadata),
                            subtitle = if (preferences.videoShowPlaybackMetadata) stringResource(Res.string.settings_playback_metadata_on) else stringResource(Res.string.settings_playback_metadata_off),
                            checked = preferences.videoShowPlaybackMetadata,
                            highlighted = highlightSettingId == "playback_metadata",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoShowPlaybackMetadata(it) } },
                        )
                        val swipeSeekRangeTitle = stringResource(Res.string.settings_swipe_seek_range)
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarRight,
                            title = stringResource(Res.string.settings_swipe_seek_range),
                            subtitle = stringResource(Res.string.settings_swipe_seek_range_subtitle),
                            trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                            highlighted = highlightSettingId == "swipe_seek_range",
                            onClick = {
                                val ranges = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L, 300_000L)
                                activePicker = pickerChip(
                                    title = swipeSeekRangeTitle,
                                    values = ranges,
                                    current = preferences.videoSwipeSeekMaxMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoSwipeSeekMaxMs(ms) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.BrightnessHalf,
                            title = stringResource(Res.string.settings_remember_brightness),
                            subtitle = if (preferences.videoRememberBrightness) stringResource(Res.string.settings_remember_brightness_on) else stringResource(Res.string.settings_remember_brightness_off),
                            checked = preferences.videoRememberBrightness,
                            highlighted = highlightSettingId == "remember_brightness",
                            onCheckedChange = { enabled ->
                                viewModel.edit { it.videoPlayer.setVideoRememberBrightness(enabled) }
                            },
                        )
                        val defaultBrightnessTitle = stringResource(Res.string.settings_default_brightness_level)
                        SettingListItem(
                            icon = Tabler.Outline.Sun,
                            title = stringResource(Res.string.settings_default_brightness_level),
                            subtitle = stringResource(Res.string.settings_default_brightness_level_subtitle),
                            trailingText = "${(preferences.videoBrightnessLevel * 100).toInt()}%",
                            highlighted = highlightSettingId == "default_brightness_level",
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = defaultBrightnessTitle,
                                    value = preferences.videoBrightnessLevel,
                                    valueRange = 0.0f..1.0f,
                                    steps = 20,
                                    valueLabel = { "${(it * 100).toInt()}%" },
                                    rangeStartLabel = "0%",
                                    rangeEndLabel = "100%",
                                    onConfirm = { viewModel.edit { scope -> scope.videoPlayer.setVideoBrightnessLevel(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Photo,
                            title = stringResource(Res.string.settings_trickplay_preview),
                            subtitle = if (preferences.trickplayEnabled) stringResource(Res.string.settings_trickplay_preview_on) else stringResource(Res.string.settings_trickplay_preview_off),
                            checked = preferences.trickplayEnabled,
                            highlighted = highlightSettingId == "trickplay_preview",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrickplayEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.HandMove,
                            title = stringResource(Res.string.settings_trickplay_on_gestures),
                            subtitle = if (preferences.trickplayOnSeekGesture) stringResource(Res.string.settings_trickplay_on_gestures_on) else stringResource(Res.string.settings_trickplay_on_gestures_off),
                            checked = preferences.trickplayOnSeekGesture,
                            highlighted = highlightSettingId == "trickplay_on_gestures",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrickplayOnSeekGesture(it) } },
                        )
                        val preloadBufferTitle = stringResource(Res.string.settings_preload_buffer)
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = stringResource(Res.string.settings_preload_buffer),
                            subtitle = stringResource(Res.string.settings_preload_buffer_subtitle),
                            trailingText = preferences.videoPreloadBufferSize.displayName,
                            highlighted = highlightSettingId == "preload_buffer",
                            onClick = {
                                activePicker = PickerState.List(
                                    title = preloadBufferTitle,
                                    items = PreloadBufferSize.entries,
                                    label = { it.displayName },
                                    subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
                                    isSelected = { it == preferences.videoPreloadBufferSize },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoPreloadBufferSize(it) } },
                                )
                            },
                        )

                        val videoCacheSizeTitle = stringResource(Res.string.settings_video_cache_size)
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = videoCacheSizeTitle,
                            subtitle = stringResource(Res.string.settings_video_cache_size_subtitle),
                            trailingText = "${preferences.videoCacheSizeMb} MB",
                            highlighted = highlightSettingId == "video_cache_size",
                            onClick = {
                                activePicker = PickerState.List(
                                    title = videoCacheSizeTitle,
                                    items = listOf(128, 256, 512, 1024, 2048, 4096),
                                    label = { "$it MB" },
                                    isSelected = { it == preferences.videoCacheSizeMb },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoCacheSizeMb(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Music,
                            title = stringResource(Res.string.settings_background_audio),
                            subtitle = stringResource(Res.string.settings_background_audio_subtitle),
                            checked = preferences.backgroundVideoAudioEnabled,
                            highlighted = highlightSettingId == "background_audio",
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setBackgroundVideoAudioEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Eye,
                            title = stringResource(Res.string.settings_keep_screen_on),
                            subtitle = stringResource(Res.string.settings_keep_screen_on_subtitle),
                            checked = preferences.keepScreenOnDuringVideo,
                            highlighted = highlightSettingId == "keep_screen_on",
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setKeepScreenOnDuringVideo(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Ghost,
                            title = stringResource(Res.string.settings_incognito_mode),
                            subtitle = if (preferences.incognitoModeEnabled) stringResource(Res.string.settings_incognito_mode_on) else stringResource(Res.string.settings_incognito_mode_off),
                            checked = preferences.incognitoModeEnabled,
                            highlighted = highlightSettingId == "incognito_mode",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setIncognitoModeEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(Res.string.settings_show_time_remaining),
                            subtitle = if (preferences.showTimeRemaining) stringResource(Res.string.settings_show_time_remaining_on) else stringResource(Res.string.settings_show_time_remaining_off),
                            checked = preferences.showTimeRemaining,
                            highlighted = highlightSettingId == "show_time_remaining",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setShowTimeRemaining(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(Res.string.settings_show_clock_player),
                            subtitle = if (preferences.showClockInPlayer) stringResource(Res.string.settings_show_clock_player_on) else stringResource(Res.string.settings_show_clock_player_off),
                            checked = preferences.showClockInPlayer,
                            highlighted = highlightSettingId == "show_clock_player",
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setShowClockInPlayer(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PlayerPause,
                            title = stringResource(Res.string.settings_pause_on_focus_loss),
                            subtitle = if (preferences.pauseOnAudioFocusLoss) stringResource(Res.string.settings_pause_on_focus_loss_on) else stringResource(Res.string.settings_pause_on_focus_loss_off),
                            checked = preferences.pauseOnAudioFocusLoss,
                            highlighted = highlightSettingId == "pause_on_focus_loss",
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setPauseOnAudioFocusLoss(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Phone,
                            title = stringResource(Res.string.settings_duck_on_phone_call),
                            subtitle = if (preferences.duckOnTransientFocusLoss) stringResource(Res.string.settings_duck_on_phone_call_on) else stringResource(Res.string.settings_duck_on_phone_call_off),
                            checked = preferences.duckOnTransientFocusLoss,
                            highlighted = highlightSettingId == "duck_on_transient_focus_loss",
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setDuckOnTransientFocusLoss(it) } },
                        )
                    }
                    }
                }
            }

            if (showAdvanced) {
            item {
                val offLabel2 = stringResource(Res.string.settings_off)
                val resSwitchSuffix = stringResource(Res.string.settings_resolution_switching)
                SettingsGroup(
                    icon = Tabler.Outline.BadgeHd,
                    title = stringResource(Res.string.settings_advanced_video),
                    summary = { stringResource(Res.string.settings_decoder_summary, preferences.decoderMode.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_ADVANCED_GROUP_IDS,
                ) {
                    SettingsItemList(total = 6 + (if (preferences.dialogueBoostEnabled) 1 else 0)) {
                    SettingToggleItem(
                        icon = Tabler.Outline.Microphone2,
                        title = stringResource(Res.string.settings_dialogue_boost),
                        subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else offLabel2,
                        checked = preferences.dialogueBoostEnabled,
                        highlighted = highlightSettingId == "dialogue_boost",
                        onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setDialogueBoostEnabled(it) } },
                    )
                    if (preferences.dialogueBoostEnabled) {
                        val dialogueBoostStrengthTitle = stringResource(Res.string.settings_dialogue_boost_strength)
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = stringResource(Res.string.settings_dialogue_boost_strength),
                            subtitle = preferences.dialogueBoostStrength.displayName,
                            trailingText = preferences.dialogueBoostStrength.displayName,
                            onClick = {
                                val strengths = EffectStrength.entries
                                activePicker = pickerChip(
                                    title = dialogueBoostStrengthTitle,
                                    values = strengths,
                                    current = preferences.dialogueBoostStrength,
                                    label = { it.displayName },
                                    onSelect = { strength -> viewModel.edit { it.audioEffects.setDialogueBoostStrength(strength) } },
                                )
                            },
                        )
                    }
                    val decoderTitle = stringResource(Res.string.settings_decoder)
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = decoderTitle,
                        subtitle = preferences.decoderMode.displayName,
                        trailingText = preferences.decoderMode.displayName.split(" ").first(),
                        highlighted = highlightSettingId == "decoder",
                        onClick = {
                            activePicker = PickerState.List(
                                title = decoderTitle,
                                items = DecoderMode.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.decoderMode },
                                onSelect = { viewModel.edit { scope -> scope.playback.setDecoderMode(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Movie,
                        title = stringResource(Res.string.settings_audio_passthrough),
                        subtitle = if (preferences.audioPassthrough) stringResource(Res.string.settings_audio_passthrough_on) else stringResource(Res.string.settings_audio_passthrough_off),
                        checked = preferences.audioPassthrough,
                        highlighted = highlightSettingId == "audio_passthrough",
                        onCheckedChange = { viewModel.edit { scope -> scope.playback.setAudioPassthrough(it) } },
                    )
                    val refreshRateSubtitles = com.raulshma.jellyplay.core.model.RefreshRateMode.entries.associateWith {
                        when (it) {
                            com.raulshma.jellyplay.core.model.RefreshRateMode.OFF -> stringResource(Res.string.settings_refresh_rate_off)
                            com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_ONLY -> stringResource(Res.string.settings_refresh_rate_rate)
                            com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION -> stringResource(Res.string.settings_refresh_rate_rate_res)
                        }
                    }
                    val refreshRateDescs = com.raulshma.jellyplay.core.model.RefreshRateMode.entries.associateWith {
                        when (it) {
                            com.raulshma.jellyplay.core.model.RefreshRateMode.OFF -> stringResource(Res.string.settings_refresh_rate_desc_off)
                            com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_ONLY -> stringResource(Res.string.settings_refresh_rate_desc_rate)
                            com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION -> stringResource(Res.string.settings_refresh_rate_desc_res)
                        }
                    }
                    val refreshRateMatchingTitle = stringResource(Res.string.settings_refresh_rate_matching)
                    SettingListItem(
                        icon = Tabler.Outline.Maximize,
                        title = stringResource(Res.string.settings_refresh_rate_match),
                        subtitle = preferences.refreshRateMode.displayName +
                            if (preferences.refreshRateMode == com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
                                " $resSwitchSuffix" else "",
                        trailingText = refreshRateSubtitles[preferences.refreshRateMode] ?: preferences.refreshRateMode.displayName,
                        highlighted = highlightSettingId == "frame_rate_matching",
                        onClick = {
                            activePicker = PickerState.List(
                                title = refreshRateMatchingTitle,
                                items = com.raulshma.jellyplay.core.model.RefreshRateMode.entries,
                                label = { it.displayName },
                                subtitle = { refreshRateDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.refreshRateMode },
                                onSelect = { viewModel.edit { scope -> scope.playback.setRefreshRateMode(it) } },
                            )
                        },
                    )
                    val qualityLabels = StreamingQuality.entries.associateWith { stringResource(streamingQualityLabelRes(it)) }
                    val qualityShorts = StreamingQuality.entries.associateWith { stringResource(streamingQualityShortRes(it)) }
                    val streamingQualityTitle = stringResource(Res.string.settings_streaming_quality)
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = stringResource(Res.string.settings_streaming_quality),
                        subtitle = qualityLabels[preferences.streamingQuality] ?: preferences.streamingQuality.name,
                        trailingText = qualityShorts[preferences.streamingQuality] ?: preferences.streamingQuality.name,
                        highlighted = highlightSettingId == "streaming_quality",
                        onClick = {
                            activePicker = PickerState.List(
                                title = streamingQualityTitle,
                                items = StreamingQuality.entries,
                                label = { qualityLabels[it] ?: it.name },
                                isSelected = { it == preferences.streamingQuality },
                                onSelect = { viewModel.edit { scope -> scope.playback.setStreamingQuality(it) } },
                            )
                        },
                    )
                    val liveLabels = LiveStreamOption.entries.associateWith { stringResource(liveStreamOptionLabelRes(it)) }
                    val liveTvStreamTitle = stringResource(Res.string.settings_live_tv_stream)
                    SettingListItem(
                        icon = Tabler.Outline.DeviceTv,
                        title = stringResource(Res.string.settings_live_tv_stream),
                        subtitle = liveLabels[preferences.liveStreamOption] ?: preferences.liveStreamOption.displayName,
                        trailingText = preferences.liveStreamOption.displayName,
                        highlighted = highlightSettingId == "live_stream_option",
                        onClick = {
                            activePicker = PickerState.List(
                                title = liveTvStreamTitle,
                                items = LiveStreamOption.entries,
                                label = { liveLabels[it] ?: it.displayName },
                                isSelected = { it == preferences.liveStreamOption },
                                onSelect = { viewModel.edit { scope -> scope.playback.setLiveStreamOption(it) } },
                            )
                        },
                    )
                    val noAudioDelay = stringResource(Res.string.settings_no_audio_delay)
                    val noDelayLabel = stringResource(Res.string.settings_no_delay)
                    val audioDelayTitle = stringResource(Res.string.settings_audio_delay)
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = stringResource(Res.string.settings_audio_delay),
                        subtitle = if (preferences.audioDelayMs == 0L) noAudioDelay else stringResource(Res.string.settings_audio_delay_value, preferences.audioDelayMs),
                        trailingText = if (preferences.audioDelayMs == 0L) offLabel2 else "${preferences.audioDelayMs}ms",
                        highlighted = highlightSettingId == "audio_delay",
                        onClick = {
                            activePicker = PickerState.Slider(
                                title = audioDelayTitle,
                                value = preferences.audioDelayMs.toFloat(),
                                valueRange = -500f..500f,
                                steps = 99,
                                valueLabel = { if (it.toLong() == 0L) noDelayLabel else "${it.toLong()}ms" },
                                rangeStartLabel = "-500ms",
                                rangeEndLabel = "+500ms",
                                onConfirm = { viewModel.edit { scope -> scope.audio.setAudioDelay(it.toLong()) } },
                            )
                        },
                    )
                    }
                }
            }

            item {
                val offLabel2 = stringResource(Res.string.settings_off)
                SettingsGroup(
                    icon = Tabler.Outline.Settings,
                    title = stringResource(Res.string.settings_engine_config),
                    summary = { preferences.preferredPlayer.displayName },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_ENGINE_GROUP_IDS,
                ) {
                    when (preferences.preferredPlayer) {
                        PlayerType.MPV -> {
                            val mpvCfg = preferences.mpvConfig
                            val mpvDefault = MpvEngineConfig()
                            SettingsItemList(total = 12) {
                            val videoOutputTitle = stringResource(Res.string.settings_video_output)
                            SettingListItem(
                                icon = Tabler.Outline.Video,
                                title = stringResource(Res.string.settings_video_output),
                                subtitle = "${mpvCfg.videoOutput.displayName} (${mpvCfg.videoOutput.key})",
                                trailingText = mpvCfg.videoOutput.key,
                                highlighted = highlightSettingId == "mpv_video_output",
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = videoOutputTitle,
                                        items = MpvVideoOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.videoOutput },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(videoOutput = it)) } },
                                    )
                                },
                            )
                            val scalerTitle = stringResource(Res.string.settings_scaler)
                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = stringResource(Res.string.settings_scaler),
                                subtitle = "${mpvCfg.scaler.displayName} (${mpvCfg.scaler.key})",
                                trailingText = mpvCfg.scaler.key,
                                highlighted = highlightSettingId == "mpv_scaler",
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = scalerTitle,
                                        items = MpvScaler.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.scaler },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(scaler = it)) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ColorFilter,
                                title = stringResource(Res.string.settings_debanding),
                                subtitle = if (mpvCfg.deband) stringResource(Res.string.settings_debanding_on) else stringResource(Res.string.settings_debanding_off),
                                checked = mpvCfg.deband,
                                highlighted = highlightSettingId == "mpv_debanding",
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(deband = it)) } },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ArrowsHorizontal,
                                title = stringResource(Res.string.settings_interpolation),
                                subtitle = if (mpvCfg.interpolation) stringResource(Res.string.settings_interpolation_on) else stringResource(Res.string.settings_interpolation_off),
                                checked = mpvCfg.interpolation,
                                highlighted = highlightSettingId == "mpv_interpolation",
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(interpolation = it)) } },
                            )
                            val audioOutputTitle = stringResource(Res.string.settings_audio_output)
                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = stringResource(Res.string.settings_audio_output),
                                subtitle = "${mpvCfg.audioOutput.displayName} (${mpvCfg.audioOutput.key})",
                                trailingText = mpvCfg.audioOutput.key,
                                highlighted = highlightSettingId == "mpv_audio_output",
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = audioOutputTitle,
                                        items = MpvAudioOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.audioOutput },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(audioOutput = it)) } },
                                    )
                                },
                            )
                            val noneLabel = stringResource(Res.string.settings_none)
                            val audioFallbackTitle = stringResource(Res.string.settings_audio_fallback)
                            val noFallback = stringResource(Res.string.settings_no_fallback)
                            SettingListItem(
                                icon = Tabler.Outline.ArrowBack,
                                title = stringResource(Res.string.settings_audio_fallback),
                                subtitle = mpvCfg.audioFallback?.displayName ?: noneLabel,
                                trailingText = mpvCfg.audioFallback?.key ?: noneLabel,
                                highlighted = highlightSettingId == "mpv_audio_fallback",
                                onClick = {
                                    val options = listOf(null) + MpvAudioOutput.entries
                                    activePicker = PickerState.List(
                                        title = audioFallbackTitle,
                                        items = options,
                                        label = { it?.displayName ?: noneLabel },
                                        subtitle = { it?.key ?: noFallback },
                                        isSelected = { it == mpvCfg.audioFallback },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(audioFallback = it)) } },
                                    )
                                },
                            )
                            val bufferSizeTitle = stringResource(Res.string.settings_buffer_size)
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = stringResource(Res.string.settings_buffer_size),
                                subtitle = "${mpvCfg.demuxerMaxBytes.displayName} (${mpvCfg.demuxerMaxBytes.key})",
                                trailingText = mpvCfg.demuxerMaxBytes.key,
                                highlighted = highlightSettingId == "mpv_buffer_size",
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = bufferSizeTitle,
                                        items = MpvDemuxerMaxBytes.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.demuxerMaxBytes },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(demuxerMaxBytes = it)) } },
                                    )
                                },
                            )
                            val hwdecUniversal = stringResource(Res.string.settings_hwdec_universal)
                            val hwdecOverrideTitle = stringResource(Res.string.settings_hwdec_override)
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = stringResource(Res.string.settings_hwdec_override),
                                subtitle = mpvCfg.hwdecOverride?.displayName ?: hwdecUniversal,
                                trailingText = mpvCfg.hwdecOverride?.key ?: stringResource(Res.string.settings_streaming_quality_auto),
                                highlighted = highlightSettingId == "mpv_hwdec_override",
                                onClick = {
                                    val options = listOf(null) + MpvHwdec.entries
                                    activePicker = PickerState.List(
                                        title = hwdecOverrideTitle,
                                        items = options,
                                        label = { it?.displayName ?: hwdecUniversal },
                                        subtitle = { it?.key ?: "auto" },
                                        isSelected = { it == mpvCfg.hwdecOverride },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(hwdecOverride = it)) } },
                                    )
                                },
                            )
                            val skipLoopFilterTitle = stringResource(Res.string.settings_skip_loop_filter)
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = stringResource(Res.string.settings_skip_loop_filter),
                                subtitle = "${mpvCfg.skipLoopFilter.displayName} (${mpvCfg.skipLoopFilter.key})",
                                trailingText = mpvCfg.skipLoopFilter.key,
                                highlighted = highlightSettingId == "mpv_skip_loop_filter",
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = skipLoopFilterTitle,
                                        items = MpvSkipLoopFilter.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.skipLoopFilter },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(skipLoopFilter = it)) } },
                                    )
                                },
                            )
                            val frameDropTitle = stringResource(Res.string.settings_frame_drop)
                            SettingListItem(
                                icon = Tabler.Outline.PhotoDown,
                                title = stringResource(Res.string.settings_frame_drop),
                                subtitle = "${mpvCfg.frameDrop.displayName} (${mpvCfg.frameDrop.key})",
                                trailingText = mpvCfg.frameDrop.key,
                                highlighted = highlightSettingId == "mpv_frame_drop",
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = frameDropTitle,
                                        items = MpvFrameDrop.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.frameDrop },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(frameDrop = it)) } },
                                    )
                                },
                            )
                            val customOptionsSuffix = stringResource(Res.string.settings_custom_options)
                            val advancedMpvConfigTitle = stringResource(Res.string.settings_advanced_mpv_config)
                            val mpvHelperText = stringResource(Res.string.settings_mpv_helper_text)
                            SettingListItem(
                                icon = Tabler.Outline.Code,
                                title = stringResource(Res.string.settings_advanced_config),
                                subtitle = if (mpvCfg.mpvExtraConfig.isBlank()) {
                                    stringResource(Res.string.settings_raw_mpv_options)
                                } else {
                                    "${parseMpvConfigOptions(mpvCfg.mpvExtraConfig).size} $customOptionsSuffix"
                                },
                                trailingText = "mpv.conf",
                                highlighted = highlightSettingId == "mpv_extra_config",
                                onClick = {
                                    activePicker = PickerState.Text(
                                        title = advancedMpvConfigTitle,
                                        initialText = mpvCfg.mpvExtraConfig,
                                        helperText = mpvHelperText,
                                        onSave = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(mpvExtraConfig = it)) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = stringResource(Res.string.settings_reset_to_defaults),
                                subtitle = stringResource(Res.string.settings_reset_mpv),
                                onClick = { viewModel.edit { it.engine.setMpvConfig(mpvDefault) } },
                            )
                            }
                        }
                        PlayerType.LIBVLC -> {
                            val vlcCfg = preferences.libVlcConfig
                            val vlcDefault = LibVlcEngineConfig()
                            val vlcTotal = 8
                            var vlcIdx = 0

                            val networkCachingTitle = stringResource(Res.string.settings_network_caching)
                            val networkCachingAuto = stringResource(Res.string.settings_auto_device_based)
                            val skipLoopFilterTitle = stringResource(Res.string.settings_skip_loop_filter)
                            val skipFramesTitle = stringResource(Res.string.settings_skip_frames)
                            val decoderThreadsTitle = stringResource(Res.string.settings_decoder_threads)
                            val skipLoopFilterLabels = (0..4).associateWith { stringResource(vlcSkipLoopFilterLabelRes(it)) }
                            val skipFrameLabels = (0..4).associateWith { stringResource(vlcSkipFrameLabelRes(it)) }
                            val vlcAudioOutputTitle = stringResource(Res.string.settings_audio_output)
                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = stringResource(Res.string.settings_audio_output),
                                subtitle = "${vlcCfg.audioOutput.displayName} (${vlcCfg.audioOutput.key})",
                                trailingText = vlcCfg.audioOutput.key,
                                highlighted = highlightSettingId == "vlc_audio_output",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = vlcAudioOutputTitle,
                                        items = VlcAudioOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == vlcCfg.audioOutput },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(audioOutput = it)) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Clock,
                                title = stringResource(Res.string.settings_audio_time_stretch),
                                subtitle = if (vlcCfg.audioTimeStretch) stringResource(Res.string.settings_audio_time_stretch_on) else stringResource(Res.string.settings_audio_time_stretch_off),
                                checked = vlcCfg.audioTimeStretch,
                                highlighted = highlightSettingId == "vlc_audio_time_stretch",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(audioTimeStretch = it)) } },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Wifi,
                                title = networkCachingTitle,
                                subtitle = if (vlcCfg.networkCaching == 0) networkCachingAuto else "${vlcCfg.networkCaching}ms",
                                trailingText = if (vlcCfg.networkCaching == 0) stringResource(Res.string.settings_streaming_quality_auto) else "${vlcCfg.networkCaching}ms",
                                highlighted = highlightSettingId == "vlc_network_caching",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = {
                                    val options = listOf(0, 500, 1000, 1500, 2000, 3000, 5000)
                                    activePicker = PickerState.List(
                                        title = networkCachingTitle,
                                        items = options,
                                        label = { if (it == 0) networkCachingAuto else "${it}ms" },
                                        subtitle = { if (it == 0) "auto" else "${it}ms" },
                                        isSelected = { it == vlcCfg.networkCaching },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(networkCaching = it)) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = skipLoopFilterTitle,
                                subtitle = skipLoopFilterLabels[vlcCfg.skipLoopFilter] ?: vlcCfg.skipLoopFilter.toString(),
                                trailingText = stringResource(Res.string.settings_b_frames_level, vlcCfg.skipLoopFilter),
                                highlighted = highlightSettingId == "vlc_skip_loop_filter",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = {
                                    val options = (0..4).toList()
                                    activePicker = PickerState.List(
                                        title = skipLoopFilterTitle,
                                        items = options,
                                        label = { skipLoopFilterLabels[it] ?: it.toString() },
                                        subtitle = { "level $it" },
                                        isSelected = { it == vlcCfg.skipLoopFilter },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(skipLoopFilter = it)) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = skipFramesTitle,
                                subtitle = if (vlcCfg.skipFrames) stringResource(Res.string.settings_skip_frames_on) else stringResource(Res.string.settings_skip_frames_off),
                                checked = vlcCfg.skipFrames,
                                highlighted = highlightSettingId == "vlc_skip_frames",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(skipFrames = it)) } },
                                onClick = {
                                    val options = (0..4).toList()
                                    activePicker = PickerState.List(
                                        title = skipFramesTitle,
                                        items = options,
                                        label = { skipFrameLabels[it] ?: it.toString() },
                                        subtitle = { "level $it" },
                                        isSelected = { it == vlcCfg.skipFrame },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(skipFrame = it)) } },
                                    )
                                },
                            )
                            val threadsSuffix = stringResource(Res.string.settings_threads_suffix)
                            val autoLabel = stringResource(Res.string.settings_streaming_quality_auto)
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = decoderThreadsTitle,
                                subtitle = if (vlcCfg.decoderThreads == 0) autoLabel else "${vlcCfg.decoderThreads} $threadsSuffix",
                                trailingText = if (vlcCfg.decoderThreads == 0) autoLabel else "${vlcCfg.decoderThreads}",
                                highlighted = highlightSettingId == "vlc_decoder_threads",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = {
                                    val options = listOf(0, 1, 2, 4, 6, 8)
                                    activePicker = PickerState.List(
                                        title = decoderThreadsTitle,
                                        items = options,
                                        label = { if (it == 0) autoLabel else "$it $threadsSuffix" },
                                        subtitle = { if (it == 0) "auto" else "$it" },
                                        isSelected = { it == vlcCfg.decoderThreads },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(decoderThreads = it)) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Trash,
                                title = stringResource(Res.string.settings_drop_late_frames),
                                subtitle = if (vlcCfg.dropLateFrames) stringResource(Res.string.settings_drop_late_frames_on) else stringResource(Res.string.settings_drop_late_frames_off),
                                checked = vlcCfg.dropLateFrames,
                                highlighted = highlightSettingId == "vlc_drop_late_frames",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(dropLateFrames = it)) } },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = stringResource(Res.string.settings_reset_to_defaults),
                                subtitle = stringResource(Res.string.settings_reset_libvlc),
                                index = vlcIdx, count = vlcTotal,
                                onClick = { viewModel.edit { it.engine.setLibVlcConfig(vlcDefault) } },
                            )
                        }
                        PlayerType.EXO_PLAYER -> {
                            val exoCfg = preferences.exoPlayerConfig
                            val exoDefault = ExoPlayerEngineConfig()
                            val exoTotal = 8
                            var exoIdx = 0

                            val videoScalingTitle = stringResource(Res.string.settings_video_scaling)
                            val frameRateStrategyTitle = stringResource(Res.string.settings_frame_rate_strategy)
                            val audioOffloadTitle = stringResource(Res.string.settings_audio_offload)
                            val backBufferTitle = stringResource(Res.string.settings_back_buffer)
                            val preferredCodecsTitle = stringResource(Res.string.settings_preferred_codecs)
                            val disabledLabel = stringResource(Res.string.settings_disabled)
                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = videoScalingTitle,
                                subtitle = "${exoCfg.videoScalingMode.displayName} (${exoCfg.videoScalingMode.key})",
                                trailingText = exoCfg.videoScalingMode.key,
                                highlighted = highlightSettingId == "exo_video_scaling",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = videoScalingTitle,
                                        items = ExoVideoScalingMode.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == exoCfg.videoScalingMode },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(videoScalingMode = it)) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Clock,
                                title = frameRateStrategyTitle,
                                subtitle = "${exoCfg.frameRateStrategy.displayName} (${exoCfg.frameRateStrategy.key})",
                                trailingText = exoCfg.frameRateStrategy.key,
                                highlighted = highlightSettingId == "exo_frame_rate_strategy",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = frameRateStrategyTitle,
                                        items = ExoFrameRateStrategy.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == exoCfg.frameRateStrategy },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(frameRateStrategy = it)) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Volume,
                                title = stringResource(Res.string.settings_skip_silence),
                                subtitle = if (exoCfg.skipSilence) stringResource(Res.string.settings_skip_silence_on) else stringResource(Res.string.settings_skip_silence_off),
                                checked = exoCfg.skipSilence,
                                highlighted = highlightSettingId == "exo_skip_silence",
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(skipSilence = it)) } },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Headphones,
                                title = audioOffloadTitle,
                                subtitle = "${exoCfg.audioOffloadMode.displayName} (${exoCfg.audioOffloadMode.key})",
                                trailingText = exoCfg.audioOffloadMode.key,
                                highlighted = highlightSettingId == "exo_audio_offload",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = audioOffloadTitle,
                                        items = ExoAudioOffloadMode.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == exoCfg.audioOffloadMode },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(audioOffloadMode = it)) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ToggleLeft,
                                title = stringResource(Res.string.settings_decoder_fallback),
                                subtitle = if (exoCfg.enableDecoderFallback) stringResource(Res.string.settings_decoder_fallback_on) else stringResource(Res.string.settings_decoder_fallback_off),
                                checked = exoCfg.enableDecoderFallback,
                                highlighted = highlightSettingId == "exo_decoder_fallback",
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(enableDecoderFallback = it)) } },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = backBufferTitle,
                                subtitle = if (exoCfg.backBufferDurationMs == 0) disabledLabel else stringResource(Res.string.settings_back_buffer_value, exoCfg.backBufferDurationMs / 1000),
                                trailingText = if (exoCfg.backBufferDurationMs == 0) offLabel2 else "${exoCfg.backBufferDurationMs / 1000}s",
                                highlighted = highlightSettingId == "exo_back_buffer",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    val options = listOf(0, 5000, 10000, 15000, 20000, 30000)
                                    activePicker = PickerState.List(
                                        title = backBufferTitle,
                                        items = options,
                                        label = { if (it == 0) disabledLabel else "${it / 1000}s" },
                                        subtitle = { if (it == 0) "off" else "${it}ms" },
                                        isSelected = { it == exoCfg.backBufferDurationMs },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(backBufferDurationMs = it)) } },
                                    )
                                },
                            )
                            val allCodecs = stringResource(Res.string.settings_all_codecs)
                            val customLabel = stringResource(Res.string.settings_custom)
                            val presetLabels = listOf(
                                allCodecs,
                                stringResource(Res.string.settings_codecs_hevc_avc),
                                stringResource(Res.string.settings_codecs_av1_hevc_avc),
                                stringResource(Res.string.settings_codecs_avc_only),
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Code,
                                title = preferredCodecsTitle,
                                subtitle = if (exoCfg.preferredVideoMimeTypes.isEmpty()) allCodecs else exoCfg.preferredVideoMimeTypes.joinToString(", "),
                                trailingText = if (exoCfg.preferredVideoMimeTypes.isEmpty()) stringResource(Res.string.settings_all) else customLabel,
                                highlighted = highlightSettingId == "exo_preferred_codecs",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    val presets = listOf(
                                        emptyList<String>(),
                                        listOf("video/hevc", "video/avc"),
                                        listOf("video/av1", "video/hevc", "video/avc"),
                                        listOf("video/avc"),
                                    )
                                    activePicker = PickerState.List(
                                        title = preferredCodecsTitle,
                                        items = presets,
                                        label = { presetLabels[presets.indexOf(it)] },
                                        subtitle = { if (it.isEmpty()) "*" else it.joinToString(", ") },
                                        isSelected = { it == exoCfg.preferredVideoMimeTypes },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(preferredVideoMimeTypes = it)) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = stringResource(Res.string.settings_reset_to_defaults),
                                subtitle = stringResource(Res.string.settings_reset_exoplayer),
                                index = exoIdx, count = exoTotal,
                                onClick = { viewModel.edit { it.engine.setExoPlayerConfig(exoDefault) } },
                            )
                        }
                        else -> {}
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerTrackNext,
                    title = stringResource(Res.string.settings_media_segments),
                    summary = {
                        val autoCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.AUTO_SKIP }
                        val buttonCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON }
                        stringResource(Res.string.settings_media_segments_summary, autoCount, buttonCount)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId?.startsWith("media_segment_") == true,
                ) {
                    val segmentTypes = com.raulshma.jellyplay.core.model.MediaSegmentType.entries
                    val totalTypes = segmentTypes.size
                    segmentTypes.forEachIndexed { index, type ->
                        val behavior = preferences.segmentBehaviors[type]
                            ?: com.raulshma.jellyplay.core.model.SegmentBehavior.IGNORE
                        // Resolve localized strings here in composable scope; the
                        // onClick lambda below is not composable so it must use
                        // these pre-resolved values.
                        val typeTitle = type.localizedDisplayName()
                        val allBehaviors = com.raulshma.jellyplay.core.model.SegmentBehavior.entries
                        val behaviorLabels = allBehaviors.associateWith { it.localizedDisplayName() }
                        val behaviorDescriptions = allBehaviors.associateWith { it.localizedDescription() }
                        SettingListItem(
                            icon = Tabler.Outline.PlayerTrackNext,
                            title = typeTitle,
                            subtitle = type.localizedDescription(),
                            trailingText = behavior.localizedDisplayName(),
                            index = index, count = totalTypes,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = typeTitle,
                                    items = allBehaviors,
                                    label = { behaviorLabels.getValue(it) },
                                    subtitle = { behaviorDescriptions.getValue(it) },
                                    isSelected = { it == behavior },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setSegmentBehavior(type, it) } },
                                )
                            },
                        )
                    }
                }
            }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Users,
                    title = stringResource(Res.string.settings_syncplay),
                    summary = { stringResource(Res.string.settings_syncplay_join_summary, preferences.syncPlayJoinBehavior.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_SYNCPLAY_GROUP_IDS,
                ) {
                    val syncTotal = 3
                    var syncIdx = 0
                    val joinBehaviorDescs = SyncPlayJoinBehavior.entries.associateWith {
                        when (it) {
                            SyncPlayJoinBehavior.ALWAYS_JOIN -> stringResource(Res.string.settings_join_always)
                            SyncPlayJoinBehavior.ASK -> stringResource(Res.string.settings_join_ask)
                            SyncPlayJoinBehavior.NEVER_JOIN -> stringResource(Res.string.settings_join_never)
                        }
                    }

                    val joinBehaviorTitle = stringResource(Res.string.settings_join_behavior)
                    SettingListItem(
                        icon = Tabler.Outline.MessageQuestion,
                        title = joinBehaviorTitle,
                        subtitle = stringResource(Res.string.settings_join_behavior_subtitle),
                        trailingText = preferences.syncPlayJoinBehavior.displayName,
                        highlighted = highlightSettingId == "syncplay_join_behavior",
                        index = syncIdx++, count = syncTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = joinBehaviorTitle,
                                items = SyncPlayJoinBehavior.entries,
                                label = { it.displayName },
                                subtitle = { joinBehaviorDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.syncPlayJoinBehavior },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setSyncPlayJoinBehavior(it) } },
                            )
                        },
                    )

                    val syncToleranceTitle = stringResource(Res.string.settings_sync_tolerance)
                    val toleranceDescs = mapOf(
                        50L to stringResource(Res.string.settings_sync_tight),
                        100L to stringResource(Res.string.settings_sync_balanced),
                        500L to stringResource(Res.string.settings_sync_loose),
                    )
                    val syncCustom = stringResource(Res.string.settings_sync_custom)
                    SettingListItem(
                        icon = Tabler.Outline.WaveSine,
                        title = syncToleranceTitle,
                        subtitle = stringResource(Res.string.settings_sync_tolerance_subtitle),
                        trailingText = "${preferences.syncPlayToleranceMs}ms",
                        highlighted = highlightSettingId == "syncplay_tolerance",
                        index = syncIdx++, count = syncTotal,
                        onClick = {
                            val options = listOf(50L, 100L, 200L, 300L, 500L, 1000L)
                            activePicker = PickerState.List(
                                title = syncToleranceTitle,
                                items = options,
                                label = { "${it}ms" },
                                subtitle = { toleranceDescs[it] ?: syncCustom },
                                isSelected = { it == preferences.syncPlayToleranceMs },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setSyncPlayToleranceMs(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.CircleCheck,
                        title = stringResource(Res.string.settings_auto_accept_invites),
                        subtitle = stringResource(Res.string.settings_auto_accept_invites_subtitle),
                        checked = preferences.syncPlayAutoAcceptInvites,
                        highlighted = highlightSettingId == "syncplay_auto_accept_invites",
                        index = syncIdx++, count = syncTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.syncPlayCast.setSyncPlayAutoAcceptInvites(it) } },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.DeviceTv,
                    title = stringResource(Res.string.settings_casting_dlna),
                    summary = { stringResource(Res.string.settings_casting_strategy_summary, preferences.defaultCastingStrategy.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_CASTING_GROUP_IDS,
                ) {
                    val castTotal = 3
                    var castIdx = 0
                    val castingStrategyDescs = CastingStrategy.entries.associateWith {
                        when (it) {
                            CastingStrategy.PREFER_CAST -> stringResource(Res.string.settings_casting_prefer_cast)
                            CastingStrategy.PREFER_DLNA -> stringResource(Res.string.settings_casting_prefer_dlna)
                            CastingStrategy.ASK -> stringResource(Res.string.settings_casting_ask)
                        }
                    }

                    val castingStrategyTitle = stringResource(Res.string.settings_casting_strategy)
                    SettingListItem(
                        icon = Tabler.Outline.Cast,
                        title = castingStrategyTitle,
                        subtitle = stringResource(Res.string.settings_casting_strategy_subtitle),
                        trailingText = preferences.defaultCastingStrategy.displayName,
                        highlighted = highlightSettingId == "casting_strategy",
                        index = castIdx++, count = castTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = castingStrategyTitle,
                                items = CastingStrategy.entries,
                                label = { it.displayName },
                                subtitle = { castingStrategyDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.defaultCastingStrategy },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDefaultCastingStrategy(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Settings,
                        title = stringResource(Res.string.settings_background_casting),
                        subtitle = stringResource(Res.string.settings_background_casting_subtitle),
                        checked = preferences.backgroundCastingEnabled,
                        highlighted = highlightSettingId == "background_casting",
                        index = castIdx++, count = castTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.syncPlayCast.setBackgroundCastingEnabled(it) } },
                    )

                    val noneLabel = stringResource(Res.string.settings_none)
                    val rendererText = preferences.preferredRenderer ?: noneLabel
                    val livingRoomTvLabel = stringResource(Res.string.settings_living_room_tv)
                    SettingListItem(
                        icon = Tabler.Outline.Devices,
                        title = stringResource(Res.string.settings_preferred_renderer),
                        subtitle = stringResource(Res.string.settings_preferred_renderer_subtitle),
                        trailingText = rendererText,
                        highlighted = highlightSettingId == "preferred_renderer",
                        index = castIdx++, count = castTotal,
                        onClick = {
                            if (preferences.preferredRenderer != null) {
                                viewModel.edit { it.syncPlayCast.setPreferredRenderer(null) }
                            } else {
                                viewModel.edit { it.syncPlayCast.setPreferredRenderer(livingRoomTvLabel) }
                            }
                        },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.DeviceTvOld,
                    title = stringResource(Res.string.settings_live_tv_dvr),
                    summary = { stringResource(Res.string.settings_dvr_padding_summary, preferences.dvrPrePaddingMinutes, preferences.dvrPostPaddingMinutes) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_DVR_GROUP_IDS,
                ) {
                    val dvrTotal = 3
                    var dvrIdx = 0
                    val noneLabel = stringResource(Res.string.settings_none)

                    val dvrPrePaddingTitle = stringResource(Res.string.settings_dvr_pre_padding)
                    val xMinutesFormat = stringResource(Res.string.settings_x_minutes)
                    val dvrStartOnTime = stringResource(Res.string.settings_dvr_start_on_time)
                    val dvrStartEarlyFormat = stringResource(Res.string.settings_dvr_start_early)
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = dvrPrePaddingTitle,
                        subtitle = stringResource(Res.string.settings_dvr_pre_padding_subtitle),
                        trailingText = stringResource(Res.string.settings_x_minutes, preferences.dvrPrePaddingMinutes),
                        highlighted = highlightSettingId == "dvr_pre_padding",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = {
                            val options = listOf(0, 1, 2, 5, 10, 15)
                            activePicker = PickerState.List(
                                title = dvrPrePaddingTitle,
                                items = options,
                                label = { if (it == 0) noneLabel else xMinutesFormat.format(it) },
                                subtitle = { if (it == 0) dvrStartOnTime else dvrStartEarlyFormat.format(it) },
                                isSelected = { it == preferences.dvrPrePaddingMinutes },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDvrPrePaddingMinutes(it) } },
                            )
                        },
                    )

                    val dvrPostPaddingTitle = stringResource(Res.string.settings_dvr_post_padding)
                    val dvrStopOnTime = stringResource(Res.string.settings_dvr_stop_on_time)
                    val dvrStopLateFormat = stringResource(Res.string.settings_dvr_stop_late)
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = dvrPostPaddingTitle,
                        subtitle = stringResource(Res.string.settings_dvr_post_padding_subtitle),
                        trailingText = stringResource(Res.string.settings_x_minutes, preferences.dvrPostPaddingMinutes),
                        highlighted = highlightSettingId == "dvr_post_padding",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = {
                            val options = listOf(0, 1, 2, 5, 10, 15, 30)
                            activePicker = PickerState.List(
                                title = dvrPostPaddingTitle,
                                items = options,
                                label = { if (it == 0) noneLabel else xMinutesFormat.format(it) },
                                subtitle = { if (it == 0) dvrStopOnTime else dvrStopLateFormat.format(it) },
                                isSelected = { it == preferences.dvrPostPaddingMinutes },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDvrPostPaddingMinutes(it) } },
                            )
                        },
                    )

                    val dvrRecordingQualityTitle = stringResource(Res.string.settings_dvr_recording_quality)
                    val dvrQualityDescs = mapOf(
                        "AUTO" to stringResource(Res.string.settings_dvr_quality_auto),
                        "HIGH" to stringResource(Res.string.settings_dvr_quality_high),
                        "MEDIUM" to stringResource(Res.string.settings_dvr_quality_medium),
                        "LOW" to stringResource(Res.string.settings_dvr_quality_low),
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Video,
                        title = dvrRecordingQualityTitle,
                        subtitle = stringResource(Res.string.settings_dvr_recording_quality_subtitle),
                        trailingText = preferences.dvrRecordingQuality,
                        highlighted = highlightSettingId == "dvr_recording_quality",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = {
                            val options = listOf("AUTO", "HIGH", "MEDIUM", "LOW")
                            activePicker = PickerState.List(
                                title = dvrRecordingQualityTitle,
                                items = options,
                                label = { it },
                                subtitle = { dvrQualityDescs[it] ?: "" },
                                isSelected = { it == preferences.dvrRecordingQuality },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDvrRecordingQuality(it) } },
                            )
                        },
                    )
                }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 9,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
        }
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_reset_playback_title),
            message = stringResource(Res.string.settings_reset_playback_message),
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                viewModel.resetPlaybackSettings()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
