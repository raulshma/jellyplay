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
 * The declared screen sections in LazyColumn order — the derivation source the
 * deep-link scroll resolver consumes (see HighlightScroll.kt): passing the
 * same declaration the rows are built from means the scroll target can never
 * drift from the UI. Index 0 is the always-visible player group; indices 1-3
 * (advanced video, engine config, media segments) only compose when advanced
 * settings are shown (their [SettingsScreenSection] carries
 * `advancedOnly = true`); 4-6 (SyncPlay, casting, DVR) always render. The
 * advanced shape folds into the scroll adjust via [deriveAdjustForAdvanced].
 */
private val playbackScreenSections: List<SettingsScreenSection> = listOf(
    SettingsScreenSection(SettingsScreenGroups.playbackPlayer.itemIdSet),
    SettingsScreenSection(SettingsScreenGroups.playbackAdvancedVideo.itemIdSet, advancedOnly = true),
    SettingsScreenSection(SettingsScreenGroups.playbackEngine.itemIdSet, advancedOnly = true),
    SettingsScreenSection(SettingsScreenGroups.playbackMediaSegments.itemIdSet, advancedOnly = true),
    SettingsScreenSection(SettingsScreenGroups.playbackSyncPlay.itemIdSet),
    SettingsScreenSection(SettingsScreenGroups.playbackCasting.itemIdSet),
    SettingsScreenSection(SettingsScreenGroups.playbackDvr.itemIdSet),
)

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
        PlaybackRows.SpatialAudio.id to preferences.spatialAudioEnabled,
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
        highlightGroups = playbackScreenSections.map { it.idSet },
        adjustForAdvanced = deriveAdjustForAdvanced(playbackScreenSections, showAdvanced),
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
            // playbackScreenSections, advancedOnly = true) compose behind the
            // advanced toggle, the same gate deriveAdjustForAdvanced's drift
            // fix reads.
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
                        // FROZEN hand approximation — the one hiddenCount the
                        // hiddenRowsFor derivation does not own: the real
                        // hidden-row count varies by platform, engine and the
                        // dialogue-boost toggle (the three advanced-only
                        // groups alone hide anywhere from 7 to 19+ rows), so
                        // no derivation via rowTotalFor reproduces 9 across
                        // the (isTv, engine) combos. Its source rows are the
                        // advanced-only blocks: the player group's advanced
                        // rows, the advanced-video group, the engine group
                        // and the media-segments group.
                        hiddenCount = 9,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
    }
}
