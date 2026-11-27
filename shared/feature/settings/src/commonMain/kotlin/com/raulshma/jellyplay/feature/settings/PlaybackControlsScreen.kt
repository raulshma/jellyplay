package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.HandMove
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_controls
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_tap_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gestures_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_opposite
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gesture_indicator_same
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trickplay_on_gestures_off
import org.koin.compose.viewmodel.koinViewModel

/**
 * The `playback.controls` group (ADR-0017): the input-surface rows the
 * former player group carried — screen orientation, the touch-gesture set
 * (with the input-binding editor drill-in, preserved inside its historical
 * touch-gesture emission block), the hold-speed multiplier and the advanced
 * gesture-curve/keyboard-seek rows. The advanced rows render inside the
 * group's advanced block exactly as on the former combined screen.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackControlsGroup(
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
        icon = Tabler.Outline.HandMove,
        title = stringResource(Res.string.settings_playback_entry_controls),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId == null || highlightSettingId in SettingsScreenGroups.playbackControls.itemIdSet,
    ) {
        SettingsItemList(
            total = rowTotalFor(SettingsScreenGroups.playbackControls, rowFlags),
        ) {
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
                    // 0f encodes "Off" for hold-to-seek.
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
            if (SettingsScreenGroups.playbackControls.rowAdmitted(PlaybackRows.Orientation.id, rowFlags)) {
                val orientationTitle = rowTitle(PlaybackRows.Orientation)
                // Resolve localized labels in composable scope; the picker's
                // label lambda runs outside composition so it reads the
                // pre-resolved map (segment-behavior picker pattern). The
                // picker's per-item subtitle is gone with it: it printed the
                // raw persisted wire string (`sensor_landscape`, …).
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
            if (SettingsScreenGroups.playbackControls.rowAdmitted(PlaybackRows.Gestures.id, rowFlags)) {
                val gesturesTitle = rowTitle(PlaybackRows.Gestures)
                val gesturesSubtitle = when (preferences.videoGestureMode) {
                    GestureMode.ALL -> stringResource(Res.string.settings_gestures_on)
                    GestureMode.TAP_ONLY -> stringResource(Res.string.settings_gestures_tap_only)
                    GestureMode.NONE -> stringResource(Res.string.settings_gestures_off)
                }
                // Localized labels for the trailing text + picker, resolved
                // in composable scope (see the orientation row).
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
                // double-tap-and-hold continuous seek. Shares the Gestures
                // row's touch-gesture admission (the same gate
                // [GestureIndicatorSide] rides), so no separate rowAdmitted
                // check — mirroring the local pattern above.
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
            }
            // Not a touch-gesture row: the bindings drill-in carries its own
            // Always admission, independent of the Gestures platform gate.
            if (SettingsScreenGroups.playbackControls.rowAdmitted(PlaybackRows.InputBindings.id, rowFlags)) {
                SettingListItem(
                    icon = rowIcon(PlaybackRows.InputBindings),
                    title = rowTitle(PlaybackRows.InputBindings),
                    subtitle = rowSubtitle(PlaybackRows.InputBindings),
                    highlighted = highlightSettingId == PlaybackRows.InputBindings.id,
                    onClick = { navActions.onNavigate(Route.InputBindings) },
                )
            }
            if (showAdvanced) {
                PlaybackControlsAdvancedRows(
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

/**
 * The controls group's advanced sub-rows — the gesture-curve/keyboard-seek
 * rows the former player group's advanced block carried (ADR-0017).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaybackControlsAdvancedRows(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>,
) {
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
        icon = rowIcon(PlaybackRows.TrickplayOnGestures),
        title = rowTitle(PlaybackRows.TrickplayOnGestures),
        subtitle = if (preferences.trickplayOnSeekGesture) stringResource(Res.string.settings_trickplay_on_gestures_on) else stringResource(Res.string.settings_trickplay_on_gestures_off),
        checked = preferences.trickplayOnSeekGesture,
        highlighted = highlightSettingId == PlaybackRows.TrickplayOnGestures.id,
        onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setTrickplayOnSeekGesture(it) } },
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
                items = com.raulshma.jellyplay.core.model.KeyboardSeekPreset.entries,
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
                items = com.raulshma.jellyplay.core.model.HoldSeekCadence.entries,
                label = { it.displayName },
                isSelected = { it == preferences.videoHoldSeekCadence },
                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setVideoHoldSeekCadence(it) } },
            )
        },
    )
}

/**
 * The Playback Controls & gestures leaf (ADR-0017): the input-surface rows
 * ([PlaybackControlsGroup]) behind the hub's Controls entrance, including
 * the input-binding editor drill-in (navigated through the shared nav
 * facade). The advanced rows ride the same advanced toggle they always did.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackControlsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** The input-bindings drill-in rides the shared nav facade. */
    navActions: SettingsNavActions,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val controlsGroups = listOf(
        SettingsScreenGroups.playbackControls.asScreenGroup { activePicker ->
            PlaybackControlsGroup(
                preferences = preferences,
                showAdvanced = showAdvanced,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
                navActions = navActions,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_controls),
        onBack = onBack,
        focusTag = "playback_controls_init",
        highlightSettingId = highlightSettingId,
        groups = controlsGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            rowFlags = rowFlags,
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        pickerHost = true,
    )
}
