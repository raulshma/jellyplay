package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaType
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
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.VlcAudioOutput
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private fun streamingQualityLabel(quality: StreamingQuality): String = when (quality) {
    StreamingQuality.AUTO -> "Auto (adaptive)"
    StreamingQuality.LOW_360P -> "360p (Low)"
    StreamingQuality.SD_480P -> "480p (SD)"
    StreamingQuality.HD_720P -> "720p (HD)"
    StreamingQuality.FHD_1080P -> "1080p (Full HD)"
    StreamingQuality.UHD_4K -> "4K (Ultra HD)"
}

private fun streamingQualityShort(quality: StreamingQuality): String = when (quality) {
    StreamingQuality.AUTO -> "Auto"
    StreamingQuality.LOW_360P -> "360p"
    StreamingQuality.SD_480P -> "480p"
    StreamingQuality.HD_720P -> "720p"
    StreamingQuality.FHD_1080P -> "1080p"
    StreamingQuality.UHD_4K -> "4K"
}

private fun vlcSkipLoopFilterLabel(level: Int): String = when (level) {
    0 -> "None (Best Quality)"
    1 -> "Default"
    2 -> "Non-Reference"
    3 -> "Bi-Directional"
    4 -> "All (Fastest)"
    else -> "Level $level"
}

private fun vlcSkipFrameLabel(level: Int): String = when (level) {
    0 -> "None (No Skipping)"
    1 -> "Default"
    2 -> "Non-Reference"
    3 -> "Bi-Directional"
    4 -> "All (Aggressive)"
    else -> "Level $level"
}

sealed class PlaybackSettingsDialog {
    object None : PlaybackSettingsDialog()
    object PlayerPicker : PlaybackSettingsDialog()
    object OrientationPicker : PlaybackSettingsDialog()
    object AspectRatioPicker : PlaybackSettingsDialog()
    object VideoSpeedPicker : PlaybackSettingsDialog()
    object VideoHoldSpeedMultiplierPicker : PlaybackSettingsDialog()
    object VideoSeekDurationPicker : PlaybackSettingsDialog()
    object ControlsTimeoutPicker : PlaybackSettingsDialog()
    object SkipBackOnResumePicker : PlaybackSettingsDialog()
    object PassOutProtectionPicker : PlaybackSettingsDialog()
    object SwipeSeekPicker : PlaybackSettingsDialog()
    object PreloadBufferPicker : PlaybackSettingsDialog()
    object AudioDelayPicker : PlaybackSettingsDialog()
    object BrightnessPicker : PlaybackSettingsDialog()
    object GestureIndicatorSidePicker : PlaybackSettingsDialog()
    object MpvVideoOutputPicker : PlaybackSettingsDialog()
    object MpvScalerPicker : PlaybackSettingsDialog()
    object MpvAudioOutputPicker : PlaybackSettingsDialog()
    object MpvAudioFallbackPicker : PlaybackSettingsDialog()
    object MpvDemuxerPicker : PlaybackSettingsDialog()
    object MpvHwdecPicker : PlaybackSettingsDialog()
    object MpvSkipLoopFilterPicker : PlaybackSettingsDialog()
    object MpvFrameDropPicker : PlaybackSettingsDialog()
    object VlcAudioOutputPicker : PlaybackSettingsDialog()
    object VlcNetworkCachingPicker : PlaybackSettingsDialog()
    object VlcSkipLoopFilterPicker : PlaybackSettingsDialog()
    object VlcSkipFramePicker : PlaybackSettingsDialog()
    object VlcDecoderThreadsPicker : PlaybackSettingsDialog()
    object ExoScalingPicker : PlaybackSettingsDialog()
    object ExoFrameRatePicker : PlaybackSettingsDialog()
    object ExoAudioOffloadPicker : PlaybackSettingsDialog()
    object ExoBackBufferPicker : PlaybackSettingsDialog()
    object ExoCodecPicker : PlaybackSettingsDialog()
    object SyncPlayJoinBehaviorPicker : PlaybackSettingsDialog()
    object SyncPlayTolerancePicker : PlaybackSettingsDialog()
    object CastingStrategyPicker : PlaybackSettingsDialog()
    object DvrPrePaddingPicker : PlaybackSettingsDialog()
    object DvrPostPaddingPicker : PlaybackSettingsDialog()
    object DvrRecordingQualityPicker : PlaybackSettingsDialog()
    object AutoPlayCountdownPicker : PlaybackSettingsDialog()
    object TvZoomModePicker : PlaybackSettingsDialog()
}

