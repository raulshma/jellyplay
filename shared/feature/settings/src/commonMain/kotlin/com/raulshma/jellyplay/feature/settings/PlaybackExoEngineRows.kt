package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_silence_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_silence_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_fallback_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_fallback_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_back_buffer_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_all_codecs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_codecs_hevc_avc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_codecs_av1_hevc_avc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_codecs_avc_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_exoplayer

/** The `playback.engine` group's ExoPlayer branch rows. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackExoEngineRows(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                            val offLabel2 = stringResource(Res.string.settings_off)
                            val exoCfg = preferences.exoPlayerConfig
                            val exoDefault = ExoPlayerEngineConfig()
                            SettingsItemList(total = playbackEngineScreenRowTotal("exo_")) {

                            val videoScalingTitle = rowTitle(PlaybackRows.ExoVideoScaling)
                            val frameRateStrategyTitle = rowTitle(PlaybackRows.ExoFrameRateStrategy)
                            val audioOffloadTitle = rowTitle(PlaybackRows.ExoAudioOffload)
                            val backBufferTitle = rowTitle(PlaybackRows.ExoBackBuffer)
                            val preferredCodecsTitle = rowTitle(PlaybackRows.ExoPreferredCodecs)
                            val disabledLabel = stringResource(Res.string.settings_disabled)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.ExoVideoScaling),
                                title = videoScalingTitle,
                                subtitle = "${exoCfg.videoScalingMode.displayName} (${exoCfg.videoScalingMode.key})",
                                trailingText = exoCfg.videoScalingMode.key,
                                highlighted = highlightSettingId == PlaybackRows.ExoVideoScaling.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.ExoFrameRateStrategy),
                                title = frameRateStrategyTitle,
                                subtitle = "${exoCfg.frameRateStrategy.displayName} (${exoCfg.frameRateStrategy.key})",
                                trailingText = exoCfg.frameRateStrategy.key,
                                highlighted = highlightSettingId == PlaybackRows.ExoFrameRateStrategy.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.ExoSkipSilence),
                                title = rowTitle(PlaybackRows.ExoSkipSilence),
                                subtitle = if (exoCfg.skipSilence) stringResource(Res.string.settings_skip_silence_on) else stringResource(Res.string.settings_skip_silence_off),
                                checked = exoCfg.skipSilence,
                                highlighted = highlightSettingId == PlaybackRows.ExoSkipSilence.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(skipSilence = it)) } },
                            )
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.ExoAudioOffload),
                                title = audioOffloadTitle,
                                subtitle = "${exoCfg.audioOffloadMode.displayName} (${exoCfg.audioOffloadMode.key})",
                                trailingText = exoCfg.audioOffloadMode.key,
                                highlighted = highlightSettingId == PlaybackRows.ExoAudioOffload.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.ExoDecoderFallback),
                                title = rowTitle(PlaybackRows.ExoDecoderFallback),
                                subtitle = if (exoCfg.enableDecoderFallback) stringResource(Res.string.settings_decoder_fallback_on) else stringResource(Res.string.settings_decoder_fallback_off),
                                checked = exoCfg.enableDecoderFallback,
                                highlighted = highlightSettingId == PlaybackRows.ExoDecoderFallback.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setExoPlayerConfig(exoCfg.copy(enableDecoderFallback = it)) } },
                            )
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.ExoBackBuffer),
                                title = backBufferTitle,
                                subtitle = if (exoCfg.backBufferDurationMs == 0) disabledLabel else stringResource(Res.string.settings_back_buffer_value, exoCfg.backBufferDurationMs / 1000),
                                trailingText = if (exoCfg.backBufferDurationMs == 0) offLabel2 else "${exoCfg.backBufferDurationMs / 1000}s",
                                highlighted = highlightSettingId == PlaybackRows.ExoBackBuffer.id,
                                onClick = {
                                    val options = listOf(0, 5000, 10000, 15000, 20000, 30000)
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.ExoPreferredCodecs),
                                title = preferredCodecsTitle,
                                subtitle = if (exoCfg.preferredVideoMimeTypes.isEmpty()) allCodecs else exoCfg.preferredVideoMimeTypes.joinToString(", "),
                                trailingText = if (exoCfg.preferredVideoMimeTypes.isEmpty()) stringResource(Res.string.settings_all) else customLabel,
                                highlighted = highlightSettingId == PlaybackRows.ExoPreferredCodecs.id,
                                onClick = {
                                    val presets = listOf(
                                        emptyList<String>(),
                                        listOf("video/hevc", "video/avc"),
                                        listOf("video/av1", "video/hevc", "video/avc"),
                                        listOf("video/avc"),
                                    )
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.ResetEngineDefaults),
                                title = rowTitle(PlaybackRows.ResetEngineDefaults),
                                subtitle = stringResource(Res.string.settings_reset_exoplayer),
                                onClick = { viewModel.edit { it.engine.setExoPlayerConfig(exoDefault) } },
                            )
                            }
}
