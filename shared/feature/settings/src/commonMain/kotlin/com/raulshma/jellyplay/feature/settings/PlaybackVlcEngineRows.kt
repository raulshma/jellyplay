package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.VlcAudioOutput
import com.raulshma.jellyplay.core.model.VlcVideoOutput
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_b_frames_level
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_time_stretch_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_time_stretch_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_device_based
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_frames_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_frames_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_threads_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_drop_late_frames_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_drop_late_frames_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_libvlc

/** The `playback.engine` group's libVLC branch rows. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackVlcEngineRows(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                            val vlcCfg = preferences.libVlcConfig
                            val vlcDefault = LibVlcEngineConfig()
                            // Same branch-shape as MPV: declared VLC rows (which
                            // include vlc_video_output) plus the reset row.
                            SettingsItemList(total = playbackEngineScreenRowTotal("vlc_")) {

                            val networkCachingTitle = rowTitle(PlaybackRows.VlcNetworkCaching)
                            val networkCachingAuto = stringResource(Res.string.settings_auto_device_based)
                            val skipLoopFilterTitle = rowTitle(PlaybackRows.VlcSkipLoopFilter)
                            val skipFramesTitle = rowTitle(PlaybackRows.VlcSkipFrames)
                            val decoderThreadsTitle = rowTitle(PlaybackRows.VlcDecoderThreads)
                            val skipLoopFilterLabels = (0..4).associateWith { stringResource(vlcSkipLoopFilterLabelRes(it)) }
                            val skipFrameLabels = (0..4).associateWith { stringResource(vlcSkipFrameLabelRes(it)) }
                            val vlcAudioOutputTitle = rowTitle(PlaybackRows.VlcAudioOutput)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.VlcAudioOutput),
                                title = rowTitle(PlaybackRows.VlcAudioOutput),
                                subtitle = "${vlcCfg.audioOutput.displayName} (${vlcCfg.audioOutput.key})",
                                trailingText = vlcCfg.audioOutput.key,
                                highlighted = highlightSettingId == PlaybackRows.VlcAudioOutput.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.VlcAudioTimeStretch),
                                title = rowTitle(PlaybackRows.VlcAudioTimeStretch),
                                subtitle = if (vlcCfg.audioTimeStretch) stringResource(Res.string.settings_audio_time_stretch_on) else stringResource(Res.string.settings_audio_time_stretch_off),
                                checked = vlcCfg.audioTimeStretch,
                                highlighted = highlightSettingId == PlaybackRows.VlcAudioTimeStretch.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(audioTimeStretch = it)) } },
                            )
                            val vlcVideoOutputTitle = rowTitle(PlaybackRows.VlcVideoOutput)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.VlcVideoOutput),
                                title = vlcVideoOutputTitle,
                                subtitle = "${vlcCfg.videoOutput.displayName} (${vlcCfg.videoOutput.key})",
                                trailingText = vlcCfg.videoOutput.key,
                                highlighted = highlightSettingId == PlaybackRows.VlcVideoOutput.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = vlcVideoOutputTitle,
                                        items = VlcVideoOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == vlcCfg.videoOutput },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(videoOutput = it)) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.VlcNetworkCaching),
                                title = networkCachingTitle,
                                subtitle = if (vlcCfg.networkCaching == 0) networkCachingAuto else "${vlcCfg.networkCaching}ms",
                                trailingText = if (vlcCfg.networkCaching == 0) stringResource(Res.string.settings_streaming_quality_auto) else "${vlcCfg.networkCaching}ms",
                                highlighted = highlightSettingId == PlaybackRows.VlcNetworkCaching.id,
                                onClick = {
                                    val options = listOf(0, 500, 1000, 1500, 2000, 3000, 5000)
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.VlcSkipLoopFilter),
                                title = skipLoopFilterTitle,
                                subtitle = skipLoopFilterLabels[vlcCfg.skipLoopFilter] ?: vlcCfg.skipLoopFilter.toString(),
                                trailingText = stringResource(Res.string.settings_b_frames_level, vlcCfg.skipLoopFilter),
                                highlighted = highlightSettingId == PlaybackRows.VlcSkipLoopFilter.id,
                                onClick = {
                                    val options = (0..4).toList()
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.VlcSkipFrames),
                                title = skipFramesTitle,
                                subtitle = if (vlcCfg.skipFrames) stringResource(Res.string.settings_skip_frames_on) else stringResource(Res.string.settings_skip_frames_off),
                                checked = vlcCfg.skipFrames,
                                highlighted = highlightSettingId == PlaybackRows.VlcSkipFrames.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(skipFrames = it)) } },
                                onClick = {
                                    val options = (0..4).toList()
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.VlcDecoderThreads),
                                title = decoderThreadsTitle,
                                subtitle = if (vlcCfg.decoderThreads == 0) autoLabel else "${vlcCfg.decoderThreads} $threadsSuffix",
                                trailingText = if (vlcCfg.decoderThreads == 0) autoLabel else "${vlcCfg.decoderThreads}",
                                highlighted = highlightSettingId == PlaybackRows.VlcDecoderThreads.id,
                                onClick = {
                                    val options = listOf(0, 1, 2, 4, 6, 8)
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.VlcDropLateFrames),
                                title = rowTitle(PlaybackRows.VlcDropLateFrames),
                                subtitle = if (vlcCfg.dropLateFrames) stringResource(Res.string.settings_drop_late_frames_on) else stringResource(Res.string.settings_drop_late_frames_off),
                                checked = vlcCfg.dropLateFrames,
                                highlighted = highlightSettingId == PlaybackRows.VlcDropLateFrames.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setLibVlcConfig(vlcCfg.copy(dropLateFrames = it)) } },
                            )
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.ResetEngineDefaults),
                                title = rowTitle(PlaybackRows.ResetEngineDefaults),
                                subtitle = stringResource(Res.string.settings_reset_libvlc),
                                onClick = { viewModel.edit { it.engine.setLibVlcConfig(vlcDefault) } },
                            )
                            }
}
