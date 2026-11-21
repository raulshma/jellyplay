package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlaybackSpeedLadder
import com.raulshma.jellyplay.core.model.SessionRestoreMode
import com.raulshma.jellyplay.core.model.StillWatchingMode
import com.raulshma.jellyplay.core.model.TrickplayMode
import com.raulshma.jellyplay.core.model.platformEngineSupport
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_basics
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_video_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preferred_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_double_tap_seek_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_play_next_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_countdown_immediate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_countdown_seconds
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_still_watching_episodes_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_autoplay_trailers_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cinema_mode_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_next_row_off
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
import org.koin.compose.viewmodel.koinViewModel

/**
 * The `playback.basics` group (ADR-0017): the mainstream playback-behavior
 * rows the former player group carried — the engine picker, transport
 * defaults, the autoplay/resume family, session restore, the pause-screen
 * experience and the streaming/privacy toggles. The advanced rows render
 * inside the group's advanced block exactly as on the former combined
 * screen (the group itself is not advancedOnly — the leaf's non-advanced
 * rows always compose).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackBasicsGroup(
    preferences: PlaybackPreferences,
    showAdvanced: Boolean,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.PlayerPlay,
        title = stringResource(Res.string.settings_video_player),
        summary = { stringResource(Res.string.settings_playback_subtitle, preferences.preferredPlayer.displayName) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackBasics.itemIdSet,
    ) {
        // Derived by rowTotalFor from the basics group declaration (full
        // per-id admission coverage): the platform-gated rows drop where the
        // capability is missing, the TV row needs the TV form factor, and
        // isAdvanced rows only render behind the advanced toggle.
        SettingsItemList(
            total = rowTotalFor(SettingsScreenGroups.playbackBasics, rowFlags),
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
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.SeekDuration.id, rowFlags)) {
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
            val defaultSpeedTitle = rowTitle(PlaybackRows.DefaultSpeed)
            SettingListItem(
                icon = rowIcon(PlaybackRows.DefaultSpeed),
                title = rowTitle(PlaybackRows.DefaultSpeed),
                subtitle = rowSubtitle(PlaybackRows.DefaultSpeed),
                trailingText = PlaybackSpeedLadder.format(preferences.videoDefaultSpeed),
                highlighted = highlightSettingId == PlaybackRows.DefaultSpeed.id,
                onClick = {
                    val speeds = PlaybackSpeedLadder.fullRungs
                    activePicker.value = pickerChip(
                        title = defaultSpeedTitle,
                        values = speeds,
                        current = preferences.videoDefaultSpeed,
                        label = { PlaybackSpeedLadder.format(it) },
                        onSelect = { speed -> viewModel.edit { it.videoPlayer.setVideoDefaultSpeed(speed) } },
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
            // "Still watching?" confirm prompt (feature 1.3): both rows ride
            // the autoplay toggle (their declared WhenOn admission — no
            // autoplay, no prompt to configure).
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AutoplayEpisodeLimit.id, rowFlags)) {
                val autoplayLimitTitle = rowTitle(PlaybackRows.AutoplayEpisodeLimit)
                val unlimitedLabel = stringResource(Res.string.settings_unlimited)
                SettingListItem(
                    icon = rowIcon(PlaybackRows.AutoplayEpisodeLimit),
                    title = autoplayLimitTitle,
                    subtitle = rowSubtitle(PlaybackRows.AutoplayEpisodeLimit),
                    trailingText = if (preferences.autoplayEpisodeLimit == 0) unlimitedLabel else "${preferences.autoplayEpisodeLimit}",
                    highlighted = highlightSettingId == PlaybackRows.AutoplayEpisodeLimit.id,
                    onClick = {
                        val limits = listOf(0, 1, 2, 3, 5, 10)
                        activePicker.value = PickerState.List(
                            title = autoplayLimitTitle,
                            items = limits,
                            label = { if (it == 0) unlimitedLabel else "$it" },
                            isSelected = { it == preferences.autoplayEpisodeLimit },
                            onSelect = { viewModel.edit { scope -> scope.videoPlayer.setAutoplayEpisodeLimit(it) } },
                        )
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.SessionRestore.id, rowFlags)) {
                val sessionRestoreTitle = rowTitle(PlaybackRows.SessionRestore)
                // Localized labels resolved in composable scope; the picker's
                // label lambda runs outside composition (the orientation-row
                // pattern on the controls leaf).
                val sessionRestoreLabels = SessionRestoreMode.entries.associateWith { stringResource(sessionRestoreModeLabelRes(it)) }
                SettingListItem(
                    icon = rowIcon(PlaybackRows.SessionRestore),
                    title = sessionRestoreTitle,
                    subtitle = rowSubtitle(PlaybackRows.SessionRestore),
                    trailingText = sessionRestoreLabels[preferences.sessionRestoreMode]
                        ?: preferences.sessionRestoreMode.name,
                    highlighted = highlightSettingId == PlaybackRows.SessionRestore.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = sessionRestoreTitle,
                            items = SessionRestoreMode.entries,
                            label = { sessionRestoreLabels.getValue(it) },
                            isSelected = { it == preferences.sessionRestoreMode },
                            onSelect = { viewModel.edit { scope -> scope.playback.setSessionRestoreMode(it) } },
                        )
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.NextEpisodeDetails.id, rowFlags)) {
                SettingToggleItem(
                    icon = rowIcon(PlaybackRows.NextEpisodeDetails),
                    title = rowTitle(PlaybackRows.NextEpisodeDetails),
                    subtitle = rowSubtitle(PlaybackRows.NextEpisodeDetails),
                    checked = preferences.showNextEpisodeDetailsAfterFinish,
                    highlighted = highlightSettingId == PlaybackRows.NextEpisodeDetails.id,
                    onCheckedChange = { enabled ->
                        viewModel.edit { scope -> scope.videoPlayer.setShowNextEpisodeDetailsAfterFinish(enabled) }
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.OfferDeleteAfterPlaybackMovies.id, rowFlags)) {
                SettingToggleItem(
                    icon = rowIcon(PlaybackRows.OfferDeleteAfterPlaybackMovies),
                    title = rowTitle(PlaybackRows.OfferDeleteAfterPlaybackMovies),
                    subtitle = rowSubtitle(PlaybackRows.OfferDeleteAfterPlaybackMovies),
                    checked = preferences.offerDeleteAfterPlaybackMovies,
                    highlighted = highlightSettingId == PlaybackRows.OfferDeleteAfterPlaybackMovies.id,
                    onCheckedChange = { enabled ->
                        viewModel.edit { scope -> scope.playback.setOfferDeleteAfterPlaybackMovies(enabled) }
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.OfferDeleteAfterPlaybackEpisodes.id, rowFlags)) {
                SettingToggleItem(
                    icon = rowIcon(PlaybackRows.OfferDeleteAfterPlaybackEpisodes),
                    title = rowTitle(PlaybackRows.OfferDeleteAfterPlaybackEpisodes),
                    subtitle = rowSubtitle(PlaybackRows.OfferDeleteAfterPlaybackEpisodes),
                    checked = preferences.offerDeleteAfterPlaybackEpisodes,
                    highlighted = highlightSettingId == PlaybackRows.OfferDeleteAfterPlaybackEpisodes.id,
                    onCheckedChange = { enabled ->
                        viewModel.edit { scope -> scope.playback.setOfferDeleteAfterPlaybackEpisodes(enabled) }
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AmbientBlur.id, rowFlags)) {
                SettingToggleItem(
                    icon = rowIcon(PlaybackRows.AmbientBlur),
                    title = rowTitle(PlaybackRows.AmbientBlur),
                    subtitle = rowSubtitle(PlaybackRows.AmbientBlur),
                    checked = preferences.ambientBlurEnabled,
                    highlighted = highlightSettingId == PlaybackRows.AmbientBlur.id,
                    onCheckedChange = { enabled ->
                        viewModel.edit { scope -> scope.videoPlayer.setAmbientBlurEnabled(enabled) }
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.PauseDetails.id, rowFlags)) {
                SettingToggleItem(
                    icon = rowIcon(PlaybackRows.PauseDetails),
                    title = rowTitle(PlaybackRows.PauseDetails),
                    subtitle = rowSubtitle(PlaybackRows.PauseDetails),
                    checked = preferences.pauseDetailsEnabled,
                    highlighted = highlightSettingId == PlaybackRows.PauseDetails.id,
                    onCheckedChange = { enabled ->
                        viewModel.edit { scope -> scope.videoPlayer.setPauseDetailsEnabled(enabled) }
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.TrickplayMode.id, rowFlags)) {
                val trickplayModeTitle = rowTitle(PlaybackRows.TrickplayMode)
                val trickplayModeLabels = TrickplayMode.entries.associateWith { stringResource(trickplayModeLabelRes(it)) }
                SettingListItem(
                    icon = rowIcon(PlaybackRows.TrickplayMode),
                    title = trickplayModeTitle,
                    subtitle = rowSubtitle(PlaybackRows.TrickplayMode),
                    trailingText = trickplayModeLabels[preferences.trickplayMode]
                        ?: preferences.trickplayMode.name,
                    highlighted = highlightSettingId == PlaybackRows.TrickplayMode.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = trickplayModeTitle,
                            items = TrickplayMode.entries,
                            label = { trickplayModeLabels.getValue(it) },
                            isSelected = { it == preferences.trickplayMode },
                            onSelect = { viewModel.edit { scope -> scope.videoPlayer.setTrickplayMode(it) } },
                        )
                    },
                )
            }
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.StillWatchingMode.id, rowFlags)) {
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
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.StillWatchingEpisodes.id, rowFlags)) {
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
            // per-content-type volume memory — desktop-only (the app owns
            // mpv's volume scalar there); the declared Platform admission
            // hides the row wholesale on Android.
            if (SettingsScreenGroups.playbackBasics.rowAdmitted(
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
                PlaybackBasicsAdvancedRows(
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
 * The basics group's advanced sub-rows — the former player group's advanced
 * block minus the rows the Controls & gestures and Player chrome leaves
 * took (ADR-0017). Emitted inside the group's `if (showAdvanced)` block.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaybackBasicsAdvancedRows(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>,
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
    // pausing must not summon the control overlay (opt-in; off = today's
    // show-on-pause behavior).
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
    // The pause screensaver: renders beside the pause-OSD toggle (the other
    // paused-screen knob); its delay row rides the mode row's WhenOn
    // admission.
    val pauseScreensaverTitle = rowTitle(PlaybackRows.PauseScreensaver)
    val pauseScreensaverLabels = com.raulshma.jellyplay.core.model.PauseScreensaverMode.entries.associateWith { stringResource(pauseScreensaverModeLabelRes(it)) }
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
                items = com.raulshma.jellyplay.core.model.PauseScreensaverMode.entries,
                label = { pauseScreensaverLabels.getValue(it) },
                isSelected = { it == preferences.pauseScreensaverMode },
                onSelect = { viewModel.edit { scope -> scope.videoPlayer.setPauseScreensaverMode(it) } },
            )
        },
    )
    if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.PauseScreensaverDelay.id, rowFlags)) {
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
    if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, rowFlags)) {
        SettingToggleItem(
            icon = rowIcon(PlaybackRows.AndroidTvWatchNext),
            title = rowTitle(PlaybackRows.AndroidTvWatchNext),
            subtitle = if (preferences.androidTvWatchNextEnabled) stringResource(Res.string.settings_watch_next_row_on) else stringResource(Res.string.settings_watch_next_row_off),
            checked = preferences.androidTvWatchNextEnabled,
            highlighted = highlightSettingId == PlaybackRows.AndroidTvWatchNext.id,
            onCheckedChange = { viewModel.setAndroidTvWatchNextEnabled(it) },
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
                items = com.raulshma.jellyplay.core.model.PreloadBufferSize.entries,
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
    // Auto-PiP on Home/recents — Android-only; the declared Platform(Pip)
    // admission hides the row on desktop. Off makes leaving the app
    // background it normally instead of entering picture-in-picture; the
    // manual PiP button in the controls is unaffected.
    if (SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AutoEnterPip.id, rowFlags)) {
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
    // opt-in resume when headphones reconnect after the becoming-noisy
    // auto-pause.
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

/**
 * The Playback Basics leaf (ADR-0017): the mainstream playback-behavior rows
 * ([PlaybackBasicsGroup]) behind the hub's Basics entrance. The advanced
 * rows ride the same advanced toggle they always did — the leaf carries the
 * toggle and the derived hidden-settings hint, exactly the former combined
 * screen's shape narrowed to this group.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackBasicsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    // Picker payloads built off suspend work (the desktop audio-device
    // enumeration on the engines leaf) launch here on the leaves that need it.
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val basicsGroups = listOf(
        SettingsScreenGroups.playbackBasics.asScreenGroup { activePicker ->
            PlaybackBasicsGroup(
                preferences = preferences,
                showAdvanced = showAdvanced,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_basics),
        onBack = onBack,
        focusTag = "playback_basics_init",
        highlightSettingId = highlightSettingId,
        groups = basicsGroups,
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
