package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode
import com.raulshma.jellyplay.core.model.HomeHeroStyle
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MpvDebandMode
import com.raulshma.jellyplay.core.model.MpvToneMappingCurve
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.SessionRestoreMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.TranscodeAudioCodecPreference
import com.raulshma.jellyplay.core.model.TranscodeVideoCodecPreference
import com.raulshma.jellyplay.core.model.TrickplayMode
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_all_aggressive
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_all_fastest
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_bidir
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_level
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_none_best
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_none_no_skip
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_non_ref
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_deband_mode_heavy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_deband_mode_light
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_deband_mode_medium
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_deband_mode_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_format_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_format_dv_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_format_hdr10_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_format_prefer_dv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_format_sdr_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_style_detailed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_style_carousel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_style_slideshow
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_style_tv_slider
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_direct
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_transcode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_screensaver_clock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_screensaver_fade_to_black
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_screensaver_logo
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_screensaver_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_360p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_360p_low
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_480p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_480p_sd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_4k
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_4k_ultra_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_720p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_720p_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_1080p
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_1080p_full_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_session_restore_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_session_restore_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_session_restore_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto_adaptive
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bitmap_subtitle_scale_cover
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bitmap_subtitle_scale_fit
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bitmap_subtitle_scale_stretch
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_audio_codec_ac3
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_audio_codec_aac
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_audio_codec_eac3
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_audio_codec_opus
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_codec_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_video_codec_av1
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_video_codec_h264
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transcode_video_codec_hevc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_bt_2390
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_bt_2446a
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_clip
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_hable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_linear
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tone_mapping_curve_spline
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_mode_bubble
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_mode_filmstrip
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_mode_fullscreen

// The enum→label resource mappings for the playback rows (streaming quality,
// live-stream option, the libVLC skip-loop-filter / skip-frame levels);
// `internal` only because the advanced-video and libVLC-branch composables
// live in their own files.

internal fun streamingQualityLabelRes(quality: StreamingQuality): StringResource = when (quality) {
    StreamingQuality.AUTO -> Res.string.settings_streaming_quality_auto_adaptive
    StreamingQuality.LOW_360P -> Res.string.settings_quality_360p_low
    StreamingQuality.SD_480P -> Res.string.settings_quality_480p_sd
    StreamingQuality.HD_720P -> Res.string.settings_quality_720p_hd
    StreamingQuality.FHD_1080P -> Res.string.settings_quality_1080p_full_hd
    StreamingQuality.UHD_4K -> Res.string.settings_quality_4k_ultra_hd
}

internal fun streamingQualityShortRes(quality: StreamingQuality): StringResource = when (quality) {
    StreamingQuality.AUTO -> Res.string.settings_streaming_quality_auto
    StreamingQuality.LOW_360P -> Res.string.settings_quality_360p
    StreamingQuality.SD_480P -> Res.string.settings_quality_480p
    StreamingQuality.HD_720P -> Res.string.settings_quality_720p
    StreamingQuality.FHD_1080P -> Res.string.settings_quality_1080p
    StreamingQuality.UHD_4K -> Res.string.settings_quality_4k
}

internal fun liveStreamOptionLabelRes(option: LiveStreamOption): StringResource = when (option) {
    LiveStreamOption.AUTO -> Res.string.settings_live_auto
    LiveStreamOption.DIRECT_STREAM -> Res.string.settings_live_direct
    LiveStreamOption.TRANSCODE -> Res.string.settings_live_transcode
}

internal fun vlcSkipLoopFilterLabelRes(level: Int): StringResource = when (level) {
    0 -> Res.string.settings_b_frames_none_best
    1 -> Res.string.settings_b_frames_default
    2 -> Res.string.settings_b_frames_non_ref
    3 -> Res.string.settings_b_frames_bidir
    4 -> Res.string.settings_b_frames_all_fastest
    else -> Res.string.settings_b_frames_level
}

internal fun vlcSkipFrameLabelRes(level: Int): StringResource = when (level) {
    0 -> Res.string.settings_b_frames_none_no_skip
    1 -> Res.string.settings_b_frames_default
    2 -> Res.string.settings_b_frames_non_ref
    3 -> Res.string.settings_b_frames_bidir
    4 -> Res.string.settings_b_frames_all_aggressive
    else -> Res.string.settings_b_frames_level
}

// The new playback-screen / home-hero enum pickers' labels (the
// streaming-quality when-map shape). `internal` so the group emission
// composables and the diff registry (PreferenceCategoryPresentation) share
// them.

internal fun sessionRestoreModeLabelRes(mode: SessionRestoreMode): StringResource = when (mode) {
    SessionRestoreMode.OFF -> Res.string.settings_session_restore_off
    SessionRestoreMode.ASK -> Res.string.settings_session_restore_ask
    SessionRestoreMode.AUTO -> Res.string.settings_session_restore_auto
}

