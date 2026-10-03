package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.formatOneDecimal
import androidx.compose.runtime.LaunchedEffect
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_auto_play_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_auto_play_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_auto_play_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_clear
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_clear_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_network_policy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_caching_enable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_caching_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_caching_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_caching_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_caching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_default_speed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_default_speed_normal
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_description
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_description_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_description_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_player_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_prefetch_backfill
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_prefetch_backfill_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_prefetch_lookahead
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_prefetch_lookahead_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_visualizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_visualizer_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_visualizer_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_eq_genre
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_eq_genre_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_balance_center
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_balance_left
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_balance_right
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bass_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bass_boost_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_channel_mix_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_channel_mixing
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_channel_mixing_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_channel_mixing_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_crossfade_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_crossfade_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_crossfade_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_speed_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_default_speed_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dialogue_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dialogue_boost_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer_preset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer_preset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gain_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gapless_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gapless_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gapless_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lr_balance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_gain
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_gain_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_volume
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_volume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_album
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_album_short
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_dynamic
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_dynamic_short
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_track
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_norm_track_short
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pitch_normal
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pitch_shift
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preload_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preload_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_replaygain_preamp
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_replaygain_preamp_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reverb
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_prev_threshold
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_prev_threshold_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_15_min
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_1_hour
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_2_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_30_min
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_45_min
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_strength_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_virtualizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_virtualizer_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_boost_gain
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_boost_gain_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_normalization

/**
 * The audio screen's [RowAdmissionFlags] — the parent-toggle states the audio
 * group's declared [RowAdmission.WhenOn] gates resolve against (both
 * [audioScreenRowTotal] and the screen's emission `if`s read them; the
 * `volume_normalization` parent counts as on in the TRACK/ALBUM modes).
 * Pure (and internal) so the contract test can pin it.
 */
internal fun audioRowAdmissionFlags(showAdvanced: Boolean, preferences: AudioPreferences): RowAdmissionFlags =
    RowAdmissionFlags(
        showAdvanced = showAdvanced,
        parentsOn = rowParentsOn(
            AudioRows.VolumeNormalization.id to (
                preferences.audioNormalizationMode == AudioNormalizationMode.TRACK ||
                    preferences.audioNormalizationMode == AudioNormalizationMode.ALBUM
                ),
            AudioRows.Equalizer.id to preferences.equalizerEnabled,
            PlaybackRows.DialogueBoost.id to preferences.dialogueBoostEnabled,
            AudioRows.NightMode.id to preferences.nightModeEnabled,
            AudioRows.BassBoost.id to preferences.bassBoostEnabled,
            AudioRows.Virtualizer.id to preferences.virtualizerEnabled,
            AudioRows.VolumeBoost.id to preferences.volumeBoostEnabled,
            AudioRows.ChannelMixing.id to preferences.channelMixEnabled,
        ),
    )

/**
 * The audio group's `SettingsItemList(total = …)` row count, derived by
 * [rowTotalFor] from the [SettingsScreenGroups.audio] declaration (full
 * per-id admission coverage — the base gate is each record's `isAdvanced`
 * flag, the effect-dependent rows override with `All(Advanced, WhenOn)`).
 * The dialogue-boost pair renders inside this screen's equalizer block too,
 * but its declaration lives with the playback advanced-video group (both
 * screens render the shared audio-effects rows), so it is added from that
 * group through its declared gates: the toggle rides the equalizer block
 * (read through the preset row's declared gate), the strength row its own
 * declared `All(Advanced, WhenOn)` gate — the two screen-local terms
 * [rowTotalFor] cannot see.
 *
 * Pure (and internal) so the contract test can pin each conditional against
 * the declaration.
 */
