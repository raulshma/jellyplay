package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

sealed class AudioSettingsDialog {
    object None : AudioSettingsDialog()
    object AudioSpeedPicker : AudioSettingsDialog()
    object AudioPreloadBufferPicker : AudioSettingsDialog()
    object NightModeVolumePicker : AudioSettingsDialog()
    object NightModeGainPicker : AudioSettingsDialog()
    object SkipPrevThresholdPicker : AudioSettingsDialog()
    object CrossfadePicker : AudioSettingsDialog()
    object EqualizerEditor : AudioSettingsDialog()
    object NormalizationModePicker : AudioSettingsDialog()
    object PreAmpPicker : AudioSettingsDialog()
    object EqualizerPresetPicker : AudioSettingsDialog()
    object ChannelMixModePicker : AudioSettingsDialog()
    object SleepTimerPicker : AudioSettingsDialog()
    object LrBalancePicker : AudioSettingsDialog()
    object PitchShiftPicker : AudioSettingsDialog()
    object VolumeBoostGainPicker : AudioSettingsDialog()
    object AudioCacheSizePicker : AudioSettingsDialog()
    object AudioPrefetchLookaheadPicker : AudioSettingsDialog()
    object AudioPrefetchBackfillPicker : AudioSettingsDialog()
    object AudioCacheNetworkPolicyPicker : AudioSettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<AudioSettingsDialog>(AudioSettingsDialog.None) }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "audio_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_audio_player_title),
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
                    icon = Tabler.Outline.Music,
                    title = stringResource(R.string.settings_audio_player_title),
                    summary = { stringResource(R.string.settings_default_speed_value, if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x") },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    var idx = 0
                    val total = run {
                        var c = 5 // Default Speed, Auto-play Next, Visualizer, Sleep Timer, Audio Description
                        if (showAdvanced) {
                            c += 7 // Night Volume, Night Gain, Skip Prev, Gapless, Crossfade, Preload, Normalization
                            if (preferences.audioNormalizationMode == AudioNormalizationMode.TRACK ||
                                preferences.audioNormalizationMode == AudioNormalizationMode.ALBUM) c++ // Pre-Amp
                            c += 1 // Equalizer toggle
                            if (preferences.equalizerEnabled) c += 2 // Equalizer Preset, Dialogue Boost toggle
                            if (preferences.dialogueBoostEnabled) c++ // Dialogue Boost Strength
                            c += 1 // Night Mode toggle
                            if (preferences.nightModeEnabled) c++ // Night Mode Strength
                            c += 1 // Bass Boost toggle
                            if (preferences.bassBoostEnabled) c++ // Bass Boost Strength
                            c += 1 // Virtualizer toggle
                            if (preferences.virtualizerEnabled) c++ // Virtualizer Strength
                            c += 1 // Volume Boost toggle
                            if (preferences.volumeBoostEnabled) c++ // Volume Boost Gain
                            c += 5 // Reverb, Auto-EQ, Channel Mix toggle, L/R Balance, Pitch Shift
                            if (preferences.channelMixEnabled) c++ // Channel Mix Mode
                        }
                        c
                    }

                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(R.string.settings_audio_default_speed),
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) stringResource(R.string.settings_audio_default_speed_normal) else stringResource(R.string.settings_audio_default_speed_value, "${preferences.audioDefaultSpeed}x"),
                        trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        highlighted = highlightSettingId == "audio_default_speed",
                        index = idx++, count = total,
                        onClick = { activeDialog = AudioSettingsDialog.AudioSpeedPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = stringResource(R.string.settings_audio_auto_play_next),
                        subtitle = if (preferences.audioAutoplayNext) stringResource(R.string.settings_audio_auto_play_on) else stringResource(R.string.settings_audio_auto_play_off),
                        checked = preferences.audioAutoplayNext,
                        highlighted = highlightSettingId == "audio_autoplay_next",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = stringResource(R.string.settings_audio_visualizer),
                        subtitle = if (preferences.audioVisualizerEnabled) stringResource(R.string.settings_audio_visualizer_on) else stringResource(R.string.settings_audio_visualizer_off),
                        checked = preferences.audioVisualizerEnabled,
                        highlighted = highlightSettingId == "audio_visualizer",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioVisualizerEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = stringResource(R.string.settings_sleep_timer),
                        subtitle = if (preferences.sleepTimerDurationMs == 0L) stringResource(R.string.settings_sleep_timer_off) else stringResource(R.string.settings_sleep_timer_minutes, preferences.sleepTimerDurationMs / 60000),
                        trailingText = if (preferences.sleepTimerDurationMs == 0L) "Off" else "${preferences.sleepTimerDurationMs / 60000}m",
                        highlighted = highlightSettingId == "sleep_timer",
                        index = idx++, count = total,
                        onClick = { activeDialog = AudioSettingsDialog.SleepTimerPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Speakerphone,
                        title = stringResource(R.string.settings_audio_description),
                        subtitle = if (preferences.preferAudioDescription) stringResource(R.string.settings_audio_description_on) else stringResource(R.string.settings_audio_description_off),
                        checked = preferences.preferAudioDescription,
                        highlighted = highlightSettingId == "audio_description",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setPreferAudioDescription(it) },
                    )
                    if (showAdvanced) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = stringResource(R.string.settings_night_mode_volume),
                            subtitle = stringResource(R.string.settings_night_mode_volume_subtitle),
                            trailingText = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                            highlighted = highlightSettingId == "night_mode_volume",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.NightModeVolumePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(R.string.settings_night_mode_gain),
                            subtitle = stringResource(R.string.settings_night_mode_gain_subtitle),
                            trailingText = "${preferences.audioNightModeGain}",
                            highlighted = highlightSettingId == "night_mode_gain",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.NightModeGainPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.PlayerSkipForward,
                            title = stringResource(R.string.settings_skip_prev_threshold),
                            subtitle = stringResource(R.string.settings_skip_prev_threshold_subtitle),
                            trailingText = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                            highlighted = highlightSettingId == "audio_skip_prev_threshold",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.SkipPrevThresholdPicker },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PlaylistAdd,
                            title = stringResource(R.string.settings_gapless_playback),
                            subtitle = if (preferences.audioGaplessEnabled) stringResource(R.string.settings_gapless_on) else stringResource(R.string.settings_gapless_off),
                            checked = preferences.audioGaplessEnabled,
                            highlighted = highlightSettingId == "gapless_playback",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setGaplessEnabled(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = stringResource(R.string.settings_crossfade_duration),
                            subtitle = if (preferences.audioCrossfadeDurationMs > 0) stringResource(R.string.settings_crossfade_on, preferences.audioCrossfadeDurationMs / 1000) else stringResource(R.string.settings_crossfade_off),
                            trailingText = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s" else "Off",
                            highlighted = highlightSettingId == "crossfade",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.CrossfadePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = stringResource(R.string.settings_preload_buffer),
                            subtitle = stringResource(R.string.settings_preload_buffer_subtitle),
                            trailingText = preferences.audioPreloadBufferSize.displayName,
                            highlighted = highlightSettingId == "audio_preload_buffer",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.AudioPreloadBufferPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(R.string.settings_volume_normalization),
                            subtitle = when (preferences.audioNormalizationMode) {
                                AudioNormalizationMode.NONE -> stringResource(R.string.settings_norm_off)
                                AudioNormalizationMode.DYNAMIC -> stringResource(R.string.settings_norm_dynamic)
                                AudioNormalizationMode.TRACK -> stringResource(R.string.settings_norm_track)
                                AudioNormalizationMode.ALBUM -> stringResource(R.string.settings_norm_album)
                            },
                            trailingText = when (preferences.audioNormalizationMode) {
                                AudioNormalizationMode.NONE -> stringResource(R.string.settings_norm_off)
                                AudioNormalizationMode.DYNAMIC -> stringResource(R.string.settings_norm_dynamic_short)
                                AudioNormalizationMode.TRACK -> stringResource(R.string.settings_norm_track_short)
                                AudioNormalizationMode.ALBUM -> stringResource(R.string.settings_norm_album_short)
                            },
                            highlighted = highlightSettingId == "volume_normalization",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.NormalizationModePicker },
                        )
                        if (preferences.audioNormalizationMode == AudioNormalizationMode.TRACK ||
                            preferences.audioNormalizationMode == AudioNormalizationMode.ALBUM
                        ) {
                            SettingListItem(
                                icon = Tabler.Outline.Adjustments,
                                title = stringResource(R.string.settings_replaygain_preamp),
                                subtitle = stringResource(R.string.settings_replaygain_preamp_subtitle),
                                trailingText = "${if (preferences.replayGainPreAmpDb >= 0) "+" else ""}${String.format("%.1f", preferences.replayGainPreAmpDb)} dB",
                                highlighted = highlightSettingId == "replaygain_preamp",
                                index = idx++, count = total,
                                onClick = { activeDialog = AudioSettingsDialog.PreAmpPicker },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(R.string.settings_equalizer),
                            subtitle = if (preferences.equalizerEnabled) stringResource(R.string.settings_equalizer_on) else stringResource(R.string.settings_equalizer_off),
                            checked = preferences.equalizerEnabled,
                            highlighted = highlightSettingId == "equalizer",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                            onClick = { activeDialog = AudioSettingsDialog.EqualizerEditor },
                        )
                        if (preferences.equalizerEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Adjustments,
                                title = "Equalizer Preset",
                                subtitle = "Quick preset: ${preferences.equalizerPreset.displayName}",
                                trailingText = preferences.equalizerPreset.displayName,
                                highlighted = highlightSettingId == "equalizer_preset",
                                index = idx++, count = total,
                                onClick = { activeDialog = AudioSettingsDialog.EqualizerPresetPicker },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Microphone2,
                                title = "Dialogue Boost",
                                subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                                checked = preferences.dialogueBoostEnabled,
                                index = idx++, count = total,
                                onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                            )
                        }
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
                        SettingToggleItem(
                            icon = Tabler.Outline.Gauge,
                            title = "Night Mode",
                            subtitle = if (preferences.nightModeEnabled) preferences.nightModeStrength.displayName else "Off",
                            checked = preferences.nightModeEnabled,
                            highlighted = highlightSettingId == "night_mode",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setNightModeEnabled(it) },
                        )
                        if (preferences.nightModeEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Moon,
                                title = "Night Mode Strength",
                                subtitle = preferences.nightModeStrength.displayName,
                                trailingText = preferences.nightModeStrength.displayName,
                                highlighted = highlightSettingId == "night_mode_strength",
                                index = idx++, count = total,
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    val currentIndex = strengths.indexOf(preferences.nightModeStrength)
                                    val nextIndex = (currentIndex + 1) % strengths.size
                                    viewModel.setNightModeStrength(strengths[nextIndex])
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.WaveSine,
                            title = "Bass Boost",
                            subtitle = if (preferences.bassBoostEnabled) preferences.bassBoostStrength.displayName else "Off",
                            checked = preferences.bassBoostEnabled,
                            highlighted = highlightSettingId == "bass_boost",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setBassBoostEnabled(it) },
                        )
                        if (preferences.bassBoostEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.WaveSine,
                                title = "Bass Boost Strength",
                                subtitle = preferences.bassBoostStrength.displayName,
                                trailingText = preferences.bassBoostStrength.displayName,
                                highlighted = highlightSettingId == "bass_boost_strength",
                                index = idx++, count = total,
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    val currentIndex = strengths.indexOf(preferences.bassBoostStrength)
                                    val nextIndex = (currentIndex + 1) % strengths.size
                                    viewModel.setBassBoostStrength(strengths[nextIndex])
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = "Virtualizer / Spatial Audio",
                            subtitle = if (preferences.virtualizerEnabled) "${preferences.virtualizerStrength / 10}% strength" else "Off",
                            checked = preferences.virtualizerEnabled,
                            highlighted = highlightSettingId == "virtualizer",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setVirtualizerEnabled(it) },
                        )
                        if (preferences.virtualizerEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Speakerphone,
                                title = "Virtualizer Strength",
                                subtitle = "${preferences.virtualizerStrength / 10}%",
                                trailingText = "${preferences.virtualizerStrength / 10}%",
                                highlighted = highlightSettingId == "virtualizer_strength",
                                index = idx++, count = total,
                                onClick = {
                                    val steps = listOf(0, 200, 400, 500, 600, 800, 1000)
                                    val currentIdx = steps.indexOf(preferences.virtualizerStrength).coerceAtLeast(0)
                                    val nextIdx = (currentIdx + 1) % steps.size
                                    viewModel.setVirtualizerStrength(steps[nextIdx])
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = "Volume Boost",
                            subtitle = if (preferences.volumeBoostEnabled) "+${"%.1f".format(preferences.volumeBoostGain / 100.0)} dB gain" else "Off",
                            checked = preferences.volumeBoostEnabled,
                            highlighted = highlightSettingId == "volume_boost",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setVolumeBoostEnabled(it) },
                        )
                        if (preferences.volumeBoostEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Speakerphone,
                                title = "Volume Boost Gain",
                                subtitle = "Loudness boost level",
                                trailingText = "+${"%.1f".format(preferences.volumeBoostGain / 100.0)} dB",
                                highlighted = highlightSettingId == "volume_boost_gain",
                                index = idx++, count = total,
                                onClick = { activeDialog = AudioSettingsDialog.VolumeBoostGainPicker },
                            )
                        }
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = "Reverb",
                            subtitle = preferences.reverbPreset.displayName,
                            trailingText = preferences.reverbPreset.displayName,
                            highlighted = highlightSettingId == "reverb",
                            index = idx++, count = total,
                            onClick = {
                                val presets = com.raulshma.jellyplay.core.model.ReverbPreset.entries
                                val currentIndex = presets.indexOf(preferences.reverbPreset)
                                val nextIndex = (currentIndex + 1) % presets.size
                                viewModel.setReverbPreset(presets[nextIndex])
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Wand,
                            title = "Auto-EQ by Genre",
                            subtitle = if (preferences.autoEqByGenre) "Automatically applies EQ preset based on genre" else "Off",
                            checked = preferences.autoEqByGenre,
                            highlighted = highlightSettingId == "auto_eq_by_genre",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setAutoEqByGenre(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = "Channel Mixing",
                            subtitle = if (preferences.channelMixEnabled) "Surround mixing active" else "Stereo bypass",
                            checked = preferences.channelMixEnabled,
                            highlighted = highlightSettingId == "channel_mixing",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setChannelMixEnabled(it) },
                        )
                        if (preferences.channelMixEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Speakerphone,
                                title = "Channel Mix Mode",
                                subtitle = preferences.channelMixMode.displayName,
                                trailingText = preferences.channelMixMode.displayName,
                                highlighted = highlightSettingId == "channel_mix_mode",
                                index = idx++, count = total,
                                onClick = { activeDialog = AudioSettingsDialog.ChannelMixModePicker },
                            )
                        }
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = "L/R Balance",
                            subtitle = if (preferences.lrBalance == 0f) "Center" else if (preferences.lrBalance < 0f) "Left" else "Right",
                            trailingText = if (preferences.lrBalance == 0f) "Center" else String.format("%.2f", preferences.lrBalance),
                            highlighted = highlightSettingId == "lr_balance",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.LrBalancePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = "Pitch Shift",
                            subtitle = if (preferences.pitchSemitones == 0f) "Normal pitch" else "${if (preferences.pitchSemitones > 0) "+" else ""}${preferences.pitchSemitones} semitones",
                            trailingText = if (preferences.pitchSemitones == 0f) "0" else "${if (preferences.pitchSemitones > 0) "+" else ""}${preferences.pitchSemitones}",
                            highlighted = highlightSettingId == "pitch_shift",
                            index = idx, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.PitchShiftPicker },
                        )
                }
            }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 19,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = stringResource(R.string.settings_audio_caching_title),
                    summary = { stringResource(R.string.settings_audio_caching_summary) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = false,
                ) {
                    var cacheIdx = 0
                    val cacheTotal = 6
                    SettingToggleItem(
                        icon = Tabler.Outline.Database,
                        title = stringResource(R.string.settings_audio_caching_enable),
                        subtitle = if (preferences.audioCachingEnabled)
                            stringResource(R.string.settings_audio_caching_on)
                        else stringResource(R.string.settings_audio_caching_off),
                        checked = preferences.audioCachingEnabled,
                        highlighted = highlightSettingId == "audio_caching_enabled",
                        index = cacheIdx++, count = cacheTotal,
                        onCheckedChange = { viewModel.setAudioCachingEnabled(it) },
                    )
                    if (preferences.audioCachingEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.DeviceFloppy,
                            title = stringResource(R.string.settings_audio_cache_size),
                            subtitle = stringResource(R.string.settings_audio_cache_size_subtitle),
                            trailingText = "${preferences.audioCacheSizeMb} MB",
                            highlighted = highlightSettingId == "audio_cache_size",
                            index = cacheIdx++, count = cacheTotal,
                            onClick = { activeDialog = AudioSettingsDialog.AudioCacheSizePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.ListNumbers,
                            title = stringResource(R.string.settings_audio_prefetch_lookahead),
                            subtitle = stringResource(R.string.settings_audio_prefetch_lookahead_subtitle),
                            trailingText = "${preferences.audioPrefetchLookahead}",
                            highlighted = highlightSettingId == "audio_prefetch_lookahead",
                            index = cacheIdx++, count = cacheTotal,
                            onClick = { activeDialog = AudioSettingsDialog.AudioPrefetchLookaheadPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.History,
                            title = stringResource(R.string.settings_audio_prefetch_backfill),
                            subtitle = stringResource(R.string.settings_audio_prefetch_backfill_subtitle),
                            trailingText = "${preferences.audioPrefetchBackfill}",
                            highlighted = highlightSettingId == "audio_prefetch_backfill",
                            index = cacheIdx++, count = cacheTotal,
                            onClick = { activeDialog = AudioSettingsDialog.AudioPrefetchBackfillPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Wifi,
                            title = stringResource(R.string.settings_audio_cache_network_policy),
                            subtitle = preferences.audioCacheNetworkPolicy.displayName,
                            trailingText = preferences.audioCacheNetworkPolicy.displayName,
                            highlighted = highlightSettingId == "audio_cache_network_policy",
                            index = cacheIdx++, count = cacheTotal,
                            onClick = { activeDialog = AudioSettingsDialog.AudioCacheNetworkPolicyPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Trash,
                            title = stringResource(R.string.settings_audio_cache_clear),
                            subtitle = stringResource(R.string.settings_audio_cache_clear_subtitle),
                            trailingText = "",
                            highlighted = highlightSettingId == "audio_cache_clear",
                            index = cacheIdx++, count = cacheTotal,
                            onClick = { viewModel.clearAudioCache() },
                        )
                    }
                }
            }
        }
        }
    }

    if (activeDialog is AudioSettingsDialog.AudioSpeedPicker) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        SettingsChipPickerSheet(
            title = "Default Audio Speed",
            options = speeds.map { if (it == 1.0f) "1x" else "${it}x" },
            selectedIndex = speeds.indexOf(preferences.audioDefaultSpeed),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setAudioDefaultSpeed(speeds[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.AudioPreloadBufferPicker) {
        SettingsListPickerSheet(
            title = "Audio Preload Buffer Size",
            items = PreloadBufferSize.entries,
            label = { it.displayName },
            subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
            isSelected = { it == preferences.audioPreloadBufferSize },
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = {
                viewModel.setAudioPreloadBufferSize(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.EqualizerPresetPicker) {
        val presets = EqualizerPreset.entries
        SettingsListPickerSheet(
            title = "Equalizer Preset",
            items = presets,
            label = { it.displayName },
            isSelected = { it == preferences.equalizerPreset },
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = {
                viewModel.setEqualizerPreset(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.ChannelMixModePicker) {
        val modes = ChannelMixMode.entries
        SettingsListPickerSheet(
            title = "Channel Mix Mode",
            items = modes,
            label = { it.displayName },
            isSelected = { it == preferences.channelMixMode },
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = {
                viewModel.setChannelMixMode(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.SleepTimerPicker) {
        val options = listOf(0L, 15 * 60000L, 30 * 60000L, 45 * 60000L, 60 * 60000L, 120 * 60000L)
        val labels = listOf("Off", "15 minutes", "30 minutes", "45 minutes", "1 hour", "2 hours")
        SettingsListPickerSheet(
            title = "Sleep Timer Duration",
            items = options,
            label = { labels[options.indexOf(it)] },
            isSelected = { it == preferences.sleepTimerDurationMs },
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = {
                viewModel.setSleepTimerDurationMs(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.LrBalancePicker) {
        SettingsSliderSheet(
            title = "L/R Balance",
            value = preferences.lrBalance,
            valueRange = -1.0f..1.0f,
            steps = 20,
            valueLabel = { if (it == 0f) "Center" else if (it < 0f) "${(it * -100).toInt()}% Left" else "${(it * 100).toInt()}% Right" },
            rangeStartLabel = "Left",
            rangeEndLabel = "Right",
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onConfirm = {
                viewModel.setLrBalance(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.PitchShiftPicker) {
        SettingsSliderSheet(
            title = "Pitch Shift",
            value = preferences.pitchSemitones,
            valueRange = -12.0f..12.0f,
            steps = 24,
            valueLabel = { if (it == 0f) "Normal pitch" else "${if (it > 0) "+" else ""}${it.toInt()} semitones" },
            rangeStartLabel = "-12 semitones",
            rangeEndLabel = "+12 semitones",
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onConfirm = {
                viewModel.setPitchSemitones(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.NightModeVolumePicker) {
        SettingsSliderSheet(
            title = "Night Mode Volume",
            value = preferences.audioNightModeVolume,
            valueRange = 0.1f..0.8f,
            steps = 6,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "10%",
            rangeEndLabel = "80%",
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onConfirm = {
                viewModel.setAudioNightModeVolume(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.NightModeGainPicker) {
        SettingsSliderSheet(
            title = "Night Mode Loudness Gain",
            value = preferences.audioNightModeGain.toFloat(),
            valueRange = 0f..3000f,
            steps = 29,
            valueLabel = { "${it.toInt()}" },
            rangeStartLabel = "0",
            rangeEndLabel = "3000",
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onConfirm = {
                viewModel.setAudioNightModeGain(it.toInt())
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.SkipPrevThresholdPicker) {
        val thresholds = listOf(1_000L, 2_000L, 3_000L, 5_000L, 7_000L, 10_000L)
        SettingsChipPickerSheet(
            title = "Skip Previous Threshold",
            options = thresholds.map { "${it / 1000}s" },
            selectedIndex = thresholds.indexOf(preferences.audioSkipPreviousThresholdMs),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setAudioSkipPreviousThresholdMs(thresholds[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.CrossfadePicker) {
        val durations = listOf(0L, 2000L, 3000L, 5000L, 8000L, 12000L)
        SettingsChipPickerSheet(
            title = "Crossfade Duration",
            options = durations.map { if (it == 0L) "Off" else "${it / 1000}s" },
            selectedIndex = durations.indexOf(preferences.audioCrossfadeDurationMs),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setCrossfadeDurationMs(durations[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.NormalizationModePicker) {
        val modes = AudioNormalizationMode.entries
        SettingsChipPickerSheet(
            title = "Volume Normalization",
            options = modes.map { it.displayName },
            selectedIndex = modes.indexOf(preferences.audioNormalizationMode),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setAudioNormalizationMode(modes[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.PreAmpPicker) {
        SettingsSliderSheet(
            title = "ReplayGain Pre-Amp",
            value = preferences.replayGainPreAmpDb,
            valueRange = -15f..15f,
            steps = 59,
            valueLabel = { "${if (it >= 0) "+" else ""}${String.format("%.1f", it)} dB" },
            rangeStartLabel = "-15 dB",
            rangeEndLabel = "+15 dB",
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onConfirm = {
                viewModel.setReplayGainPreAmpDb(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.EqualizerEditor) {
        val bandLevels = remember(preferences.equalizerSettings.bandLevels) {
            mutableStateListOf<Int>().apply { addAll(preferences.equalizerSettings.bandLevels) }
        }
        AdaptiveSheet(onDismissRequest = { activeDialog = AudioSettingsDialog.None }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "Equalizer",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f),
                ) {
                    items(EqualizerSettings.BAND_FREQUENCIES.size, key = { it }, contentType = { "band" }) { i ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${EqualizerSettings.BAND_FREQUENCIES[i]} Hz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("-15", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                                Slider(
                                    value = (bandLevels[i] + 15).toFloat(),
                                    onValueChange = { bandLevels[i] = (it - 15).toInt() },
                                    valueRange = 0f..30f,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("+15", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { activeDialog = AudioSettingsDialog.None }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        viewModel.setEqualizerSettings(EqualizerSettings(bandLevels.toList()))
                        activeDialog = AudioSettingsDialog.None
                    }) { Text("Apply") }
                }
            }
        }
    }

    if (activeDialog is AudioSettingsDialog.VolumeBoostGainPicker) {
        SettingsSliderSheet(
            title = "Volume Boost Gain",
            value = preferences.volumeBoostGain.toFloat(),
            valueRange = 0f..3000f,
            steps = 30,
            valueLabel = { "+${it.toInt() / 100} dB" },
            rangeStartLabel = "0 dB",
            rangeEndLabel = "+30 dB",
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onConfirm = {
                viewModel.setVolumeBoostGain(it.toInt())
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.AudioCacheSizePicker) {
        val sizes = listOf(128, 256, 512, 1024, 2048, 4096)
        SettingsChipPickerSheet(
            title = stringResource(R.string.settings_audio_cache_size),
            options = sizes.map { "$it MB" },
            selectedIndex = sizes.indexOf(preferences.audioCacheSizeMb),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setAudioCacheSizeMb(sizes[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.AudioPrefetchLookaheadPicker) {
        val lookahead = listOf(0, 1, 2, 3, 5, 8)
        SettingsChipPickerSheet(
            title = stringResource(R.string.settings_audio_prefetch_lookahead),
            options = lookahead.map { if (it == 0) "Off" else "$it" },
            selectedIndex = lookahead.indexOf(preferences.audioPrefetchLookahead),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setAudioPrefetchLookahead(lookahead[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.AudioPrefetchBackfillPicker) {
        val backfill = listOf(0, 1, 2, 5, 10, 20)
        SettingsChipPickerSheet(
            title = stringResource(R.string.settings_audio_prefetch_backfill),
            options = backfill.map { if (it == 0) "Off" else "$it" },
            selectedIndex = backfill.indexOf(preferences.audioPrefetchBackfill),
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = { index ->
                viewModel.setAudioPrefetchBackfill(backfill[index])
                activeDialog = AudioSettingsDialog.None
            },
        )
    }

    if (activeDialog is AudioSettingsDialog.AudioCacheNetworkPolicyPicker) {
        val policies = AudioCacheNetworkPolicy.entries
        SettingsListPickerSheet(
            title = stringResource(R.string.settings_audio_cache_network_policy),
            items = policies,
            label = { it.displayName },
            isSelected = { it == preferences.audioCacheNetworkPolicy },
            onDismiss = { activeDialog = AudioSettingsDialog.None },
            onSelect = {
                viewModel.setAudioCacheNetworkPolicy(it)
                activeDialog = AudioSettingsDialog.None
            },
        )
    }
}
