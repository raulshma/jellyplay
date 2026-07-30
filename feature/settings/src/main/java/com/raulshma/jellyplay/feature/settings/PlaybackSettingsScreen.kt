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
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
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

private fun liveStreamOptionLabel(option: LiveStreamOption): String = when (option) {
    LiveStreamOption.AUTO -> "Auto (server decides)"
    LiveStreamOption.DIRECT_STREAM -> "Direct Stream (best quality)"
    LiveStreamOption.TRANSCODE -> "Transcode (lower bandwidth)"
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
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
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
                        onClick = {
                            activePicker = PickerState.List(
                                title = "Preferred Player",
                                items = PlayerType.entries,
                                label = { it.displayName },
                                subtitle = { it.description },
                                isSelected = { it == preferences.preferredPlayer },
                                onSelect = { viewModel.setPreferredPlayer(it) },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.PlayerTrackNext,
                        title = stringResource(R.string.settings_seek_duration),
                        subtitle = stringResource(R.string.settings_seek_duration_subtitle),
                        trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                        highlighted = highlightSettingId == "seek_duration",
                        index = idx++, count = total,
                        onClick = {
                            val durations = listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L, 60_000L)
                            activePicker = pickerChip(
                                title = "Double-Tap Seek Duration",
                                values = durations,
                                current = preferences.videoSeekDurationMs,
                                label = { "${it / 1000}s" },
                                onSelect = viewModel::setVideoSeekDurationMs,
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobileRotated,
                        title = stringResource(R.string.settings_orientation),
                        subtitle = stringResource(R.string.settings_orientation_subtitle),
                        trailingText = preferences.videoDefaultOrientation.displayName,
                        highlighted = highlightSettingId == "orientation",
                        index = idx++, count = total,
                        onClick = {
                            activePicker = PickerState.List(
                                title = "Default Orientation",
                                items = OrientationMode.entries,
                                label = { it.displayName },
                                subtitle = { it.constant },
                                isSelected = { it == preferences.videoDefaultOrientation },
                                onSelect = { viewModel.setVideoDefaultOrientation(it) },
                            )
                        },
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
                        onClick = {
                            activePicker = PickerState.List(
                                title = "Gesture Indicator Side",
                                items = GestureIndicatorSide.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.videoGestureIndicatorSide },
                                onSelect = { viewModel.setVideoGestureIndicatorSide(it) },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(R.string.settings_default_speed),
                        subtitle = stringResource(R.string.settings_default_speed_subtitle),
                        trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        highlighted = highlightSettingId == "default_speed",
                        index = idx++, count = total,
                        onClick = {
                            val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                            activePicker = pickerChip(
                                title = "Default Video Speed",
                                values = speeds,
                                current = preferences.videoDefaultSpeed,
                                label = { if (it == 1.0f) "1x" else "${it}x" },
                                onSelect = viewModel::setVideoDefaultSpeed,
                            )
                        },
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
                        onClick = {
                            val multipliers = listOf(1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                            activePicker = pickerChip(
                                title = "Hold-to-Seek Speed",
                                values = multipliers,
                                current = preferences.videoHoldSpeedMultiplier,
                                label = { "${it}x" },
                                defaultIndex = 1,
                                onSelect = viewModel::setVideoHoldSpeedMultiplier,
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.ArrowAutofitHeight,
                        title = stringResource(R.string.settings_default_aspect),
                        subtitle = stringResource(R.string.settings_default_aspect_subtitle),
                        trailingText = preferences.videoDefaultAspectRatio,
                        highlighted = highlightSettingId == "default_aspect",
                        index = idx++, count = total,
                        onClick = {
                            val aspectRatios = listOf("AUTO", "FIT", "FILL", "CROP", "16:9", "4:3", "21:9")
                            activePicker = PickerState.List(
                                title = "Default Aspect Ratio",
                                items = aspectRatios,
                                label = { it },
                                isSelected = { it == preferences.videoDefaultAspectRatio },
                                onSelect = { viewModel.setVideoDefaultAspectRatio(it) },
                            )
                        },
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
                        onClick = {
                            val options = listOf(0, 5, 10, 15)
                            activePicker = PickerState.List(
                                title = "Auto-Play Countdown",
                                items = options,
                                label = { if (it == 0) "Off" else "${it}s" },
                                subtitle = { if (it == 0) "Play next item immediately" else "Show countdown screen for $it seconds" },
                                isSelected = { it == preferences.autoPlayCountdownSec },
                                onSelect = { viewModel.setAutoPlayCountdownSec(it) },
                            )
                        },
                    )
                    if (showAdvanced) {
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(R.string.settings_controls_timeout),
                            subtitle = stringResource(R.string.settings_controls_timeout_subtitle),
                            trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                            highlighted = highlightSettingId == "controls_timeout",
                            index = idx++, count = total,
                            onClick = {
                                val timeouts = listOf(3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
                                activePicker = pickerChip(
                                    title = "Controls Auto-Hide Timeout",
                                    values = timeouts,
                                    current = preferences.videoControlsTimeoutMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = viewModel::setVideoControlsTimeoutMs,
                                )
                            },
                        )
                        val skipBackLabel = if (preferences.videoSkipBackOnResumeMs == 0L) "Off" else "${preferences.videoSkipBackOnResumeMs / 1000}s"
                        SettingListItem(
                            icon = Tabler.Outline.History,
                            title = "Skip Back on Resume",
                            subtitle = "Jump back when un-pausing playback",
                            trailingText = skipBackLabel,
                            highlighted = highlightSettingId == "skip_back_on_resume",
                            index = idx++, count = total,
                            onClick = {
                                val durations = listOf(0L, 3_000L, 5_000L, 10_000L, 15_000L, 30_000L)
                                activePicker = pickerChip(
                                    title = "Skip Back on Resume",
                                    values = durations,
                                    current = preferences.videoSkipBackOnResumeMs,
                                    label = { if (it == 0L) "Off" else "${it / 1000}s" },
                                    onSelect = viewModel::setVideoSkipBackOnResumeMs,
                                )
                            },
                        )
                        val passOutLabel = if (preferences.videoPassOutProtectionHours == 0) "Off" else "${preferences.videoPassOutProtectionHours}h"
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = "Pass-out Protection",
                            subtitle = "Pause playback after no interaction",
                            trailingText = passOutLabel,
                            highlighted = highlightSettingId == "pass_out_protection",
                            index = idx++, count = total,
                            onClick = {
                                val hours = listOf(0, 1, 2, 3, 4, 6, 8)
                                activePicker = pickerChip(
                                    title = "Pass-out protection",
                                    values = hours,
                                    current = preferences.videoPassOutProtectionHours,
                                    label = { if (it == 0) "Off" else "${it}h" },
                                    onSelect = viewModel::setVideoPassOutProtectionHours,
                                )
                            },
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
                                onClick = {
                                    val percents = listOf(0f, 5f, 10f, 15f, 20f, 25f, 33f, 50f)
                                    activePicker = PickerState.List(
                                        title = "TV Zoom Mode",
                                        items = percents,
                                        label = { if (it == 0f) "Off" else "${it.toInt()}%" },
                                        subtitle = { percent ->
                                            if (percent == 0f) "No zoom (original aspect ratio)"
                                            else "Crop and zoom video by ${percent.toInt()}% to fill screen"
                                        },
                                        isSelected = { it == preferences.tvZoomModePercent },
                                        onSelect = { viewModel.setTvZoomModePercent(it) },
                                    )
                                },
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
                            onClick = {
                                val ranges = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L, 300_000L)
                                activePicker = pickerChip(
                                    title = "Swipe Seek Maximum Range",
                                    values = ranges,
                                    current = preferences.videoSwipeSeekMaxMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = viewModel::setVideoSwipeSeekMaxMs,
                                )
                            },
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
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = "Default Video Brightness Level",
                                    value = preferences.videoBrightnessLevel,
                                    valueRange = 0.0f..1.0f,
                                    steps = 20,
                                    valueLabel = { "${(it * 100).toInt()}%" },
                                    rangeStartLabel = "0%",
                                    rangeEndLabel = "100%",
                                    onConfirm = { viewModel.setVideoBrightnessLevel(it) },
                                )
                            },
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
                            onClick = {
                                activePicker = PickerState.List(
                                    title = "Preload Buffer Size",
                                    items = PreloadBufferSize.entries,
                                    label = { it.displayName },
                                    subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
                                    isSelected = { it == preferences.videoPreloadBufferSize },
                                    onSelect = { viewModel.setVideoPreloadBufferSize(it) },
                                )
                            },
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
                                activePicker = pickerChip(
                                    title = "Dialogue Boost Strength",
                                    values = strengths,
                                    current = preferences.dialogueBoostStrength,
                                    label = { it.displayName },
                                    onSelect = viewModel::setDialogueBoostStrength,
                                )
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
                            activePicker = PickerState.List(
                                title = "Decoder",
                                items = DecoderMode.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.decoderMode },
                                onSelect = { viewModel.setDecoderMode(it) },
                            )
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
                    SettingListItem(
                        icon = Tabler.Outline.Maximize,
                        title = "Refresh Rate Match",
                        subtitle = preferences.refreshRateMode.displayName +
                            if (preferences.refreshRateMode == com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
                                " (resolution switching)" else "",
                        trailingText = when (preferences.refreshRateMode) {
                            com.raulshma.jellyplay.core.model.RefreshRateMode.OFF -> "Off"
                            com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_ONLY -> "Rate"
                            com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION -> "Rate+Res"
                        },
                        highlighted = highlightSettingId == "frame_rate_matching",
                        index = idx++, count = total,
                        onClick = {
                            activePicker = PickerState.List(
                                title = "Refresh Rate Matching",
                                items = com.raulshma.jellyplay.core.model.RefreshRateMode.entries,
                                label = { it.displayName },
                                subtitle = {
                                    when (it) {
                                        com.raulshma.jellyplay.core.model.RefreshRateMode.OFF -> "No display mode switch"
                                        com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_ONLY -> "Match frame rate at current resolution"
                                        com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION -> "Match frame rate and switch resolution"
                                    }
                                },
                                isSelected = { it == preferences.refreshRateMode },
                                onSelect = { viewModel.setRefreshRateMode(it) },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = "Streaming Quality",
                        subtitle = streamingQualityLabel(preferences.streamingQuality),
                        trailingText = streamingQualityShort(preferences.streamingQuality),
                        highlighted = highlightSettingId == "streaming_quality",
                        index = idx++, count = total,
                        onClick = {
                            activePicker = PickerState.List(
                                title = "Streaming Quality",
                                items = StreamingQuality.entries,
                                label = { streamingQualityLabel(it) },
                                isSelected = { it == preferences.streamingQuality },
                                onSelect = { viewModel.setStreamingQuality(it) },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.DeviceTv,
                        title = "Live TV Stream",
                        subtitle = liveStreamOptionLabel(preferences.liveStreamOption),
                        trailingText = preferences.liveStreamOption.displayName,
                        highlighted = highlightSettingId == "live_stream_option",
                        index = idx++, count = total,
                        onClick = {
                            activePicker = PickerState.List(
                                title = "Live TV Stream",
                                items = LiveStreamOption.entries,
                                label = { liveStreamOptionLabel(it) },
                                isSelected = { it == preferences.liveStreamOption },
                                onSelect = { viewModel.setLiveStreamOption(it) },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = "Audio Delay",
                        subtitle = if (preferences.audioDelayMs == 0L) "No audio delay" else "${preferences.audioDelayMs}ms delay",
                        trailingText = if (preferences.audioDelayMs == 0L) "Off" else "${preferences.audioDelayMs}ms",
                        highlighted = highlightSettingId == "audio_delay",
                        index = idx, count = total,
                        onClick = {
                            activePicker = PickerState.Slider(
                                title = "Audio Delay",
                                value = preferences.audioDelayMs.toFloat(),
                                valueRange = -500f..500f,
                                steps = 99,
                                valueLabel = { if (it.toLong() == 0L) "No delay" else "${it.toLong()}ms" },
                                rangeStartLabel = "-500ms",
                                rangeEndLabel = "+500ms",
                                onConfirm = { viewModel.setAudioDelayMs(it.toLong()) },
                            )
                        },
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
                            val mpvTotal = 12
                            var mpvIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.Video,
                                title = "Video Output",
                                subtitle = "${mpvCfg.videoOutput.displayName} (${mpvCfg.videoOutput.key})",
                                trailingText = mpvCfg.videoOutput.key,
                                highlighted = highlightSettingId == "mpv_video_output",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Video Output (vo)",
                                        items = MpvVideoOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.videoOutput },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(videoOutput = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = "Scaler",
                                subtitle = "${mpvCfg.scaler.displayName} (${mpvCfg.scaler.key})",
                                trailingText = mpvCfg.scaler.key,
                                highlighted = highlightSettingId == "mpv_scaler",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Scaler (dscale)",
                                        items = MpvScaler.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.scaler },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(scaler = it)) },
                                    )
                                },
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
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Audio Output (ao)",
                                        items = MpvAudioOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.audioOutput },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(audioOutput = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowBack,
                                title = "Audio Fallback",
                                subtitle = mpvCfg.audioFallback?.displayName ?: "None",
                                trailingText = mpvCfg.audioFallback?.key ?: "None",
                                highlighted = highlightSettingId == "mpv_audio_fallback",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    val options = listOf(null) + MpvAudioOutput.entries
                                    activePicker = PickerState.List(
                                        title = "Audio Fallback (ao-fallback)",
                                        items = options,
                                        label = { it?.displayName ?: "None" },
                                        subtitle = { it?.key ?: "no fallback" },
                                        isSelected = { it == mpvCfg.audioFallback },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(audioFallback = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Buffer Size",
                                subtitle = "${mpvCfg.demuxerMaxBytes.displayName} (${mpvCfg.demuxerMaxBytes.key})",
                                trailingText = mpvCfg.demuxerMaxBytes.key,
                                highlighted = highlightSettingId == "mpv_buffer_size",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Buffer Size (demuxer-max-bytes)",
                                        items = MpvDemuxerMaxBytes.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.demuxerMaxBytes },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(demuxerMaxBytes = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "HW Dec Override",
                                subtitle = mpvCfg.hwdecOverride?.displayName ?: "Use universal setting",
                                trailingText = mpvCfg.hwdecOverride?.key ?: "Auto",
                                highlighted = highlightSettingId == "mpv_hwdec_override",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    val options = listOf(null) + MpvHwdec.entries
                                    activePicker = PickerState.List(
                                        title = "HW Decoder Override (hwdec)",
                                        items = options,
                                        label = { it?.displayName ?: "Use universal setting" },
                                        subtitle = { it?.key ?: "auto" },
                                        isSelected = { it == mpvCfg.hwdecOverride },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(hwdecOverride = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = "${mpvCfg.skipLoopFilter.displayName} (${mpvCfg.skipLoopFilter.key})",
                                trailingText = mpvCfg.skipLoopFilter.key,
                                highlighted = highlightSettingId == "mpv_skip_loop_filter",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Skip Loop Filter (vd-lavc-skiploopfilter)",
                                        items = MpvSkipLoopFilter.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.skipLoopFilter },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(skipLoopFilter = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.PhotoDown,
                                title = "Frame Drop",
                                subtitle = "${mpvCfg.frameDrop.displayName} (${mpvCfg.frameDrop.key})",
                                trailingText = mpvCfg.frameDrop.key,
                                highlighted = highlightSettingId == "mpv_frame_drop",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Frame Drop (framedrop)",
                                        items = MpvFrameDrop.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.frameDrop },
                                        onSelect = { viewModel.setMpvConfig(mpvCfg.copy(frameDrop = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Code,
                                title = "Advanced Configuration",
                                subtitle = if (mpvCfg.mpvExtraConfig.isBlank()) {
                                    "Raw mpv options (mpv.conf style)"
                                } else {
                                    "${parseMpvConfigOptions(mpvCfg.mpvExtraConfig).size} custom option(s)"
                                },
                                trailingText = "mpv.conf",
                                highlighted = highlightSettingId == "mpv_extra_config",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = {
                                    activePicker = PickerState.Text(
                                        title = "Advanced MPV Configuration",
                                        initialText = mpvCfg.mpvExtraConfig,
                                        helperText = "One option per line (key=value). Applied after the settings above, " +
                                            "so a value here overrides its structured counterpart. Blank lines and # " +
                                            "comments are ignored. Examples:\n" +
                                            "scale=ewa_lanczossharp\ntscale=mitchell\ntone-mapping=bt.2390",
                                        onSave = { viewModel.setMpvConfig(mpvCfg.copy(mpvExtraConfig = it)) },
                                    )
                                },
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
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Audio Output (aout)",
                                        items = VlcAudioOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == vlcCfg.audioOutput },
                                        onSelect = { viewModel.setLibVlcConfig(vlcCfg.copy(audioOutput = it)) },
                                    )
                                },
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
                                onClick = {
                                    val options = listOf(0, 500, 1000, 1500, 2000, 3000, 5000)
                                    activePicker = PickerState.List(
                                        title = "Network Caching (network-caching)",
                                        items = options,
                                        label = { if (it == 0) "Auto (device-based)" else "${it}ms" },
                                        subtitle = { if (it == 0) "auto" else "${it}ms" },
                                        isSelected = { it == vlcCfg.networkCaching },
                                        onSelect = { viewModel.setLibVlcConfig(vlcCfg.copy(networkCaching = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = vlcSkipLoopFilterLabel(vlcCfg.skipLoopFilter),
                                trailingText = "Level ${vlcCfg.skipLoopFilter}",
                                highlighted = highlightSettingId == "vlc_skip_loop_filter",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = {
                                    val options = (0..4).toList()
                                    activePicker = PickerState.List(
                                        title = "Skip Loop Filter (skiploopfilter)",
                                        items = options,
                                        label = { vlcSkipLoopFilterLabel(it) },
                                        subtitle = { "level $it" },
                                        isSelected = { it == vlcCfg.skipLoopFilter },
                                        onSelect = { viewModel.setLibVlcConfig(vlcCfg.copy(skipLoopFilter = it)) },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = "Skip Frames",
                                subtitle = if (vlcCfg.skipFrames) "Enable frame skipping" else "No frame skipping",
                                checked = vlcCfg.skipFrames,
                                highlighted = highlightSettingId == "vlc_skip_frames",
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(skipFrames = it)) },
                                onClick = {
                                    val options = (0..4).toList()
                                    activePicker = PickerState.List(
                                        title = "Skip Frames (skip-frames)",
                                        items = options,
                                        label = { vlcSkipFrameLabel(it) },
                                        subtitle = { "level $it" },
                                        isSelected = { it == vlcCfg.skipFrame },
                                        onSelect = { viewModel.setLibVlcConfig(vlcCfg.copy(skipFrame = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "Decoder Threads",
                                subtitle = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads} threads",
                                trailingText = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads}",
                                highlighted = highlightSettingId == "vlc_decoder_threads",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = {
                                    val options = listOf(0, 1, 2, 4, 6, 8)
                                    activePicker = PickerState.List(
                                        title = "Decoder Threads (codec-dr-threads)",
                                        items = options,
                                        label = { if (it == 0) "Auto" else "$it threads" },
                                        subtitle = { if (it == 0) "auto" else "$it" },
                                        isSelected = { it == vlcCfg.decoderThreads },
                                        onSelect = { viewModel.setLibVlcConfig(vlcCfg.copy(decoderThreads = it)) },
                                    )
                                },
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
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Video Scaling (scalingMode)",
                                        items = ExoVideoScalingMode.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == exoCfg.videoScalingMode },
                                        onSelect = { viewModel.setExoPlayerConfig(exoCfg.copy(videoScalingMode = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Clock,
                                title = "Frame Rate Strategy",
                                subtitle = "${exoCfg.frameRateStrategy.displayName} (${exoCfg.frameRateStrategy.key})",
                                trailingText = exoCfg.frameRateStrategy.key,
                                highlighted = highlightSettingId == "exo_frame_rate_strategy",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Frame Rate Strategy (setVideoAspectRatio)",
                                        items = ExoFrameRateStrategy.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == exoCfg.frameRateStrategy },
                                        onSelect = { viewModel.setExoPlayerConfig(exoCfg.copy(frameRateStrategy = it)) },
                                    )
                                },
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
                                onClick = {
                                    activePicker = PickerState.List(
                                        title = "Audio Offload (audioOffloadMode)",
                                        items = ExoAudioOffloadMode.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == exoCfg.audioOffloadMode },
                                        onSelect = { viewModel.setExoPlayerConfig(exoCfg.copy(audioOffloadMode = it)) },
                                    )
                                },
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
                                onClick = {
                                    val options = listOf(0, 5000, 10000, 15000, 20000, 30000)
                                    activePicker = PickerState.List(
                                        title = "Back Buffer (backBufferDurationMs)",
                                        items = options,
                                        label = { if (it == 0) "Disabled" else "${it / 1000}s" },
                                        subtitle = { if (it == 0) "off" else "${it}ms" },
                                        isSelected = { it == exoCfg.backBufferDurationMs },
                                        onSelect = { viewModel.setExoPlayerConfig(exoCfg.copy(backBufferDurationMs = it)) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Code,
                                title = "Preferred Codecs",
                                subtitle = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All codecs" else exoCfg.preferredVideoMimeTypes.joinToString(", "),
                                trailingText = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All" else "Custom",
                                highlighted = highlightSettingId == "exo_preferred_codecs",
                                index = exoIdx++, count = exoTotal,
                                onClick = {
                                    val presets = listOf(
                                        emptyList<String>(),
                                        listOf("video/hevc", "video/avc"),
                                        listOf("video/av1", "video/hevc", "video/avc"),
                                        listOf("video/avc"),
                                    )
                                    val presetLabels = listOf("All codecs", "HEVC + AVC", "AV1 + HEVC + AVC", "AVC only")
                                    activePicker = PickerState.List(
                                        title = "Preferred Codecs (preferredVideoMimeTypes)",
                                        items = presets,
                                        label = { presetLabels[presets.indexOf(it)] },
                                        subtitle = { if (it.isEmpty()) "*" else it.joinToString(", ") },
                                        isSelected = { it == exoCfg.preferredVideoMimeTypes },
                                        onSelect = { viewModel.setExoPlayerConfig(exoCfg.copy(preferredVideoMimeTypes = it)) },
                                    )
                                },
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
                                activePicker = PickerState.List(
                                    title = type.displayName,
                                    items = behaviors,
                                    label = { it.displayName },
                                    subtitle = { it.description },
                                    isSelected = { it == behavior },
                                    onSelect = { viewModel.setSegmentBehavior(type, it) },
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
                        onClick = {
                            activePicker = PickerState.List(
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
                                onSelect = { viewModel.setSyncPlayJoinBehavior(it) },
                            )
                        },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.WaveSine,
                        title = "Sync Tolerance",
                        subtitle = "Allowed drift before correcting playback",
                        trailingText = "${preferences.syncPlayToleranceMs}ms",
                        highlighted = highlightSettingId == "syncplay_tolerance",
                        index = syncIdx++, count = syncTotal,
                        onClick = {
                            val options = listOf(50L, 100L, 200L, 300L, 500L, 1000L)
                            activePicker = PickerState.List(
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
                                onSelect = { viewModel.setSyncPlayToleranceMs(it) },
                            )
                        },
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
                        onClick = {
                            activePicker = PickerState.List(
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
                                onSelect = { viewModel.setDefaultCastingStrategy(it) },
                            )
                        },
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
                        onClick = {
                            val options = listOf(0, 1, 2, 5, 10, 15)
                            activePicker = PickerState.List(
                                title = "DVR Pre-Padding",
                                items = options,
                                label = { if (it == 0) "None" else "$it minutes" },
                                subtitle = { if (it == 0) "Start exactly on time" else "Start recording $it minutes early" },
                                isSelected = { it == preferences.dvrPrePaddingMinutes },
                                onSelect = { viewModel.setDvrPrePaddingMinutes(it) },
                            )
                        },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = "DVR Post-Padding",
                        subtitle = "Extend recording after scheduled end time",
                        trailingText = "${preferences.dvrPostPaddingMinutes} min",
                        highlighted = highlightSettingId == "dvr_post_padding",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = {
                            val options = listOf(0, 1, 2, 5, 10, 15, 30)
                            activePicker = PickerState.List(
                                title = "DVR Post-Padding",
                                items = options,
                                label = { if (it == 0) "None" else "$it minutes" },
                                subtitle = { if (it == 0) "Stop exactly on time" else "Stop recording $it minutes late" },
                                isSelected = { it == preferences.dvrPostPaddingMinutes },
                                onSelect = { viewModel.setDvrPostPaddingMinutes(it) },
                            )
                        },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Video,
                        title = "DVR Recording Quality",
                        subtitle = "Default video quality for DVR recordings",
                        trailingText = preferences.dvrRecordingQuality,
                        highlighted = highlightSettingId == "dvr_recording_quality",
                        index = dvrIdx++, count = dvrTotal,
                        onClick = {
                            val options = listOf("AUTO", "HIGH", "MEDIUM", "LOW")
                            activePicker = PickerState.List(
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
                                onSelect = { viewModel.setDvrRecordingQuality(it) },
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

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