internal fun audioScreenRowTotal(
    showAdvanced: Boolean,
    preferences: AudioPreferences,
): Int {
    val flags = audioRowAdmissionFlags(showAdvanced, preferences)
    return rowTotalFor(SettingsScreenGroups.audio, flags) +
        (if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.EqualizerPreset.id, flags)) 1 else 0) +
        (if (SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.DialogueBoostStrength.id, flags)) 1 else 0)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: AudioSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    // The declared row admissions both the row total and the emission `if`s
    // below read — one gate per id, declared beside the group items
    // (SettingsSearchItemGroup.rowAdmitted).
    val rowFlags = audioRowAdmissionFlags(showAdvanced, preferences)
    // The one remaining dialog slot (beside the activePicker): the co-located
    // EqualizerEditorSheet. The former sealed AudioSettingsDialog identity tag
    // carried a single real variant.
    var showEqualizerEditor by remember { mutableStateOf(false) }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_audio_player_title),
        onBack = onBack,
        focusTag = "audio_init",
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        pickerHost = true,
    ) { activePicker ->
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Music,
                    title = stringResource(Res.string.settings_audio_player_title),
                    summary = { stringResource(Res.string.settings_default_speed_value, if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x") },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingsItemList(total = audioScreenRowTotal(showAdvanced, preferences)) {
                    val audioDefaultSpeedTitle = rowTitle(AudioRows.AudioDefaultSpeed)
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = rowTitle(AudioRows.AudioDefaultSpeed),
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) stringResource(Res.string.settings_audio_default_speed_normal) else stringResource(Res.string.settings_audio_default_speed_value, "${preferences.audioDefaultSpeed}x"),
                        trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        highlighted = highlightSettingId == AudioRows.AudioDefaultSpeed.id,
                        onClick = {
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                activePicker.value = pickerChip(
                    title = audioDefaultSpeedTitle,
                    values = speeds,
                    current = preferences.audioDefaultSpeed,
                    label = { if (it == 1.0f) "1x" else "${it}x" },
                    onSelect = { speed -> viewModel.edit { it.audio.setAudioDefaultSpeed(speed) } },
                )
            },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = rowTitle(AudioRows.AudioAutoplayNext),
                        subtitle = if (preferences.audioAutoplayNext) stringResource(Res.string.settings_audio_auto_play_on) else stringResource(Res.string.settings_audio_auto_play_off),
                        checked = preferences.audioAutoplayNext,
                        highlighted = highlightSettingId == AudioRows.AudioAutoplayNext.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.audio.setAudioAutoplayNext(it) } },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = rowTitle(AudioRows.AudioVisualizer),
                        subtitle = if (preferences.audioVisualizerEnabled) stringResource(Res.string.settings_audio_visualizer_on) else stringResource(Res.string.settings_audio_visualizer_off),
                        checked = preferences.audioVisualizerEnabled,
                        highlighted = highlightSettingId == AudioRows.AudioVisualizer.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.audio.setAudioVisualizerEnabled(it) } },
                    )
                    val sleepTimerOffLabel = stringResource(Res.string.settings_off)
                    val sleepTimer15Min = stringResource(Res.string.settings_sleep_timer_15_min)
                    val sleepTimer30Min = stringResource(Res.string.settings_sleep_timer_30_min)
                    val sleepTimer45Min = stringResource(Res.string.settings_sleep_timer_45_min)
                    val sleepTimer1Hour = stringResource(Res.string.settings_sleep_timer_1_hour)
                    val sleepTimer2Hours = stringResource(Res.string.settings_sleep_timer_2_hours)
                    val sleepTimerDurationTitle = stringResource(Res.string.settings_sleep_timer_duration)
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = rowTitle(AudioRows.SleepTimer),
                        subtitle = if (preferences.sleepTimerDurationMs == 0L) stringResource(Res.string.settings_sleep_timer_off) else stringResource(Res.string.settings_sleep_timer_minutes, preferences.sleepTimerDurationMs / 60000),
                        trailingText = if (preferences.sleepTimerDurationMs == 0L) stringResource(Res.string.settings_off) else "${preferences.sleepTimerDurationMs / 60000}m",
                        highlighted = highlightSettingId == AudioRows.SleepTimer.id,
                        onClick = {
                            val options = listOf(0L, 15 * 60000L, 30 * 60000L, 45 * 60000L, 60 * 60000L, 120 * 60000L)
                            val sleepTimerLabels = listOf(
                                sleepTimerOffLabel,
                                sleepTimer15Min,
                                sleepTimer30Min,
                                sleepTimer45Min,
                                sleepTimer1Hour,
                                sleepTimer2Hours,
                            )
                            activePicker.value = PickerState.List(
                                title = sleepTimerDurationTitle,
                                items = options,
                                label = { sleepTimerLabels[options.indexOf(it)] },
                                isSelected = { it == preferences.sleepTimerDurationMs },
                                onSelect = { viewModel.edit { scope -> scope.audio.setSleepTimerDurationMs(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Speakerphone,
                        title = rowTitle(AudioRows.AudioDescription),
                        subtitle = if (preferences.preferAudioDescription) stringResource(Res.string.settings_audio_description_on) else stringResource(Res.string.settings_audio_description_off),
                        checked = preferences.preferAudioDescription,
                        highlighted = highlightSettingId == AudioRows.AudioDescription.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.subtitle.setPreferAudioDescription(it) } },
                    )
                    if (showAdvanced) {
                        val nightModeVolumeTitle = rowTitle(AudioRows.NightModeVolume)
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = rowTitle(AudioRows.NightModeVolume),
                            subtitle = stringResource(Res.string.settings_night_mode_volume_subtitle),
                            trailingText = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                            highlighted = highlightSettingId == AudioRows.NightModeVolume.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = nightModeVolumeTitle,
                                    value = preferences.audioNightModeVolume,
                                    valueRange = 0.1f..0.8f,
                                    steps = 6,
                                    valueLabel = { "${(it * 100).toInt()}%" },
                                    rangeStartLabel = "10%",
                                    rangeEndLabel = "80%",
                                    onConfirm = { viewModel.edit { scope -> scope.audio.setAudioNightModeVolume(it) } },
                                )
                            },
                        )
                        val nightModeGainTitle = rowTitle(AudioRows.NightModeGain)
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = rowTitle(AudioRows.NightModeGain),
                            subtitle = stringResource(Res.string.settings_night_mode_gain_subtitle),
                            trailingText = "${preferences.audioNightModeGain}",
                            highlighted = highlightSettingId == AudioRows.NightModeGain.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = nightModeGainTitle,
                                    value = preferences.audioNightModeGain.toFloat(),
                                    valueRange = 0f..3000f,
                                    steps = 29,
                                    valueLabel = { "${it.toInt()}" },
                                    rangeStartLabel = "0",
                                    rangeEndLabel = "3000",
                                    onConfirm = { viewModel.edit { scope -> scope.audio.setAudioNightModeGain(it.toInt()) } },
                                )
                            },
                        )
                        val skipPrevThresholdTitle = rowTitle(AudioRows.AudioSkipPrevThreshold)
                        SettingListItem(
                            icon = Tabler.Outline.PlayerSkipForward,
                            title = rowTitle(AudioRows.AudioSkipPrevThreshold),
                            subtitle = stringResource(Res.string.settings_skip_prev_threshold_subtitle),
                            trailingText = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                            highlighted = highlightSettingId == AudioRows.AudioSkipPrevThreshold.id,
                            onClick = {
                                val thresholds = listOf(1_000L, 2_000L, 3_000L, 5_000L, 7_000L, 10_000L)
                                activePicker.value = pickerChip(
                                    title = skipPrevThresholdTitle,
                                    values = thresholds,
                                    current = preferences.audioSkipPreviousThresholdMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.audio.setAudioSkipPreviousThresholdMs(ms) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PlaylistAdd,
                            title = rowTitle(AudioRows.GaplessPlayback),
                            subtitle = if (preferences.audioGaplessEnabled) stringResource(Res.string.settings_gapless_on) else stringResource(Res.string.settings_gapless_off),
                            checked = preferences.audioGaplessEnabled,
                            highlighted = highlightSettingId == AudioRows.GaplessPlayback.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audio.setAudioGaplessEnabled(it) } },
                        )
                        val crossfadeOffLabel = stringResource(Res.string.settings_off)
                        val crossfadeDurationTitle = rowTitle(AudioRows.Crossfade)
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = rowTitle(AudioRows.Crossfade),
                            subtitle = if (preferences.audioCrossfadeDurationMs > 0) stringResource(Res.string.settings_crossfade_on, preferences.audioCrossfadeDurationMs / 1000) else stringResource(Res.string.settings_crossfade_off),
                            trailingText = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s" else stringResource(Res.string.settings_off),
                            highlighted = highlightSettingId == AudioRows.Crossfade.id,
                            onClick = {
                                val durations = listOf(0L, 2000L, 3000L, 5000L, 8000L, 12000L)
                                activePicker.value = pickerChip(
                                    title = crossfadeDurationTitle,
                                    values = durations,
                                    current = preferences.audioCrossfadeDurationMs,
                                    label = { if (it == 0L) crossfadeOffLabel else "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.audio.setAudioCrossfadeDurationMs(ms) } },
                                )
                            },
                        )
                        val preloadBufferTitle = rowTitle(AudioRows.AudioPreloadBuffer)
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = rowTitle(AudioRows.AudioPreloadBuffer),
                            subtitle = stringResource(Res.string.settings_preload_buffer_subtitle),
                            trailingText = preferences.audioPreloadBufferSize.displayName,
                            highlighted = highlightSettingId == AudioRows.AudioPreloadBuffer.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = preloadBufferTitle,
                                    items = PreloadBufferSize.entries,
                                    label = { it.displayName },
                                    subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
                                    isSelected = { it == preferences.audioPreloadBufferSize },
                                    onSelect = { viewModel.edit { scope -> scope.audio.setAudioPreloadBufferSize(it) } },
                                )
                            },
                        )
                        val volumeNormalizationTitle = rowTitle(AudioRows.VolumeNormalization)
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = rowTitle(AudioRows.VolumeNormalization),
                            subtitle = when (preferences.audioNormalizationMode) {
                                AudioNormalizationMode.NONE -> stringResource(Res.string.settings_norm_off)
                                AudioNormalizationMode.DYNAMIC -> stringResource(Res.string.settings_norm_dynamic)
                                AudioNormalizationMode.TRACK -> stringResource(Res.string.settings_norm_track)
                                AudioNormalizationMode.ALBUM -> stringResource(Res.string.settings_norm_album)
                            },
                            trailingText = when (preferences.audioNormalizationMode) {
                                AudioNormalizationMode.NONE -> stringResource(Res.string.settings_norm_off)
                                AudioNormalizationMode.DYNAMIC -> stringResource(Res.string.settings_norm_dynamic_short)
                                AudioNormalizationMode.TRACK -> stringResource(Res.string.settings_norm_track_short)
                                AudioNormalizationMode.ALBUM -> stringResource(Res.string.settings_norm_album_short)
                            },
                            highlighted = highlightSettingId == AudioRows.VolumeNormalization.id,
                            onClick = {
                                val modes = AudioNormalizationMode.entries
                                activePicker.value = pickerChip(
                                    title = volumeNormalizationTitle,
                                    values = modes,
                                    current = preferences.audioNormalizationMode,
                                    label = { it.displayName },
                                    onSelect = { mode -> viewModel.edit { it.audio.setAudioNormalizationMode(mode) } },
                                )
                            },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.ReplaygainPreamp.id, rowFlags)) {
                            val replayGainPreAmpTitle = rowTitle(AudioRows.ReplaygainPreamp)
                            SettingListItem(
                                icon = Tabler.Outline.Adjustments,
                                title = rowTitle(AudioRows.ReplaygainPreamp),
                                subtitle = stringResource(Res.string.settings_replaygain_preamp_subtitle),
                                trailingText = "${if (preferences.replayGainPreAmpDb >= 0) "+" else ""}${formatOneDecimal(preferences.replayGainPreAmpDb.toDouble())} dB",
                                highlighted = highlightSettingId == AudioRows.ReplaygainPreamp.id,
                                onClick = {
                                    activePicker.value = PickerState.Slider(
                                        title = replayGainPreAmpTitle,
                                        value = preferences.replayGainPreAmpDb,
                                        valueRange = -15f..15f,
                                        steps = 59,
                                        valueLabel = { "${if (it >= 0) "+" else ""}${formatOneDecimal(it.toDouble())} dB" },
                                        rangeStartLabel = "-15 dB",
                                        rangeEndLabel = "+15 dB",
                                        onConfirm = { viewModel.edit { scope -> scope.audio.setReplayGainPreAmpDb(it) } },
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Adjustments,
                            title = rowTitle(AudioRows.Equalizer),
                            subtitle = if (preferences.equalizerEnabled) stringResource(Res.string.settings_equalizer_on) else stringResource(Res.string.settings_equalizer_off),
                            checked = preferences.equalizerEnabled,
                            highlighted = highlightSettingId == AudioRows.Equalizer.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setEqualizerEnabled(it) } },
                            onClick = { showEqualizerEditor = true },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.EqualizerPreset.id, rowFlags)) {
                            val equalizerPresetTitle = rowTitle(AudioRows.EqualizerPreset)
                            SettingListItem(
                                icon = Tabler.Outline.Adjustments,
                                title = rowTitle(AudioRows.EqualizerPreset),
                                subtitle = stringResource(Res.string.settings_equalizer_preset_subtitle, preferences.equalizerPreset.displayName),
                                trailingText = preferences.equalizerPreset.displayName,
                                highlighted = highlightSettingId == AudioRows.EqualizerPreset.id,
                                onClick = {
                                    val presets = EqualizerPreset.entries
                                    activePicker.value = PickerState.List(
                                        title = equalizerPresetTitle,
                                        items = presets,
                                        label = { it.displayName },
                                        isSelected = { it == preferences.equalizerPreset },
                                        onSelect = { viewModel.edit { scope -> scope.audioEffects.setEqualizerPreset(it) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Microphone2,
                                title = rowTitle(PlaybackRows.DialogueBoost),
                                subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else stringResource(Res.string.settings_off),
                                checked = preferences.dialogueBoostEnabled,
                                onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setDialogueBoostEnabled(it) } },
                            )
                        }
                        if (SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.DialogueBoostStrength.id, rowFlags)) {
                            val dialogueBoostStrengthTitle = stringResource(Res.string.settings_dialogue_boost_strength)
                            SettingListItem(
                                icon = Tabler.Outline.Music,
                                title = rowTitle(PlaybackRows.DialogueBoostStrength),
                                subtitle = preferences.dialogueBoostStrength.displayName,
                                trailingText = preferences.dialogueBoostStrength.displayName,
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
                        SettingToggleItem(
                            icon = Tabler.Outline.Gauge,
                            title = rowTitle(AudioRows.NightMode),
                            subtitle = if (preferences.nightModeEnabled) preferences.nightModeStrength.displayName else stringResource(Res.string.settings_off),
                            checked = preferences.nightModeEnabled,
                            highlighted = highlightSettingId == AudioRows.NightMode.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setNightModeEnabled(it) } },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.NightModeStrength.id, rowFlags)) {
                                val nightModeStrengthTitle = rowTitle(AudioRows.NightModeStrength)
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = rowTitle(AudioRows.NightModeStrength),
                                subtitle = preferences.nightModeStrength.displayName,
                                trailingText = preferences.nightModeStrength.displayName,
                                highlighted = highlightSettingId == AudioRows.NightModeStrength.id,
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    activePicker.value = pickerChip(
                                        title = nightModeStrengthTitle,
                                        values = strengths,
                                        current = preferences.nightModeStrength,
                                        label = { it.displayName },
                                        onSelect = { strength -> viewModel.edit { it.audioEffects.setNightModeStrength(strength) } },
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.WaveSine,
                            title = rowTitle(AudioRows.BassBoost),
                            subtitle = if (preferences.bassBoostEnabled) preferences.bassBoostStrength.displayName else stringResource(Res.string.settings_off),
                            checked = preferences.bassBoostEnabled,
                            highlighted = highlightSettingId == AudioRows.BassBoost.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setBassBoostEnabled(it) } },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.BassBoostStrength.id, rowFlags)) {
                                val bassBoostStrengthTitle = rowTitle(AudioRows.BassBoostStrength)
                                SettingListItem(
                                    icon = Tabler.Outline.WaveSine,
                                    title = rowTitle(AudioRows.BassBoostStrength),
                                subtitle = preferences.bassBoostStrength.displayName,
                                trailingText = preferences.bassBoostStrength.displayName,
                                highlighted = highlightSettingId == AudioRows.BassBoostStrength.id,
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    activePicker.value = pickerChip(
                                        title = bassBoostStrengthTitle,
                                        values = strengths,
                                        current = preferences.bassBoostStrength,
                                        label = { it.displayName },
                                        onSelect = { strength -> viewModel.edit { it.audioEffects.setBassBoostStrength(strength) } },
                                    )
                                },
                            )
                        }
                        val virtualizerStrengthSuffix = stringResource(Res.string.settings_strength_suffix)
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = rowTitle(AudioRows.Virtualizer),
                            subtitle = if (preferences.virtualizerEnabled) "${preferences.virtualizerStrength / 10}%$virtualizerStrengthSuffix" else stringResource(Res.string.settings_off),
                            checked = preferences.virtualizerEnabled,
                            highlighted = highlightSettingId == AudioRows.Virtualizer.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setVirtualizerEnabled(it) } },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.VirtualizerStrength.id, rowFlags)) {
                                val virtualizerStrengthTitle = rowTitle(AudioRows.VirtualizerStrength)
                                SettingListItem(
                                    icon = Tabler.Outline.Speakerphone,
                                    title = rowTitle(AudioRows.VirtualizerStrength),
                                subtitle = "${preferences.virtualizerStrength / 10}%",
                                trailingText = "${preferences.virtualizerStrength / 10}%",
                                highlighted = highlightSettingId == AudioRows.VirtualizerStrength.id,
                                onClick = {
                                    val stepsList = listOf(0, 200, 400, 500, 600, 800, 1000)
                                    activePicker.value = PickerState.List(
                                        title = virtualizerStrengthTitle,
                                        items = stepsList,
                                        label = { "${it / 10}%" },
                                        isSelected = { it == preferences.virtualizerStrength },
                                        onSelect = { viewModel.edit { scope -> scope.audioEffects.setVirtualizerStrength(it) } },
                                    )
                                },
                            )
                        }
                        val volumeBoostGainSuffix = stringResource(Res.string.settings_gain_suffix)
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = rowTitle(AudioRows.VolumeBoost),
                            subtitle = if (preferences.volumeBoostEnabled) "+${formatOneDecimal(preferences.volumeBoostGain / 100.0)} $volumeBoostGainSuffix" else stringResource(Res.string.settings_off),
                            checked = preferences.volumeBoostEnabled,
                            highlighted = highlightSettingId == AudioRows.VolumeBoost.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setVolumeBoostEnabled(it) } },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.VolumeBoostGain.id, rowFlags)) {
                                val volumeBoostGainTitle = rowTitle(AudioRows.VolumeBoostGain)
                            SettingListItem(
                                icon = Tabler.Outline.Speakerphone,
                                title = rowTitle(AudioRows.VolumeBoostGain),
                                subtitle = stringResource(Res.string.settings_volume_boost_gain_subtitle),
                                trailingText = "+${formatOneDecimal(preferences.volumeBoostGain / 100.0)} dB",
                                highlighted = highlightSettingId == AudioRows.VolumeBoostGain.id,
                                onClick = {
                                    activePicker.value = PickerState.Slider(
                                        title = volumeBoostGainTitle,
                                        value = preferences.volumeBoostGain.toFloat(),
                                        valueRange = 0f..3000f,
                                        steps = 30,
                                        valueLabel = { "+${it.toInt() / 100} dB" },
                                        rangeStartLabel = "0 dB",
                                        rangeEndLabel = "+30 dB",
                                        onConfirm = { viewModel.edit { scope -> scope.audioEffects.setVolumeBoostGain(it.toInt()) } },
                                    )
                                },
                            )
                        }
                        val reverbTitle = rowTitle(AudioRows.Reverb)
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = rowTitle(AudioRows.Reverb),
                            subtitle = preferences.reverbPreset.displayName,
                            trailingText = preferences.reverbPreset.displayName,
                            highlighted = highlightSettingId == AudioRows.Reverb.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = reverbTitle,
                                    items = ReverbPreset.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.reverbPreset },
                                    onSelect = { viewModel.edit { scope -> scope.audioEffects.setReverbPreset(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Wand,
                            title = rowTitle(AudioRows.AutoEqByGenre),
                            subtitle = if (preferences.autoEqByGenre) stringResource(Res.string.settings_auto_eq_genre_on) else stringResource(Res.string.settings_off),
                            checked = preferences.autoEqByGenre,
                            highlighted = highlightSettingId == AudioRows.AutoEqByGenre.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioEffects.setAutoEqByGenre(it) } },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = rowTitle(AudioRows.ChannelMixing),
                            subtitle = if (preferences.channelMixEnabled) stringResource(Res.string.settings_channel_mixing_on) else stringResource(Res.string.settings_channel_mixing_off),
                            checked = preferences.channelMixEnabled,
                            highlighted = highlightSettingId == AudioRows.ChannelMixing.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audio.setChannelMixEnabled(it) } },
                        )
                        if (SettingsScreenGroups.audio.rowAdmitted(AudioRows.ChannelMixMode.id, rowFlags)) {
                                val channelMixModeTitle = rowTitle(AudioRows.ChannelMixMode)
                                SettingListItem(
                                    icon = Tabler.Outline.Speakerphone,
                                    title = rowTitle(AudioRows.ChannelMixMode),
                                subtitle = preferences.channelMixMode.displayName,
                                trailingText = preferences.channelMixMode.displayName,
                                highlighted = highlightSettingId == AudioRows.ChannelMixMode.id,
                                onClick = {
                                    val modes = ChannelMixMode.entries
                                    activePicker.value = PickerState.List(
                                        title = channelMixModeTitle,
                                        items = modes,
                                        label = { it.displayName },
                                        isSelected = { it == preferences.channelMixMode },
                                        onSelect = { viewModel.edit { scope -> scope.audio.setChannelMixMode(it) } },
                                    )
                                },
                            )
                        }
                        val balanceCenter = stringResource(Res.string.settings_balance_center)
                        val balanceLeft = stringResource(Res.string.settings_balance_left)
                        val balanceRight = stringResource(Res.string.settings_balance_right)
                        val lrBalanceTitle = rowTitle(AudioRows.LrBalance)
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = rowTitle(AudioRows.LrBalance),
                            subtitle = if (preferences.lrBalance == 0f) balanceCenter else if (preferences.lrBalance < 0f) balanceLeft else balanceRight,
                            trailingText = if (preferences.lrBalance == 0f) balanceCenter else formatTwoDecimals(preferences.lrBalance.toDouble()),
                            highlighted = highlightSettingId == AudioRows.LrBalance.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = lrBalanceTitle,
                                    value = preferences.lrBalance,
                                    valueRange = -1.0f..1.0f,
                                    steps = 20,
                                    valueLabel = { if (it == 0f) balanceCenter else if (it < 0f) "${(it * -100).toInt()}% $balanceLeft" else "${(it * 100).toInt()}% $balanceRight" },
                                    rangeStartLabel = balanceLeft,
                                    rangeEndLabel = balanceRight,
                                    onConfirm = { viewModel.edit { scope -> scope.audioEffects.setLrBalance(it) } },
                                )
                            },
                        )
                        val normalPitch = stringResource(Res.string.settings_pitch_normal)
                        val pitchShiftTitle = rowTitle(AudioRows.PitchShift)
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = rowTitle(AudioRows.PitchShift),
                            subtitle = if (preferences.pitchSemitones == 0f) normalPitch else "${if (preferences.pitchSemitones > 0) "+" else ""}${preferences.pitchSemitones} semitones",
                            trailingText = if (preferences.pitchSemitones == 0f) "0" else "${if (preferences.pitchSemitones > 0) "+" else ""}${preferences.pitchSemitones}",
                            highlighted = highlightSettingId == AudioRows.PitchShift.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = pitchShiftTitle,
                                    value = preferences.pitchSemitones,
                                    valueRange = -12.0f..12.0f,
                                    steps = 24,
                                    valueLabel = { if (it == 0f) normalPitch else "${if (it > 0) "+" else ""}${it.toInt()} semitones" },
                                    rangeStartLabel = "-12 semitones",
                                    rangeEndLabel = "+12 semitones",
                                    onConfirm = { viewModel.edit { scope -> scope.audioEffects.setPitchSemitones(it) } },
                                )
                            },
                        )
                }
                    } // SettingsItemList
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
            // No desktop audio cache exists (the AudioCacheClearer seam no-ops
            // there) — the whole group stays off the surface.
            if (settingsCapabilities.supportsAudioCache) {
                item {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = stringResource(Res.string.settings_audio_caching_title),
                    summary = { stringResource(Res.string.settings_audio_caching_summary) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    // Derived from the audio-cache declaration: a deep-linked
                    // cache id expands the group it renders in.
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.audioCache.itemIdSet,
                ) {
                    SettingToggleItem(
                            icon = Tabler.Outline.Database,
                            title = rowTitle(AudioRows.AudioCachingEnabled),
                            subtitle = if (preferences.audioCachingEnabled)
                                stringResource(Res.string.settings_audio_caching_on)
                            else stringResource(Res.string.settings_audio_caching_off),
                            checked = preferences.audioCachingEnabled,
                            highlighted = highlightSettingId == AudioRows.AudioCachingEnabled.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.audioCache.setAudioCachingEnabled(it) } },
                        )
                        if (preferences.audioCachingEnabled) {
                            val cacheSizeTitle = rowTitle(AudioRows.AudioCacheSize)
                            SettingListItem(
                                icon = Tabler.Outline.DeviceFloppy,
                                title = cacheSizeTitle,
                                subtitle = stringResource(Res.string.settings_audio_cache_size_subtitle),
                                trailingText = "${preferences.audioCacheSizeMb} MB",
                                highlighted = highlightSettingId == AudioRows.AudioCacheSize.id,
                                onClick = {
                                    val sizes = listOf(128, 256, 512, 1024, 2048, 4096)
                                    activePicker.value = pickerChip(
                                        title = cacheSizeTitle,
                                        values = sizes,
                                        current = preferences.audioCacheSizeMb,
                                        label = { "$it MB" },
                                        onSelect = { sizeMb -> viewModel.edit { it.audioCache.setAudioCacheSizeMb(sizeMb) } },
                                    )
                                },
                            )
                            val lookaheadTitle = rowTitle(AudioRows.AudioPrefetchLookahead)
                            val lookaheadOffLabel = stringResource(Res.string.settings_off)
                            SettingListItem(
                                icon = Tabler.Outline.ListNumbers,
                                title = lookaheadTitle,
                                subtitle = stringResource(Res.string.settings_audio_prefetch_lookahead_subtitle),
                                trailingText = "${preferences.audioPrefetchLookahead}",
                                highlighted = highlightSettingId == AudioRows.AudioPrefetchLookahead.id,
                                onClick = {
                                    val lookahead = listOf(0, 1, 2, 3, 5, 8)
                                    activePicker.value = pickerChip(
                                        title = lookaheadTitle,
                                        values = lookahead,
                                        current = preferences.audioPrefetchLookahead,
                                        label = { if (it == 0) lookaheadOffLabel else "$it" },
                                        onSelect = { lookahead -> viewModel.edit { it.audioCache.setAudioPrefetchLookahead(lookahead) } },
                                    )
                                },
                            )
                            val backfillTitle = rowTitle(AudioRows.AudioPrefetchBackfill)
                            val backfillOffLabel = stringResource(Res.string.settings_off)
                            SettingListItem(
                                icon = Tabler.Outline.History,
                                title = backfillTitle,
                                subtitle = stringResource(Res.string.settings_audio_prefetch_backfill_subtitle),
                                trailingText = "${preferences.audioPrefetchBackfill}",
                                highlighted = highlightSettingId == AudioRows.AudioPrefetchBackfill.id,
                                onClick = {
                                    val backfill = listOf(0, 1, 2, 5, 10, 20)
                                    activePicker.value = pickerChip(
                                        title = backfillTitle,
                                        values = backfill,
                                        current = preferences.audioPrefetchBackfill,
                                        label = { if (it == 0) backfillOffLabel else "$it" },
                                        onSelect = { backfill -> viewModel.edit { it.audioCache.setAudioPrefetchBackfill(backfill) } },
                                    )
                                },
                            )
                            val policyTitle = rowTitle(AudioRows.AudioCacheNetworkPolicy)
                            SettingListItem(
                                icon = Tabler.Outline.Wifi,
                                title = policyTitle,
                                subtitle = preferences.audioCacheNetworkPolicy.displayName,
                                trailingText = preferences.audioCacheNetworkPolicy.displayName,
                                highlighted = highlightSettingId == AudioRows.AudioCacheNetworkPolicy.id,
                                onClick = {
                                    val policies = AudioCacheNetworkPolicy.entries
                                    activePicker.value = PickerState.List(
                                        title = policyTitle,
                                        items = policies,
                                        label = { it.displayName },
                                        isSelected = { it == preferences.audioCacheNetworkPolicy },
                                        onSelect = { viewModel.edit { scope -> scope.audioCache.setAudioCacheNetworkPolicy(it) } },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Trash,
                                title = rowTitle(AudioRows.AudioCacheClear),
                                subtitle = stringResource(Res.string.settings_audio_cache_clear_subtitle),
                                trailingText = "",
                                highlighted = highlightSettingId == AudioRows.AudioCacheClear.id,
                                onClick = { viewModel.clearAudioCache() },
                            )
                        }
                    }
                }
            }
    }

    EqualizerEditorSheet(
        visible = showEqualizerEditor,
        bandLevels = preferences.equalizerSettings.bandLevels,
        onApply = {
            viewModel.edit { scope -> scope.audioEffects.setEqualizerSettings(it) }
            showEqualizerEditor = false
        },
        onDismiss = { showEqualizerEditor = false },
    )

}
