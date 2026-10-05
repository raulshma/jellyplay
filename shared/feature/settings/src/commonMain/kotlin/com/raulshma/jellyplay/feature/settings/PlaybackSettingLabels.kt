package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.StreamingQuality
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_direct
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_transcode
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto_adaptive

// The enum→label resource mappings for the playback rows (streaming quality,
// live-stream option, the libVLC skip-loop-filter / skip-frame levels), moved
// verbatim out of PlaybackSettingsScreen.kt; `internal` only because the
// advanced-video and libVLC-branch composables now live in their own files.

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
