package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.StillWatchingMode
import com.raulshma.jellyplay.core.model.platformEngineSupport
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_double_tap_seek_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_tap_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_opposite
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_same
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_countdown_immediate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_countdown_seconds
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_still_watching_episodes_value

/** The `playback.player` group: player defaults (engine picker, transport, autoplay, player UX). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackPlayerGroup(
    preferences: PlaybackPreferences,
    showAdvanced: Boolean,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>,
    navActions: SettingsNavActions,
) {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerPlay,
                    title = stringResource(Res.string.settings_video_player),
                    summary = { stringResource(Res.string.settings_playback_subtitle, preferences.preferredPlayer.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    // Derived by rowTotalFor from the player group
                    // declaration (full per-id admission coverage): the
                    // platform-gated rows drop where the capability is
                    // missing, the TV rows need the TV form factor, and
                    // isAdvanced rows only render behind the advanced toggle.
                    SettingsItemList(
                        total = rowTotalFor(SettingsScreenGroups.playbackPlayer, rowFlags),
                    ) {
                    val preferredPlayerTitle = stringResource(Res.string.settings_preferred_player)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.PlayerEngine),
                        title = rowTitle(PlaybackRows.PlayerEngine),
                        subtitle = rowSubtitle(PlaybackRows.PlayerEngine),
                        trailingText = preferences.preferredPlayer.displayName,
                        highlighted = highlightSettingId == PlaybackRows.PlayerEngine.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = preferredPlayerTitle,
                                items = platformEngineSupport.engines,
                                label = { it.displayName },
                                subtitle = { it.description },
                                isSelected = { it == preferences.preferredPlayer },
                                onSelect = { viewModel.edit { scope -> scope.playback.setPreferredPlayer(it) } },
                            )
                        },
                    )
                    if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.SeekDuration.id, rowFlags)) {
                        val doubleTapSeekTitle = stringResource(Res.string.settings_double_tap_seek_duration)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.SeekDuration),
                            title = rowTitle(PlaybackRows.SeekDuration),
                            subtitle = rowSubtitle(PlaybackRows.SeekDuration),
                            trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                            highlighted = highlightSettingId == PlaybackRows.SeekDuration.id,
                            onClick = {
                                val durations = listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L, 60_000L)
                                activePicker.value = pickerChip(
                                    title = doubleTapSeekTitle,
                                    values = durations,
                                    current = preferences.videoSeekDurationMs,
                                    label = { "${it / 1000}s" },
                                    onSelect = { ms -> viewModel.edit { it.videoPlayer.setVideoSeekDurationMs(ms) } },
                                )
                            },
                        )
                    }
                    if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.Orientation.id, rowFlags)) {
                        val orientationTitle = rowTitle(PlaybackRows.Orientation)
                        // Resolve localized labels in composable scope; the
                        // picker's label lambda runs outside composition so
                        // it reads the pre-resolved map (segment-behavior
                        // picker pattern). The picker's per-item subtitle is
                        // gone with it: it printed the raw persisted wire
                        // string (`sensor_landscape`, …).
                        val orientationLabels = OrientationMode.entries.associateWith { it.localizedDisplayName() }
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.Orientation),
                            title = rowTitle(PlaybackRows.Orientation),
                            subtitle = rowSubtitle(PlaybackRows.Orientation),
                            trailingText = preferences.videoDefaultOrientation.localizedDisplayName(),
                            highlighted = highlightSettingId == PlaybackRows.Orientation.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = orientationTitle,
                                    items = OrientationMode.entries,
                                    label = { orientationLabels.getValue(it) },
                                    isSelected = { it == preferences.videoDefaultOrientation },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoDefaultOrientation(it) } },
                                )
                            },
                        )
                    }
                    if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.Gestures.id, rowFlags)) {
                        val gesturesTitle = rowTitle(PlaybackRows.Gestures)
                        val gesturesSubtitle = when (preferences.videoGestureMode) {
                            GestureMode.ALL -> stringResource(Res.string.settings_gestures_on)
                            GestureMode.TAP_ONLY -> stringResource(Res.string.settings_gestures_tap_only)
                            GestureMode.NONE -> stringResource(Res.string.settings_gestures_off)
                        }
                        // Localized labels for the trailing text + picker,
                        // resolved in composable scope (see the orientation
                        // row) — this row used to show the localized subtitle
                        // beside an English `displayName` value.
                        val gestureModeLabels = GestureMode.entries.associateWith { it.localizedDisplayName() }
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.Gestures),
                            title = gesturesTitle,
                            subtitle = gesturesSubtitle,
                            trailingText = preferences.videoGestureMode.localizedDisplayName(),
                            highlighted = highlightSettingId == PlaybackRows.Gestures.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = gesturesTitle,
                                    items = GestureMode.entries,
                                    label = { gestureModeLabels.getValue(it) },
                                    isSelected = { it == preferences.videoGestureMode },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoGestureMode(it) } },
                                )
                            },
                        )
                        val gestureIndicatorTitle = rowTitle(PlaybackRows.GestureIndicatorSide)
                        val gestureIndicatorLabels = GestureIndicatorSide.entries.associateWith { it.localizedDisplayName() }
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.GestureIndicatorSide),
                            title = gestureIndicatorTitle,
                            subtitle = if (preferences.videoGestureIndicatorSide == GestureIndicatorSide.OPPOSITE)
                                stringResource(Res.string.settings_gesture_indicator_opposite) else stringResource(Res.string.settings_gesture_indicator_same),
                            trailingText = preferences.videoGestureIndicatorSide.localizedDisplayName(),
                            highlighted = highlightSettingId == PlaybackRows.GestureIndicatorSide.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = gestureIndicatorTitle,
                                    items = GestureIndicatorSide.entries,
                                    label = { gestureIndicatorLabels.getValue(it) },
                                isSelected = { it == preferences.videoGestureIndicatorSide },
                                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoGestureIndicatorSide(it) } },
                            )
                        },
                        )
                        // double-tap-and-hold continuous seek.
                        // Shares the Gestures row's touch-gesture admission (the
                        // same gate [GestureIndicatorSide] rides), so no separate
                        // rowAdmitted check — mirroring the local pattern above.
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.DoubleTapHoldSeek),
                            title = rowTitle(PlaybackRows.DoubleTapHoldSeek),
                            subtitle = rowSubtitle(PlaybackRows.DoubleTapHoldSeek),
                            checked = preferences.videoDoubleTapHoldSeekEnabled,
                            highlighted = highlightSettingId == PlaybackRows.DoubleTapHoldSeek.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.videoPlayer.setVideoDoubleTapHoldSeekEnabled(enabled) }
                            },
                        )
                        if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.InputBindings.id, rowFlags)) {
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.InputBindings),
                                title = rowTitle(PlaybackRows.InputBindings),
                                subtitle = rowSubtitle(PlaybackRows.InputBindings),
                                highlighted = highlightSettingId == PlaybackRows.InputBindings.id,
                                onClick = { navActions.onNavigate(Route.InputBindings) },
                            )
                        }
                    }
                    val defaultSpeedTitle = rowTitle(PlaybackRows.DefaultSpeed)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.DefaultSpeed),
                        title = rowTitle(PlaybackRows.DefaultSpeed),
                        subtitle = rowSubtitle(PlaybackRows.DefaultSpeed),
                        trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        highlighted = highlightSettingId == PlaybackRows.DefaultSpeed.id,
                        onClick = {
                            val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                            activePicker.value = pickerChip(
                                title = defaultSpeedTitle,
                                values = speeds,
                                current = preferences.videoDefaultSpeed,
                                label = { if (it == 1.0f) "1x" else "${it}x" },
                                onSelect = { speed -> viewModel.edit { it.videoPlayer.setVideoDefaultSpeed(speed) } },
                            )
                        },
                    )
                    val holdSpeedTitle = rowTitle(PlaybackRows.HoldSpeedMultiplier)
                    val holdSpeedOffLabel = stringResource(Res.string.settings_off)
                    val holdSpeedOffSubtitle = stringResource(Res.string.settings_disabled)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.HoldSpeedMultiplier),
                        title = holdSpeedTitle,
                        subtitle = rowSubtitle(PlaybackRows.HoldSpeedMultiplier),
                        trailingText = if (preferences.videoHoldSpeedEnabled) "${preferences.videoHoldSpeedMultiplier}x" else holdSpeedOffLabel,
                        highlighted = highlightSettingId == PlaybackRows.HoldSpeedMultiplier.id,
                        onClick = {
                            // 0f encodes "Off" — selecting it replaces the former Hold-to-Seek toggle
                            val speeds = listOf(0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                            activePicker.value = PickerState.List(
                                title = holdSpeedTitle,
                                items = speeds,
                                label = { if (it == 0f) holdSpeedOffLabel else "${it}x" },
                                subtitle = { if (it == 0f) holdSpeedOffSubtitle else "" },
                                isSelected = { speed ->
                                    if (preferences.videoHoldSpeedEnabled) speed == preferences.videoHoldSpeedMultiplier else speed == 0f
                                },
                                onSelect = { speed ->
                                    if (speed == 0f) {
                                        viewModel.edit { it.videoPlayer.setVideoHoldSpeedEnabled(false) }
                                    } else {
                                        viewModel.edit { it.videoPlayer.setVideoHoldSpeedEnabled(true) }
                                        viewModel.edit { it.videoPlayer.setVideoHoldSpeedMultiplier(speed) }
                                    }
                                },
                            )
                        },
                    )
                    val defaultAspectTitle = rowTitle(PlaybackRows.DefaultAspect)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.DefaultAspect),
                        title = rowTitle(PlaybackRows.DefaultAspect),
                        subtitle = rowSubtitle(PlaybackRows.DefaultAspect),
                        trailingText = preferences.videoDefaultAspectRatio,
                        highlighted = highlightSettingId == PlaybackRows.DefaultAspect.id,
                        onClick = {
                            val aspectRatios = listOf("AUTO", "FIT", "FILL", "CROP", "16:9", "4:3", "21:9")
                            activePicker.value = PickerState.List(
                                title = defaultAspectTitle,
                                items = aspectRatios,
                                label = { it },
                                isSelected = { it == preferences.videoDefaultAspectRatio },
                                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoDefaultAspectRatio(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.VideoAutoplayNext),
                        title = rowTitle(PlaybackRows.VideoAutoplayNext),
                        subtitle = if (preferences.videoAutoplayNext) stringResource(Res.string.settings_auto_play_next_on) else stringResource(Res.string.settings_auto_play_next_off),
                        checked = preferences.videoAutoplayNext,
                        highlighted = highlightSettingId == PlaybackRows.VideoAutoplayNext.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoAutoplayNext(it) } },
                    )
                    val offLabel = stringResource(Res.string.settings_off)
                    val countdownLabel = if (preferences.autoPlayCountdownSec == 0) offLabel else "${preferences.autoPlayCountdownSec}s"
                    val countdownImmediate = stringResource(Res.string.settings_countdown_immediate)
                    val autoPlayCountdownTitle = rowTitle(PlaybackRows.AutoplayCountdown)
                    val countdownSecondsFormat = stringResource(Res.string.settings_countdown_seconds)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.AutoplayCountdown),
                        title = rowTitle(PlaybackRows.AutoplayCountdown),
                        subtitle = rowSubtitle(PlaybackRows.AutoplayCountdown),
                        trailingText = countdownLabel,
                        highlighted = highlightSettingId == PlaybackRows.AutoplayCountdown.id,
                        onClick = {
                            val options = listOf(0, 5, 10, 15)
                            activePicker.value = PickerState.List(
                                title = autoPlayCountdownTitle,
                                items = options,
                                label = { if (it == 0) offLabel else "${it}s" },
                                subtitle = { if (it == 0) countdownImmediate else formatIntPattern(countdownSecondsFormat, it) },
                                isSelected = { it == preferences.autoPlayCountdownSec },
                                onSelect = { viewModel.edit { scope -> scope.playback.setAutoPlayCountdownSec(it) } },
                            )
                        },
                    )
                    // "Still watching?" confirm prompt (feature 1.3): both
                    // rows ride the autoplay toggle (their declared WhenOn
                    // admission — no autoplay, no prompt to configure).
                    if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.StillWatchingMode.id, rowFlags)) {
                        val stillWatchingModeTitle = rowTitle(PlaybackRows.StillWatchingMode)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.StillWatchingMode),
                            title = rowTitle(PlaybackRows.StillWatchingMode),
                            subtitle = rowSubtitle(PlaybackRows.StillWatchingMode),
                            trailingText = preferences.stillWatchingMode.displayName,
                            highlighted = highlightSettingId == PlaybackRows.StillWatchingMode.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = stillWatchingModeTitle,
                                    items = StillWatchingMode.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.stillWatchingMode },
                                    onSelect = { mode -> viewModel.edit { scope -> scope.videoPlayer.setStillWatchingMode(mode) } },
                                )
                            },
                        )
                    }
                    if (SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.StillWatchingEpisodes.id, rowFlags)) {
                        val stillWatchingEpisodesTitle = rowTitle(PlaybackRows.StillWatchingEpisodes)
                        val episodesFormat = stringResource(Res.string.settings_still_watching_episodes_value)
                        SettingListItem(
                            icon = rowIcon(PlaybackRows.StillWatchingEpisodes),
                            title = rowTitle(PlaybackRows.StillWatchingEpisodes),
                            subtitle = rowSubtitle(PlaybackRows.StillWatchingEpisodes),
                            trailingText = if (preferences.stillWatchingEpisodeThreshold == 0) offLabel else formatIntPattern(episodesFormat, preferences.stillWatchingEpisodeThreshold),
                            highlighted = highlightSettingId == PlaybackRows.StillWatchingEpisodes.id,
                            onClick = {
                                val thresholds = listOf(0, 2, 3, 5, 8)
                                activePicker.value = PickerState.List(
                                    title = stillWatchingEpisodesTitle,
                                    items = thresholds,
                                    label = { if (it == 0) offLabel else formatIntPattern(episodesFormat, it) },
                                    isSelected = { it == preferences.stillWatchingEpisodeThreshold },
                                    onSelect = { episodes -> viewModel.edit { scope -> scope.videoPlayer.setStillWatchingEpisodeThreshold(episodes) } },
                                )
                            },
                        )
                    }
                    // per-content-type volume memory — desktop-only
                    // (the app owns mpv's volume scalar there); the declared
                    // Platform admission hides the row wholesale on Android.
                    if (SettingsScreenGroups.playbackPlayer.rowAdmitted(
                            PlaybackRows.RememberVolumePerContentType.id,
                            rowFlags,
                        )
                    ) {
                        SettingToggleItem(
                            icon = rowIcon(PlaybackRows.RememberVolumePerContentType),
                            title = rowTitle(PlaybackRows.RememberVolumePerContentType),
                            subtitle = rowSubtitle(PlaybackRows.RememberVolumePerContentType),
                            checked = preferences.rememberVolumePerContentType,
                            highlighted = highlightSettingId == PlaybackRows.RememberVolumePerContentType.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.volumeProfile.setRememberVolumePerContentType(enabled) }
                            },
                        )
                    }
                    if (showAdvanced) {
                        PlaybackPlayerAdvancedRows(
                            preferences = preferences,
                            rowFlags = rowFlags,
                            highlightSettingId = highlightSettingId,
                            viewModel = viewModel,
                            scope = scope,
                            activePicker = activePicker,
                        )
                    }
                    }
                }
}
