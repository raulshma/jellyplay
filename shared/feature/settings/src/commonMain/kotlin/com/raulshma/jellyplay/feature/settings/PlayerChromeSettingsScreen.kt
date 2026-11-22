package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode
import com.raulshma.jellyplay.core.model.BufferingVeil
import com.raulshma.jellyplay.core.model.ControlLayout
import com.raulshma.jellyplay.core.model.MaxZoom
import com.raulshma.jellyplay.core.model.PauseFadeStyle
import com.raulshma.jellyplay.core.model.PlayerCenterDoubleTapAction
import com.raulshma.jellyplay.core.model.PlayerDoubleTapZoneBias
import com.raulshma.jellyplay.core.model.PlayerHoldSpeedPillStyle
import com.raulshma.jellyplay.core.model.PlayerSeekBarStyle
import com.raulshma.jellyplay.core.model.PlayerSwipeSensitivity
import com.raulshma.jellyplay.core.model.PollCadence
import com.raulshma.jellyplay.core.model.SpeedPillClearance
import com.raulshma.jellyplay.core.model.SpeedPresetSet
import com.raulshma.jellyplay.core.model.TvTimeoutScale
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Video
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_chrome
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tv_zoom_percent
import org.koin.compose.viewmodel.koinViewModel

/**
 * The `playback.playerChrome` group (ADR-0017): the video_* customization
 * cluster the former player group's advanced block carried — the
 * overlay/transport faces, the gesture-curve knobs, the seek-bar
 * presentation, the engine badge, the episode-browser watch filter, the
 * bitmap-subtitle scale and the TV overscan zoom. Every row is
 * advanced-gated, so the whole group composes only behind the advanced
 * toggle (the leaf's slot declares `advancedOnly` — the shell owns the
 * gate; the bodies keep their per-row emission verbatim).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackPlayerChromeGroup(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>,
) {
    val offLabel = stringResource(Res.string.settings_off)
    SettingsGroup(
        icon = Tabler.Outline.Video,
        title = stringResource(Res.string.settings_playback_entry_chrome),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackPlayerChrome.itemIdSet,
    ) {
        SettingsItemList(
            total = rowTotalFor(SettingsScreenGroups.playbackPlayerChrome, rowFlags),
        ) {
            // The bitmap-subtitle scaling mode — beside the zoom/aspect
            // knobs (the other how-the-frame-fits control).
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

            // ── The player-chrome customization cluster ──────────────
            // The overlay/transport faces, the gesture-curve knobs and the
            // seek-bar presentation pair. All advanced rows without extra
            // gates (the admission derives from their spec flags) — except
            // the TV zoom, whose Tv gate the rowAdmitted check reads.
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
            // The browser's watch filter — rides the browser toggle it
            // qualifies (the other episode-browser knob).
            SettingToggleItem(
                icon = rowIcon(PlaybackRows.EpisodeBrowserHideWatched),
                title = rowTitle(PlaybackRows.EpisodeBrowserHideWatched),
                subtitle = rowSubtitle(PlaybackRows.EpisodeBrowserHideWatched),
                checked = preferences.videoEpisodeBrowserHideWatched,
                highlighted = highlightSettingId == PlaybackRows.EpisodeBrowserHideWatched.id,
                onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setVideoEpisodeBrowserHideWatched(it) } },
            )

            // ── The second QoL wave: transport/HUD cadence + ─────────
            // preset families. All advanced rows without extra gates (same
            // admission shape as the cluster above).
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
            if (SettingsScreenGroups.playbackPlayerChrome.rowAdmitted(PlaybackRows.TvZoomMode.id, rowFlags)) {
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
                icon = rowIcon(PlaybackRows.PlaybackDiagnostics),
                title = rowTitle(PlaybackRows.PlaybackDiagnostics),
                subtitle = rowSubtitle(PlaybackRows.PlaybackDiagnostics),
                checked = preferences.videoPlaybackDiagnostics,
                highlighted = highlightSettingId == PlaybackRows.PlaybackDiagnostics.id,
                onCheckedChange = { enabled ->
                    viewModel.edit { scope -> scope.videoPlayer.setVideoPlaybackDiagnostics(enabled) }
                },
            )
        }
    }
}

/**
 * The Player chrome leaf (ADR-0017): the advanced video_* customization
 * cluster ([PlaybackPlayerChromeGroup]) behind the hub's Player chrome
 * entrance. Every row is advanced-gated, so the leaf carries the advanced
 * toggle and the derived hidden-settings hint; with advanced off the group
 * does not compose and the hint carries the reveal affordance.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerChromeSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val chromeGroups = listOf(
        SettingsScreenGroups.playbackPlayerChrome.asScreenGroup(advancedOnly = true) { activePicker ->
            PlaybackPlayerChromeGroup(
                preferences = preferences,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_chrome),
        onBack = onBack,
        focusTag = "player_chrome_init",
        highlightSettingId = highlightSettingId,
        groups = chromeGroups,
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
