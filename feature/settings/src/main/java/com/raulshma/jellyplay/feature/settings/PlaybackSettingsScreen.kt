package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
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
import com.raulshma.jellyplay.core.model.VlcVideoOutput
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
    object VideoSeekDurationPicker : PlaybackSettingsDialog()
    object ControlsTimeoutPicker : PlaybackSettingsDialog()
    object SwipeSeekPicker : PlaybackSettingsDialog()
    object PreloadBufferPicker : PlaybackSettingsDialog()
    object AudioDelayPicker : PlaybackSettingsDialog()
    object MpvVideoOutputPicker : PlaybackSettingsDialog()
    object MpvScalerPicker : PlaybackSettingsDialog()
    object MpvAudioOutputPicker : PlaybackSettingsDialog()
    object MpvAudioFallbackPicker : PlaybackSettingsDialog()
    object MpvDemuxerPicker : PlaybackSettingsDialog()
    object MpvHwdecPicker : PlaybackSettingsDialog()
    object MpvSkipLoopFilterPicker : PlaybackSettingsDialog()
    object MpvFrameDropPicker : PlaybackSettingsDialog()
    object VlcAudioOutputPicker : PlaybackSettingsDialog()
    object VlcVideoOutputPicker : PlaybackSettingsDialog()
    object VlcNetworkCachingPicker : PlaybackSettingsDialog()
    object VlcSkipLoopFilterPicker : PlaybackSettingsDialog()
    object VlcSkipFramePicker : PlaybackSettingsDialog()
    object VlcDecoderThreadsPicker : PlaybackSettingsDialog()
    object ExoScalingPicker : PlaybackSettingsDialog()
    object ExoFrameRatePicker : PlaybackSettingsDialog()
    object ExoAudioOffloadPicker : PlaybackSettingsDialog()
    object ExoBackBufferPicker : PlaybackSettingsDialog()
    object ExoCodecPicker : PlaybackSettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<PlaybackSettingsDialog>(PlaybackSettingsDialog.None) }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Playback",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerPlay,
                    title = "Video Player",
                    summary = { "Player Engine: ${preferences.preferredPlayer.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val videoItems = buildList {
                        add("player" to 0)
                        add("seek" to 1)
                        add("orientation" to 2)
                        add("timeout" to 3)
                        add("gestures" to 4)
                        add("speed" to 5)
                        add("aspect" to 6)
                        add("autoplay" to 7)
                        add("autoplayTrailers" to 8)
                        add("browser" to 9)
                        add("metadata" to 10)
                        add("swipe" to 11)
                        add("brightness" to 12)
                        add("trickplay" to 13)
                        add("trickplayGesture" to 14)
                        add("preload" to 15)
                    }
                    val total = videoItems.size

                    SettingListItem(
                        icon = Tabler.Outline.PlayerPlay,
                        title = "Player Engine",
                        subtitle = "Choose media playback engine",
                        trailingText = preferences.preferredPlayer.displayName,
                        index = 0, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.PlayerPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.PlayerTrackNext,
                        title = "Seek Duration",
                        subtitle = "Double-tap to seek",
                        trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                        index = 1, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.VideoSeekDurationPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobileRotated,
                        title = "Orientation",
                        subtitle = "Default screen orientation",
                        trailingText = preferences.videoDefaultOrientation.displayName,
                        index = 2, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.OrientationPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = "Controls Timeout",
                        subtitle = "Auto-hide player controls",
                        trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                        index = 3, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.ControlsTimeoutPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandMove,
                        title = "Gestures",
                        subtitle = if (preferences.videoGesturesEnabled) "Swipe and tap gestures active" else "Touch gestures disabled",
                        checked = preferences.videoGesturesEnabled,
                        index = 4, count = total,
                        onCheckedChange = { viewModel.setVideoGesturesEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Default Speed",
                        subtitle = "Initial playback speed",
                        trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        index = 5, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.VideoSpeedPicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.ArrowAutofitHeight,
                        title = "Default Aspect",
                        subtitle = "Video aspect ratio",
                        trailingText = preferences.videoDefaultAspectRatio,
                        index = 6, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.AspectRatioPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = "Auto-play Next",
                        subtitle = if (preferences.videoAutoplayNext) "Automatically plays next episode" else "Manual episode selection",
                        checked = preferences.videoAutoplayNext,
                        index = 7, count = total,
                        onCheckedChange = { viewModel.setVideoAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Clipboard,
                        title = "Autoplay Trailers",
                        subtitle = if (preferences.trailerAutoplay) "Trailers play automatically" else "Trailers require manual play",
                        checked = preferences.trailerAutoplay,
                        index = 8, count = total,
                        onCheckedChange = { viewModel.setTrailerAutoplay(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.List,
                        title = "Episode Browser",
                        subtitle = if (preferences.videoEpisodeBrowserEnabled) "Show episode list during playback" else "Hide episode list",
                        checked = preferences.videoEpisodeBrowserEnabled,
                        index = 9, count = total,
                        onCheckedChange = { viewModel.setVideoEpisodeBrowserEnabled(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.InfoCircle,
                        title = "Playback Metadata",
                        subtitle = if (preferences.videoShowPlaybackMetadata) "Show codec and stream info" else "Hide codec info",
                        checked = preferences.videoShowPlaybackMetadata,
                        index = 10, count = total,
                        onCheckedChange = { viewModel.setVideoShowPlaybackMetadata(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.ArrowBarRight,
                        title = "Swipe Seek Range",
                        subtitle = "Maximum seek range for swipe gesture",
                        trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                        index = 11, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.SwipeSeekPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.BrightnessHalf,
                        title = "Remember Brightness",
                        subtitle = if (preferences.videoRememberBrightness) "Brightness saved between sessions" else "Reset brightness each session",
                        checked = preferences.videoRememberBrightness,
                        index = 12, count = total,
                        onCheckedChange = { enabled ->
                            viewModel.setVideoRememberBrightness(enabled)
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Photo,
                        title = "Trickplay Preview",
                        subtitle = if (preferences.trickplayEnabled) "Show thumbnails on seek bar" else "No seek bar thumbnails",
                        checked = preferences.trickplayEnabled,
                        index = 13, count = total,
                        onCheckedChange = { viewModel.setTrickplayEnabled(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandMove,
                        title = "Trickplay on Gestures",
                        subtitle = if (preferences.trickplayOnSeekGesture) "Show thumbnails during swipe seek" else "No thumbnails during seek",
                        checked = preferences.trickplayOnSeekGesture,
                        index = 14, count = total,
                        onCheckedChange = { viewModel.setTrickplayOnSeekGesture(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Preload Buffer",
                        subtitle = "Amount to buffer ahead during playback",
                        trailingText = preferences.videoPreloadBufferSize.displayName,
                        index = 15, count = total,
                        onClick = { activeDialog = PlaybackSettingsDialog.PreloadBufferPicker },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.BadgeHd,
                    title = "Advanced Video",
                    summary = { "Decoder: ${preferences.decoderMode.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val advancedItems = mutableListOf<Pair<String, Int>>()
                    advancedItems.add("dialogue" to 0)
                    if (preferences.dialogueBoostEnabled) {
                        advancedItems.add("dialogueStrength" to advancedItems.size)
                    }
                    advancedItems.add("decoder" to advancedItems.size)
                    advancedItems.add("passthrough" to advancedItems.size)
                    advancedItems.add("framerate" to advancedItems.size)
                    advancedItems.add("quality" to advancedItems.size)
                    advancedItems.add("delay" to advancedItems.size)
                    val total = advancedItems.size

                    var idx = 0
                    SettingToggleItem(
                        icon = Tabler.Outline.Microphone2,
                        title = "Dialogue Boost",
                        subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                        checked = preferences.dialogueBoostEnabled,
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
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioPassthrough(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Maximize,
                        title = "Frame Rate Match",
                        subtitle = if (preferences.frameRateMatching) "Display refresh matches content" else "Fixed display refresh rate",
                        checked = preferences.frameRateMatching,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setFrameRateMatching(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = "Streaming Quality",
                        subtitle = streamingQualityLabel(preferences.streamingQuality),
                        trailingText = streamingQualityShort(preferences.streamingQuality),
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
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvVideoOutputPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = "Scaler",
                                subtitle = "${mpvCfg.scaler.displayName} (${mpvCfg.scaler.key})",
                                trailingText = mpvCfg.scaler.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvScalerPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ColorFilter,
                                title = "Debanding",
                                subtitle = if (mpvCfg.deband) "GPU debanding enabled" else "No debanding",
                                checked = mpvCfg.deband,
                                index = mpvIdx++, count = mpvTotal,
                                onCheckedChange = { viewModel.setMpvConfig(mpvCfg.copy(deband = it)) },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ArrowsHorizontal,
                                title = "Interpolation",
                                subtitle = if (mpvCfg.interpolation) "Smooth motion for mixed FPS" else "No frame interpolation",
                                checked = mpvCfg.interpolation,
                                index = mpvIdx++, count = mpvTotal,
                                onCheckedChange = { viewModel.setMpvConfig(mpvCfg.copy(interpolation = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = "Audio Output",
                                subtitle = "${mpvCfg.audioOutput.displayName} (${mpvCfg.audioOutput.key})",
                                trailingText = mpvCfg.audioOutput.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvAudioOutputPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowBack,
                                title = "Audio Fallback",
                                subtitle = mpvCfg.audioFallback?.displayName ?: "None",
                                trailingText = mpvCfg.audioFallback?.key ?: "None",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvAudioFallbackPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Buffer Size",
                                subtitle = "${mpvCfg.demuxerMaxBytes.displayName} (${mpvCfg.demuxerMaxBytes.key})",
                                trailingText = mpvCfg.demuxerMaxBytes.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvDemuxerPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "HW Dec Override",
                                subtitle = mpvCfg.hwdecOverride?.displayName ?: "Use universal setting",
                                trailingText = mpvCfg.hwdecOverride?.key ?: "Auto",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvHwdecPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = "${mpvCfg.skipLoopFilter.displayName} (${mpvCfg.skipLoopFilter.key})",
                                trailingText = mpvCfg.skipLoopFilter.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.MpvSkipLoopFilterPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.PhotoDown,
                                title = "Frame Drop",
                                subtitle = "${mpvCfg.frameDrop.displayName} (${mpvCfg.frameDrop.key})",
                                trailingText = mpvCfg.frameDrop.key,
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
                            val vlcTotal = 9
                            var vlcIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = "Audio Output",
                                subtitle = "${vlcCfg.audioOutput.displayName} (${vlcCfg.audioOutput.key})",
                                trailingText = vlcCfg.audioOutput.key,
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcAudioOutputPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Clock,
                                title = "Audio Time Stretch",
                                subtitle = if (vlcCfg.audioTimeStretch) "Pitch-corrected speed change" else "Speed changes affect pitch",
                                checked = vlcCfg.audioTimeStretch,
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(audioTimeStretch = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Video,
                                title = "Video Output",
                                subtitle = "${vlcCfg.videoOutput.displayName} (${vlcCfg.videoOutput.key})",
                                trailingText = vlcCfg.videoOutput.key,
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcVideoOutputPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Wifi,
                                title = "Network Caching",
                                subtitle = if (vlcCfg.networkCaching == 0) "Auto (device-based)" else "${vlcCfg.networkCaching}ms",
                                trailingText = if (vlcCfg.networkCaching == 0) "Auto" else "${vlcCfg.networkCaching}ms",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcNetworkCachingPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = vlcSkipLoopFilterLabel(vlcCfg.skipLoopFilter),
                                trailingText = "Level ${vlcCfg.skipLoopFilter}",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcSkipLoopFilterPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = "Skip Frames",
                                subtitle = if (vlcCfg.skipFrames) "Enable frame skipping" else "No frame skipping",
                                checked = vlcCfg.skipFrames,
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(skipFrames = it)) },
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcSkipFramePicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "Decoder Threads",
                                subtitle = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads} threads",
                                trailingText = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads}",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.VlcDecoderThreadsPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Trash,
                                title = "Drop Late Frames",
                                subtitle = if (vlcCfg.dropLateFrames) "Discard delayed frames" else "Display all frames",
                                checked = vlcCfg.dropLateFrames,
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
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoScalingPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Clock,
                                title = "Frame Rate Strategy",
                                subtitle = "${exoCfg.frameRateStrategy.displayName} (${exoCfg.frameRateStrategy.key})",
                                trailingText = exoCfg.frameRateStrategy.key,
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoFrameRatePicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Volume,
                                title = "Skip Silence",
                                subtitle = if (exoCfg.skipSilence) "Skip silent sections" else "Play all audio",
                                checked = exoCfg.skipSilence,
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.setExoPlayerConfig(exoCfg.copy(skipSilence = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Headphones,
                                title = "Audio Offload",
                                subtitle = "${exoCfg.audioOffloadMode.displayName} (${exoCfg.audioOffloadMode.key})",
                                trailingText = exoCfg.audioOffloadMode.key,
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoAudioOffloadPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ToggleLeft,
                                title = "Decoder Fallback",
                                subtitle = if (exoCfg.enableDecoderFallback) "Fallback to secondary decoders" else "Primary decoder only",
                                checked = exoCfg.enableDecoderFallback,
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.setExoPlayerConfig(exoCfg.copy(enableDecoderFallback = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Back Buffer",
                                subtitle = if (exoCfg.backBufferDurationMs == 0) "Disabled" else "${exoCfg.backBufferDurationMs / 1000}s buffer",
                                trailingText = if (exoCfg.backBufferDurationMs == 0) "Off" else "${exoCfg.backBufferDurationMs / 1000}s",
                                index = exoIdx++, count = exoTotal,
                                onClick = { activeDialog = PlaybackSettingsDialog.ExoBackBufferPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Code,
                                title = "Preferred Codecs",
                                subtitle = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All codecs" else exoCfg.preferredVideoMimeTypes.joinToString(", "),
                                trailingText = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All" else "Custom",
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

    if (activeDialog is PlaybackSettingsDialog.VlcVideoOutputPicker) {
        val vlcCfg = preferences.libVlcConfig
        SettingsListPickerSheet(
            title = "Video Output (vout)",
            items = VlcVideoOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == vlcCfg.videoOutput },
            onDismiss = { activeDialog = PlaybackSettingsDialog.None },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(videoOutput = it))
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
}
