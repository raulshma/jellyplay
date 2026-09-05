package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: AudioSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    // The one remaining dialog slot (beside the activePicker): the co-located
    // EqualizerEditorSheet. The former sealed AudioSettingsDialog identity tag
    // carried a single real variant.
    var showEqualizerEditor by remember { mutableStateOf(false) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "audio_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_audio_player_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
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
                    title = stringResource(Res.string.settings_audio_player_title),
                    summary = { stringResource(Res.string.settings_default_speed_value, if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x") },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
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

                    SettingsItemList(total = total) {
                    val audioDefaultSpeedTitle = stringResource(Res.string.settings_audio_default_speed)
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(Res.string.settings_audio_default_speed),
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) stringResource(Res.string.settings_audio_default_speed_normal) else stringResource(Res.string.settings_audio_default_speed_value, "${preferences.audioDefaultSpeed}x"),
                        trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        highlighted = highlightSettingId == "audio_default_speed",
                        onClick = {
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                activePicker = pickerChip(
                    title = audioDefaultSpeedTitle,
                    values = speeds,
                    current = preferences.audioDefaultSpeed,
                    label = { if (it == 1.0f) "1x" else "${it}x" },
                    onSelect = viewModel::setAudioDefaultSpeed,
                )
            },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = stringResource(Res.string.settings_audio_auto_play_next),
                        subtitle = if (preferences.audioAutoplayNext) stringResource(Res.string.settings_audio_auto_play_on) else stringResource(Res.string.settings_audio_auto_play_off),
                        checked = preferences.audioAutoplayNext,
                        highlighted = highlightSettingId == "audio_autoplay_next",
                        onCheckedChange = { viewModel.setAudioAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = stringResource(Res.string.settings_audio_visualizer),
                        subtitle = if (preferences.audioVisualizerEnabled) stringResource(Res.string.settings_audio_visualizer_on) else stringResource(Res.string.settings_audio_visualizer_off),
                        checked = preferences.audioVisualizerEnabled,
                        highlighted = highlightSettingId == "audio_visualizer",
                        onCheckedChange = { viewModel.setAudioVisualizerEnabled(it) },
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
                        title = stringResource(Res.string.settings_sleep_timer),
                        subtitle = if (preferences.sleepTimerDurationMs == 0L) stringResource(Res.string.settings_sleep_timer_off) else stringResource(Res.string.settings_sleep_timer_minutes, preferences.sleepTimerDurationMs / 60000),
                        trailingText = if (preferences.sleepTimerDurationMs == 0L) stringResource(Res.string.settings_off) else "${preferences.sleepTimerDurationMs / 60000}m",
                        highlighted = highlightSettingId == "sleep_timer",
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
                            activePicker = PickerState.List(
                                title = sleepTimerDurationTitle,
                                items = options,
                                label = { sleepTimerLabels[options.indexOf(it)] },
                                isSelected = { it == preferences.sleepTimerDurationMs },
                                onSelect = { viewModel.setSleepTimerDurationMs(it) },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Speakerphone,
                        title = stringResource(Res.string.settings_audio_description),
                        subtitle = if (preferences.preferAudioDescription) stringResource(Res.string.settings_audio_description_on) else stringResource(Res.string.settings_audio_description_off),
                        checked = preferences.preferAudioDescription,
                        highlighted = highlightSettingId == "audio_description",
                        onCheckedChange = { viewModel.setPreferAudioDescription(it) },
                    )
                    if (showAdvanced) {
                        val nightModeVolumeTitle = stringResource(Res.string.settings_night_mode_volume)
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = stringResource(Res.string.settings_night_mode_volume),
                            subtitle = stringResource(Res.string.settings_night_mode_volume_subtitle),
                            trailingText = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                            highlighted = highlightSettingId == "night_mode_volume",
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = nightModeVolumeTitle,
                                    value = preferences.audioNightModeVolume,
                                    valueRange = 0.1f..0.8f,
                                    steps = 6,
                                    valueLabel = { "${(it * 100).toInt()}%" },
                                    rangeStartLabel = "10%",
                                    rangeEndLabel = "80%",
                                    onConfirm = { viewModel.setAudioNightModeVolume(it) },
                                )
                            },
                        )
                        val nightModeGainTitle = stringResource(Res.string.settings_night_mode_gain)
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(Res.string.settings_night_mode_gain),
                            subtitle = stringResource(Res.string.settings_night_mode_gain_subtitle),
                            trailingText = "${preferences.audioNightModeGain}",
                            highlighted = highlightSettingId == "night_mode_gain",
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = nightModeGainTitle,
                                    value = preferences.audioNightModeGain.toFloat(),
                                    valueRange = 0f..3000f,
                                    steps = 29,
                                    valueLabel = { "${it.toInt()}" },
                                    rangeStartLabel = "0",
                                    rangeEndLabel = "3000",
                                    onConfirm = { viewModel.setAudioNightModeGain(it.toInt()) },
                                )
                            },
                        )
                        val skipPrevThresholdTitle = stringResource(Res.string.settings_skip_prev_threshold)
                        SettingListItem(
                            icon = Tabler.Outline.PlayerSkipForward,
                            title = stringResource(Res.string.settings_skip_prev_threshold),
                            subtitle = stringResource(Res.string.settings_skip_prev_threshold_subtitle),
                            trailingText = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                            highlighted = highlightSettingId == "audio_skip_prev_threshold",
                            onClick = {
                                val thresholds = listOf(1_000L, 2_000L, 3_000L, 5_000L, 7_000L, 10_000L)
                                activePicker = pickerChip(
                                    title = skipPrevThresholdTitle,
                                    values = thresholds,
                                    current = preferences.audioSkipPreviousThresholdMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = viewModel::setAudioSkipPreviousThresholdMs,
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PlaylistAdd,
                            title = stringResource(Res.string.settings_gapless_playback),
                            subtitle = if (preferences.audioGaplessEnabled) stringResource(Res.string.settings_gapless_on) else stringResource(Res.string.settings_gapless_off),
                            checked = preferences.audioGaplessEnabled,
                            highlighted = highlightSettingId == "gapless_playback",
                            onCheckedChange = { viewModel.setGaplessEnabled(it) },
                        )
                        val crossfadeOffLabel = stringResource(Res.string.settings_off)
                        val crossfadeDurationTitle = stringResource(Res.string.settings_crossfade_duration)
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = stringResource(Res.string.settings_crossfade_duration),
                            subtitle = if (preferences.audioCrossfadeDurationMs > 0) stringResource(Res.string.settings_crossfade_on, preferences.audioCrossfadeDurationMs / 1000) else stringResource(Res.string.settings_crossfade_off),
                            trailingText = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s" else stringResource(Res.string.settings_off),
                            highlighted = highlightSettingId == "crossfade",
                            onClick = {
                                val durations = listOf(0L, 2000L, 3000L, 5000L, 8000L, 12000L)
                                activePicker = pickerChip(
                                    title = crossfadeDurationTitle,
                                    values = durations,
                                    current = preferences.audioCrossfadeDurationMs,
                                    label = { if (it == 0L) crossfadeOffLabel else "${it / 1000}s" },
                                    onSelect = viewModel::setCrossfadeDurationMs,
                                )
                            },
                        )
                        val preloadBufferTitle = stringResource(Res.string.settings_preload_buffer)
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = stringResource(Res.string.settings_preload_buffer),
                            subtitle = stringResource(Res.string.settings_preload_buffer_subtitle),
                            trailingText = preferences.audioPreloadBufferSize.displayName,
                            highlighted = highlightSettingId == "audio_preload_buffer",
                            onClick = {
                                activePicker = PickerState.List(
                                    title = preloadBufferTitle,
                                    items = PreloadBufferSize.entries,
                                    label = { it.displayName },
                                    subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
                                    isSelected = { it == preferences.audioPreloadBufferSize },
                                    onSelect = { viewModel.setAudioPreloadBufferSize(it) },
                                )
                            },
                        )
                        val volumeNormalizationTitle = stringResource(Res.string.settings_volume_normalization)
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(Res.string.settings_volume_normalization),
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
                            highlighted = highlightSettingId == "volume_normalization",
                            onClick = {
                                val modes = AudioNormalizationMode.entries
                                activePicker = pickerChip(
                                    title = volumeNormalizationTitle,
                                    values = modes,
                                    current = preferences.audioNormalizationMode,
                                    label = { it.displayName },
                                    onSelect = viewModel::setAudioNormalizationMode,
                                )
                            },
                        )
                        if (preferences.audioNormalizationMode == AudioNormalizationMode.TRACK ||
                            preferences.audioNormalizationMode == AudioNormalizationMode.ALBUM
                        ) {
                            val replayGainPreAmpTitle = stringResource(Res.string.settings_replaygain_preamp)
                            SettingListItem(
                                icon = Tabler.Outline.Adjustments,
                                title = stringResource(Res.string.settings_replaygain_preamp),
                                subtitle = stringResource(Res.string.settings_replaygain_preamp_subtitle),
                                trailingText = "${if (preferences.replayGainPreAmpDb >= 0) "+" else ""}${String.format("%.1f", preferences.replayGainPreAmpDb)} dB",
                                highlighted = highlightSettingId == "replaygain_preamp",
                                onClick = {
                                    activePicker = PickerState.Slider(
                                        title = replayGainPreAmpTitle,
                                        value = preferences.replayGainPreAmpDb,
                                        valueRange = -15f..15f,
                                        steps = 59,
                                        valueLabel = { "${if (it >= 0) "+" else ""}${String.format("%.1f", it)} dB" },
                                        rangeStartLabel = "-15 dB",
                                        rangeEndLabel = "+15 dB",
                                        onConfirm = { viewModel.setReplayGainPreAmpDb(it) },
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(Res.string.settings_equalizer),
                            subtitle = if (preferences.equalizerEnabled) stringResource(Res.string.settings_equalizer_on) else stringResource(Res.string.settings_equalizer_off),
                            checked = preferences.equalizerEnabled,
                            highlighted = highlightSettingId == "equalizer",
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                            onClick = { showEqualizerEditor = true },
                        )
                        if (preferences.equalizerEnabled) {
                            val equalizerPresetTitle = stringResource(Res.string.settings_equalizer_preset)
                            SettingListItem(
                                icon = Tabler.Outline.Adjustments,
                                title = stringResource(Res.string.settings_equalizer_preset),
                                subtitle = stringResource(Res.string.settings_equalizer_preset_subtitle, preferences.equalizerPreset.displayName),
                                trailingText = preferences.equalizerPreset.displayName,
                                highlighted = highlightSettingId == "equalizer_preset",
                                onClick = {
                                    val presets = EqualizerPreset.entries
                                    activePicker = PickerState.List(
                                        title = equalizerPresetTitle,
                                        items = presets,
                                        label = { it.displayName },
                                        isSelected = { it == preferences.equalizerPreset },
                                        onSelect = { viewModel.setEqualizerPreset(it) },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Microphone2,
                                title = stringResource(Res.string.settings_dialogue_boost),
                                subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else stringResource(Res.string.settings_off),
                                checked = preferences.dialogueBoostEnabled,
                                onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                            )
                        }
                        if (preferences.dialogueBoostEnabled) {
                            val dialogueBoostStrengthTitle = stringResource(Res.string.settings_dialogue_boost_strength)
                            SettingListItem(
                                icon = Tabler.Outline.Music,
                                title = stringResource(Res.string.settings_dialogue_boost_strength),
                                subtitle = preferences.dialogueBoostStrength.displayName,
                                trailingText = preferences.dialogueBoostStrength.displayName,
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    activePicker = pickerChip(
                                        title = dialogueBoostStrengthTitle,
                                        values = strengths,
                                        current = preferences.dialogueBoostStrength,
                                        label = { it.displayName },
                                        onSelect = viewModel::setDialogueBoostStrength,
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Gauge,
                            title = stringResource(Res.string.settings_night_mode),
                            subtitle = if (preferences.nightModeEnabled) preferences.nightModeStrength.displayName else stringResource(Res.string.settings_off),
                            checked = preferences.nightModeEnabled,
                            highlighted = highlightSettingId == "night_mode",
                            onCheckedChange = { viewModel.setNightModeEnabled(it) },
                        )
                        if (preferences.nightModeEnabled) {
                                val nightModeStrengthTitle = stringResource(Res.string.settings_night_mode_strength)
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = stringResource(Res.string.settings_night_mode_strength),
                                subtitle = preferences.nightModeStrength.displayName,
                                trailingText = preferences.nightModeStrength.displayName,
                                highlighted = highlightSettingId == "night_mode_strength",
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    activePicker = pickerChip(
                                        title = nightModeStrengthTitle,
                                        values = strengths,
                                        current = preferences.nightModeStrength,
                                        label = { it.displayName },
                                        onSelect = viewModel::setNightModeStrength,
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.WaveSine,
                            title = stringResource(Res.string.settings_bass_boost),
                            subtitle = if (preferences.bassBoostEnabled) preferences.bassBoostStrength.displayName else stringResource(Res.string.settings_off),
                            checked = preferences.bassBoostEnabled,
                            highlighted = highlightSettingId == "bass_boost",
                            onCheckedChange = { viewModel.setBassBoostEnabled(it) },
                        )
                        if (preferences.bassBoostEnabled) {
                                val bassBoostStrengthTitle = stringResource(Res.string.settings_bass_boost_strength)
                                SettingListItem(
                                    icon = Tabler.Outline.WaveSine,
                                    title = stringResource(Res.string.settings_bass_boost_strength),
                                subtitle = preferences.bassBoostStrength.displayName,
                                trailingText = preferences.bassBoostStrength.displayName,
                                highlighted = highlightSettingId == "bass_boost_strength",
                                onClick = {
                                    val strengths = EffectStrength.entries
                                    activePicker = pickerChip(
                                        title = bassBoostStrengthTitle,
                                        values = strengths,
                                        current = preferences.bassBoostStrength,
                                        label = { it.displayName },
                                        onSelect = viewModel::setBassBoostStrength,
                                    )
                                },
                            )
                        }
                        val virtualizerStrengthSuffix = stringResource(Res.string.settings_strength_suffix)
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = stringResource(Res.string.settings_virtualizer),
                            subtitle = if (preferences.virtualizerEnabled) "${preferences.virtualizerStrength / 10}%$virtualizerStrengthSuffix" else stringResource(Res.string.settings_off),
                            checked = preferences.virtualizerEnabled,
                            highlighted = highlightSettingId == "virtualizer",
                            onCheckedChange = { viewModel.setVirtualizerEnabled(it) },
                        )
                        if (preferences.virtualizerEnabled) {
                                val virtualizerStrengthTitle = stringResource(Res.string.settings_virtualizer_strength)
                                SettingListItem(
                                    icon = Tabler.Outline.Speakerphone,
                                    title = stringResource(Res.string.settings_virtualizer_strength),
                                subtitle = "${preferences.virtualizerStrength / 10}%",
                                trailingText = "${preferences.virtualizerStrength / 10}%",
                                highlighted = highlightSettingId == "virtualizer_strength",
                                onClick = {
                                    val stepsList = listOf(0, 200, 400, 500, 600, 800, 1000)
                                    activePicker = PickerState.List(
                                        title = virtualizerStrengthTitle,
                                        items = stepsList,
                                        label = { "${it / 10}%" },
                                        isSelected = { it == preferences.virtualizerStrength },
                                        onSelect = { viewModel.setVirtualizerStrength(it) },
                                    )
                                },
                            )
                        }
                        val volumeBoostGainSuffix = stringResource(Res.string.settings_gain_suffix)
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = stringResource(Res.string.settings_volume_boost),
                            subtitle = if (preferences.volumeBoostEnabled) "+${"%.1f".format(preferences.volumeBoostGain / 100.0)} $volumeBoostGainSuffix" else stringResource(Res.string.settings_off),
                            checked = preferences.volumeBoostEnabled,
                            highlighted = highlightSettingId == "volume_boost",
                            onCheckedChange = { viewModel.setVolumeBoostEnabled(it) },
                        )
                        if (preferences.volumeBoostEnabled) {
                            val volumeBoostGainTitle = stringResource(Res.string.settings_volume_boost_gain)
                            SettingListItem(
                                icon = Tabler.Outline.Speakerphone,
                                title = stringResource(Res.string.settings_volume_boost_gain),
                                subtitle = stringResource(Res.string.settings_volume_boost_gain_subtitle),
                                trailingText = "+${"%.1f".format(preferences.volumeBoostGain / 100.0)} dB",
                                highlighted = highlightSettingId == "volume_boost_gain",
                                onClick = {
                                    activePicker = PickerState.Slider(
                                        title = volumeBoostGainTitle,
                                        value = preferences.volumeBoostGain.toFloat(),
                                        valueRange = 0f..3000f,
                                        steps = 30,
                                        valueLabel = { "+${it.toInt() / 100} dB" },
                                        rangeStartLabel = "0 dB",
                                        rangeEndLabel = "+30 dB",
                                        onConfirm = { viewModel.setVolumeBoostGain(it.toInt()) },
                                    )
                                },
                            )
                        }
                        val reverbTitle = stringResource(Res.string.settings_reverb)
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = stringResource(Res.string.settings_reverb),
                            subtitle = preferences.reverbPreset.displayName,
                            trailingText = preferences.reverbPreset.displayName,
                            highlighted = highlightSettingId == "reverb",
                            onClick = {
                                activePicker = PickerState.List(
                                    title = reverbTitle,
                                    items = ReverbPreset.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.reverbPreset },
                                    onSelect = { viewModel.setReverbPreset(it) },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Wand,
                            title = stringResource(Res.string.settings_auto_eq_genre),
                            subtitle = if (preferences.autoEqByGenre) stringResource(Res.string.settings_auto_eq_genre_on) else stringResource(Res.string.settings_off),
                            checked = preferences.autoEqByGenre,
                            highlighted = highlightSettingId == "auto_eq_by_genre",
                            onCheckedChange = { viewModel.setAutoEqByGenre(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = stringResource(Res.string.settings_channel_mixing),
                            subtitle = if (preferences.channelMixEnabled) stringResource(Res.string.settings_channel_mixing_on) else stringResource(Res.string.settings_channel_mixing_off),
                            checked = preferences.channelMixEnabled,
                            highlighted = highlightSettingId == "channel_mixing",
                            onCheckedChange = { viewModel.setChannelMixEnabled(it) },
                        )
                        if (preferences.channelMixEnabled) {
                                val channelMixModeTitle = stringResource(Res.string.settings_channel_mix_mode)
                                SettingListItem(
                                    icon = Tabler.Outline.Speakerphone,
                                    title = stringResource(Res.string.settings_channel_mix_mode),
                                subtitle = preferences.channelMixMode.displayName,
                                trailingText = preferences.channelMixMode.displayName,
                                highlighted = highlightSettingId == "channel_mix_mode",
                                onClick = {
                                    val modes = ChannelMixMode.entries
                                    activePicker = PickerState.List(
                                        title = channelMixModeTitle,
                                        items = modes,
                                        label = { it.displayName },
                                        isSelected = { it == preferences.channelMixMode },
                                        onSelect = { viewModel.setChannelMixMode(it) },
                                    )
                                },
                            )
                        }
                        val balanceCenter = stringResource(Res.string.settings_balance_center)
                        val balanceLeft = stringResource(Res.string.settings_balance_left)
                        val balanceRight = stringResource(Res.string.settings_balance_right)
                        val lrBalanceTitle = stringResource(Res.string.settings_lr_balance)
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = stringResource(Res.string.settings_lr_balance),
                            subtitle = if (preferences.lrBalance == 0f) balanceCenter else if (preferences.lrBalance < 0f) balanceLeft else balanceRight,
                            trailingText = if (preferences.lrBalance == 0f) balanceCenter else String.format("%.2f", preferences.lrBalance),
                            highlighted = highlightSettingId == "lr_balance",
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = lrBalanceTitle,
                                    value = preferences.lrBalance,
                                    valueRange = -1.0f..1.0f,
                                    steps = 20,
                                    valueLabel = { if (it == 0f) balanceCenter else if (it < 0f) "${(it * -100).toInt()}% $balanceLeft" else "${(it * 100).toInt()}% $balanceRight" },
                                    rangeStartLabel = balanceLeft,
                                    rangeEndLabel = balanceRight,
                                    onConfirm = { viewModel.setLrBalance(it) },
                                )
                            },
                        )
                        val normalPitch = stringResource(Res.string.settings_pitch_normal)
                        val pitchShiftTitle = stringResource(Res.string.settings_pitch_shift)
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = stringResource(Res.string.settings_pitch_shift),
                            subtitle = if (preferences.pitchSemitones == 0f) normalPitch else "${if (preferences.pitchSemitones > 0) "+" else ""}${preferences.pitchSemitones} semitones",
                            trailingText = if (preferences.pitchSemitones == 0f) "0" else "${if (preferences.pitchSemitones > 0) "+" else ""}${preferences.pitchSemitones}",
                            highlighted = highlightSettingId == "pitch_shift",
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = pitchShiftTitle,
                                    value = preferences.pitchSemitones,
                                    valueRange = -12.0f..12.0f,
                                    steps = 24,
                                    valueLabel = { if (it == 0f) normalPitch else "${if (it > 0) "+" else ""}${it.toInt()} semitones" },
                                    rangeStartLabel = "-12 semitones",
                                    rangeEndLabel = "+12 semitones",
                                    onConfirm = { viewModel.setPitchSemitones(it) },
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
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = stringResource(Res.string.settings_audio_caching_title),
                    summary = { stringResource(Res.string.settings_audio_caching_summary) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = false,
                ) {
                    var cacheIdx = 0
                    val cacheTotal = 6
                    SettingToggleItem(
                        icon = Tabler.Outline.Database,
                        title = stringResource(Res.string.settings_audio_caching_enable),
                        subtitle = if (preferences.audioCachingEnabled)
                            stringResource(Res.string.settings_audio_caching_on)
                        else stringResource(Res.string.settings_audio_caching_off),
                        checked = preferences.audioCachingEnabled,
                        highlighted = highlightSettingId == "audio_caching_enabled",
                        onCheckedChange = { viewModel.setAudioCachingEnabled(it) },
                    )
                    if (preferences.audioCachingEnabled) {
                        val cacheSizeTitle = stringResource(Res.string.settings_audio_cache_size)
                        SettingListItem(
                            icon = Tabler.Outline.DeviceFloppy,
                            title = cacheSizeTitle,
                            subtitle = stringResource(Res.string.settings_audio_cache_size_subtitle),
                            trailingText = "${preferences.audioCacheSizeMb} MB",
                            highlighted = highlightSettingId == "audio_cache_size",
                            onClick = {
                                val sizes = listOf(128, 256, 512, 1024, 2048, 4096)
                                activePicker = pickerChip(
                                    title = cacheSizeTitle,
                                    values = sizes,
                                    current = preferences.audioCacheSizeMb,
                                    label = { "$it MB" },
                                    onSelect = viewModel::setAudioCacheSizeMb,
                                )
                            },
                        )
                        val lookaheadTitle = stringResource(Res.string.settings_audio_prefetch_lookahead)
                        val lookaheadOffLabel = stringResource(Res.string.settings_off)
                        SettingListItem(
                            icon = Tabler.Outline.ListNumbers,
                            title = lookaheadTitle,
                            subtitle = stringResource(Res.string.settings_audio_prefetch_lookahead_subtitle),
                            trailingText = "${preferences.audioPrefetchLookahead}",
                            highlighted = highlightSettingId == "audio_prefetch_lookahead",
                            onClick = {
                                val lookahead = listOf(0, 1, 2, 3, 5, 8)
                                activePicker = pickerChip(
                                    title = lookaheadTitle,
                                    values = lookahead,
                                    current = preferences.audioPrefetchLookahead,
                                    label = { if (it == 0) lookaheadOffLabel else "$it" },
                                    onSelect = viewModel::setAudioPrefetchLookahead,
                                )
                            },
                        )
                        val backfillTitle = stringResource(Res.string.settings_audio_prefetch_backfill)
                        val backfillOffLabel = stringResource(Res.string.settings_off)
                        SettingListItem(
                            icon = Tabler.Outline.History,
                            title = backfillTitle,
                            subtitle = stringResource(Res.string.settings_audio_prefetch_backfill_subtitle),
                            trailingText = "${preferences.audioPrefetchBackfill}",
                            highlighted = highlightSettingId == "audio_prefetch_backfill",
                            onClick = {
                                val backfill = listOf(0, 1, 2, 5, 10, 20)
                                activePicker = pickerChip(
                                    title = backfillTitle,
                                    values = backfill,
                                    current = preferences.audioPrefetchBackfill,
                                    label = { if (it == 0) backfillOffLabel else "$it" },
                                    onSelect = viewModel::setAudioPrefetchBackfill,
                                )
                            },
                        )
                        val policyTitle = stringResource(Res.string.settings_audio_cache_network_policy)
                        SettingListItem(
                            icon = Tabler.Outline.Wifi,
                            title = policyTitle,
                            subtitle = preferences.audioCacheNetworkPolicy.displayName,
                            trailingText = preferences.audioCacheNetworkPolicy.displayName,
                            highlighted = highlightSettingId == "audio_cache_network_policy",
                            onClick = {
                                val policies = AudioCacheNetworkPolicy.entries
                                activePicker = PickerState.List(
                                    title = policyTitle,
                                    items = policies,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.audioCacheNetworkPolicy },
                                    onSelect = { viewModel.setAudioCacheNetworkPolicy(it) },
                                )
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Trash,
                            title = stringResource(Res.string.settings_audio_cache_clear),
                            subtitle = stringResource(Res.string.settings_audio_cache_clear_subtitle),
                            trailingText = "",
                            highlighted = highlightSettingId == "audio_cache_clear",
                            onClick = { viewModel.clearAudioCache() },
                        )
                    }
                }
            }
        }
        }
    }

    EqualizerEditorSheet(
        visible = showEqualizerEditor,
        bandLevels = preferences.equalizerSettings.bandLevels,
        onApply = {
            viewModel.setEqualizerSettings(it)
            showEqualizerEditor = false
        },
        onDismiss = { showEqualizerEditor = false },
    )

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
