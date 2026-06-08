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
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<AudioSettingsDialog>(AudioSettingsDialog.None) }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Audio Player",
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
                    icon = Tabler.Outline.Music,
                    title = "Audio Player",
                    summary = { "Default speed: ${if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val audioItems = mutableListOf<Int>()
                    for (i in 0..20) audioItems.add(i)
                    var idx = 0
                    val total = run {
                        var c = 9
                        if (preferences.equalizerEnabled) c++
                        if (preferences.dialogueBoostEnabled) c++
                        if (preferences.nightModeEnabled) c++
                        if (preferences.audioNormalizationMode != AudioNormalizationMode.NONE) c++
                        c
                    }

                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Default Speed",
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) "Normal playback speed" else "${preferences.audioDefaultSpeed}x playback",
                        trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        index = idx++, count = total,
                        onClick = { activeDialog = AudioSettingsDialog.AudioSpeedPicker },
                    )
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
                        title = "Auto-play Next",
                        subtitle = if (preferences.audioAutoplayNext) "Automatically plays next track" else "Manual track selection",
                        checked = preferences.audioAutoplayNext,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = "Gapless Playback",
                        subtitle = if (preferences.audioGaplessEnabled) "Seamless track transitions" else "Brief pause between tracks",
                        checked = preferences.audioGaplessEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setGaplessEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = "Crossfade Duration",
                        subtitle = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s overlap between tracks" else "No crossfade",
                        trailingText = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s" else "Off",
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
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        onClick = { activeDialog = AudioSettingsDialog.EqualizerEditor },
                    )
                    if (preferences.equalizerEnabled) {
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
                    SettingListItem(
                        icon = Tabler.Outline.WaveSine,
                        title = "Reverb",
                        subtitle = preferences.reverbPreset.displayName,
                        trailingText = preferences.reverbPreset.displayName,
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
}
