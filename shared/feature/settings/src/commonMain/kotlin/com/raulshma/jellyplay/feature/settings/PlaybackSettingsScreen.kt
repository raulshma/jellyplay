package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_message

/**
 * The playback screen's ONE ordered group declaration — parameterized by the
 * preferred player because the engine slot's content-count hook is the one
 * runtime-keyed shape the flags vocabulary cannot express (only the preferred
 * player's rows exist at runtime, so the declared all-engines group
 * contributes its delta for that prefix alone; the screen-local reset row
 * cancels in the delta). The screen passes the live preference, and the
 * contract test pins the shipped JVM shape (LIBVLC — the desktop seam backs
 * the mpv device rows) plus the MPV honesty pin against the SAME
 * declaration, so the per-engine hook is stated exactly once (ADR-0009's
 * amendment). Per-group row emission stays hand-written: the screen maps
 * each slot to its body inline in [playbackScreenGroups].
 */
internal fun PlaybackSettingsScreenSlots(
    preferredPlayer: PlayerType,
): List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.playbackPlayer.asScreenGroup(),
    SettingsScreenGroups.playbackAdvancedVideo.asScreenGroup(advancedOnly = true),
    SettingsScreenGroups.playbackEngine.asScreenGroup(
        advancedOnly = true,
        emittedRows = { flags ->
            when (preferredPlayer) {
                PlayerType.MPV -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith("mpv_") }
                PlayerType.LIBVLC -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith("vlc_") }
                PlayerType.EXO_PLAYER -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith("exo_") }
                PlayerType.EXTERNAL -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id == PlaybackRows.ExternalPlayerApp.id }
                else -> 0
            }
        },
    ),
    SettingsScreenGroups.playbackMediaSegments.asScreenGroup(advancedOnly = true),
    SettingsScreenGroups.playbackSyncPlay.asScreenGroup(),
    SettingsScreenGroups.playbackCasting.asScreenGroup(),
    SettingsScreenGroups.playbackDvr.asScreenGroup(),
)

/**
 * The playback screen's group bodies, mapped onto the ONE ordered
 * declaration ([PlaybackSettingsScreenSlots], in LazyColumn order — the
 * shell derives the deep-link scroll resolution, the whole-group advanced
 * gating (indices 1-3: advanced video, engine config, media segments only
 * compose behind the advanced toggle) and the hidden-settings hint count
 * from it, and the contract test pins the same declaration, so the scroll
 * target and the hint can never drift from the UI. The advanced shape folds
 * into the scroll adjust in the shell) with each slot's hand-written body
 * attached inline — the bodies close over the screen's state (preferences,
 * flags, view model) and the shell only reads the derived shape (ADR-0009:
 * per-row emission stays hand-written).
 */
@Composable
private fun playbackScreenGroups(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    navActions: SettingsNavActions,
): List<PreferenceScreenGroup> {
    val slots = PlaybackSettingsScreenSlots(preferredPlayer = preferences.preferredPlayer)
    return slots.withContent(
        bodies = listOf(
        { activePicker ->
            PlaybackPlayerGroup(
                preferences = preferences,
                showAdvanced = rowFlags.showAdvanced,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
                navActions = navActions,
            )
        },
        { activePicker ->
            PlaybackAdvancedVideoGroup(
                preferences = preferences,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        { activePicker ->
            PlaybackEngineGroup(
                preferences = preferences,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        { activePicker ->
            PlaybackMediaSegmentsGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        { activePicker ->
            PlaybackSyncPlayGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        { activePicker ->
            PlaybackCastingGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        { activePicker ->
            PlaybackDvrGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        ),
    )
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
        PlaybackRows.SpatialAudio.id to preferences.spatialAudioEnabled,
        // The pause-screensaver delay rides the mode row: any non-OFF
        // screensaver counts as the parent being on.
        PlaybackRows.PauseScreensaver.id to (preferences.pauseScreensaverMode != PauseScreensaverMode.OFF),
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
        groups = playbackScreenGroups(
            preferences = preferences,
            rowFlags = rowFlags,
            highlightSettingId = highlightSettingId,
            viewModel = viewModel,
            scope = scope,
            navActions = navActions,
        ),
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            // Derived in the shell from the declaration above: the player
            // group's advanced rows, the advanced-video group, the preferred
            // engine's branch rows and the media-segments group — honest
            // across the (isTv, engine, parent-toggle) combos the frozen
            // literal silently approximated.
            rowFlags = rowFlags,
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_playback_cd),
            dialogTitle = stringResource(Res.string.settings_reset_playback_title),
            dialogMessage = stringResource(Res.string.settings_reset_playback_message),
            onReset = { viewModel.resetPlaybackSettings() },
        ),
        pickerHost = true,
    )
}
