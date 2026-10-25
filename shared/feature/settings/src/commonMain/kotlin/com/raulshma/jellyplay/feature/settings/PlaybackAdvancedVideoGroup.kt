package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.TranscodeAudioCodecPreference
import com.raulshma.jellyplay.core.model.TranscodeVideoCodecPreference
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom_device_profile_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom_device_profile_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_resolution_switching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_decoder_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_speed_test_running
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_speed_test_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_speed_test_result
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_passthrough_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_passthrough_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_passthrough_codec_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_rate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_rate_res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_desc_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_desc_rate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_desc_res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_spatial_audio_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_spatial_audio_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_spatial_audio_only_headphones_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_spatial_audio_only_headphones_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_rate_matching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_playback_prefer_downloaded
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_playback_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_playback_prefer_streaming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_playback_desc_downloaded
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_playback_desc_streaming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_playback_streaming_caveat
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_audio_delay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_delay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_delay_value

/** The `playback.advancedVideo` group: dialogue boost, decoder, passthrough, refresh rate, streaming quality, live stream option, audio delay. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackAdvancedVideoGroup(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                val offLabel2 = stringResource(Res.string.settings_off)
                val resSwitchSuffix = stringResource(Res.string.settings_resolution_switching)
                SettingsGroup(
                    icon = Tabler.Outline.BadgeHd,
                    title = stringResource(Res.string.settings_advanced_video),
                    summary = { stringResource(Res.string.settings_decoder_summary, preferences.decoderMode.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackAdvancedVideo.itemIdSet,
                ) {
                    SettingsItemList(
                        total = rowTotalFor(SettingsScreenGroups.playbackAdvancedVideo, rowFlags),
                    ) {
                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.DialogueBoost),
                        title = rowTitle(PlaybackRows.DialogueBoost),
                        subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else offLabel2,
                        checked = preferences.dialogueBoostEnabled,
                        highlighted = highlightSettingId == PlaybackRows.DialogueBoost.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setDialogueBoostEnabled(it) } },
                    )
                    if (SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.DialogueBoostStrength.id, rowFlags)) {
                        val dialogueBoostStrengthTitle = rowTitle(PlaybackRows.DialogueBoostStrength)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.DialogueBoostStrength),
                            title = rowTitle(PlaybackRows.DialogueBoostStrength),
                            subtitle = preferences.dialogueBoostStrength.displayName,
                            trailingText = preferences.dialogueBoostStrength.displayName,
                            highlighted = highlightSettingId == PlaybackRows.DialogueBoostStrength.id,
                            onClick = {
                                val strengths = EffectStrength.entries
                                activePicker.value = pickerChip(
                                    title = dialogueBoostStrengthTitle,
                                    values = strengths,
                                    current = preferences.dialogueBoostStrength,
                                    label = { it.displayName },
                                    onSelect = { strength -> viewModel.edit { it.audioEffects.setDialogueBoostStrength(strength) } },
                                )
                            },
                        )
                    }
                    val decoderTitle = rowTitle(PlaybackRows.Decoder)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.Decoder),
                        title = decoderTitle,
                        subtitle = preferences.decoderMode.displayName,
                        trailingText = preferences.decoderMode.displayName.split(" ").first(),
                        highlighted = highlightSettingId == PlaybackRows.Decoder.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = decoderTitle,
                                items = DecoderMode.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.decoderMode },
                                onSelect = { viewModel.edit { scope -> scope.playback.setDecoderMode(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.AudioPassthrough),
                        title = rowTitle(PlaybackRows.AudioPassthrough),
                        subtitle = if (preferences.audioPassthrough) stringResource(Res.string.settings_audio_passthrough_on) else stringResource(Res.string.settings_audio_passthrough_off),
                        checked = preferences.audioPassthrough,
                        highlighted = highlightSettingId == PlaybackRows.AudioPassthrough.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.playback.setAudioPassthrough(it) } },
                    )
                    // The per-codec passthrough allow-list — one toggle per
                    // codec, each admitted only while the master toggle above
                    // is on (the dependent-row admission the row total reads).
                    val codecOnSubtitle = stringResource(Res.string.settings_passthrough_codec_on)
                    val codecOffSubtitle = stringResource(Res.string.settings_passthrough_codec_off)
                    listOf(
                        AudioPassthroughCodec.AC3 to PlaybackRows.PassthroughCodecAc3,
                        AudioPassthroughCodec.EAC3 to PlaybackRows.PassthroughCodecEac3,
                        AudioPassthroughCodec.DTS to PlaybackRows.PassthroughCodecDts,
                        AudioPassthroughCodec.DTS_HD to PlaybackRows.PassthroughCodecDtshd,
                        AudioPassthroughCodec.TRUEHD to PlaybackRows.PassthroughCodecTruehd,
                    ).forEach { (codec, row) ->
                        if (SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(row.id, rowFlags)) {
                            val enabled = codec in preferences.audioPassthroughCodecs
                            SettingToggleItem(
                                icon = rowIcon(row),
                                title = rowTitle(row),
                                subtitle = if (enabled) codecOnSubtitle else codecOffSubtitle,
                                checked = enabled,
                                highlighted = highlightSettingId == row.id,
                                onCheckedChange = { on ->
                                    viewModel.edit { scope ->
                                        scope.playback.setAudioPassthroughCodecs(
                                            if (on) preferences.audioPassthroughCodecs + codec else preferences.audioPassthroughCodecs - codec,
                                        )
                                    }
                                },
                            )
                        }
                    }
                    val maxChannelsTitle = rowTitle(PlaybackRows.MaxAudioChannels)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.MaxAudioChannels),
                        title = maxChannelsTitle,
                        subtitle = preferences.maxAudioChannels.displayName,
                        trailingText = preferences.maxAudioChannels.displayName,
                        highlighted = highlightSettingId == PlaybackRows.MaxAudioChannels.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = maxChannelsTitle,
                                items = MaxAudioChannelsEnum.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.maxAudioChannels },
                                onSelect = { viewModel.edit { scope -> scope.playback.setMaxAudioChannels(it) } },
                            )
                        },
                    )
                    val downmixBoostTitle = rowTitle(PlaybackRows.DownmixBoost)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.DownmixBoost),
                        title = downmixBoostTitle,
                        subtitle = if (preferences.downmixBoostDb > 0f) "${preferences.downmixBoostDb.toInt()} dB" else offLabel2,
                        trailingText = if (preferences.downmixBoostDb > 0f) "${preferences.downmixBoostDb.toInt()} dB" else offLabel2,
                        highlighted = highlightSettingId == PlaybackRows.DownmixBoost.id,
                        onClick = {
                            activePicker.value = PickerState.Slider(
                                title = downmixBoostTitle,
                                value = preferences.downmixBoostDb,
                                valueRange = 0f..12f,
                                steps = 11,
                                valueLabel = { "${it.toInt()} dB" },
                                rangeStartLabel = "0 dB",
                                rangeEndLabel = "12 dB",
                                onConfirm = { viewModel.edit { scope -> scope.playback.setDownmixBoostDb(it) } },
                            )
                        },
                    )
                    val spatialAudioOnSubtitle = stringResource(Res.string.settings_spatial_audio_on)
                    val spatialAudioOffSubtitle = stringResource(Res.string.settings_spatial_audio_off)
                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.SpatialAudio),
                        title = rowTitle(PlaybackRows.SpatialAudio),
                        subtitle = if (preferences.spatialAudioEnabled) spatialAudioOnSubtitle else spatialAudioOffSubtitle,
                        checked = preferences.spatialAudioEnabled,
                        highlighted = highlightSettingId == PlaybackRows.SpatialAudio.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.playback.setSpatialAudioEnabled(it) } },
                    )
                    if (SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.SpatialAudioOnlyHeadphones.id, rowFlags)) {
                        val headphonesOnlyOn = stringResource(Res.string.settings_spatial_audio_only_headphones_on)
                        val headphonesOnlyOff = stringResource(Res.string.settings_spatial_audio_only_headphones_off)
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.SpatialAudioOnlyHeadphones),
                            title = rowTitle(PlaybackRows.SpatialAudioOnlyHeadphones),
                            subtitle = if (preferences.spatialAudioOnlyHeadphones) headphonesOnlyOn else headphonesOnlyOff,
                            checked = preferences.spatialAudioOnlyHeadphones,
                            highlighted = highlightSettingId == PlaybackRows.SpatialAudioOnlyHeadphones.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setSpatialAudioOnlyHeadphones(it) } },
                        )
                    }
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
                        icon = rowIcon(PlaybackRows.FrameRateMatching),
                        title = rowTitle(PlaybackRows.FrameRateMatching),
                        subtitle = preferences.refreshRateMode.displayName +
                            if (preferences.refreshRateMode == com.raulshma.jellyplay.core.model.RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
                                " $resSwitchSuffix" else "",
                        trailingText = refreshRateSubtitles[preferences.refreshRateMode] ?: preferences.refreshRateMode.displayName,
                        highlighted = highlightSettingId == PlaybackRows.FrameRateMatching.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = refreshRateMatchingTitle,
                                items = com.raulshma.jellyplay.core.model.RefreshRateMode.entries,
                                label = { it.displayName },
                                subtitle = { refreshRateDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.refreshRateMode },
                                onSelect = { viewModel.edit { scope -> scope.playback.setRefreshRateMode(it) } },
                            )
                        },
                    )
                    val offlineLabels = OfflinePlaybackPreference.entries.associateWith {
                        when (it) {
                            OfflinePlaybackPreference.PREFER_DOWNLOADED -> stringResource(Res.string.settings_offline_playback_prefer_downloaded)
                            OfflinePlaybackPreference.PREFER_STREAMING -> stringResource(Res.string.settings_offline_playback_prefer_streaming)
                        }
                    }
                    val offlineDescs = OfflinePlaybackPreference.entries.associateWith {
                        when (it) {
                            OfflinePlaybackPreference.PREFER_DOWNLOADED -> stringResource(Res.string.settings_offline_playback_desc_downloaded)
                            OfflinePlaybackPreference.PREFER_STREAMING -> stringResource(Res.string.settings_offline_playback_desc_streaming)
                        }
                    }
                    val offlineCaveat = stringResource(Res.string.settings_offline_playback_streaming_caveat)
                    val offlinePlaybackTitle = rowTitle(PlaybackRows.OfflinePlayback)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.OfflinePlayback),
                        title = rowTitle(PlaybackRows.OfflinePlayback),
                        subtitle = offlineDescs[preferences.offlinePlaybackPreference] +
                            if (preferences.offlinePlaybackPreference == OfflinePlaybackPreference.PREFER_STREAMING)
                                " $offlineCaveat" else "",
                        trailingText = offlineLabels[preferences.offlinePlaybackPreference] ?: preferences.offlinePlaybackPreference.displayName,
                        highlighted = highlightSettingId == PlaybackRows.OfflinePlayback.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = offlinePlaybackTitle,
                                items = OfflinePlaybackPreference.entries,
                                label = { offlineLabels[it] ?: it.displayName },
                                subtitle = { offlineDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.offlinePlaybackPreference },
                                onSelect = { viewModel.edit { scope -> scope.playback.setOfflinePlaybackPreference(it) } },
                            )
                        },
                    )
                    // The delivery-method row (Issue #1): the same posture the
                    // in-player Playback Options sheet's Delivery picker edits,
                    // now reachable from Settings — it governs whether the
                    // quality row below applies at all (forced direct play
                    // ignores the cap).
                    val playbackModeTitle = rowTitle(PlaybackRows.PlaybackMode)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.PlaybackMode),
                        title = playbackModeTitle,
                        subtitle = stringResource(Res.string.ss_playback_mode_subtitle),
                        trailingText = preferences.playbackMode.displayName,
                        highlighted = highlightSettingId == PlaybackRows.PlaybackMode.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = playbackModeTitle,
                                items = PlaybackMode.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.playbackMode },
                                onSelect = { viewModel.edit { scope -> scope.playback.setPlaybackMode(it) } },
                            )
                        },
                    )
                    val qualityLabels = StreamingQuality.entries.associateWith { stringResource(streamingQualityLabelRes(it)) }
                    val qualityShorts = StreamingQuality.entries.associateWith { stringResource(streamingQualityShortRes(it)) }
                    val streamingQualityTitle = rowTitle(PlaybackRows.StreamingQuality)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.StreamingQuality),
                        title = rowTitle(PlaybackRows.StreamingQuality),
                        subtitle = qualityLabels[preferences.streamingQuality] ?: preferences.streamingQuality.name,
                        trailingText = qualityShorts[preferences.streamingQuality] ?: preferences.streamingQuality.name,
                        highlighted = highlightSettingId == PlaybackRows.StreamingQuality.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = streamingQualityTitle,
                                items = StreamingQuality.entries,
                                label = { qualityLabels[it] ?: it.name },
                                isSelected = { it == preferences.streamingQuality },
                                onSelect = { viewModel.edit { scope -> scope.playback.setStreamingQuality(it) } },
                            )
                        },
                    )
                    // The transcode-decision cluster — renders beside
                    // [StreamingQuality], the knobs that steer the same
                    // direct-play-vs-transcode choice.
                    val hdrFormatTitle = rowTitle(PlaybackRows.HdrFormatPreference)
                    val hdrFormatLabels = HdrFormatPreference.entries.associateWith { stringResource(hdrFormatPreferenceLabelRes(it)) }
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.HdrFormatPreference),
                        title = hdrFormatTitle,
                        subtitle = rowSubtitle(PlaybackRows.HdrFormatPreference),
                        trailingText = hdrFormatLabels[preferences.hdrFormatPreference]
                            ?: preferences.hdrFormatPreference.name,
                        highlighted = highlightSettingId == PlaybackRows.HdrFormatPreference.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = hdrFormatTitle,
                                items = HdrFormatPreference.entries,
                                label = { hdrFormatLabels.getValue(it) },
                                isSelected = { it == preferences.hdrFormatPreference },
                                onSelect = { viewModel.edit { scope -> scope.playback.setHdrFormatPreference(it) } },
                            )
                        },
                    )
                    val transcodeCapTitle = rowTitle(PlaybackRows.ForceTranscodeAboveHeight)
                    val transcodeCapOffLabel = stringResource(Res.string.settings_off)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.ForceTranscodeAboveHeight),
                        title = transcodeCapTitle,
                        subtitle = rowSubtitle(PlaybackRows.ForceTranscodeAboveHeight),
                        trailingText = if (preferences.forceTranscodeAboveHeight == 0) transcodeCapOffLabel else "${preferences.forceTranscodeAboveHeight}p",
                        highlighted = highlightSettingId == PlaybackRows.ForceTranscodeAboveHeight.id,
                        onClick = {
                            val heights = listOf(0, 720, 1080, 1440, 2160)
                            activePicker.value = PickerState.List(
                                title = transcodeCapTitle,
                                items = heights,
                                label = { if (it == 0) transcodeCapOffLabel else "${it}p" },
                                isSelected = { it == preferences.forceTranscodeAboveHeight },
                                onSelect = { viewModel.edit { scope -> scope.playback.setForceTranscodeAboveHeight(it) } },
                            )
                        },
                    )
                    val transcodeVideoCodecTitle = rowTitle(PlaybackRows.PreferredTranscodeVideoCodec)
                    val transcodeVideoCodecLabels = TranscodeVideoCodecPreference.entries.associateWith { stringResource(transcodeVideoCodecLabelRes(it)) }
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.PreferredTranscodeVideoCodec),
                        title = transcodeVideoCodecTitle,
                        subtitle = rowSubtitle(PlaybackRows.PreferredTranscodeVideoCodec),
                        trailingText = transcodeVideoCodecLabels[preferences.preferredTranscodeVideoCodec]
                            ?: preferences.preferredTranscodeVideoCodec.name,
                        highlighted = highlightSettingId == PlaybackRows.PreferredTranscodeVideoCodec.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = transcodeVideoCodecTitle,
                                items = TranscodeVideoCodecPreference.entries,
                                label = { transcodeVideoCodecLabels.getValue(it) },
                                isSelected = { it == preferences.preferredTranscodeVideoCodec },
                                onSelect = { viewModel.edit { scope -> scope.playback.setPreferredTranscodeVideoCodec(it) } },
                            )
                        },
                    )
                    val transcodeAudioCodecTitle = rowTitle(PlaybackRows.PreferredTranscodeAudioCodec)
                    val transcodeAudioCodecLabels = TranscodeAudioCodecPreference.entries.associateWith { stringResource(transcodeAudioCodecLabelRes(it)) }
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.PreferredTranscodeAudioCodec),
                        title = transcodeAudioCodecTitle,
                        subtitle = rowSubtitle(PlaybackRows.PreferredTranscodeAudioCodec),
                        trailingText = transcodeAudioCodecLabels[preferences.preferredTranscodeAudioCodec]
                            ?: preferences.preferredTranscodeAudioCodec.name,
                        highlighted = highlightSettingId == PlaybackRows.PreferredTranscodeAudioCodec.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = transcodeAudioCodecTitle,
                                items = TranscodeAudioCodecPreference.entries,
                                label = { transcodeAudioCodecLabels.getValue(it) },
                                isSelected = { it == preferences.preferredTranscodeAudioCodec },
                                onSelect = { viewModel.edit { scope -> scope.playback.setPreferredTranscodeAudioCodec(it) } },
                            )
                        },
                    )
                    // The hand-tuned device-profile escape hatch — the
                    // mpv.conf multi-line text-editor shape.
                    val deviceProfileTitle = rowTitle(PlaybackRows.CustomDeviceProfileJson)
                    val deviceProfileHelper = stringResource(Res.string.settings_custom_device_profile_helper)
                    val deviceProfilePlaceholder = stringResource(Res.string.settings_custom_device_profile_placeholder)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.CustomDeviceProfileJson),
                        title = deviceProfileTitle,
                        subtitle = rowSubtitle(PlaybackRows.CustomDeviceProfileJson),
                        trailingText = if (preferences.customDeviceProfileJson.isBlank()) "" else "JSON",
                        highlighted = highlightSettingId == PlaybackRows.CustomDeviceProfileJson.id,
                        onClick = {
                            activePicker.value = PickerState.Text(
                                title = deviceProfileTitle,
                                initialText = preferences.customDeviceProfileJson,
                                helperText = deviceProfileHelper,
                                placeholder = deviceProfilePlaceholder,
                                onSave = { viewModel.edit { scope -> scope.playback.setCustomDeviceProfileJson(it) } },
                            )
                        },
                    )
                    // RESIDUAL (NetworkOfflineStore): the LAN quality cap —
                    // the picker reuses the streaming-quality labels; the
                    // wire value is the enum name.
                    val homeQualityTitle = rowTitle(PlaybackRows.HomeNetworkQuality)
                    val homeQualityNames = StreamingQuality.entries.map { it.name }
                    val homeQualityByName = StreamingQuality.entries.associate { it.name to it }
                    val currentHomeQuality = homeQualityByName[preferences.homeStreamingQuality]
                        ?: StreamingQuality.AUTO
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.HomeNetworkQuality),
                        title = homeQualityTitle,
                        subtitle = rowSubtitle(PlaybackRows.HomeNetworkQuality),
                        trailingText = qualityShorts[currentHomeQuality] ?: preferences.homeStreamingQuality,
                        highlighted = highlightSettingId == PlaybackRows.HomeNetworkQuality.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = homeQualityTitle,
                                items = homeQualityNames,
                                label = { name -> homeQualityByName[name]?.let { qualityLabels[it] ?: name } ?: name },
                                isSelected = { it == preferences.homeStreamingQuality },
                                onSelect = { name -> viewModel.edit { it.networkOffline.setHomeStreamingQuality(name) } },
                            )
                        },
                    )
                    // RESIDUAL action row: the connection-speed probe (the
                    // quality feature agent implements the measurement; the
                    // row renders the proposed cap when one exists).
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.SpeedTest),
                        title = rowTitle(PlaybackRows.SpeedTest),
                        subtitle = rowSubtitle(PlaybackRows.SpeedTest),
                        trailingText = when (val state = viewModel.speedTestResult) {
                            null -> ""
                            SpeedTestState.Running -> stringResource(Res.string.settings_speed_test_running)
                            SpeedTestState.Failed -> stringResource(Res.string.settings_speed_test_failed)
                            is SpeedTestState.Measured -> {
                                val oneDecimal = ((state.mbps * 10f).toInt()) / 10f
                                stringResource(
                                    Res.string.settings_speed_test_result,
                                    oneDecimal.toString(),
                                    state.suggestedTier.displayName,
                                )
                            }
                        },
                        highlighted = highlightSettingId == PlaybackRows.SpeedTest.id,
                        onClick = { viewModel.runSpeedTest() },
                    )
                    val liveLabels = LiveStreamOption.entries.associateWith { stringResource(liveStreamOptionLabelRes(it)) }
                    val liveTvStreamTitle = rowTitle(PlaybackRows.LiveStreamOption)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.LiveStreamOption),
                        title = rowTitle(PlaybackRows.LiveStreamOption),
                        subtitle = liveLabels[preferences.liveStreamOption] ?: preferences.liveStreamOption.displayName,
                        trailingText = preferences.liveStreamOption.displayName,
                        highlighted = highlightSettingId == PlaybackRows.LiveStreamOption.id,
                        onClick = {
                            activePicker.value = PickerState.List(
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
                    val audioDelayTitle = rowTitle(PlaybackRows.AudioDelay)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.AudioDelay),
                        title = rowTitle(PlaybackRows.AudioDelay),
                        subtitle = if (preferences.audioDelayMs == 0L) noAudioDelay else stringResource(Res.string.settings_audio_delay_value, preferences.audioDelayMs),
                        trailingText = if (preferences.audioDelayMs == 0L) offLabel2 else "${preferences.audioDelayMs}ms",
                        highlighted = highlightSettingId == PlaybackRows.AudioDelay.id,
                        onClick = {
                            activePicker.value = PickerState.Slider(
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