private val PLAYBACK_ADVANCED_GROUP_IDS = setOf("dialogue_boost", "decoder", "audio_passthrough", "frame_rate_matching", "streaming_quality", "audio_delay")
private val PLAYBACK_ENGINE_GROUP_IDS = setOf(
    "mpv_video_output", "mpv_scaler", "mpv_debanding", "mpv_interpolation", "mpv_audio_output",
    "mpv_audio_fallback", "mpv_buffer_size", "mpv_hwdec_override", "mpv_skip_loop_filter", "mpv_frame_drop",
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
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<PlaybackSettingsDialog>(PlaybackSettingsDialog.None) }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "playback_init",
    )

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId, showAdvanced) {
        val playerGroup = listOf(
            "player_engine", "seek_duration", "orientation", "gestures", "default_speed", "hold_speed",
            "hold_speed_multiplier", "default_aspect", "video_autoplay_next", "autoplay_countdown",
            "controls_timeout", "skip_back_on_resume", "pass_out_protection", "autoplay_trailers",
            "cinema_mode", "android_tv_watch_next", "tv_zoom_mode", "episode_browser", "playback_metadata",
            "swipe_seek_range", "remember_brightness", "default_brightness_level", "trickplay_preview",
            "trickplay_on_gestures", "preload_buffer", "background_audio", "keep_screen_on", "incognito_mode",
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
        title = stringResource(R.string.settings_playback_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
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
                    title = stringResource(R.string.settings_video_player),
                    summary = { stringResource(R.string.settings_playback_subtitle, preferences.preferredPlayer.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    var idx = 0
                    val total = if (showAdvanced) 30 else 11

                    SettingListItem(
                        icon = Tabler.Outline.PlayerPlay,
                        title = stringResource(R.string.settings_player_engine),
                        subtitle = stringResource(R.string.settings_player_engine_subtitle),
                        trailingText = preferences.preferredPlayer.displayName,
                        highlighted = highlightSettingId == "player_engine",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.PlayerPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.PlayerTrackNext,
                        title = stringResource(R.string.settings_seek_duration),
                        subtitle = stringResource(R.string.settings_seek_duration_subtitle),
                        trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                        highlighted = highlightSettingId == "seek_duration",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.VideoSeekDurationPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobileRotated,
                        title = stringResource(R.string.settings_orientation),
                        subtitle = stringResource(R.string.settings_orientation_subtitle),
                        trailingText = preferences.videoDefaultOrientation.displayName,
                        highlighted = highlightSettingId == "orientation",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.OrientationPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandMove,
                        title = stringResource(R.string.settings_gestures),
                        subtitle = if (preferences.videoGesturesEnabled) stringResource(R.string.settings_gestures_on) else stringResource(R.string.settings_gestures_off),
                        checked = preferences.videoGesturesEnabled,
                        highlighted = highlightSettingId == "gestures",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setVideoGesturesEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.ArrowsHorizontal,
                        title = "Gesture Indicator Side",
                        subtitle = if (preferences.videoGestureIndicatorSide == GestureIndicatorSide.OPPOSITE)
                            "Show brightness/volume bar opposite the gesture" else "Show bar on the same side as the gesture",
                        trailingText = preferences.videoGestureIndicatorSide.displayName,
                        highlighted = highlightSettingId == "gesture_indicator_side",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.GestureIndicatorSidePicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(R.string.settings_default_speed),
                        subtitle = stringResource(R.string.settings_default_speed_subtitle),
                        trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        highlighted = highlightSettingId == "default_speed",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.VideoSpeedPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.RewindForward30,
                        title = stringResource(R.string.settings_hold_to_seek),
                        subtitle = if (preferences.videoHoldSpeedEnabled) stringResource(R.string.settings_hold_to_seek_on) else stringResource(R.string.settings_disabled),
                        checked = preferences.videoHoldSpeedEnabled,
                        highlighted = highlightSettingId == "hold_speed",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setVideoHoldSpeedEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Rocket,
                        title = stringResource(R.string.settings_hold_to_seek_speed),
                        subtitle = stringResource(R.string.settings_hold_to_seek_speed_subtitle),
                        trailingText = "${preferences.videoHoldSpeedMultiplier}x",
                        highlighted = highlightSettingId == "hold_speed_multiplier",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.VideoHoldSpeedMultiplierPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.ArrowAutofitHeight,
                        title = stringResource(R.string.settings_default_aspect),
                        subtitle = stringResource(R.string.settings_default_aspect_subtitle),
                        trailingText = preferences.videoDefaultAspectRatio,
                        highlighted = highlightSettingId == "default_aspect",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.AspectRatioPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = stringResource(R.string.settings_auto_play_next),
                        subtitle = if (preferences.videoAutoplayNext) stringResource(R.string.settings_auto_play_next_on) else stringResource(R.string.settings_auto_play_next_off),
                        checked = preferences.videoAutoplayNext,
                        highlighted = highlightSettingId == "video_autoplay_next",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setVideoAutoplayNext(it) },
                    )
                    val countdownLabel = if (preferences.autoPlayCountdownSec == 0) "Off" else "${preferences.autoPlayCountdownSec}s"
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = stringResource(R.string.settings_auto_play_countdown),
                        subtitle = stringResource(R.string.settings_auto_play_countdown_subtitle),
                        trailingText = countdownLabel,
                        highlighted = highlightSettingId == "autoplay_countdown",
                        index = idx++, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.AutoPlayCountdownPicker },
                    )
                    if (showAdvanced) {
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(R.string.settings_controls_timeout),
                            subtitle = stringResource(R.string.settings_controls_timeout_subtitle),
                            trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                            highlighted = highlightSettingId == "controls_timeout",
                            index = idx++, count = total,
                            onClick = { activeDialog = PlaybackSettingsDialog.ControlsTimeoutPicker },
                        )
                        val skipBackLabel = if (preferences.videoSkipBackOnResumeMs == 0L) "Off" else "${preferences.videoSkipBackOnResumeMs / 1000}s"
                        SettingListItem(
                            icon = Tabler.Outline.History,
                            title = "Skip Back on Resume",
                            subtitle = "Jump back when un-pausing playback",
                            trailingText = skipBackLabel,
                            highlighted = highlightSettingId == "skip_back_on_resume",
                            index = idx++, count = total,
                            onClick = { activeDialog = PlaybackSettingsDialog.SkipBackOnResumePicker },
                        )
                        val passOutLabel = if (preferences.videoPassOutProtectionHours == 0) "Off" else "${preferences.videoPassOutProtectionHours}h"
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = "Pass-out Protection",
                            subtitle = "Pause playback after no interaction",
                            trailingText = passOutLabel,
                            highlighted = highlightSettingId == "pass_out_protection",
                            index = idx++, count = total,
                            onClick = { activeDialog = PlaybackSettingsDialog.PassOutProtectionPicker },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Clipboard,
                            title = "Autoplay Trailers",
                            subtitle = if (preferences.trailerAutoplay) "Trailers play automatically" else "Trailers require manual play",
                            checked = preferences.trailerAutoplay,
                            highlighted = highlightSettingId == "autoplay_trailers",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setTrailerAutoplay(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Video,
                            title = "Cinema Mode",
                            subtitle = if (preferences.cinemaModeEnabled) "Play intros/trailers before main feature" else "Skip pre-roll intros",
                            checked = preferences.cinemaModeEnabled,
                            highlighted = highlightSettingId == "cinema_mode",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setCinemaModeEnabled(it) },
                        )
                        if (isTv) {
                            SettingToggleItem(
                                icon = Tabler.Outline.DeviceTv,
                                title = "Watch Next Row",
                                subtitle = if (preferences.androidTvWatchNextEnabled) "Publish Continue / Next Up to Android TV home" else "Do not publish to system Watch Next row",
                                checked = preferences.androidTvWatchNextEnabled,
                                highlighted = highlightSettingId == "android_tv_watch_next",
                                index = idx++, count = total,
                                onCheckedChange = { viewModel.setAndroidTvWatchNextEnabled(it) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Crop,
                                title = "TV Zoom Mode",
                                subtitle = "Crop/zoom video to fill screen (0% = off)",
                                trailingText = if (preferences.tvZoomModePercent == 0f) "Off" else "${preferences.tvZoomModePercent.toInt()}%",
                                highlighted = highlightSettingId == "tv_zoom_mode",
                                index = idx++, count = total,
                                onClick = { activeDialog = PlaybackSettingsDialog.TvZoomModePicker },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.List,
                            title = "Episode Browser",
                            subtitle = if (preferences.videoEpisodeBrowserEnabled) "Show episode list during playback" else "Hide episode list",
                            checked = preferences.videoEpisodeBrowserEnabled,
                            highlighted = highlightSettingId == "episode_browser",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setVideoEpisodeBrowserEnabled(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.InfoCircle,
                            title = "Playback Metadata",
                            subtitle = if (preferences.videoShowPlaybackMetadata) "Show codec and stream info" else "Hide codec info",
                            checked = preferences.videoShowPlaybackMetadata,
                            highlighted = highlightSettingId == "playback_metadata",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setVideoShowPlaybackMetadata(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarRight,
                            title = "Swipe Seek Range",
                            subtitle = "Maximum seek range for swipe gesture",
                            trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                            highlighted = highlightSettingId == "swipe_seek_range",
                            index = idx++, count = total,
                            onClick = { activeDialog = PlaybackSettingsDialog.SwipeSeekPicker },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.BrightnessHalf,
                            title = "Remember Brightness",
                            subtitle = if (preferences.videoRememberBrightness) "Brightness saved between sessions" else "Reset brightness each session",
                            checked = preferences.videoRememberBrightness,
                            highlighted = highlightSettingId == "remember_brightness",
                            index = idx++, count = total,
                            onCheckedChange = { enabled ->
                                viewModel.setVideoRememberBrightness(enabled)
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Sun,
                            title = "Default Brightness Level",
                            subtitle = "Default screen brightness for video playback",
                            trailingText = "${(preferences.videoBrightnessLevel * 100).toInt()}%",
                            highlighted = highlightSettingId == "default_brightness_level",
                            index = idx++, count = total,
                            onClick = { activeDialog = PlaybackSettingsDialog.BrightnessPicker },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Photo,
                            title = "Trickplay Preview",
                            subtitle = if (preferences.trickplayEnabled) "Show thumbnails on seek bar" else "No seek bar thumbnails",
                            checked = preferences.trickplayEnabled,
                            highlighted = highlightSettingId == "trickplay_preview",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setTrickplayEnabled(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.HandMove,
                            title = "Trickplay on Gestures",
                            subtitle = if (preferences.trickplayOnSeekGesture) "Show thumbnails during swipe seek" else "No thumbnails during seek",
                            checked = preferences.trickplayOnSeekGesture,
                            highlighted = highlightSettingId == "trickplay_on_gestures",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setTrickplayOnSeekGesture(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = "Preload Buffer",
                            subtitle = "Amount to buffer ahead during playback",
                            trailingText = preferences.videoPreloadBufferSize.displayName,
                            highlighted = highlightSettingId == "preload_buffer",
                            index = idx++, count = total,
                            onClick = { activeDialog = PlaybackSettingsDialog.PreloadBufferPicker },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Music,
                            title = "Background Audio",
                            subtitle = "Keep audio playing when switching apps during video playback",
                            checked = preferences.backgroundVideoAudioEnabled,
                            highlighted = highlightSettingId == "background_audio",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setBackgroundVideoAudioEnabled(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Eye,
                            title = "Keep Screen On",
                            subtitle = "Prevent screen from turning off during video playback",
                            checked = preferences.keepScreenOnDuringVideo,
                            highlighted = highlightSettingId == "keep_screen_on",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setKeepScreenOnDuringVideo(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Ghost,
                            title = "Incognito Mode",
                            subtitle = if (preferences.incognitoModeEnabled) "Bypasses reporting playback progress to server" else "Reports playback progress to server",
                            checked = preferences.incognitoModeEnabled,
                            highlighted = highlightSettingId == "incognito_mode",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setIncognitoModeEnabled(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Clock,
                            title = "Show Time Remaining",
                            subtitle = if (preferences.showTimeRemaining) "Display remaining time instead of elapsed time" else "Display elapsed time",
                            checked = preferences.showTimeRemaining,
                            highlighted = highlightSettingId == "show_time_remaining",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setShowTimeRemaining(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Clock,
                            title = "Show Clock",
                            subtitle = if (preferences.showClockInPlayer) "Display current time in player top bar" else "No clock in player",
                            checked = preferences.showClockInPlayer,
                            highlighted = highlightSettingId == "show_clock_player",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setShowClockInPlayer(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PlayerPause,
                            title = "Pause on Focus Loss",
                            subtitle = if (preferences.pauseOnAudioFocusLoss) "Pause playback when system reports focus loss" else "Continue playback on focus loss",
                            checked = preferences.pauseOnAudioFocusLoss,
                            highlighted = highlightSettingId == "pause_on_focus_loss",
                            index = idx, count = total,
                            onCheckedChange = { viewModel.setPauseOnAudioFocusLoss(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Phone,
                            title = "Duck on Phone Call",
                            subtitle = if (preferences.duckOnTransientFocusLoss) "Lower volume + rewind on phone call" else "No action on phone call",
                            checked = preferences.duckOnTransientFocusLoss,
                            highlighted = highlightSettingId == "duck_on_transient_focus_loss",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setDuckOnTransientFocusLoss(it) },
                        )
                    }
                }
            }

            if (showAdvanced) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.BadgeHd,
                    title = "Advanced Video",
                    summary = { "Decoder: ${preferences.decoderMode.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_ADVANCED_GROUP_IDS,
                ) {
                    val total = 6 + (if (preferences.dialogueBoostEnabled) 1 else 0)

                    var idx = 0
                    SettingToggleItem(
                        icon = Tabler.Outline.Microphone2,
                        title = "Dialogue Boost",
                        subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                        checked = preferences.dialogueBoostEnabled,
                        highlighted = highlightSettingId == "dialogue_boost",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                    )
                    if (preferences.dialogueBoostEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Dialogue Boost Strength",
                            subtitle = preferences.dialogueBoostStrength.displayName,
                            trailingText = preferences.dialogueBoostStrength.displayName,
                            index = idx++, count = total,
                            onClick = {
                                val strengths = EffectStrength.entries
                                val currentIndex = strengths.indexOf(preferences.dialogueBoostStrength)
                                val nextIndex = (currentIndex + 1) % strengths.size
                                viewModel.setDialogueBoostStrength(strengths[nextIndex])
                            },
                        )
                    }
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = "Decoder",
                        subtitle = preferences.decoderMode.displayName,
                        trailingText = preferences.decoderMode.displayName.split(" ").first(),
                        highlighted = highlightSettingId == "decoder",
                        index = idx++, count = total,
                        onClick = {
                            val modes = DecoderMode.entries
                            val currentIndex = modes.indexOf(preferences.decoderMode)
                            val nextIndex = (currentIndex + 1) % modes.size
                            viewModel.setDecoderMode(modes[nextIndex])
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Movie,
                        title = "Audio Passthrough",
                        subtitle = if (preferences.audioPassthrough) "Direct audio to receiver" else "Software audio processing",
                        checked = preferences.audioPassthrough,
                        highlighted = highlightSettingId == "audio_passthrough",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioPassthrough(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Maximize,
                        title = "Frame Rate Match",
                        subtitle = if (preferences.frameRateMatching) "Display refresh matches content" else "Fixed display refresh rate",
                        checked = preferences.frameRateMatching,
                        highlighted = highlightSettingId == "frame_rate_matching",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setFrameRateMatching(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = "Streaming Quality",
                        subtitle = streamingQualityLabel(preferences.streamingQuality),
                        trailingText = streamingQualityShort(preferences.streamingQuality),
                        highlighted = highlightSettingId == "streaming_quality",
                        index = idx++, count = total,
                        onClick = {
                            val qualities = StreamingQuality.entries
                            val currentIndex = qualities.indexOf(preferences.streamingQuality)
                            val nextIndex = (currentIndex + 1) % qualities.size
                            viewModel.setStreamingQuality(qualities[nextIndex])
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = "Audio Delay",
                        subtitle = if (preferences.audioDelayMs == 0L) "No audio delay" else "${preferences.audioDelayMs}ms delay",
                        trailingText = if (preferences.audioDelayMs == 0L) "Off" else "${preferences.audioDelayMs}ms",
                        highlighted = highlightSettingId == "audio_delay",
                        index = idx, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.AudioDelayPicker },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Settings,
                    title = "Engine Config",
                    summary = { preferences.preferredPlayer.displayName },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_ENGINE_GROUP_IDS,
                ) {
                    when (preferences.preferredPlayer) {
                        PlayerType.MPV -> {
                            val mpvCfg = preferences.mpvConfig
                            val mpvDefault = MpvEngineConfig()
                            val mpvTotal = 11
                            var mpvIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.Video,
                                title = "Video Output",
                                subtitle = "${mpvCfg.videoOutput.displayName} (${mpvCfg.videoOutput.key})",
                                trailingText = mpvCfg.videoOutput.key,
                                highlighted = highlightSettingId == "mpv_video_output",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvVideoOutputPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = "Scaler",
                                subtitle = "${mpvCfg.scaler.displayName} (${mpvCfg.scaler.key})",
                                trailingText = mpvCfg.scaler.key,
                                highlighted = highlightSettingId == "mpv_scaler",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvScalerPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ColorFilter,
                                title = "Debanding",
                                subtitle = if (mpvCfg.deband) "GPU debanding enabled" else "No debanding",
                                checked = mpvCfg.deband,
                                highlighted = highlightSettingId == "mpv_debanding",
                                index = mpvIdx++, count = mpvTotal,
                                onCheckedChange = { viewModel.setMpvConfig(mpvCfg.copy(deband = it)) },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ArrowsHorizontal,
                                title = "Interpolation",
                                subtitle = if (mpvCfg.interpolation) "Smooth motion for mixed FPS" else "No frame interpolation",
                                checked = mpvCfg.interpolation,
                                highlighted = highlightSettingId == "mpv_interpolation",
                                index = mpvIdx++, count = mpvTotal,
                                onCheckedChange = { viewModel.setMpvConfig(mpvCfg.copy(interpolation = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = "Audio Output",
                                subtitle = "${mpvCfg.audioOutput.displayName} (${mpvCfg.audioOutput.key})",
                                trailingText = mpvCfg.audioOutput.key,
                                highlighted = highlightSettingId == "mpv_audio_output",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvAudioOutputPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowBack,
                                title = "Audio Fallback",
                                subtitle = mpvCfg.audioFallback?.displayName ?: "None",
                                trailingText = mpvCfg.audioFallback?.key ?: "None",
                                highlighted = highlightSettingId == "mpv_audio_fallback",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvAudioFallbackPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Buffer Size",
                                subtitle = "${mpvCfg.demuxerMaxBytes.displayName} (${mpvCfg.demuxerMaxBytes.key})",
                                trailingText = mpvCfg.demuxerMaxBytes.key,
                                highlighted = highlightSettingId == "mpv_buffer_size",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvDemuxerPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "HW Dec Override",
                                subtitle = mpvCfg.hwdecOverride?.displayName ?: "Use universal setting",
                                trailingText = mpvCfg.hwdecOverride?.key ?: "Auto",
                                highlighted = highlightSettingId == "mpv_hwdec_override",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvHwdecPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = "${mpvCfg.skipLoopFilter.displayName} (${mpvCfg.skipLoopFilter.key})",
                                trailingText = mpvCfg.skipLoopFilter.key,
                                highlighted = highlightSettingId == "mpv_skip_loop_filter",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvSkipLoopFilterPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.PhotoDown,
                                title = "Frame Drop",
                                subtitle = "${mpvCfg.frameDrop.displayName} (${mpvCfg.frameDrop.key})",
                                trailingText = mpvCfg.frameDrop.key,
                                highlighted = highlightSettingId == "mpv_frame_drop",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvFrameDropPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = "Reset to Defaults",
                                subtitle = "Restore all MPV settings",
                                index = mpvIdx, count = mpvTotal,
                                onClick = { viewModel.setMpvConfig(mpvDefault) },
                            )
                        }
                        PlayerType.LIBVLC -> {
                            val vlcCfg = preferences.libVlcConfig
                            val vlcDefault = LibVlcEngineConfig()
                            val vlcTotal = 8
                            var vlcIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = "Audio Output",
                                subtitle = "${vlcCfg.audioOutput.displayName} (${vlcCfg.audioOutput.key})",
                                trailingText = vlcCfg.audioOutput.key,
                                highlighted = highlightSettingId == "vlc_audio_output",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcAudioOutputPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Clock,
                                title = "Audio Time Stretch",
                                subtitle = if (vlcCfg.audioTimeStretch) "Pitch-corrected speed change" else "Speed changes affect pitch",
                                checked = vlcCfg.audioTimeStretch,
                                highlighted = highlightSettingId == "vlc_audio_time_stretch",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(audioTimeStretch = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Wifi,
                                title = "Network Caching",
                                subtitle = if (vlcCfg.networkCaching == 0) "Auto (device-based)" else "${vlcCfg.networkCaching}ms",
                                trailingText = if (vlcCfg.networkCaching == 0) "Auto" else "${vlcCfg.networkCaching}ms",
                                highlighted = highlightSettingId == "vlc_network_caching",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcNetworkCachingPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = vlcSkipLoopFilterLabel(vlcCfg.skipLoopFilter),
                                trailingText = "Level ${vlcCfg.skipLoopFilter}",
                                highlighted = highlightSettingId == "vlc_skip_loop_filter",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcSkipLoopFilterPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = "Skip Frames",
                                subtitle = if (vlcCfg.skipFrames) "Enable frame skipping" else "No frame skipping",
                                checked = vlcCfg.skipFrames,
                                highlighted = highlightSettingId == "vlc_skip_frames",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(skipFrames = it)) },
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcSkipFramePicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "Decoder Threads",
                                subtitle = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads} threads",
                                trailingText = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads}",
                                highlighted = highlightSettingId == "vlc_decoder_threads",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcDecoderThreadsPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Trash,
                                title = "Drop Late Frames",
                                subtitle = if (vlcCfg.dropLateFrames) "Discard delayed frames" else "Display all frames",
                                checked = vlcCfg.dropLateFrames,
                                highlighted = highlightSettingId == "vlc_drop_late_frames",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(dropLateFrames = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = "Reset to Defaults",
                                subtitle = "Restore all LibVLC settings",
                                index = vlcIdx, count = vlcTotal,
                                onClick = { viewModel.setLibVlcConfig(vlcDefault) },
                            )
                        }
                        PlayerType.EXO_PLAYER -> {
                            val exoCfg = preferences.exoPlayerConfig
                            val exoDefault = ExoPlayerEngineConfig()
                            val exoTotal = 8
                            var exoIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = "Video Scaling",
                                subtitle = "${exoCfg.videoScalingMode.displayName} (${exoCfg.videoScalingMode.key})",
                                trailingText = exoCfg.videoScalingMode.key,
                                highlighted = highlightSettingId == "exo_video_scaling",
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoScalingPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Clock,
                                title = "Frame Rate Strategy",
                                subtitle = "${exoCfg.frameRateStrategy.displayName} (${exoCfg.frameRateStrategy.key})",
                                trailingText = exoCfg.frameRateStrategy.key,
                                highlighted = highlightSettingId == "exo_frame_rate_strategy",
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoFrameRatePicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Volume,
                                title = "Skip Silence",
                                subtitle = if (exoCfg.skipSilence) "Skip silent sections" else "Play all audio",
                                checked = exoCfg.skipSilence,
                                highlighted = highlightSettingId == "exo_skip_silence",
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.setExoPlayerConfig(exoCfg.copy(skipSilence = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Headphones,
                                title = "Audio Offload",
                                subtitle = "${exoCfg.audioOffloadMode.displayName} (${exoCfg.audioOffloadMode.key})",
                                trailingText = exoCfg.audioOffloadMode.key,
                                highlighted = highlightSettingId == "exo_audio_offload",
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoAudioOffloadPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ToggleLeft,
                                title = "Decoder Fallback",
                                subtitle = if (exoCfg.enableDecoderFallback) "Fallback to secondary decoders" else "Primary decoder only",
                                checked = exoCfg.enableDecoderFallback,
                                highlighted = highlightSettingId == "exo_decoder_fallback",
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.setExoPlayerConfig(exoCfg.copy(enableDecoderFallback = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Back Buffer",
                                subtitle = if (exoCfg.backBufferDurationMs == 0) "Disabled" else "${exoCfg.backBufferDurationMs / 1000}s buffer",
                                trailingText = if (exoCfg.backBufferDurationMs == 0) "Off" else "${exoCfg.backBufferDurationMs / 1000}s",
                                highlighted = highlightSettingId == "exo_back_buffer",
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoBackBufferPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Code,
                                title = "Preferred Codecs",
                                subtitle = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All codecs" else exoCfg.preferredVideoMimeTypes.joinToString(", "),
                                trailingText = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All" else "Custom",
                                highlighted = highlightSettingId == "exo_preferred_codecs",
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoCodecPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = "Reset to Defaults",
                                subtitle = "Restore all ExoPlayer settings",
                                index = exoIdx, count = exoTotal,
                                onClick = { viewModel.setExoPlayerConfig(exoDefault) },
                            )
                        }
                        else -> {}
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerTrackNext,
                    title = "Media Segments",
                    summary = {
                        val autoCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.AUTO_SKIP }
                        val buttonCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON }
                        "$autoCount auto-skip, $buttonCount show button"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val segmentTypes = com.raulshma.jellyplay.core.model.MediaSegmentType.entries
                    val totalTypes = segmentTypes.size
                    segmentTypes.forEachIndexed { index, type ->
                        val behavior = preferences.segmentBehaviors[type]
                            ?: com.raulshma.jellyplay.core.model.SegmentBehavior.IGNORE
                        SettingListItem(
                            icon = Tabler.Outline.PlayerTrackNext,
                            title = type.displayName,
                            subtitle = type.description,
                            trailingText = behavior.displayName,
                            index = index, count = totalTypes,
                            onClick = {
                                val behaviors = com.raulshma.jellyplay.core.model.SegmentBehavior.entries
                                val currentIndex = behaviors.indexOf(behavior)
                                val nextIndex = (currentIndex + 1) % behaviors.size
                                viewModel.setSegmentBehavior(type, behaviors[nextIndex])
                            },
                        )
                    }
                }
            }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Users,
                    title = "SyncPlay",
                    summary = { "Join behavior: ${preferences.syncPlayJoinBehavior.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_SYNCPLAY_GROUP_IDS,
                ) {
                    val syncTotal = 3
                    var syncIdx = 0

                    SettingListItem(
                        icon = Tabler.Outline.MessageQuestion,
                        title = "Join Behavior",
                        subtitle = "Action when joining a SyncPlay group",
                        trailingText = preferences.syncPlayJoinBehavior.displayName,
                        highlighted = highlightSettingId == "syncplay_join_behavior",
                        index = syncIdx++, count = syncTotal,
                        onClick = { activeDialog = PlaybackSettingsDialog.SyncPlayJoinBehaviorPicker },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.WaveSine,
                        title = "Sync Tolerance",
                        subtitle = "Allowed drift before correcting playback",
                        trailingText = "${preferences.syncPlayToleranceMs}ms",
                        highlighted = highlightSettingId == "syncplay_tolerance",
                        index = syncIdx++, count = syncTotal,
                        onClick = { activeDialog = PlaybackSettingsDialog.SyncPlayTolerancePicker },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.CircleCheck,
                        title = "Auto-Accept Invites",
                        subtitle = "Automatically accept SyncPlay invites from friends",
                        checked = preferences.syncPlayAutoAcceptInvites,
                        highlighted = highlightSettingId == "syncplay_auto_accept_invites",
                        index = syncIdx++, count = syncTotal,
                        onCheckedChange = { viewModel.setSyncPlayAutoAcceptInvites(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.DeviceTv,
                    title = "Casting & DLNA",
                    summary = { "Strategy: ${preferences.defaultCastingStrategy.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_CASTING_GROUP_IDS,
                ) {
                    val castTotal = 3
                    var castIdx = 0

                    SettingListItem(
                        icon = Tabler.Outline.Cast,
                        title = "Casting Strategy",
                        subtitle = "Preferred method for big screen streaming",
                        trailingText = preferences.defaultCastingStrategy.displayName,
                        highlighted = highlightSettingId == "casting_strategy",
                        index = castIdx++, count = castTotal,
                        onClick = { activeDialog = PlaybackSettingsDialog.CastingStrategyPicker },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Settings,
                        title = "Background Casting",
                        subtitle = "Keep casting active when app is closed",
                        checked = preferences.backgroundCastingEnabled,
                        highlighted = highlightSettingId == "background_casting",
                        index = castIdx++, count = castTotal,
                        onCheckedChange = { viewModel.setBackgroundCastingEnabled(it) },
                    )

                    val rendererText = preferences.preferredRenderer ?: "None"
                    SettingListItem(
                        icon = Tabler.Outline.Devices,
                        title = "Preferred Renderer",
                        subtitle = "Device to target by default when casting (click to toggle)",
                        trailingText = rendererText,
                        highlighted = highlightSettingId == "preferred_renderer",
                        index = castIdx++, count = castTotal,
                        onClick = {
                            if (preferences.preferredRenderer != null) {
                                viewModel.setPreferredRenderer(null)
                            } else {
                                viewModel.setPreferredRenderer("Living Room TV")
                            }
                        },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.DeviceTvOld,
                    title = "Live TV & DVR",
                    summary = { "Padding: +${preferences.dvrPrePaddingMinutes}m / -${preferences.dvrPostPaddingMinutes}m" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PLAYBACK_DVR_GROUP_IDS,
                ) {
                    val dvrTotal = 3
                    var dvrIdx = 0

                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = "DVR Pre-Padding",
                        subtitle = "Start recording before scheduled time",
                        trailingText = "${preferences.dvrPrePaddingMinutes} min",
                        highlighted = highlightSettingId == "dvr_pre_padding",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = { activeDialog = PlaybackSettingsDialog.DvrPrePaddingPicker },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = "DVR Post-Padding",
                        subtitle = "Extend recording after scheduled end time",
                        trailingText = "${preferences.dvrPostPaddingMinutes} min",
                        highlighted = highlightSettingId == "dvr_post_padding",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = { activeDialog = PlaybackSettingsDialog.DvrPostPaddingPicker },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Video,
                        title = "DVR Recording Quality",
                        subtitle = "Default video quality for DVR recordings",
                        trailingText = preferences.dvrRecordingQuality,
                        highlighted = highlightSettingId == "dvr_recording_quality",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = { activeDialog = PlaybackSettingsDialog.DvrRecordingQualityPicker },
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

    if (activeDialog is PlaybackSettingsDialog.PlayerPicker) {
        SettingsListPickerSheet(
            title = "Preferred Player",
            items = PlayerType.entries,
            label = { it.displayName },
            subtitle = { it.description },
            isSelected = { it == preferences.preferredPlayer },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { viewModel.setPreferredPlayer(it); activeDialog = PlaybackSettingsDialog.None },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.OrientationPicker) {
        SettingsListPickerSheet(
            title = "Default Orientation",
            items = OrientationMode.entries,
            label = { it.displayName },
            subtitle = { it.constant },
            isSelected = { it == preferences.videoDefaultOrientation },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { viewModel.setVideoDefaultOrientation(it); activeDialog = PlaybackSettingsDialog.None },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.GestureIndicatorSidePicker) {
        SettingsListPickerSheet(
            title = "Gesture Indicator Side",
            items = GestureIndicatorSide.entries,
            label = { it.displayName },
            isSelected = { it == preferences.videoGestureIndicatorSide },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { viewModel.setVideoGestureIndicatorSide(it); activeDialog = PlaybackSettingsDialog.None },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.AspectRatioPicker) {
        val aspectRatios = listOf("AUTO", "FIT", "FILL", "CROP", "16:9", "4:3", "21:9")
        SettingsListPickerSheet(
            title = "Default Aspect Ratio",
            items = aspectRatios,
            label = { it },
            isSelected = { it == preferences.videoDefaultAspectRatio },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setVideoDefaultAspectRatio(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VideoSpeedPicker) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        SettingsChipPickerSheet(
            title = "Default Video Speed",
            options = speeds.map { if (it == 1.0f) "1x" else "${it}x" },
            selectedIndex = speeds.indexOf(preferences.videoDefaultSpeed),
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoDefaultSpeed(speeds[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VideoHoldSpeedMultiplierPicker) {
        val multipliers = listOf(1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
        SettingsChipPickerSheet(
            title = "Hold-to-Seek Speed",
            options = multipliers.map { "${it}x" },
            selectedIndex = multipliers.indexOf(preferences.videoHoldSpeedMultiplier).let { if (it < 0) 1 else it },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoHoldSpeedMultiplier(multipliers[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VideoSeekDurationPicker) {
        val durations = listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L, 60_000L)
        SettingsChipPickerSheet(
            title = "Double-Tap Seek Duration",
            options = durations.map { "${it / 1000}s" },
            selectedIndex = durations.indexOf(preferences.videoSeekDurationMs),
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoSeekDurationMs(durations[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.ControlsTimeoutPicker) {
        val timeouts = listOf(3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
        SettingsChipPickerSheet(
            title = "Controls Auto-Hide Timeout",
            options = timeouts.map { "${it / 1000}s" },
            selectedIndex = timeouts.indexOf(preferences.videoControlsTimeoutMs),
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoControlsTimeoutMs(timeouts[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.SkipBackOnResumePicker) {
        val durations = listOf(0L, 3_000L, 5_000L, 10_000L, 15_000L, 30_000L)
        SettingsChipPickerSheet(
            title = "Skip Back on Resume",
            options = durations.map { if (it == 0L) "Off" else "${it / 1000}s" },
            selectedIndex = durations.indexOf(preferences.videoSkipBackOnResumeMs),
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoSkipBackOnResumeMs(durations[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.PassOutProtectionPicker) {
        val hours = listOf(0, 1, 2, 3, 4, 6, 8)
        SettingsChipPickerSheet(
            title = "Pass-out Protection",
            options = hours.map { if (it == 0) "Off" else "${it}h" },
            selectedIndex = hours.indexOf(preferences.videoPassOutProtectionHours),
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoPassOutProtectionHours(hours[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.SwipeSeekPicker) {
        val ranges = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L, 300_000L)
        SettingsChipPickerSheet(
            title = "Swipe Seek Maximum Range",
            options = ranges.map { "${it / 1000}s" },
            selectedIndex = ranges.indexOf(preferences.videoSwipeSeekMaxMs),
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = { index ->
                viewModel.setVideoSwipeSeekMaxMs(ranges[index])
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.PreloadBufferPicker) {
        SettingsListPickerSheet(
            title = "Preload Buffer Size",
            items = PreloadBufferSize.entries,
            label = { it.displayName },
            subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
            isSelected = { it == preferences.videoPreloadBufferSize },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setVideoPreloadBufferSize(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.AudioDelayPicker) {
        SettingsSliderSheet(
            title = "Audio Delay",
            value = preferences.audioDelayMs.toFloat(),
            valueRange = -500f..500f,
            steps = 99,
            valueLabel = { if (it.toLong() == 0L) "No delay" else "${it.toLong()}ms" },
            rangeStartLabel = "-500ms",
            rangeEndLabel = "+500ms",
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onConfirm = {
                viewModel.setAudioDelayMs(it.toLong())
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvVideoOutputPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Video Output (vo)",
            items = MpvVideoOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.videoOutput },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(videoOutput = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvScalerPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Scaler (dscale)",
            items = MpvScaler.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.scaler },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(scaler = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvAudioOutputPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Audio Output (ao)",
            items = MpvAudioOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.audioOutput },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(audioOutput = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvAudioFallbackPicker) {
        val mpvCfg = preferences.mpvConfig
        val options = listOf(null) + MpvAudioOutput.entries
        SettingsListPickerSheet(
            title = "Audio Fallback (ao-fallback)",
            items = options,
            label = { it?.displayName ?: "None" },
            subtitle = { it?.key ?: "no fallback" },
            isSelected = { it == mpvCfg.audioFallback },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(audioFallback = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvDemuxerPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Buffer Size (demuxer-max-bytes)",
            items = MpvDemuxerMaxBytes.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.demuxerMaxBytes },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(demuxerMaxBytes = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvHwdecPicker) {
        val mpvCfg = preferences.mpvConfig
        val options = listOf(null) + MpvHwdec.entries
        SettingsListPickerSheet(
            title = "HW Decoder Override (hwdec)",
            items = options,
            label = { it?.displayName ?: "Use universal setting" },
            subtitle = { it?.key ?: "auto" },
            isSelected = { it == mpvCfg.hwdecOverride },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(hwdecOverride = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvSkipLoopFilterPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Skip Loop Filter (vd-lavc-skiploopfilter)",
            items = MpvSkipLoopFilter.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.skipLoopFilter },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(skipLoopFilter = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.MpvFrameDropPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Frame Drop (framedrop)",
            items = MpvFrameDrop.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.frameDrop },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(frameDrop = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VlcAudioOutputPicker) {
        val vlcCfg = preferences.libVlcConfig
        SettingsListPickerSheet(
            title = "Audio Output (aout)",
            items = VlcAudioOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == vlcCfg.audioOutput },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(audioOutput = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VlcNetworkCachingPicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = listOf(0, 500, 1000, 1500, 2000, 3000, 5000)
        SettingsListPickerSheet(
            title = "Network Caching (network-caching)",
            items = options,
            label = { if (it == 0) "Auto (device-based)" else "${it}ms" },
            subtitle = { if (it == 0) "auto" else "${it}ms" },
            isSelected = { it == vlcCfg.networkCaching },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(networkCaching = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VlcSkipLoopFilterPicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = (0..4).toList()
        SettingsListPickerSheet(
            title = "Skip Loop Filter (skiploopfilter)",
            items = options,
            label = { vlcSkipLoopFilterLabel(it) },
            subtitle = { "level $it" },
            isSelected = { it == vlcCfg.skipLoopFilter },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(skipLoopFilter = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VlcSkipFramePicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = (0..4).toList()
        SettingsListPickerSheet(
            title = "Skip Frames (skip-frames)",
            items = options,
            label = { vlcSkipFrameLabel(it) },
            subtitle = { "level $it" },
            isSelected = { it == vlcCfg.skipFrame },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(skipFrame = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.VlcDecoderThreadsPicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = listOf(0, 1, 2, 4, 6, 8)
        SettingsListPickerSheet(
            title = "Decoder Threads (codec-dr-threads)",
            items = options,
            label = { if (it == 0) "Auto" else "$it threads" },
            subtitle = { if (it == 0) "auto" else "$it" },
            isSelected = { it == vlcCfg.decoderThreads },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(decoderThreads = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.ExoScalingPicker) {
        val exoCfg = preferences.exoPlayerConfig
        SettingsListPickerSheet(
            title = "Video Scaling (scalingMode)",
            items = ExoVideoScalingMode.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == exoCfg.videoScalingMode },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(videoScalingMode = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.ExoFrameRatePicker) {
        val exoCfg = preferences.exoPlayerConfig
        SettingsListPickerSheet(
            title = "Frame Rate Strategy (setVideoAspectRatio)",
            items = ExoFrameRateStrategy.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == exoCfg.frameRateStrategy },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(frameRateStrategy = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.ExoAudioOffloadPicker) {
        val exoCfg = preferences.exoPlayerConfig
        SettingsListPickerSheet(
            title = "Audio Offload (audioOffloadMode)",
            items = ExoAudioOffloadMode.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == exoCfg.audioOffloadMode },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(audioOffloadMode = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.ExoBackBufferPicker) {
        val exoCfg = preferences.exoPlayerConfig
        val options = listOf(0, 5000, 10000, 15000, 20000, 30000)
        SettingsListPickerSheet(
            title = "Back Buffer (backBufferDurationMs)",
            items = options,
            label = { if (it == 0) "Disabled" else "${it / 1000}s" },
            subtitle = { if (it == 0) "off" else "${it}ms" },
            isSelected = { it == exoCfg.backBufferDurationMs },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(backBufferDurationMs = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.ExoCodecPicker) {
        val exoCfg = preferences.exoPlayerConfig
        val presets = listOf(
            emptyList<String>(),
            listOf("video/hevc", "video/avc"),
            listOf("video/av1", "video/hevc", "video/avc"),
            listOf("video/avc"),
        )
        val presetLabels = listOf("All codecs", "HEVC + AVC", "AV1 + HEVC + AVC", "AVC only")
        SettingsListPickerSheet(
            title = "Preferred Codecs (preferredVideoMimeTypes)",
            items = presets,
            label = { presetLabels[presets.indexOf(it)] },
            subtitle = { if (it.isEmpty()) "*" else it.joinToString(", ") },
            isSelected = { it == exoCfg.preferredVideoMimeTypes },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(preferredVideoMimeTypes = it))
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.BrightnessPicker) {
        SettingsSliderSheet(
            title = "Default Video Brightness Level",
            value = preferences.videoBrightnessLevel,
            valueRange = 0.0f..1.0f,
            steps = 20,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "0%",
            rangeEndLabel = "100%",
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onConfirm = {
                viewModel.setVideoBrightnessLevel(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.SyncPlayJoinBehaviorPicker) {
        SettingsListPickerSheet(
            title = "Join Behavior",
            items = SyncPlayJoinBehavior.entries,
            label = { it.displayName },
            subtitle = {
                when (it) {
                    SyncPlayJoinBehavior.ALWAYS_JOIN -> "Automatically join active groups"
                    SyncPlayJoinBehavior.ASK -> "Prompt to join when a group is active"
                    SyncPlayJoinBehavior.NEVER_JOIN -> "Ignore group playback sessions"
                }
            },
            isSelected = { it == preferences.syncPlayJoinBehavior },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setSyncPlayJoinBehavior(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.SyncPlayTolerancePicker) {
        val options = listOf(50L, 100L, 200L, 300L, 500L, 1000L)
        SettingsListPickerSheet(
            title = "Sync Tolerance",
            items = options,
            label = { "${it}ms" },
            subtitle = {
                when (it) {
                    50L -> "Tight sync (more network seeks)"
                    100L -> "Balanced sync (recommended)"
                    500L -> "Loose sync (fewer seeks)"
                    else -> "Custom drift limit"
                }
            },
            isSelected = { it == preferences.syncPlayToleranceMs },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setSyncPlayToleranceMs(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.CastingStrategyPicker) {
        SettingsListPickerSheet(
            title = "Casting Strategy",
            items = CastingStrategy.entries,
            label = { it.displayName },
            subtitle = {
                when (it) {
                    CastingStrategy.PREFER_CAST -> "Always try Google Cast protocol first"
                    CastingStrategy.PREFER_DLNA -> "Always try DLNA/UPnP rendering first"
                    CastingStrategy.ASK -> "Show device choice menu when casting"
                }
            },
            isSelected = { it == preferences.defaultCastingStrategy },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setDefaultCastingStrategy(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.DvrPrePaddingPicker) {
        val options = listOf(0, 1, 2, 5, 10, 15)
        SettingsListPickerSheet(
            title = "DVR Pre-Padding",
            items = options,
            label = { if (it == 0) "None" else "$it minutes" },
            subtitle = { if (it == 0) "Start exactly on time" else "Start recording $it minutes early" },
            isSelected = { it == preferences.dvrPrePaddingMinutes },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setDvrPrePaddingMinutes(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.DvrPostPaddingPicker) {
        val options = listOf(0, 1, 2, 5, 10, 15, 30)
        SettingsListPickerSheet(
            title = "DVR Post-Padding",
            items = options,
            label = { if (it == 0) "None" else "$it minutes" },
            subtitle = { if (it == 0) "Stop exactly on time" else "Stop recording $it minutes late" },
            isSelected = { it == preferences.dvrPostPaddingMinutes },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setDvrPostPaddingMinutes(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.DvrRecordingQualityPicker) {
        val options = listOf("AUTO", "HIGH", "MEDIUM", "LOW")
        SettingsListPickerSheet(
            title = "DVR Recording Quality",
            items = options,
            label = { it },
            subtitle = {
                when (it) {
                    "AUTO" -> "Original broadcast stream quality"
                    "HIGH" -> "Transcode to High quality (1080p)"
                    "MEDIUM" -> "Transcode to Medium quality (720p)"
                    "LOW" -> "Transcode to Low quality (480p)"
                    else -> ""
                }
            },
            isSelected = { it == preferences.dvrRecordingQuality },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setDvrRecordingQuality(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }


    if (activeDialog is PlaybackSettingsDialog.AutoPlayCountdownPicker) {
        val options = listOf(0, 5, 10, 15)
        SettingsListPickerSheet(
            title = "Auto-Play Countdown",
            items = options,
            label = { if (it == 0) "Off" else "${it}s" },
            subtitle = { if (it == 0) "Play next item immediately" else "Show countdown screen for $it seconds" },
            isSelected = { it == preferences.autoPlayCountdownSec },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setAutoPlayCountdownSec(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }

    if (activeDialog is PlaybackSettingsDialog.TvZoomModePicker) {
        val percents = listOf(0f, 5f, 10f, 15f, 20f, 25f, 33f, 50f)
        SettingsListPickerSheet(
            title = "TV Zoom Mode",
            items = percents,
            label = { if (it == 0f) "Off" else "${it.toInt()}%" },
            subtitle = { percent ->
                if (percent == 0f) "No zoom (original aspect ratio)"
                else "Crop and zoom video by ${percent.toInt()}% to fill screen"
            },
            isSelected = { it == preferences.tvZoomModePercent },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setTvZoomModePercent(it)
                activeDialog = PlaybackSettingsDialog.None
            },
        )
    }
}
