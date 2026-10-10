package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_message

/**
 * The declared screen groups in LazyColumn order — the derivation source the
 * deep-link scroll resolver consumes (see HighlightScroll.kt): passing the
 * same declaration the rows are built from means the scroll target can never
 * drift from the UI. Index 0 is the always-visible player group; indices 1-3
 * (advanced video, engine config, media segments) only compose when advanced
 * settings are shown; 4-6 (SyncPlay, casting, DVR) always render.
 */
private val playbackScreenGroups: List<Set<String>> = listOf(
    SettingsScreenGroups.playbackPlayer.itemIdSet,
    SettingsScreenGroups.playbackAdvancedVideo.itemIdSet,
    SettingsScreenGroups.playbackEngine.itemIdSet,
    SettingsScreenGroups.playbackMediaSegments.itemIdSet,
    SettingsScreenGroups.playbackSyncPlay.itemIdSet,
    SettingsScreenGroups.playbackCasting.itemIdSet,
    SettingsScreenGroups.playbackDvr.itemIdSet,
)

/**
 * [resolveHighlightScrollIndex]'s [rememberHighlightScrollIndex adjustForAdvanced]
 * for this screen's LazyColumn: with advanced hidden the three advanced-only
 * groups don't compose — their rows cannot take a highlight (`-1`) — while the
 * three always-visible trailing groups shift up by exactly those three slots.
 * Pure (and internal) so the contract test can pin the drift fixes against it.
 */
internal fun playbackAdjustForAdvanced(showAdvanced: Boolean): (Int) -> Int =
    if (showAdvanced) {
        { it }
    } else {
        { raw ->
            when {
                raw == 0 -> 0
                raw <= 3 -> -1
                else -> raw - 3
            }
        }
    }

/**
 * The playback screen's admission flags — the declared row admissions both
 * the SettingsItemList totals and the emission `if`s read. Pure (and
 * internal) so the contract test can pin every WhenOn parent the playback
 * search groups declare against this wiring: the AUDIO_PASSTHROUGH pair
 * dropping out of it is exactly how the five per-codec rows went
 * permanently invisible.
 */
internal fun playbackRowAdmissionFlags(
    isTv: Boolean,
    showAdvanced: Boolean,
    preferences: PlaybackPreferences,
): RowAdmissionFlags = RowAdmissionFlags(
    isTv = isTv,
    showAdvanced = showAdvanced,
    parentsOn = rowParentsOn(
        PlaybackRows.DialogueBoost.id to preferences.dialogueBoostEnabled,
        PlaybackRows.VideoAutoplayNext.id to preferences.videoAutoplayNext,
        PlaybackRows.AudioPassthrough.id to preferences.audioPassthrough,
    ),
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** Drill-ins (the "Customize controls" row) ride the shared nav facade. */
    navActions: SettingsNavActions,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    // Picker payloads built off suspend work (the desktop audio-device
    // enumeration) launch here.
    val scope = rememberCoroutineScope()

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_title),
        onBack = onBack,
        focusTag = "playback_init",
        highlightSettingId = highlightSettingId,
        highlightGroups = playbackScreenGroups,
        adjustForAdvanced = playbackAdjustForAdvanced(showAdvanced),
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_playback_cd),
            dialogTitle = stringResource(Res.string.settings_reset_playback_title),
            dialogMessage = stringResource(Res.string.settings_reset_playback_message),
            onReset = { viewModel.resetPlaybackSettings() },
        ),
        pickerHost = true,
    ) { activePickerState ->
            item {
                PlaybackPlayerGroup(
                    preferences = preferences,
                    showAdvanced = showAdvanced,
                    rowFlags = rowFlags,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                    navActions = navActions,
                )
            }

            // The three advanced-only groups (declared indices 1-3 in
            // playbackScreenGroups) compose behind the advanced toggle,
            // the same gate playbackAdjustForAdvanced's drift fix reads.
            if (showAdvanced) {
                item {
                PlaybackAdvancedVideoGroup(
                    preferences = preferences,
                    rowFlags = rowFlags,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                )
                }
                item {
                PlaybackEngineGroup(
                    preferences = preferences,
                    rowFlags = rowFlags,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                )
                }
                item {
                PlaybackMediaSegmentsGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                )
                }
            }

            item {
                PlaybackSyncPlayGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                )
            }

            item {
                PlaybackCastingGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                )
            }

            item {
                PlaybackDvrGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePickerState,
                )
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        // A frozen hand approximation, kept verbatim: the real hidden-
                        // row count varies by platform, engine and the dialogue-boost
                        // toggle (the three advanced-only groups alone hide anywhere
                        // from 7 to 19+ rows), so no derivation via rowTotalFor
                        // reproduces 9 across the (isTv, engine) combos. Its source
                        // rows are the advanced-only blocks: the player group's
                        // advanced rows, the advanced-video group, the engine group
                        // and the media-segments group.
                        hiddenCount = 9,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
    }
}
