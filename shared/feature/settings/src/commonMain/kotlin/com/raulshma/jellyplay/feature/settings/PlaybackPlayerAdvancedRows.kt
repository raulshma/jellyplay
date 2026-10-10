package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_percent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_episode_browser_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_episode_browser_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_metadata_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remember_brightness_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remember_brightness_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_preview_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_preview_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_pip_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_pip_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incognito_mode_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incognito_mode_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_time_remaining_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_time_remaining_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_player_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_player_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_on_focus_loss_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pause_on_focus_loss_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_duck_on_phone_call_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_duck_on_phone_call_off

/** The `playback.player` group's advanced sub-rows (the group's inner if (showAdvanced) block). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackPlayerAdvancedRows(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                    val offLabel = stringResource(Res.string.settings_off)
                        val controlsTimeoutTitle = rowTitle(PlaybackRows.ControlsTimeout)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.ControlsTimeout),
                            title = rowTitle(PlaybackRows.ControlsTimeout),
                            subtitle = rowSubtitle(PlaybackRows.ControlsTimeout),
                            trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                            highlighted = highlightSettingId == PlaybackRows.ControlsTimeout.id,
                            onClick = {
                                val timeouts = listOf(3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
                                activePicker.value = pickerChip(
                                    title = controlsTimeoutTitle,
                                    values = timeouts,
                                    current = preferences.videoControlsTimeoutMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoControlsTimeoutMs(ms) } },
                                )
                            },
                        )
                        val skipBackLabel = if (preferences.videoSkipBackOnResumeMs == 0L) offLabel else "${preferences.videoSkipBackOnResumeMs / 1000}s"
                        // pausing must not summon the control overlay
                        // (opt-in; off = today's show-on-pause behavior).
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.HideOsdOnPause),
                            title = rowTitle(PlaybackRows.HideOsdOnPause),
                            subtitle = rowSubtitle(PlaybackRows.HideOsdOnPause),
                            checked = preferences.videoHideOsdOnPause,
                            highlighted = highlightSettingId == PlaybackRows.HideOsdOnPause.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoHideOsdOnPause(enabled) }
                            },
                        )
                        val skipBackOnResumeTitle = rowTitle(PlaybackRows.SkipBackOnResume)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SkipBackOnResume),
                            title = rowTitle(PlaybackRows.SkipBackOnResume),
                            subtitle = rowSubtitle(PlaybackRows.SkipBackOnResume),
                            trailingText = skipBackLabel,
                            highlighted = highlightSettingId == PlaybackRows.SkipBackOnResume.id,
                            onClick = {
                                val durations = listOf(0L, 3_000L, 5_000L, 10_000L, 15_000L, 30_000L)
                                activePicker.value = pickerChip(
                                    title = skipBackOnResumeTitle,
                                    values = durations,
                                    current = preferences.videoSkipBackOnResumeMs,
                                    label = { if (it == 0L) offLabel else "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoSkipBackOnResumeMs(ms) } },
                                )
                            },
                        )
                        val passOutLabel = if (preferences.videoPassOutProtectionHours == 0) offLabel else "${preferences.videoPassOutProtectionHours}h"
                        val passOutProtectionTitle = rowTitle(PlaybackRows.PassOutProtection)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.PassOutProtection),
                            title = rowTitle(PlaybackRows.PassOutProtection),
                            subtitle = rowSubtitle(PlaybackRows.PassOutProtection),
                            trailingText = passOutLabel,
                            highlighted = highlightSettingId == PlaybackRows.PassOutProtection.id,
                            onClick = {
                                val hours = listOf(0, 1, 2, 3, 4, 6, 8)
                                activePicker.value = pickerChip(
                                    title = passOutProtectionTitle,
                                    values = hours,
                                    current = preferences.videoPassOutProtectionHours,
                                    label = { if (it == 0) offLabel else "${it}h" },
                                    onSelect = { hours -> viewModel.edit { it.videoPlayer.setVideoPassOutProtectionHours(hours) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.AutoplayTrailers),
                            title = rowTitle(PlaybackRows.AutoplayTrailers),
                            subtitle = if (preferences.trailerAutoplay) stringResource(Res.string.settings_autoplay_trailers_on) else stringResource(Res.string.settings_autoplay_trailers_off),
                            checked = preferences.trailerAutoplay,
                            highlighted = highlightSettingId == PlaybackRows.AutoplayTrailers.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrailerAutoplay(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.CinemaMode),
                            title = rowTitle(PlaybackRows.CinemaMode),
                            subtitle = if (preferences.cinemaModeEnabled) stringResource(Res.string.settings_cinema_mode_on) else stringResource(Res.string.settings_cinema_mode_off),
                            checked = preferences.cinemaModeEnabled,
                            highlighted = highlightSettingId == PlaybackRows.CinemaMode.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setCinemaModeEnabled(it) } },
                        )
                        if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, rowFlags)) {
                            SettingToggleItem(
                                icon = rowIcon(PlaybackRows.AndroidTvWatchNext),
                                title = rowTitle(PlaybackRows.AndroidTvWatchNext),
                                subtitle = if (preferences.androidTvWatchNextEnabled) stringResource(Res.string.settings_watch_next_row_on) else stringResource(Res.string.settings_watch_next_row_off),
                                checked = preferences.androidTvWatchNextEnabled,
                                highlighted = highlightSettingId == PlaybackRows.AndroidTvWatchNext.id,
                                onCheckedChange = { viewModel.setAndroidTvWatchNextEnabled(it) },
                            )
                            val tvZoomTitle = rowTitle(PlaybackRows.TvZoomMode)
                            val tvZoomNoneLabel = stringResource(Res.string.settings_tv_zoom_none)
                            val tvZoomPercentLabels = listOf(0f, 5f, 10f, 15f, 20f, 25f, 33f, 50f).associateWith {
                                if (it == 0f) tvZoomNoneLabel else stringResource(Res.string.settings_tv_zoom_percent, it.toInt())
                            }
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.TvZoomMode),
                                title = tvZoomTitle,
                                subtitle = rowSubtitle(PlaybackRows.TvZoomMode),
                                trailingText = if (preferences.tvZoomModePercent == 0f) offLabel else "${preferences.tvZoomModePercent.toInt()}%",
                                highlighted = highlightSettingId == PlaybackRows.TvZoomMode.id,
                                onClick = {
                                    val percents = listOf(0f, 5f, 10f, 15f, 20f, 25f, 33f, 50f)
                                    activePicker.value = PickerState.List(
                                        title = tvZoomTitle,
                                        items = percents,
                                        label = { if (it == 0f) offLabel else "${it.toInt()}%" },
                                        subtitle = { percent -> tvZoomPercentLabels[percent] ?: "" },
                                        isSelected = { it == preferences.tvZoomModePercent },
                                        onSelect = { viewModel.edit { scope -> scope.videoPlayer.setTvZoomModePercent(it) } },
                                    )
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.EpisodeBrowser),
                            title = rowTitle(PlaybackRows.EpisodeBrowser),
                            subtitle = if (preferences.videoEpisodeBrowserEnabled) stringResource(Res.string.settings_episode_browser_on) else stringResource(Res.string.settings_episode_browser_off),
                            checked = preferences.videoEpisodeBrowserEnabled,
                            highlighted = highlightSettingId == PlaybackRows.EpisodeBrowser.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoEpisodeBrowserEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.PlaybackMetadata),
                            title = rowTitle(PlaybackRows.PlaybackMetadata),
                            subtitle = if (preferences.videoShowPlaybackMetadata) stringResource(Res.string.settings_playback_metadata_on) else stringResource(Res.string.settings_playback_metadata_off),
                            checked = preferences.videoShowPlaybackMetadata,
                            highlighted = highlightSettingId == PlaybackRows.PlaybackMetadata.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoShowPlaybackMetadata(it) } },
                        )
                        val swipeSeekRangeTitle = rowTitle(PlaybackRows.SwipeSeekRange)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SwipeSeekRange),
                            title = rowTitle(PlaybackRows.SwipeSeekRange),
                            subtitle = rowSubtitle(PlaybackRows.SwipeSeekRange),
                            trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                            highlighted = highlightSettingId == PlaybackRows.SwipeSeekRange.id,
                            onClick = {
                                val ranges = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L, 300_000L)
                                activePicker.value = pickerChip(
                                    title = swipeSeekRangeTitle,
                                    values = ranges,
                                    current = preferences.videoSwipeSeekMaxMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoSwipeSeekMaxMs(ms) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.RememberBrightness),
                            title = rowTitle(PlaybackRows.RememberBrightness),
                            subtitle = if (preferences.videoRememberBrightness) stringResource(Res.string.settings_remember_brightness_on) else stringResource(Res.string.settings_remember_brightness_off),
                            checked = preferences.videoRememberBrightness,
                            highlighted = highlightSettingId == PlaybackRows.RememberBrightness.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { it.videoPlayer.setVideoRememberBrightness(enabled) }
                            },
                        )
                        val defaultBrightnessTitle = rowTitle(PlaybackRows.DefaultBrightnessLevel)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.DefaultBrightnessLevel),
                            title = rowTitle(PlaybackRows.DefaultBrightnessLevel),
                            subtitle = rowSubtitle(PlaybackRows.DefaultBrightnessLevel),
                            trailingText = "${(preferences.videoBrightnessLevel * 100).toInt()}%",
                            highlighted = highlightSettingId == PlaybackRows.DefaultBrightnessLevel.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = defaultBrightnessTitle,
                                    value = preferences.videoBrightnessLevel,
                                    valueRange = 0.0f..1.0f,
                                    steps = 20,
                                    valueLabel = { "${(it * 100).toInt()}%" },
                                    rangeStartLabel = "0%",
                                    rangeEndLabel = "100%",
                                    onConfirm = { viewModel.edit { scope -> scope.videoPlayer.setVideoBrightnessLevel(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.TrickplayPreview),
                            title = rowTitle(PlaybackRows.TrickplayPreview),
                            subtitle = if (preferences.trickplayEnabled) stringResource(Res.string.settings_trickplay_preview_on) else stringResource(Res.string.settings_trickplay_preview_off),
                            checked = preferences.trickplayEnabled,
                            highlighted = highlightSettingId == PlaybackRows.TrickplayPreview.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrickplayEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.TrickplayOnGestures),
                            title = rowTitle(PlaybackRows.TrickplayOnGestures),
                            subtitle = if (preferences.trickplayOnSeekGesture) stringResource(Res.string.settings_trickplay_on_gestures_on) else stringResource(Res.string.settings_trickplay_on_gestures_off),
                            checked = preferences.trickplayOnSeekGesture,
                            highlighted = highlightSettingId == PlaybackRows.TrickplayOnGestures.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrickplayOnSeekGesture(it) } },
                        )
                        val preloadBufferTitle = rowTitle(PlaybackRows.PreloadBuffer)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.PreloadBuffer),
                            title = rowTitle(PlaybackRows.PreloadBuffer),
                            subtitle = rowSubtitle(PlaybackRows.PreloadBuffer),
                            trailingText = preferences.videoPreloadBufferSize.displayName,
                            highlighted = highlightSettingId == PlaybackRows.PreloadBuffer.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = preloadBufferTitle,
                                    items = PreloadBufferSize.entries,
                                    label = { it.displayName },
                                    subtitle = { "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s" },
                                    isSelected = { it == preferences.videoPreloadBufferSize },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoPreloadBufferSize(it) } },
                                )
                            },
                        )

                        val videoCacheSizeTitle = rowTitle(PlaybackRows.VideoCacheSize)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.VideoCacheSize),
                            title = videoCacheSizeTitle,
                            subtitle = rowSubtitle(PlaybackRows.VideoCacheSize),
                            trailingText = "${preferences.videoCacheSizeMb} MB",
                            highlighted = highlightSettingId == PlaybackRows.VideoCacheSize.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = videoCacheSizeTitle,
                                    items = listOf(128, 256, 512, 1024, 2048, 4096),
                                    label = { "$it MB" },
                                    isSelected = { it == preferences.videoCacheSizeMb },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoCacheSizeMb(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.BackgroundAudio),
                            title = rowTitle(PlaybackRows.BackgroundAudio),
                            subtitle = rowSubtitle(PlaybackRows.BackgroundAudio),
                            checked = preferences.backgroundVideoAudioEnabled,
                            highlighted = highlightSettingId == PlaybackRows.BackgroundAudio.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setBackgroundVideoAudioEnabled(it) } },
                        )
                        // Auto-PiP on Home/recents (issue #167) — Android-only;
                        // the declared Platform(Pip) admission hides the row on
                        // desktop. Off makes leaving the app background it
                        // normally instead of entering picture-in-picture; the
                        // manual PiP button in the controls is unaffected.
                        if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.AutoEnterPip.id, rowFlags)) {
                            SettingToggleItem(
                                icon = rowIcon(PlaybackRows.AutoEnterPip),
                                title = rowTitle(PlaybackRows.AutoEnterPip),
                                subtitle = if (preferences.autoEnterPip) stringResource(Res.string.settings_auto_pip_on) else stringResource(Res.string.settings_auto_pip_off),
                                checked = preferences.autoEnterPip,
                                highlighted = highlightSettingId == PlaybackRows.AutoEnterPip.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.playback.setAutoEnterPip(it) } },
                            )
                        }
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.KeepScreenOn),
                            title = rowTitle(PlaybackRows.KeepScreenOn),
                            subtitle = rowSubtitle(PlaybackRows.KeepScreenOn),
                            checked = preferences.keepScreenOnDuringVideo,
                            highlighted = highlightSettingId == PlaybackRows.KeepScreenOn.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setKeepScreenOnDuringVideo(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.IncognitoMode),
                            title = rowTitle(PlaybackRows.IncognitoMode),
                            subtitle = if (preferences.incognitoModeEnabled) stringResource(Res.string.settings_incognito_mode_on) else stringResource(Res.string.settings_incognito_mode_off),
                            checked = preferences.incognitoModeEnabled,
                            highlighted = highlightSettingId == PlaybackRows.IncognitoMode.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setIncognitoModeEnabled(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowTimeRemaining),
                            title = rowTitle(PlaybackRows.ShowTimeRemaining),
                            subtitle = if (preferences.showTimeRemaining) stringResource(Res.string.settings_show_time_remaining_on) else stringResource(Res.string.settings_show_time_remaining_off),
                            checked = preferences.showTimeRemaining,
                            highlighted = highlightSettingId == PlaybackRows.ShowTimeRemaining.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setShowTimeRemaining(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowClockPlayer),
                            title = rowTitle(PlaybackRows.ShowClockPlayer),
                            subtitle = if (preferences.showClockInPlayer) stringResource(Res.string.settings_show_clock_player_on) else stringResource(Res.string.settings_show_clock_player_off),
                            checked = preferences.showClockInPlayer,
                            highlighted = highlightSettingId == PlaybackRows.ShowClockPlayer.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setShowClockInPlayer(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.PauseOnFocusLoss),
                            title = rowTitle(PlaybackRows.PauseOnFocusLoss),
                            subtitle = if (preferences.pauseOnAudioFocusLoss) stringResource(Res.string.settings_pause_on_focus_loss_on) else stringResource(Res.string.settings_pause_on_focus_loss_off),
                            checked = preferences.pauseOnAudioFocusLoss,
                            highlighted = highlightSettingId == PlaybackRows.PauseOnFocusLoss.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setPauseOnAudioFocusLoss(it) } },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.DuckOnTransientFocusLoss),
                            title = rowTitle(PlaybackRows.DuckOnTransientFocusLoss),
                            subtitle = if (preferences.duckOnTransientFocusLoss) stringResource(Res.string.settings_duck_on_phone_call_on) else stringResource(Res.string.settings_duck_on_phone_call_off),
                            checked = preferences.duckOnTransientFocusLoss,
                            highlighted = highlightSettingId == PlaybackRows.DuckOnTransientFocusLoss.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.playback.setDuckOnTransientFocusLoss(it) } },
                        )
                        // opt-in resume when headphones
                        // reconnect after the becoming-noisy auto-pause.
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ResumeHeadsetPlug),
                            title = rowTitle(PlaybackRows.ResumeHeadsetPlug),
                            subtitle = rowSubtitle(PlaybackRows.ResumeHeadsetPlug),
                            checked = preferences.videoResumeOnHeadsetPlug,
                            highlighted = highlightSettingId == PlaybackRows.ResumeHeadsetPlug.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoResumeOnHeadsetPlug(enabled) }
                            },
                        )
}
