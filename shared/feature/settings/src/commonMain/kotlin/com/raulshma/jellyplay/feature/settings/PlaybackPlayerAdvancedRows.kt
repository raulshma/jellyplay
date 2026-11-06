package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode
import com.raulshma.jellyplay.core.model.BufferingVeil
import com.raulshma.jellyplay.core.model.ControlLayout
import com.raulshma.jellyplay.core.model.HoldSeekCadence
import com.raulshma.jellyplay.core.model.KeyboardSeekPreset
import com.raulshma.jellyplay.core.model.MaxZoom
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PauseFadeStyle
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlayerCenterDoubleTapAction
import com.raulshma.jellyplay.core.model.PlayerDoubleTapZoneBias
import com.raulshma.jellyplay.core.model.PlayerHoldSpeedPillStyle
import com.raulshma.jellyplay.core.model.PlayerSeekBarStyle
import com.raulshma.jellyplay.core.model.PlayerSwipeSensitivity
import com.raulshma.jellyplay.core.model.PollCadence
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SpeedPillClearance
import com.raulshma.jellyplay.core.model.SpeedPresetSet
import com.raulshma.jellyplay.core.model.TvTimeoutScale
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_play_trailer_before_movie_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_play_trailer_before_movie_on
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
                        // The pause screensaver: renders beside the
                        // pause-OSD toggle (the other paused-screen knob);
                        // its delay row rides the mode row's WhenOn admission.
                        val pauseScreensaverTitle = rowTitle(PlaybackRows.PauseScreensaver)
                        val pauseScreensaverLabels = PauseScreensaverMode.entries.associateWith { stringResource(pauseScreensaverModeLabelRes(it)) }
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.PauseScreensaver),
                            title = pauseScreensaverTitle,
                            subtitle = rowSubtitle(PlaybackRows.PauseScreensaver),
                            trailingText = pauseScreensaverLabels[preferences.pauseScreensaverMode]
                                ?: preferences.pauseScreensaverMode.name,
                            highlighted = highlightSettingId == PlaybackRows.PauseScreensaver.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = pauseScreensaverTitle,
                                    items = PauseScreensaverMode.entries,
                                    label = { pauseScreensaverLabels.getValue(it) },
                                    isSelected = { it == preferences.pauseScreensaverMode },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setPauseScreensaverMode(it) } },
                                )
                            },
                        )
                        if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.PauseScreensaverDelay.id, rowFlags)) {
                            val screensaverDelayTitle = rowTitle(PlaybackRows.PauseScreensaverDelay)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.PauseScreensaverDelay),
                                title = screensaverDelayTitle,
                                subtitle = rowSubtitle(PlaybackRows.PauseScreensaverDelay),
                                trailingText = "${preferences.pauseScreensaverAfterMinutes}m",
                                highlighted = highlightSettingId == PlaybackRows.PauseScreensaverDelay.id,
                                onClick = {
                                    val minutes = listOf(1, 2, 5, 10, 15, 30)
                                    activePicker.value = PickerState.List(
                                        title = screensaverDelayTitle,
                                        items = minutes,
                                        label = { "${it}m" },
                                        isSelected = { it == preferences.pauseScreensaverAfterMinutes },
                                        onSelect = { viewModel.edit { scope -> scope.videoPlayer.setPauseScreensaverAfterMinutes(it) } },
                                    )
                                },
                            )
                        }
                        // The bitmap-subtitle scaling mode — beside the
                        // zoom/aspect knobs (the other how-the-frame-fits
                        // control).
                        val bitmapScaleTitle = rowTitle(PlaybackRows.BitmapSubtitleScale)
                        val bitmapScaleLabels = BitmapSubtitleScaleMode.entries.associateWith { stringResource(bitmapSubtitleScaleModeLabelRes(it)) }
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.BitmapSubtitleScale),
                            title = bitmapScaleTitle,
                            subtitle = rowSubtitle(PlaybackRows.BitmapSubtitleScale),
                            trailingText = bitmapScaleLabels[preferences.bitmapSubtitleScaleMode]
                                ?: preferences.bitmapSubtitleScaleMode.name,
                            highlighted = highlightSettingId == PlaybackRows.BitmapSubtitleScale.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = bitmapScaleTitle,
                                    items = BitmapSubtitleScaleMode.entries,
                                    label = { bitmapScaleLabels.getValue(it) },
                                    isSelected = { it == preferences.bitmapSubtitleScaleMode },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setBitmapSubtitleScaleMode(it) } },
                                )
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
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.PlayTrailerBeforeMovie),
                            title = rowTitle(PlaybackRows.PlayTrailerBeforeMovie),
                            subtitle = if (preferences.playTrailerBeforeMovie) stringResource(Res.string.settings_play_trailer_before_movie_on) else stringResource(Res.string.settings_play_trailer_before_movie_off),
                            checked = preferences.playTrailerBeforeMovie,
                            highlighted = highlightSettingId == PlaybackRows.PlayTrailerBeforeMovie.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setPlayTrailerBeforeMovie(it) } },
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
                        // The browser's watch filter — rides the browser toggle
                        // it qualifies (the other episode-browser knob).
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.EpisodeBrowserHideWatched),
                            title = rowTitle(PlaybackRows.EpisodeBrowserHideWatched),
                            subtitle = rowSubtitle(PlaybackRows.EpisodeBrowserHideWatched),
                            checked = preferences.videoEpisodeBrowserHideWatched,
                            highlighted = highlightSettingId == PlaybackRows.EpisodeBrowserHideWatched.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoEpisodeBrowserHideWatched(it) } },
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
                        // Auto-PiP on Home/recents — Android-only;
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

                        // ── The player-chrome customization cluster ──────────
                        // The overlay/transport faces, the gesture-curve knobs
                        // and the seek-bar presentation pair. All advanced rows
                        // without extra gates (the admission derives from their
                        // spec flags — no rowAdmitted checks, mirroring the
                        // rows above).
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.OverlayAnimations),
                            title = rowTitle(PlaybackRows.OverlayAnimations),
                            subtitle = rowSubtitle(PlaybackRows.OverlayAnimations),
                            checked = preferences.videoOverlayAnimations,
                            highlighted = highlightSettingId == PlaybackRows.OverlayAnimations.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoOverlayAnimations(enabled) }
                            },
                        )
                        val resumeChipTitle = rowTitle(PlaybackRows.ResumeChipMs)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.ResumeChipMs),
                            title = resumeChipTitle,
                            subtitle = rowSubtitle(PlaybackRows.ResumeChipMs),
                            trailingText = if (preferences.videoResumeChipMs == 0L) offLabel else "${preferences.videoResumeChipMs / 1000}s",
                            highlighted = highlightSettingId == PlaybackRows.ResumeChipMs.id,
                            onClick = {
                                val durations = listOf(0L, 2_000L, 3_000L, 5_000L)
                                activePicker.value = pickerChip(
                                    title = resumeChipTitle,
                                    values = durations,
                                    current = preferences.videoResumeChipMs,
                                    label = { if (it == 0L) offLabel else "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoResumeChipMs(ms) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowPrevNextButtons),
                            title = rowTitle(PlaybackRows.ShowPrevNextButtons),
                            subtitle = rowSubtitle(PlaybackRows.ShowPrevNextButtons),
                            checked = preferences.videoShowPrevNextButtons,
                            highlighted = highlightSettingId == PlaybackRows.ShowPrevNextButtons.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoShowPrevNextButtons(enabled) }
                            },
                        )
                        val swipeSensitivityTitle = rowTitle(PlaybackRows.SwipeSensitivity)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SwipeSensitivity),
                            title = swipeSensitivityTitle,
                            subtitle = rowSubtitle(PlaybackRows.SwipeSensitivity),
                            trailingText = preferences.videoSwipeSensitivity.displayName,
                            highlighted = highlightSettingId == PlaybackRows.SwipeSensitivity.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = swipeSensitivityTitle,
                                    items = PlayerSwipeSensitivity.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoSwipeSensitivity },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoSwipeSensitivity(it) } },
                                )
                            },
                        )
                        val gestureLingerTitle = rowTitle(PlaybackRows.GestureOverlayLingerMs)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.GestureOverlayLingerMs),
                            title = gestureLingerTitle,
                            subtitle = rowSubtitle(PlaybackRows.GestureOverlayLingerMs),
                            trailingText = "${preferences.videoGestureOverlayLingerMs / 1000.0}s",
                            highlighted = highlightSettingId == PlaybackRows.GestureOverlayLingerMs.id,
                            onClick = {
                                val lingers = listOf(400L, 800L, 1_200L)
                                activePicker.value = pickerChip(
                                    title = gestureLingerTitle,
                                    values = lingers,
                                    current = preferences.videoGestureOverlayLingerMs,
                                    label = { "${it / 1000.0}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoGestureOverlayLingerMs(ms) } },
                                )
                            },
                        )
                        val centerDoubleTapTitle = rowTitle(PlaybackRows.CenterDoubleTapAction)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.CenterDoubleTapAction),
                            title = centerDoubleTapTitle,
                            subtitle = rowSubtitle(PlaybackRows.CenterDoubleTapAction),
                            trailingText = preferences.videoCenterDoubleTapAction.displayName,
                            highlighted = highlightSettingId == PlaybackRows.CenterDoubleTapAction.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = centerDoubleTapTitle,
                                    items = PlayerCenterDoubleTapAction.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoCenterDoubleTapAction },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoCenterDoubleTapAction(it) } },
                                )
                            },
                        )
                        val zoneBiasTitle = rowTitle(PlaybackRows.DoubleTapZoneBias)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.DoubleTapZoneBias),
                            title = zoneBiasTitle,
                            subtitle = rowSubtitle(PlaybackRows.DoubleTapZoneBias),
                            trailingText = preferences.videoDoubleTapZoneBias.displayName,
                            highlighted = highlightSettingId == PlaybackRows.DoubleTapZoneBias.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = zoneBiasTitle,
                                    items = PlayerDoubleTapZoneBias.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoDoubleTapZoneBias },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoDoubleTapZoneBias(it) } },
                                )
                            },
                        )
                        val holdSpeedPillTitle = rowTitle(PlaybackRows.HoldSpeedPillStyle)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.HoldSpeedPillStyle),
                            title = holdSpeedPillTitle,
                            subtitle = rowSubtitle(PlaybackRows.HoldSpeedPillStyle),
                            trailingText = preferences.videoHoldSpeedPillStyle.displayName,
                            highlighted = highlightSettingId == PlaybackRows.HoldSpeedPillStyle.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = holdSpeedPillTitle,
                                    items = PlayerHoldSpeedPillStyle.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoHoldSpeedPillStyle },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoHoldSpeedPillStyle(it) } },
                                )
                            },
                        )
                        val seekBarStyleTitle = rowTitle(PlaybackRows.SeekBarStyle)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SeekBarStyle),
                            title = seekBarStyleTitle,
                            subtitle = rowSubtitle(PlaybackRows.SeekBarStyle),
                            trailingText = preferences.videoSeekBarStyle.displayName,
                            highlighted = highlightSettingId == PlaybackRows.SeekBarStyle.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = seekBarStyleTitle,
                                    items = PlayerSeekBarStyle.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoSeekBarStyle },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoSeekBarStyle(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowBufferedRange),
                            title = rowTitle(PlaybackRows.ShowBufferedRange),
                            subtitle = rowSubtitle(PlaybackRows.ShowBufferedRange),
                            checked = preferences.videoShowBufferedRange,
                            highlighted = highlightSettingId == PlaybackRows.ShowBufferedRange.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoShowBufferedRange(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowChapterMarkers),
                            title = rowTitle(PlaybackRows.ShowChapterMarkers),
                            subtitle = rowSubtitle(PlaybackRows.ShowChapterMarkers),
                            checked = preferences.videoShowChapterMarkers,
                            highlighted = highlightSettingId == PlaybackRows.ShowChapterMarkers.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoShowChapterMarkers(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.HideSeekTimestamps),
                            title = rowTitle(PlaybackRows.HideSeekTimestamps),
                            subtitle = rowSubtitle(PlaybackRows.HideSeekTimestamps),
                            checked = preferences.videoHideSeekTimestamps,
                            highlighted = highlightSettingId == PlaybackRows.HideSeekTimestamps.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoHideSeekTimestamps(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowBufferPercent),
                            title = rowTitle(PlaybackRows.ShowBufferPercent),
                            subtitle = rowSubtitle(PlaybackRows.ShowBufferPercent),
                            checked = preferences.videoShowBufferPercent,
                            highlighted = highlightSettingId == PlaybackRows.ShowBufferPercent.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoShowBufferPercent(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.SeekPreviewTime),
                            title = rowTitle(PlaybackRows.SeekPreviewTime),
                            subtitle = rowSubtitle(PlaybackRows.SeekPreviewTime),
                            checked = preferences.videoSeekPreviewTime,
                            highlighted = highlightSettingId == PlaybackRows.SeekPreviewTime.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoSeekPreviewTime(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ShowEngineBadge),
                            title = rowTitle(PlaybackRows.ShowEngineBadge),
                            subtitle = rowSubtitle(PlaybackRows.ShowEngineBadge),
                            checked = preferences.videoShowEngineBadge,
                            highlighted = highlightSettingId == PlaybackRows.ShowEngineBadge.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoShowEngineBadge(enabled) }
                            },
                        )

                        // ── The second QoL wave: transport/HUD cadence + ────
                        // preset families. All advanced rows without extra
                        // gates (same admission shape as the cluster above).
                        val tvTimeoutScaleTitle = rowTitle(PlaybackRows.TvTimeoutScale)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.TvTimeoutScale),
                            title = tvTimeoutScaleTitle,
                            subtitle = rowSubtitle(PlaybackRows.TvTimeoutScale),
                            trailingText = preferences.videoTvTimeoutScale.displayName,
                            highlighted = highlightSettingId == PlaybackRows.TvTimeoutScale.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = tvTimeoutScaleTitle,
                                    items = TvTimeoutScale.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoTvTimeoutScale },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoTvTimeoutScale(it) } },
                                )
                            },
                        )
                        val keyboardSeekPresetTitle = rowTitle(PlaybackRows.KeyboardSeekPreset)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.KeyboardSeekPreset),
                            title = keyboardSeekPresetTitle,
                            subtitle = rowSubtitle(PlaybackRows.KeyboardSeekPreset),
                            trailingText = preferences.videoKeyboardSeekPreset.displayName,
                            highlighted = highlightSettingId == PlaybackRows.KeyboardSeekPreset.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = keyboardSeekPresetTitle,
                                    items = KeyboardSeekPreset.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoKeyboardSeekPreset },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoKeyboardSeekPreset(it) } },
                                )
                            },
                        )
                        val holdSeekCadenceTitle = rowTitle(PlaybackRows.HoldSeekCadence)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.HoldSeekCadence),
                            title = holdSeekCadenceTitle,
                            subtitle = rowSubtitle(PlaybackRows.HoldSeekCadence),
                            trailingText = preferences.videoHoldSeekCadence.displayName,
                            highlighted = highlightSettingId == PlaybackRows.HoldSeekCadence.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = holdSeekCadenceTitle,
                                    items = HoldSeekCadence.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoHoldSeekCadence },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoHoldSeekCadence(it) } },
                                )
                            },
                        )
                        val pillClearanceTitle = rowTitle(PlaybackRows.SpeedPillClearance)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SpeedPillClearance),
                            title = pillClearanceTitle,
                            subtitle = rowSubtitle(PlaybackRows.SpeedPillClearance),
                            trailingText = preferences.videoSpeedPillClearance.displayName,
                            highlighted = highlightSettingId == PlaybackRows.SpeedPillClearance.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = pillClearanceTitle,
                                    items = SpeedPillClearance.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoSpeedPillClearance },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoSpeedPillClearance(it) } },
                                )
                            },
                        )
                        val pauseFadeStyleTitle = rowTitle(PlaybackRows.PauseFadeStyle)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.PauseFadeStyle),
                            title = pauseFadeStyleTitle,
                            subtitle = rowSubtitle(PlaybackRows.PauseFadeStyle),
                            trailingText = preferences.videoPauseFadeStyle.displayName,
                            highlighted = highlightSettingId == PlaybackRows.PauseFadeStyle.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = pauseFadeStyleTitle,
                                    items = PauseFadeStyle.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoPauseFadeStyle },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoPauseFadeStyle(it) } },
                                )
                            },
                        )
                        val controlLayoutTitle = rowTitle(PlaybackRows.ControlLayout)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.ControlLayout),
                            title = controlLayoutTitle,
                            subtitle = rowSubtitle(PlaybackRows.ControlLayout),
                            trailingText = preferences.videoControlLayout.displayName,
                            highlighted = highlightSettingId == PlaybackRows.ControlLayout.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = controlLayoutTitle,
                                    items = ControlLayout.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoControlLayout },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoControlLayout(it) } },
                                )
                            },
                        )
                        val maxZoomTitle = rowTitle(PlaybackRows.MaxZoom)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.MaxZoom),
                            title = maxZoomTitle,
                            subtitle = rowSubtitle(PlaybackRows.MaxZoom),
                            trailingText = preferences.videoMaxZoom.displayName,
                            highlighted = highlightSettingId == PlaybackRows.MaxZoom.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = maxZoomTitle,
                                    items = MaxZoom.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoMaxZoom },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoMaxZoom(it) } },
                                )
                            },
                        )
                        val pollCadenceTitle = rowTitle(PlaybackRows.PollCadence)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.PollCadence),
                            title = pollCadenceTitle,
                            subtitle = rowSubtitle(PlaybackRows.PollCadence),
                            trailingText = preferences.videoPollCadence.displayName,
                            highlighted = highlightSettingId == PlaybackRows.PollCadence.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = pollCadenceTitle,
                                    items = PollCadence.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoPollCadence },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoPollCadence(it) } },
                                )
                            },
                        )
                        val sleepConfirmTitle = rowTitle(PlaybackRows.SleepConfirmMinutes)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SleepConfirmMinutes),
                            title = sleepConfirmTitle,
                            subtitle = rowSubtitle(PlaybackRows.SleepConfirmMinutes),
                            trailingText = "${preferences.videoSleepConfirmMinutes}m",
                            highlighted = highlightSettingId == PlaybackRows.SleepConfirmMinutes.id,
                            onClick = {
                                val minutes = listOf(1, 2, 5, 10, 15, 30)
                                activePicker.value = PickerState.List(
                                    title = sleepConfirmTitle,
                                    items = minutes,
                                    label = { "${it}m" },
                                    isSelected = { it == preferences.videoSleepConfirmMinutes },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoSleepConfirmMinutes(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.AmbientUnderFill),
                            title = rowTitle(PlaybackRows.AmbientUnderFill),
                            subtitle = rowSubtitle(PlaybackRows.AmbientUnderFill),
                            checked = preferences.videoAmbientUnderFill,
                            highlighted = highlightSettingId == PlaybackRows.AmbientUnderFill.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoAmbientUnderFill(enabled) }
                            },
                        )
                        val bufferingVeilTitle = rowTitle(PlaybackRows.BufferingVeil)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.BufferingVeil),
                            title = bufferingVeilTitle,
                            subtitle = rowSubtitle(PlaybackRows.BufferingVeil),
                            trailingText = preferences.videoBufferingVeil.displayName,
                            highlighted = highlightSettingId == PlaybackRows.BufferingVeil.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = bufferingVeilTitle,
                                    items = BufferingVeil.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoBufferingVeil },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoBufferingVeil(it) } },
                                )
                            },
                        )
                        val speedPresetSetTitle = rowTitle(PlaybackRows.SpeedPresetSet)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SpeedPresetSet),
                            title = speedPresetSetTitle,
                            subtitle = rowSubtitle(PlaybackRows.SpeedPresetSet),
                            trailingText = preferences.videoSpeedPresetSet.displayName,
                            highlighted = highlightSettingId == PlaybackRows.SpeedPresetSet.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = speedPresetSetTitle,
                                    items = SpeedPresetSet.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.videoSpeedPresetSet },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoSpeedPresetSet(it) } },
                                )
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.AutoplayWifiOnly),
                            title = rowTitle(PlaybackRows.AutoplayWifiOnly),
                            subtitle = rowSubtitle(PlaybackRows.AutoplayWifiOnly),
                            checked = preferences.videoAutoplayWifiOnly,
                            highlighted = highlightSettingId == PlaybackRows.AutoplayWifiOnly.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoAutoplayWifiOnly(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.ConfirmExit),
                            title = rowTitle(PlaybackRows.ConfirmExit),
                            subtitle = rowSubtitle(PlaybackRows.ConfirmExit),
                            checked = preferences.videoConfirmExit,
                            highlighted = highlightSettingId == PlaybackRows.ConfirmExit.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoConfirmExit(enabled) }
                            },
                        )
}
