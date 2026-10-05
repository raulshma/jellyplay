package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.parseMpvConfigOptions
import com.raulshma.jellyplay.core.model.MpvVideoOutput
import com.raulshma.jellyplay.core.model.MpvScaler
import com.raulshma.jellyplay.core.model.MpvAudioOutput
import com.raulshma.jellyplay.core.model.MpvAudioDevice
import com.raulshma.jellyplay.core.model.MpvAudioOutputMode
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvInterpolationTscale
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvHwdec
import com.raulshma.jellyplay.core.model.MpvSkipLoopFilter
import com.raulshma.jellyplay.core.model.MpvFrameDrop
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_debanding_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_debanding_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_interpolation_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_fallback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_device_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_device_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_exclusive_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_exclusive_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_mode_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_mode_stereo
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_mode_optical
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_mode_hdmi
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_passthrough_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_passthrough_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hwdec_universal
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom_options
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_mpv_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_mpv_helper_text
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_mpv_config_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_raw_mpv_options
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_mpv

/** The `playback.engine` group's MPV branch rows. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackMpvEngineRows(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                            val mpvCfg = preferences.mpvConfig
                            val mpvDefault = MpvEngineConfig()
                            // The branch's declared rows plus the one reset row —
                            // the declared reset_engine_defaults item renders as
                            // that row in every engine branch (the admission
                            // total beside SettingsScreenGroups).
                            SettingsItemList(
                                total = playbackEngineScreenRowTotal("mpv_"),
                            ) {
                            val videoOutputTitle = rowTitle(PlaybackRows.MpvVideoOutput)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvVideoOutput),
                                title = rowTitle(PlaybackRows.MpvVideoOutput),
                                subtitle = "${mpvCfg.videoOutput.displayName} (${mpvCfg.videoOutput.key})",
                                trailingText = mpvCfg.videoOutput.key,
                                highlighted = highlightSettingId == PlaybackRows.MpvVideoOutput.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = videoOutputTitle,
                                        items = MpvVideoOutput.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.videoOutput },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(videoOutput = it)) } },
                                    )
                                },
                            )
                            val scalerTitle = rowTitle(PlaybackRows.MpvScaler)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvScaler),
                                title = rowTitle(PlaybackRows.MpvScaler),
                                subtitle = "${mpvCfg.scaler.displayName} (${mpvCfg.scaler.key})",
                                trailingText = mpvCfg.scaler.key,
                                highlighted = highlightSettingId == PlaybackRows.MpvScaler.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                                icon = rowIcon(PlaybackRows.MpvDebanding),
                                title = rowTitle(PlaybackRows.MpvDebanding),
                                subtitle = if (mpvCfg.deband) stringResource(Res.string.settings_debanding_on) else stringResource(Res.string.settings_debanding_off),
                                checked = mpvCfg.deband,
                                highlighted = highlightSettingId == PlaybackRows.MpvDebanding.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(deband = it)) } },
                            )
                            SettingToggleItem(
                                icon = rowIcon(PlaybackRows.MpvInterpolation),
                                title = rowTitle(PlaybackRows.MpvInterpolation),
                                subtitle = if (mpvCfg.interpolation) stringResource(Res.string.settings_interpolation_on) else stringResource(Res.string.settings_interpolation_off),
                                checked = mpvCfg.interpolation,
                                highlighted = highlightSettingId == PlaybackRows.MpvInterpolation.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(interpolation = it)) } },
                            )
                            val audioOutputTitle = rowTitle(PlaybackRows.MpvAudioOutput)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvAudioOutput),
                                title = rowTitle(PlaybackRows.MpvAudioOutput),
                                subtitle = "${mpvCfg.audioOutput.displayName} (${mpvCfg.audioOutput.key})",
                                trailingText = mpvCfg.audioOutput.key,
                                highlighted = highlightSettingId == PlaybackRows.MpvAudioOutput.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                            val audioFallbackTitle = rowTitle(PlaybackRows.MpvAudioFallback)
                            val noFallback = stringResource(Res.string.settings_no_fallback)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvAudioFallback),
                                title = rowTitle(PlaybackRows.MpvAudioFallback),
                                subtitle = mpvCfg.audioFallback?.displayName ?: noneLabel,
                                trailingText = mpvCfg.audioFallback?.key ?: noneLabel,
                                highlighted = highlightSettingId == PlaybackRows.MpvAudioFallback.id,
                                onClick = {
                                    val options = listOf(null) + MpvAudioOutput.entries
                                    activePicker.value = PickerState.List(
                                        title = audioFallbackTitle,
                                        items = options,
                                        label = { it?.displayName ?: noneLabel },
                                        subtitle = { it?.key ?: noFallback },
                                        isSelected = { it == mpvCfg.audioFallback },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(audioFallback = it)) } },
                                    )
                                },
                            )
                            // ── Desktop-only audio rows: the mpv audio
                            // device picker / exclusive toggle / output mode —
                            // gated by the declared Platform admission (the
                            // Android mpv binding has no device-list surface).
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvAudioDevice.id, rowFlags)) {
                                val autoDeviceLabel = stringResource(Res.string.settings_audio_device_auto)
                                val noDevicesLabel = stringResource(Res.string.settings_audio_device_none)
                                val audioDeviceTitle = rowTitle(PlaybackRows.MpvAudioDevice)
                                SettingListItem(
                                    icon = rowIcon(PlaybackRows.MpvAudioDevice),
                                    title = rowTitle(PlaybackRows.MpvAudioDevice),
                                    subtitle = mpvCfg.audioDevice ?: autoDeviceLabel,
                                    trailingText = mpvCfg.audioDevice ?: "auto",
                                    highlighted = highlightSettingId == PlaybackRows.MpvAudioDevice.id,
                                    onClick = {
                                        // Enumeration spins up a throwaway mpv
                                        // context — fetched (and VM-cached) at
                                        // pick time, not composition time.
                                        scope.launch {
                                            val devices = viewModel.audioDevices()
                                            val options: List<MpvAudioDevice?> =
                                                if (devices.isEmpty()) listOf(null) else listOf(null) + devices
                                            activePicker.value = PickerState.List(
                                                title = audioDeviceTitle,
                                                items = options,
                                                label = { it?.name ?: autoDeviceLabel },
                                                subtitle = { device ->
                                                    when {
                                                        device == null -> autoDeviceLabel
                                                        // Enumeration degraded (no libmpv):
                                                        // say so instead of an empty line.
                                                        devices.isEmpty() -> noDevicesLabel
                                                        else -> device.description
                                                    }
                                                },
                                                isSelected = { option ->
                                                    (option == null && mpvCfg.audioDevice == null) ||
                                                        (option != null && option.name == mpvCfg.audioDevice)
                                                },
                                                onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(audioDevice = it?.name)) } },
                                            )
                                        }
                                    },
                                )
                            }
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvAudioExclusive.id, rowFlags)) {
                                SettingToggleItem(
                                    icon = rowIcon(PlaybackRows.MpvAudioExclusive),
                                    title = rowTitle(PlaybackRows.MpvAudioExclusive),
                                    subtitle = if (mpvCfg.audioExclusive) {
                                        stringResource(Res.string.settings_audio_exclusive_on)
                                    } else {
                                        stringResource(Res.string.settings_audio_exclusive_off)
                                    },
                                    checked = mpvCfg.audioExclusive,
                                    highlighted = highlightSettingId == PlaybackRows.MpvAudioExclusive.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(audioExclusive = it)) } },
                                )
                            }
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvAudioMode.id, rowFlags)) {
                                // Pre-resolved (the picker's subtitle lambda is
                                // not composable): the OPTICAL/HDMI descriptions
                                // carry the documented fallback behavior.
                                val modeDescriptions: Map<MpvAudioOutputMode, String> = mapOf(
                                    MpvAudioOutputMode.AUTO to stringResource(Res.string.settings_audio_mode_auto),
                                    MpvAudioOutputMode.STEREO to stringResource(Res.string.settings_audio_mode_stereo),
                                    MpvAudioOutputMode.OPTICAL to stringResource(Res.string.settings_audio_mode_optical),
                                    MpvAudioOutputMode.HDMI to stringResource(Res.string.settings_audio_mode_hdmi),
                                )
                                val audioModeTitle = rowTitle(PlaybackRows.MpvAudioMode)
                                SettingListItem(
                                    icon = rowIcon(PlaybackRows.MpvAudioMode),
                                    title = rowTitle(PlaybackRows.MpvAudioMode),
                                    subtitle = modeDescriptions[mpvCfg.audioOutputMode].orEmpty(),
                                    trailingText = mpvCfg.audioOutputMode.key,
                                    highlighted = highlightSettingId == PlaybackRows.MpvAudioMode.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = audioModeTitle,
                                            items = MpvAudioOutputMode.entries,
                                            label = { it.displayName },
                                            subtitle = { modeDescriptions[it].orEmpty() },
                                            isSelected = { it == mpvCfg.audioOutputMode },
                                            onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(audioOutputMode = it)) } },
                                        )
                                    },
                                )
                            }
                            // ── Desktop-only render rows: shader
                            // pack, tone mapping, quality profile, the
                            // interpolation tscale preset, and the HDR
                            // passthrough output — gated by the same platform
                            // admission surface as the desktop audio rows (the
                            // extraction + vo/gpu-next machinery is desktop's).
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvShaderPack.id, rowFlags)) {
                                val shaderPackTitle = rowTitle(PlaybackRows.MpvShaderPack)
                                SettingListItem(
                                    icon = rowIcon(PlaybackRows.MpvShaderPack),
                                    title = rowTitle(PlaybackRows.MpvShaderPack),
                                    subtitle = mpvCfg.shaderPack.displayName,
                                    trailingText = mpvCfg.shaderPack.key,
                                    highlighted = highlightSettingId == PlaybackRows.MpvShaderPack.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = shaderPackTitle,
                                            items = MpvShaderPack.entries,
                                            label = { it.displayName },
                                            subtitle = { it.key },
                                            isSelected = { it == mpvCfg.shaderPack },
                                            onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(shaderPack = it)) } },
                                        )
                                    },
                                )
                            }
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvToneMapping.id, rowFlags)) {
                                val toneMappingTitle = rowTitle(PlaybackRows.MpvToneMapping)
                                SettingListItem(
                                    icon = rowIcon(PlaybackRows.MpvToneMapping),
                                    title = rowTitle(PlaybackRows.MpvToneMapping),
                                    subtitle = mpvCfg.toneMapping.displayName,
                                    trailingText = mpvCfg.toneMapping.key ?: "auto",
                                    highlighted = highlightSettingId == PlaybackRows.MpvToneMapping.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = toneMappingTitle,
                                            items = MpvToneMapping.entries,
                                            label = { it.displayName },
                                            subtitle = { it.key ?: "auto" },
                                            isSelected = { it == mpvCfg.toneMapping },
                                            onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(toneMapping = it)) } },
                                        )
                                    },
                                )
                            }
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvRenderQuality.id, rowFlags)) {
                                val renderQualityTitle = rowTitle(PlaybackRows.MpvRenderQuality)
                                SettingListItem(
                                    icon = rowIcon(PlaybackRows.MpvRenderQuality),
                                    title = rowTitle(PlaybackRows.MpvRenderQuality),
                                    subtitle = mpvCfg.renderQuality.displayName,
                                    trailingText = mpvCfg.renderQuality.key,
                                    highlighted = highlightSettingId == PlaybackRows.MpvRenderQuality.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = renderQualityTitle,
                                            items = MpvRenderQuality.entries,
                                            label = { it.displayName },
                                            subtitle = { it.key },
                                            isSelected = { it == mpvCfg.renderQuality },
                                            onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(renderQuality = it)) } },
                                        )
                                    },
                                )
                            }
                            if (SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvHdrPassthrough.id, rowFlags)) {
                                // engine-creation-time — the row says so.
                                SettingToggleItem(
                                    icon = rowIcon(PlaybackRows.MpvHdrPassthrough),
                                    title = rowTitle(PlaybackRows.MpvHdrPassthrough),
                                    subtitle = if (mpvCfg.hdrPassthrough) {
                                        stringResource(Res.string.settings_hdr_passthrough_on)
                                    } else {
                                        stringResource(Res.string.settings_hdr_passthrough_off)
                                    },
                                    checked = mpvCfg.hdrPassthrough,
                                    highlighted = highlightSettingId == PlaybackRows.MpvHdrPassthrough.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(hdrPassthrough = it)) } },
                                )
                            }
                            if (mpvCfg.interpolation &&
                                SettingsScreenGroups.playbackEngine.rowAdmitted(PlaybackRows.MpvInterpolationTscale.id, rowFlags)
                            ) {
                                // tscale only matters with interpolation on.
                                val tscaleTitle = rowTitle(PlaybackRows.MpvInterpolationTscale)
                                SettingListItem(
                                    icon = rowIcon(PlaybackRows.MpvInterpolationTscale),
                                    title = rowTitle(PlaybackRows.MpvInterpolationTscale),
                                    subtitle = mpvCfg.interpolationTscale.displayName,
                                    trailingText = mpvCfg.interpolationTscale.key,
                                    highlighted = highlightSettingId == PlaybackRows.MpvInterpolationTscale.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = tscaleTitle,
                                            items = MpvInterpolationTscale.entries,
                                            label = { it.displayName },
                                            subtitle = { it.key },
                                            isSelected = { it == mpvCfg.interpolationTscale },
                                            onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(interpolationTscale = it)) } },
                                        )
                                    },
                                )
                            }
                            val bufferSizeTitle = rowTitle(PlaybackRows.MpvBufferSize)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvBufferSize),
                                title = rowTitle(PlaybackRows.MpvBufferSize),
                                subtitle = "${mpvCfg.demuxerMaxBytes.displayName} (${mpvCfg.demuxerMaxBytes.key})",
                                trailingText = mpvCfg.demuxerMaxBytes.key,
                                highlighted = highlightSettingId == PlaybackRows.MpvBufferSize.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                            val hwdecOverrideTitle = rowTitle(PlaybackRows.MpvHwdecOverride)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvHwdecOverride),
                                title = rowTitle(PlaybackRows.MpvHwdecOverride),
                                subtitle = mpvCfg.hwdecOverride?.displayName ?: hwdecUniversal,
                                trailingText = mpvCfg.hwdecOverride?.key ?: stringResource(Res.string.settings_streaming_quality_auto),
                                highlighted = highlightSettingId == PlaybackRows.MpvHwdecOverride.id,
                                onClick = {
                                    val options = listOf(null) + MpvHwdec.entries
                                    activePicker.value = PickerState.List(
                                        title = hwdecOverrideTitle,
                                        items = options,
                                        label = { it?.displayName ?: hwdecUniversal },
                                        subtitle = { it?.key ?: "auto" },
                                        isSelected = { it == mpvCfg.hwdecOverride },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(hwdecOverride = it)) } },
                                    )
                                },
                            )
                            val skipLoopFilterTitle = rowTitle(PlaybackRows.MpvSkipLoopFilter)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvSkipLoopFilter),
                                title = rowTitle(PlaybackRows.MpvSkipLoopFilter),
                                subtitle = "${mpvCfg.skipLoopFilter.displayName} (${mpvCfg.skipLoopFilter.key})",
                                trailingText = mpvCfg.skipLoopFilter.key,
                                highlighted = highlightSettingId == PlaybackRows.MpvSkipLoopFilter.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = skipLoopFilterTitle,
                                        items = MpvSkipLoopFilter.entries,
                                        label = { it.displayName },
                                        subtitle = { it.key },
                                        isSelected = { it == mpvCfg.skipLoopFilter },
                                        onSelect = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(skipLoopFilter = it)) } },
                                    )
                                },
                            )
                            val frameDropTitle = rowTitle(PlaybackRows.MpvFrameDrop)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvFrameDrop),
                                title = rowTitle(PlaybackRows.MpvFrameDrop),
                                subtitle = "${mpvCfg.frameDrop.displayName} (${mpvCfg.frameDrop.key})",
                                trailingText = mpvCfg.frameDrop.key,
                                highlighted = highlightSettingId == PlaybackRows.MpvFrameDrop.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
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
                            val mpvConfigPlaceholder = stringResource(Res.string.settings_mpv_config_placeholder)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.MpvExtraConfig),
                                title = rowTitle(PlaybackRows.MpvExtraConfig),
                                subtitle = if (mpvCfg.mpvExtraConfig.isBlank()) {
                                    stringResource(Res.string.settings_raw_mpv_options)
                                } else {
                                    "${parseMpvConfigOptions(mpvCfg.mpvExtraConfig).size} $customOptionsSuffix"
                                },
                                trailingText = "mpv.conf",
                                highlighted = highlightSettingId == PlaybackRows.MpvExtraConfig.id,
                                onClick = {
                                    activePicker.value = PickerState.Text(
                                        title = advancedMpvConfigTitle,
                                        initialText = mpvCfg.mpvExtraConfig,
                                        helperText = mpvHelperText,
                                        placeholder = mpvConfigPlaceholder,
                                        onSave = { viewModel.edit { scope -> scope.engine.setMpvConfig(mpvCfg.copy(mpvExtraConfig = it)) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.ResetEngineDefaults),
                                title = rowTitle(PlaybackRows.ResetEngineDefaults),
                                subtitle = stringResource(Res.string.settings_reset_mpv),
                                onClick = { viewModel.edit { it.engine.setMpvConfig(mpvDefault) } },
                            )
                            }
}