internal fun hdrFormatPreferenceLabelRes(preference: HdrFormatPreference): StringResource = when (preference) {
    HdrFormatPreference.AUTO -> Res.string.settings_hdr_format_auto
    HdrFormatPreference.PREFER_DV_OVER_HDR10 -> Res.string.settings_hdr_format_prefer_dv
    HdrFormatPreference.SDR_ONLY -> Res.string.settings_hdr_format_sdr_only
    HdrFormatPreference.HDR10_ONLY -> Res.string.settings_hdr_format_hdr10_only
    HdrFormatPreference.DOLBY_VISION_ONLY -> Res.string.settings_hdr_format_dv_only
}

internal fun transcodeVideoCodecLabelRes(codec: TranscodeVideoCodecPreference): StringResource = when (codec) {
    TranscodeVideoCodecPreference.AUTO -> Res.string.settings_transcode_codec_auto
    TranscodeVideoCodecPreference.H264 -> Res.string.settings_transcode_video_codec_h264
    TranscodeVideoCodecPreference.HEVC -> Res.string.settings_transcode_video_codec_hevc
    TranscodeVideoCodecPreference.AV1 -> Res.string.settings_transcode_video_codec_av1
}

internal fun transcodeAudioCodecLabelRes(codec: TranscodeAudioCodecPreference): StringResource = when (codec) {
    TranscodeAudioCodecPreference.AUTO -> Res.string.settings_transcode_codec_auto
    TranscodeAudioCodecPreference.AAC -> Res.string.settings_transcode_audio_codec_aac
    TranscodeAudioCodecPreference.AC3 -> Res.string.settings_transcode_audio_codec_ac3
    TranscodeAudioCodecPreference.EAC3 -> Res.string.settings_transcode_audio_codec_eac3
    TranscodeAudioCodecPreference.OPUS -> Res.string.settings_transcode_audio_codec_opus
}

internal fun trickplayModeLabelRes(mode: TrickplayMode): StringResource = when (mode) {
    TrickplayMode.BUBBLE -> Res.string.settings_trickplay_mode_bubble
    TrickplayMode.FILMSTRIP -> Res.string.settings_trickplay_mode_filmstrip
    TrickplayMode.FULLSCREEN -> Res.string.settings_trickplay_mode_fullscreen
}

internal fun pauseScreensaverModeLabelRes(mode: PauseScreensaverMode): StringResource = when (mode) {
    PauseScreensaverMode.OFF -> Res.string.settings_pause_screensaver_off
    PauseScreensaverMode.CLOCK -> Res.string.settings_pause_screensaver_clock
    PauseScreensaverMode.LOGO -> Res.string.settings_pause_screensaver_logo
    PauseScreensaverMode.FADE_TO_BLACK -> Res.string.settings_pause_screensaver_fade_to_black
}

internal fun mpvToneMappingCurveLabelRes(curve: MpvToneMappingCurve): StringResource = when (curve) {
    MpvToneMappingCurve.AUTO -> Res.string.settings_tone_mapping_curve_auto
    MpvToneMappingCurve.BT_2390 -> Res.string.settings_tone_mapping_curve_bt_2390
    MpvToneMappingCurve.BT_2446A -> Res.string.settings_tone_mapping_curve_bt_2446a
    MpvToneMappingCurve.SPLINE -> Res.string.settings_tone_mapping_curve_spline
    MpvToneMappingCurve.HABLE -> Res.string.settings_tone_mapping_curve_hable
    MpvToneMappingCurve.CLIP -> Res.string.settings_tone_mapping_curve_clip
    MpvToneMappingCurve.LINEAR -> Res.string.settings_tone_mapping_curve_linear
}

internal fun mpvDebandModeLabelRes(mode: MpvDebandMode): StringResource = when (mode) {
    MpvDebandMode.OFF -> Res.string.settings_deband_mode_off
    MpvDebandMode.LIGHT -> Res.string.settings_deband_mode_light
    MpvDebandMode.MEDIUM -> Res.string.settings_deband_mode_medium
    MpvDebandMode.HEAVY -> Res.string.settings_deband_mode_heavy
}

internal fun bitmapSubtitleScaleModeLabelRes(mode: BitmapSubtitleScaleMode): StringResource = when (mode) {
    BitmapSubtitleScaleMode.FIT -> Res.string.settings_bitmap_subtitle_scale_fit
    BitmapSubtitleScaleMode.STRETCH -> Res.string.settings_bitmap_subtitle_scale_stretch
    BitmapSubtitleScaleMode.COVER -> Res.string.settings_bitmap_subtitle_scale_cover
}

internal fun homeHeroStyleLabelRes(style: HomeHeroStyle): StringResource = when (style) {
    HomeHeroStyle.CAROUSEL -> Res.string.settings_home_hero_style_carousel
    HomeHeroStyle.SLIDESHOW -> Res.string.settings_home_hero_style_slideshow
    HomeHeroStyle.DETAILED -> Res.string.settings_home_hero_style_detailed
    HomeHeroStyle.TV_SLIDER -> Res.string.settings_home_hero_style_tv_slider
}
