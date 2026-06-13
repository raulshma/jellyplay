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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    LaunchedEffect(Unit) {
        if (isTv) {
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                if (focusRequester.tryRequestFocus("audio_init")) break
            }
        }
    }

    JellyPlayScreenScaffold(
        title = "Audio Player",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
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
                    title = "Audio Player",
                    summary = { "Default speed: ${if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"}" },
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
                        title = "Default Speed",
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) "Normal playback speed" else "${preferences.audioDefaultSpeed}x playback",
                        trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        highlighted = highlightSettingId == "audio_default_speed",
                        index = idx++, count = total,
                        onClick = { activeDialog = AudioSettingsDialog.AudioSpeedPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = "Auto-play Next",
                        subtitle = if (preferences.audioAutoplayNext) "Automatically plays next track" else "Manual track selection",
                        checked = preferences.audioAutoplayNext,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = "Audio Visualizer",
                        subtitle = if (preferences.audioVisualizerEnabled) "Real-time FFT audio visualizer active" else "Visualizer disabled",
                        checked = preferences.audioVisualizerEnabled,
                        highlighted = highlightSettingId == "audio_visualizer",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioVisualizerEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = "Sleep Timer",
                        subtitle = if (preferences.sleepTimerDurationMs == 0L) "No sleep timer set" else "${preferences.sleepTimerDurationMs / 60000} minutes",
                        trailingText = if (preferences.sleepTimerDurationMs == 0L) "Off" else "${preferences.sleepTimerDurationMs / 60000}m",
                        highlighted = highlightSettingId == "sleep_timer",
                        index = idx++, count = total,
                        onClick = { activeDialog = AudioSettingsDialog.SleepTimerPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Speakerphone,
                        title = "Audio Description",
                        subtitle = if (preferences.preferAudioDescription) "Prefer descriptive/narrated audio tracks" else "Standard audio tracks",
                        checked = preferences.preferAudioDescription,
                        highlighted = highlightSettingId == "audio_description",
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setPreferAudioDescription(it) },
                    )
                    if (showAdvanced) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Night Mode Volume",
                            subtitle = "Maximum volume level at night",
                            trailingText = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.NightModeVolumePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = "Night Mode Gain",
                            subtitle = "Loudness compensation",
                            trailingText = "${preferences.audioNightModeGain}",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.NightModeGainPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.PlayerSkipForward,
                            title = "Skip Prev Threshold",
                            subtitle = "Restart song if past this point",
                            trailingText = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.SkipPrevThresholdPicker },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PlaylistAdd,
                            title = "Gapless Playback",
                            subtitle = if (preferences.audioGaplessEnabled) "Seamless track transitions" else "Brief pause between tracks",
                            checked = preferences.audioGaplessEnabled,
                            highlighted = highlightSettingId == "gapless_playback",
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setGaplessEnabled(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Crossfade Duration",
                            subtitle = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s overlap between tracks" else "No crossfade",
                            trailingText = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s" else "Off",
                            highlighted = highlightSettingId == "crossfade",
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.CrossfadePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = "Preload Buffer",
                            subtitle = "Amount to buffer ahead during audio playback",
                            trailingText = preferences.audioPreloadBufferSize.displayName,
                            index = idx++, count = total,
                            onClick = { activeDialog = AudioSettingsDialog.AudioPreloadBufferPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = "Volume Normalization",
                            subtitle = when (preferences.audioNormalizationMode) {
                                AudioNormalizationMode.NONE -> "Off"
                                AudioNormalizationMode.DYNAMIC -> "Dynamic compression"
                                AudioNormalizationMode.TRACK -> "Per-track ReplayGain"
                                AudioNormalizationMode.ALBUM -> "Album-aware ReplayGain"
                            },
                            trailingText = when (preferences.audioNormalizationMode) {
                                AudioNormalizationMode.NONE -> "Off"
                                AudioNormalizationMode.DYNAMIC -> "Dynamic"
                                AudioNormalizationMode.TRACK -> "Track"
                                AudioNormalizationMode.ALBUM -> "Album"
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
                                title = "ReplayGain Pre-Amp",
                                subtitle = "Fine-tune target loudness",
                                trailingText = "${if (preferences.replayGainPreAmpDb >= 0) "+" else ""}${String.format("%.1f", preferences.replayGainPreAmpDb)} dB",
                                index = idx++, count = total,
                                onClick = { activeDialog = AudioSettingsDialog.PreAmpPicker },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Adjustments,
                            title = "Equalizer",
                            subtitle = if (preferences.equalizerEnabled) "10-band equalizer active" else "Equalizer disabled",
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
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setNightModeEnabled(it) },
                        )
                        if (preferences.nightModeEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Moon,
                                title = "Night Mode Strength",
                                subtitle = preferences.nightModeStrength.displayName,
                                trailingText = preferences.nightModeStrength.displayName,
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
}
